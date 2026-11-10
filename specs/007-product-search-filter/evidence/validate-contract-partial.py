"""T006 partial schema/serialization evidence; NOT a full OpenAPI validator."""
from pathlib import Path
import copy
import importlib.metadata
import json
from urllib.parse import urlencode, parse_qs
import yaml
from jsonschema import Draft202012Validator

contract = Path(__file__).resolve().parents[1] / 'contracts/openapi.yaml'
doc = yaml.safe_load(contract.read_text())
for schema in doc['components']['schemas'].values():
    Draft202012Validator.check_schema(schema)

def expect(name, schema, value, valid):
    validator = Draft202012Validator({'components': doc['components'], '$ref': '#/components/schemas/' + schema})
    actual = not list(validator.iter_errors(value))
    assert actual == valid, name

filters = [{'key': 'physical_ram', 'alternatives': [{'op': 'gte', 'value': 16, 'unit': 'GB'}]}]
for name, value, valid in [
    ('quantity shape', filters, True), ('empty', [], False), ('null', None, False),
    ('operator', [{'key': 'physical_ram', 'alternatives': [{'op': 'ne', 'value': 16}]}], False),
    ('extra member', [{'key': 'physical_ram', 'alternatives': [{'op': 'eq', 'value': 16, 'unexpected': 1}]}], False),
    ('boolean', [{'key': 'synthetic_boolean', 'alternatives': [{'op': 'eq', 'value': False}]}], True),
    ('text', [{'key': 'synthetic_text', 'alternatives': [{'op': 'prefix', 'value': 'abc'}]}], True),
]:
    expect(name, 'SpecFilters', value, valid)
uuid = '00000000-0000-0000-0000-000000000001'
item = {'id': uuid, 'name': 'test', 'category': {'id': uuid, 'name': 'test'}, 'primaryImageUrl': None, 'displayPrice': None, 'representativeVariantId': None}
expect('paired null', 'ProductListingItem', item, True)
item.update(displayPrice={'amount': 1, 'currency': 'VND'}, representativeVariantId=uuid)
expect('paired real', 'ProductListingItem', item, True)
mixed = copy.deepcopy(item)
mixed['representativeVariantId'] = None
expect('mixed pair', 'ProductListingItem', mixed, False)
encoded = urlencode({'categoryId': uuid, 'specFilters': json.dumps(filters, separators=(',', ':'))})
assert json.loads(parse_qs(encoded)['specFilters'][0]) == filters
operation = doc['paths']['/api/products']['get']
assert len(operation['parameters']) == 9
assert '409' not in operation['responses'] and '503' in operation['responses']
print('PASS:10 structural cases;schema syntax;URL roundtrip;9 parameters;503/no409. FULL OPENAPI VALIDATION BLOCKED.')
print({name: importlib.metadata.version(name) for name in ['PyYAML', 'jsonschema']})
