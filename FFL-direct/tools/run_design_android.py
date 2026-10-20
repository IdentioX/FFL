"""Collect current design instrumentation and real APK screenshots on an owned emulator.

Credentials-free target only. Use dev_database.py for the separate restored DB runs.
This runner clears the Direct test package; it refuses personal-device serials.
"""
import argparse,hashlib,io,json,re,shutil,subprocess,tarfile,time
from pathlib import Path
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--serial',required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--classes',default='ru.ffl24.direct.DesignImplementationTest');args=p.parse_args()
 if not args.serial.startswith('emulator-'):p.error('Dedicated emulator required')
 config=(ROOT/'app/build/generated/source/buildConfig/debug/ru/ffl24/direct/BuildConfig.java').read_text()
 if not all(re.search(rf'DIRECT_MYSQL_{k}\s*=\s*"";',config) for k in ['HOST','PORT','DATABASE','USER','PASSWORD','USE_SSL','CA_PEM']):p.error('Use credentials-free APK for screenshot fixtures')
 expected={}
 for selected in args.classes.split(','):
  parts=selected.split('#',1);source=ROOT/'app/src/androidTest/java'/Path(parts[0].replace('.','/')).with_suffix('.kt')
  expected[parts[0]]=expected.get(parts[0],0)+(1 if len(parts)==2 else len(re.findall(r'@Test\b',source.read_text())))
 args.output.mkdir(parents=True,exist_ok=False,mode=0o700);base=['adb','-s',args.serial]
 def run(argv,timeout=60,check=True):
  r=subprocess.run(base+argv,capture_output=True,timeout=timeout)
  if check and r.returncode:(args.output/'command-failure.log').write_bytes(r.stdout+r.stderr);raise RuntimeError('Android command failed')
  return r
 hashes={}
 for name,path in [('main',ROOT/'app/build/outputs/apk/debug/app-debug.apk'),('test',ROOT/'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')]:
  hashes[name]=hashlib.sha256(path.read_bytes()).hexdigest();copy=args.output/(name+'.apk');shutil.copyfile(path,copy);copy.chmod(0o600)
  run(['install','-r','-t',str(copy)],300)
 installed=run(['shell','pm','path','ru.ffl24.direct.dev']).stdout.decode().strip().removeprefix('package:')
 installed_hash=hashlib.sha256(run(['exec-out','cat',installed],120).stdout).hexdigest();assert installed_hash==hashes['main']
 run(['shell','am','force-stop','ru.ffl24.direct.dev']);run(['shell','pm','clear','ru.ffl24.direct.dev']);run(['shell','input','keyevent','KEYCODE_WAKEUP'])
 run(['shell','svc','power','stayon','true']);run(['shell','input','keyevent','KEYCODE_WAKEUP']);run(['shell','wm','dismiss-keyguard'])
 command=['shell','am','instrument','-w','-r','-e','listener','ru.ffl24.direct.CompletionRunListener','-e','class',args.classes,'ru.ffl24.direct.dev.test/androidx.test.runner.AndroidJUnitRunner']
 start=time.time();r=run(command,1200,False);(args.output/'instrumentation.log').write_bytes(r.stdout+r.stderr)
 xml=run(['exec-out','run-as','ru.ffl24.direct.dev','cat','files/completion-ui/instrumentation.xml'],check=False).stdout;(args.output/'instrumentation.xml').write_bytes(xml)
 try:node=ET.fromstring(xml)
 except ET.ParseError:node=ET.Element('testsuite',tests='0',failures='0',errors='1',skipped='0')
 actual={name:sum(c.get('classname')==name for c in node.findall('testcase')) for name in expected};passed=r.returncode==0 and int(node.get('tests','0'))>0 and all(node.get(k)=='0' for k in ['failures','errors','skipped']) and f"OK ({node.get('tests')} test".encode() in r.stdout
 passed=passed and actual==expected and int(node.get('tests','0'))==sum(expected.values())
 archive=run(['exec-out','run-as','ru.ffl24.direct.dev','tar','cf','-','-C','files','design-ui'],120,False)
 if archive.returncode==0:
  try:
   with tarfile.open(fileobj=io.BytesIO(archive.stdout),mode='r:') as t:
    for m in t.getmembers():
     if m.isfile() and Path(m.name).suffix=='.png':(args.output/Path(m.name).name).write_bytes(t.extractfile(m).read())
  except tarfile.ReadError:
   passed=False
 summary={'passed':passed,'junit':dict(node.attrib),'command':base+command,'exit':r.returncode,'elapsed_seconds':round(time.time()-start,3),'main_apk_sha256':hashes['main'],'installed_apk_sha256':installed_hash,'test_apk_sha256':hashes['test'],'screenshots':sorted(x.name for x in args.output.glob('*.png')),'data':'fictional UI fixtures; not DB E2E','android_os_hw_timeout_multiplier':run(['shell','getprop','ro.hw_timeout_multiplier']).stdout.decode().strip() or '1','emulator_api':int(run(['shell','getprop','ro.build.version.sdk']).stdout.strip()),'class_counts':actual,'display_size':run(['shell','wm','size']).stdout.decode().strip(),'display_density':run(['shell','wm','density']).stdout.decode().strip()}
 (args.output/'summary.json').write_text(json.dumps(summary,indent=2));print(json.dumps(summary,indent=2))
 if not passed:raise SystemExit(1)
if __name__=='__main__':main()
