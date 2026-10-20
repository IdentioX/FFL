"""Owned, persistent, loopback-only Direct development DB. Never connects to production.

Python 3.11+, Docker, OpenSSL; works in PowerShell and bash. Secrets and imports stay
in a private sibling .ffl/direct-dev directory, never in the source checkout.
"""
from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import sys
import time
import threading
import traceback
import zipfile
import xml.etree.ElementTree as ET
import re

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_STATE = (Path(os.environ.get("LOCALAPPDATA", Path.home())) / "FFLDriverDirect" if os.name == "nt" else ROOT.parent / ".ffl" / "direct-dev")
STATE = Path(os.environ.get("FFL_DEV_STATE_DIR", DEFAULT_STATE)).expanduser().resolve()
IMAGE = "mariadb:10.6.25@sha256:6055d4ee6a6045626442cafe4feaf7419e878c65baf3af658265e24114af1cd7"
MARKER = "ffl-direct-owned-fixture-20261017"
LABEL = "org.ffl.direct.development-owner"


class DevFailure(Exception):
    pass


def save(path: Path, value: str):
    path.write_text(value, encoding="utf-8")
    path.chmod(0o600)


def run(args, *, data=None, timeout=120, check=True):
    save(STATE / "private-last-operation.json", json.dumps({"argv": [str(x) for x in args], "started_unix": time.time()}))
    result = subprocess.run([str(x) for x in args], input=data, capture_output=True, timeout=timeout)
    if check and result.returncode:
        with (STATE / "private-command-errors.log").open("ab") as log:
            log.write(result.stdout + result.stderr)
        raise DevFailure("Command failed; diagnostics are in the private state directory.")
    return result


def load():
    if not (STATE / "owner.json").exists():
        raise DevFailure("Run up first.")
    return json.loads((STATE / "owner.json").read_text(encoding="utf-8"))


def persist(owner):
    save(STATE / "owner.json", json.dumps(owner, indent=2))


def client_grants(database):
    db=quoted(database)
    tables=["__hr_typepos","reys","reys_den","meneger_contr_ffl","meneger_ffl","phone","nastroiki"]
    return "REVOKE ALL PRIVILEGES,GRANT OPTION FROM 'fixture_read'@'%'; " + " ".join(
        f"GRANT SELECT ON {db}.{quoted(table)} TO 'fixture_read'@'%';" for table in tables) + (
        f" GRANT EXECUTE ON FUNCTION {db}.name_v TO 'fixture_read'@'%';"
        f" GRANT UPDATE(st_vv,date_att_log) ON {db}.reys TO 'fixture_read'@'%';"
        f" GRANT UPDATE(st_vv,date_att_log) ON {db}.reys_den TO 'fixture_read'@'%';")


def harden_grants(owner):
    inspect(owner)
    config=json.loads((STATE/"android-config.json").read_text())
    name="ffl_audit_"+owner["id"].replace("-","")[:8]
    path=STATE/"integrity-config.json"
    if path.exists():
        audit=json.loads(path.read_text())
        if audit.get("owner")!=owner["id"] or audit.get("user")!=name:
            raise DevFailure("Unexpected private auditor configuration; refusing to replace it.")
    else:
        present=sql(owner,f"SELECT COUNT(*) FROM mysql.user WHERE User='{name}' AND Host='%';").stdout.strip()
        if present!=b"0":
            raise DevFailure("Auditor account collision; refusing to alter an unknown account.")
        audit={key:config[key] for key in ["fixtureMarker","host","port","database","caPem"]}
        audit.update(user=name,password=secrets.token_hex(24),owner=owner["id"],purpose="integrity-audit")
        save(path,json.dumps(audit))
    db=quoted(owner["database"])
    sql(owner,client_grants(owner["database"])+f" CREATE USER IF NOT EXISTS '{name}'@'%' IDENTIFIED BY '{audit['password']}';"
        f" REVOKE ALL PRIVILEGES,GRANT OPTION FROM '{name}'@'%'; GRANT SELECT ON {db}.* TO '{name}'@'%';"
        f" ALTER USER '{name}'@'%' REQUIRE SSL; ALTER USER 'fixture_read'@'%' REQUIRE SSL;")
    return {"application_select_tables":7,"application_column_updates_only":True,"separate_read_only_integrity_account":True,"auditor_not_compiled_into_apk":True}


def inspect(owner, kind="container"):
    name = owner["container" if kind == "container" else "volume"]
    args = ["docker", "inspect"] if kind == "container" else ["docker", "volume", "inspect"]
    result = run(args + [name], check=False)
    if result.returncode:
        return None
    value = json.loads(result.stdout)[0]
    labels = value["Config"].get("Labels", {}) if kind == "container" else value.get("Labels", {})
    if labels.get(LABEL) != owner["id"]:
        raise DevFailure("Ownership mismatch; refusing to touch an existing Docker resource.")
    return value


def sql(owner, text, *, reader=False, check=True):
    # Password stays inside the owned container's environment, never in argv.
    user, variable = ("fixture_read", "MARIADB_PASSWORD") if reader else ("root", "MARIADB_ROOT_PASSWORD")
    command = f'MYSQL_PWD="${variable}" exec mariadb -u{user} --batch --skip-column-names --default-character-set=utf8mb4'
    if reader:
        command += " --protocol=TCP --host=localhost --ssl-ca=/tls/ca.pem --ssl-verify-server-cert"
    else:
        command += " --protocol=socket"
    return run(["docker", "exec", "-i", owner["container"], "sh", "-c", command], data=text.encode(), check=check)


