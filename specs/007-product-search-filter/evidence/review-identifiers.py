"""T004 reviewer evidence only:no production classifier,fuzzy scorer or ranker is implemented."""
from pathlib import Path
import hashlib
import json
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
E = ROOT/'specs/007-product-search-filter/evidence'
proposal = json.loads((E/'identifier-vocabulary-proposal.json').read_text())
review = json.loads((E/'identifier-review-cases.json').read_text())
draft_path = ROOT/'demo/src/test/resources/catalog/search/identifier-cases.json'
drafts = json.loads(draft_path.read_text())['cases']
assert len(drafts)==16 and len(review['reviewCases'])==14
for source in proposal['sources'].values():
    assert hashlib.sha256((ROOT.parent/source['path']).read_bytes()).hexdigest()==source['sha256']
products = [json.loads(line) for line in (ROOT.parent/'Catalog-Data/data/products.jsonl').read_text().splitlines()if line.strip()]
by_asin = {x['parent_asin']:x for x in products}
families = {x['term'] for x in proposal['candidateFamilies']}
models = {(x['evidence']['parentAsin'],x['normalizedModelToken'])for x in proposal['manualDeviceModelAnchors']}
positive_context = {'core':'Intel Core i5-10110U','pro':'Apple iPhone Pro','air':'Apple MacBook Air','note':'Samsung Galaxy Note','plus':'Samsung Galaxy Plus','max':'Apple iPhone Pro Max','studio':'Sony studio monitor headphones','surface':'Microsoft Surface Pro','pavilion':'HP Pavilion','aspire':'Acer Aspire','envy':'HP Envy','spectre':'HP Spectre','pixel':'Google Pixel','nord':'OnePlus Nord','vega':'AMD Radeon Vega','graphics':'Intel graphics feature','hd':'HD display','uhd':'UHD display','rog':'ASUS ROG family','tuf':'ASUS TUF family','legion':'Lenovo Legion'}
rows=[]
normal_rank='No model-rank7 promotion;ordinary whole-name/phrase/token/prefix/partial rules remain R3. Eligibility/publication/same-variant constraints first.'
for i,item in enumerate(proposal['candidateFamilies'],1):
    term=item['term'];rows.append({'id':f'F{i:02}','term':term,'classification':'PRODUCT_FAMILY','sourceEvidence':item['examples'],'recognitionRule':'Proposed exact normalized whole-token fuzzy exclusion in query/candidate names;not substring,alias or auto model. Homonym recall cost still needs final sign-off.','expectedRanking':normal_rank,'positiveCase':{'id':f'M-F{i:02}-P','input':term,'expected':'Matches this proposed family entry only if approved'},'negativeCase':{'id':f'M-F{i:02}-N','input':term+'x','expected':'Does not equal this family entry;no fuzzy identifier admission or implicit alias'},'relatedCases':['IV03','IV04','IV13','D13','D14']})
for i,item in enumerate(proposal['manualDeviceModelAnchors'],1):
    term=item['normalizedModelToken'];rows.append({'id':f'M{i:02}','term':term,'classification':'SOURCE_VERIFIED_MODEL_ANCHOR','sourceEvidence':[item['evidence']],'recognitionRule':'Complete-query normalized equality with this exact Product-scoped device model token;reconcile actual owner identity first. Not a compatibility target/global substring.','expectedRanking':'Proposed rank7 only for whole-query equality to the reviewed anchored model after eligibility;different identifiers never gain fuzzy admission. No other source/owner gets this promotion automatically.','positiveCase':{'id':f'M-M{i:02}-P','input':term,'expected':'Exact equality to this proposed owner-bound model anchor'},'negativeCase':{'id':f'M-M{i:02}-N','input':term+'x','expected':'Not exact anchor equality;no rank7 from this anchor,no fuzzy. Existing prefix/partial rules are not redefined.'},'relatedCases':['IV01','IV02','IV11','D01','D02','D11','D12']})
