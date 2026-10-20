// Browser E2E (real headless Chrome over CDP) for OPEN-10 / OPEN-10.1 on the running app + independent migrated copy.
// usage: node browser.mjs <base> <db> <pw> <chrome> <outDir>
import { spawn, execFileSync } from 'node:child_process';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import path from 'node:path';
import { Client } from '../lib.mjs';

const [,, base, DB, PW, chrome, out] = process.argv;
await mkdir(out, { recursive: true });
const sql = q => execFileSync('sqlcmd', ['-S', 'localhost', '-U', 'sa', '-P', PW, '-C', '-I', '-d', DB, '-h', '-1', '-W', '-s', '|', '-Q', 'SET NOCOUNT ON; ' + q], { encoding: 'utf8' }).trim();
const one = q => sql(q).split('\n')[0].trim();
const results = [];
const check = (name, ok, detail = '') => { results.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + name + (ok ? '' : '  -> ' + detail)); };

// ---------- stage the data through the real HTTP stack (same as a user would)
const cartIds = h => [...h.matchAll(/data-cart-item-id="(\d+)"/g)].map(m => m[1]);
const field = (b, n) => (b.match(new RegExp(`name="${n}"[^>]*value="([^"]*)"`)) || b.match(new RegExp(`name="${n}" value="([^"]*)"`)) || [])[1];
const login = async e => { const c = new Client(e); await c.login(e); return c; };
const [c1, td, gv] = await Promise.all(['khachhang1@example.com', 'staff.thuduc@oneshop.vn', 'staff.govap@oneshop.vn'].map(login));
Client.prototype.constructor; // (BASE comes from process.argv[2] in lib.mjs)
for (const id of cartIds((await c1.get('/cart')).body)) await c1.post(`/cart/items/${id}/delete`);
for (const p of [12, 17]) await c1.post('/cart/items', { productId: p, quantity: 1 });
const ids = cartIds((await c1.get('/cart')).body);
const pv = await c1.get('/checkout?' + ids.map(i => 'cartItemIds=' + i).join('&') + '&fulfillmentType=DELIVERY&paymentMethod=ONLINE');
const addr = (pv.body.match(/name="shippingAddress"[\s\S]*?value="([^"]*)"/) || [])[1]?.replace(/&amp;/g, '&');
const co = await c1.post('/checkout', { cartItemIds: ids, fulfillmentType: 'DELIVERY', paymentMethod: 'ONLINE', receiverName: 'QA', receiverPhone: '0911111111', shippingAddress: addr,
  planSignature: field(pv.body, 'planSignature'), expectedTotal: field(pv.body, 'expectedTotal') });