def certs():
    directory = STATE / "tls"
    directory.mkdir(mode=0o700, exist_ok=True)
    openssl = shutil.which("openssl")
    if not openssl and os.name == "nt":
        candidate = Path(os.environ.get("ProgramFiles", "C:/Program Files")) / "Git/usr/bin/openssl.exe"
        if candidate.exists():
            openssl = str(candidate)
    if not openssl:
        raise DevFailure("OpenSSL is required (Git for Windows includes it).")
    if not (directory / "server.pem").exists():
        run([openssl, "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-keyout", directory / "ca.key", "-out", directory / "ca.pem", "-days", "3650", "-subj", "/CN=FFL local development CA", "-addext", "basicConstraints=critical,CA:TRUE"])
        run([openssl, "req", "-new", "-newkey", "rsa:2048", "-nodes", "-keyout", directory / "server.key", "-out", directory / "server.csr", "-subj", "/CN=localhost"])
        save(directory / "server.ext", "subjectAltName=DNS:localhost\nbasicConstraints=critical,CA:FALSE\nextendedKeyUsage=serverAuth\n")
        run([openssl, "x509", "-req", "-in", directory / "server.csr", "-CA", directory / "ca.pem", "-CAkey", directory / "ca.key", "-CAcreateserial", "-out", directory / "server.pem", "-days", "3650", "-extfile", directory / "server.ext"])
    for path in directory.iterdir():
        path.chmod(0o600)


def dump_stream(path):
    """Streams imports; no extraction to source tree and no SQL text in public logs."""
    if path.suffix.lower() == ".gz":
        return gzip.open(path, "rb"), None
    if path.suffix.lower() == ".zip":
        archive = zipfile.ZipFile(path)
        entries = [x for x in archive.infolist() if not x.is_dir() and x.filename.lower().endswith(".sql")]
        if len(entries) != 1:
            archive.close()
            raise DevFailure("ZIP must contain exactly one .sql file.")
        return archive.open(entries[0]), archive
    return path.open("rb"), None


def restore(owner, path):
    stream, archive = dump_stream(path)
    command = ["docker", "exec", "-i", owner["container"], "sh", "-c", 'MYSQL_PWD="$MARIADB_ROOT_PASSWORD" exec mariadb -uroot --protocol=socket --default-character-set=utf8mb4 ffl_bootstrap']
    total = 0
    digest = hashlib.sha256()
    try:
        with stream, (STATE / "private-import.log").open("wb") as log:
            child = subprocess.Popen(command, stdin=subprocess.PIPE, stdout=log, stderr=log)
            try:
                while chunk := stream.read(1024 * 1024):
                    total += len(chunk)
                    digest.update(chunk)
                    if total > 8 * 1024**3:
                        raise DevFailure("Import exceeds the 8 GiB uncompressed limit.")
                    child.stdin.write(chunk)
                child.stdin.close()
                if child.wait(timeout=1800):
                    raise DevFailure("Import failed; inspect only the private import log.")
            except BaseException:
                child.kill()
                child.wait()
                raise
    finally:
        if archive:
            archive.close()
    databases = sql(owner, "SHOW DATABASES;").stdout.decode().splitlines()
    databases = [x for x in databases if x not in {"information_schema", "performance_schema", "mysql", "sys", "ffl_bootstrap"}]
    if len(databases) != 1:
        raise DevFailure("Import must produce one non-system database; automatic selection is unsafe.")
    save(STATE/"restored-source.json",json.dumps({"sql_sha256":digest.hexdigest(),"sql_bytes":total,
        "input_sha256":owner["profile"].removeprefix("dump:"),"complete_import_exit":0},indent=2))
    return databases[0]


def quoted(identifier):
    return "`" + identifier.replace("`", "``") + "`"


def localize_routines(owner, database):
    """Rebind only this restored test copy's routines; preserve body/mode/charset."""
    db = quoted(database)
    routines = sql(owner, f"USE {db}; SELECT ROUTINE_NAME,ROUTINE_TYPE FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA=DATABASE() ORDER BY ROUTINE_NAME;").stdout.decode().splitlines()
    routines += sql(owner, f"USE {db}; SELECT TRIGGER_NAME,'TRIGGER' FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE() ORDER BY TRIGGER_NAME;").stdout.decode().splitlines()
    records = []
    directory = STATE / "private-routine-definitions"
    directory.mkdir(mode=0o700, exist_ok=True)
    for number, row in enumerate(routines):
        name, kind = row.split("\t")
        if kind not in {"FUNCTION", "PROCEDURE", "TRIGGER"}:
            raise DevFailure("Unsupported routine type.")
        command = ["docker", "exec", "-i", owner["container"], "sh", "-c", 'MYSQL_PWD="$MARIADB_ROOT_PASSWORD" exec mariadb -uroot --protocol=socket --default-character-set=utf8mb4 --xml']
        def definition():
            data = run(command, data=f"SHOW CREATE {kind} {db}.{quoted(name)};".encode()).stdout
            tree = ET.fromstring(data)
            return {field.get("name"): field.text or "" for field in tree.findall("row/field")}
        fields = definition()
        ddl_field = "SQL Original Statement" if kind == "TRIGGER" else "Create " + kind.title()
        create = fields[ddl_field]
        if not (directory / f"{number}-original.sql").exists():
            save(directory / f"{number}-original.sql", create)
        pattern = r"\bDEFINER\s*=\s*`(?:[^`]|``)*`@`(?:[^`]|``)*`"
        rewritten, count = re.subn(pattern, "DEFINER=CURRENT_USER", create, count=1, flags=re.I)
        if count != 1:
            raise DevFailure("Unexpected SHOW CREATE definer format; original definition preserved privately.")
        mode = fields.get("sql_mode", "")
        charset = fields.get("character_set_client", "utf8mb4")
        collation = fields.get("collation_connection", "utf8mb4_general_ci")
        if not re.fullmatch(r"[A-Za-z0-9_,]*", mode) or not re.fullmatch(r"[A-Za-z0-9_]+", charset + collation):
            raise DevFailure("Unexpected routine creation metadata.")
        # MariaDB's command-line client truncates delimiters longer than 15 bytes.
        delimiter = "FFL_" + secrets.token_hex(4)
        if delimiter in rewritten:
            raise DevFailure("Unexpected delimiter collision.")
        sql(owner, f"USE {db}; SET SESSION sql_mode='{mode}'; SET NAMES {charset} COLLATE {collation}; DROP {kind} {quoted(name)};\nDELIMITER {delimiter}\n{rewritten}{delimiter}\nDELIMITER ;\n")
        effective = definition()[ddl_field]
        normalized = lambda text: re.sub(pattern, "DEFINER=LOCAL_OWNER", text, count=1, flags=re.I)
        if normalized(create) != normalized(effective):
            raise DevFailure("Routine changed beyond its local definer; inspect private definitions.")
        records.append({"routine": name, "kind": kind, "body_and_attributes_unchanged": True, "definition_without_definer_sha256": hashlib.sha256(normalized(create).encode()).hexdigest()})
    save(STATE / "routine-localization.json", json.dumps({"owned_test_copy_only": True, "source_dump_unchanged": True, "routines": records}, indent=2))
    return records


