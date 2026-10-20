"""Sign a PRIVATE configured R8 APK with a local debug key and test its ordinary UI.

First run dev_database.py build --include-release for the restored owned profile.
No production signing key or password is accepted. APK/logs remain in private state.
"""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import shutil
import time
import xml.etree.ElementTree as ET
import zipfile
import dev_database as dev

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--serial",required=True)
    p.add_argument("--debug-keystore",type=Path,required=True)
    p.add_argument("--output",type=Path,default=dev.ROOT/"docs/evidence/final-audit/android-minified.json")
    args=p.parse_args()
    if not args.serial.startswith("emulator-") or args.debug_keystore.name!="debug.keystore":
        p.error("Only a dedicated emulator and a local debug.keystore are allowed")
    owner=dev.load();dev.inspect(owner)
    if not owner["profile"].startswith("dump:"):
        raise dev.DevFailure("This audit requires the restored owned test copy")
    scope=json.loads((dev.STATE/"restored-test-scope.json").read_text())
    if scope["driver"]!=2000000001 or scope["order"]!=2000000001:
        raise dev.DevFailure("Unexpected test scope")
    directory=dev.STATE/"minified-test-results"
    if directory.exists():
        history=dev.STATE/"minified-test-history";history.mkdir(mode=0o700,exist_ok=True)
        shutil.move(str(directory),str(history/str(time.time_ns())))
    directory.mkdir(mode=0o700)
    tools=Path(os.environ["ANDROID_HOME"])/"build-tools/36.0.0"
    signer=[str(tools/("apksigner.bat" if os.name=="nt" else "apksigner"))]
    if os.name=="nt": signer=["cmd","/d","/c"]+signer
    source=dev.STATE/"artifacts/configured-local-unsigned-release.apk"
    apk=dev.STATE/"artifacts/configured-local-minified-signed.apk"
    dev.run(signer+["sign","--ks",args.debug_keystore,"--ks-key-alias","androiddebugkey","--ks-pass","pass:android","--key-pass","pass:android","--out",apk,source])
    apk.chmod(0o600)
    dev.run(signer+["verify","--verbose",apk])
    badging=dev.run([tools/("aapt.exe" if os.name=="nt" else "aapt"),"dump","badging",apk]).stdout
    if b"application-debuggable" in badging:
        raise dev.DevFailure("Expected a non-debuggable release target")
    mapping=dev.STATE/"android-build/outputs/mapping/release/mapping.txt"
    match=re.search(r"^ru\.ffl24\.direct\.AppGraph -> (.+):$",mapping.read_text(),re.MULTILINE)
    if not match or match.group(1)=="ru.ffl24.direct.AppGraph":
        raise dev.DevFailure("R8 obfuscation was not confirmed")
    # Debug androidTest excludes Kotlin/AndroidX already in the debug target. Such
    # classes may be renamed by R8, so use an independent framework-only Java APK.
    harness=dev.STATE/"release-ui-harness";harness.mkdir(mode=0o700,exist_ok=True)
    classes=harness/"classes";classes.mkdir(exist_ok=True)
    dex=harness/"dex";dex.mkdir(exist_ok=True)
    sdk=Path(os.environ["ANDROID_HOME"])
    android_jar=sdk/"platforms/android-36.1/android.jar"
    if not android_jar.exists(): android_jar=sdk/"platforms/android-36/android.jar"
    manifest=harness/"AndroidManifest.xml"
    manifest.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="ru.ffl24.audit.smoke" android:versionCode="1" android:versionName="1">
      <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="36"/>
      <application android:label="FFL R8 audit harness" android:debuggable="true" android:allowBackup="false"/>
      <instrumentation android:name="ru.ffl24.audit.ReleaseSmokeInstrumentation" android:targetPackage="ru.ffl24.direct.dev" android:functionalTest="true"/>
    </manifest>''')
    dev.run(["javac","-encoding","UTF-8","--release","8","-classpath",android_jar,"-d",classes,
             dev.ROOT/"tools/fixtures/ReleaseSmokeInstrumentation.java"])
    d8=[str(tools/("d8.bat" if os.name=="nt" else "d8"))]
    if os.name=="nt":d8=["cmd","/d","/c"]+d8
    dev.run(d8+["--min-api","26","--lib",android_jar,"--output",dex]+list(classes.rglob("*.class")))
    unsigned_harness=harness/"unsigned.apk"
    dev.run([tools/("aapt.exe" if os.name=="nt" else "aapt"),"package","-f","-M",manifest,"-I",android_jar,"-F",unsigned_harness])
    with zipfile.ZipFile(unsigned_harness,"a") as z:
        for path in dex.glob("*.dex"):z.write(path,path.name)
    aligned=harness/"aligned.apk"
    dev.run([tools/("zipalign.exe" if os.name=="nt" else "zipalign"),"-f","4",unsigned_harness,aligned])
    harness_apk=harness/"harness.apk"
    dev.run(signer+["sign","--ks",args.debug_keystore,"--ks-key-alias","androiddebugkey","--ks-pass","pass:android","--key-pass","pass:android","--out",harness_apk,aligned])
    base=["adb","-s",args.serial]
    dev.run(base+["reverse",f"tcp:{owner['port']}",f"tcp:{owner['port']}"])
    dev.run(base+["install","-r","-t",apk],timeout=300)
    dev.run(base+["install","-r","-t",harness_apk],timeout=300)
    installed_path=dev.run(base+["shell","pm","path","ru.ffl24.direct.dev"]).stdout.decode().strip().removeprefix("package:")
    installed=directory/"installed-base.apk"
    dev.run(base+["pull",installed_path,installed],timeout=120)
    installed_sha=hashlib.sha256(installed.read_bytes()).hexdigest()
    if installed_sha!=hashlib.sha256(apk.read_bytes()).hexdigest():
        raise dev.DevFailure("Installed minified APK does not match signed artifact")
    dev.run(base+["shell","am","force-stop","ru.ffl24.direct.dev"])
    dev.run(base+["shell","pm","clear","ru.ffl24.direct.dev"])
    db=dev.quoted(owner["database"])
    dev.sql(owner,f"UPDATE {db}.reys SET st_vv=0,date_att_log=NULL WHERE id=2000000001 AND id_v=2000000001;")
    results=[]
    for method in ["loginAndDelivery","coldRestoreAndLogout"]:
        dev.run(base+["shell","am","force-stop","ru.ffl24.direct.dev"])
        command=base+["shell","am","instrument","-w","-r","-e","fflOwnedLocalRelease","true","-e","method",method,
            "ru.ffl24.audit.smoke/ru.ffl24.audit.ReleaseSmokeInstrumentation"]
        started=time.time();result=dev.run(command,timeout=500,check=False)
        (directory/(method+".log")).write_bytes(result.stdout+result.stderr)
        encoded=re.search(rb"INSTRUMENTATION_STATUS: fflAuditJUnitBase64=([A-Za-z0-9+/=]+)",result.stdout)
        if not encoded:
            raise dev.DevFailure("Fresh JUnit report missing from minified instrumentation stream")
        xml=base64.b64decode(encoded.group(1),validate=True)
        (directory/(method+".xml")).write_bytes(xml)
        node=ET.fromstring(xml);case=node.find("testcase")
        valid=result.returncode==0 and node.get("tests")=="1" and all(node.get(k)=="0" for k in ["failures","errors","skipped"])
        valid=valid and case is not None and case.get("name")==method and case.get("classname")=="ru.ffl24.audit.ReleaseSmokeInstrumentation" and b"fflAuditComplete=PASS" in result.stdout
        state=dev.sql(owner,f"SELECT st_vv,IF(date_att_log IS NULL,0,1),status,col FROM {db}.reys WHERE id=2000000001 AND id_v=2000000001;",reader=True).stdout.strip()
        valid=valid and state==b"1\t1\t2\t3"
        results.append({"method":method,"command":command,"exit":result.returncode,"junit":dict(node.attrib),"passed":valid,
                        "sql_delivery_verified":state==b"1\t1\t2\t3","started_unix":started,"finished_unix":time.time()})
        report={"private_configured_r8_apk_sha256":hashlib.sha256(apk.read_bytes()).hexdigest(),"installed_apk_sha256":installed_sha,"apk_debuggable":False,
                "r8_obfuscation_confirmed":True,"debug_signed_for_internal_test_only":True,"signature_verified":True,
                "cases":results,"passed":len(results)==2 and all(x["passed"] for x in results),"raw_logs_private":True,
                "personal_rows_written":False,"production_accessed":False}
        args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(json.dumps(report,indent=2))
        if not valid: raise dev.DevFailure("Minified runtime case failed; diagnostics are private")
    print(json.dumps(report,indent=2))

if __name__=="__main__":
    main()
