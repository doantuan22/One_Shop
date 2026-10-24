import re
from pathlib import Path
root=Path(__file__).resolve().parents[2]
source=root/'target/qa-audit/additional.log'
text=source.read_text(encoding='utf-8-sig',errors='replace')
lines=text.splitlines();keep=[]
for i,line in enumerate(lines):
    if ('Page offset exceeds Integer.MAX_VALUE' in line or
            'One or more values is out of range' in line):
        keep.append(f'additional.log:{i+1}: {line}')
        keep.extend(l.strip() for l in lines[i+1:i+100] if re.search(r'at (com\.oneshop|org\.springframework\.data\.support\.PageableUtils)',l))
out=root/'docs/qa-audit/evidence/boundary-log-excerpts.txt'
out.write_text('\n'.join(keep)+'\n',encoding='utf-8')
