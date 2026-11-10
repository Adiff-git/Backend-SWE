# T013 reproducible verifier;requires a fresh,labelled,network-isolated tmpfs container.
from pathlib import Path
import subprocess,json,unicodedata,hashlib,datetime,xml.etree.ElementTree as E
import sys
r=Path('specs/007-product-search-filter/evidence');name=sys.argv[1]
owned=json.loads(subprocess.check_output(['docker','inspect','--format','{{json .}}',name],text=True))
assert owned['Config']['Labels'].get('speckit.task')=='T013'
assert owned['HostConfig']['NetworkMode']=='none' and not owned['Mounts']
assert '/var/lib/postgresql/data' in owned['HostConfig']['Tmpfs']
assert not owned['HostConfig']['PortBindings']
fixtures=json.loads(Path('demo/src/test/resources/catalog/search/normalization-cases.json').read_text());inputs=[x['input']for x in fixtures]
inputs += [None,'',' \t','ĐẮNG điện thoại Ờ Ự Ỷ',unicodedata.normalize('NFD','ĐẮNG điện thoại Ờ Ự Ỷ'),'samsung samsung','𐐀','İ ΣΟΣ','٣.٥','a\x01b','a\x7fb','***']
whites=list(range(9,14))+[32,133,160,5760]+list(range(8192,8203))+[8232,8233,8239,8287,12288]
inputs += [chr(cp)+'Điện'+chr(cp)+'THOẠI'+chr(cp)for cp in whites]
assert json.loads((r/'normalization-parity-inputs.json').read_text())==inputs
report=E.parse('demo/target/surefire-reports/TEST-com.example.demo.catalog.search.ProductSearchNormalizerTest.xml').getroot();cp=next(x.get('value') for x in report.findall('./properties/property') if x.get('name')=='java.class.path');Path('/tmp/t013-probe').mkdir(exist_ok=True)
subprocess.run(['javac','-cp',cp,'-d','/tmp/t013-probe',str(r/'NormalizationParityProbe.java')],check=True)
p=subprocess.run(['java','-cp','/tmp/t013-probe:'+cp,'NormalizationParityProbe',str(r/'normalization-parity-inputs.json')],capture_output=True,text=True,check=True);java=json.loads(p.stdout)
def sql(s,check=True):
 p=subprocess.run(['docker','exec','-i',name,'psql','-X','-At','-v','ON_ERROR_STOP=1','-U','postgres','-d','normalization_parity'],input=s,text=True,capture_output=True)
 if check and p.returncode:raise RuntimeError(p.stderr)
 return p
results=[]
for item in java:
 raw=item['input'];expr='NULL::text' if raw is None else "convert_from(decode('"+raw.encode().hex()+"','hex'),'UTF8')"
 statement=f"SELECT json_build_object('normalized',catalog_search_normalize_v1({expr}),'tokens',(SELECT coalesce(json_agg(json_build_object('text',token,'protected',structurally_protected)),'[]'::json) FROM catalog_search_tokens_v1({expr})));"
 p=sql(statement,False);out={'accepted':p.returncode==0}
 if p.returncode==0:out.update(json.loads(p.stdout))
 else:out['error']=p.stderr.strip()
 match=item['accepted']==out['accepted'] and(not item['accepted']or(item['normalized']==out['normalized']and item['tokens']==out['tokens']))
 results.append({'input':raw,'java':item,'postgresql':out,'equal':match})
for i,fixture in enumerate(fixtures):
 assert [t['text']for t in java[i]['tokens']]==fixture['tokens']
 assert [t['protected']for t in java[i]['tokens']]==fixture['protected']
audit=sql("SELECT json_build_object('version',version(),'encoding',current_setting('server_encoding'),'collation',(SELECT row_to_json(c) FROM (SELECT collname,collprovider,collisdeterministic,collversion,pg_collation_actual_version(oid) AS actual_version FROM pg_collation WHERE collname='und-x-icu')c),'functions',(SELECT json_agg(row_to_json(f)) FROM (SELECT oid::regprocedure::text AS signature,provolatile,proparallel FROM pg_proc WHERE proname LIKE 'catalog_search_%' OR oid IN ('lower(text)'::regprocedure,'normalize(text,text)'::regprocedure,'substr(text,integer,integer)'::regprocedure,'char_length(text)'::regprocedure,'ascii(text)'::regprocedure,'chr(integer)'::regprocedure,'textregexeq(text,text)'::regprocedure,'textcat(text,text)'::regprocedure)) f));")
a=json.loads(audit.stdout);immutable=all(x['provolatile']=='i'for x in a['functions']);assert immutable
# Exercise the exact prototype as an expression index only on a tiny disposable proof table.
index=sql("CREATE TABLE parity_index_probe(value text); INSERT INTO parity_index_probe VALUES('Điện thoại'),('WH-1000XM5'); CREATE INDEX parity_normalize_idx ON parity_index_probe(catalog_search_normalize_v1(value)); SELECT indisvalid FROM pg_index WHERE indexrelid='parity_normalize_idx'::regclass;")
identity=json.loads(subprocess.check_output(['docker','inspect','--format','{{json .}}',name],text=True));identity={k:identity[k]for k in ['Id','Image','Name','Created','HostConfig']};identity['HostConfig']={k:identity['HostConfig'][k]for k in ['NetworkMode','Tmpfs','Binds','PortBindings']}
report={'capturedUTC':datetime.datetime.now(datetime.timezone.utc).isoformat(),'scope':'D2 structural parity,not vocabulary/search/production index approval','identity':identity,'prototypeSHA256':hashlib.sha256((r/'normalization-prototype.sql').read_bytes()).hexdigest(),'javaNormalizerSHA256':hashlib.sha256(Path('demo/src/main/java/com/example/demo/catalog/search/ProductSearchNormalizer.java').read_bytes()).hexdigest(),'caseCount':len(results),'matched':sum(x['equal']for x in results),'cases':results,'audit':a,'immutableAuditPass':immutable,'expressionIndexProbe':{'exitCode':index.returncode,'stdout':index.stdout},'cleanup':'pending'}
Path('/tmp/t013-reproduction-results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print('PARITY',report['matched'],'/',report['caseCount'],'IMMUTABLE',immutable)
for item in results:
 if not item['equal']:print('MISMATCH',json.dumps(item,ensure_ascii=False))

assert all(x["equal"] for x in results), "Normalization mismatch:do not approve this prototype"
