// Browser check (real headless Chrome over CDP): catalog pages with the real product images actually decode and display.
// usage: node browser.mjs <base> <db> <pw> <chrome> <outDir>
import { spawn, execFileSync } from 'node:child_process';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import path from 'node:path';

const [,, base, DB, PW, chrome, out] = process.argv;
await mkdir(out, { recursive: true });
const sql = q => execFileSync('sqlcmd', ['-S', 'localhost', '-U', 'sa', '-P', PW, '-C', '-I', '-d', DB, '-h', '-1', '-W', '-s', '|', '-Q', 'SET NOCOUNT ON; ' + q], { encoding: 'utf8' }).trim();
const one = q => sql(q).split('\n')[0].trim();
const results = [];
const check = (name, ok, detail = '') => { results.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + name + (ok ? '' : '  -> ' + detail)); };

// ---------- browser
const profile = path.join(out, '_profile');
const browser = spawn(chrome, ['--headless=new', '--disable-gpu', '--no-first-run', '--remote-debugging-port=0', `--user-data-dir=${profile}`, 'about:blank'], { windowsHide: true, stdio: 'ignore' });
const pause = ms => new Promise(r => setTimeout(r, ms));
let ws, seq = 0; const pending = new Map();
async function until(fn, label, timeout = 30000) { const end = Date.now() + timeout; while (Date.now() < end) { try { const v = await fn(); if (v) return v; } catch (e) {} await pause(100); } throw new Error('Timeout ' + label); }
try {
  const port = await until(async () => Number((await readFile(path.join(profile, 'DevToolsActivePort'), 'utf8')).split('\n')[0]), 'chrome');
  const version = await (await fetch(`http://127.0.0.1:${port}/json/version`)).json();
  ws = new WebSocket(version.webSocketDebuggerUrl);
  await new Promise((ok, no) => { ws.onopen = ok; ws.onerror = no; });
  ws.onmessage = e => { const m = JSON.parse(e.data); const p = m.id && pending.get(m.id); if (p) { pending.delete(m.id); m.error ? p.reject(new Error(m.error.message)) : p.resolve(m.result); } };
  const call = (method, params = {}, sessionId) => new Promise((resolve, reject) => { const id = ++seq; pending.set(id, { resolve, reject }); ws.send(JSON.stringify({ id, method, params, ...(sessionId ? { sessionId } : {}) })); });
  async function tab(width = 1440) {
    const { browserContextId } = await call('Target.createBrowserContext');
    const { targetId } = await call('Target.createTarget', { url: 'about:blank', browserContextId });
    const { sessionId } = await call('Target.attachToTarget', { targetId, flatten: true });
    await call('Page.enable', {}, sessionId);
    await call('Emulation.setDeviceMetricsOverride', { width, height: 1000, deviceScaleFactor: 1, mobile: width < 600 }, sessionId);
    return sessionId;
  }
  const ev = async (s, expression) => (await call('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true }, s)).result.value;
  async function nav(s, url) { await call('Page.navigate', { url: base + url }, s); await pause(400); await until(() => ev(s, 'document.readyState==="complete"'), 'ready ' + url); }
  async function loginBrowser(width, email) {
    const s = await tab(width); await nav(s, '/login');
    await ev(s, `document.querySelector('#identifier').value=${JSON.stringify(email)};document.querySelector('#password').value='OneShop@123';document.querySelector('form[action="/login"]').submit()`);
    await pause(1500); await until(() => ev(s, 'location.pathname!=="/login"'), 'login ' + email); return s;
  }
  const text = s => ev(s, 'document.body.innerText');
  const overflow = s => ev(s, 'document.documentElement.scrollWidth - window.innerWidth');
  async function shot(s, file) {
    const { contentSize } = await call('Page.getLayoutMetrics', {}, s);
    const r = await call('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true, clip: { x: 0, y: 0, width: Math.ceil(contentSize.width), height: Math.ceil(contentSize.height), scale: 1 } }, s);
    await writeFile(path.join(out, file), Buffer.from(r.data, 'base64'));
  }


  const imgReport = s => ev(s, "(() => { const imgs = [...document.querySelectorAll('img')].filter(i => i.src.includes('res.cloudinary.com') && i.src.includes('/oneshop/v3/products/')); return JSON.stringify({ total: imgs.length, loaded: imgs.filter(i => i.complete && i.naturalWidth > 0).length }); })()");
  const g = await tab(1440);
  for (const [label, url] of [['home', '/'], ['catalog', '/products'], ['catalog-page2', '/products?page=1'], ['detail-sensibio', '/products/9'], ['detail-matteink', '/products/17'], ['credits', '/image-credits']]) {
    await nav(g, url); await pause(2500);
    const r = JSON.parse(await imgReport(g));
    await shot(g, `seed-${label}-desktop.png`);
    check(`browser ${label}: ${r.loaded}/${r.total} product images decoded and displayed`, r.total === r.loaded && (label === 'credits' || label.startsWith('home') ? true : r.total > 0), JSON.stringify(r));
  }
  const m = await tab(390);
  for (const [label, url] of [['catalog', '/products'], ['detail', '/products/5']]) {
    await nav(m, url); await pause(1500); for (let y = 0; y < 8; y++) { await ev(m, 'window.scrollBy(0, 700)'); await pause(500); } await pause(2000);
    const r = JSON.parse(await imgReport(m));
    await shot(m, `seed-${label}-phone.png`);
    check(`browser phone ${label}: images loaded ${r.loaded}/${r.total}, no horizontal scroll`, r.total === r.loaded && (await overflow(m)) <= 1, JSON.stringify(r) + ' overflow ' + (await overflow(m)));
  }
  const a = await loginBrowser(1440, 'admin@oneshop.vn');
  await nav(a, '/admin/products'); await pause(2000); await shot(a, 'seed-admin-products.png');
  check('browser admin products: lists the real products', /CeraVe Moisturising Cream 340g/.test(await text(a)));
  const st = await loginBrowser(1440, 'staff.thuduc@oneshop.vn');
  await nav(st, '/staff/stock'); await pause(1500); await shot(st, 'seed-staff-inventory.png');
  check('browser staff inventory: shows real products and stock', /Simple Purifying Gel Wash 150ml/.test(await text(st)));
} finally {
  try { ws && ws.close(); } catch (e) {}
  browser.kill();
}
const f = results.filter(x => !x).length;
console.log(`
BROWSER-SEED ${results.length - f}/${results.length} passed`);
process.exit(f ? 1 : 0);