for i,item in enumerate(proposal['ambiguousNotGlobalVocabulary'],1):
    term=item['term'];rule=proposal['contextRecognitionProposals'][term]
    rows.append({'id':f'A{i:02}','term':term,'classification':'CONTEXT_DEPENDENT_AMBIGUOUS','sourceEvidence':item['examples'],'recognitionRule':rule+';query/candidate context is checked independently. Never assume missing context is a verified model.','expectedRanking':normal_rank,'positiveCase':{'id':f'M-A{i:02}-P','input':positive_context[term],'expected':('Generic/descriptive,not identifier'if term in ['studio','graphics']else'Short-token protection independent of vocabulary'if term in ['hd','uhd','rog','tuf']else'Context-only exclusion proposal;not a verified classifier result')},'negativeCase':{'id':f'M-A{i:02}-N','input':'generic '+term+' description','expected':'No automatic global model identity or rank7;generic/short uses follow their documented rules'},'relatedCases':['IV05','IV06','IV07','IV08','IV09','IV10','IV12','IV14']})
assert len(rows)==46
# Execute ONLY the existing structural primitive,not a new identifier classifier.
inputs=[case['input']for case in drafts]+[case['input']for case in review['reviewCases']]
inputs += [case['input'] for row in rows for case in [row['positiveCase'],row['negativeCase']]]
source_inputs=[row['product']['name']for row in products]
all_inputs=list(dict.fromkeys(inputs+source_inputs))
work=Path('/tmp/t004-final-review');work.mkdir(exist_ok=True);input_file=work/'inputs.json';input_file.write_text(json.dumps(all_inputs,ensure_ascii=False))
xml=ET.parse(ROOT/'demo/target/surefire-reports/TEST-com.example.demo.catalog.search.ProductSearchNormalizerTest.xml').getroot()
cp=next(p.get('value')for p in xml.findall('./properties/property')if p.get('name')=='java.class.path')
subprocess.run(['javac','-cp',cp,'-d',str(work),str(E/'NormalizationParityProbe.java')],check=True)
p=subprocess.run(['java','-cp',str(work)+':'+cp,'NormalizationParityProbe',str(input_file)],capture_output=True,text=True,check=True)
actual={x['input']:x for x in json.loads(p.stdout)}
results=[]
for i,case in enumerate(drafts,1):
    a=actual[case['input']];assert a['accepted'];assert [t['text']for t in a['tokens']]==case['expectedTokens'];assert [t['protected']for t in a['tokens']]==case['structurallyProtected']
    results.append({'id':f'D{i:02}','input':case['input'],'validation':'PASS_EXECUTED_D2_PRIMITIVE','actual':a,'runtimeIdentifierClassifierOrRankingTest':False,'finalSignOff':None})
