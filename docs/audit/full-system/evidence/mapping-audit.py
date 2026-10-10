"""Compare annotated JPA fields (including mapped superclasses) with actual SQL metadata."""
from pathlib import Path
import json,re
out=Path(__file__).parent
db=json.loads((out/'database-inspection.json').read_text(encoding='utf-8-sig'))['queries']
sources={p.stem:p for p in Path('src/main/java/com/oneshop/entity').glob('*.java')}
tables={}
for name,p in sources.items():
 s=p.read_text(encoding='utf8');t=re.search(r'@Table\(name\s*=\s*"([^"]+)"',s)
 if t: tables[name]=t[1]
def fields(name):
 p=sources[name];s=p.read_text(encoding='utf8');results=[]
 parent=re.search(r'class\s+\w+\s+extends\s+(\w+)',s)
 if parent and parent[1] in sources:results+=fields(parent[1])
 for m in re.finditer(r'((?:\s*@\w+(?:\([^\n]*\))?\s*)+)private\s+([\w<>]+)\s+(\w+)\s*(?:=[^;]*)?;',s):
  annotations,typ,field=m.groups();c=re.search(r'@(Column|JoinColumn)\(((?:"[^"]*"|[^)])*)\)',annotations)
  if not c:continue
  named=re.search(r'name\s*=\s*"([^"]+)"',c[2]);column=named[1] if named else re.sub(r'(?<!^)(?=[A-Z])','_',field).lower()
  spec=dict(re.findall(r'(\w+)\s*=\s*([^,]+)',c[2]));spec={k:v.strip().strip('"') for k,v in spec.items()}
  results.append({'file':str(p).replace('\\','/'),'line':s[:m.start()].count('\n')+1,'field':field,'javaType':typ,'column':column,'kind':c[1],'nullable':False if '@Id' in annotations else spec.get('nullable','true')!='false','spec':spec})
 return results
metadata={(r['table_name'],r['name']):r for r in db['columns']['rows']}
types={'String':'nvarchar','Long':'bigint','Integer':'int','int':'int','Boolean':'bit','boolean':'bit','LocalDateTime':'datetime2','BigDecimal':'decimal'}
rows=[];diff=[];seen=set()
for entity,table in tables.items():
 for f in fields(entity):
  key=(table,f['column']);seen.add(key);sql=metadata.get(key);issues=[]
  if not sql:issues.append('column missing')
  else:
   if f['kind']=='JoinColumn':
    target=next(x for x in fields(f['javaType']) if x['field']=='id');expected=types[target['javaType']]
   else:expected=types.get(f['javaType'],'nvarchar')
   if sql['sql_type']!=expected:issues.append('type mismatch: '+expected+' vs '+sql['sql_type'])
   if sql['is_nullable']!=f['nullable']:issues.append('nullable mismatch')
   if expected=='nvarchar':
    length=-1 if f['spec'].get('columnDefinition','')=='NVARCHAR(MAX)' else int(f['spec'].get('length',255))*2
    if sql['max_length']!=length:issues.append('length mismatch')
   if expected=='decimal':
    if sql['precision']!=int(f['spec'].get('precision',0)) or sql['scale']!=int(f['spec'].get('scale',0)):issues.append('precision/scale mismatch')
  row={'table':table,**f,'sql':sql,'issues':issues};rows.append(row)
  if issues:diff.append(row)
unmapped=[{'table':t,'column':c} for t,c in metadata if (t,c) not in seen]
summary={'method':'source annotated fields + inherited fields vs actual sys.columns; Id treated as NOT NULL; join FK type from target entity Id; Hibernate validate separately passed','entities':len(tables),'mappedFields':len(rows),'sqlColumns':len(metadata),'differences':diff,'unmappedColumns':unmapped,'rows':rows}
(out/'mapping-comparison.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf8')
print(json.dumps({k:v for k,v in summary.items() if k!='rows'},ensure_ascii=False))
