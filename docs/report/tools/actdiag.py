"""Tiny UML Activity Diagram (swimlane) renderer: a step list -> SVG. No third-party code is used besides Pillow
(for text measuring). Steps:
  ("start", lane)
  ("act", lane, text)                       action
  ("dec", lane, question, yes, no, fail)    decision; main flow continues with the `yes` guard, `fail` is a list of
                                            action texts that ends in a final node (the `no` guard branch)
  ("end", lane)                             final node
A step may carry a 5th/7th element: dict(db=True) to draw it as a database action.
"""
from PIL import ImageFont
from xml.sax.saxutils import escape

FONT = ImageFont.truetype(r"C:\Windows\Fonts\arial.ttf", 16)
LANE_W = 430
MAIN_DX = 130          # centre of the main column inside a lane
SIDE_DX = 350          # centre of the side (failure) column inside a lane
HEAD_H = 40
GAP = 26
LINE_H = 20
NODE_W = 230
SIDE_W = 150
COLORS = ["#eef3fb", "#f5f5f5", "#fbf3ea", "#eef7ee"]


def _split_long(word, width_px):
    """Break a word that is wider than the box, preferably after URL punctuation (/ ? & = _ . , : ;)."""
    parts = []
    while FONT.getlength(word) > width_px:
        k = 1
        while k < len(word) and FONT.getlength(word[:k + 1]) <= width_px:
            k += 1
        cut = max((i + 1 for i, c in enumerate(word[:k]) if c in "/?&=_.,:;"), default=k)
        if cut < k * 0.4:
            cut = k
        parts.append(word[:cut])
        word = word[cut:]
    parts.append(word)
    return parts


def wrap(text, width_px):
    lines, cur = [], ""
    # pieces produced by _split_long must not be re-joined with a space, so track them
    pieces = []
    for w in text.split():
        sub = _split_long(w, width_px)
        for i, sp in enumerate(sub):
            pieces.append((sp, i > 0))
    for w, glue in pieces:
        trial = (cur + ("" if glue else " ") + w).strip() if cur else w
        if FONT.getlength(trial) <= width_px:
            cur = trial
        else:
            if cur:
                lines.append(cur)
            cur = w
    if cur:
        lines.append(cur)
    return lines or [""]