def ready(owner):
    deadline = time.monotonic() + 100
    while time.monotonic() < deadline:
        result = sql(owner, "SELECT 1;", check=False)
        if result.returncode == 0 and result.stdout.strip() == b"1":
            return
        time.sleep(1)
    raise DevFailure("MariaDB did not pass SELECT 1; inspect owned container logs privately.")


def up(args):
    if not 1 <= args.port <= 65535:
        raise DevFailure("Port must be between 1 and 65535.")
    if STATE.exists() and not (STATE / "owner.json").exists() and any(STATE.iterdir()):
        raise DevFailure("State directory is not empty and is not owned by this helper; choose a dedicated private directory.")
    STATE.mkdir(parents=True, mode=0o700, exist_ok=True)
    STATE.chmod(0o700)
    profile = "synthetic"
    if args.dump:
        with args.dump.open("rb") as source:
            profile = "dump:" + hashlib.file_digest(source, "sha256").hexdigest()
    if (STATE / "owner.json").exists():
        owner = load()
        if owner["profile"] != profile:
            raise DevFailure("Existing volume uses another profile. Preserve it or explicitly destroy only this owned development DB first.")
        if owner["port"] != args.port:
            raise DevFailure("Existing development state uses another port; use start or its original port.")
    else:
        certs()
        identity = secrets.token_hex(16)
        owner = {"id": identity, "container": "ffl-direct-dev-" + identity[:8], "volume": "ffl-direct-dev-" + identity[:8], "profile": profile, "port": args.port, "initialized": False}
        save(STATE / "container.env", "MARIADB_ROOT_PASSWORD=" + secrets.token_hex(24) + "\nMARIADB_PASSWORD=" + secrets.token_hex(24) + "\nMARIADB_DATABASE=ffl_bootstrap\nMARIADB_USER=fixture_read\nMARIADB_ROOT_HOST=localhost\n")
        persist(owner)
    if run(["docker", "image", "inspect", IMAGE], check=False).returncode:
        run(["docker", "pull", IMAGE], timeout=300)
    container = inspect(owner)
    if container is None:
        if inspect(owner, "volume") is not None:
            raise DevFailure("Volume exists without its container; preserve it rather than reinitialize automatically.")
        # Docker resources may not survive a cloud snapshot even when helper files do.
        # With no volume there is no DB to overwrite. Re-seed or re-import the supplied file.
        owner.update(initialized=False, initializing=False)
        persist(owner)
        stale_scope = STATE / "restored-test-scope.json"
        if stale_scope.exists():
            if json.loads(stale_scope.read_text()).get("owner") != owner["id"]:
                raise DevFailure("Stale test scope has a different owner.")
            stale_scope.unlink()  # No Docker volume survived; its fictional rows must be re-seeded.
        run(["docker", "volume", "create", "--label", f"{LABEL}={owner['id']}", owner["volume"]])
        run(["docker", "create", "--name", owner["container"], "--label", f"{LABEL}={owner['id']}", "--publish", f"127.0.0.1:{owner['port']}:3306", "--mount", f"type=volume,source={owner['volume']},target=/var/lib/mysql", "--env-file", STATE / "container.env", "--entrypoint", "/bin/bash", IMAGE, "-c", "chown -R mysql:mysql /tls; exec /usr/local/bin/docker-entrypoint.sh mariadbd --ssl-ca=/tls/ca.pem --ssl-cert=/tls/server.pem --ssl-key=/tls/server.key --require-secure-transport=ON --event-scheduler=OFF --local-infile=OFF --secure-file-priv=/tmp"])
        # Copy only server key/certificate and public CA, never CA private key.
        staging = STATE / "server-tls"
        staging.mkdir(mode=0o700, exist_ok=True)
        for name in ["server.key", "server.pem", "ca.pem"]:
            shutil.copyfile(STATE / "tls" / name, staging / name)
        run(["docker", "cp", str(staging), owner["container"] + ":/tls"])
    run(["docker", "start", owner["container"]])
    ready(owner)
    if not owner["initialized"]:
        if owner.get("initializing"):
            raise DevFailure("Previous import was interrupted; inspect private diagnostics and explicitly reset only the owned test volume.")
        owner["initializing"] = True
        persist(owner)
        if args.dump:
            database = restore(owner, args.dump)
            localize_routines(owner, database)
        else:
            seed = (ROOT / "tools/fixtures/completion_mysql.sql").read_text(encoding="utf-8")
            sql(owner, "CREATE DATABASE fixture CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;\n" + seed)
            database = "fixture"
        db = quoted(database)
        # Same restricted client privileges for both profiles, never reuse dump credentials.
        sql(owner, client_grants(database))
        values = dict(line.split("=", 1) for line in (STATE / "container.env").read_text().splitlines())
        config = {"fixtureMarker": MARKER if not args.dump else "ffl-direct-private-restored-dump", "host": "localhost", "port": str(owner["port"]), "database": database, "user": "fixture_read", "password": values["MARIADB_PASSWORD"], "caPem": (STATE / "tls/ca.pem").read_text()}
        save(STATE / "android-config.json", json.dumps(config))
        owner.update(initialized=True, initializing=False, database=database)
        persist(owner)
        harden_grants(owner)
    return status(owner)


