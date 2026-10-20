"""Offline golden fixtures from unchanged backend pure functions. No app/config imports or SQL."""
import ast
import base64
import hashlib
import json
from pathlib import Path
import random
import re
from typing import TypedDict

ROOT = Path(__file__).resolve().parents[1]
SOURCE = Path('/workspace/FFL-backend/app/repositories/orders_repository.py')
FUNCTIONS = {
    '_normalize_spaces', '_blank_to_none', '_order_number', '_order_identity', '_section_fields',
    '_manual_address_result', '_manager_phone', '_usable_route_url', '_address_result_from_candidate',
    '_normalize_match_text', '_address_match_tokens', '_score_address_candidate', '_select_order_contact_and_route',
    'parse_order_text', '_readiness_fields', '_delivery_fields', '_address_fields_from_row',
    'map_order_details', 'map_previous_driver_row',
}
CONSTANTS = {
    'ORDER_TABLES', 'ORDER_SECTIONS', 'ADDRESS_MARKERS', 'TIME_PATTERN', 'MATCH_PUNCTUATION_PATTERN',
    'ADDRESS_MATCH_STOPWORDS', 'ROUTE_LINK_SCHEMES', 'ADDRESS_RESULT_KEYS',
}
tree = ast.parse(SOURCE.read_text())
nodes = [ast.ImportFrom(module='__future__', names=[ast.alias(name='annotations')], level=0)]
for node in tree.body:
    if isinstance(node, ast.FunctionDef) and node.name in FUNCTIONS:
        nodes.append(node)
    elif isinstance(node, ast.ClassDef) and node.name == 'AddressResult':
        nodes.append(node)
    elif isinstance(node, ast.Assign) and any(isinstance(t, ast.Name) and t.id in CONSTANTS for t in node.targets):
        nodes.append(node)
namespace = {'re': re, 'TypedDict': TypedDict}
exec(compile(ast.fix_missing_locations(ast.Module(body=nodes, type_ignores=[])), str(SOURCE), 'exec'), namespace)

cases = []
def add(client, candidates, name):
    result, _ = namespace['_select_order_contact_and_route'](candidates, client)
    cases.append({'client': client, 'candidates': candidates, 'expected': result, 'name': name})

def candidate(ident=1, **kw):
    return dict(id=ident, **kw)

add(None, [], 'no candidates')
add(None, [candidate()], 'single empty candidate')
for link in ['http://example.invalid/a', 'HTTPS://example.invalid/a', 'yandexmaps://maps.example/a',
             'yandexnavi://build_route_on_map?lat_to=0&lon_to=0', None, '', 'javascript:bad', 'ftp://example.invalid', 'https://']:
    add('Other', [candidate(adress='  Проверочный адрес  ', link=link, tell_klient='  +70000000000  ',
                           manager_phone=' ', manager_short_phone='  12345 ')], f'single link {link}')
add('Тверская 1', [candidate(1, adress='Тверская 1'), candidate(99, adress='Тверская 1')], 'equal score ids do not break ambiguity')
add('Тверская 1', [candidate(1, adress='Тверская 1'), candidate(2, adress='Иной адрес')], 'full match')
add('Совсем другое', [candidate(1, adress='Тверская 1'), candidate(2, adress='Лесная 2')], 'all zero')
add('Ромашка', [candidate(1, ooo='Ромашка'), candidate(2, ooo='Другое')], 'organization branch 100')
add('Озёрная (улица) №1', [candidate(1, adress='озерная/улица-1'), candidate(2, adress='Другое')], 'punctuation yo')
add('Альфа бета гамма дельта', [candidate(1, adress='альфа бета гамма дельта'), candidate(2, adress='альфа бета гамма')], 'cap three tokens tie')
add('тверскаяX', [candidate(1, adress='тверская 33'), candidate(2, adress='лесная 99')], 'substring token medium')
add('1234 ²²²² ١٢٣٤', [candidate(1, adress='1234 ²²²² ١٢٣٤ дом'), candidate(2, adress='москва 5555')], 'python digit tokens')
rng = random.Random(5)
words = ['Тверская', 'Лесная', 'Озёрная', 'Примерная', 'город', 'Москва', '№1', '1234', 'Ромашка']
for i in range(100):
    client = rng.choice([' ', '\u00a0', '\u0085']).join(rng.choices(words, k=6))
    candidates = [candidate(j, adress=' '.join(rng.choices(words, k=rng.randint(1, 5))),
                            ooo=rng.choice(words), link='https://example.invalid/route', tell_gruzchik='  12345 ')
                  for j in range(rng.randint(2, 5))]
    add(client, candidates, f'seeded {i}')

texts = []
notes = [None, '', '  ', '11-13ч', '11-13 ч', 'с 12ч', 'с 12 ч', '11 ч', '11:30', 'с 11:30',
         'Позвонить, с 11:30; на склад', '11:30 затем 12:30', 'abc11чx', '١١:٣٠ комментарий',
         'С\u00a012\u0085ч,\tтелефон +70000000000', 'без времени; +70000000000', '11ч .,;:-',
         '11-13часов', '1:99 комментарий', 'до 12 ч после', '11 ч 12 ч', '11ч\u0301', '11ч\u203f']
clients = [None, '', '  Клиент   ', '😀'*85, 'İ'*4+' ул. Примерная', 'Клиент\u0085Москва', 'Клиент\u00a0Лесная']
clients += ['Клиент '+m+' адрес' for m in namespace['ADDRESS_MARKERS']]
for i, client in enumerate(clients):
    for j, note in enumerate(notes):
        texts.append({'name': f'text {i}/{j}', 'client': client, 'note': note,
                      'expected': namespace['parse_order_text'](client or '', note or '')})

data = {}
def put(key, value):
    if value is None:
        data[key] = '~'
    else:
        if isinstance(value, bool): value = str(value).lower()
        data[key] = base64.b64encode(str(value).encode()).decode()
put('resolver.count', len(cases))
for i, case in enumerate(cases):
    prefix = f'resolver.{i}'
    for key in ('name', 'client'): put(f'{prefix}.{key}', case[key])
    put(f'{prefix}.count', len(case['candidates']))
    for j, row in enumerate(case['candidates']):
        for key in ('id', 'ooo', 'adress', 'link', 'tell_klient', 'tell_gruzchik', 'manager_name', 'manager_phone', 'manager_short_phone'):
            put(f'{prefix}.candidate.{j}.{key}', row.get(key))
    for key, value in case['expected'].items(): put(f'{prefix}.expected.{key}', value)
put('text.count', len(texts))
for i, case in enumerate(texts):
    for key in ('name', 'client', 'note'): put(f'text.{i}.{key}', case[key])
    for key in ('title', 'deliveryTime', 'comment'): put(f'text.{i}.expected.{key}', case['expected'][key])
target = ROOT/'app/src/test/resources/p5-golden.properties'
target.write_text('# Offline backend golden; every value is UTF-8 base64, ~ means null.\n'+
                  ''.join(f'{key}={value}\n' for key, value in data.items()))
evidence = ROOT/'docs/direct-rewrite/evidence/p5/golden.json'
evidence.write_text(json.dumps({'source': str(SOURCE), 'source_sha256': hashlib.sha256(SOURCE.read_bytes()).hexdigest(),
    'source_functions': sorted(FUNCTIONS), 'resolver_cases': len(cases), 'text_cases': len(texts),
    'fixture_sha256': hashlib.sha256(target.read_bytes()).hexdigest(), 'live_db_used': False}, indent=2)+'\n')
print(json.dumps({'resolver_cases': len(cases), 'text_cases': len(texts), 'live_db_used': False}))
