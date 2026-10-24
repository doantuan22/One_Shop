import json
import shutil
from pathlib import Path
root=Path(__file__).resolve().parents[2]
base=root/'target/be07-evidence/browser'
out=root/'docs/qa-audit/evidence/browser'
manifest=[]
for mode in ['COD','COD_SPLIT','PICKUP','ONLINE','FAILED','REFUND','REVIEW']:
    dirs=sorted(base.glob(mode+'-*'),key=lambda p:p.stat().st_mtime,reverse=True)
    dirs=[p for p in dirs if (p/'config.json').exists() and json.loads((p/'config.json').read_text())['mode']==mode]
    if not dirs: continue
    latest=dirs[0];dest=out/mode;dest.mkdir(parents=True,exist_ok=True)
    for p in latest.iterdir():
        if p.suffix=='.png' or p.name in {'result.json','error.txt','node.log'}: shutil.copy2(p,dest/p.name)
    manifest.append(dict(mode=mode,source=latest.relative_to(root).as_posix(),result='PASS' if (latest/'result.json').exists() else 'INCOMPLETE'))
out.mkdir(parents=True,exist_ok=True)
(out/'manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
print(json.dumps(manifest,indent=2))