for case in review['reviewCases']:
    key=case['case'];a=actual[case['input']];assert a['accepted'];source=case['source'];checks=[]
    if source:
        assert source in by_asin;checks.append('Source ASIN exists in unchanged package')
    source_tokens = {t['text'] for t in actual[by_asin[source]['product']['name']]['tokens']} if source else set()
    required_source_tokens = {'IV01':{'wh-1000xm5'},'IV03':{'galaxy','a51'},'IV05':{'microsoft','surface'},'IV06':{'any','surface'},'IV07':{'hp','pavilion'},'IV08':{'google','pixel'},'IV09':{'galaxy','a03','core'},'IV10':{'studio','monitor'}}
    if key in required_source_tokens:
        assert required_source_tokens[key] <= source_tokens,(key,source_tokens)
        checks.append('Actual source title tokens corroborate the claimed role/context')
    if key=='IV06':assert 'microsoft'not in source_tokens
    if key=='IV02':
        assert not any({'i7-1360p','c++'} & {t['text']for t in actual[row['product']['name']]['tokens']} for row in products)
        checks.append('Both examples absent from source title token corpus;synthetic label confirmed')
    if key=='IV04':assert (by_asin[source]['product'].get('brand_reference_proposal')or{})['name'].lower()=='samsung'
    if key=='IV11':
        match = next(x for x in proposal['manualDeviceModelAnchors']if x['evidence']['parentAsin']==source)
        assert match['evidence']['skuFromImportApprovalFile']==case['input']
        checks.append('Input matches the actual import-file SKU observation')
    if key=='IV14':
        specs=[json.loads(line)for line in (ROOT.parent/'Catalog-Data/data/specifications.jsonl').read_text().splitlines()if line.strip()]
        entry=next(x for x in specs if x['parent_asin']==source)
        assert entry['approval']=='UNVERIFIED'and entry['technical_specifications']['values']['cpu_model']=='Exynos 9611'
        checks.append('Actual UNVERIFIED cpu_model value corroborated without activating its key')
    if key=='IV01':assert ('B0C5ZC7FWR','wh-1000xm5')in models;checks.append('Exact proposed model anchor exists')
    if key=='IV02':assert [t['protected']for t in a['tokens']]==[True,True];checks.append('Both synthetic grammar tokens structurally protected')
    if key=='IV03':assert 'galaxy'in families and 'galaxi'not in families;checks.append('Canonical/typo distinct,no implicit alias in proposal')
    if key=='IV04':assert 'samsung'not in families and 'sony'not in families;checks.append('Brand words not globally blacklisted by family list')
    if key in ['IV05','IV06']:assert 'surface'not in families;checks.append('Surface requires context;not a global family or model anchor')
    if key=='IV07':assert 'pavilion'not in families and proposal['contextRecognitionProposals']['pavilion']=='HP Pavilion';checks.append('HP-context proposal exists')
    if key=='IV08':assert 'pixel'not in families and proposal['contextRecognitionProposals']['pixel']=='Google Pixel';checks.append('Google-context proposal exists')
    if key=='IV09':assert 'core'not in families;checks.append('Core not global family whitelist')
    if key=='IV10':assert 'studio'not in families;checks.append('Studio not globally promoted')
    if key=='IV11':assert all(t['protected']for t in a['tokens']);checks.append('SKU-shaped string structurally protected;live canonical ownership NOT_VERIFIED')
    if key=='IV12':assert [t['text']for t in a['tokens']]==['16gb','16','gb','16.0','3.50mm'];checks.append('No implicit unit/numeric/model rewrite')
    if key=='IV13':assert 'galaxy'in families;checks.append('Homonym recall trade-off retained for final human decision')
    if key=='IV14':assert proposal['specificationApprovalCounts']=={'UNVERIFIED':1500};assert 'exynos'not in families;checks.append('Unverified technical value does not activate vocabulary/registry')
    results.append({'id':key,'input':case['input'],'validation':'PASS_SOURCE_PRIMITIVE_AND_PROPOSAL_CONSISTENCY_ONLY','checks':checks,'actual':a,'runtimeIdentifierClassifierOrRankingTest':False,'finalSignOff':None})
for row in rows:
    for sample in [row['positiveCase'],row['negativeCase']]:
        assert actual[sample['input']]['accepted'];sample['executedPrimitive']=actual[sample['input']]
        sample['policyOutcome']='EXPECTED_PROPOSAL_ONLY;classifier/fuzzy/ranking execution deferred'
    if row['classification']=='PRODUCT_FAMILY':assert row['negativeCase']['input']not in families
    if row['classification']=='SOURCE_VERIFIED_MODEL_ANCHOR':
        source=row['sourceEvidence'][0]['parentAsin'];name=by_asin[source]['product']['name'];assert row['term']in [t['text']for t in actual[name]['tokens']]
        assert (source,row['negativeCase']['input'])not in models
    row['sourceEvidenceVerified']=all(example['parentAsin']in by_asin for example in row['sourceEvidence'])
    row['finalSignOff']=None
