"""T004 observational proposal only. Does not implement/approve a production classifier."""
from pathlib import Path
import collections
import csv
import hashlib
import json
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
EVIDENCE = ROOT / 'specs/007-product-search-filter/evidence'
DATA = ROOT.parent / 'Catalog-Data/data'
FILES = {name: DATA / name for name in ['products.jsonl', 'variants.jsonl', 'specifications.jsonl', 'approvals/amazon_import_approved_1500.csv']}
products = [json.loads(line) for line in FILES['products.jsonl'].read_text().splitlines() if line.strip()]
variants = [json.loads(line) for line in FILES['variants.jsonl'].read_text().splitlines() if line.strip()]
specifications = [json.loads(line) for line in FILES['specifications.jsonl'].read_text().splitlines() if line.strip()]
with FILES['approvals/amazon_import_approved_1500.csv'].open() as source:
    approvals = list(csv.DictReader(source))
assert len(products) == len(variants) == len(specifications) == len(approvals) == 1500
by_asin = {row['parent_asin']: (i + 1, row) for i, row in enumerate(products)}
approval_by_asin = {row['parent_asin']: row for row in approvals}
texts = [row['product']['name'] for row in products]
texts += [row['variant']['sku_proposal'] for row in variants]
texts += [(row['product'].get('brand_reference_proposal') or {}).get('name') or '' for row in products]
report = ET.parse(ROOT / 'demo/target/surefire-reports/TEST-com.example.demo.catalog.search.ProductSearchNormalizerTest.xml').getroot()
classpath = next(p.get('value') for p in report.findall('./properties/property') if p.get('name') == 'java.class.path')
work = Path('/tmp/t004-observation');work.mkdir(exist_ok=True)
inputs = work / 'inputs.json';inputs.write_text(json.dumps(texts, ensure_ascii=False))
subprocess.run(['javac', '-cp', classpath, '-d', str(work), str(EVIDENCE / 'NormalizationParityProbe.java')], check=True)
result = subprocess.run(['java', '-cp', str(work) + ':' + classpath, 'NormalizationParityProbe', str(inputs)], capture_output=True, text=True, check=True)
normalized = json.loads(result.stdout)
def tokens(index):
    return {token['text'] for token in normalized[index].get('tokens', [])}
name_tokens = [tokens(i) for i in range(1500)]
brand_tokens = [tokens(i) for i in range(3000,4500)]
def example(index):
    row = products[index];p = row['product'];a = approval_by_asin[row['parent_asin']]
    return {'sourceFile':'products.jsonl','line':index+1,'parentAsin':row['parent_asin'],
            'proposedProductId':p['id_proposal'],'nameExcerpt':p['name'][:180],
            'brandObservation':p.get('brand_reference_proposal'), 'category':p['category_label'],
            'skuFromImportApprovalFile':a['approved_sku'],'skuPolicyApproval':a['sku_policy_approval'],
            'publicationApproval':a['publication_approval']}
families = {'galaxy':'samsung','iphone':'apple','ipad':'apple','xperia':'sony','redmi':'xiaomi',
            'thinkpad':'lenovo','ideapad':'lenovo','inspiron':'dell','vivobook':'asus','zenbook':'asus',
            'macbook':'apple','chromebook':'platform term;device-brand anchor required','elitebook':'hp',
            'celeron':'intel','pentium':'intel','ryzen':'amd','snapdragon':'qualcomm','geforce':'nvidia','radeon':'amd'}
ambiguous = ['core','pro','air','note','plus','max','studio','surface','pavilion','aspire','envy','spectre','pixel','nord','vega','graphics','hd','uhd','rog','tuf','legion']
def observation(term, anchor=None):
    hits = [i for i, ts in enumerate(name_tokens) if term in ts]
    brand_hits = [i for i, ts in enumerate(brand_tokens) if term in ts]
    return {'term':term,'brandOrTechnicalContextEvidence':anchor,'productNameExactTokenRecords':len(hits),
            'brandNameExactTokenRecords':len(brand_hits),'examples':[example(i) for i in hits[:3]],
            'status':'PROPOSED_NOT_APPROVED'}
candidates = [observation(term,anchor) for term,anchor in families.items()]
manual_models = [('B0C5ZC7FWR','wh-1000xm5'),('B08DBDSVBC','wh-1000xm4'),('B08PPSRLQ5','a515f'),('B09L88FZ95','pro-i'),('B081V6W99V','l210'),('B08R7D4KZJ','be2026')]
anchors = []
for asin,model in manual_models:
    line,row = by_asin[asin];assert model in name_tokens[line-1],(asin,model)
    anchors.append({'normalizedModelToken':model,'role':'device-model equality candidate for rank7 only after sign-off',
                    'evidence':example(line-1),'status':'PROPOSED_NOT_APPROVED'})
