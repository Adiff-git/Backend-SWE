"""Export review artifacts without staging or modifying Git metadata."""
from pathlib import Path
import subprocess, hashlib, json
ROOT=Path(__file__).resolve().parents[3]
FEATURE=ROOT/'specs/013-variant-review-sale'
def git(*args):
    result=subprocess.run(['git',*args],cwd=ROOT,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
    if result.returncode not in (0,1):raise RuntimeError(result.stderr.decode(errors='replace'))
    return result.stdout
tracked=git('diff','--name-only','--','demo','docs').decode().splitlines()
new=git('ls-files','--others','--exclude-standard','--','demo','docs').decode().splitlines()
files=sorted(set(tracked+new))
patch=git('diff','--','demo','docs')
for name in new:patch+=git('diff','--no-index','--','NUL',name)
(FEATURE/'implementation.diff').write_bytes(patch)
head=git('rev-parse','HEAD').decode().strip()
manifest={'baseline':head,'branch':git('branch','--show-current').decode().strip(),'patchSha256':hashlib.sha256(patch).hexdigest(),'scope':'Current demo/ and docs/ changes, including untracked sources; excludes unrelated output and generated evidence. Per-run logs identify previously tested revisions.','files':[{'path':name,'sha256':hashlib.sha256((ROOT/name).read_bytes()).hexdigest()} for name in files]}
(FEATURE/'validation/working-tree-source-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
lines=['# Implementation changed files','','Against baseline `'+head+'`. '+str(len(files))+' code, test, migration and API documentation files. Includes new sources without staging. Performance and unrestricted verification are deferred; see implementation-validation.md. Existing unrelated output/ is excluded.','','| Status | File |','| --- | --- |']
for name in files:lines.append('| '+('New' if name in new else 'Modified')+' | ['+name+'](<'+(ROOT/name).as_posix()+'>) |')
lines+=['','Feature specification, plan, tasks and validation evidence are in [the feature directory](<'+FEATURE.as_posix()+'>). The patch contains the code/API changes above; feature evidence and logs are linked separately.']
(FEATURE/'changed-files.md').write_text('\n'.join(lines)+'\n')
print(json.dumps({'files':len(files),'new':len(new),'patchBytes':len(patch),'patchSha256':manifest['patchSha256']}))