matrix={'candidateScopeAcceptedForReview':True,'finalVocabularyApproval':None,'enabledInApplication':False,'counts':{'families':19,'models':6,'ambiguous':21},'proposalVersion':proposal['proposalVersion'],'rows':rows}
(E/'identifier-review-matrix.json').write_text(json.dumps(matrix,ensure_ascii=False,indent=2)+'\n')
summary={'task':'T004','status':'BLOCKED_PENDING_EXPLICIT_FINAL_SIGNOFF','draftCases':16,'reviewCases':14,'casesChecked':30,'structuralPrimitiveCasesPassed':16,'reviewSourceAndPolicyCasesChecked':14,'matrixWitnessInputsChecked':92,'runtimeClassifierFuzzyRankingCasesExecuted':0,'finalVocabularyApproval':None,'caseResults':results,'originalDraftSHA256':hashlib.sha256(draft_path.read_bytes()).hexdigest(),'matrixSHA256':hashlib.sha256((E/'identifier-review-matrix.json').read_bytes()).hexdigest(),'limits':['Thirty cases checked at primitive/source/proposal level only','No production identifier/fuzzy/ranking implementation or sign-off implied','Six anchors are source verified,not proof of live canonical ownership','T003 remains outside scope']}
(E/'identifier-review-results.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n')
def escape(s):return s.replace('|','/').replace('\n',' ')
lines=['# Complete T004 reviewer matrix','', '**46 entries:19 family terms,6 source-verified model anchors,21 context-dependent terms.** Candidate review scope was technically accepted by the user;final vocabulary/context approval is still absent. Nothing is enabled in the application.','', 'Sources are unchanged Catalog-Data package records,not live DB proof. Every JSON row carries source excerpts/ASIN/line,recognition rule,expected ranking,and concrete positive/negative witness inputs. Ranking is an expectation for later implementation after eligibility,not an executed ranker result.','', '| ID | Term/identifier | Classification | Source evidence | Recognition/context | Expected ranking | Positive / negative cases |','| --- | --- | --- | --- | --- | --- | --- |']
for row in rows:
    source=row['sourceEvidence'][0];src=f"products.jsonl:{source['line']} / {source['parentAsin']}"
    cases=f"{row['positiveCase']['id']}: {row['positiveCase']['input']} / {row['negativeCase']['id']}: {row['negativeCase']['input']} ; related {','.join(row['relatedCases'])}"
    lines.append('| '+' | '.join(escape(str(x))for x in [row['id'],row['term'],row['classification'],src,row['recognitionRule'],row['expectedRanking'],cases])+' |')
lines += ['', '## Thirty original/review cases','', '| Case | Input | Validation actually performed | Pending |','| --- | --- | --- | --- |']
for result in results:lines.append('| '+' | '.join(escape(str(x))for x in [result['id'],result['input'],result['validation'],'Final sign-off;runtime classifier/fuzzy/ranking tests'])+' |')
lines+=['','All16 original D2 expectations matched actual existing Java primitive outputs. All14 review scenarios were checked for existing source identity,primitive behavior and proposal invariants where applicable. Ninety-two matrix witness inputs also tokenize successfully;their context/ranking expectations are review material,not92 classifier passes. No universal model recognition,calibrated fuzzy acceptance or approved technical registry is inferred.','', 'Final reviewer decision must identify accepted/rejected entries,context rules,version/hashes,reviewer/date and limitations. T004 remains unchecked until that explicit final decision. T005 cannot start merely because this matrix exists.','', 'Artifacts:[matrix JSON](identifier-review-matrix.json),[case results](identifier-review-results.json),[source vocabulary proposal](identifier-vocabulary-proposal.json). Reproduce:`python3 specs/007-product-search-filter/evidence/review-identifiers.py`.','']
(E/'identifier-review-matrix.md').write_text('\n'.join(lines))
print('PASS:16 D2 cases;14 source/primitive/proposal checks;46 matrix entries/92 witness inputs. Final T004 sign-off PENDING.')
