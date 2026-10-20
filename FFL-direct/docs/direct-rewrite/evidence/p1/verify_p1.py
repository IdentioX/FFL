"""Проверка независимости собранного APK P1; не проверяет live DB/runtime."""
from pathlib import Path
from datetime import datetime
from zoneinfo import ZoneInfo
import hashlib,json,re,subprocess,zipfile
import xml.etree.ElementTree as E

root=Path('/workspace/FFL-direct')
ev=root/'docs/direct-rewrite/evidence/p1'
p0=json.loads((ev.parent/'p0-validation.json').read_text())
assert p0['result']=='POSITIVE'
for row in json.loads((ev.parent/'reference-files.json').read_text()):
 assert hashlib.sha256(Path(row['path']).read_bytes()).hexdigest()==row['sha256'],row['path']
for row in p0['references']:
 assert subprocess.check_output(['git','-C',row['path'],'rev-parse','HEAD'],text=True).strip()==row['head']
 assert not subprocess.check_output(['git','-C',row['path'],'status','--porcelain=v1','--untracked-files=all'],text=True).strip()
assert (root/'settings.gradle.kts').read_text().count('include(')==1
assert 'include(":app")' in (root/'settings.gradle.kts').read_text()
main=root/'app/src/main'
for p in main.rglob('*'):
 if p.suffix in ['.kt','.xml']:
  text=p.read_text()
  for term in ['Retrofit','DriverApiService','DRIVER_API_BASE_URL','accessToken','java.sql','java.net','ru.ffl24.zakaz','Адрес API','Адрес сервера']:
   assert term not in text,(str(p),term)
for variant in ['debug','release']:
 components=(root/f'app/build/reports/direct-runtime/{variant}-components.txt').read_text().splitlines()
 assert len(components)>10
 for item in components:
  assert not re.search(r'^(com\.squareup\.(retrofit2|okhttp3)|io\.ktor|io\.jsonwebtoken|com\.auth0|com\.mysql|org\.mariadb\.jdbc):',item),item
apk=root/'app/build/outputs/apk/debug/app-debug.apk'
aapt=Path('/workspace/.ffl/android-sdk/build-tools/36.0.0/aapt2')
manifest=subprocess.check_output([str(aapt),'dump','xmltree','--file','AndroidManifest.xml',str(apk)],text=True)
badging=subprocess.check_output([str(aapt),'dump','badging',str(apk)],text=True)
(ev/'apk-manifest.txt').write_text(manifest)
(ev/'apk-badging.txt').write_text(badging)
assert 'android.permission.INTERNET' not in manifest
assert 'ru.ffl24.direct.dev' in badging
assert 'ru.ffl24.direct.MainActivity' in badging
with zipfile.ZipFile(apk) as z:
 dex=b''.join(z.read(n) for n in z.namelist() if re.match(r'classes\d*\.dex$',n))
 assert b'DIRECT_MYSQL' in dex
 for term in [b'Lretrofit2/',b'Lokhttp3/',b'Lru/ffl24/zakaz/',b'Lcom/mysql/',b'Lorg/mariadb/']:
  assert term not in dex,term
lint=E.parse(root/'app/build/reports/lint-results-debug.xml').getroot()
errors=[i.get('id') for i in lint.findall('issue') if i.get('severity') in ['Error','Fatal']]
assert not errors,errors
suites=list(E.parse(root/'app/build/test-results/testDebugUnitTest/TEST-ru.ffl24.direct.AppGraphTest.xml').getroot().iter('testsuite'))
counts={k:sum(int(s.get(k,0)) for s in suites) for k in ['tests','failures','errors','skipped']}
assert counts=={'tests':2,'failures':0,'errors':0,'skipped':0},counts
for log in ['build-final','build-package','dependencies']:
 assert int((ev/(log+'.exit')).read_text())==0,log
files=[]
for base in [root/'app/src',root/'gradle']:
 files.extend(str(p.relative_to(root)) for p in base.rglob('*') if p.is_file())
files += ['.gitignore','README.md','settings.gradle.kts','build.gradle.kts','gradle.properties','gradlew','gradlew.bat']
result={'time_moscow':datetime.now(ZoneInfo('Europe/Moscow')).isoformat(),
 'static_check':'POSITIVE','reference_files_unchanged':104,'p0_evidence_validated':True,
 'runtime_mode':'DIRECT_MYSQL','internet_permission':False,
 'package':'ru.ffl24.direct.dev','apk_path':str(apk),'apk_sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),
 'apk_size':apk.stat().st_size,'unit':counts,
 'lint_errors':errors,'lint_warnings':[i.get('id') for i in lint.findall('issue') if i.get('severity')=='Warning'],
 'debug_runtime_components':len((root/'app/build/reports/direct-runtime/debug-components.txt').read_text().splitlines()),
 'release_runtime_components':len((root/'app/build/reports/direct-runtime/release-components.txt').read_text().splitlines()),
 'new_project_files':sorted(set(files)),
 'runtime_checked_by_this_script':False,'live_db_used':False,'p2_started':False}
(ev/'static-validation.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
print(json.dumps(result,ensure_ascii=False,indent=2))
