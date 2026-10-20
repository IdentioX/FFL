"""Validate collected 0.3.0 UI evidence; does not run tests or mutate databases."""
from pathlib import Path
import hashlib,json,re,shutil,subprocess,xml.etree.ElementTree as ET,zipfile
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'docs/evidence/ui-design'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
read=lambda n:json.loads((OUT/n).read_text())
unit={k:0 for k in ['tests','failures','errors','skipped']}
(OUT/'unit').mkdir(exist_ok=True)
for p in (ROOT/'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'):
    node=ET.parse(p).getroot()
    for k in unit:unit[k]+=int(node.get(k,0))
    shutil.copyfile(p,OUT/'unit'/p.name)
assert unit==dict(tests=843,failures=0,errors=0,skipped=0)
assert read('build-command.json')['exit']==0
assert 'BUILD SUCCESSFUL' in (OUT/'build.log').read_text()
ui=read('android/summary.json')
assert ui['passed'] and ui['junit']['tests']=='38' and all(ui['junit'][k]=='0' for k in ['failures','errors','skipped'])
small=read('android-small/summary.json')
assert small['passed'] and small['junit']['tests']=='2' and '384x768' in small['display_size'] and '192' in small['display_density']
assert all(small['junit'][k]=='0' for k in ['failures','errors','skipped'])
assert small['main_apk_sha256']==ui['main_apk_sha256']
restored=read('android-restored.json')
assert len(restored['cases'])==12 and all(c['passed'] and c['xml']['tests']=='1' and all(c['xml'][k]=='0' for k in ['failures','errors','skipped']) for c in restored['cases'])
assert restored['installed_apk_sha256']==restored['private_configured_apk_sha256']
assert {'mysql-refresh','cold-restore','compiled-login'}.issubset({c['label'] for c in restored['cases']})
mini=read('android-minified.json')
assert mini['passed'] and len(mini['cases'])==2 and mini['r8_obfuscation_confirmed'] and not mini['apk_debuggable']
assert mini['installed_apk_sha256']==mini['private_configured_r8_apk_sha256']
assert all(c['passed'] and c['sql_delivery_verified'] and c['junit']['tests']=='1' and all(c['junit'][k]=='0' for k in ['failures','errors','skipped']) for c in mini['cases'])
integrity=read('original-data-integrity.json')
assert integrity['all_original_records_unchanged'] and integrity['original_tables_checked']==101 and integrity['baseline_rows']==359540
config=(ROOT/'app/build/generated/source/buildConfig/debug/ru/ffl24/direct/BuildConfig.java').read_text()
assert 'DATA_SOURCE_MODE = "DIRECT_MYSQL"' in config and 'VERSION_CODE = 5' in config and 'VERSION_NAME = "0.3.0-dev"' in config
assert all(re.search(rf'DIRECT_MYSQL_{key}\s*=\s*"";',config) for key in ['HOST','PORT','DATABASE','USER','PASSWORD','USE_SSL','CA_PEM'])
apk=ROOT/'app/build/outputs/apk/debug/app-debug.apk'
frozen=read('frozen-sources.json')
assert all(sha(ROOT/n)==expected for n,expected in frozen['compile_inputs_sha256'].items())
assert sha(apk)==frozen['public_debug_apk_sha256']
assert restored['private_configured_apk_sha256']==frozen['private_configured_debug_apk_sha256']
assert sha(apk)==ui['main_apk_sha256']==ui['installed_apk_sha256']
with zipfile.ZipFile(apk) as z:dex=b''.join(z.read(n) for n in z.namelist() if re.fullmatch(r'classes\d*\.dex',n))
assert b'BINARY parol = BINARY ?' in dex
assert all(n not in dex for n in [b'DriverApiService',b'RetrofitProvider',b'DRIVER_API_BASE_URL',b'OwnedTestDatabase',b'DesignFixture',b'completion-db.json'])
for variant in ['debug','release']:
    path=ROOT/f'app/build/reports/direct-runtime/{variant}-components.txt';graph=path.read_text()
    assert all(g not in graph for g in ['com.squareup.retrofit2:','com.squareup.okhttp3:','io.ktor:','io.jsonwebtoken:','com.auth0:','com.mysql:'])
    assert 'org.mariadb.jdbc:mariadb-java-client:3.5.10' in graph and 'androidx.compose.runtime:runtime-android:1.10.6' in graph
    shutil.copyfile(path,OUT/path.name)
