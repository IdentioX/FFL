"""Static/evidence validation for P2; never opens a database or claims live readiness."""
from pathlib import Path
import datetime
import hashlib
import json
import re
import subprocess
import xml.etree.ElementTree as X
import zipfile
from zoneinfo import ZoneInfo

root = Path(__file__).resolve().parents[1]
evidence = root / "docs/direct-rewrite/evidence"
out = evidence / "p2"
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
references = json.loads((evidence / "reference-files.json").read_text())
assert all(sha(Path(f["path"])) == f["sha256"] for f in references), "Reference changed"
for repository, head in [("FFL", "047458aa416465dea772ee65082c093f26064699"),
                         ("FFL-backend", "68b82b09c4f66b2408e563b71a0c7baf9fa0085a")]:
    location = root.parent / repository
    assert subprocess.check_output(["git", "-C", str(location), "status", "--porcelain"]) == b""
    assert subprocess.check_output(["git", "-C", str(location), "rev-parse", "HEAD"], text=True).strip() == head
assert json.loads((out / "p1-gate.json").read_text())["p1_result"] == "POSITIVE"

java_sources = "\n".join(p.read_text() for p in (root / "app/src/main/java").rglob("*.kt"))
for forbidden in ["retrofit2", "okhttp3", "DriverApiService", "DRIVER_API_BASE_URL", "ru.ffl24.zakaz", "accessToken"]:
    assert forbidden not in java_sources, "Old runtime reference"
assert re.findall(r'prepareStatement\("([^"]+)"\)', java_sources) == ["SELECT 1"]
assert not re.search(r"\b(executeUpdate|executeBatch|runBlocking)\b", java_sources)
build_config = (root / "app/build/generated/source/buildConfig/debug/ru/ffl24/direct/BuildConfig.java").read_text()
for name in ["HOST", "PORT", "DATABASE", "USER", "PASSWORD", "USE_SSL", "CA_PEM"]:
    assert f'DIRECT_MYSQL_{name} = "";' in build_config, "Expected unconfigured developer APK"

graphs = {}
for variant in ["debug", "release"]:
    components = (out / f"{variant}-components.txt").read_text().splitlines()
    assert "org.mariadb.jdbc:mariadb-java-client:3.5.10" in components
    assert not any(any(name in c for name in ["retrofit2", "okhttp3", "io.ktor", "io.jsonwebtoken", "com.auth0", "com.mysql:"]) for c in components)
    assert not any("project :" in c and c != "project :app" for c in components)
    graphs[variant] = len(components)
artifact = next(a for a in json.loads((out / "driver-artifacts.json").read_text())
                if a["url"].endswith("/mariadb-java-client-3.5.10.jar"))
cached_jars = list((Path("/workspace/.ffl/gradle/caches/modules-2/files-2.1/org.mariadb.jdbc/mariadb-java-client/3.5.10")).rglob("*.jar"))
assert cached_jars and all(sha(p) == artifact["sha256"] for p in cached_jars), "JDBC checksum mismatch"

def counts(paths):
    total = {key: 0 for key in ["tests", "failures", "errors", "skipped"]}
    for path in paths:
        xml = X.parse(path).getroot()
        # Root testsuites already aggregates its child suites: do not double count.
        for key in total:
            total[key] += int(xml.attrib.get(key, 0))
    return total

unit = counts((out / "unit").glob("TEST*.xml"))
android = counts([out / "instrumentation-tests.xml"])
assert unit == dict(tests=26, failures=0, errors=0, skipped=0)
assert android == dict(tests=11, failures=0, errors=0, skipped=0)
lint = X.parse(out / "lint.xml").getroot()
assert not [i for i in lint.findall("issue") if i.attrib["severity"] in ["Error", "Fatal"]]
apk = root / "app/build/outputs/apk/debug/app-debug.apk"
device = json.loads((out / "device.json").read_text())
assert sha(apk) == device["installed_apk_sha256"]
with zipfile.ZipFile(apk) as package:
    dex = b"".join(package.read(n) for n in package.namelist() if n.endswith(".dex"))
    for marker in [b"Lretrofit2/", b"Lokhttp3/", b"Lru/ffl24/zakaz/", b"DriverApiService", b"fixture-only", b"fixture.invalid"]:
        assert marker not in dex, "Forbidden reference/fixture in app APK"
    assert not any("fixture-ca" in n for n in package.namelist()), "Test CA in application APK"
manifest = (out / "apk-manifest.xml").read_text()
assert 'android:usesCleartextTraffic="false"' in manifest and 'android:allowBackup="false"' in manifest
assert "android.permission.INTERNET" in manifest and 'package="ru.ffl24.direct.dev"' in manifest
tls = [json.loads(line) for line in (out / "tls-fixture.log").read_text().splitlines() if line.startswith("{")]
assert any(i == dict(fixture="valid", state="tls_accepted_auth_not_processed") for i in tls)
for name in ["untrusted", "wrong-host"]:
    assert any(i == dict(fixture=name, state="tls_rejected") for i in tls)
    assert not any(i == dict(fixture=name, state="tls_accepted_auth_not_processed") for i in tls)

before = json.loads((out / "before-files.json").read_text())
after = {str(p.relative_to(root)): sha(p) for p in root.rglob("*") if p.is_file()
         and not any(part in ["build", ".gradle", ".kotlin"] for part in p.relative_to(root).parts)
         and "evidence" not in p.relative_to(root).parts and p.name != "local.properties"}
before = {k: v for k, v in before.items() if "evidence" not in Path(k).parts}
changed = {k: ("ADDED" if k not in before else "MODIFIED") for k, v in after.items() if before.get(k) != v}
changed.update({k: "REMOVED" for k in before if k not in after})
(out / "files.md").write_text("# Изменённые файлы P2\n\n" + "\n".join(f"- {state}: `{name}`" for name, state in sorted(changed.items())) + "\n\nEvidence: новые файлы в docs/direct-rewrite/evidence/p2; прежние P0/P1 evidence не изменены.\n")
result = dict(time_moscow=datetime.datetime.now(ZoneInfo("Europe/Moscow")).isoformat(),
              static_check="POSITIVE", reference_files_unchanged=len(references), unit=unit, instrumentation=android,
              lint_errors=0, lint_warnings=[i.attrib["id"] for i in lint.findall("issue")],
              runtime_components=graphs, jdbc_sha256=artifact["sha256"], apk_sha256=sha(apk), apk_size=apk.stat().st_size,
              release_unsigned_sha256=sha(root / "app/build/outputs/apk/release/app-release-unsigned.apk"),
              db_configured=False, live_mysql_used=False, fixture_is_database=False,
              full_p2_result="BLOCKED", physical_device=False, p3_started=False, changed_files=changed)
(out / "static-validation.json").write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n")
print(json.dumps({k: v for k, v in result.items() if k != "changed_files"}, indent=2))
