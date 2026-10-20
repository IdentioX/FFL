"""Verify collected final-audit evidence, APK and preserved reference sources.

This does not run tests, access production or rewrite historical phase reports.
"""
from pathlib import Path
import hashlib
import json
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET
import zipfile

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/"docs/evidence/final-audit"
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
read=lambda name:json.loads((OUT/name).read_text())

for report in ["FINAL_ENGINEERING_AUDIT.md","FINAL_TEST_RESULTS.md","REMAINING_RISKS.md","RELEASE_READINESS.md"]:
    assert (ROOT/"docs"/report).is_file()
assert read("release-room-fixed-command.json")["exit"]==0
assert "BUILD SUCCESSFUL" in (OUT/"release-room-fixed-build.log").read_text()
assert "-keep class androidx.work.impl.WorkDatabase_Impl { <init>(); }" in (ROOT/"app/proguard-rules.pro").read_text()
assert read("build-delivery-command.json")["exit"]==0
assert "BUILD SUCCESSFUL" in (OUT/"build-delivery.log").read_text()
unit={k:0 for k in ["tests","failures","errors","skipped"]}
(OUT/"unit").mkdir(exist_ok=True)
for p in (ROOT/"app/build/test-results/testDebugUnitTest").glob("TEST-*.xml"):
    r=ET.parse(p).getroot()
    for key in unit:unit[key]+=int(r.get(key,0))
    shutil.copyfile(p,OUT/"unit"/p.name)
assert unit=={"tests":843,"failures":0,"errors":0,"skipped":0}
ui=read("android-ui/summary.json")
assert ui["passed"] and ui["junit"]["tests"]=="29" and all(ui["junit"][k]=="0" for k in ["failures","errors","skipped"])
restored=read("android-restored.json")
assert len(restored["cases"])==11 and all(c["passed"] and c["xml"]["tests"]=="1" and all(c["xml"][k]=="0" for k in ["failures","errors","skipped"]) for c in restored["cases"])
minified=read("android-minified.json")
assert minified["passed"] and len(minified["cases"])==2 and not minified["apk_debuggable"] and minified["r8_obfuscation_confirmed"]
assert minified["installed_apk_sha256"]==minified["private_configured_r8_apk_sha256"]
assert all(c["passed"] and c["sql_delivery_verified"] and c["junit"]["tests"]=="1" and all(c["junit"][k]=="0" for k in ["failures","errors","skipped"]) for c in minified["cases"])
assert {c["method"] for c in minified["cases"]}=={"loginAndDelivery","coldRestoreAndLogout"}
integrity=read("original-data-integrity.json")
assert integrity["all_original_records_unchanged"] and integrity["original_tables_checked"]==101 and integrity["baseline_rows"]==359540
assert read("clean-restore-meta.json")["previous_baseline_independently_confirmed"]
assert read("restricted-grants.json")["unrelated_table_read_denied"] and not read("restricted-grants.json")["driver_row_isolation_provided"]
config=(ROOT/"app/build/generated/source/buildConfig/debug/ru/ffl24/direct/BuildConfig.java").read_text()
assert 'DATA_SOURCE_MODE = "DIRECT_MYSQL"' in config and 'VERSION_CODE = 4' in config
assert all(re.search(rf'DIRECT_MYSQL_{key}\s*=\s*"";',config) for key in ["HOST","PORT","DATABASE","USER","PASSWORD","USE_SSL","CA_PEM"])
apk=ROOT/"app/build/outputs/apk/debug/app-debug.apk"
assert sha(apk)==ui["apk_sha256"]==ui["installed_apk_sha256"]
with zipfile.ZipFile(apk) as z:
    dex=b"".join(z.read(n) for n in z.namelist() if re.fullmatch(r"classes\d*\.dex",n))
assert b"BINARY parol = BINARY ?" in dex
assert all(name not in dex for name in [b"DriverApiService",b"RetrofitProvider",b"DRIVER_API_BASE_URL",b"OwnedTestDatabase",b"AuditDatabaseTest",b"completion-db.json"])
for variant in ["debug","release"]:
    p=ROOT/f"app/build/reports/direct-runtime/{variant}-components.txt"
    graph=p.read_text()
    assert all(g not in graph for g in ["com.squareup.retrofit2:","com.squareup.okhttp3:","io.ktor:","io.jsonwebtoken:","com.auth0:","com.mysql:"])
    assert "org.mariadb.jdbc:mariadb-java-client:3.5.10" in graph
    assert "androidx.compose.runtime:runtime-android:1.10.6" in graph
    shutil.copyfile(p,OUT/f"{variant}-runtime-components.txt")
