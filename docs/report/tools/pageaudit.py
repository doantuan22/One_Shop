import os, sys
from PIL import Image
d = sys.argv[1]
rows = []
for f in sorted(os.listdir(d)):
    im = Image.open(os.path.join(d, f)).convert("L")
    w, h = im.size
    # page area inside the dark viewer background: crop the sheet (white area)
    px = im.load()
    ink = 0; total = 0; maxy = 0; minx = w; maxx = 0
    for y in range(60, h - 55, 3):
        for x in range(60, w - 20, 3):
            total += 1
            if px[x, y] < 200:
                ink += 1; maxy = max(maxy, y); minx = min(minx, x); maxx = max(maxx, x)
    rows.append((f, ink / total, maxy, minx, maxx))
low = [r for r in rows if r[1] < 0.012]
print("pages", len(rows)); print("near-empty (<1.2% ink):", [(r[0], round(r[1], 4)) for r in low])
short = [(r[0], r[2]) for r in rows if r[2] < 330]
print("pages whose content ends in the top third:", short[:40])
over = [(r[0], r[3], r[4]) for r in rows if r[4] > 730 or r[3] < 100]
print("possible horizontal overflow:", over[:20])