def status(owner):
    container = inspect(owner)
    if not container or not container["State"]["Running"]:
        raise DevFailure("Owned development DB is stopped; use up.")
    if not owner["initialized"]:
        raise DevFailure("DB initialization incomplete.")
    db = quoted(owner["database"])
    result = sql(owner, f"SELECT 1; SELECT VERSION(); SELECT @@require_secure_transport; SELECT COUNT(*) FROM {db}.__hr_typepos; SELECT COUNT(*) FROM {db}.reys; SELECT COUNT(*) FROM {db}.reys_den;", reader=True)
    rows = result.stdout.decode().splitlines()
    if len(rows) != 6 or rows[0] != "1" or rows[2] != "1":
        raise DevFailure("TLS/SQL health checks failed.")
    engine_rows = sql(owner, f"USE {db}; SELECT TABLE_NAME,ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('reys','reys_den');", reader=True).stdout.decode().splitlines()
    engines = dict(row.split("\t", 1) for row in engine_rows)
    return {"state": "ready", "profile": "synthetic" if owner["profile"] == "synthetic" else "private-restored-dump", "server_version": rows[1], "tls_required_and_hostname_verified": True, "table_row_counts": {"drivers": int(rows[3]), "reys": int(rows[4]), "reys_den": int(rows[5])}, "delivery_storage_engines": engines, "innodb_delivery_tables": engines == {"reys": "InnoDB", "reys_den": "InnoDB"}, "port": owner["port"], "image": IMAGE}


def prepare_restored(owner):
    if not owner["profile"].startswith("dump:"):
        raise DevFailure("This action requires an owned restored dump profile.")
    status(owner)
    scope_file = STATE / "restored-test-scope.json"
    if not scope_file.exists():
        for table, key in [("__hr_typepos", "posTypeId"), ("reys", "id"), ("reys_den", "id"), ("meneger_contr_ffl", "id"), ("meneger_ffl", "id"), ("phone", "id")]:
            count = sql(owner, f"SELECT COUNT(*) FROM {quoted(owner['database'])}.{quoted(table)} WHERE {quoted(key)}>=2000000000;").stdout.strip()
            if count != b"0":
                raise DevFailure("Reserved test ID range already contains data; refusing to overwrite it.")
        seed = (ROOT / "tools/fixtures/restored_e2e.sql").read_text(encoding="utf-8")
        sql(owner, f"USE {quoted(owner['database'])}; SET NAMES utf8mb4;\n" + seed)
        save(scope_file, json.dumps({"owner": owner["id"], "driver": 2000000001, "disabled": 2000000002, "order": 2000000001, "foreign": 2000000003, "notification": 2000000004, "date": "2099-12-31", "password": "ffl-e2e-only-42", "restored_schema": True}, indent=2))
    scope = json.loads(scope_file.read_text())
    if scope["owner"] != owner["id"]:
        raise DevFailure("Test scope owner mismatch.")
    db = quoted(owner["database"])
    count = sql(owner, f"SELECT COUNT(*) FROM {db}.reys WHERE id=2000000020;").stdout.strip()
    if count == b"0":
        sql(owner, f"INSERT INTO {db}.reys(id,nomer_z,id_v,name_c,col,p,vremya,date,status,col_s,sort_r,st_vv,date_att_log,bf_vv) VALUES(2000000020,'FFL-E2E-PAIRED',2000000001,'FFL E2E paired',2,0,'','2000-01-01',2,2,1,0,NULL,2000000010);")
    elif sql(owner, f"SELECT COUNT(*) FROM {db}.reys WHERE id=2000000020 AND id_v=2000000001 AND nomer_z='FFL-E2E-PAIRED';").stdout.strip() != b"1":
        raise DevFailure("Paired test record collision.")
    scope["paired"] = 2000000020
    save(scope_file, json.dumps(scope, indent=2))
    config = json.loads((STATE / "android-config.json").read_text())
    config["testScope"] = scope
    audit_path=STATE/"integrity-config.json"
    if audit_path.exists():
        audit=json.loads(audit_path.read_text())
        if audit.get("owner")!=owner["id"]:
            raise DevFailure("Integrity account owner mismatch.")
        config["verification"]=audit
    save(STATE / "android-config.json", json.dumps(config))
    return {"controlled_test_records_prepared": True, "reserved_ids_checked": True, "original_rows_not_modified": True}


