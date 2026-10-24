"""Generate transparent, evidence-backed summaries. Unknown semantic coverage stays unknown."""
import collections
import hashlib
import json
import re
import shutil
import xml.etree.ElementTree as ET
from pathlib import Path

root=Path(__file__).resolve().parents[2]
out=root/'docs/qa-audit'; ev=out/'evidence'
load=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
data=load(ev/'inventory.json')
runs=[load(ev/f'{name}-results.json') for name in ['baseline','additional'] if (ev/f'{name}-results.json').exists()]
cases=[c for run in runs for c in run['cases']]
counts=collections.Counter(c['status'] for c in cases)
routes=load(ev/'routes/runtime-mappings.json')
route_keys={(r['verb'],r['path']) for r in routes}
probes=[load(p) for p in (ev/'routes').glob('*.json') if p.name!='runtime-mappings.json']
screens=load(ev/'screens/results.json')
repro=load(ev/'reproduce-results.json') if (ev/'reproduce-results.json').exists() else None

coverage={}; missed=[]
xml=root/'target/site/jacoco/jacoco.xml'
if xml.exists():
    shutil.copy2(xml,ev/'jacoco.xml')
    csv=xml.with_suffix('.csv')
    if csv.exists():shutil.copy2(csv,ev/'jacoco.csv')
    report=ET.parse(xml).getroot()
    for c in report.findall('counter'):
        hit,total=int(c.get('covered')),int(c.get('covered'))+int(c.get('missed'))
        coverage[c.get('type')]=dict(covered=hit,total=total,percent=round(100*hit/total,2) if total else None)
    for pkg in report.findall('package'):
        for source in pkg.findall('sourcefile'):
            lines=[int(line.get('nr')) for line in source.findall('line') if int(line.get('mi'))>0 or int(line.get('mb'))>0]
            if lines:missed.append(dict(file='src/main/java/'+pkg.get('name')+'/'+source.get('name'),lines=lines))
    (ev/'jacoco-uncovered-lines.json').write_text(json.dumps(missed,indent=2),encoding='utf-8')

def cell(x):return str(x).replace('|','\\|').replace('\n',' ')
def table(headers,rows):return '\n'.join(['| '+' | '.join(headers)+' |','|'+'|'.join(['---']*len(headers))+'|']+['| '+' | '.join(cell(x) for x in row)+' |' for row in rows])
def write(name,text):(out/name).write_text(text+'\n',encoding='utf-8')

summ=dict(primaryRuns=[{k:v for k,v in r.items() if k!='cases'} for r in runs],primaryCounts=dict(counts),primaryTotal=len(cases),
          runtimeMappingRecords=len(routes),uniqueRoutePairs=len(route_keys),httpProbeRequests=len(probes),
          httpProbesPass=sum(p['expected']==p['actual'] for p in probes),
          screenCheckpoints=dict(collections.Counter(c['status'] for c in screens['results'])),
          reproduction=None if repro is None else {k:v for k,v in repro.items() if k!='cases'},coverage=coverage,
          semanticFunctionCoverage=None,semanticFlowCoverage=None,semanticDecisionCoverage=None)
(ev/'summary.json').write_text(json.dumps(summ,ensure_ascii=False,indent=2),encoding='utf-8')

body=['# Test execution report','', '## Environment','',
      '2026-10-24, Windows, JDK 21.0.12.1, Spring Boot 3.5.16, SQL Server 2025 17.0.1145.4. Database riêng `oneshop_qa_20261024_test`; Hibernate validate; timeout scheduler OFF. Embedded Tomcat thật trên random ports; Chrome headless qua CDP, profile tạm riêng. Không gọi payment/refund ngân hàng thật. Cloudinary write chỉ qua test double.', '',
      '## Primary runs (không cộng rerun vào unique coverage)','',table(['Phase','Total','PASS','FAIL','ERROR','SKIPPED'],[[r['phase'],r['total'],r['counts'].get('PASS',0),r['counts'].get('FAIL',0),r['counts'].get('ERROR',0),r['counts'].get('SKIPPED',0)] for r in runs]),'',
      'ERROR ở baseline do fixture vi phạm one-active-store constraint: phân loại BLOCKED cho scenario đó, không tự coi là bug sản phẩm. FAIL khác phải đọc BUG_REPORT và HARNESS_NOTES. Tất cả test asserts vẫn được giữ nguyên.', '',
      '## Test input và expected','',
      'Test ID dưới đây ánh xạ chính xác suite/method và chỉ số parameter trong Surefire. Input/expected assertion đầy đủ nằm trong source test tương ứng; bảng không suy diễn input từ tên test. Các test bổ sung có input/expected/actual HTTP trong `evidence/boundaries/`, `evidence/routes/`; race có before/after SQL trong `evidence/races/`; rollback trong `evidence/image-rollback/`.', '',
      table(['ID','Suite / Scenario','Status','Seconds','Executed (suite start)','Evidence'],[[c['id'],c['suite']+'#'+c['scenario'],c['status'],c['seconds'],c['timestamp'],c['evidence']] for c in cases]),'',
      '## Failures / errors: exact assertion excerpts','']
