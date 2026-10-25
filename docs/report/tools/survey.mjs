// Survey of the running OneShop site: logs in as each demo role and saves a raw full-page screenshot plus a DOM
// summary of every page, so that the annotation spec can be written against the real UI.
// Requires Chrome with --remote-debugging-port=9222 and the app on ONESHOP_APP_URL (default http://127.0.0.1:8080).
import fs from 'node:fs';
import path from 'node:path';
import { CdpPage, sleep } from './lib/cdp.mjs';

process.env.ONESHOP_APP_URL ||= 'http://127.0.0.1:8080';
const OUT = process.argv[2];
const PASSWORD = process.env.ONESHOP_SEED_PASSWORD;
if (!OUT || !PASSWORD) throw new Error('usage: ONESHOP_SEED_PASSWORD=... node survey.mjs <outDir>');

const ROLES = {
  guest: { login: null, pages: [
    ['home', '/'], ['login', '/login'], ['register', '/register'], ['forgot', '/forgot-password'],
    ['products', '/products'], ['product-detail', '/products/1'], ['stores', '/stores'], ['credits', '/image-credits'],
  ] },
  customer: { login: 'khachhang1@example.com', pages: [
    ['account', '/account'], ['orders', '/orders'], ['order-1', '/orders/1'], ['order-13', '/orders/13'],
    ['order-7', '/orders/7'], ['return-form', '/orders/1/return'], ['cart', '/cart'], ['payment-8', '/orders/8/payments'],
    ['checkout-1', '/checkout/1'],
  ] },
  admin: { login: 'admin@example.com', pages: [
    ['dashboard', '/admin'], ['users', '/admin/users'], ['user-new', '/admin/users/new'], ['user-edit', '/admin/users/2/edit'],
    ['assignments', '/admin/staff-assignments'], ['stores', '/admin/stores'], ['store-new', '/admin/stores/new'],
    ['categories', '/admin/categories'], ['brands', '/admin/brands'], ['products', '/admin/products'],
    ['product-new', '/admin/products/new'], ['product-edit', '/admin/products/1/edit'],
    ['store-products', '/admin/store-products'], ['store-product-new', '/admin/store-products/new'],
    ['store-product-edit', '/admin/store-products/1/edit'],
    ['inventory', '/admin/inventory'], ['orders', '/admin/orders'], ['order-detail', '/admin/orders/13'],
    ['payments', '/admin/payments'], ['payment-detail', '/admin/payments/8'], ['returns', '/admin/returns'],
    ['promotions', '/admin/promotions'], ['promotion-new', '/admin/promotions/new'], ['promotion-edit', '/admin/promotions/1/edit'],
    ['reviews', '/admin/reviews'],
  ] },
  staff: { login: 'staff.thuduc@example.com', pages: [
    ['dashboard', '/staff'], ['fulfillments', '/staff/fulfillments?open=false'], ['delivery', '/staff/fulfillments/delivery'],
    ['pickup', '/staff/fulfillments/pickup'], ['f-confirmed', '/staff/fulfillments/2'], ['f-packed', '/staff/fulfillments/4'],
    ['f-shipping', '/staff/fulfillments/5'], ['f-ready', '/staff/fulfillments/6'], ['stock', '/staff/stock'],
    ['stock-adjust', '/staff/stock/1/adjust'], ['inventory-history', '/staff/inventory-history'],
    ['history-1', '/staff/inventory-history/1'], ['returns', '/staff/returns'], ['reviews', '/staff/reviews'],
    ['review-1', '/staff/reviews/1'],
  ] },
};

async function shoot(page, file, maxH = 4000) {
  const dims = await page.eval(`({w: document.documentElement.clientWidth, h: Math.min(document.documentElement.scrollHeight, ${maxH})})`);
  const result = await page.send('Page.captureScreenshot', {
    format: 'png', captureBeyondViewport: true, clip: { x: 0, y: 0, width: dims.w, height: dims.h, scale: 1 },
  });
  fs.writeFileSync(file, Buffer.from(result.data, 'base64'));
  return dims;
}

const summary = {};
for (const [role, cfg] of Object.entries(ROLES)) {
  const page = await CdpPage.open('about:blank');
  await page.setViewport(1440, 900);
  await page.clearSession();
  if (cfg.login) await page.login(cfg.login, PASSWORD);
  fs.mkdirSync(path.join(OUT, role), { recursive: true });
  for (const [name, url] of cfg.pages) {
    try {
      await page.navigate(url);
      await sleep(500);
      const info = await page.eval(`(() => {
        const t = (e) => (e.innerText || e.value || '').trim().replace(/\\s+/g, ' ').slice(0, 60);
        return {
          path: location.pathname + location.search, title: document.title,
          h1: [...document.querySelectorAll('h1,h2')].map(t).slice(0, 6),
          buttons: [...document.querySelectorAll('button,a.btn,input[type=submit]')].map(t).filter(Boolean).slice(0, 25),
          links: [...document.querySelectorAll('a[href]')].map(e => t(e) + ' -> ' + e.getAttribute('href')).slice(0, 40),
          fields: [...document.querySelectorAll('input,select,textarea')].map(e => (e.name || e.id) + ':' + e.type).slice(0, 25),
          rows: document.querySelectorAll('tbody tr').length,
        };
      })()`);
      const dims = await shoot(page, path.join(OUT, role, `${name}.png`));
      summary[`${role}/${name}`] = { status: page.lastDocumentStatus, dims, ...info };
      console.log(role, name, page.lastDocumentStatus, dims.h, info.path);
    } catch (e) {
      console.log('ERR', role, name, e.message);
      summary[`${role}/${name}`] = { error: e.message };
    }
  }
  await page.close();
}
fs.writeFileSync(path.join(OUT, 'summary.json'), JSON.stringify(summary, null, 1));
