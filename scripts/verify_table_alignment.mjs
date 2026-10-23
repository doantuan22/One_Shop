import fs from 'node:fs';
import path from 'node:path';
import { APP, CdpPage, sleep } from './lib/cdp.mjs';

const out = path.resolve('artifacts', 'ui-audit', 'table-fix');
fs.mkdirSync(out, { recursive: true });
const page = await CdpPage.open(`${APP}/login`);
const report = [];
const measure = `(() => {
  const table = document.querySelector('.os-table');
  const wrap = document.querySelector('.table-responsive');
  const ths = [...table.querySelectorAll('thead th')].map(t => t.getBoundingClientRect());
  const problems = [];
  const rows = [...table.querySelectorAll('tbody tr')].map((tr, i) => {
    const r = tr.getBoundingClientRect();
    const cells = [...tr.children];
    cells.forEach((td, c) => {
      const b = td.getBoundingClientRect();
      if (getComputedStyle(td).display !== 'table-cell') problems.push('row' + i + ' cell' + c + ' display=' + getComputedStyle(td).display);
      if (Math.abs(b.top - r.top) > 0.6 || Math.abs(b.bottom - r.bottom) > 0.6) problems.push('row' + i + ' cell' + c + ' top/bottom mismatch ' + b.top + '/' + b.bottom + ' vs ' + r.top + '/' + r.bottom);
      if (cells.length === ths.length && Math.abs(b.left - ths[c].left) > 0.6) problems.push('row' + i + ' cell' + c + ' not under header');
    });
    return { h: Math.round(r.height), cells: cells.length };
  });
  return { docScroll: document.documentElement.scrollWidth, vw: innerWidth, wrapScroll: wrap.scrollWidth, wrapClient: wrap.clientWidth, tableW: Math.round(table.getBoundingClientRect().width), rowCount: rows.length, heights: rows.map(r => r.h).slice(0, 8), problems };
})()`;
try {
  await page.clearSession();
  await page.login('admin@oneshop.vn');
  for (const [label, route] of [['categories', '/admin/categories'], ['brands', '/admin/brands']]) {
    for (const w of [1440, 1366, 768, 390]) {
      await page.setViewport(w, 900);
      await page.navigate(route);
      await sleep(300);
      const m = await page.eval(measure);
      report.push({ label, w, ...m });
      await page.screenshot(path.join(out, `after-${label}-${w}.png`));
    }
    // modal regression at 1440
    await page.setViewport(1440, 900);
    await page.navigate(route);
    await sleep(300);
    const modal = await page.eval(`(async () => {
      const wait = ms => new Promise(r => setTimeout(r, ms));
      const addBtn = document.querySelector('[data-bs-toggle="modal"]');
      addBtn.click(); await wait(600);
      const m = document.querySelector('.modal.show');
      const openedAdd = !!m;
      m.querySelector('[data-bs-dismiss="modal"]').click(); await wait(600);
      const closed = !document.querySelector('.modal.show');
      return { openedAdd, closed };
    })()`);
    report.push({ label: label + '-modal-add', ...modal });
    const edit = await page.eval(`(() => { const a = document.querySelector('.os-table tbody a[href*="/edit"]'); return a ? a.getAttribute('href') : null; })()`);
    if (edit) {
      await page.navigate(edit);
      await sleep(900);
      const f = await page.eval(`(() => ({ modalShown: !!document.querySelector('.modal.show'), name: document.querySelector('.modal.show input[name="name"]')?.value || null }))()`);
      report.push({ label: label + '-modal-edit', edit, ...f });
      await page.screenshot(path.join(out, `after-${label}-edit-modal.png`));
    }
  }
  report.push({ consoleErrors: page.errors });
} catch (e) { report.push({ error: e.stack || String(e) }); process.exitCode = 1; }
finally { fs.writeFileSync(path.join(out, 'after-measurements.json'), JSON.stringify(report, null, 2)); await page.close(); }
console.log(JSON.stringify(report, null, 1));
