import json, os, sys
sys.path.insert(0, os.path.dirname(__file__))
from actdiag import render
import content_customer, content_admin, content_staff
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
SRC = os.path.abspath(os.path.join(ROOT, "..", "..", "src", "main", "java", "com", "oneshop"))
GROUPS = {"customer": content_customer, "admin": content_admin, "staff": content_staff}
sizes = {}
bad = 0
for grp, mod in GROUPS.items():
    d = os.path.join(ROOT, "diagrams", grp); os.makedirs(d, exist_ok=True)
    for f in mod.FUNCS:
        w, h = render(f["lanes"], f["steps"], os.path.join(d, f["id"] + ".svg"))
        json.dump({"id": f["id"], "title": f["title"], "lanes": f["lanes"], "steps": f["steps"]},
                  open(os.path.join(d, f["id"] + ".json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
        sizes[f["id"]] = (w, h)
        for cap, rel, a, b in f["codes"]:
            lines = open(os.path.join(SRC, rel), encoding="utf-8").read().split("\n")
            if b > len(lines) or a < 1:
                print("RANGE ERROR", f["id"], rel, a, b); bad += 1; continue
            print(f'{f["id"]} {rel.split("/")[-1]}:{a}-{b} ({b-a+1}) | {lines[a-1].strip()[:60]} || {lines[b-1].strip()[:40]}')
print("bad", bad)
json.dump(sizes, open(os.path.join(ROOT, "diagrams", "sizes.json"), "w"))
for k, (w, h) in sorted(sizes.items(), key=lambda kv: -kv[1][1] / kv[1][0])[:8]:
    print("tallest", k, w, h, round(h / w, 2))
