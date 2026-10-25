// Role-by-role page smoke test with browser console / network error capture.
// Needs a running app (ONESHOP_APP_URL) and Chrome with --remote-debugging-port (ONESHOP_CDP_URL), see scripts/lib/cdp.mjs.
// Accounts: the demo accounts of database/seed/20_demo_data.sql; password from ONESHOP_DEMO_PASSWORD.
// Usage: node scripts/qa-audit/smoke-pages.mjs [output-dir]
import fs from 'node:fs';
import path from 'node:path';
import { CdpPage, APP, sleep } from '../lib/cdp.mjs';

const OUT = path.resolve(process.argv[2] || 'target/qa-audit/smoke-pages');
fs.mkdirSync(OUT, { recursive: true });

const ROLES = [
  { role: 'guest', email: null, pages: ['/', '/products', '/products?q=kem', '/stores', '/login', '/register', '/forgot-password', '/image-credits'] },
  { role: 'customer', email: 'khachhang1@example.com', pages: ['/', '/products', '/cart', '/orders', '/account'] },
  { role: 'staff', email: 'staff.thuduc@example.com', pages: ['/staff', '/staff/dashboard', '/staff/orders', '/staff/stock', '/staff/inventory-history', '/staff/reviews'] },
  { role: 'admin', email: 'admin@example.com', pages: ['/admin', '/admin/products', '/admin/categories', '/admin/brands', '/admin/stores', '/admin/store-products', '/admin/inventory', '/admin/orders', '/admin/payments', '/admin/reviews', '/admin/users', '/admin/staff-assignments', '/admin/promotions', '/admin/returns'] },
];

const results = [];
const page = await CdpPage.open(`${APP}/login`);
try {
  for (const m of ['Runtime', 'Log', 'Network', 'Page']) await page.send(`${m}.enable`);
  let current = null;
  const issue = (kind, detail) => current && current.issues.push({ kind, detail: String(detail).slice(0, 300) });
  page.on('Runtime.exceptionThrown', (e) => issue('js-exception', e.exceptionDetails?.exception?.description || e.exceptionDetails?.text));
  page.on('Runtime.consoleAPICalled', (e) => { if (e.type === 'error') issue('console.error', (e.args || []).map((a) => a.value ?? a.description).join(' ')); });
  page.on('Log.entryAdded', (e) => { if (e.entry.level === 'error') issue('log', `${e.entry.text} ${e.entry.url || ''}`); });
  page.on('Network.responseReceived', (e) => { if (e.response.status >= 400 && e.type !== 'Document') issue(`http-${e.response.status}`, e.response.url); });
  page.on('Network.loadingFailed', (e) => { if (!e.canceled) issue('network-failed', `${e.errorText} ${e.requestId}`); });

  for (const r of ROLES) {
    await page.clearSession();
    if (r.email) await page.login(r.email);
    for (const p of r.pages) {
      current = { role: r.role, page: p, status: null, issues: [] };
      const response = page.nextEvent('Network.responseReceived', 20000).catch(() => null);
      await page.navigate(`${APP}${p}`);
      await page.waitReady();
      await sleep(400);
      const nav = await response;
      current.status = nav?.response?.status ?? null;
      current.finalPath = await page.pathname();
      current.title = await page.eval('document.title');
      current.overflowX = await page.eval('Math.max(0, document.documentElement.scrollWidth - document.documentElement.clientWidth)');
      current.hasServerError = await page.eval(`/Whitelabel|Exception|at com\\.oneshop|stack trace/i.test(document.body.innerText)`);
      results.push(current);
      console.log(`${current.issues.length || current.hasServerError || current.overflowX > 1 ? 'ISSUE' : 'ok   '} ${r.role.padEnd(8)} ${p} -> ${current.finalPath} [${current.status}] ${current.issues.map((i) => i.kind).join(',')}`);
    }
  }
} finally {
  fs.writeFileSync(path.join(OUT, 'smoke-pages.json'), JSON.stringify(results, null, 2));
  await page.close();
}
const bad = results.filter((r) => r.issues.length || r.hasServerError || r.overflowX > 1);
console.log(`\n${results.length} pages, ${bad.length} with issues`);
process.exit(bad.length ? 1 : 0);
