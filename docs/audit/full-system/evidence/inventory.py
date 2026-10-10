"""Audit evidence only: inventories tracked source without modifying application code."""
from pathlib import Path
import re,json,hashlib,subprocess
root=Path.cwd();out=root/'docs/audit/full-system/evidence';out.mkdir(parents=True,exist_ok=True)
files=subprocess.check_output(['git','ls-files'],text=True).splitlines()
manifest={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in files if (root/p).is_file()}
out.joinpath('source-manifest-before.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
endpoints=[]
for f in sorted(root.glob('src/main/java/com/oneshop/controller/**/*.java')):
 s=f.read_text(encoding='utf-8');pos=s.find('public class')
 roots=re.findall(r'@RequestMapping\("([^"]*)"\)',s[:pos]);base=roots[-1] if roots else ''
 for m in re.finditer(r'@(Get|Post|Put|Delete|Patch)Mapping(?:\(([^)]*)\))?',s):
  values=re.findall(r'"([^"]*)"',m.group(2) or '') or ['']
  after=s[m.end():];method=re.search(r'public\s+\S+\s+(\w+)\s*\(',after)
  endpoints.append(dict(file=str(f.relative_to(root)).replace('\\','/'),line=s[:m.start()].count('\n')+1,verb=m.group(1).upper(),paths=[base+v for v in values],method=method.group(1) if method else '?'))
entities=[]
for f in root.glob('src/main/java/com/oneshop/entity/*.java'):
 s=f.read_text(encoding='utf-8');tables=re.findall(r'@Table\(name\s*=\s*"([^"]+)"',s)
 if tables:entities.append(dict(file=str(f.relative_to(root)).replace('\\','/'),table=tables[0],relations=len(re.findall(r'@(ManyToOne|OneToMany|OneToOne|ManyToMany)',s))))
tests=[]
for f in sorted(root.glob('src/test/java/**/*.java')):
 s=f.read_text(encoding='utf-8');tests.append(dict(file=str(f.relative_to(root)).replace('\\','/'),annotations=len(re.findall(r'@(?:Test|ParameterizedTest)\b',s)),conditional='@EnabledIf' in s,assumptions='assume' in s,mockAdapter='CloudinaryService' in s and ('MockitoBean' in s or 'mock(' in s)))
reports=[str(p.relative_to(root)).replace('\\','/') for p in root.glob('docs/*Report.md')]
templates=[str(p.relative_to(root)).replace('\\','/') for p in root.glob('src/main/resources/templates/**/*.html')]
services=[str(p.relative_to(root)).replace('\\','/') for p in root.glob('src/main/java/com/oneshop/service/**/*.java')]
out.joinpath('inventory.json').write_text(json.dumps(dict(commit=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),endpoints=endpoints,entities=entities,templates=templates,services=services,tests=tests,reports=reports),indent=2,ensure_ascii=False),encoding='utf-8')
print(json.dumps(dict(endpoints=len(endpoints),entities=len(entities),templates=len(templates),services=len(services),testFiles=len(tests),phaseReports=len(reports))))
