import fs from 'node:fs';
import path from 'node:path';
import { APP, CdpPage, sleep } from './lib/cdp.mjs';

// Responsive + affordance regression over every role. Writes artifacts/ui-audit/v2/regression.json and
// screenshots (all 4 viewports) under artifacts/ui-audit/v2/responsive/<viewport>/<role>/.
const OUT = path.resolve('artifacts', 'ui-audit', 'v2');
const VPS = [[1440, 900], [1024, 768], [768, 1024], [375, 812]];
const ROLES = {
  guest: { email: null, routes: ['/', '/login', '/login?error', '/register', '/products', '/products?q=serum', '/products?q=zzzzzz', '/products/1', '/stores', '/image-credits', '/products/999999'] },
  customer: { email: 'khachhang1@example.com', routes: ['/', '/account', '/products/1', '/cart', '/orders', '/orders/1', '/orders/1/payments'] },
  staff: { email: 'staff.thuduc@oneshop.vn', routes: ['/staff', '/staff/fulfillments', '/staff/fulfillments/delivery', '/staff/fulfillments/pickup', '/staff/orders', '/staff/reviews', '/staff/stock', '/staff/stock/1/adjust', '/staff/inventory-history', '/staff/inventory-history?view=select', '/staff/inventory-history/1', '/admin'] },
  admin: { email: 'admin@oneshop.vn', routes: ['/admin', '/admin/orders', '/admin/payments', '/admin/payments?view=all', '/admin/inventory', '/admin/store-products', '/admin/store-products/new', '/admin/products', '/admin/products/new', '/admin/categories', '/admin/brands', '/admin/users', '/admin/users/new', '/admin/stores', '/admin/stores/new', '/admin/staff-assignments', '/admin/reviews', '/staff'] },
};
const probe = `(() => {
  const vis = e => { const r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0; };
  const issues = [];
  const de = document.documentElement;
  if (de.scrollWidth > innerWidth + 1) issues.push('body horizontal overflow ' + de.scrollWidth + '>' + innerWidth);
  document.querySelectorAll('main .btn, .btn').forEach(b => { if (!vis(b)) return; const h = b.getBoundingClientRect().height; if (h < 30) issues.push('button <30px: ' + b.innerText.trim().slice(0, 20) + ' ' + Math.round(h)); });
  document.querySelectorAll('.badge').forEach(b => { if (!vis(b)) return; const c = getComputedStyle(b); if (c.cursor === 'pointer' && b.tagName !== 'A' && !b.closest('a,button')) issues.push('badge with pointer cursor: ' + b.innerText.trim().slice(0, 20)); });
  document.querySelectorAll('main td, main th').forEach(c => { if (!vis(c)) return; if (c.scrollWidth > c.clientWidth + 2 && getComputedStyle(c).overflow !== 'visible') issues.push('clipped cell: ' + c.innerText.trim().slice(0, 24)); });
  const wrap = document.querySelector('.table-responsive');
  const tableScroll = wrap ? wrap.scrollWidth > wrap.clientWidth + 1 : false;
  const txt = document.body.innerText; const bad = ['Whitelabel', ' undefined', '[[', 'th:text'].filter(k => txt.includes(k));
  if (bad.length) issues.push('raw text: ' + bad.join(','));
  return { path: location.pathname + location.search, issues: [...new Set(issues)].slice(0, 10), tableScroll };
})()`;
const page = await CdpPage.open(`${APP}/login`);
const results = [];
try {
  for (const [role, cfg] of Object.entries(ROLES)) {
    await page.clearSession();
    if (cfg.email) await page.login(cfg.email);
    for (const [w, h] of VPS) {
      await page.setViewport(w, h);
      for (const route of cfg.routes) {
        await page.navigate(route); await sleep(250);
        const r = await page.eval(probe);
        results.push({ role, vp: `${w}x${h}`, route, status: page.lastDocumentStatus, ...r });
        if (w !== 1440) {
          const name = route.replace(/[^a-z0-9]+/gi, '_').replace(/^_|_$/g, '') || 'root';
          const file = path.join(OUT, 'responsive', `${w}x${h}`, role, `${name}.png`);
          fs.mkdirSync(path.dirname(file), { recursive: true });
          const s = await page.send('Page.captureScreenshot', { format: 'png' });
          fs.writeFileSync(file, Buffer.from(s.data, 'base64'));
        }
      }
    }
  }
} finally {
  fs.mkdirSync(OUT, { recursive: true });
  fs.writeFileSync(path.join(OUT, 'regression.json'), JSON.stringify({ results, consoleErrors: page.errors }, null, 1));
  await page.close();
}
const bad = results.filter(r => r.issues.length);
console.log(`checked ${results.length} page-viewports, ${bad.length} with issues, ${results.filter(r => r.tableScroll).length} with table scroll`);
for (const r of bad) console.log(r.role, r.vp, r.route, '=>', r.issues.join(' ; '));
