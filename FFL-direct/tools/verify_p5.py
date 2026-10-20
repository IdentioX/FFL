"""Local evidence only: never connects to a DB, starts Android or promotes external phase gates."""
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
EVIDENCE = ROOT/'docs/direct-rewrite/evidence/p5'
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()

before = json.loads((EVIDENCE/'before.json').read_text())['files']
existing_changes = sorted(name for name, digest in before.items() if not (ROOT/name).is_file() or sha(ROOT/name) != digest)
allowed = {
    'app/src/main/java/ru/ffl24/direct/AppGraph.kt', 'app/src/main/java/ru/ffl24/direct/ui/DirectApp.kt',
    'docs/direct-rewrite/MASTER_CONTEXT.md', 'docs/direct-rewrite/DIRECT_REWRITE_PLAN.md',
    'docs/direct-rewrite/DIRECT_UI_CONTRACT.md', 'docs/direct-rewrite/SECURITY_RISKS.md',
    'docs/direct-rewrite/PROGRESS.md',
}
assert set(existing_changes) <= allowed, 'Unexpected changes to prior files'
refs = json.loads((ROOT/'docs/direct-rewrite/evidence/reference-files.json').read_text())
assert len(refs) == 104 and all(sha(Path(x['path'])) == x['sha256'] for x in refs), 'Reference changed'

build = json.loads((EVIDENCE/'build-final-command.json').read_text())
assert build['exit'] == 0
tests = []
for p in sorted((ROOT/'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml')):
    assert p.stat().st_mtime >= build['started_epoch'], 'Stale unit XML'
    x = ET.parse(p).getroot()
    tests.append(dict(name=x.get('name'), tests=int(x.get('tests', 0)), failures=int(x.get('failures', 0)),
                      errors=int(x.get('errors', 0)), skipped=int(x.get('skipped', 0))))
assert sum(x['tests'] for x in tests) == 811
assert all(x['failures'] == x['errors'] == x['skipped'] == 0 for x in tests)
golden = json.loads((EVIDENCE/'golden.json').read_text())
assert sha(Path(golden['source'])) == golden['source_sha256']
assert sha(ROOT/'app/src/test/resources/p5-golden.properties') == golden['fixture_sha256']

lint = ET.parse(ROOT/'app/build/reports/lint-results-debug.xml').getroot()
issues = [dict(id=x.get('id'), severity=x.get('severity')) for x in lint.findall('issue')]
assert not any(x['severity'] in ('Error', 'Fatal') for x in issues)
graphs = {}
for variant in ('debug', 'release'):
    entries = (ROOT/f'app/build/reports/direct-runtime/{variant}-components.txt').read_text().splitlines()
    assert not any(any(g in entry for g in ('com.squareup.retrofit2:', 'com.squareup.okhttp3:', 'io.ktor:', 'io.jsonwebtoken:', 'com.auth0:')) for entry in entries)
    graphs[variant] = len(entries)
build_config = next((ROOT/'app/build/generated/source/buildConfig/debug').rglob('BuildConfig.java')).read_text()
for suffix in ('HOST', 'PORT', 'DATABASE', 'USER', 'PASSWORD', 'USE_SSL', 'CA_PEM'):
    assert f'DIRECT_MYSQL_{suffix} = "";' in build_config, 'Validation APK must contain no configured DB values'

apk = ROOT/'app/build/outputs/apk/debug/app-debug.apk'
with zipfile.ZipFile(apk) as z:
    dex = b''.join(z.read(name) for name in z.namelist() if name.startswith('classes') and name.endswith('.dex'))
    assert b'Lru/ffl24/direct/ui/OrderDetailsScreenKt;' in dex
    assert b'Lru/ffl24/direct/data/mysql/DirectMySqlOrderDetailsDataSource;' in dex
    for forbidden in (b'Lretrofit2/', b'Lokhttp3/', b'Lru/ffl24/zakaz/', b'DriverApiService'):
        assert forbidden not in dex
metadata = json.loads((ROOT/'app/build/outputs/apk/debug/output-metadata.json').read_text())
assert metadata['applicationId'] == 'ru.ffl24.direct.dev'
sql_source = (ROOT/'app/src/main/java/ru/ffl24/direct/data/mysql/DirectMySqlOrderDetailsDataSource.kt').read_text()
assert not any(word in sql_source.upper().split() for word in ('UPDATE', 'INSERT', 'DELETE', 'CREATE', 'ALTER', 'DROP'))
release = ROOT/'app/build/outputs/apk/release/app-release-unsigned.apk'
result = {
    'static_check': 'POSITIVE_LOCAL', 'phase_result': 'BLOCKED', 'p4_gate': 'BLOCKED_USER_WAIVED_FOR_P5_IMPLEMENTATION',
    'existing_files_changed': existing_changes, 'reference_files_unchanged': len(refs),
    'unit': {'executed': 811, 'passed': 811, 'failed': 0, 'errors': 0, 'skipped': 0, 'disabled': 0,
             'p5_tests': 785, 'prior_connection_graph_tests': 26, 'suites': tests},
    'golden': golden, 'runtime_components': graphs, 'lint': issues,
    'apk': {'path': str(apk), 'sha256': sha(apk), 'bytes': apk.stat().st_size, 'package': metadata['applicationId']},
    'release': {'path': str(release), 'sha256': sha(release), 'runtime_tested': False, 'signed': False},
    'live_db_used': False, 'physical_device_checked': False, 'p6_started': False,
}
(EVIDENCE/'static-validation.json').write_text(json.dumps(result, ensure_ascii=False, indent=2)+'\n')
print(json.dumps({key: result[key] for key in ('static_check', 'phase_result', 'reference_files_unchanged', 'apk')}, ensure_ascii=False))
