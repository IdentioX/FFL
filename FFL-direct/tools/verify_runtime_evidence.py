"""Validate collected local Android/MySQL evidence; never contacts a DB or promotes legacy acceptance."""
from pathlib import Path
import json,hashlib,xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
e=root/'docs/direct-rewrite/evidence/completion'
r=ET.parse(e/'instrumentation-final/instrumentation.xml').getroot()
assert int(r.get('tests','0'))==28
assert all(int(r.get(k,'0'))==0 for k in ['failures','errors','skipped'])
assert len(r.findall('testcase'))==28
assert len({(x.get('classname'),x.get('name')) for x in r.findall('testcase')})==28
assert 'OK (28 tests)' in (e/'instrumentation-final.log').read_text()
assert json.loads((e/'instrumentation-final-command.json').read_text())['exit']==0
assert json.loads((e/'instrumentation-final/mysql-ui-write.json').read_text())['screen_to_mysql_delivery_verified'] is True
before=json.loads((e/'mysql-before.json').read_text());after=json.loads((e/'mysql-after.json').read_text())
assert before['fixture_only'] and after['fixture_only']
changes={}
for table in ['reys','reys_den']:
    b=before['tables'][table];a=after['tables'][table]
    assert len(b)==len(a)
    delta=[]
    for old,new in zip(b,a):
        assert old['id']==new['id']
        fields=sorted(k for k in old.keys()|new.keys() if old.get(k)!=new.get(k))
        if old['id']==1:
            assert fields==['date_att_log','st_vv'] and old['st_vv']==0 and new['st_vv']==1
            assert old['date_att_log'] is None and new['date_att_log'] is not None
        else:assert not fields
        if fields:delta.append({'id':old['id'],'fields':fields})
    assert len(delta)==1
    changes[table]=delta
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
artifact=json.loads((e/'artifact-verification.json').read_text())
assert artifact['installed_sha256']==artifact['main_sha256']==sha(root/'app/build/outputs/apk/debug/app-debug.apk')
assert sha(root/'releases/FFL-Driver-Direct-0.2.0-dev.apk')==artifact['main_sha256']
static=json.loads((e/'static-validation.json').read_text())
assert static['reference_files_unchanged']==104 and static['unit']['tests']==831
assert all(static['unit'][k]==0 for k in ['failures','errors','skipped'])
assert static['build_exit']==0 and static['local_check']=='POSITIVE'
(e/'mysql-invariants.json').write_text(json.dumps({'fixture_only':True,'only_expected_two_columns_changed':True,'changes':changes,'other_rows_unchanged':True},indent=2))
report={'developer_runtime':'POSITIVE','android':dict(r.attrib),'installed_apk_sha256':artifact['installed_sha256'],
    'real_disposable_mysql':'POSITIVE','ui_to_mysql_read_and_write':'POSITIVE','only_expected_columns_changed':True,
    'legacy_database':'BLOCKED_NOT_CONFIGURED','physical_device':'BLOCKED_UNAVAILABLE',
    'measured_background_doze_reboot':'BLOCKED_NOT_RUN','production_write':False,'driver_rollout':'BLOCKED_SECURITY'}
(e/'runtime-validation.json').write_text(json.dumps(report,indent=2));print(json.dumps(report,indent=2))
