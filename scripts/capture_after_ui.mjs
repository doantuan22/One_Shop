import path from 'node:path';
import { APP, CdpPage, DEMO_PASSWORD, sleep } from './lib/cdp.mjs';

// After-UI captures for the admin/staff UI refresh (GET navigations only - no writes).
const OUT_ADMIN = path.resolve('screenshots', 'after-admin');
const OUT_STAFF = path.resolve('screenshots', 'after-staff');
const STAFF = 'staff.thuduc@oneshop.vn';
const ADMIN = 'admin@oneshop.vn';

const widthsFull = [[1440, 1024], [1366, 900], [1024, 1024], [768, 1024], [390, 844]];

function safeName(value) {
  return String(value).replace(/[^a-z0-9-]+/gi, '-').replace(/^-|-$/g, '').toLowerCase();
}

async function loginFresh(page, email) {
  await page.clearSession();
  await page.login(email);
}

async function toOuted(role) {
  const out = role === 'staff' ? OUT_STAFF : OUT_ADMIN;
  const fs = await import('node:fs');
  fs.mkdirSync(out, { recursive: true });
  return out;
}

async function captureAt(role, page, name, route, viewport) {
  const out = await toOuted(role);
  await page.setViewport(viewport[0], viewport[1]);
  await page.navigate(route);
  await sleep(350);
  const file = path.join(out, `${safeName(name)}-${viewport[0]}x${viewport[1]}.png`);
  await page.screenshot(file);
  console.log(`  saved screenshots/${path.basename(out)}/${path.basename(file)}`);
  return file;
}

// Full captures: every route at every responsive width (desktop first, then phones).
async function captureRouteResponsive(role, page, name, route) {
  const files = [];
  for (const viewport of widthsFull) {
    files.push(await captureAt(role, page, name, route, viewport));
  }
  return files;
}

// Single-width (full scrolling) captures for page galleries.
async function captureRoute(role, page, name, route) {
  const out = await toOuted(role);
  const widths = [[1440, 900]];
  const files = [];
  for (const viewport of widths) {
    await page.setViewport(viewport[0], viewport[1]);
    await page.navigate(route);
    await sleep(300);
    const vh = await page.eval('window.innerHeight');
    const sh = await page.eval('document.documentElement.scrollHeight');
    const positions = [];
    let pos = 0;
    while (pos + vh < sh - 2) {
      positions.push(pos);
      pos += vh;
    }
    positions.push(Math.max(0, sh - vh));
    const unique = [];
    for (const p of positions) {
      if (unique.length === 0 || p !== unique[unique.length - 1]) unique.push(p);
    }
    if (unique.length === 0) unique.push(0);
    for (let i = 0; i < unique.length; i++) {
      await page.eval(`window.scrollTo(0, ${unique[i]})`);
      await sleep(150);
      const file = path.join(out, `${safeName(name)}-${String(i + 1).padStart(2, '0')}.png`);
      await page.screenshot(file);
      files.push(path.relative(process.cwd(), file));
      console.log(`  saved screenshots/${path.basename(out)}/${path.basename(file)} (scrollY=${unique[i]})`);
    }
    await page.eval('window.scrollTo(0, 0)');
  }
  return files;
}

const page = await CdpPage.open(`${APP}/login`);
const consoleProbe = {
  badRequests: [],
  runtimeErrors: page.errors,
};
try {
  await page.setViewport(1440, 900);

  // ---------- ADMIN ----------
  await loginFresh(page, ADMIN);
  for (const r of [
    { name: 'dashboard', path: '/admin' },
    { name: 'orders', path: '/admin/orders' },
    { name: 'payments', path: '/admin/payments' },
    { name: 'inventory', path: '/admin/inventory' },
    { name: 'store-products', path: '/admin/store-products' },
    { name: 'reviews', path: '/admin/reviews' },
    { name: 'products', path: '/admin/products' },
    { name: 'categories', path: '/admin/categories' },
    { name: 'brands', path: '/admin/brands' },
    { name: 'users', path: '/admin/users' },
    { name: 'stores', path: '/admin/stores' },
    { name: 'staff-assignments', path: '/admin/staff-assignments' },
  ]) {
    const route = r.path;
    await captureRoute('admin', page, r.name, route);
  }

  // Responsive sweep of the moderation and queue-style pages.
  for (const r of [
    { name: 'resp-reviews', path: '/admin/reviews' },
    { name: 'resp-orders', path: '/admin/orders' },
  ]) {
    await captureRouteResponsive('admin', page, r.name, r.path);
  }

  // ---------- STAFF ----------
  await loginFresh(page, STAFF);
  await page.navigate('/staff');
  // Modal preview check via DOM only (open - type - read - cancel, no submit)
  await sleep(300);
  const probe = await page.eval(`(() => {
    const modal = document.querySelector('[id^=adjust-modal-]');
    return { modal: !!modal, modalId: modal ? modal.id : null };
  })()`);
  console.log(`  [staff stock modal probe] ${JSON.stringify(probe)}`);
  for (const r of [
    { name: 'dashboard', path: '/staff' },
    { name: 'fulfillments', path: '/staff/fulfillments' },
    { name: 'fulfillments-customer-filter', path: '/staff/fulfillments?q=Kh%C3%A1nh' },
    { name: 'fulfillments-delivery', path: '/staff/fulfillments/delivery' },
    { name: 'fulfillments-pickup', path: '/staff/fulfillments/pickup' },
    { name: 'fulfillment-detail', path: '/staff/fulfillments/4' },
    { name: 'stock', path: '/staff/stock' },
    { name: 'stock-adjust-form', path: '/staff/stock/1/adjust' },
    { name: 'inventory-history-log', path: '/staff/inventory-history' },
    { name: 'inventory-history-select', path: '/staff/inventory-history?view=select' },
    { name: 'inventory-history-product', path: '/staff/inventory-history/1' },
    { name: 'reviews', path: '/staff/reviews' },
  ]) {
    await captureRoute('staff', page, r.name, r.path);
  }

  for (const r of [
    { name: 'resp-dashboard', path: '/staff' },
    { name: 'resp-queue', path: '/staff/fulfillments' },
    { name: 'resp-stock', path: '/staff/stock' },
    { name: 'resp-log', path: '/staff/inventory-history' },
  ]) {
    await captureRouteResponsive('staff', page, r.name, r.path);
  }

  console.log('console/runtime errors during captures:', consoleProbe.runtimeErrors.length);
} finally {
  const report = {
    generatedAt: new Date().toISOString(),
    app: APP,
    runtimeErrors: consoleProbe.runtimeErrors,
  };
  const fs = await import('node:fs');
  fs.writeFileSync(path.resolve('screenshots', 'after-manifest.json'), JSON.stringify(report, null, 2));
  try { await page.close(); } catch { /* best effort */ }
}
console.log('done');