def build(owner, include_release=False):
    status(owner)
    config = json.loads((STATE / "android-config.json").read_text())
    environment = os.environ.copy()
    for name, value in {"HOST": config["host"], "PORT": config["port"], "DATABASE": config["database"], "USER": config["user"], "PASSWORD": config["password"], "USE_SSL": "true", "CA_PEM": config["caPem"]}.items():
        environment["DIRECT_MYSQL_" + name] = value
    environment["FFL_DIRECT_BUILD_DIR"] = str(STATE / "android-build")
    launcher = ["cmd", "/d", "/c", str(ROOT / "gradlew.bat")] if os.name == "nt" else ["bash", str(ROOT / "gradlew")]
    # Different output roots also need separate task-history caches: otherwise Gradle
    # can delete a previous configured build as stale output during a normal build.
    command = launcher + ["--no-daemon", "--max-workers=2", "--project-cache-dir", str(STATE / "gradle-project-cache"), ":app:assembleDebug", ":app:assembleDebugAndroidTest", ":app:verifyDirectRuntime"]
    if include_release:
        command.append(":app:assembleRelease")
    with (STATE / "private-build.log").open("wb") as log:
        result = subprocess.run(command, cwd=ROOT, env=environment, stdout=log, stderr=log)
    if result.returncode:
        raise DevFailure("Configured Android build failed; inspect private-build.log.")
    artifacts = STATE / "artifacts"
    artifacts.mkdir(mode=0o700, exist_ok=True)
    apk = artifacts / "configured-local.apk"
    shutil.copyfile(STATE / "android-build/outputs/apk/debug/app-debug.apk", apk)
    shutil.copyfile(STATE / "android-build/outputs/apk/androidTest/debug/app-debug-androidTest.apk", artifacts / "configured-local-test.apk")
    apk.chmod(0o600)
    (artifacts / "configured-local-test.apk").chmod(0o600)
    if include_release:
        unsigned=artifacts / "configured-local-unsigned-release.apk"
        shutil.copyfile(STATE / "android-build/outputs/apk/release/app-release-unsigned.apk",unsigned)
        unsigned.chmod(0o600)
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    save(STATE / "build.json", json.dumps({"sha256": digest, "apk": str(apk), "exit": 0, "profile": owner["profile"]}, indent=2))
    return {"build": "passed", "configured_local_apk_sha256": digest, "artifact_is_private": True}


def android(owner, serial):
    status(owner)
    build_info = json.loads((STATE / "build.json").read_text())
    if build_info["profile"] != owner["profile"]:
        raise DevFailure("Rebuild the APK for the current test profile.")
    apk = Path(build_info["apk"])
    if hashlib.sha256(apk.read_bytes()).hexdigest() != build_info["sha256"]:
        raise DevFailure("Configured APK changed; rebuild it.")
    base = ["adb", "-s", serial]
    run(base + ["reverse", f"tcp:{owner['port']}", f"tcp:{owner['port']}"])
    run(base + ["install", "-r", "-t", apk], timeout=300)
    run(base + ["install", "-r", "-t", STATE / "artifacts/configured-local-test.apk"], timeout=300)
    run(base + ["shell", "run-as", "ru.ffl24.direct.dev", "mkdir", "-p", "files"])
    run(base + ["shell", "run-as ru.ffl24.direct.dev sh -c 'cat > files/completion-db.json'"], data=(STATE / "android-config.json").read_bytes())
    run(base + ["shell", "am", "start", "-n", "ru.ffl24.direct.dev/ru.ffl24.direct.MainActivity"])
    return {"installed": True, "configured_local_apk_sha256": build_info["sha256"], "launch_requires_render_check": True}


