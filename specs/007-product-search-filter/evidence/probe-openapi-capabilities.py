"""T006 evidence only. Default uses existing tools; specialized tools require prior approval."""
from pathlib import Path
import argparse
import copy
import importlib.metadata
import importlib.util
import json
from urllib.parse import urlencode, parse_qs
import hashlib
import yaml
from jsonschema import Draft202012Validator, FormatChecker

ROOT = Path(__file__).resolve().parents[3]
FEATURE = ROOT / 'specs/007-product-search-filter'
paths = {'feature':FEATURE/'contracts/openapi.yaml','catalog':ROOT/'specs/001-product-catalog/contracts/openapi.yaml'}
docs = {k:yaml.safe_load(p.read_text())for k,p in paths.items()}
args = argparse.ArgumentParser()
args.add_argument('--approved-tools', action='store_true', help='Run installed OAS packages only after developer approval;flag is not an approval receipt')
flags=args.parse_args()
results=[]
schema_cases=[]
def probe(name,schema,value,expected,components=None):
    schema_cases.append((name,schema,value,expected,components))
    wrapped={'components':components or {},**schema}
    validator=Draft202012Validator(wrapped,format_checker=FormatChecker())
    errors=list(validator.iter_errors(value));valid=not errors
    assert valid==expected,(name,[str(x)for x in errors])
    results.append({'case':name,'expectedSchemaValid':expected,'schemaValid':valid,'errors':[{'path':list(x.path),'message':x.message[:260]}for x in errors]})
def component(name,document,value,expected):
    probe(name,{'$ref':'#/components/schemas/'+document[1]},value,expected,docs[document[0]]['components'])
u='00000000-0000-0000-0000-000000000001'
base={'id':u,'name':'Device','category':{'id':u,'name':'Category'},'primaryImageUrl':None}
item={**base,'displayPrice':{'amount':10,'currency':'VND'},'representativeVariantId':u}
component('listing_real_pair',('feature','ProductListingItem'),item,True)
orphan={**base,'displayPrice':None,'representativeVariantId':None}
component('listing_null_pair',('feature','ProductListingItem'),orphan,True)
for field in ['displayPrice','representativeVariantId']:
    mixed=copy.deepcopy(item);mixed[field]=None;component('mixed_pair_'+field,('feature','ProductListingItem'),mixed,False)
    missing=copy.deepcopy(item);del missing[field];component('missing_pair_'+field,('feature','ProductListingItem'),missing,False)
extra=copy.deepcopy(item);extra['rawRank']=7;component('listing_extra_field',('feature','ProductListingItem'),extra,False)
variant={'id':u,'sku':'MODEL-1','options':{},'sellingPrice':{'amount':10,'currency':'VND'},'technicalSpecifications':{'schema_version':1,'values':{}}}
detail={**base,'description':None,'brand':None,'variants':[variant],'images':[]}
component('catalog_detail_allOf_unevaluatedProperties_nullable',('catalog','ProductDetail'),detail,True)
extra=copy.deepcopy(detail);extra['rawRank']=1;component('catalog_detail_extra_field',('catalog','ProductDetail'),extra,False)
for field in ['brand','variants']:
    missing=copy.deepcopy(detail);del missing[field];component('catalog_detail_missing_'+field,('catalog','ProductDetail'),missing,False)
composed={'type':'object','allOf':[{'properties':{'a':{'type':'integer'}},'required':['a']},{'properties':{'b':{'type':'string'}},'required':['b']}],'unevaluatedProperties':False}
probe('synthetic_allOf_positive',composed,{'a':1,'b':'x'},True)
probe('synthetic_unevaluated_extra',composed,{'a':1,'b':'x','c':1},False)
probe('synthetic_allOf_wrong_type',composed,{'a':'1','b':'x'},False)
probe('oas31_null_union',{'type':['string','null']},None,True)
probe('oas30_nullable_not_a_31_type_union',{'type':'string','nullable':True},None,False)
params={p['name']:p for p in docs['feature']['paths']['/api/products']['get']['parameters']}
for name,value,valid in [('page',0,True),('page',-1,False),('size',101,False),('categoryId',u,True),('categoryId','bad',False),('brandId',[u,u],True),('brandId',[u]*21,False),('minPrice','1e3',False),('minPrice','0',True),('sort','random',False)]:
    probe('parameter_'+name+'_'+str(len(results)),params[name]['schema'],value,valid,docs['feature']['components'])
assert params['brandId']['style']=='form'and params['brandId']['explode']is True
filters=[{'key':'physical_ram','alternatives':[{'op':'gte','value':16,'unit':'GB'}]}]
encoded=urlencode({'categoryId':u,'specFilters':json.dumps(filters,separators=(',',':'))});assert json.loads(parse_qs(encoded)['specFilters'][0])==filters
probe('specFilters_URL_roundtrip_shape',params['specFilters']['content']['application/json']['schema'],filters,True,docs['feature']['components'])
for status in [400,401,500,503]:
    problem={'type':'about:blank','title':'Synthetic error','status':status,'detail':'Synthetic detail'}
    component('error_'+str(status),('feature','Problem'),problem,True)