for c in cases:
    if c['status']!='PASS':body+=['### '+c['id']+' — '+c['suite']+'#'+c['scenario'],'','```text',c['message'],'\n'.join(c['trace']),'```','']
body+=['## Browser checkpoints (không cộng thêm vào số JUnit cases)','',table(['Scenario','Status','Actual / evidence'],[[s['id'],s['status'],s.get('error',s.get('detail',''))] for s in screens['results']]),'',
       'Bảy business journeys COD/COD_SPLIT/PICKUP/ONLINE/FAILED/REFUND/REVIEW được chạy bằng Chrome và xác nhận SQL trong BrowserV3DatabaseIntegrationTest. Trace và screenshots: `evidence/browser/`. Screen render PASS không chứng minh mọi button trên screen đã được click.', '',
       '## Reproduction','',json.dumps(summ['reproduction'],ensure_ascii=False,indent=2) if repro else 'NOT_RUN', '',
       '## Blocked / chưa có bằng chứng đầy đủ','',
       '- Cloudinary thật: BLOCKED_BY_SAFETY — chưa có tenant test được xác định; mock SDK/storage và fault injection đã chạy, không thay thế live verification.',
       '- Mười fixture multi-assignment baseline: BLOCKED_BY_SPEC_CONFLICT — SQL hiện tại từ chối trước hành vi cần test.',
       '- Semantic true/false condition coverage và mapping từng UI action: NOT_RECONCILED, không thể tính số chưa chạy chính xác từ line markers.',
       '- Cắt kết nối SQL Server thật, HTTP connection loss sau commit, mọi phối hợp hai Admin sửa cùng một record: chưa có execution evidence đầy đủ. Không phá dịch vụ local dùng chung.',
       '- Scheduler cadence/restart recovery thực tế: worker được giữ OFF; tests gọi engine trực tiếp.']
body+=['','## Build / final SQL check','','`target/qa-audit/package.log`: BUILD SUCCESS với `-DskipTests` sau các test runs độc lập; không coi package thành công là full-test PASS. `target/qa-audit/coverage.log`: JaCoCo report success. Final SQL check trong `evidence/database-inspection-inspect.txt`: bảy invariant queries đều 0 violations; constraints được DBCC kiểm tra. Đây là trạng thái sau fixture cleanup, không xóa các bằng chứng lỗi trung gian.']
write('TEST_EXECUTION_REPORT.md','\n'.join(body))

