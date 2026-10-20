"""Collect only this task's known test artifacts through the debuggable app's own UID."""
from pathlib import Path
import argparse,json,subprocess,xml.etree.ElementTree as ET
p=argparse.ArgumentParser();p.add_argument('--serial',default='emulator-5558');p.add_argument('--recovered',action='store_true');args=p.parse_args()
root=Path(__file__).resolve().parents[1]
folder='completion-initial-recovered' if args.recovered else 'completion-ui'
output=root/'docs/direct-rewrite/evidence/completion'/('instrumentation-initial-recovered' if args.recovered else 'instrumentation-final')
output.mkdir(exist_ok=True)
adb='/workspace/.ffl/android-sdk/platform-tools/adb'
for name in ['instrumentation.xml','orders-first.png','orders-second.png','orders-delivered.png','directory.png','mysql-orders.png','mysql-details.png']:
    result=subprocess.run([adb,'-s',args.serial,'exec-out','run-as','ru.ffl24.direct.dev','cat',f'files/{folder}/{name}'],capture_output=True,timeout=20)
    assert result.returncode==0,name
    if name.endswith('.xml'):ET.fromstring(result.stdout)
    else:assert result.stdout.startswith(b'\x89PNG\r\n\x1a\n'),name
    (output/name).write_bytes(result.stdout)
if not args.recovered:
    result=subprocess.run([adb,'-s',args.serial,'exec-out','run-as','ru.ffl24.direct.dev','cat','files/completion-ui/mysql-ui-write.json'],capture_output=True,timeout=20)
    assert result.returncode==0
    assert json.loads(result.stdout)=={'fixture_only':True,'screen_to_mysql_delivery_verified':True}
    (output/'mysql-ui-write.json').write_bytes(result.stdout)
r=ET.parse(output/'instrumentation.xml').getroot()
print(json.dumps(dict(r.attrib),indent=2))
