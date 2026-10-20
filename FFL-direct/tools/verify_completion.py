"""Local artifact/evidence verification. Does not connect to MySQL or elevate phase acceptance."""
from pathlib import Path
import hashlib,json,re,subprocess,xml.etree.ElementTree as ET,zipfile

root=Path(__file__).resolve().parents[1]
evidence=root/'docs/direct-rewrite/evidence/completion'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
refs=json.loads((root/'docs/direct-rewrite/evidence/reference-files.json').read_text())
assert len(refs)==104 and all(sha(Path(x['path']))==x['sha256'] for x in refs)
for repo in ['/workspace/FFL','/workspace/FFL-backend']:
    assert subprocess.check_output(['git','-C',repo,'status','--porcelain=v1'],text=True)==''
for variant in ['debug','release']:
    components=(root/f'app/build/reports/direct-runtime/{variant}-components.txt').read_text()
    assert all(x not in components for x in ['com.squareup.retrofit2:','com.squareup.okhttp3:','io.ktor:','io.jsonwebtoken:','com.auth0:','com.mysql:'])
    assert 'org.mariadb.jdbc:mariadb-java-client:3.5.10' in components
    assert 'androidx.work:work-runtime:' in components
apk=root/'app/build/outputs/apk/debug/app-debug.apk'
with zipfile.ZipFile(apk) as z:
    dex=b''.join(z.read(n) for n in z.namelist() if re.fullmatch(r'classes\d*\.dex',n))
assert all(x not in dex for x in [b'DriverApiService',b'RetrofitProvider',b'DRIVER_API_BASE_URL',b'NavigationFixture',b'completion-db.json',b'driver-fixture-only'])
buildconfig=(root/'app/build/generated/source/buildConfig/debug/ru/ffl24/direct/BuildConfig.java').read_text()
assert 'DATA_SOURCE_MODE = "DIRECT_MYSQL"' in buildconfig
empty={name:bool(re.search(rf'{name}\s*=\s*"";',buildconfig)) for name in ['DIRECT_MYSQL_HOST','DIRECT_MYSQL_PORT','DIRECT_MYSQL_DATABASE','DIRECT_MYSQL_USER','DIRECT_MYSQL_PASSWORD','DIRECT_MYSQL_USE_SSL','DIRECT_MYSQL_CA_PEM']}
assert all(empty.values()),'Configured APK requires separate handling; no config values are printed'
command=json.loads((evidence/'build-current-command.json').read_text())
assert command['exit']==0
stats={k:0 for k in ['tests','failures','errors','skipped']}
unit=evidence/'unit';unit.mkdir(exist_ok=True)
for p in (root/'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'):
    r=ET.parse(p).getroot()
    for k in stats:stats[k]+=int(r.get(k,'0'))
    (unit/p.name).write_bytes(p.read_bytes())
assert stats['tests']>=831 and stats['failures']==stats['errors']==stats['skipped']==0
before=json.loads((evidence/'before.json').read_text())
current={str(p.relative_to(root)):sha(p) for p in root.rglob('*') if p.is_file() and not {'build','.gradle','.kotlin','.git','completion','releases'}.intersection(p.relative_to(root).parts)}
changed=sorted(k for k in before.keys()|current.keys() if before.get(k)!=current.get(k))
source={str(p.relative_to(root)):sha(p) for p in (root/'app/src').rglob('*') if p.is_file()}
report={'local_check':'POSITIVE','legacy_mysql_used':False,'physical_device_tested':False,'production_write':False,
    'reference_files_unchanged':104,'unit':stats,'build_exit':command['exit'],
    'configuration_empty':empty,'apk':{'path':str(apk),'sha256':sha(apk),'bytes':apk.stat().st_size},
    'release':{'path':str(root/'app/build/outputs/apk/release/app-release-unsigned.apk'),'sha256':sha(root/'app/build/outputs/apk/release/app-release-unsigned.apk'),'signed':False},
    'changed_files':changed,'source_hashes':source}
(evidence/'static-validation.json').write_text(json.dumps(report,indent=2))
print(json.dumps({k:v for k,v in report.items() if k not in ['source_hashes','configuration_empty','changed_files']},indent=2))