matrix=['# Function → flow → branch → execution matrix','',
        'Tất cả route runtime có guard mapping. Đây KHÔNG phải chứng nhận full business/happy-path/condition coverage. Route ANY chỉ được probe GET trong bảng này; hai handler /error khác produces được kiểm kê riêng trong JSON. Static resources do Spring handler phục vụ không phải từng MVC controller mapping.', '',
        table(['Function','Entry point','Flow / branch','Test source','Execution','Evidence'],[
            [f'R{i:03}',r['verb']+' '+r['path'],'anonymous; wrong roles + valid JWT/CSRF where protected',
             'QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards',
             'PASS' if all(p['actual']==p['expected'] for p in probes if p['route']['path']==r['path'] and p['route']['verb']==r['verb']) else 'FAIL',
             'evidence/routes/'] for i,r in enumerate(routes,1)]),'',
        '## Deeper execution mapping','',
        table(['Module / Flow','Tests','Evidence / coverage caveat'],[
            ['Auth / JWT / CSRF / profile','Auth*, AccessControl*, AccountControllerTest, QaAuditRace*','baseline/additional-results.json; profile race violates revocation'],
            ['Catalog / Category / Brand / Store','Catalog*, ProductRequestValidationTest, CategoryServiceTest','baseline-results.json; stale UI expectations and finder spec conflict remain'],
            ['Cart ownership / quantity / concurrent writes','Cart*, CentralCartIntegrationTest','baseline-results.json; no claim every UI quantity event covered'],
            ['Allocation / checkout / split / consent / fault / last stock','Checkout*, AllocationEngineTest, QaAuditAllocationInvariantTest','SQL-backed suites vs pure generated tests distinguished'],
            ['Order / Fulfillment / legal & illegal transitions','OrderState*, Fulfillment*, StaffFulfillment*, Delivery*, Pickup*','baseline-results.json; multi-assignment scenarios blocked by canonical constraint'],
            ['Payment / retry / cancel / refund / worker engine','Payment*, PartialRefund*, AdminAttention*','baseline-results.json; simulation only, worker engine not cadence'],
            ['Admin users / assignments / dashboards / inventory','Phase11*, Admin*, Staff*','baseline-results.json; complete unique use-case mapping still open'],
            ['Reviews / reply / moderation / race','Review*, Phase9*, Phase10*','baseline-results.json; conflicting multi-assignment fixtures not PASS'],
            ['Images / primary race / transaction rollback','CloudinaryServiceTest, Phase12History*, QaAuditRace*, QaAuditImageRollback*','actual SQL + simulated provider; no live upload'],
            ['All HTTP readers page/date boundaries','QaAuditBoundaryDatabaseIntegrationTest','evidence/boundaries/: concrete inputs and HTTP status'],
            ['Browser small actions / modals / logout / navigation','QaAuditScreenBrowserDatabaseIntegrationTest','evidence/screens/results.json; individual checkpoint details'],
            ['Browser cross-module order/payment/review journeys','BrowserV3DatabaseIntegrationTest','evidence/browser/: 7 modes + SQL assertions']]),'',
        '## Non-reconciled source candidates','',
        f"{len(data['decision_candidates'])} B IDs và {len(data['ui_actions'])} U IDs có file:line/source trong inventory.json. Tất cả còn cần mapping semantic chính xác tới testcase/condition. Không đánh PASS chỉ vì dòng code được chạy. Các F/M/U/B không bị xóa khỏi inventory vì thiếu coverage."]
write('FLOW_TEST_MATRIX.md','\n'.join(matrix))

coverage_text=['# Coverage report','',
    '## Đo bằng công cụ','',table(['Counter','Covered','Total','Percent'],[[k,v['covered'],v['total'],v['percent']] for k,v in coverage.items()]),'',
    'JaCoCo 0.8.12, source production không đổi, execution file riêng phiên audit `target/qa-audit/session-20261024.exec`. Báo cáo máy đọc `evidence/jacoco.xml`, `evidence/jacoco.csv`; HTML ở `target/site/jacoco/index.html`. Bao gồm unit, mock-backed HTTP và real-SQL tests; không được gọi đây là E2E coverage.', '',
    '## Coverage nghiệp vụ','',table(['Metric','Actual','Meaning / limitation'],[
        ['Controller route declarations',len(data['endpoints']),'structural scan; constants resolved'],
        ['Runtime mapping records',len(routes),'includes two Boot /error handlers + Security logout'],
        ['Unique HTTP verb/path pairs',len(route_keys),'ANY represented by one GET probe; all verbs not exhausted'],
        ['Guard probe requests',len(probes),'one or more negative auth/input probes per route; not happy path'],
        ['Primary JUnit invocations',len(cases),'parameterized/repeated invocations, not semantic scenarios'],
        ['UI screen checkpoints',len(screens['results']),'do not add again to JUnit totals'],
        ['Source decision candidates',len(data['decision_candidates']),'heuristic lines, not a semantic branch denominator'],
        ['Source UI candidates',len(data['ui_actions']),'line-level links/forms/events/conditions; not unique user actions'],
        ['Semantic function / flow / condition coverage','NOT MEASURED','full de-duplication, manual branch mapping not completed'],
        ['Unexecuted semantic test cases','UNKNOWN','cannot subtract source line candidates from JUnit counts']]),'',
    '## Uncovered / partially covered code','',
    'Every source line with missed instructions or branches is listed in `evidence/jacoco-uncovered-lines.json`. Concrete source candidates and 34 enum/state declarations are in `inventory.json`. Remaining work includes all UI events, every condition in validation chains, every persistence failure point, stale record updates across all CRUD modules, and transport-failure retry.', '',
    'No percentage is supplied for function/flow coverage without a valid denominator. High line coverage cannot negate the confirmed failures.']
write('COVERAGE_REPORT.md','\n'.join(coverage_text))

