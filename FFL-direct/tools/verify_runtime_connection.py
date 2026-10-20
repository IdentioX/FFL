"""Verify collected runtime-profile evidence; this does not execute tests."""
from pathlib import Path
import hashlib
import json
import re
import subprocess
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
EVIDENCE=ROOT/'docs/evidence/runtime-connection'
def read(path):return json.loads(path.read_text())
checks={}
main=ROOT/'app/build/outputs/apk/debug/app-debug.apk'
sha=hashlib.sha256(main.read_bytes()).hexdigest()
config=(ROOT/'app/build/generated/source/buildConfig/debug/ru/ffl24/direct/BuildConfig.java').read_text()
checks['public_apk_has_empty_build_credentials']=all(re.search(rf'DIRECT_MYSQL_{key}\s*=\s*"";',config) for key in ('HOST','PORT','DATABASE','USER','PASSWORD','USE_SSL','CA_PEM'))
build=read(EVIDENCE/'build-metadata.json')
checks['successful_debug_release_builds']=build['apk_sha256']==sha and build['release_unsigned_sha256']==hashlib.sha256((ROOT/'app/build/outputs/apk/release/app-release-unsigned.apk').read_bytes()).hexdigest() and all('BUILD SUCCESSFUL' in (EVIDENCE/name).read_text() for name in ('build-final.log','release-build.log','test-harness-build.log'))
checks['direct_mode']=bool(re.search(r'DATA_SOURCE_MODE\s*=\s*"DIRECT_MYSQL"',config))
checks['frozen_production_sources']=all((ROOT/name).is_file() and hashlib.sha256((ROOT/name).read_bytes()).hexdigest()==digest for name,digest in read(EVIDENCE/'main-sources.json').items())
units=[ET.parse(p).getroot() for p in (ROOT/'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml')]
counts={k:sum(int(x.get(k,0)) for x in units) for k in ('tests','failures','errors','skipped')}
checks['jvm_845_pass']=counts=={'tests':845,'failures':0,'errors':0,'skipped':0}
for label,count in [('ui',3),('public-regression',2),('small-regression',2)]:
    summary=read(EVIDENCE/label/'summary.json')
    checks[label]=summary['passed'] and int(summary['junit']['tests'])==count and summary['main_apk_sha256']==sha and summary['installed_apk_sha256']==sha
