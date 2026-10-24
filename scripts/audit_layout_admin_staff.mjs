import fs from 'node:fs';
import path from 'node:path';
import { APP, CdpPage, sleep } from './lib/cdp.mjs';

const out = path.resolve('screenshots', 'audit-layout');
const ROLES = {
  admin: { email: 'admin@oneshop.vn', routes: ['/admin', '/admin/orders', '/admin/payments', '/admin/inventory', '/admin/store-products', '/admin/store-products/new', '/admin/products', '/admin/products/new', '/admin/categories', '/admin/brands', '/admin/users', '/admin/users/new', '/admin/stores', '/admin/stores/new', '/admin/staff-assignments', '/admin/reviews'] },
  staff: { email: 'staff.thuduc@oneshop.vn', routes: ['/staff', '/staff/fulfillments', '/staff/fulfillments/delivery', '/staff/fulfillments/pickup', '/staff/orders', '/staff/reviews', '/staff/stock', '/staff/stock/1/adjust', '/staff/inventory-history'] },
};
const probe = `(() => {
  const issues = [];
  const vis = e => { const r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0; };
  if (document.documentElement.scrollWidth > innerWidth + 1) issues.push('page horizontal overflow ' + document.documentElement.scrollWidth);
  const txt = document.body.innerText;
  if (['Whitelabel','Exception','Internal Server Error','th:text','\$'+'{'].some(k => txt.includes(k))) issues.push('error/raw text visible');
  const alert = document.querySelector('.alert-danger'); if (alert && vis(alert)) issues.push('alert-danger: ' + alert.innerText.trim().slice(0, 80));
  // button heights per sibling group
  const groups = new Map();
  document.querySelectorAll('main .btn, main button.btn, main a.btn').forEach(b => { if (!vis(b)) return; const p = b.parentElement; if (!groups.has(p)) groups.set(p, []); groups.get(p).push(b); });
  groups.forEach((bs, p) => { if (bs.length < 2) return; const hs = bs.map(b => Math.round(b.getBoundingClientRect().height)); if (Math.max(...hs) - Math.min(...hs) > 2) issues.push('uneven buttons [' + hs.join(',') + '] ' + bs.map(b => b.innerText.trim().slice(0, 14)).join('|')); });
  // table row cell misalignment / overflow of cells
  document.querySelectorAll('main table').forEach(t => t.querySelectorAll('tbody tr').forEach(tr => { const r = tr.getBoundingClientRect(); [...tr.children].forEach(td => { const b = td.getBoundingClientRect(); if (Math.abs(b.top - r.top) > 1 || Math.abs(b.bottom - r.bottom) > 1) issues.push('cell misaligned'); }); }));
  // elements overflowing their container
  document.querySelectorAll('main *').forEach(e => { if (!vis(e)) return; const p = e.parentElement; if (!p || p.closest('.table-responsive,.overflow-auto,.modal')) return; const a = e.getBoundingClientRect(), b = p.getBoundingClientRect(); if (a.right > b.right + 2 && getComputedStyle(p).overflowX === 'visible' && b.width > 0 && !['TR','TD','TH','TBODY','THEAD'].includes(e.tagName)) issues.push('overflow ' + e.tagName + '.' + String(e.className).slice(0, 30)); });
  return { title: document.title, path: location.pathname, issues: [...new Set(issues)].slice(0, 12) };
})()`;
const only = process.argv[2] ? process.argv[2].split(',').map(r => r.startsWith('/') ? r : '/' + r) : null;
const page = await CdpPage.open(`${APP}/login`);
const report = []; fs.mkdirSync(out, { recursive: true });
try {
  for (const [role, cfg] of Object.entries(ROLES)) {
    await page.clearSession();
    await page.login(cfg.email);
    for (const w of [1440, 390]) {
      await page.setViewport(w, 900);
      for (const route of cfg.routes.filter(r => !only || only.includes(r)).concat([])) {
        await page.navigate(route);
        await sleep(350);
        const info = await page.eval(probe);
        const h = await page.eval('Math.min(document.documentElement.scrollHeight, 4000)');
        const name = route.replace(/^\/|\/$/g, '').replace(/[\/]+/g, '_') || 'root';
        const file = path.join(out, role, `${name}-${w}.png`);
        fs.mkdirSync(path.dirname(file), { recursive: true });
        await page.send('Emulation.setDeviceMetricsOverride', { width: w, height: h, deviceScaleFactor: 1, mobile: false });
        await sleep(150);
        const shot = await page.send('Page.captureScreenshot', { format: 'png' });
        fs.writeFileSync(file, Buffer.from(shot.data, 'base64'));
        await page.setViewport(w, 900);
        report.push({ role, w, route, ...info });
      }
    }
  }
} catch (e) { report.push({ error: e.stack || String(e) }); process.exitCode = 1; }
finally { if (!only) fs.writeFileSync(path.join(out, 'report.json'), JSON.stringify(report, null, 2)); await page.close(); }
for (const r of report) console.log(r.error || `${r.role} ${r.w} ${r.route} -> ${r.issues.length ? r.issues.join(' ; ') : 'ok'}`);
