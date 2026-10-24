import fs from 'node:fs';
import path from 'node:path';
import { APP, CdpPage, capture, sleep } from './lib/cdp.mjs';

const out = path.resolve('artifacts', 'ui-audit', 'after');
const reportPath = path.resolve('artifacts', 'ui-audit', 'LIVE_QA_REPORT.json');
const viewports = [[1440, 900], [1366, 768], [768, 1024], [390, 844]];
const report = { app: APP, generatedAt: new Date().toISOString(), captures: [], checks: [], skipped: [], consoleErrors: [], failedResponses: [] };
const page = await CdpPage.open(`${APP}/`);

page.on('Network.responseReceived', event => {
  const status = event.response?.status ?? 0;
  const url = event.response?.url ?? '';
  if (status >= 400 && !url.startsWith('data:')) report.failedResponses.push(`${status} ${url}`);
});

async function inspect(role, name, route, selector) {
  for (const [width, height] of viewports) {
    await page.setViewport(width, height);
    await page.navigate(route);
    await sleep(220);
    const info = await capture(page, `${role}-${name}`, path.join(out, `${role}-${name}-${width}x${height}.png`));
    const target = await page.eval(`Boolean(document.querySelector(${JSON.stringify(selector)}))`);
    report.captures.push({ role, name, route, status: page.lastDocumentStatus, target, ...info });
    report.checks.push({ role, name, viewport: `${width}x${height}`, target, noPageOverflow: !info.overflow });
  }
}

try {
  await page.clearSession();
  await inspect('guest', 'home', '/', '.home-hero');
  await inspect('guest', 'catalog', '/products', '#catalog-filter');
  await inspect('guest', 'product-detail', '/products/1', '#product-price');
  await inspect('guest', 'stores', '/stores', '#store-filter');

  await page.clearSession();
  await page.login('khachhang1@example.com');
  await inspect('customer', 'cart', '/cart', '.cart-container');
  await page.navigate('/cart');
  const cartIds = await page.eval(`[...document.querySelectorAll('input[name="cartItemIds"]')].map(input => input.value).filter(Boolean)`);
  if (cartIds.length) {
    const query = cartIds.map(id => `cartItemIds=${encodeURIComponent(id)}`).join('&');
    await inspect('customer', 'checkout', `/checkout?${query}&fulfillmentType=DELIVERY&paymentMethod=ONLINE`, '#checkout-form');
  } else {
    report.skipped.push('customer/checkout: cart is empty; no mutation was performed for visual QA');
  }

  await page.clearSession();
  await page.login('admin@oneshop.vn');
  await inspect('admin', 'dashboard', '/admin', '#chain-summary');
  await inspect('admin', 'orders', '/admin/orders', '#order-filter');
  await inspect('admin', 'inventory', '/admin/inventory', '#inventory-filter');
  await inspect('admin', 'products', '/admin/products', '#product-search');

  await page.clearSession();
  await page.login('staff.thuduc@oneshop.vn');
  await inspect('staff', 'dashboard', '/staff', '.os-staff-dashboard');
  await inspect('staff', 'fulfillments', '/staff/fulfillments', '#fulfillments-body');
  await inspect('staff', 'stock', '/staff/stock', '#stock-filter');
  await inspect('staff', 'inventory-history', '/staff/inventory-history', '#movement-log-filter');
} finally {
  report.consoleErrors = page.errors;
  fs.mkdirSync(path.dirname(reportPath), { recursive: true });
  fs.writeFileSync(reportPath, JSON.stringify(report, null, 2));
  await page.close();
}

const failedChecks = report.checks.filter(check => !check.target || !check.noPageOverflow);
console.log(JSON.stringify({ captures: report.captures.length, failedChecks, consoleErrors: report.consoleErrors, failedResponses: report.failedResponses }, null, 2));
if (failedChecks.length || report.consoleErrors.length) process.exitCode = 1;