spec_keys = ['cpu_model','cpu_family','gpu_model']
spec_observations = {}
for key in spec_keys:
    rows=[(i+1,row)for i,row in enumerate(specifications)if key in row['technical_specifications']['values']]
    counts=collections.Counter(str(row['technical_specifications']['values'][key])for _,row in rows)
    spec_observations[key]={'records':len(rows),'mostFrequent':counts.most_common(12),
                            'activation':'UNVERIFIED;observation only;no technical-key search or filter approval',
                            'examples':[{'line':line,'parentAsin':row['parent_asin'],'approval':row['approval'],'value':row['technical_specifications']['values'][key]}for line,row in rows[:3]]}
context_rules = {
 'core':'Only anchored Intel Core or Samsung Galaxy ... Core spans;not generic core/processor descriptions',
 'pavilion':'HP Pavilion','aspire':'Acer Aspire','envy':'HP Envy','spectre':'HP Spectre',
 'pixel':'Google Pixel','nord':'OnePlus Nord','vega':'AMD Radeon Vega','legion':'Lenovo Legion',
 'surface':'Microsoft Surface including compatibility references;not generic surface descriptions',
 'pro':'Only qualifier inside a reviewed full model/family span;not standalone professional/audio prose',
 'air':'Only qualifier inside reviewed Apple MacBook/iPad Air span;not airflow descriptions',
 'note':'Only reviewed Samsung Galaxy Note/Redmi Note span;not note-taking prose',
 'plus':'Only qualifier in reviewed device span;not generic additive description',
 'max':'Only qualifier in reviewed device span;not maximum-rating prose',
 'studio':'No global identifier rule from current evidence;observed use is descriptive audio/studio',
 'graphics':'No global identifier rule;generic feature label,not a model identity',
 'hd':'Short token already non-fuzzy;not model identity','uhd':'Short token already non-fuzzy;not model identity',
 'rog':'Short token already non-fuzzy;ASUS context is family,not automatic rank7',
 'tuf':'Short token already non-fuzzy;ASUS context is family,not automatic rank7'}
report={'task':'T004' ,'proposalVersion':'identifier-scope-proposal-2026-11-09-v1','status':'BLOCKED_PENDING_REVIEW',
        'approval':None,'productionVocabularyEnabled':False,
        'sources':{name:{'path':str(path.relative_to(ROOT.parent)),'sha256':hashlib.sha256(path.read_bytes()).hexdigest()}for name,path in FILES.items()},
        'normalizerSHA256':hashlib.sha256((ROOT/'demo/src/main/java/com/example/demo/catalog/search/ProductSearchNormalizer.java').read_bytes()).hexdigest(),
        'sourceCounts':{'products':len(products),'variants':len(variants),'specifications':len(specifications),'importApprovalRows':len(approvals)},
        'specificationApprovalCounts':dict(collections.Counter(row['approval']for row in specifications)),
        'sourceSkuProposalEqualsImportApprovedSku':sum(row['variant']['sku_proposal']==approval_by_asin[row['parent_asin']]['approved_sku']for row in variants),
        'javaNormalizationRejectedSourceNames':[products[i]['parent_asin']for i in range(1500)if not normalized[i]['accepted']],
        'candidateFamilies':candidates,'ambiguousNotGlobalVocabulary':[observation(term)for term in ambiguous],
        'contextRecognitionProposals':context_rules,'manualDeviceModelAnchors':anchors,'specificationObservations':spec_observations,
        'proposedRecognitionPolicy':{'familyTerms':'Exact normalized whole-token exclusion for the19 listed proper family terms on both query and Product/Brand candidate names;conservative loss of fuzzy recall for homonyms requires sign-off','ambiguousTerms':'Recognize only in the exact reviewed brand/family context spans on query/candidates;unanchored generic uses are not automatically identifiers','modelPromotion':'Only6 product-scoped anchored device model tokens;families,component terms and quantities do not automatically get rank7','sourceGuard':'Candidate protection is checked independently,so misspelled query models cannot enter via fuzzy matching against protected candidates','normalization':'Use existing T007 D2;no new alias/transliteration/unit conversion;immutable indexed primitives remain structural only'},'limits':['Source-package observations,not live DB verification','All source specifications UNVERIFIED;T003 untouched','Nineteen family proposals need context review;not nineteen approved model IDs','Explicit model anchors are product-scoped;no inferred model column','No aliasing,correction,unit conversion or fuzzy identifier admission proposed']}
(EVIDENCE/'identifier-vocabulary-proposal.json').write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n')
print(json.dumps({'sourceCounts':report['sourceCounts'],'familyCandidates':len(candidates),'modelAnchors':len(anchors),'specApprovals':report['specificationApprovalCounts'],'skuMatches':report['sourceSkuProposalEqualsImportApprovedSku'],'rejectedNames':len(report['javaNormalizationRejectedSourceNames'])}))