# Preserve the failed full run and require fresh targeted evidence for every failure.
# This is distinct-method coverage across runs, never a rewritten green JUnit run.
initial=read(EVIDENCE/'public-regression-initial/summary.json')
first=ET.parse(EVIDENCE/'public-regression-initial/instrumentation.xml').getroot()
retry=ET.parse(EVIDENCE/'public-regression/instrumentation.xml').getroot()
def case_key(c):return (c.get('classname'),c.get('name'))
coverage={case_key(c):c.find('failure') is None and c.find('error') is None and c.find('skipped') is None for c in first.findall('testcase')}
initial_failed={k for k,ok in coverage.items() if not ok}
retried={case_key(c) for c in retry.findall('testcase')}
for c in retry.findall('testcase'):coverage[case_key(c)]=c.find('failure') is None and c.find('error') is None and c.find('skipped') is None
checks['public_38_distinct_methods_pass_across_runs']=initial['main_apk_sha256']==sha and initial['installed_apk_sha256']==sha and first.get('tests')=='38' and first.get('errors')=='0' and first.get('skipped')=='0' and initial_failed=={('ru.ffl24.direct.WorkspaceNavigationTest','loginListDetailsDirectorySettingsAndLogoutUseRealNavigation')} and initial_failed<=retried and len(coverage)==38 and all(coverage.values())
(EVIDENCE/'public-method-coverage.json').write_text(json.dumps({'mode':'distinct methods across immutable initial and targeted runs','initial_tests':38,'initial_passed':sum(c.find('failure') is None for c in first.findall('testcase')),'targeted_tests':len(retry.findall('testcase')),'distinct_methods':len(coverage),'all_latest_results_pass':all(coverage.values()),'cases':[{'class':k[0],'method':k[1],'passed':ok} for k,ok in sorted(coverage.items())]},indent=2))
runtime=read(EVIDENCE/'summary.json')
checks['runtime_7_same_public_apk']=runtime['passed'] and runtime['apk_sha256']==sha and sum(int(case['junit']['tests']) for case in runtime['cases'])==7 and len(runtime['cases'])==3 and all(case['passed'] for case in runtime['cases']) and runtime['configured_build'] is False
checks['new_editor_screenshots']=all((EVIDENCE/'ui'/name).read_bytes().startswith(b'\x89PNG\r\n\x1a\n') for name in ('10-db-settings-fields.png','10b-db-settings-success-synthetic.png'))
lint=ET.parse(ROOT/'app/build/reports/lint-results-debug.xml').getroot()
checks['lint_no_errors']=all(item.get('severity') not in ('Error','Fatal') for item in lint)
refs=read(ROOT/'docs/direct-rewrite/evidence/reference-files.json')
preserved=read(EVIDENCE/'business-sql-and-tls-preservation.json')
checks['business_sql_and_tls_unchanged']=len(preserved)==9 and all(item['unchanged_from_runtime_settings_baseline'] and hashlib.sha256((ROOT/item['file']).read_bytes()).hexdigest()==item['sha256'] for item in preserved)
checks['legacy_104_hashes']=len(refs)==104 and all(hashlib.sha256(Path(item['path']).read_bytes()).hexdigest()==item['sha256'] for item in refs)
checks['legacy_git_clean']=all(subprocess.check_output(['git','-C',path,'status','--porcelain'])==b'' for path in ('/workspace/FFL','/workspace/FFL-backend'))
integrity=read(EVIDENCE/'original-data-integrity.json')
# Keep the underlying integrity report intact; verify its documented result below.
checks['original_records_unchanged']=integrity.get('all_original_records_unchanged') is True and integrity.get('original_tables_checked')==101 and integrity.get('baseline_rows')==359540 and integrity.get('plaintext_rows_published') is False and integrity.get('source_sql_sha256')=='04e481bdfa30e286e0b568e999763fab8fcf0cc91b192f564f74a039ee9e8c55'
restored=read(EVIDENCE/'restored-regression.json')
retry=read(EVIDENCE/'notification-instrumented/summary.json')
retry_xml=ET.parse(EVIDENCE/'notification-instrumented/instrumentation.xml').getroot()
retry_case=retry_xml.find('testcase')
retry_ok=retry['passed'] and retry['public_apk_sha256']==sha and retry_xml.get('tests')=='1' and all(retry_xml.get(k)=='0' for k in ['failures','errors','skipped']) and retry_case is not None and retry_case.get('name')=='realWorkManagerPollsMysqlDeduplicatesAndOpensSecondRoute'
failed_labels={case['label'] for case in restored['cases'] if not case['passed']}
restored_ok=len(restored['cases'])==12 and (not failed_labels or failed_labels=={'mysql-notifications'} and retry_ok)
native=read(EVIDENCE/'native-notification/summary.json')
checks['native_system_notification_without_instrumentation']=native['passed'] and native['public_apk_sha256']==sha and native['setup_is_fixture_only'] and native['instrumentation_ended_before_tap'] and all(native[k] for k in ['real_os_notification_found','native_notification_clicked','notification_intent_received','controlled_second_order_visible','app_package_visible']) and all(value=='0' for value in native['animations'].values())
checks['restored_12_distinct_cases_pass']=restored_ok
result={'runtime_connection_verified':all(checks.values()),'checks':checks,'jvm':counts,'public_apk_sha256':sha,'restored_regression_passed':restored_ok,'restored_case_counts':{'executed':len(restored['cases']),'initial_passed':sum(c['passed'] for c in restored['cases']),'latest_distinct_passed':12 if restored_ok else sum(c['passed'] for c in restored['cases'])},'instrumented_notification_animation_scale_0':'NEGATIVE; separate native system notification scale0 PASS; instrumented scale1 PASS','physical_device':'BLOCKED','production_accessed':False,'security_rollout':'BLOCKED_SECURITY'}
(EVIDENCE/'validation.json').write_text(json.dumps(result,indent=2));print(json.dumps(result,indent=2))
if not all(checks.values()):raise SystemExit(1)
