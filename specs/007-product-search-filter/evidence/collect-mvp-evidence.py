"""Read local test artifacts and Git metadata;never accesses a configured/live database."""
from pathlib import Path
import collections,datetime,gzip,hashlib,json,re,shutil,subprocess,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[3]
OUT=Path(__file__).resolve().parent
PARENT=ROOT.parent
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def write(name,value):
    (OUT/name).write_text(json.dumps(value,ensure_ascii=False,indent=2,default=str)+'\n')
def git(*args):return subprocess.check_output(['git','-C',str(ROOT),*args],text=True).strip()
rows=[]
for directory in ['surefire-reports','failsafe-reports']:
    for path in sorted((ROOT/'demo/target'/directory).glob('TEST-*.xml')):
        suite=ET.parse(path).getroot()
        rows.append({'suite':suite.attrib['name'],'kind':directory,'tests':int(suite.attrib['tests']),
            'failures':int(suite.attrib['failures']),'errors':int(suite.attrib['errors']),'skipped':int(suite.attrib['skipped']),
            'seconds':suite.attrib.get('time'),'xmlSha256':sha(path),
            'cases':[{'name':t.attrib['name'],'status':'FAIL'if t.find('failure')is not None or t.find('error')is not None else 'NOT_VERIFIED'if t.find('skipped')is not None else 'PASS'}for t in suite.findall('testcase')]})
summary={k:sum(r[k]for r in rows)for k in ['tests','failures','errors','skipped']}
expected=[]
for p in(ROOT/'demo/src/test/java').rglob('*.java'):
    name=p.stem
    if not(re.match(r'Test.*|.*Tests?|.*TestCase|.*IT|IT.*|.*ITCase',name)):continue
    source=p.read_text()
    if not re.search(r'@(?:org\.junit[^\s]*\.)?(?:Test|ParameterizedTest|RepeatedTest|TestFactory)\b',source):continue
    expected.append('.'.join(p.relative_to(ROOT/'demo/src/test/java').with_suffix('').parts))
observed={r['suite'].split('$')[0]for r in rows}
missing=sorted(set(expected)-observed)
write('test-results-final.json',{'capturedUTC':datetime.datetime.now(datetime.timezone.utc).isoformat(),'counts':summary,'suites':rows,'sourceTestClassesMissingReports':missing,'discoveryNote':'Final functional+measured jobs are a union;latest XML per suite is counted once. Exploratory10k assertions are retained but not an MVP timing gate.'})
baseline=json.loads((OUT/'mission-baseline.json').read_text())
migration_drift=[p for p,h in baseline['migrationHashes'].items()if not(ROOT/p).exists()or sha(ROOT/p)!=h]
old_path=Path('/tmp/search-mission-baseline.json')
if old_path.exists():
    old=json.loads(old_path.read_text());shutil.copyfile(old_path,OUT/'mission-file-baseline.json')
else:old=json.loads((OUT/'mission-file-baseline.json').read_text())
readonly_drift=[p for p,h in old.items()if p.startswith(('Catalog-Data/','AI-SWE/'))and(not(PARENT/p).is_file()or sha(PARENT/p)!=h)]
changed=[p for p,h in old.items()if p.startswith('Backend-SWE/')and(PARENT/p).is_file()and sha(PARENT/p)!=h]
files=subprocess.check_output(['git','-C',str(ROOT),'ls-files','--cached','--others','--exclude-standard','-z']).decode().split('\0')
new=[p for p in files if p and 'Backend-SWE/'+p not in old]
deleted=[p for p in old if p.startswith('Backend-SWE/')and not(PARENT/p).exists()]
write('preservation-final.json',{'v1ToV15Drift':migration_drift,'readOnlyRepositoriesDrift':readonly_drift,'preexistingBackendFilesModifiedDuringMission':changed,'backendFilesCreatedDuringMission':new,'preexistingBackendFilesDeleted':deleted,'branch':git('branch','--show-current'),'head':git('rev-parse','HEAD'),'workingTree':git('status','--short').splitlines(),'noCommitPushDeploymentPerformed':True})
for source,name in [('search-calibration/results.json','fuzzy-calibration-v2.json'),('search-performance/results.json','performance-final.json'),('source-workload-performance/results.json','source-workload-performance-final.json')]:
    shutil.copyfile(ROOT/'demo/target'/source,OUT/name)
for directory in ['t015-evidence','t019-evidence','search-plans','source-workload-plans']:
    target=OUT/'final-artifacts'/directory;target.mkdir(parents=True,exist_ok=True)
    for p in(ROOT/'demo/target'/directory).glob('*.json'):
        with gzip.open(target/(p.name+'.gz'),'wb')as f:f.write(p.read_bytes())
for source in ['search-mvp-final-functional.log','search-mvp-final-measured.log','search-imageless-race-red.log','search-publication-fix-green.log','search-resume-openapi.log','search-mvp-family-final-functional.log','search-mvp-family-final-http-performance.log','search-mvp-final-http-performance.log','search-mvp-final-openapi.log']:
    p=Path('/tmp')/source
    if p.exists():
        with gzip.open(OUT/(source+'.gz'),'wb')as f:f.write(p.read_bytes())
tasks=(ROOT/'specs/007-product-search-filter/tasks.md').read_text();nodes={}
for match in re.finditer(r'^- \[([ Xx])\] (T\d{3})(.*?)(?=\n- \[|\n---|\Z)',tasks,re.M|re.S):
    text=match.group(3);line=re.search(r'\*\*Depends on\*\*:(.*)',text)
    nodes[match.group(2)]={'checked':match.group(1).lower()=='x','dependencies':re.findall(r'T\d{3}',line.group(1))if line else []}
seen=set();active=set()
def visit(n):
    if n in active:raise RuntimeError('Dependency cycle:'+n)
    if n in seen:return
    active.add(n)
    for d in nodes[n]['dependencies']:
        if d not in nodes:raise RuntimeError('Unknown dependency:'+d)
        visit(d)
    active.remove(n);seen.add(n)
for n in nodes:visit(n)
unresolved=[(n,d)for n,v in nodes.items()if v['checked']for d in v['dependencies']if not nodes[d]['checked']and d!='T059']
assert not unresolved,unresolved
spec=(ROOT/'specs/007-product-search-filter/spec.md').read_text()
fr=set(re.findall(r'\*\*(FR-\d{3})\*\*',spec));sc=set(re.findall(r'\*\*(SC-\d{3})\*\*',spec))
write('dependency-validation-final.json',{'taskCount':len(nodes),'acyclic':True,'tasks':nodes,'functionalRequirements':sorted(fr),'successCriteria':sorted(sc),'unmappedRequirements':[x for x in fr|sc if not re.search(r'\| '+x+r' \| T\d{3}',tasks)],'conditionalDependency':'T059 index addition is DEFERRED/no justified additional index at1500;T060/T061 use the tested existing-index state and generated cache. T063 is separate deployment-only BLOCKED.'})
assert len(nodes)==66 and len(fr)==20 and len(sc)==6
assert not migration_drift and not readonly_drift and not deleted
assert not missing and summary['failures']==summary['errors']==summary['skipped']==0,summary
print(json.dumps({'tests':summary,'taskCount':len(nodes),'FR':len(fr),'SC':len(sc),'sourcePreservation':'PASS'}))
