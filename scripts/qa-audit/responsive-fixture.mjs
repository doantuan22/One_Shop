import fs from 'node:fs';
import path from 'node:path';
import { APP, CdpPage, DEMO_PASSWORD, sleep } from '../lib/cdp.mjs';

const OUT = path.resolve('test-results/full-system-audit/evidence/browser-responsive');
fs.mkdirSync(OUT, { recursive: true });

const CUSTOMER = 'khachhang1@example.com';
const STAFF_STORE_1 = 'staff.thuduc@example.com';
const ADMIN = 'admin@example.com';
const WIDTHS = [375, 768, 1440];

const evidence = {
  startedAt: new Date().toISOString(),
  app: APP,
  database: 'oneshop_fixture_20261025_test',
  widths: WIDTHS,
  method: 'Authenticated browser navigation plus one real cart/checkout interaction; no order, stock, payment or admin form submitted.',
  captures: [],
  failures: [],
};

function assert(condition, message) {
  if (!condition) throw new Error(`ASSERTION FAILED: ${message}`);
}

async function loginFresh(page, email) {
  await page.clearSession();
  await page.login(email);
  assert(!(await page.pathname()).startsWith('/login'), `${email} is authenticated`);
}

function safeName(value) {
  return value.replace(/[^a-z0-9-]+/gi, '-').replace(/^-|-$/g, '').toLowerCase();
}

async function metrics(page) {
  return page.eval(`(() => {
    const root = document.documentElement;
    const vw = root.clientWidth;
    const layoutOverflow = Math.max(0, root.scrollWidth - vw);
    const startX = window.scrollX;
    window.scrollTo(9999, window.scrollY);
    const scrolledX = window.scrollX;
    window.scrollTo(startX, window.scrollY);
    const overflow = scrolledX > 0 ? layoutOverflow : 0;
    const offenders = [];
    if (layoutOverflow > 1) {
      for (const el of document.querySelectorAll('body *')) {
        const cs = getComputedStyle(el);
        if (cs.display === 'none' || cs.visibility === 'hidden' || Number(cs.opacity) === 0) continue;
        const r = el.getBoundingClientRect();
        if (r.width < 2 || r.height < 2) continue;
        if (r.right > vw + 1 || r.left < -1) {
          offenders.push({
            tag: el.tagName.toLowerCase(),
            id: el.id || null,
            className: typeof el.className === 'string' ? el.className.slice(0, 120) : null,
            left: Math.round(r.left),
            right: Math.round(r.right),
            width: Math.round(r.width)
          });
          if (offenders.length >= 8) break;
        }
      }
    }
    return {
      title: document.title,
      path: location.pathname + location.search,
      documentWidth: root.scrollWidth,
      viewportWidth: vw,
      viewportOuterWidth: window.innerWidth,
      layoutOverflow,
      scrollX: scrolledX,
      overflow,
      offenders,
      interactiveCount: document.querySelectorAll('a, button, input, select, textarea').length,
      hasMain: Boolean(document.querySelector('main')),
      bodyText: (document.querySelector('main')?.innerText || document.body.innerText || '').replace(/\\s+/g, ' ').trim().slice(0, 240)
    };
  })()`);
}

async function captureRoute(page, role, name, route, width) {
  await page.setViewport(width, 900);
  await page.navigate(route);
  await sleep(120);
  return captureCurrent(page, role, name, width);
}

async function captureCurrent(page, role, name, width) {
  const info = await metrics(page);
  const status = page.lastDocumentStatus;
  const file = path.join(OUT, `${role}-${safeName(name)}-${width}.png`);
  await page.eval('window.scrollTo(0, 0)');
  await sleep(100);
  await page.screenshot(file);
  const row = {
    role,
    name,
    requested: role === 'customer-checkout' ? null : undefined,
    url: info.path,
    width,
    httpStatus: status,
    title: info.title,
    documentWidth: info.documentWidth,
    viewportWidth: info.viewportWidth,
    viewportOuterWidth: info.viewportOuterWidth,
    layoutOverflow: info.layoutOverflow,
    scrollX: info.scrollX,
    overflow: info.overflow,
    offenders: info.offenders,
    interactiveCount: info.interactiveCount,
    hasMain: info.hasMain,
    bodyText: info.bodyText,
    screenshot: path.relative(process.cwd(), file),
  };
  evidence.captures.push(row);
  const bad = status !== 200 || info.overflow > 1 || !info.hasMain;
  if (bad) evidence.failures.push(row);
  console.log(`${bad ? 'FAIL' : 'PASS'} ${role}/${name} @${width} status=${status} overflow=${info.overflow}`);
  return row;
}

async function captureSet(page, role, routes) {
  for (const route of routes) {
    for (const width of WIDTHS) {
      const row = await captureRoute(page, role, route.name, route.path, width);
      row.requested = route.path;
    }
  }
}

const page = await CdpPage.open(`${APP}/login`);
await page.setViewport(1440, 900);