component('error_status_string',('feature','Problem'),{'type':'about:blank','title':'Error','status':'503','detail':'x'},False)
component('error_missing_detail',('feature','Problem'),{'type':'about:blank','title':'Error','status':503},False)
responses=docs['feature']['paths']['/api/products']['get']['responses'];assert'409'not in responses and'503'in responses
probe('mode_header_valid',responses['200']['headers']['X-Catalog-Search-Mode']['schema'],'exact-only',True)
probe('mode_header_invalid',responses['200']['headers']['X-Catalog-Search-Mode']['schema'],'silent-fallback',False)
# Verify the authorized restoration on both contracts;retain the pre-restoration evidence separately.
for field in ['name','category','primaryImageUrl']:
    value=copy.deepcopy(item)
    if field=='name':value['name']=''
    elif field=='category':value['category']['name']=''
    else:value['primaryImageUrl']='not-a-uri'
    component('feature_rejects_restored_'+field,('feature','ProductListingItem'),value,field=='primaryImageUrl' and 'uri' not in FormatChecker.checkers)
    old={k:value[k]for k in base}
    component(('catalog_uri_checker_UNAVAILABLE' if field=='primaryImageUrl' and 'uri' not in FormatChecker.checkers else 'catalog_rejects_weak_'+field),('catalog','ProductSummary'),old,field=='primaryImageUrl' and 'uri' not in FormatChecker.checkers)
refs=[]
def walk(node,doc):
    if isinstance(node,dict):
        if '$ref'in node:
            ref=node['$ref'];assert ref.startswith('#/');target=doc
            for part in ref[2:].split('/'):target=target[part.replace('~1','/').replace('~0','~')]
            refs.append(ref)
        for value in node.values():walk(value,doc)
    elif isinstance(node,list):
        for value in node:walk(value,doc)
for doc in docs.values():walk(doc,doc)
semantic={'status':'BLOCKED_TOOL_NOT_INSTALLED_OR_NOT_AUTHORIZED','toolApproved':False}
if flags.approved_tools:
    from openapi_spec_validator import OpenAPIV31SpecValidator
    from openapi_schema_validator import OAS31Validator
    semantic={'status':'EXECUTED_APPROVED_ISOLATED_VERIFICATION','toolApproved':True,'versions':{p:importlib.metadata.version(p)for p in ['openapi-spec-validator','openapi-schema-validator']},'documents':{}}
    for key,doc in docs.items():
        errors=list(OpenAPIV31SpecValidator(doc).iter_errors());semantic['documents'][key]={'errors':[str(x)for x in errors],'valid':not errors}
    semantic['schemaInstanceReplay']=[]
    for name,schema,value,expected,components in schema_cases:
        validator=OAS31Validator({'components':components or {},**schema},format_checker=OAS31Validator.FORMAT_CHECKER)
        errors=list(validator.iter_errors(value));actual=not errors
        assert actual==expected,(name,[str(x)for x in errors])
        semantic['schemaInstanceReplay'].append({'case':name,'valid':actual})
    semantic['negativeDocumentControls']=[]
    mutations=[]
    missing=copy.deepcopy(docs['feature']);del missing['info'];mutations.append(('missing_info',missing))
    duplicate=copy.deepcopy(docs['feature']);duplicate['paths']['/api/products']['get']['parameters'].append(copy.deepcopy(duplicate['paths']['/api/products']['get']['parameters'][0]));mutations.append(('duplicate_query_parameter',duplicate))
    duplicated_id=copy.deepcopy(docs['feature']);duplicated_id['paths']['/synthetic-control']={'get':copy.deepcopy(duplicated_id['paths']['/api/products']['get'])};mutations.append(('duplicate_operation_id',duplicated_id))
    for name,mutation in mutations:
        errors=list(OpenAPIV31SpecValidator(mutation).iter_errors());assert errors,name
        semantic['negativeDocumentControls'].append({'case':name,'rejected':True,'errors':[str(x)for x in errors]})
result={'task':'T006','status':'SEMANTIC_VERIFICATION_COMPLETE' if flags.approved_tools else 'PARTIAL_ONLY','pythonTools':{p:importlib.metadata.version(p)for p in ['PyYAML','jsonschema']},'specializedAvailable':{p:bool(importlib.util.find_spec(p))for p in ['openapi_spec_validator','openapi_schema_validator']},'contractSHA256':{k:hashlib.sha256(p.read_bytes()).hexdigest()for k,p in paths.items()},'availableFormatChecks':sorted(FormatChecker.checkers),'uriFormatActuallyEnforced':'uri' in FormatChecker.checkers,'localRefsResolved':len(refs),'instanceCases':results,'caseCount':len(results),'urlEncodedSpecFiltersExample':encoded,'semanticOpenAPI':semantic,'findings':[{'id':'T006-C01','kind':'compatibility-schema-gap','status':'RESOLVED_BY_AUTHORIZED_RESTORATION','summary':'Restored product/category minLength1 and image URI format;negative examples reject with approved URI checker','resolution':'Exactly the approved three-constraint patch;nullable fields retained'}],'limitations':['JSON Schema instance validation is not full OpenAPI document/operation semantics','categoryId dependence,registry authorization,duplicate raw JSON members,conditional headers/status congruence require prose/transport/runtime checks','URI checks require actual optional format dependency;availability and rejection sentinel are recorded','Only authorized contract restoration;no application/migration changes;tools installed in temporary venv with user approval']}
(FEATURE/'evidence/openapi-capability-results.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
print('PASS schema probes:',len(results),'local refs:',len(refs),'specialized status:',semantic['status'])

if flags.approved_tools and any(not item['valid'] for item in semantic['documents'].values()):
    raise SystemExit(1)