summary_text=['# Final audit summary — phạm vi chưa hoàn tất exhaustive','',
    '**Đã chạy backend, SQL Server và Chrome thật; đã chứng minh 8 bug. Chưa đạt điều kiện tuyên bố hoàn tất toàn bộ phạm vi exhaustive. Không có thay đổi production code.**','',
    '## Số liệu thực tế','',
    table(['Metric','Result'],[
        ['Source files structurally inventoried',len(data['files'])],
        ['Controller route declarations',len(data['endpoints'])],
        ['Runtime mapping records / unique verb-path',f'{len(routes)} / {len(route_keys)}'],
        ['Database tables / repositories','23 / 23'],
        ['Enum/action/state declarations',len(data['enums'])],
        ['Heuristic branch lines / UI candidate lines',f"{len(data['decision_candidates'])} / {len(data['ui_actions'])}"],
        ['Semantic functions / all flows / all branches','Chưa đủ semantic reconciliation để có denominator chính xác'],
        ['Primary Surefire records',len(cases)],
        ['PASS / FAIL / ERROR / SKIPPED',f"{counts.get('PASS',0)} / {counts.get('FAIL',0)} / {counts.get('ERROR',0)} / {counts.get('SKIPPED',0)}"],
        ['BLOCKED interpretation','10 fixture errors + 3 live Cloudinary skipped; không cộng hai lần vào raw totals'],
        ['Guard probes / matching expected status',f"{len(probes)} / {sum(p['expected']==p['actual'] for p in probes)}"],
        ['Latest Chrome screen checkpoints',dict(collections.Counter(c['status'] for c in screens['results']))],
        ['Cross-module Chrome journeys','7 modes PASS, verified SQL; included in baseline, not added twice'],
        ['Generated allocation cases','3 seeds × 1,000 cases; 3 JUnit invocations, unit-level not E2E'],
        ['Line coverage',coverage.get('LINE')],['Branch coverage',coverage.get('BRANCH')],
        ['Not-run semantic cases','UNKNOWN, không tự suy đoán từ source marker counts']]),'',
    '## Bug đã xác nhận','',
    table(['Severity','Count','Bugs'],[
        ['CRITICAL',1,'QA-001: profile khôi phục account ACTIVE / role vừa bị Admin revoke'],
        ['HIGH',1,'QA-004: rollback metadata nhưng ảnh cũ đã bị xóa tại storage boundary'],
        ['MEDIUM',5,'QA-002 primary image race; QA-003 orphan upload; QA-005 page overflow; QA-006 date overflow; QA-008 category image 404'],
        ['LOW',1,'QA-007 double-escaped catalog browser title']]),'',
    'Số FAIL không bằng số bug: một bug có nhiều input/lần lặp; baseline còn các assertion/fixture lệch policy. Chi tiết tái hiện, before/after SQL, root cause và remediation trong BUG_REPORT.md.', '',
    '## Mô hình và khả năng vận hành','',
    'Runtime là V3 thật: giá Product, cart Product, central ONLINE Order nhiều Fulfillments, consent COD split. Không tự triển khai/chuyển đổi nghiệp vụ. Staff hiện có tối đa một assignment ACTIVE theo SQL và service; 11 scenario baseline cũ mâu thuẫn policy này (10 ERROR, một FAIL). Các khác biệt UI text/finder chưa bị đánh thành runtime business bug khi thiếu spec xác nhận.', '',
    'Các hành trình checkout/payment/fulfillment/review chính đã chạy với SQL checks, nhưng không đủ để kết luận an toàn vận hành. QA-001 trực tiếp phá bảo đảm thu hồi truy cập; nên ưu tiên chặn trước khi đưa hệ thống vào sử dụng với tài khoản thật. Ảnh/rollback và reader boundary vẫn cần khắc phục. Không có kết luận an toàn cho payment/refund thật vì dự án chỉ có simulation.', '',
    '## Ưu tiên sửa','',
    '1. QA-001: profile chỉ patch cột được phép, dùng lock/version phù hợp với Admin, kiểm tra revoke race.',
    '2. QA-004 / QA-003: lifecycle external asset gắn commit/rollback và retry compensation.',
    '3. QA-002: serialize primary image selection + DB uniqueness.',
    '4. QA-005 / QA-006: validate page offset/date theo giới hạn kỹ thuật, trả 4xx hợp lý.',
    '5. QA-008 / QA-007: sửa asset reference có xác nhận và title escaping.',
    '6. Chốt tài liệu hiện hành, sửa fixture/assertion cũ theo policy đã phê duyệt; không bỏ constraint để che test lỗi.', '',
    '## Phần chưa hoàn tất / chưa đủ bằng chứng','',
    '- Toàn bộ 1.430 source decision candidates chưa được map semantic từng true/false/AND/OR sang execution. JaCoCo đo bytecode branch, không chứng minh đầy đủ business decision coverage.',
    '- 1.113 UI candidate lines đã inventory nhưng chưa tất cả có thao tác browser riêng; page render không phải mọi button/modal/AJAX đã test.',
    '- Mọi failure point, transport loss sau commit/retry, mọi stale CRUD và mọi cặp concurrency theo prompt chưa có full execution mapping. Các race đã test không được suy rộng cho mọi module.',
    '- Live Cloudinary: có request đọc asset từ browser và phát hiện 404; upload/delete/rollback provider thật chưa test vì không có tenant test được xác định. Ba live cases SKIPPED được công khai.',
    '- Scheduler engine đã test trực tiếp, nhưng scheduler cadence thực tế giữ OFF để tránh background writes ngoài fixture.',
    '- Thiếu các tài liệu V3/runbook được README tham chiếu. Không khẳng định đầy đủ mọi business DEC khi thiếu tài liệu gốc.', '',
    '## Bằng chứng và tái chạy','',
    '`TEST_EXECUTION_REPORT.md`, `FLOW_TEST_MATRIX.md`, `SYSTEM_INVENTORY.md`, `COVERAGE_REPORT.md`; JSON/XML/CSV/screenshots và manifest SHA-256 trong `evidence/`. Raw Maven/Surefire/HTML coverage trong `target/`. Database audit được giữ lại, không drop; fixture phục hồi nội dung, identity counters có thể tăng.', '',
    'Runner: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/qa-audit/run.ps1 -Phase baseline|audit|reproduce|coverage|package` (chọn một Phase mỗi lần, không chạy hai writer suites trên cùng DB). `database.ps1 -Action provision` chỉ dành cho tên DB mới chưa tồn tại; script từ chối database có sẵn.']