def test_android(owner, serial):
    if owner["profile"] != "synthetic":
        raise DevFailure("Automated write tests are restricted to the owned synthetic profile, never restored personal data.")
    android(owner, serial)
    base = ["adb", "-s", serial]
    run(base + ["shell", "am", "force-stop", "ru.ffl24.direct.dev"])
    run(base + ["shell", "pm", "clear", "ru.ffl24.direct.dev"])
    run(base + ["shell", "run-as", "ru.ffl24.direct.dev", "mkdir", "-p", "files"])
    run(base + ["shell", "run-as ru.ffl24.direct.dev sh -c 'cat > files/completion-db.json'"], data=(STATE / "android-config.json").read_bytes())
    sql(owner, "DELETE FROM fixture.reys_den WHERE id=4; UPDATE fixture.reys SET st_vv=0,date_att_log=NULL WHERE id=1; UPDATE fixture.reys_den SET st_vv=0,date_att_log=NULL WHERE id=1;")
    cases = [
        ("compiled-login", "ru.ffl24.direct.ConfiguredRuntimeTest#freshLoginOrdersDetailsDeliveryAndDirectoryUseCompiledConfiguration"),
        ("cold-restore", "ru.ffl24.direct.ConfiguredRuntimeTest#coldProcessRestoresPersistedSessionAndDateThenLogsOut"),
        ("mysql-notifications", "ru.ffl24.direct.ConfiguredNotificationTest#realWorkManagerPollsMysqlDeduplicatesAndOpensSecondRoute"),
        ("jdbc-transactions", "ru.ffl24.direct.DirectDatabaseFlowTest"),
    ]
    reports = STATE / "test-results"
    if reports.exists():
        history = STATE / "test-history"
        history.mkdir(mode=0o700, exist_ok=True)
        shutil.move(str(reports), str(history / str(time.time_ns())))
    reports.mkdir(mode=0o700, exist_ok=True)
    results = []
    save(reports / "summary.json", json.dumps({"fixture_only": True, "cases": results}, indent=2))
    for label, test_class in cases:
        if label == "cold-restore":
            run(base + ["shell", "am", "force-stop", "ru.ffl24.direct.dev"])
        if label == "mysql-notifications":
            sql(owner, "UPDATE fixture.reys_den SET st_vv=0,date_att_log=NULL WHERE id=1; INSERT INTO fixture.reys_den(id,nomer_z,id_v,name_c,col,vremya,date,status,col_s,sort_r,st_vv,date_att_log) VALUES(4,'Notification-fixture',97,'Fictional notification client',1,'','2026-05-18',2,2,4,1,NOW());")
        if label == "jdbc-transactions":
            sql(owner, "DELETE FROM fixture.reys_den WHERE id=4;")
            sql(owner, "UPDATE fixture.reys SET st_vv=0,date_att_log=NULL WHERE id=1; UPDATE fixture.reys_den SET st_vv=0,date_att_log=NULL WHERE id=1;")
        command = base + ["shell", "am", "instrument", "-w", "-r", "-e", "listener", "ru.ffl24.direct.CompletionRunListener", "-e", "class", test_class, "ru.ffl24.direct.dev.test/androidx.test.runner.AndroidJUnitRunner"]
        run(base + ["shell", "run-as", "ru.ffl24.direct.dev", "rm", "-f", "files/completion-ui/instrumentation.xml"])
        started = time.time()
        result = run(command, timeout=600, check=False)
        (reports / (label + ".log")).write_bytes(result.stdout + result.stderr)
        xml = run(base + ["exec-out", "run-as", "ru.ffl24.direct.dev", "cat", "files/completion-ui/instrumentation.xml"]).stdout
        (reports / (label + ".xml")).write_bytes(xml)
        try:
            parsed = ET.fromstring(xml)
        except ET.ParseError:
            results.append({"label": label, "command": command, "exit": result.returncode, "xml": None, "passed": False, "fresh_junit_xml_missing_or_invalid": True})
            save(reports / "summary.json", json.dumps({"cases": results}, indent=2))
            (reports / (label + ".xml")).rename(reports / (label + "-invalid-junit.txt"))
            raise DevFailure("Instrumentation did not produce fresh valid JUnit XML; raw diagnostics remain private.")
        valid = result.returncode == 0 and parsed.get("tests") == "1" and all(parsed.get(k) == "0" for k in ["failures", "errors", "skipped"]) and b"OK (1 test)" in result.stdout
        case = parsed.find("testcase")
        expected = test_class.split("#", 1)
        valid = valid and case is not None and case.get("classname") == expected[0]
        if len(expected) == 2:
            valid = valid and case.get("name") == expected[1]
        results.append({"label": label, "command": command, "exit": result.returncode, "xml": dict(parsed.attrib), "passed": valid, "started_unix": started, "finished_unix": time.time()})
        save(reports / "summary.json", json.dumps({"fixture_only": True, "cases": results}, indent=2))
        if not valid:
            raise DevFailure("Android case failed or skipped; inspect private test-results, not just am instrument exit code.")
    return {"fixture_only": True, "configured_main_runtime_cases_passed": 4, "failed": 0, "skipped": 0, "cold_process_restore_tested": True, "real_workmanager_mysql_notification_tested": True, "graph_injection_in_compiled_cases": False}


