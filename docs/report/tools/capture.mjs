// capture.mjs: captures real screenshots of the running OneShop site for every page listed in spec.mjs, in a raw
// version and a version with numbered annotations (badges + dashed outlines injected into the live DOM).
// Usage: node capture.mjs [--only id1,id2] ; env: ONESHOP_APP_URL, ONESHOP_SEED_PASSWORD, ONESHOP_CDP_URL
import fs from 'node:fs';
import path from 'node:path';
import { CdpPage, sleep } from './lib/cdp.mjs';
import { PAGES, LOGINS } from './spec.mjs';

const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1')), '..');
const OUT = path.join(ROOT, 'screenshots');
const PASSWORD = process.env.ONESHOP_SEED_PASSWORD;
if (!PASSWORD) throw new Error('ONESHOP_SEED_PASSWORD is required');
const onlyArg = process.argv.indexOf('--only');
const only = onlyArg > 0 ? process.argv[onlyArg + 1].split(',') : null;
const VIEW_W = 1280;

// Browser-side helpers (installed once per document)
const HELPERS = `
window.__find = (loc) => {
  let css = loc, n = 0;
  if (typeof loc === 'string') {
    const m = loc.match(/^(.*)@(\\d+)$/);
    if (m) { css = m[1]; n = +m[2]; }
    const all = [...document.querySelectorAll(css)].filter(e => { const r = e.getBoundingClientRect(); return r.width > 2 && r.height > 2; });
    return all[n] || null;
  }
  const re = new RegExp(loc.t, 'i');
  const scope = loc.within ? document.querySelector(loc.within) : document;
  if (!scope) return null;
  let all = [...scope.querySelectorAll(loc.in || 'a,button,h1,h2,h3,h4,label,th,strong,span,p,dt,summary')]
    .filter(e => { const r = e.getBoundingClientRect(); return r.width > 2 && r.height > 2 && re.test((e.innerText || e.value || '').trim()); });
  // prefer the innermost match
  all = all.filter(e => !all.some(o => o !== e && e.contains(o)));
  let el = all[loc.n || 0] || null;
  if (el && loc.up) el = el.closest(loc.up);
  return el;
};
window.__rect = (loc) => {
  const e = window.__find(loc); if (!e) return null;
  const r = e.getBoundingClientRect();
  return { x: r.left + scrollX, y: r.top + scrollY, w: r.width, h: r.height };
};
window.__badges = (items, clipY) => {
  document.querySelectorAll('.__ann').forEach(e => e.remove());
  items.forEach((it, i) => {
    const o = document.createElement('div');
    o.className = '__ann';
    o.style.cssText = 'position:absolute;z-index:2147483000;pointer-events:none;border:2px dashed #e8590c;border-radius:6px;box-sizing:border-box;' +
      'left:' + (it.x - 2) + 'px;top:' + (it.y - 2) + 'px;width:' + (it.w + 4) + 'px;height:' + (it.h + 4) + 'px;';
    const b = document.createElement('div');
    b.className = '__ann';
    b.textContent = String(i + 1);
    const small = it.w * it.h < 60000;
    const bx = Math.max(2, it.x - (small ? 24 : 13)), by = Math.max(clipY + 2, it.y - (small ? 20 : 13));
    b.style.cssText = 'position:absolute;z-index:2147483001;pointer-events:none;width:26px;height:26px;border-radius:50%;background:#e8590c;color:#fff;' +
      'font:bold 14px Arial,sans-serif;line-height:26px;text-align:center;border:2px solid #fff;box-shadow:0 1px 4px rgba(0,0,0,.5);box-sizing:border-box;' +
      'left:' + bx + 'px;top:' + by + 'px;';
    document.body.appendChild(o); document.body.appendChild(b);
  });
};
window.__clear = () => document.querySelectorAll('.__ann').forEach(e => e.remove());
`;

async function revealAll(page) {
  // scroll through the page so lazily revealed sections (IntersectionObserver) are rendered
  const h = await page.eval('document.documentElement.scrollHeight');
  for (let y = 0; y < h; y += 500) { await page.eval(`window.scrollTo(0, ${y})`); await sleep(60); }
  await page.eval('window.scrollTo(0, 0)');
  await sleep(250);
}

async function shot(page, file, clip) {
  const r = await page.send('Page.captureScreenshot', {
    format: 'png', captureBeyondViewport: true, clip: { x: 0, y: clip.y, width: VIEW_W, height: clip.h, scale: 1 },
  });
  fs.writeFileSync(file, Buffer.from(r.data, 'base64'));
}

