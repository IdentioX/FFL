"""Dedicated-emulator runtime configuration checks; secrets stay in private test state.

Requires two already-owned, running local MariaDB fixtures and current credentials-free
app-debug / androidTest APKs. Clears this app only on the explicitly named emulator.
"""
import argparse
import atexit
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
APP = 'ru.ffl24.direct.dev'
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', required=True)
parser.add_argument('--synthetic-state', type=Path, required=True)
parser.add_argument('--restored-state', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--checks', choices=('all','live-settings'), default='all')
args = parser.parse_args()
if not args.serial.startswith('emulator-'):
    parser.error('Only an explicitly selected dedicated emulator is supported')
output = args.output.resolve()
if output == ROOT or ROOT in output.parents or output in ROOT.parents:
    parser.error('Raw test evidence must stay outside the source tree')
output.mkdir(mode=0o700, exist_ok=False, parents=True)
base = ['adb', '-s', args.serial]
def adb(cmd, data=None, check=True):
    result = subprocess.run(base+cmd, input=data, capture_output=True, timeout=300)
    if check and result.returncode:
        (output/'private-command.log').write_bytes(result.stdout+result.stderr)
        raise RuntimeError('Android operation failed; private diagnostics retained')
    return result.stdout

def cleanup_private_inputs():
    subprocess.run(base+['shell','run-as',APP,'rm','-f','files/runtime-test-1.json','files/runtime-test-2.json'],capture_output=True,timeout=15)
atexit.register(cleanup_private_inputs)

config = (ROOT/'app/build/generated/source/buildConfig/debug/ru/ffl24/direct/BuildConfig.java').read_text()
if not all(re.search(rf'DIRECT_MYSQL_{key}\s*=\s*"";', config) for key in ('HOST','PORT','DATABASE','USER','PASSWORD','USE_SSL','CA_PEM')):
    raise RuntimeError('A credentials-free APK is required')
hashes = {}
for label, apk in [('main', ROOT/'app/build/outputs/apk/debug/app-debug.apk'),('test',ROOT/'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')]:
    copy = output/(label+'.apk'); shutil.copyfile(apk,copy); copy.chmod(0o600)
    hashes[label] = hashlib.sha256(copy.read_bytes()).hexdigest()
    adb(['install','-r','-t',str(copy)])
installed = adb(['shell','pm','path',APP]).decode().strip().removeprefix('package:')
assert hashlib.sha256(adb(['exec-out','cat',installed])).hexdigest() == hashes['main']
adb(['shell','am','force-stop',APP]); adb(['shell','pm','clear',APP])
adb(['shell','svc','power','stayon','true']);adb(['shell','input','keyevent','KEYCODE_WAKEUP']);adb(['shell','wm','dismiss-keyguard'])
adb(['shell','run-as',APP,'mkdir','-p','files'])
for index,state in enumerate((args.synthetic_state,args.restored_state),1):
    owner = json.loads((state/'owner.json').read_text())
    profile = json.loads((state/'android-config.json').read_text())
    assert profile['host']=='localhost' and int(profile['port']) == owner['port']
    assert profile['fixtureMarker'] in ('ffl-direct-owned-fixture-20261017','ffl-direct-private-restored-dump')
    # Verify ownership before using either service. Do not expose server/account values.
    labels = json.loads(subprocess.check_output(['docker','inspect',owner['container']]))[0]['Config']['Labels']
    assert labels['org.ffl.direct.development-owner'] == owner['id']
    adb(['reverse','tcp:'+profile['port'],'tcp:'+profile['port']])
    adb(['shell',f"run-as {APP} sh -c 'cat > files/runtime-test-{index}.json'"],json.dumps(profile).encode())
adb(['reverse','tcp:19064','tcp:19064'])
results=[]
cold_start_readiness=None
def run(label, selected, count):
    adb(['shell','run-as',APP,'rm','-f','files/completion-ui/instrumentation.xml'])
    command=base+['shell','am','instrument','-w','-r','-e','listener','ru.ffl24.direct.CompletionRunListener','-e','class',selected,APP+'.test/androidx.test.runner.AndroidJUnitRunner']
    started=time.time(); result=subprocess.run(command,capture_output=True,timeout=900)
    (output/(label+'-private.log')).write_bytes(result.stdout+result.stderr)
    xml=adb(['exec-out','run-as',APP,'cat','files/completion-ui/instrumentation.xml'],check=False)
    (output/(label+'.xml')).write_bytes(xml)
    try: suite=ET.fromstring(xml)
    except ET.ParseError: suite=ET.Element('testsuite',tests='0',failures='0',errors='1',skipped='0')
    passed=result.returncode==0 and suite.get('tests')==str(count) and all(suite.get(k)=='0' for k in ('failures','errors','skipped')) and f'OK ({count} test'.encode() in result.stdout
    cases=suite.findall('testcase')
    if '#' in selected:
        expected=set(tuple(s.split('#')) for s in selected.split(','))
        passed=passed and {(c.get('classname'),c.get('name')) for c in cases}==expected
    else: passed=passed and all(c.get('classname')==selected for c in cases)
    results.append({'label':label,'passed':passed,'junit':dict(suite.attrib),'command':command,'elapsed_seconds':round(time.time()-started,2)})
    (output/'summary.json').write_text(json.dumps({'passed':len(results)==(3 if args.checks=='all' else 1) and all(r['passed'] for r in results),'expected_group_count':3 if args.checks=='all' else 1,'cases':results,'apk_sha256':hashes['main'],'test_apk_sha256':hashes['test'],'configured_build':False,'android_os_hw_timeout_multiplier':adb(['shell','getprop','ro.hw_timeout_multiplier']).decode().strip() or '1','production_accessed':False,'cold_start_readiness':cold_start_readiness},indent=2))
    print(label+': '+('PASS' if passed else 'FAIL'),flush=True)
    if not passed:raise SystemExit(1)

if args.checks=='all':
    methods=['actualMysqlSelectAndSafeFailureCategories','switchingProfilesInvalidatesSessionClosesOldGraphAndIsolatesOrder','failedSaveKeepsWorkingGraphAndEncryptedProfile','encryptedProfileSurvivesFreshManagerAndDriverMustReauthenticate','cancelledProbeDoesNotPublishOrReplaceConfiguration']
    run('connection-layer',','.join('ru.ffl24.direct.RuntimeConnectionTest#'+m for m in methods),5)
    # Force-stop keeps the encrypted profile and Keystore key; no pm clear or reinstall.
    adb(['shell','am','force-stop',APP])
    launch_started=time.monotonic()
    started=adb(['shell','am','start','-W','-n',APP+'/ru.ffl24.direct.MainActivity'])
    status=re.search(rb'Status: ([^\n]+)',started)
    # am start's display wait may expire on the software emulator. Require the actual
    # accessible foreground application, with a visible login/admin action, instead.
    ready=False
    while time.monotonic()-launch_started < 60:
        adb(['shell','uiautomator','dump','/data/local/tmp/ffl-runtime-window.xml'],check=False)
        xml=adb(['exec-out','cat','/data/local/tmp/ffl-runtime-window.xml'],check=False)
        try:
            nodes=list(ET.fromstring(xml).iter('node'))
            app_nodes=[n for n in nodes if n.get('package')==APP]
            ready=any('подключени' in n.get('text','').lower() or 'водител' in n.get('text','').lower() for n in app_nodes)
        except ET.ParseError: ready=False
        if ready:break
        time.sleep(1)
    adb(['shell','rm','-f','/data/local/tmp/ffl-runtime-window.xml'])
    cold_start_readiness={'am_start_status':status.group(1).decode().strip() if status else 'unknown','accessible_application_ready':ready,'elapsed_seconds':round(time.monotonic()-launch_started,2),'deadline_seconds':60}
    (output/'cold-start-readiness.json').write_text(json.dumps(cold_start_readiness,indent=2))
    if not ready:raise RuntimeError('Ordinary app did not expose the login/admin screen within 60 seconds')
    run('cold-process-profile','ru.ffl24.direct.RuntimeConnectionTest#actualProcessRestartUsesSavedRuntimeProfile',1)
run('live-settings-ui','ru.ffl24.direct.LiveConnectionSettingsUiTest',1)