def render(lanes, steps, path):
    n = len(lanes)
    W = n * LANE_W
    els, edges = [], []
    y = HEAD_H + 22
    prev = None            # (x, bottom_y)
    side_free = 0

    def lane_x(lane, side=False):
        return lanes.index(lane) * LANE_W + (SIDE_DX if side else MAIN_DX)

    def node_box(kind, lane, text, cx, top, w, db=False):
        lines = wrap(text, w - 18) if kind != "dec" else wrap(text, 142)
        if kind == "act":
            h = max(38, 16 + LINE_H * len(lines))
            fill = "#e4e4e4" if db else "#ffffff"
            els.append(f'<rect x="{cx-w/2}" y="{top}" width="{w}" height="{h}" rx="12" fill="{fill}" stroke="#333" stroke-width="1.3"/>')
            ty = top + h / 2 - (len(lines) - 1) * LINE_H / 2 + 4.5
            for i, l in enumerate(lines):
                els.append(f'<text x="{cx}" y="{ty+i*LINE_H}" text-anchor="middle">{escape(l)}</text>')
        else:  # dec
            h = max(74, 46 + LINE_H * len(lines))
            hw = 118
            els.append(f'<polygon points="{cx},{top} {cx+hw},{top+h/2} {cx},{top+h} {cx-hw},{top+h/2}" fill="#fffbe6" stroke="#333" stroke-width="1.3"/>')
            ty = top + h / 2 - (len(lines) - 1) * LINE_H / 2 + 4.5
            for i, l in enumerate(lines):
                els.append(f'<text x="{cx}" y="{ty+i*LINE_H}" text-anchor="middle" font-size="14">{escape(l)}</text>')
        return h

    def connect(frm, to_x, to_top, label=None):
        fx, fy = frm
        if abs(fx - to_x) < 1:
            pts = f"{fx},{fy} {to_x},{to_top}"
        else:
            mid = to_top - 14
            pts = f"{fx},{fy} {fx},{mid} {to_x},{mid} {to_x},{to_top}"
        edges.append(f'<polyline points="{pts}" fill="none" stroke="#333" stroke-width="1.2" marker-end="url(#a)"/>')
        if label:
            els.append(f'<text x="{fx+6}" y="{fy+14}" font-size="14" font-style="italic">[{escape(label)}]</text>')

    pending_label = None
    prev_lane = None
    for st in steps:
        kind, lane = st[0], st[1]
        cx = lane_x(lane)
        if kind != 'start' and (kind == 'dec' or prev is None or lane != prev_lane):
            y = max(y, side_free)
        if kind == "start":
            y0 = y
            els.append(f'<circle cx="{cx}" cy="{y0+11}" r="11" fill="#222"/>')
            prev = (cx, y0 + 22); prev_lane = lane
            y = y0 + 22 + GAP
        elif kind == "end":
            if prev:
                connect(prev, cx, y, pending_label)
            els.append(f'<circle cx="{cx}" cy="{y+13}" r="13" fill="#fff" stroke="#222" stroke-width="1.5"/><circle cx="{cx}" cy="{y+13}" r="7" fill="#222"/>')
            prev = None
            y += 26 + GAP
        elif kind == "act":
            db = len(st) > 3 and st[3].get("db")
            if prev:
                connect(prev, cx, y, pending_label)
            pending_label = None
            h = node_box("act", lane, st[2], cx, y, NODE_W, db)
            prev = (cx, y + h); prev_lane = lane
            y += h + GAP
        elif kind == "dec":
            _, _, q, yes, no, fail = st[:6]
            if prev:
                connect(prev, cx, y, pending_label)
            pending_label = None
            qlines = wrap(q, 142)
            dh = max(74, 46 + LINE_H * len(qlines))
            # keep failure columns from overlapping: wait until the previous one has ended
            fail_h = sum(max(38, 16 + LINE_H * len(wrap(t, SIDE_W - 24))) + GAP for t in fail) + 26
            node_box("dec", lane, q, cx, y, NODE_W)
            cy = y + dh / 2
            sx = lane_x(lane, True)
            # failure chain
            fy = cy - max(38, 16 + LINE_H * len(wrap(fail[0], SIDE_W - 24))) / 2
            edges.append(f'<polyline points="{cx+118},{cy} {sx-SIDE_W/2},{cy}" fill="none" stroke="#333" stroke-width="1.2" marker-end="url(#a)"/>')
            els.append(f'<text x="{sx-SIDE_W/2}" y="{fy-6}" font-size="14" font-style="italic">[{escape(no)}]</text>')
            cur_top = fy
            last = None
            for t in fail:
                h = node_box("act", lane, t, sx, cur_top, SIDE_W)
                if last:
                    edges.append(f'<polyline points="{sx},{last} {sx},{cur_top}" fill="none" stroke="#333" stroke-width="1.2" marker-end="url(#a)"/>')
                last = cur_top + h
                cur_top = last + GAP
            edges.append(f'<polyline points="{sx},{last} {sx},{cur_top}" fill="none" stroke="#333" stroke-width="1.2" marker-end="url(#a)"/>')
            els.append(f'<circle cx="{sx}" cy="{cur_top+13}" r="13" fill="#fff" stroke="#222" stroke-width="1.5"/><circle cx="{sx}" cy="{cur_top+13}" r="7" fill="#222"/>')
            side_free = cur_top + 26 + 8
            prev = (cx, y + dh); prev_lane = lane
            pending_label = yes
            y += dh + GAP
    H = max(y, side_free) + 10
    bands = []
    for i, name in enumerate(lanes):
        bands.append(f'<rect x="{i*LANE_W}" y="0" width="{LANE_W}" height="{H}" fill="{COLORS[i%len(COLORS)]}" stroke="#888" stroke-width="1"/>')
        bands.append(f'<rect x="{i*LANE_W}" y="0" width="{LANE_W}" height="{HEAD_H}" fill="#d9d9d9" stroke="#888"/>')
        bands.append(f'<text x="{i*LANE_W+LANE_W/2}" y="{HEAD_H/2+5}" text-anchor="middle" font-weight="bold" font-size="17">{escape(name)}</text>')
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}" '
           f'font-family="Arial, Helvetica, sans-serif" font-size="16">'
           '<defs><marker id="a" markerWidth="9" markerHeight="9" refX="8" refY="4.5" orient="auto">'
           '<path d="M0,0 L9,4.5 L0,9 z" fill="#333"/></marker></defs>'
           f'<rect width="{W}" height="{H}" fill="#fff"/>' + "".join(bands) + "".join(edges) + "".join(els) + "</svg>")
    with open(path, "w", encoding="utf-8") as f:
        f.write(svg)
    return W, H
