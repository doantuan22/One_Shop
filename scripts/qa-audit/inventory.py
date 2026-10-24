"""Read-only structural inventory. Heuristic candidates are NOT measured coverage."""
import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'docs/qa-audit/evidence'
OUT.mkdir(parents=True, exist_ok=True)

def balanced(text, start, left='(', right=')'):
    depth, quote, escape = 0, None, False
    for i in range(start, len(text)):
        c = text[i]
        if quote:
            if escape: escape = False
            elif c == '\\': escape = True
            elif c == quote: quote = None
        elif c in '\"\'': quote = c
        elif c == left: depth += 1
        elif c == right:
            depth -= 1
            if depth == 0: return text[start + 1:i], i + 1
    return '', start + 1

files, endpoints, methods, decisions, ui, enums = [], [], [], [], [], []
constants={}
for p in (ROOT/'src/main/java').rglob('*.java'):
    for name,value in re.findall(r'(?:public\s+)?static\s+final\s+String\s+(\w+)\s*=\s*"([^"\n]+)"',p.read_text(encoding='utf-8-sig')):
        constants[name]=value
for p in sorted(ROOT.rglob('*')):
    rel = p.relative_to(ROOT).as_posix()
    if not p.is_file() or rel.split('/')[0] in {'.git', 'target', 'backups', 'backup', 'artifacts', 'screenshots'}:
        continue
    if p.suffix not in {'.java', '.html', '.js', '.mjs', '.sql', '.properties', '.md', '.xml', '.ps1', '.yml', '.yaml'}:
        continue
    if rel.startswith(('docs/qa-audit/', 'scripts/qa-audit/')): continue
    raw = p.read_bytes()
    text = raw.decode('utf-8-sig', errors='replace')
    files.append(dict(path=rel, lines=len(text.splitlines()), sha256=hashlib.sha256(raw).hexdigest()))
    if rel.startswith('src/main/java/'):
        for m in re.finditer(r'\b(public|protected|private)\s+(?:(?:static|final|synchronized|abstract|default)\s+)*(?:[\w<>?,.\[\]]+\s+)+(?P<name>\w+)\s*\(', text):
            params, end = balanced(text, text.index('(', m.start()))
            methods.append(dict(id=f'M{len(methods)+1:04}', file=rel, line=text.count('\n', 0, m.start())+1, method=m['name'], input=params.replace('\n', ' ')))
        for n,line in enumerate(text.splitlines(),1):
            if re.search(r'\b(if|else|switch|case|catch|finally)\b|\?|\.orElse|\.filter\(|&&|\|\|', line) and not line.strip().startswith(('*','//','import')):
                decisions.append(dict(id=f'B{len(decisions)+1:05}', file=rel, line=n, candidate=line.strip()))
        if '/controller/' in rel:
            cls = re.search(r'\bclass\s+\w+',text)
            base = ''
            for m in re.finditer(r'@(Request|Get|Post|Put|Delete|Patch)Mapping\s*(\()?', text):
                arg,end = balanced(text,m.end()-1) if m.group(2) else ('',m.end())
                values = re.findall(r'"([^"\n]*)"', arg)
                if not values and arg.strip() in constants: values=[constants[arg.strip()]]
                if cls and m.start()<cls.start():
                    base = values[0] if values else ''
                    continue
                tail = text[end:]
                meth = re.search(r'\bpublic\s+[\w<>?,.\[\]\s]+?\s+(\w+)\s*\(', tail)
                verb = m.group(1).upper() if m.group(1) != 'Request' else '|'.join(re.findall(r'RequestMethod\.(\w+)',arg)) or 'ANY'
                for path in values or ['']:
                    # Annotation names/produces values are retained for manual reconciliation, not silently discarded.
                    endpoints.append(dict(id=f'F{len(endpoints)+1:03}', verb=verb, path=base+path, controller=rel, line=text.count('\n',0,m.start())+1, method=meth.group(1) if meth else 'UNRESOLVED', mapping=arg))
        for enum in re.finditer(r'\benum\s+(\w+)\s*\{',text):
            values,end=balanced(text,enum.end()-1,'{','}')
            enums.append(dict(file=rel,name=enum[1],line=text.count('\n',0,enum.start())+1,source=values))
    if rel.startswith('src/main/resources/') and p.suffix in {'.html','.js'} and '/vendor/' not in rel:
        for n,line in enumerate(text.splitlines(),1):
            kinds = [kind for kind,rx in [('form',r'<form\b'),('button',r'<button\b'),('link',r'<a\b'),('input',r'<(?:input|select|textarea)\b'),('event',r'addEventListener|onclick|onchange|onsubmit|fetch\(|XMLHttpRequest'),('condition',r'th:(?:if|unless|switch|case)') ] if re.search(rx,line)]
            if kinds: ui.append(dict(id=f'U{len(ui)+1:04}', file=rel,line=n,kinds=kinds,source=line.strip()))

tests=[]
for p in sorted((ROOT/'src/test/java').rglob('*.java')):
    text=p.read_text(encoding='utf-8-sig')
    for m in re.finditer(r'@(?:Test|ParameterizedTest|RepeatedTest|TestFactory)\b',text):
        tail=text[m.end():]
        name=re.search(r'\b(?:void|Stream<[^\n]+?>|Collection<[^\n]+?>)\s+(\w+)\s*\(',tail)
        if name: tests.append(dict(file=p.relative_to(ROOT).as_posix(),method=name[1]))
data=dict(files=files,endpoints=endpoints,methods=methods,decision_candidates=decisions,ui_actions=ui,enums=enums,test_methods=tests)
(OUT/'inventory.json').write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({k:len(v) for k,v in data.items()},indent=2))
