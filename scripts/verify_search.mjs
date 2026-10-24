import fs from 'node:fs';
import { APP, CdpPage, sleep } from './lib/cdp.mjs';

// Search boxes: suggestions + word/accent-insensitive filtering on every page that has a product search.
const OUT = 'artifacts/ui-audit/v2';
const page = await CdpPage.open(`${APP}/login`);
const out = [];

async function suggest(selector, text) {
  return page.eval(`(async () => {
    const i = document.querySelector(${JSON.stringify(selector)});
    i.focus(); i.value = ${JSON.stringify(text)};
    i.dispatchEvent(new Event('input', { bubbles: true }));
    await new Promise(r => setTimeout(r, 500));
    const l = document.querySelector('.os-suggest-pop:not([hidden])');
    return { open: !!l, items: l ? [...l.querySelectorAll('.os-suggest-pop__item')].map(x => x.textContent) : [] };
  })()`);
}

async function count(url, expr) {
  await page.navigate(url); await sleep(500);
  return page.eval(expr);
}

try {
  await page.setViewport(1440, 900);

  // ---- Guest: home + catalog
  await page.clearSession();
  await page.navigate('/'); await sleep(600);
  out.push(['home suggestions "kem duong cerave"', await suggest('#hero-q', 'kem duong cerave')]);
  fs.writeFileSync(`${OUT}/search-home.png`, Buffer.from((await page.send('Page.captureScreenshot', { format: 'png' })).data, 'base64'));
  const catalogLinks = `new Set([...document.querySelectorAll('main a[href]')].map(a => a.getAttribute('href')).filter(h => h.indexOf('/products/') === 0 && h.length < 16)).size`;
  for (const q of ['kem duong am', 'cerave kem', 'dưỡng ẩm', 'zzzz']) {
    out.push(['catalog q=' + q, await count('/products?q=' + encodeURIComponent(q), catalogLinks)]);
  }
  await page.navigate('/products'); await sleep(500);
  out.push(['catalog suggestions "son"', await suggest('#q', 'son')]);

  // ---- Admin
  await page.clearSession(); await page.login('admin@oneshop.vn');
  const rows = `document.querySelectorAll('.os-table tbody tr').length`;
  for (const [path, label] of [['/admin/products', 'admin products'], ['/admin/inventory', 'admin inventory']]) {
    for (const q of ['kem duong cerave', 'DUONG am', 'zzzz']) {
      out.push([label + ' q=' + q, await count(path + '?q=' + encodeURIComponent(q), rows)]);
    }
  }
  await page.navigate('/admin/products'); await sleep(500);
  out.push(['admin products suggestions "cerave kem"', await suggest('#products-q', 'cerave kem')]);
  await page.navigate('/admin/inventory'); await sleep(500);
  out.push(['admin inventory suggestions "vaseline"', await suggest('#filter-q', 'vaseline')]);

  // ---- Staff
  await page.clearSession(); await page.login('staff.thuduc@oneshop.vn');
  for (const [path, label] of [['/staff/stock', 'staff stock'], ['/staff/inventory-history', 'staff history']]) {
    for (const q of ['kem duong cerave', 'zzzz']) {
      out.push([label + ' q=' + q, await count(path + '?q=' + encodeURIComponent(q), rows)]);
    }
  }
  await page.navigate('/staff/stock'); await sleep(500);
  out.push(['staff stock suggestions "kem duong"', await suggest('#stock-q', 'kem duong')]);
  fs.writeFileSync(`${OUT}/search-staff-stock.png`, Buffer.from((await page.send('Page.captureScreenshot', { format: 'png' })).data, 'base64'));
} finally {
  await page.close();
}
for (const [k, v] of out) console.log(k, '=>', JSON.stringify(v));
