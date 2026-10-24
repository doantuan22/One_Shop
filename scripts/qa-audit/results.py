"""Summarize Surefire without exporting JVM properties, raw HTML, JWTs, passwords or binary hashes."""
import collections
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

root=Path(__file__).resolve().parents[2]
phase=sys.argv[1]
source=root/(sys.argv[2] if len(sys.argv)>2 else 'target/surefire-reports')
out=root/'docs/qa-audit/evidence'
cases=[]
for p in sorted(source.glob('TEST-*.xml')):
    audit=p.name.startswith('TEST-com.oneshop.QaAudit')
    if phase=='baseline' and audit: continue
    if (phase in {'additional','reproduce'} or phase.startswith('harness')) and not audit: continue
    if phase=='reproduce' and not any(x in p.name for x in ['QaAuditRace','QaAuditImageRollback','QaAuditScreenBrowser']): continue
    suite=ET.parse(p).getroot()
    for case in suite.findall('testcase'):
        issue=next((n for n in case if n.tag in {'error','failure','skipped'}),None)
        status='PASS' if issue is None else {'error':'ERROR','failure':'FAIL','skipped':'SKIPPED'}[issue.tag]
        # Failure suffix captures expected/actual instead of full response HTML which can contain CSRF values.
        message='' if issue is None else issue.get('message','')
        if len(message)>1000:
            message=message[:120]+' ... [HTML/large body omitted] ... '+message[-650:]
        message=re.sub(r'eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+','[JWT REDACTED]',message)
        message=re.sub(r'(value=")[A-Za-z0-9_-]{40,}(" )',r'\1[REDACTED]\2',message)
        trace=[] if issue is None else [line.strip() for line in (issue.text or '').splitlines() if re.match(r'\s*at com\.oneshop\.',line)][:6]
        cases.append(dict(id=f'{phase}-{len(cases)+1:04}',suite=case.get('classname',suite.get('name')),
                          scenario=case.get('name'),status=status,seconds=case.get('time'),
                          timestamp=suite.get('timestamp'),evidence=p.relative_to(root).as_posix(),message=message,trace=trace))
summary=dict(phase=phase,cases=cases,counts=dict(collections.Counter(c['status'] for c in cases)),
             total=len(cases),suites=len(set(c['suite'] for c in cases)))
out.mkdir(parents=True,exist_ok=True)
(out/f'{phase}-results.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
screen=out/'screens/results.json'
if phase in {'additional','reproduce'} and screen.exists():
    (out/f'screens-{phase}-results.json').write_bytes(screen.read_bytes())
print(json.dumps({k:v for k,v in summary.items() if k!='cases'},indent=2))
