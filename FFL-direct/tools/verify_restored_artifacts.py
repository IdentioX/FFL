"""Verify this cloud task's collected artifacts. No DB access and no secret values printed."""
from pathlib import Path
import hashlib
import json
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET
import zipfile

root = Path(__file__).resolve().parents[1]
evidence = root / "docs/direct-rewrite/evidence/restored-db"
sha = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
build = json.loads((evidence / "build-final-command.json").read_text())
assert build["exit"] == 0
unit = {key: 0 for key in ["tests", "failures", "errors", "skipped"]}
(evidence / "unit").mkdir(exist_ok=True)
for path in (root / "app/build/test-results/testDebugUnitTest").glob("TEST-*.xml"):
    node = ET.parse(path).getroot()
    for key in unit:
        unit[key] += int(node.get(key, "0"))
    shutil.copyfile(path, evidence / "unit" / path.name)
assert unit == {"tests": 831, "failures": 0, "errors": 0, "skipped": 0}
android = json.loads((evidence / "android-safe-summary.json").read_text())
assert android["passed"] == 6 and all(case["passed"] for case in android["cases"])
regression = ET.parse(evidence / "fixture-regression.xml").getroot()
assert regression.get("tests") == "8" and all(regression.get(k) == "0" for k in ["failures", "errors", "skipped"])
integrity = json.loads((evidence / "original-data-integrity.json").read_text())
assert integrity["all_original_records_unchanged"] and integrity["original_tables_checked"] == 101 and integrity["baseline_rows"] == 359540
for variant in ["debug", "release"]:
    path = root / f"app/build/reports/direct-runtime/{variant}-components.txt"
    text = path.read_text()
    assert all(group not in text for group in ["com.squareup.retrofit2:", "com.squareup.okhttp3:", "io.ktor:", "io.jsonwebtoken:", "com.auth0:", "com.mysql:"])
    assert "org.mariadb.jdbc:mariadb-java-client:3.5.10" in text and "androidx.compose.runtime:runtime-android:1.10.6" in text
    shutil.copyfile(path, evidence / f"public-{variant}-components.txt")
config = (root / "app/build/generated/source/buildConfig/debug/ru/ffl24/direct/BuildConfig.java").read_text()
assert 'DATA_SOURCE_MODE = "DIRECT_MYSQL"' in config
assert all(re.search(rf'DIRECT_MYSQL_{key}\s*=\s*"";', config) for key in ["HOST", "PORT", "DATABASE", "USER", "PASSWORD", "USE_SSL", "CA_PEM"])
apk = root / "app/build/outputs/apk/debug/app-debug.apk"
with zipfile.ZipFile(apk) as archive:
    dex = b"".join(archive.read(name) for name in archive.namelist() if re.fullmatch(r"classes\d*\.dex", name))
assert all(word not in dex for word in [b"DriverApiService", b"RetrofitProvider", b"DRIVER_API_BASE_URL", b"OwnedTestDatabase", b"RestoredDatabaseFlowTest", b"completion-db.json"])
lint = ET.parse(root / "app/build/reports/lint-results-debug.xml").getroot()
counts = {}
for issue in lint.findall("issue"):
    severity = issue.get("severity"); counts[severity] = counts.get(severity, 0) + 1
assert counts.get("Error", 0) == 0
shutil.copyfile(root / "app/build/reports/lint-results-debug.xml", evidence / "lint-results-debug.xml")
refs = json.loads((root / "docs/direct-rewrite/evidence/reference-files.json").read_text())
assert len(refs) == 104 and all(sha(Path(item["path"])) == item["sha256"] for item in refs)
for repo in ["/workspace/FFL", "/workspace/FFL-backend"]:
    assert subprocess.check_output(["git", "-C", repo, "status", "--porcelain=v1"]) == b""
before = json.loads((root / "docs/direct-rewrite/evidence/db-integration/before.json").read_text())
current = {str(p.relative_to(root)): sha(p) for p in root.rglob("*") if p.is_file() and not {"build", ".gradle", ".kotlin", ".git", "releases", "db-integration", "restored-db", "__pycache__"}.intersection(p.relative_to(root).parts)}
changed = sorted(k for k in before.keys() | current.keys() if before.get(k) != current.get(k))
report = {"local_result": "POSITIVE", "build_exit": 0, "unit": unit, "android_restored_cases_passed": 6,
    "android_fixture_cases_passed": 8, "original_tables_unchanged": 101, "original_rows_unchanged": 359540,
    "reference_files_unchanged": 104, "lint": counts, "api_dependencies_present": False,
    "public_apk_configuration_empty": True, "public_apk": {"sha256": sha(apk), "bytes": apk.stat().st_size},
    "release_unsigned_sha256": sha(root / "app/build/outputs/apk/release/app-release-unsigned.apk"),
    "physical_device_tested": False, "production_accessed": False, "windows_executed": False,
    "changed_files": changed, "source_hashes": {str(p.relative_to(root)): sha(p) for p in (root / "app/src").rglob("*") if p.is_file()}}
(evidence / "artifact-validation.json").write_text(json.dumps(report, indent=2))
(evidence / "files.md").write_text("# Изменённые файлы продолжения\n\n" + "\n".join("- `" + name + "`" for name in changed) + "\n")
print(json.dumps({k: v for k, v in report.items() if k not in ["changed_files", "source_hashes"]}, indent=2))
