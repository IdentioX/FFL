"""Run the credentials-free Android regression on a dedicated emulator.

Start tools/p2_tls_fixture.py separately and adb reverse ports 19061..19064 first.
The fixture CA asset must match its private external TLS state. No restored rows
are read by these 29 selected tests. Database E2E has a separate guarded runner.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
CLASSES={"StartupTest":4,"OrderDetailsUiTest":6,"WorkspaceNavigationTest":1,
         "WorkspaceUiTest":8,"data.mysql.ConnectionLayerTest":7,"AuditLayoutTest":2,"AuditPreferencesTest":1}

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial",required=True)
    parser.add_argument("--output",type=Path,default=ROOT/"docs/evidence/final-audit/android-ui")
    args=parser.parse_args()
    if not args.serial.startswith("emulator-"):
        parser.error("This runner clears app data; use a dedicated emulator, never a personal phone.")
    output=args.output
    output.mkdir(parents=True,exist_ok=False)
    base=["adb","-s",args.serial]
    def run(argv,timeout=60,check=True):
        result=subprocess.run(base+argv,capture_output=True,timeout=timeout)
        if check and result.returncode:
            (output/"command-failure.log").write_bytes(result.stdout+result.stderr)
            raise RuntimeError("Android command failed; inspect the output directory")
        return result
    apk=ROOT/"app/build/outputs/apk/debug/app-debug.apk"
    expected_hash=hashlib.sha256(apk.read_bytes()).hexdigest()
    for path in [apk,ROOT/"app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"]:
        run(["install","-r","-t",str(path)],300)
    installed_path=run(["shell","pm","path","ru.ffl24.direct.dev"]).stdout.decode().strip().removeprefix("package:")
    installed_hash=hashlib.sha256(run(["exec-out","cat",installed_path]).stdout).hexdigest()
    if installed_hash!=expected_hash:
        raise RuntimeError("Installed APK does not match the selected build")
    run(["shell","am","force-stop","ru.ffl24.direct.dev"])
    run(["shell","pm","clear","ru.ffl24.direct.dev"])
    run(["shell","input","keyevent","KEYCODE_WAKEUP"])
    selected=",".join("ru.ffl24.direct."+c for c in CLASSES)
    command=["shell","am","instrument","-w","-r","-e","listener","ru.ffl24.direct.CompletionRunListener",
             "-e","class",selected,"ru.ffl24.direct.dev.test/androidx.test.runner.AndroidJUnitRunner"]
    started=time.time();result=run(command,900,False)
    (output/"instrumentation.log").write_bytes(result.stdout+result.stderr)
    xml=run(["exec-out","run-as","ru.ffl24.direct.dev","cat","files/completion-ui/instrumentation.xml"]).stdout
    (output/"instrumentation.xml").write_bytes(xml)
    node=ET.fromstring(xml)
    cases=node.findall("testcase")
    expected=sum(CLASSES.values())
    actual={c:sum(x.get("classname")=="ru.ffl24.direct."+c for x in cases) for c in CLASSES}
    valid=result.returncode==0 and node.get("tests")==str(expected) and all(node.get(k)=="0" for k in ["failures","errors","skipped"])
    valid=valid and actual==CLASSES and f"OK ({expected} tests)".encode() in result.stdout
    for folder,names in {"completion-ui":["orders-first.png","orders-second.png","orders-delivered.png","directory.png"],
                         "audit-ui":["small-light.png","small-dark-large.png"]}.items():
        for name in names:
            image=run(["exec-out","run-as","ru.ffl24.direct.dev","cat",f"files/{folder}/{name}"],check=False)
            if image.returncode==0 and image.stdout.startswith(b"\x89PNG\r\n\x1a\n"):
                (output/name).write_bytes(image.stdout)
    report={"command":base+command,"exit":result.returncode,"junit":dict(node.attrib),"class_counts":actual,
            "passed":valid,"apk_sha256":expected_hash,"installed_apk_sha256":installed_hash,
            "started_unix":started,"finished_unix":time.time(),"real_database_e2e":False}
    (output/"summary.json").write_text(json.dumps(report,indent=2))
    print(json.dumps(report,indent=2))
    if not valid:
        raise SystemExit(1)

if __name__=="__main__":
    main()
