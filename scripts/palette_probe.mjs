import fs from 'node:fs';
import { APP, CdpPage, sleep } from './lib/cdp.mjs';
const out = process.argv[2];
const page = await CdpPage.open(`${APP}/login`);
const PROBES = [
  ['customer', '/products/1', [['add-to-cart', 'form[action="/cart/items"] button[type=submit]'], ['buy-now', '.btn-buy-now, [data-buy-now], form[action*="checkout"] button'], ['navbar', 'header, nav.navbar'], ['body', 'body']]],
  ['admin', '/admin/stores', [['appbar', '.os-appbar'], ['sidebar-active', '.nav-link.os-nav-link.active, .os-nav-link.active'], ['btn-primary', '.btn-primary'], ['status-active', '.badge.os-soft-success'], ['status-neutral', '.badge.os-soft-neutral'], ['table-head', '.os-table thead th'], ['body', 'body'], ['btn-outline-primary', '.btn-outline-primary'], ['btn-outline-secondary', '.btn-outline-secondary'], ['btn-outline-danger', '.btn-outline-danger']]],
  ['staff', '/staff', [['appbar', '.os-appbar'], ['sidebar-active', '.os-nav-link.active'], ['btn-primary', '.btn-primary'], ['body', 'body']]],
];
const res = {};
try {
  for (const [role, route, probes] of PROBES) {
    await page.clearSession();
    if (role !== 'guest') await page.login({ customer: 'khachhang1@example.com', admin: 'admin@oneshop.vn', staff: 'staff.thuduc@oneshop.vn' }[role]);
    await page.setViewport(1440, 900); await page.navigate(route); await sleep(400);
    for (const [name, sel] of probes) {
      res[`${role}:${name}`] = await page.eval(`(() => { const e = document.querySelector(${JSON.stringify(sel)}); if (!e) return null; const c = getComputedStyle(e); return { color: c.color, bg: c.backgroundColor, border: c.borderTopColor, shadow: c.boxShadow === 'none' ? 'none' : 'yes' }; })()`);
    }
  }
} finally { await page.close(); }
fs.writeFileSync(out, JSON.stringify(res, null, 1)); console.log(JSON.stringify(res));
