"""Read-only whole-dump row fingerprints. Never writes or prints personal values."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import ssl
import sys

import pymysql

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--state", type=Path, required=True)
mode=parser.add_mutually_exclusive_group(required=True)
mode.add_argument("--baseline", type=Path)
mode.add_argument("--create-baseline",action="store_true")
parser.add_argument("--output", type=Path, required=True)
args = parser.parse_args()


def main():
    auditor=args.state/"integrity-config.json"
    config = json.loads((auditor if auditor.exists() else args.state / "android-config.json").read_text())
    owner = json.loads((args.state / "owner.json").read_text())
    if config.get("fixtureMarker") != "ffl-direct-private-restored-dump" or config["host"] != "localhost" or int(config["port"]) != owner["port"] or config["database"] != owner["database"]:
        raise ValueError("Only an owned local restored profile is supported")
    baseline = json.loads(args.baseline.read_text()) if args.baseline else None
    if args.create_baseline and (args.state/"restored-test-scope.json").exists():
        raise ValueError("Baseline creation requires an independent unseeded restore")
    keyed = {"__hr_typepos": "posTypeId", "reys": "id", "reys_den": "id", "meneger_contr_ffl": "id", "meneger_ffl": "id", "phone": "id"}
    connection = pymysql.connect(host="localhost", port=owner["port"], database=config["database"],
        user=config["user"], password=config["password"], charset="utf8mb4",
        ssl=ssl.create_default_context(cafile=str(args.state / "tls/ca.pem")), cursorclass=pymysql.cursors.SSCursor)
    current = {}
    with connection:
        with connection.cursor() as query:
            query.execute("SET time_zone='+00:00'")
            query.execute("SHOW TABLES")
            tables = [r[0] for r in query.fetchall()]
            if args.create_baseline:
                for table,key in keyed.items():
                    query.execute(f"SELECT COUNT(*) FROM `{table}` WHERE `{key}`>=2000000000")
                    if query.fetchall()[0][0]!=0: raise ValueError("Reserved IDs exist in baseline source")
                for table in ["reys_log","reys_bf"]:
                    query.execute(f"SELECT COUNT(*) FROM `{table}` WHERE nomer_z LIKE 'FFL-E2E-%'")
                    if query.fetchall()[0][0]!=0: raise ValueError("Reserved prefix exists in baseline source")
            for table in tables:
                quoted = "`" + table.replace("`", "``") + "`"
                where = ""
                if not args.create_baseline and table in keyed:
                    where = " WHERE `" + keyed[table] + "`<2000000000"
                elif not args.create_baseline and table in {"reys_bf", "reys_log"}:
                    where = " WHERE nomer_z IS NULL OR nomer_z NOT LIKE 'FFL-E2E-%'"
                query.execute("SELECT * FROM " + quoted + where)
                hashes = []
                for row in query:
                    encoded = json.dumps(row, default=str, ensure_ascii=False, separators=(",", ":")).encode()
                    hashes.append(hashlib.sha256(encoded).digest())
                current[table] = {"rows": len(hashes), "unordered_rows_sha256": hashlib.sha256(b"".join(sorted(hashes))).hexdigest()}
    if args.create_baseline:
        imported=json.loads((args.state/"restored-source.json").read_text())
        report={"source_sql_sha256":imported["sql_sha256"],"tables":current,"independent_clean_restore":True}
        args.output.write_text(json.dumps(report,indent=2))
        print(json.dumps({"clean_restore_baseline_created":True,"tables":len(current),"rows":sum(x["rows"] for x in current.values()),"source_sql_sha256":imported["sql_sha256"]}))
        return 0
    changed = sorted(k for k in baseline["tables"].keys() | current.keys() if baseline["tables"].get(k) != current.get(k))
    report = {"original_tables_checked": len(current), "baseline_rows": sum(x["rows"] for x in baseline["tables"].values()),
        "all_original_records_unchanged": not changed, "changed_original_tables": changed,
        "excluded_only_reserved_test_ids_and_prefix": True, "plaintext_rows_published": False,
        "source_sql_sha256": baseline["source_sql_sha256"], "current_original_rows_fingerprints": current}
    args.output.write_text(json.dumps(report, indent=2))
    print(json.dumps({k: v for k, v in report.items() if k != "current_original_rows_fingerprints"}, indent=2))
    return 1 if changed else 0


try:
    sys.exit(main())
except Exception:
    print("Integrity check failed; no personal values were printed.", file=sys.stderr)
    sys.exit(1)
