import fs from 'node:fs';
import path from 'node:path';
import { APP, CdpPage, sleep } from './lib/cdp.mjs';

const out = path.resolve('artifacts', 'ui-audit', 'table-fix');
const report = { app: APP, generatedAt: new Date().toISOString(), pages: [] };
const page = await CdpPage.open(`${APP}/login`);

async function inspect(label, route) {
  await page.setViewport(1440, 900);
  await page.navigate(route);
  await sleep(200);
  const data = await page.eval(`(() => {
    const table = document.querySelector('.os-table');
    const rows = [...table.querySelectorAll('tbody tr')];
    return {
      table: {
        width: Math.round(table.getBoundingClientRect().width),
        borderCollapse: getComputedStyle(table).borderCollapse,
        tableLayout: getComputedStyle(table).tableLayout
      },
      rows: rows.map((tr, rowIndex) => {
        const cells = [...tr.children];
        return {
          rowIndex,
          rowTop: +tr.getBoundingClientRect().top.toFixed(2),
          rowBottom: +tr.getBoundingClientRect().bottom.toFixed(2),
          cells: cells.map(td => ({
            text: (td.innerText || '').trim().slice(0, 30),
            tag: td.tagName,
            display: getComputedStyle(td).display,
            top: +td.getBoundingClientRect().top.toFixed(2),
            bottom: +td.getBoundingClientRect().bottom.toFixed(2),
            height: +td.getBoundingClientRect().height.toFixed(2),
            padding: getComputedStyle(td).padding,
            borderBottom: getComputedStyle(td).borderBottomWidth + ' ' + getComputedStyle(td).borderBottomColor
          }))
        };
      })
    };
  })()`);
  report.pages.push({ label, route, ...data });
  await page.screenshot(path.join(out, `before-${label}-1440.png`));
  await page.setViewport(390, 844);
  await page.navigate(route);
  await sleep(200);
  const mobile = await page.eval(`(() => ({
    viewport: innerWidth,
    pageScrollWidth: document.documentElement.scrollWidth,
    tableScrollWidth: document.querySelector('.table-responsive').scrollWidth,
    tableClientWidth: document.querySelector('.table-responsive').clientWidth,
    tableWidth: Math.round(document.querySelector('.os-table').getBoundingClientRect().width)
  }))()`);
  report.pages.push({ label: `${label}-mobile`, route, ...mobile });
  await page.screenshot(path.join(out, `before-${label}-390.png`));
}

try {
  await page.clearSession();
  await page.login('admin@oneshop.vn');
  await inspect('categories', '/admin/categories');
  await inspect('brands', '/admin/brands');
  report.result = 'OK';
} catch (error) {
  report.result = 'ERROR';
  report.error = error.stack || String(error);
  process.exitCode = 1;
} finally {
  fs.mkdirSync(out, { recursive: true });
  fs.writeFileSync(path.join(out, 'before-measurements.json'), JSON.stringify(report, null, 2));
  await page.close();
}

console.log(JSON.stringify(report, null, 2));