const manifest = {};
const mfile = path.join(OUT, 'manifest.json');
if (fs.existsSync(mfile)) Object.assign(manifest, JSON.parse(fs.readFileSync(mfile, 'utf8')));

const sessions = {};
async function session(role) {
  if (sessions[role]) return sessions[role];
  const page = await CdpPage.open('about:blank');
  await page.setViewport(VIEW_W, 900);
  await page.clearSession();
  if (LOGINS[role]) await page.login(LOGINS[role], PASSWORD);
  sessions[role] = page;
  return page;
}

let failures = 0;
const withTimeout = (p, ms, label) => Promise.race([p, new Promise((_, rej) => setTimeout(() => rej(new Error('timeout ' + label)), ms))]);
for (const spec of PAGES) {
  if (only && !only.includes(spec.id)) continue;
  const dir = path.join(OUT, spec.group);
  fs.mkdirSync(dir, { recursive: true });
  try {
    const page = await withTimeout(session(spec.role), 60000, 'session ' + spec.role);
    await withTimeout((async () => {
    if (spec.before) await spec.before(page);
    const url = typeof spec.url === 'function' ? spec.url() : spec.url;
    await page.navigate(url);
    await sleep(500);
    await page.eval(HELPERS);
    if (spec.act) { await spec.act(page); await sleep(300); }
    await revealAll(page);
    const total = await page.eval('document.documentElement.scrollHeight');
    const y0 = spec.y || 0;
    const h = Math.min(spec.h || 1500, total - y0);
    const clip = { y: y0, h: Math.max(h, 200) };
    await page.eval(`(() => { document.querySelectorAll('body *').forEach(e => { const c = getComputedStyle(e);
      if (c.position === 'fixed' && e.getBoundingClientRect().bottom >= window.innerHeight - 4 && e.getBoundingClientRect().height < 200) e.style.visibility = 'hidden'; }); })()`);
    // raw
    await page.eval('window.__clear()');
    await shot(page, path.join(dir, `${spec.id}.png`), clip);
    // annotate
    const items = [], kept = [];
    for (const [loc, label, desc] of spec.notes) {
      const r = await page.eval(`window.__rect(${JSON.stringify(loc)})`);
      if (!r) { console.log(`  !! ${spec.id}: not found: ${JSON.stringify(loc)} (${label})`); failures++; continue; }
      if (r.y + 4 < clip.y || r.y > clip.y + clip.h - 30) {
        console.log(`  !! ${spec.id}: outside clip: ${label} y=${Math.round(r.y)} h=${Math.round(r.h)} clip=${clip.y}..${clip.y + clip.h}`); failures++; continue;
      }
      items.push({ x: r.x, y: r.y, w: r.w, h: r.h });
      kept.push({ label, desc });
    }
    // number the components top-to-bottom, then left-to-right
    const order = items.map((it, i) => i).sort((i, j) => (Math.floor(items[i].y / 16) - Math.floor(items[j].y / 16)) || (items[i].x - items[j].x));
    const items2 = order.map(i => items[i]); const kept2 = order.map(i => kept[i]);
    items.length = 0; items.push(...items2); kept.length = 0; kept.push(...kept2);
    await page.eval(`window.__badges(${JSON.stringify(items)}, ${clip.y})`);
    await sleep(120);
    await shot(page, path.join(dir, `${spec.id}_annotated.png`), clip);
    await page.eval('window.__clear()');
    manifest[spec.id] = { group: spec.group, role: spec.role, url, title: spec.title, tableTitle: spec.tableTitle,
      width: VIEW_W, height: clip.h, y: clip.y, notes: kept, capturedAt: new Date().toISOString() };
    if (spec.after) await spec.after(page);
    console.log(`ok ${spec.id} ${url} h=${clip.h} notes=${kept.length}/${spec.notes.length}`);
    })(), 120000, spec.id);
  } catch (e) {
    console.log(`ERR ${spec.id}: ${e.message}`); failures++;
    if (/timeout/.test(e.message) && sessions[spec.role]) { try { await sessions[spec.role].close(); } catch {} delete sessions[spec.role]; }
  }
}
fs.writeFileSync(mfile, JSON.stringify(manifest, null, 1));
for (const p of Object.values(sessions)) await p.close();
console.log('failures', failures);
