import fs from 'node:fs';
import { APP, CdpPage, sleep } from './lib/cdp.mjs';
// Usage: node scripts/axe_audit.mjs <path-to-axe.min.js>. Injects axe-core and lists violations per page (1440x900).
const axeSrc = fs.readFileSync(process.argv[2], 'utf8');
const ROLES = {
  guest: [null, ['/', '/login', '/register', '/products', '/products/1', '/stores']],
  customer: ['khachhang1@example.com', ['/', '/account', '/cart', '/orders', '/orders/1']],
  staff: ['staff.thuduc@oneshop.vn', ['/staff', '/staff/fulfillments', '/staff/reviews', '/staff/stock', '/staff/inventory-history']],
  admin: ['admin@oneshop.vn', ['/admin', '/admin/orders', '/admin/payments', '/admin/products', '/admin/categories', '/admin/stores', '/admin/users', '/admin/users/new', '/admin/reviews']],
};
const page = await CdpPage.open(`${APP}/login`);
const rows = [];
try {
  await page.setViewport(1440, 900);
  for (const [role, [email, routes]] of Object.entries(ROLES)) {
    await page.clearSession(); if (email) await page.login(email);
    for (const r of routes) {
      await page.navigate(r); await sleep(300);
      await page.eval(axeSrc);
      const res = await page.eval(`(async () => { const r = await axe.run(document, { runOnly: ['wcag2a', 'wcag2aa'], resultTypes: ['violations'] }); return r.violations.map(v => ({ id: v.id, impact: v.impact, n: v.nodes.length, sample: v.nodes[0].target.join(' ').slice(0, 70) })); })()`);
      rows.push({ role, route: r, violations: res });
    }
  }
} finally { await page.close(); }
fs.writeFileSync('artifacts/ui-audit/v2/axe.json', JSON.stringify(rows, null, 1));
const agg = {};
for (const r of rows) for (const v of r.violations) { const k = v.id + ' [' + v.impact + ']'; (agg[k] ||= { pages: 0, nodes: 0, sample: v.sample, route: r.role + r.route }); agg[k].pages++; agg[k].nodes += v.n; }
console.log(JSON.stringify(agg, null, 1));