write('FINAL_AUDIT_SUMMARY.md','\n'.join(summary_text))

# Supplement initial source inventory with authoritative runtime mapping and exact state/action declarations.
inv=(out/'SYSTEM_INVENTORY.md').read_text(encoding='utf-8').split('\n## Runtime reconciliation')[0]
inv+='\n## Runtime reconciliation\n\n'+table(['ID','HTTP','Path','Controller','Method'],[[f'R{i:03}',r['verb'],r['path'],r['controller'],r['method']] for i,r in enumerate(routes,1)])
inv+='\n\n## State / enum declarations\n\n'
for enum in data['enums']:
    inv+=f"### {enum['name']} — {enum['file']}:{enum['line']}\n\n```java\n{enum['source'].strip()}\n```\n\n"
inv+='## Runtime model\n\nV3 được xác nhận cả source, 23-table SQL Server schema và bảy Chrome business journeys. Product.price là giá online; CartItem.product; ONLINE một Order/N Fulfillments; COD split cần consent; shipping 30.000/Order, pickup miễn phí. Legacy StoreProduct price/store selectors vẫn tồn tại nhưng không quyết định nguồn hàng V3. Staff hiện chỉ một ACTIVE Store (filtered unique index); test cũ multi-assignment mâu thuẫn policy hiện hành. Không áp kỳ vọng V2 lên V3 hoặc ngược lại.\n'
inv+='\n## Missing functionality / external boundaries\n\nKhông tìm thấy SMTP/forgot-password/address CRUD controllers/cổng ngân hàng thật; địa chỉ chỉ đọc trên profile và dùng cho checkout. Không tự đánh runtime BUG cho các chức năng chưa triển khai. README trỏ một số đặc tả và runbook không có trong checkout: SPEC GAP về tài liệu.\n'
inv+='\n## Detailed function records\n\n`evidence/function-flow-records.json` chứa đủ FunctionID, Module, Actor, EntryPoint, Controller, Service, Repository, Input, Preconditions, BusinessRules, Branches, SideEffects, FailurePaths, RelatedUI, TestIDs và Coverage cho 115 khai báo route. Dependency links là phân tích tĩnh từ field/interface, không giả làm runtime trace; mỗi điểm chưa reconcile được ghi rõ. `inventory.json` giữ tất cả methods, U/B IDs, file hashes và state declarations.\n'
write('SYSTEM_INVENTORY.md',inv)

manifest=[]
for p in sorted(ev.rglob('*')):
    if p.is_file() and p.name!='manifest-sha256.json':
        manifest.append(dict(path=p.relative_to(out).as_posix(),size=p.stat().st_size,sha256=hashlib.sha256(p.read_bytes()).hexdigest()))
(ev/'manifest-sha256.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
print(json.dumps(summ,ensure_ascii=False,indent=2))
