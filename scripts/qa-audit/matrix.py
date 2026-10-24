import json
from pathlib import Path
root=Path(__file__).resolve().parents[2]
out=root/'docs/qa-audit'
data=json.loads((out/'evidence/inventory.json').read_text(encoding='utf-8'))
def cell(s): return str(s).replace('|','\\|').replace('\n',' ')
lines=['# Function & flow inventory', '', 'Kiểm kê cấu trúc; ID F là entry point, không đồng nghĩa một chức năng nghiệp vụ chỉ có một flow. Chi tiết source/hash, methods, UI, enum, branch candidates: `evidence/inventory.json`.', '', '| ID | HTTP | Entry point | Controller:line | Method |', '|---|---|---|---|---|']
matrix=['# Flow test matrix', '', 'Thiết kế trước execution. Candidate suite là gợi ý theo module, KHÔNG phải bằng chứng mapping đã thực thi. Mỗi route cần các flow: happy; auth/role/owner; invalid/missing/boundary input; failure/rollback nếu ghi; stale/retry/concurrency nếu shared state. Tất cả decision candidates và UI candidates giữ trạng thái NOT_RECONCILED đến khi có evidence cụ thể.', '', '| Function | Entry point | Flow/branch design | Test ID | Status | Evidence |', '|---|---|---|---|---|---|']
for e in data['endpoints']:
    lines.append('| '+' | '.join(map(cell,[e['id'],e['verb'],e['path'],e['controller']+':'+str(e['line']),e['method']]))+' |')
    matrix.append('| '+' | '.join(map(cell,[e['id'],e['verb']+' '+e['path'],'happy / authorization / validation / error'+(' / rollback / retry / race' if e['verb']=='POST' else ''),'PLAN-'+e['id'],'NOT_RECONCILED','inventory.json; execution reconciliation pending']))+' |')
for id,route in [('TECH-LOGOUT','POST /logout'),('TECH-ERROR','ANY /error')]:
    lines.append(f'| {id} | framework | {route} | SecurityConfig / Spring Boot | technical |')
    matrix.append(f'| {id} | {route} | CSRF, cookie clearing, redirect / error dispatch | PLAN-{id} | NOT_RECONCILED | framework |')
lines+=['','## Service methods', '', '| ID | Source | Method | Inputs |','|---|---|---|---|']
for m in data['methods']:
    if '/service/' in m['file']:
        lines.append('| '+' | '.join(map(cell,[m['id'],m['file']+':'+str(m['line']),m['method'],m['input']]))+' |')
lines+=['','## UI actions and decision candidates','','Every U/B ID is in `evidence/inventory.json` with file, line and exact source. No candidate is assumed exercised from a page HTTP 200. Semantic deduplication and condition/decision mapping remain open.']
(out/'SYSTEM_INVENTORY.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
(out/'FLOW_TEST_MATRIX.md').write_text('\n'.join(matrix)+'\n',encoding='utf-8')