try {
  // ------------------------------------------------------------------ Guest
  await page.clearSession();
  await captureSet(page, 'guest', [
    { name: 'home', path: '/' },
    { name: 'products', path: '/products' },
    { name: 'product-detail', path: '/products/2' },
    { name: 'stores', path: '/stores' },
    { name: 'login', path: '/login' },
    { name: 'register', path: '/register' },
    { name: 'image-credits', path: '/image-credits' },
  ]);

  // ------------------------------------------------------------------ Customer
  await loginFresh(page, CUSTOMER);

  // One real cart interaction gives the cart and checkout pages real data.
  await page.navigate('/products/2');
  await page.waitFor(`Boolean(document.querySelector('#add-to-cart form'))`, 'add-to-cart form');
  await page.fill('#add-quantity', '1');
  await page.clickAndWait('#add-to-cart form button[type="submit"]');
  await page.navigate('/cart');
  await page.waitFor(`document.querySelectorAll('.os-cart-item').length >= 1`, 'cart row');
  const cartItemId = await page.eval(`Number(document.querySelector('.os-cart-item')?.dataset.cartItemId)`);
  assert(Number.isFinite(cartItemId) && cartItemId > 0, 'cart item id is present');

  await captureSet(page, 'customer', [
    { name: 'cart', path: '/cart' },
    { name: 'orders', path: '/orders' },
    { name: 'order-online-partial-refund', path: '/orders/2' },
    { name: 'order-pickup-completed', path: '/orders/1' },
    { name: 'payment-online', path: '/orders/2/payments' },
    { name: 'checkout-result', path: '/checkout/1' },
  ]);

  // Open the real checkout form for the selected cart line, then capture its actual URL at every width.
  await page.setViewport(1440, 900);
  await page.navigate('/cart');
  await page.eval(`(() => {
    const all = document.getElementById('select-all-items');
    if (all && !all.checked) all.click();
    return true;
  })()`);
  await page.waitFor(`!document.getElementById('checkout-submit').disabled`, 'checkout submit enabled');
  await page.clickAndWait('#checkout-submit');
  await page.waitFor(`location.pathname === '/checkout'`, 'checkout page');
  const checkoutUrl = await page.eval('location.pathname + location.search');
  assert(checkoutUrl.includes(`cartItemIds=${cartItemId}`), 'checkout URL names the selected cart line');
  for (const width of WIDTHS) {
    await page.setViewport(width, 900);
    await page.navigate(checkoutUrl);
    const row = await captureCurrent(page, 'customer', 'checkout', width);
    row.requested = checkoutUrl;
  }

  // Leave the disposable test cart as it was found.
  await page.setViewport(1440, 900);
  await page.navigate('/cart');
  while ((await page.eval(`document.querySelectorAll('.os-cart-item').length`)) > 0) {
    await page.clickAndWait('.os-cart-item form[action$="/delete"] button[type="submit"]');
  }

  // ------------------------------------------------------------------ Staff (Store 1)
  await loginFresh(page, STAFF_STORE_1);
  await captureSet(page, 'staff', [
    { name: 'dashboard', path: '/staff' },
    { name: 'fulfillments', path: '/staff/fulfillments' },
    { name: 'fulfillments-delivery', path: '/staff/fulfillments/delivery' },
    { name: 'fulfillments-pickup', path: '/staff/fulfillments/pickup' },
    { name: 'fulfillment-detail', path: '/staff/fulfillments/1' },
    { name: 'stock', path: '/staff/stock' },
    { name: 'stock-history', path: '/staff/inventory-history' },
    { name: 'stock-adjust', path: '/staff/stock/1/adjust' },
    { name: 'reviews', path: '/staff/reviews' },
    { name: 'review-detail', path: '/staff/reviews/1' },
  ]);

  // ------------------------------------------------------------------ Admin
  await loginFresh(page, ADMIN);
  await captureSet(page, 'admin', [
    { name: 'dashboard', path: '/admin' },
    { name: 'orders', path: '/admin/orders' },
    { name: 'order-detail', path: '/admin/orders/2' },
    { name: 'payments', path: '/admin/payments' },
    { name: 'payment-detail', path: '/admin/payments/2' },
    { name: 'products', path: '/admin/products' },
    { name: 'product-edit', path: '/admin/products/2/edit' },
    { name: 'product-new', path: '/admin/products/new' },
    { name: 'store-products', path: '/admin/store-products' },
    { name: 'store-product-edit', path: '/admin/store-products/1/edit' },
    { name: 'stores', path: '/admin/stores' },
    { name: 'store-edit', path: '/admin/stores/1/edit' },
    { name: 'categories', path: '/admin/categories' },
    { name: 'category-edit', path: '/admin/categories/1/edit' },
    { name: 'brands', path: '/admin/brands' },
    { name: 'brand-edit', path: '/admin/brands/1/edit' },
    { name: 'users', path: '/admin/users' },
    { name: 'user-edit', path: '/admin/users/1/edit' },
    { name: 'reviews', path: '/admin/reviews' },
    { name: 'staff-assignments', path: '/admin/staff-assignments' },
    { name: 'inventory', path: '/admin/inventory' },
  ]);

  evidence.finishedAt = new Date().toISOString();
  evidence.captureCount = evidence.captures.length;
  evidence.overflowCount = evidence.captures.filter((row) => row.overflow > 1).length;
  evidence.result = evidence.failures.length === 0 ? 'PASS' : 'FAIL';
  fs.writeFileSync(path.join(OUT, 'responsive-result.json'), JSON.stringify(evidence, null, 2));
  console.log(`Responsive ${evidence.result}: ${evidence.captures.length} captures, ${evidence.failures.length} failures`);
  if (evidence.failures.length) process.exitCode = 1;
} catch (error) {
  evidence.finishedAt = new Date().toISOString();
  evidence.result = 'FAIL';
  evidence.error = error.stack || String(error);
  try {
    await page.screenshot(path.join(OUT, '99-responsive-failure.png'));
  } catch {
    // Ignore a screenshot failure while already failing.
  }
  fs.writeFileSync(path.join(OUT, 'responsive-result.json'), JSON.stringify(evidence, null, 2));
  console.error(error.stack || error);
  process.exitCode = 1;
} finally {
  await page.close();
}
