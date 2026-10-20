"""Click a real OS notification after its owned JDBC/Worker fixture instrumentation ends.

Dedicated emulator only; private output outside checkout; no production endpoints or data.
The setup fixture is not a tap assertion. Native host checks provide the separate verdict.
"""
import argparse,hashlib,json,os,re,shutil,subprocess,time,xml.etree.ElementTree as ET
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];APP='ru.ffl24.direct.dev'
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--serial',required=True);p.add_argument('--state',type=Path,required=True);p.add_argument('--output',type=Path,required=True);args=p.parse_args()
if not args.serial.startswith('emulator-'):p.error('An explicitly selected dedicated emulator is required')
out=args.output.resolve()
if out==ROOT or ROOT in out.parents or out in ROOT.parents:p.error('Use a new private output directory outside source')
out.mkdir(parents=True,exist_ok=False,mode=0o700)
os.environ['FFL_DEV_STATE_DIR']=str(args.state.resolve());import dev_database as dev
base=['adb','-s',args.serial]
def adb(cmd,data=None,check=True,timeout=120):
 r=subprocess.run(base+cmd,input=data,capture_output=True,timeout=timeout)
 if check and r.returncode:(out/'command-private.log').write_bytes(r.stdout+r.stderr);raise RuntimeError('Android command failed; diagnostics remain private')
 return r.stdout
owner=dev.load();assert dev.inspect(owner);assert owner['profile']!='synthetic';dev.prepare_restored(owner)
c=json.loads((dev.STATE/'android-config.json').read_text());assert c['host']=='localhost' and c['fixtureMarker']=='ffl-direct-private-restored-dump' and int(c['port'])==owner['port']
config=(ROOT/'app/build/generated/source/buildConfig/debug/ru/ffl24/direct/BuildConfig.java').read_text();assert all(re.search(rf'DIRECT_MYSQL_{k}\s*=\s*"";',config) for k in ['HOST','PORT','DATABASE','USER','PASSWORD','USE_SSL','CA_PEM'])
hashes={}
for name,path in [('main',ROOT/'app/build/outputs/apk/debug/app-debug.apk'),('test',ROOT/'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')]:
 copy=out/(name+'.apk');shutil.copyfile(path,copy);copy.chmod(0o600);hashes[name]=hashlib.sha256(copy.read_bytes()).hexdigest();adb(['install','-r','-t',str(copy)],timeout=300)
installed=adb(['shell','pm','path',APP]).decode().strip().removeprefix('package:');assert hashlib.sha256(adb(['exec-out','cat',installed])).hexdigest()==hashes['main']
adb(['reverse','tcp:'+c['port'],'tcp:'+c['port']]);adb(['shell','am','force-stop',APP]);adb(['shell','svc','power','stayon','true']);adb(['shell','input','keyevent','KEYCODE_WAKEUP']);adb(['shell','wm','dismiss-keyguard'])
animation={k:adb(['shell','settings','get','global',k]).decode().strip() for k in ['animator_duration_scale','transition_animation_scale','window_animation_scale']}
adb(['shell','run-as',APP,'mkdir','-p','files']);adb(['shell','run-as',APP,'rm','-f','files/completion-ui/instrumentation.xml'])
adb(['shell',f"run-as {APP} sh -c 'cat > files/completion-db.json'"],(dev.STATE/'android-config.json').read_bytes())
db=dev.quoted(owner['database']);dev.sql(owner,f"UPDATE {db}.reys_den SET st_vv=0,date_att_log=NULL WHERE id=2000000001 AND id_v=2000000001;UPDATE {db}.reys_den SET st_vv=1,date_att_log='2026-05-18 12:00:00' WHERE id=2000000004 AND id_v=2000000001;")
method='ru.ffl24.direct.ConfiguredNotificationTest#prepareSystemNotificationForNativeHostTap';command=base+['shell','am','instrument','-w','-r','-e','listener','ru.ffl24.direct.CompletionRunListener','-e','class',method,APP+'.test/androidx.test.runner.AndroidJUnitRunner']
started=time.time()
try:
 r=subprocess.run(command,capture_output=True,timeout=300);(out/'setup-private.log').write_bytes(r.stdout+r.stderr)
 xml=adb(['exec-out','run-as',APP,'cat','files/completion-ui/instrumentation.xml']);(out/'setup.xml').write_bytes(xml);suite=ET.fromstring(xml);case=suite.find('testcase')
 assert r.returncode==0 and suite.get('tests')=='1' and all(suite.get(k)=='0' for k in ['failures','errors','skipped']) and b'OK (1 test)' in r.stdout and case is not None and case.get('name')=='prepareSystemNotificationForNativeHostTap'
finally:adb(['shell','run-as',APP,'rm','-f','files/completion-db.json'],check=False)
# No instrumentation or in-process PendingIntent.send() remains active for this step.
active=adb(['shell','dumpsys','activity','instrumentation']).decode();assert 'ru.ffl24.direct.dev.test' not in active
adb(['shell','cmd','statusbar','expand-notifications'])
def snapshot(label):
 adb(['shell','uiautomator','dump','/data/local/tmp/ffl-native-notification.xml'],timeout=30)
 b=adb(['exec-out','cat','/data/local/tmp/ffl-native-notification.xml']);(out/(label+'-private.xml')).write_bytes(b)
 try:return list(ET.fromstring(b).iter('node'))
 except ET.ParseError:return []
deadline=time.monotonic()+30;title=None
while time.monotonic()<deadline:
 nodes=snapshot('shade');title=next((n for n in nodes if n.get('text')=='Новые заказы второго рейса'),None)
 if title is not None:break
 time.sleep(0.5)
assert title is not None,'Real OS notification did not appear after the setup process ended'
coords=list(map(int,re.findall(r'\d+',title.get('bounds',''))));assert len(coords)==4
adb(['shell','input','tap',str((coords[0]+coords[2])//2),str((coords[1]+coords[3])//2)])
clicked=time.monotonic();ready=False;packages=[]
while time.monotonic()-clicked<60:
 nodes=snapshot('application');packages=sorted(set(n.get('package','') for n in nodes));text='\n'.join(n.get('text','') for n in nodes)
 ready=APP in packages and 'FFL E2E Driver' in text and 'Второй рейс' in text and 'FFL-E2E-NOTIFICATION' in text and 'Обновление заказов…' not in text
 if ready:break
 time.sleep(0.5)
activity=adb(['shell','dumpsys','activity','activities']).decode();(out/'activity-private.txt').write_text(activity)
intent_received='ru.ffl24.direct.SECOND_ROUTE.2000000001' in activity
if ready:
 image=adb(['exec-out','screencap','-p']);assert image.startswith(b'\x89PNG\r\n\x1a\n');(out/'native-second-route-private.png').write_bytes(image)
result={'passed':ready and intent_received,'public_apk_sha256':hashes['main'],'test_apk_sha256':hashes['test'],'setup_command':command,'setup_junit':dict(suite.attrib),'setup_is_fixture_only':True,'instrumentation_ended_before_tap':True,'real_os_notification_found':title is not None,'native_notification_clicked':True,'notification_intent_received':intent_received,'controlled_second_order_visible':ready,'app_package_visible':APP in packages,'animations':animation,'native_wait_seconds':round(time.monotonic()-clicked,2),'total_seconds':round(time.time()-started,2),'production_accessed':False}
(out/'summary.json').write_text(json.dumps(result,indent=2));print(json.dumps(result,indent=2));adb(['shell','rm','-f','/data/local/tmp/ffl-native-notification.xml'])
if not result['passed']:raise SystemExit(1)