lint_path=ROOT/"app/build/reports/lint-results-debug.xml"
lint={}
for issue in ET.parse(lint_path).getroot():
    level=issue.get("severity");lint[level]=lint.get(level,0)+1
assert not lint.get("Error",0)
shutil.copyfile(lint_path,OUT/"lint-results-debug.xml")
source=ROOT.parent/".ffl/restore-source/112.sql.gz"
assert sha(source)=="fbd81f4fe11f61c13967b3cff3a53c682ac81046bf37924dd4e474e37c3cf94d"
assert sha(source.with_suffix(""))=="04e481bdfa30e286e0b568e999763fab8fcf0cc91b192f564f74a039ee9e8c55"
assert source.with_suffix("").stat().st_size==52606346
passwords=[]
for state_name in ["direct-dev","direct-restored"]:
    for line in (ROOT.parent/".ffl"/state_name/"container.env").read_text().splitlines():
        key,sep,value=line.partition("=")
        if key in {"MARIADB_ROOT_PASSWORD","MARIADB_PASSWORD"}:
            assert sep and len(value)>=32
            passwords.append(value.encode())
passwords.append(json.loads((ROOT.parent/".ffl/direct-restored/integrity-config.json").read_text())["password"].encode())
assert len(passwords)==5
scan_excluded={"build",".gradle",".kotlin",".git","releases","__pycache__"}
scanned=0
for path in ROOT.rglob("*"):
    if path.is_file() and not scan_excluded.intersection(path.relative_to(ROOT).parts):
        assert not any(value in path.read_bytes() for value in passwords),"Private DB credential found in project artifact"
        scanned+=1
assert not any(value in dex for value in passwords),"Private DB credential found in public APK"

refs=json.loads((ROOT/"docs/direct-rewrite/evidence/reference-files.json").read_text())
assert len(refs)==104 and all(sha(Path(item["path"]))==item["sha256"] for item in refs)
preserved=read("preservation-before.json")["references"]
for repo in ["FFL","FFL-backend"]:
    assert subprocess.check_output(["git","-C",str(ROOT.parent/repo),"status","--porcelain=v1"])==b""
    assert subprocess.check_output(["git","-C",str(ROOT.parent/repo),"rev-parse","HEAD"]).decode().strip()==preserved[repo]["head"]
for sums_name in ["SHA256SUMS","SHA256SUMS-0.2.1.txt"]:
    for line in (ROOT/"releases"/sums_name).read_text().splitlines():
        expected,name=line.split(maxsplit=1)
        assert sha(ROOT/"releases"/name.strip().removeprefix("*"))==expected
before=read("before.json")
excluded={"build",".gradle",".kotlin",".git","releases","__pycache__","final-audit"}
current={str(p.relative_to(ROOT)):sha(p) for p in ROOT.rglob("*") if p.is_file() and not excluded.intersection(p.relative_to(ROOT).parts)}
changed=sorted(k for k in before.keys()|current.keys() if before.get(k)!=current.get(k))
(OUT/"files.md").write_text("# Изменённые файлы аудита\n\n"+"\n".join("- `"+p+"`" for p in changed)+"\n")
report={"readiness":"DEVELOPER_TEST_READY","version":"0.2.2-dev","version_code":4,
    "unit":unit,"android_credentials_free_passed":29,"android_restored_passed":11,"android_minified_passed":2,
    "android_total_distinct_selected_methods":42,"public_apk_sha256":sha(apk),"public_apk_bytes":apk.stat().st_size,
    "public_configuration_empty":True,"api_runtime_dependencies_present":False,"lint":lint,
    "original_tables_unchanged":101,"original_rows_unchanged":359540,"reference_files_unchanged":104,
    "driver_rollout":"BLOCKED_SECURITY","physical_device_tested":False,"windows_executed":False,
    "production_accessed":False,"private_passwords_checked_without_disclosure":5,"public_files_scanned":scanned,"changed_files":changed,"current_file_hashes":current}
(OUT/"validation.json").write_text(json.dumps(report,indent=2))
print(json.dumps({k:v for k,v in report.items() if k not in ["changed_files","current_file_hashes"]},indent=2))
