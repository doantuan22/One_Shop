import collections,fnmatch,json,re,sys,xml.etree.ElementTree as E
from pathlib import Path
root=Path(__file__).resolve().parents[2]
phase=sys.argv[1]; selectors=sys.argv[2].split(',') if len(sys.argv)>2 and sys.argv[2] else []
cases=[]
for p in sorted((root/'target/surefire-reports').glob('TEST-*.xml')):
    name=p.stem.removeprefix('TEST-').split('.')[-1]
    matching=[s for s in selectors if fnmatch.fnmatch(name,s.split('#')[0])]
    if selectors and not matching:continue
    suite=E.parse(p).getroot()
    for c in suite.findall('testcase'):
        if matching and all('#' in s for s in matching) and not any(fnmatch.fnmatch(c.get('name',''),s.split('#')[1]+'*') for s in matching):continue
        issue=next((n for n in c if n.tag in {'error','failure','skipped'}),None)
        message=issue.get('message','') if issue is not None else ''
        if len(message)>1000:message=message[:100]+' [large response omitted] '+message[-650:]
        message=re.sub(r'eyJ[\w-]+\.[\w-]+\.[\w-]+','[JWT REDACTED]',message)
        cases.append(dict(suite=c.get('classname',suite.get('name')),test=c.get('name'),
            status='PASS' if issue is None else {'failure':'FAIL','error':'ERROR','skipped':'SKIPPED'}[issue.tag],
            message=message,timestamp=suite.get('timestamp'),seconds=c.get('time')))
data=dict(phase=phase,total=len(cases),counts=dict(collections.Counter(c['status'] for c in cases)),cases=cases)
out=root/'docs/qa-audit/fix-verification/evidence'/phase;out.mkdir(parents=True,exist_ok=True)
(out/'results.json').write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({k:v for k,v in data.items() if k!='cases'},indent=2))
