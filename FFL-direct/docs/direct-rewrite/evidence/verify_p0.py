"""Проверка полноты P0 без изменений исходников и без обращения к БД."""
from pathlib import Path
from datetime import datetime
from zoneinfo import ZoneInfo
import hashlib
import json
import re
import subprocess
import xml.etree.ElementTree as ET

root = Path('/workspace/FFL-direct')
docs = root / 'docs/direct-rewrite'
ev = docs / 'evidence'
required = ['MASTER_CONTEXT', 'DIRECT_REWRITE_PLAN', 'DIRECT_DB_CONTRACT',
            'DIRECT_UI_CONTRACT', 'DIRECT_QA_CHECKLIST', 'SECURITY_RISKS', 'PROGRESS']
for name in required:
    assert (docs / (name + '.md')).stat().st_size > 1000, name
plan = (docs / 'DIRECT_REWRITE_PLAN.md').read_text()
phases = re.findall(r'^## (P\d+) — (.+)$', plan, re.M)
assert [p for p, _ in phases] == ['P' + str(i) for i in range(12)]
for i in range(12):
    section = plan.split('## P' + str(i) + ' — ', 1)[1].split('\n## ', 1)[0]
    for field in ['Цель', 'Файлы', 'Вход', 'Обязательно', 'Запрещено', 'Unit',
                  'Runtime', 'Acceptance', 'Stop conditions', 'Отчёт']:
        assert '**' + field + ':**' in section, (i, field)
links = 0
for name in required:
    for link in re.findall(r'\]\(([^)]+)\)', (docs / (name + '.md')).read_text()):
        if not re.match(r'https?://', link):
            target = link.split('#', 1)[0]
            assert not target or (docs / target).exists(), (name, link)
            links += 1
assert not [p for p in root.rglob('*') if p.is_file() and not p.is_relative_to(root/'docs')]
for forbidden in ['settings.gradle.kts', 'build.gradle.kts', 'app', 'gradlew', '.git']:
    assert not (root / forbidden).exists(), forbidden
source_hashes = json.loads((ev / 'source-hashes.json').read_text())
for source, digest in source_hashes.items():
    assert hashlib.sha256(Path(source).read_bytes()).hexdigest() == digest
report_source = next(k for k in source_hashes if '3380a244-' in k)
master_source = next(k for k in source_hashes if '1b76a5fb-' in k)
assert (docs/'BACKEND_CONTRACT_REPORT.md').read_bytes() == Path(report_source).read_bytes()
assert (docs/'sources/MASTER_INSTRUCTIONS.md').read_bytes() == Path(master_source).read_bytes()
refs = []
expected = json.loads((ev/'reference-files.json').read_text())
for item in expected:
    assert hashlib.sha256(Path(item['path']).read_bytes()).hexdigest() == item['sha256'], item['path']
for repo, expected_head in [('/workspace/FFL','047458aa416465dea772ee65082c093f26064699'),
                             ('/workspace/FFL-backend','68b82b09c4f66b2408e563b71a0c7baf9fa0085a')]:
    head = subprocess.check_output(['git','-C',repo,'rev-parse','--verify','HEAD^{commit}'],text=True).strip()
    assert head == expected_head
    assert not subprocess.check_output(['git','-C',repo,'status','--porcelain=v1','--untracked-files=all'],text=True).strip()
    refs.append({'path':repo,'head':head,'clean':True})
results = {}
for stem in ['backend-tests','legacy-android-tests']:
    assert int((ev/(stem+'.exit')).read_text()) == 0
    doc = ET.parse(ev/(stem+'.xml')).getroot()
    suites = list(doc.iter('testsuite'))
    total = {key:sum(int(s.get(key,0)) for s in suites) for key in ['tests','failures','errors','skipped']}
    assert total['tests'] > 0 and total['failures'] == total['errors'] == total['skipped'] == 0
    assert (ev/(stem+'.xml')).stat().st_mtime >= (ev/'reference-files.json').stat().st_mtime
    results[stem] = total
assert results['backend-tests']['tests'] == 102
assert results['legacy-android-tests']['tests'] == 1
assert int((ev/'legacy-android-build.exit').read_text()) == 0
apk = Path('/workspace/FFL/app/build/outputs/apk/debug/app-debug.apk')
statuses = ['STATIC_CHECK','UNIT_TEST','BUILD','EMULATOR_RUNTIME','REAL_DEVICE_RUNTIME','LIVE_DB_READ','LIVE_DB_WRITE','SECURITY_REVIEW']
for item in statuses:
    assert item in (docs/'PROGRESS.md').read_text()
result = {'date_moscow':datetime.now(ZoneInfo('Europe/Moscow')).isoformat(),
          'result':'POSITIVE','required_documents':len(required),'phases':len(phases),
          'links_checked':links,'tracked_reference_files_unchanged':len(expected),
          'report_exact_copy':True,'no_new_runtime':True,'references':refs,
          'fresh_test_results':results,
          'legacy_apk':{'path':str(apk),'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'size':apk.stat().st_size,'installed_in_p0':False},
          'direct_apk_exists':False,'live_db_used':False,
          'driver_rollout':'BLOCKED_SECURITY'}
(ev/'p0-validation.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
print(json.dumps(result,ensure_ascii=False,indent=2))