lint={};lintpath=ROOT/'app/build/reports/lint-results-debug.xml'
for issue in ET.parse(lintpath).getroot():
    key=issue.get('severity');lint[key]=lint.get(key,0)+1
assert not lint.get('Error',0)
shutil.copyfile(lintpath,OUT/lintpath.name)
# All data/domain/security/worker code must remain byte-for-byte identical.
before=read('before.json');protected=[]
for name,expected in before.items():
    if name.startswith(tuple('app/src/main/java/ru/ffl24/direct/'+p for p in ['data/','domain/','local/','session/','notifications/','navigation/'])) or name in ['app/src/main/java/ru/ffl24/direct/AppGraph.kt','app/proguard-rules.pro','gradle/libs.versions.toml']:
        assert sha(ROOT/name)==expected,name
        protected.append(name)
refs=json.loads((ROOT/'docs/direct-rewrite/evidence/reference-files.json').read_text())
assert len(refs)==104 and all(sha(Path(r['path']))==r['sha256'] for r in refs)
for repo,meta in read('preservation.json')['references'].items():
    assert subprocess.check_output(['git','-C',str(ROOT.parent/repo),'status','--porcelain=v1'])==b''
    assert subprocess.check_output(['git','-C',str(ROOT.parent/repo),'rev-parse','HEAD']).decode().strip()==meta['head']
for name in ['SHA256SUMS','SHA256SUMS-0.2.1.txt','SHA256SUMS-0.2.2.txt']:
    for line in (ROOT/'releases'/name).read_text().splitlines():
        expected,filename=line.split(maxsplit=1);assert sha(ROOT/'releases'/filename.strip().removeprefix('*'))==expected
private=ROOT.parent/'.ffl';source=private/'restore-source/112.sql.gz'
assert sha(source)=='fbd81f4fe11f61c13967b3cff3a53c682ac81046bf37924dd4e474e37c3cf94d'
assert sha(source.with_suffix(''))=='04e481bdfa30e286e0b568e999763fab8fcf0cc91b192f564f74a039ee9e8c55'
passwords=[]
for state in ['direct-dev','direct-restored']:
    for line in (private/state/'container.env').read_text().splitlines():
        key,sep,value=line.partition('=')
        if key in ['MARIADB_ROOT_PASSWORD','MARIADB_PASSWORD']:
            assert sep and len(value)>=32;passwords.append(value.encode())
passwords.append(json.loads((private/'direct-restored/integrity-config.json').read_text())['password'].encode())
assert len(passwords)==5
excluded={'build','.gradle','.kotlin','.git','releases','__pycache__'}
scanned=0
for path in ROOT.rglob('*'):
    if path.is_file() and not excluded.intersection(path.relative_to(ROOT).parts):
        assert not any(v in path.read_bytes() for v in passwords),'Private DB credential found in public project'
        scanned+=1
assert not any(v in dex for v in passwords),'Private DB credential found in public APK'
with zipfile.ZipFile(private/'direct-restored/artifacts/configured-local.apk') as z:
    assert not any(passwords[-1] in z.read(n) for n in z.namelist()),'Auditor credential in Main APK'
current={str(p.relative_to(ROOT)):sha(p) for p in ROOT.rglob('*') if p.is_file() and not (excluded|{'evidence','design'}).intersection(p.relative_to(ROOT).parts)}
changed=sorted(n for n in before.keys()|current.keys() if before.get(n)!=current.get(n))
(OUT/'files.md').write_text('# Изменённые файлы 0.3.0\n\n'+'\n'.join('- `'+n+'`' for n in changed)+'\n')
report={'status':'UI_IMPLEMENTATION_VERIFIED','version':'0.3.0-dev','version_code':5,'unit':unit,'android_ui_tls_distinct_methods':38,'android_small_repeat_methods':2,'android_restored_methods':12,'android_minified_methods':2,'android_total_distinct_methods':52,'public_apk_sha256':sha(apk),'public_apk_bytes':apk.stat().st_size,'public_configuration_empty':True,'api_runtime_dependencies_present':False,'lint':lint,'protected_direct_files_unchanged':len(protected),'reference_files_unchanged':104,'original_tables_unchanged':101,'original_rows_unchanged':359540,'driver_rollout':'BLOCKED_SECURITY','physical_device_tested':False,'windows_executed':False,'production_accessed':False,'private_passwords_checked_without_disclosure':5,'public_files_scanned':scanned,'changed_files':changed}
(OUT/'validation.json').write_text(json.dumps(report,indent=2));print(json.dumps({k:v for k,v in report.items() if k!='changed_files'},indent=2))
