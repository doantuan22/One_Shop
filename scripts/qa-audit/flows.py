"""Dependency and entry-point records; static links are explicitly distinct from verified runtime flows."""
import json
import re
from pathlib import Path
root=Path(__file__).resolve().parents[2]
out=root/'docs/qa-audit/evidence'
data=json.loads((out/'inventory.json').read_text(encoding='utf-8'))
sources={p.stem:(p.relative_to(root).as_posix(),p.read_text(encoding='utf-8-sig')) for p in (root/'src/main/java').rglob('*.java')}
implementations={}
for name,(file,text) in sources.items():
    for interfaces in re.findall(r'\bimplements\s+([\w, ]+)\s*\{',text):
        for interface in interfaces.replace(' ','').split(','):implementations.setdefault(interface,[]).append(name)

def closure(name):
    visited=set();todo=[name]
    while todo:
        node=todo.pop()
        if node in visited or node not in sources:continue
        visited.add(node)
        text=sources[node][1]
        todo+=implementations.get(node,[])
        todo+=re.findall(r'private\s+(?:final\s+)?(\w+(?:Service|Repository|Policy|Engine|Mapper|Limiter|Validator|Calculator|Gateway|Writer))\s+\w+',text)
    return sorted(visited)

records=[]
for e in data['endpoints']:
    name=Path(e['controller']).stem;deps=closure(name);path=e['path']
    actor='ADMIN' if path.startswith('/admin') else 'STAFF + ACTIVE assignment' if path.startswith('/staff') else 'CUSTOMER' if re.match(r'^/(cart|checkout|orders|reviews)',path) else 'Authenticated' if path.startswith('/account') else 'Guest / public or technical deny-all'
    module=name.replace('Controller','').replace('Admin','Admin/').replace('Staff','Staff/')
    files={sources[d][0] for d in deps}
    repo=[sources[d][0] for d in deps if d.endswith('Repository')]
    services=[sources[d][0] for d in deps if '/service/' in sources[d][0]]
    text=sources[name][1]
    views=re.findall(r'"([a-z][\w-]*/[\w/-]+)"',text)
    related=sorted({f'src/main/resources/templates/{v}.html' for v in views if (root/f'src/main/resources/templates/{v}.html').exists()})
    branches=[b['id'] for b in data['decision_candidates'] if b['file'] in files]
    errors=sorted({x for d in deps for x in re.findall(r'throw new (\w+Exception)',sources[d][1])})
    inputs=[m for m in data['methods'] if m['file']==e['controller'] and m['method']==e['method']]
    writes=[dict(file=sources[d][0],line=i,source=line.strip()) for d in deps for i,line in enumerate(sources[d][1].splitlines(),1)
            if re.search(r'\.(?:save(?:AndFlush)?|delete\w*|setQuantity|setStatus|setOrderStatus|setPaymentStatus|uploadImage|deleteImage)\(',line)]
    records.append(dict(FunctionID=e['id'],Module=module,Actor=actor,EntryPoint=e['verb']+' '+path,Controller=e['controller']+':'+str(e['line']),
                        Service=services,Repository=repo,Input=[m['input'] for m in inputs],
                        Preconditions='SecurityConfig role/CSRF plus method-specific guards; see branch IDs. Not fully decision-reconciled.',
                        BusinessRules='Current V3 code/schema and actual test assertions, not absent target spec. Detailed audit: BUG_REPORT.md.',
                        Branches=branches,SideEffects=writes,FailurePaths=errors,RelatedUI=related,
                        TestIDs=['QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards: '+e['verb']+' '+path],
                        Coverage='HTTP guard probe mapping only; transitive branches and UI actions NOT_RECONCILED',
                        DependencyLinkKind='Heuristic static field/implementation graph, not a runtime call trace'))
(out/'function-flow-records.json').write_text(json.dumps(records,ensure_ascii=False,indent=2),encoding='utf-8')
print(f'{len(records)} detailed entry-point records; transitive links remain static candidates')