def test_restored(owner, serial, runtime_profile=False):
    prepare_restored(owner)
    scope = json.loads((STATE / "restored-test-scope.json").read_text())
    if scope["driver"] != 2000000001 or scope["order"] != 2000000001:
        raise DevFailure("Unexpected write-test scope.")
    base = ["adb", "-s", serial]
    if runtime_profile:
        # Preserve the profile just applied through the production settings UI.
        # Require the exact public APK instead of installing a compiled-credentials fixture.
        if not serial.startswith("emulator-"):
            raise DevFailure("Runtime tests require an explicitly selected dedicated emulator.")
        apk = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
        installed = run(base+["shell","pm","path","ru.ffl24.direct.dev"]).stdout.decode().strip().removeprefix("package:")
        if hashlib.sha256(run(base+["exec-out","cat",installed]).stdout).digest() != hashlib.sha256(apk.read_bytes()).digest():
            raise DevFailure("Installed runtime APK differs from the public build.")
    else:
        android(owner, serial)
        run(base + ["shell", "am", "force-stop", "ru.ffl24.direct.dev"])
        run(base + ["shell", "pm", "clear", "ru.ffl24.direct.dev"])
    run(base + ["shell", "run-as", "ru.ffl24.direct.dev", "mkdir", "-p", "files"])
    run(base + ["shell", "run-as ru.ffl24.direct.dev sh -c 'cat > files/completion-db.json'"], data=(STATE / "android-config.json").read_bytes())
    db = quoted(owner["database"])
    reset = f"UPDATE {db}.reys SET st_vv=0,date_att_log=NULL WHERE id=2000000001 AND id_v=2000000001; UPDATE {db}.reys_den SET st_vv=0,date_att_log=NULL WHERE id=2000000001 AND id_v=2000000001;"
    sql(owner, reset + f"UPDATE {db}.reys_den SET st_vv=1,date_att_log='2026-05-18 12:00:00' WHERE id=2000000004 AND id_v=2000000001;")
    cases = [
        ("legacy-parity", "ru.ffl24.direct.RestoredDatabaseFlowTest#legacyRecordsMatchSqlDtoOrderAndSummaryWithoutPublishingPersonalData"),
        ("compiled-login", "ru.ffl24.direct.ConfiguredRuntimeTest#freshLoginOrdersDetailsDeliveryAndDirectoryUseCompiledConfiguration"),
        ("cold-restore", "ru.ffl24.direct.ConfiguredRuntimeTest#coldProcessRestoresPersistedSessionAndDateThenLogsOut"),
        ("mysql-refresh", "ru.ffl24.direct.ConfiguredRuntimeTest#centralRefreshReadsActualMysqlAndPreservesDateOrderAndViewport"),
        ("restored-transactions", "ru.ffl24.direct.RestoredDatabaseFlowTest#controlledRecordsOnRestoredSchemaValidateContactsHistoryRollbackAndBothDeliveries"),
        ("trigger-rollback", "ru.ffl24.direct.RestoredDatabaseFlowTest#pairedLegacyTriggerRollsBackAndIdempotentRepeatDoesNotDuplicate"),
        ("audit-concurrency", "ru.ffl24.direct.AuditDatabaseTest#concurrentIndependentConnectionsDeliverEachSourceOnlyOnce"),
        ("audit-access-lock", "ru.ffl24.direct.AuditDatabaseTest#accessRowIsLockedUntilTheDeliveryTransactionCommits"),
        ("audit-connection-loss", "ru.ffl24.direct.AuditDatabaseTest#connectionLossAfterRealUpdateIsUnknownAndServerRollsBack"),
        ("audit-session", "ru.ffl24.direct.AuditDatabaseTest#simultaneousRestoresKeepThePersistentSessionAndLogoutWins"),
        ("audit-revoke", "ru.ffl24.direct.AuditDatabaseTest#operatorRevocationAfterLoginBlocksReadsAndDelivery"),
        ("mysql-notifications", "ru.ffl24.direct.ConfiguredNotificationTest#realWorkManagerPollsMysqlDeduplicatesAndOpensSecondRoute"),
    ]
    reports = STATE / "test-results"
    if reports.exists():
        history = STATE / "test-history"
        history.mkdir(mode=0o700, exist_ok=True)
        shutil.move(str(reports), str(history / str(time.time_ns())))
    reports.mkdir(mode=0o700, exist_ok=True)
    results = []
    save(reports / "summary.json", json.dumps({"restored_dump": True, "cases": results}, indent=2))
    for label, test_class in cases:
        if label == "cold-restore":
            run(base + ["shell", "am", "force-stop", "ru.ffl24.direct.dev"])
        if label == "restored-transactions" or label.startswith("audit-"):
            sql(owner, reset)
        if label == "trigger-rollback":
            # The legacy UPDATE trigger can INSERT an identically named BF row
            # even when resetting to zero. Remove only this reserved derived scope
            # both before and after reset so the suite is repeatable on a volume.
            cleanup=f"DELETE FROM {db}.reys_bf WHERE nomer_z LIKE 'FFL-E2E-PAIRED%';"
            sql(owner, cleanup+f"UPDATE {db}.reys SET st_vv=0,date_att_log=NULL WHERE id=2000000020 AND id_v=2000000001;"+cleanup)
        if label == "mysql-notifications":
            sql(owner, f"UPDATE {db}.reys_den SET st_vv=0,date_att_log=NULL WHERE id=2000000001 AND id_v=2000000001;")
        run(base + ["shell", "run-as", "ru.ffl24.direct.dev", "rm", "-f", "files/completion-ui/instrumentation.xml"])
        command = base + ["shell", "am", "instrument", "-w", "-r", "-e", "listener", "ru.ffl24.direct.CompletionRunListener", "-e", "class", test_class, "ru.ffl24.direct.dev.test/androidx.test.runner.AndroidJUnitRunner"]
        started = time.time()
        if label == "audit-revoke":
            # This host-only operator changes only the reserved fictional auth row.
            # No root credential or auth UPDATE privilege is added to the APK.
            marker="files/restored-private/revoke-ready"
            ack="files/restored-private/revoke-ack"
            run(base+["shell","run-as","ru.ffl24.direct.dev","rm","-f",marker,ack])
            stopped=threading.Event()
            observed=[]
            def revoke():
                try:
                    while not stopped.wait(0.5):
                        ready=run(base+["exec-out","run-as","ru.ffl24.direct.dev","cat",marker],timeout=5,check=False)
                        if ready.returncode==0 and ready.stdout==b"READY":
                            if stopped.is_set(): return
                            sql(owner,f"SET SESSION innodb_lock_wait_timeout=3; UPDATE {db}.__hr_typepos SET access=0 WHERE posTypeId=2000000001;")
                            run(base+["shell",f"run-as ru.ffl24.direct.dev sh -c 'cat > {ack}'"],data=b"ACK")
                            observed.append(True)
                            return
                except Exception:
                    observed.append(False)
            watcher=threading.Thread(target=revoke,daemon=True)
            watcher.start()
            try:
                result=run(command,timeout=600,check=False)
            finally:
                stopped.set();watcher.join(10)
                sql(owner,f"UPDATE {db}.__hr_typepos SET access=1 WHERE posTypeId=2000000001;")
            if observed!=[True]:
                raise DevFailure("Operator revocation was not observed; controlled access restored.")
        else:
            result = run(command, timeout=600, check=False)
        (reports / (label + ".log")).write_bytes(result.stdout + result.stderr)
        xml = run(base + ["exec-out", "run-as", "ru.ffl24.direct.dev", "cat", "files/completion-ui/instrumentation.xml"]).stdout
        (reports / (label + ".xml")).write_bytes(xml)
        try:
            parsed = ET.fromstring(xml)
        except ET.ParseError:
            results.append({"label": label, "command": command, "exit": result.returncode, "xml": None, "passed": False, "fresh_junit_xml_missing_or_invalid": True})
            save(reports / "summary.json", json.dumps({"restored_dump": True, "cases": results}, indent=2))
            (reports / (label + ".xml")).rename(reports / (label + "-invalid-junit.txt"))
            raise DevFailure("Instrumentation did not produce fresh valid JUnit XML; raw diagnostics remain private.")
        case = parsed.find("testcase")
        expected_class, expected_method = test_class.split("#")
        valid = result.returncode == 0 and parsed.get("tests") == "1" and all(parsed.get(k) == "0" for k in ["failures", "errors", "skipped"]) and b"OK (1 test)" in result.stdout and case is not None and case.get("classname") == expected_class and case.get("name") == expected_method
        results.append({"label": label, "command": command, "exit": result.returncode, "xml": dict(parsed.attrib), "passed": valid, "started_unix": started, "finished_unix": time.time()})
        save(reports / "summary.json", json.dumps({"restored_dump": True, "original_rows_write_target": False, "cases": results}, indent=2))
        if not valid:
            raise DevFailure("Restored DB Android case failed or skipped; raw diagnostics remain private.")
    proof = run(base + ["exec-out", "run-as", "ru.ffl24.direct.dev", "cat", "files/restored-private/legacy-read-proof.json"]).stdout
    parsed = json.loads(proof)
    if parsed.get("personal_values_published") is not False or parsed.get("writes_to_original_records") is not False:
        raise DevFailure("Unexpected original-record evidence.")
    save(reports / "legacy-read-proof.json", json.dumps(parsed, indent=2))
    # Preserve the controlled runtime screenshot privately before a later R8 run clears app data.
    image = run(base + ["exec-out", "run-as", "ru.ffl24.direct.dev", "cat", "files/restored-private/controlled-second-route.png"]).stdout
    if not image.startswith(b"\x89PNG\r\n\x1a\n"):
        raise DevFailure("Controlled runtime screenshot missing.")
    (reports / "controlled-second-route.png").write_bytes(image)
    (reports / "controlled-second-route.png").chmod(0o600)
    return {"restored_dump": True, "android_cases_passed": len(cases), "failed": 0, "skipped": 0, "real_original_data_read_parity": True, "writes_only_to_reserved_records": True, "cold_process_restore": True, "real_worker_notification_tap": True, "paired_legacy_trigger_rollback": True, "independent_connection_concurrency": True, "transaction_access_lock": True, "real_connection_loss_rollback": True}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="action", required=True)
    start = sub.add_parser("up")
    start.add_argument("--dump", type=Path)
    start.add_argument("--port", type=int, default=19065)
    sub.add_parser("status")
    sub.add_parser("start")
    sub.add_parser("stop")
    build_parser=sub.add_parser("build")
    build_parser.add_argument("--include-release",action="store_true")
    sub.add_parser("localize-routines")
    sub.add_parser("prepare-restored")
    sub.add_parser("harden-grants")
    install = sub.add_parser("android")
    install.add_argument("--serial", required=True)
    tests = sub.add_parser("test")
    tests.add_argument("--serial", required=True)
    restored_tests = sub.add_parser("test-restored")
    restored_tests.add_argument("--serial", required=True)
    restored_tests.add_argument("--runtime-profile", action="store_true", help="Use the exact public APK and its already saved runtime profile; do not clear app data")
    destroy = sub.add_parser("destroy")
    destroy.add_argument("--confirm-owned-data-loss", action="store_true", required=True)
    args = parser.parse_args()
    if STATE == ROOT or STATE in ROOT.parents or ROOT in STATE.parents:
        raise DevFailure("Private state must be outside the checkout and must not contain it.")
    if args.action == "up":
        result = up(args)
    else:
        owner = load()
        if args.action == "status":
            result = status(owner)
        elif args.action == "build":
            result = build(owner,args.include_release)
        elif args.action == "localize-routines":
            inspect(owner)
            records = localize_routines(owner, owner["database"])
            sql(owner, f"GRANT EXECUTE ON FUNCTION {quoted(owner['database'])}.name_v TO 'fixture_read'@'%';")
            result = {"local_routines_rebound": len(records), "bodies_and_attributes_unchanged": True, "source_dump_unchanged": True}
        elif args.action == "prepare-restored":
            result = prepare_restored(owner)
        elif args.action == "harden-grants":
            result = harden_grants(owner)
        elif args.action == "android":
            result = android(owner, args.serial)
        elif args.action == "test":
            result = test_android(owner, args.serial)
        elif args.action == "test-restored":
            try:
                result = test_restored(owner, args.serial, args.runtime_profile)
            finally:
                # Verification credentials are injected only for this owned test run.
                # Production keeps its configuration solely in the Keystore-encrypted profile.
                cleanup = subprocess.run(["adb", "-s", args.serial, "shell", "run-as", "ru.ffl24.direct.dev",
                    "rm", "-f", "files/completion-db.json"], capture_output=True, timeout=15)
                if cleanup.returncode:
                    raise DevFailure("Private test configuration cleanup failed; inspect the dedicated emulator.")
        elif args.action == "start":
            if not inspect(owner):
                if owner["profile"] != "synthetic":
                    raise DevFailure("Restored DB container is missing; recover its volume or re-import the same private dump.")
                result = up(argparse.Namespace(dump=None, port=owner["port"]))
            else:
                run(["docker", "start", owner["container"]])
                ready(owner)
                result = status(owner)
        elif args.action == "stop":
            if inspect(owner):
                run(["docker", "stop", owner["container"]])
            result = {"stopped": True, "data_preserved": True}
        else:
            if inspect(owner):
                run(["docker", "rm", "-f", owner["container"]])
            if inspect(owner, "volume"):
                run(["docker", "volume", "rm", owner["volume"]])
            shutil.rmtree(STATE)
            result = {"owned_development_data_removed": True}
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    try:
        main()
    except (DevFailure, OSError, subprocess.SubprocessError, ValueError, ET.ParseError) as failure:
        if (STATE / "owner.json").exists() and STATE != ROOT and STATE not in ROOT.parents and ROOT not in STATE.parents:
            save(STATE / "private-exception.log", traceback.format_exc())
        # DB/import exceptions may contain PII. Do not print their raw representation.
        print(str(failure) if isinstance(failure, DevFailure) else "Operation failed; inspect private diagnostics.", file=sys.stderr)
        raise SystemExit(1)