const order = (await c1.get(co.loc.replace(/^https?:\/\/[^/]+/, ''))).body.match(/data-order-id="(\d+)"/)?.[1];
const pid = (await c1.get(`/orders/${order}/payments`)).body.match(/data-payment-id="(\d+)"/)?.[1];
await c1.post(`/orders/${order}/payments/${pid}/simulate`, { scenario: 'SUCCESS' });
const part = s => one(`select fulfillment_id from order_fulfillments where order_id=${order} and store_id=${s}`);
const gvP = part(2), tdP = part(1);
const S = (c, p, a, f = {}) => c.post(`/staff/fulfillments/${p}/${a}`, f);
for (const [c, p] of [[gv, gvP], [td, tdP]]) { await S(c, p, 'prepare'); await S(c, p, 'pack'); await S(c, p, 'ship', { trackingCode: 'BR' + p }); }
await S(gv, gvP, 'complete-delivery');
await S(td, tdP, 'fail-delivery', { reason: 'Khách vắng nhà, gọi 3 lần không được' });
check('staged: ONLINE paid order, one part delivered, one FAILED, order held', one(`select order_status+'|'+payment_status from orders where order_id=${order}`) === 'NEEDS_ATTENTION|PAID');

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

  // ---- Admin, desktop
  const a = await loginBrowser(1440, 'admin@oneshop.vn');
  await nav(a, `/admin/orders/${order}`);
  let t = await text(a);
  check('browser/admin: exception panel visible with reason, money rows and preview', /Khách vắng nhà, gọi 3 lần/.test(t) && /Đã hoàn \(mô phỏng\)/.test(t) && /Còn giữ lại/.test(t) && /phí giao được giữ lại/.test(t));
  await shot(a, 'admin-order-exception.png');
  // UNCONFIRMED via the real form (the confirm() dialog is accepted)
  await ev(a, `window.confirm=()=>true;const f=document.querySelector('#undeliverable-form');f.querySelector('select[name=goods]').value='UNCONFIRMED';f.querySelector('textarea[name=reason]').value='Đang chờ vận chuyển xác minh hàng';f.querySelector('button[type=submit]').click()`);
  await until(() => ev(a, 'location.search.includes("exception=")'), 'submit 1');
  await until(() => ev(a, 'document.readyState==="complete"'), 'ready 1');
  t = await text(a);
  check('browser/admin: UNCONFIRMED -> notice "pending verification", panel still open, nothing refunded', /exception=pending-verification/.test(await ev(a, 'location.search')) && /chờ xác minh/i.test(t) && (await ev(a, '!!document.querySelector("#fulfillment-exception")')) && one(`select count(*) from payment_refunds where order_id=${order}`) === '0');
  await shot(a, 'admin-order-awaiting-verification.png');
  await ev(a, `window.confirm=()=>true;const f=document.querySelector('#undeliverable-form');f.querySelector('select[name=goods]').value='LOST';f.querySelector('textarea[name=reason]').value='Vận chuyển xác nhận thất lạc hàng';f.querySelector('button[type=submit]').click()`);
  await until(() => ev(a, 'location.search.includes("closed-partial")'), 'submit 2');
  await until(() => ev(a, 'document.readyState==="complete"'), 'ready 2');
  t = await text(a);
  check('browser/admin: LOST -> closed with partial refund; panel gone; ledger visible with actor', !(await ev(a, '!!document.querySelector("#fulfillment-exception")')) && /Hoàn tiền \(mô phỏng\)/.test(t) && /Người xử lý/.test(t) && /Hoàn tất một phần/.test(t));
  check('browser/admin: DB agrees (1 GOODS refund, order COMPLETED/PAID)', one(`select count(*) from payment_refunds where order_id=${order} and component='GOODS' and status='SIMULATED_REFUNDED'`) === '1' && one(`select order_status+'|'+payment_status from orders where order_id=${order}`) === 'COMPLETED|PAID');
  await shot(a, 'admin-order-closed-partial.png');
  await nav(a, `/admin/payments/${order}`); check('browser/admin: payment detail shows the ledger', /Hoàn tiền \(mô phỏng\)/.test(await text(a)));
  await nav(a, '/admin'); check('browser/admin: dashboard loads', /Cần Admin xử lý/.test(await text(a)));

  // ---- Customer, desktop and phone
  for (const [label, width] of [['desktop', 1440], ['phone', 390]]) {
    const c = await loginBrowser(width, 'khachhang1@example.com');
    await nav(c, `/orders/${order}`); t = await text(c);
    check(`browser/customer ${label}: simulated refund labelled, partial badge, reason, no staff name`, /Hoàn tiền \(mô phỏng\)/.test(t) && /không có khoản tiền thật/.test(t) && /Hoàn tất một phần/.test(t) && /Vận chuyển xác nhận thất lạc hàng/.test(t) && !/Người xử lý/.test(t) && !/Quản trị/.test(t));
    check(`browser/customer ${label}: no horizontal page scroll`, (await overflow(c)) <= 1, String(await overflow(c)));
    await shot(c, `customer-order-${label}.png`);
  }
  const ap = await loginBrowser(390, 'admin@oneshop.vn'); await nav(ap, `/admin/orders/${order}`);
  check('browser/admin phone: no horizontal page scroll on the order page', (await overflow(ap)) <= 1, String(await overflow(ap)));
  // ---- other customer cannot open it
  const c2 = await loginBrowser(1440, 'khachhang2@example.com'); await nav(c2, `/orders/${order}`);
  check('browser/other customer: 404 page, no refund data', !/Hoàn tiền \(mô phỏng\)/.test(await text(c2)) && /404|không tìm thấy/i.test(await text(c2) + await ev(c2, 'document.title')));
  // ---- staff cannot open the admin page
  const st = await loginBrowser(1440, 'staff.thuduc@oneshop.vn'); await nav(st, `/admin/orders/${order}`);
  check('browser/staff: admin order page forbidden', !/id="fulfillment-exception"/.test(await ev(st, 'document.documentElement.outerHTML')) && /403|không có quyền|từ chối/i.test(await text(st) + await ev(st, 'document.title')));
} finally {
  try { ws && ws.close(); } catch (e) {}
  browser.kill();
}
const f = results.filter(x => !x).length;
console.log(`\nBROWSER ${results.length - f}/${results.length} passed`);
process.exit(f ? 1 : 0);
