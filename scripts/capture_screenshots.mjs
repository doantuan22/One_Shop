import fs from 'node:fs';
import path from 'node:path';
import { APP, CdpPage, DEMO_PASSWORD, sleep } from './lib/cdp.mjs';

const OUT = path.resolve('screenshots');
fs.mkdirSync(OUT, { recursive: true });

const CUSTOMER = 'khachhang1@example.com';
const STAFF = 'staff.thuduc@oneshop.vn';
const ADMIN = 'admin@oneshop.vn';
const WIDTH = 1440;
const HEIGHT = 900;

function safeName(value) {
  return String(value).replace(/[^a-z0-9-]+/gi, '-').replace(/^-|-$/g, '').toLowerCase();
}

async function setViewport(page) {
  await page.setViewport(WIDTH, HEIGHT);
}

async function captureVertical(page, role, name) {
  await page.eval('window.scrollTo(0, 0)');
  await sleep(150);
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

  const base = path.join(OUT, `${role}_${safeName(name)}`);
  const files = [];
  for (let i = 0; i < unique.length; i++) {
    await page.eval(`window.scrollTo(0, ${unique[i]})`);
    await sleep(150);
    const file = `${base}-${String(i + 1).padStart(2, '0')}.png`;
    await page.screenshot(file);
    files.push(path.relative(process.cwd(), file));
    console.log(`  saved ${path.basename(file)} (scrollY=${unique[i]}, pageHeight=${sh})`);
  }
  await page.eval('window.scrollTo(0, 0)');
  return files;
}

async function captureRoute(page, role, name, route) {
  console.log(`\n[${role}] ${name} -> ${route}`);
  await setViewport(page);
  await page.navigate(route);
  await sleep(300);
  return captureVertical(page, role, name);
}

async function loginFresh(page, email) {
  await page.clearSession();
  await page.login(email);
}

const page = await CdpPage.open(`${APP}/login`);
const allFiles = [];

try {
  await setViewport(page);

  // Guest
  await page.clearSession();
  for (const r of [
    { name: 'home', path: '/' },
    { name: 'products', path: '/products' },
    { name: 'product-detail', path: '/products/2' },
    { name: 'stores', path: '/stores' },
    { name: 'login', path: '/login' },
    { name: 'register', path: '/register' },
    { name: 'image-credits', path: '/image-credits' },
  ]) {
    allFiles.push(...await captureRoute(page, 'guest', r.name, r.path));
  }

  // Customer
  await loginFresh(page, CUSTOMER);
  // cart interaction
  await page.navigate('/products/2');
  await page.waitFor(`Boolean(document.querySelector('#add-to-cart form'))`, 'add-to-cart form');
  await page.fill('#add-quantity', '1');
  await page.clickAndWait('#add-to-cart form button[type="submit"]');
  await page.navigate('/cart');
  await page.waitFor(`document.querySelectorAll('.os-cart-item').length >= 1`, 'cart row');

  for (const r of [
    { name: 'cart', path: '/cart' },
    { name: 'orders', path: '/orders' },
    { name: 'order-online-partial-refund', path: '/orders/9' },
    { name: 'order-pickup-completed', path: '/orders/11' },
    { name: 'payment-online', path: '/orders/9/payments' },
    { name: 'checkout-result', path: '/checkout/9' },
  ]) {
    allFiles.push(...await captureRoute(page, 'customer', r.name, r.path));
  }

  // checkout form
  await setViewport(page);
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
  await captureRoute(page, 'customer', 'checkout', checkoutUrl);

  // cleanup cart
  await setViewport(page);
  await page.navigate('/cart');
  while (await page.eval(`document.querySelectorAll('.os-cart-item').length`) > 0) {
    await page.clickAndWait('.os-cart-item form[action$="/delete"] button[type="submit"]');
  }

  // Staff
  await loginFresh(page, STAFF);
  for (const r of [
    { name: 'dashboard', path: '/staff' },
    { name: 'fulfillments', path: '/staff/fulfillments' },
    { name: 'fulfillments-delivery', path: '/staff/fulfillments/delivery' },
    { name: 'fulfillments-pickup', path: '/staff/fulfillments/pickup' },
    { name: 'fulfillment-detail', path: '/staff/fulfillments/17' },
    { name: 'stock', path: '/staff/stock' },
    { name: 'stock-history', path: '/staff/inventory-history' },
    { name: 'stock-adjust', path: '/staff/stock/1/adjust' },
    { name: 'reviews', path: '/staff/reviews' },
    { name: 'review-detail', path: '/staff/reviews/1' },
  ]) {
    allFiles.push(...await captureRoute(page, 'staff', r.name, r.path));
  }

  // Admin
  await loginFresh(page, ADMIN);
  for (const r of [
    { name: 'dashboard', path: '/admin' },
    { name: 'orders', path: '/admin/orders' },
    { name: 'order-detail', path: '/admin/orders/9' },
    { name: 'payments', path: '/admin/payments' },
    { name: 'payment-detail', path: '/admin/payments/9' },
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
  ]) {
    allFiles.push(...await captureRoute(page, 'admin', r.name, r.path));
  }

  console.log(`\nDONE: ${allFiles.length} screenshot files`);
} catch (error) {
  console.error(error.stack || error);
  try { await page.screenshot(path.join(OUT, '99-capture-failure.png')); } catch {}
  process.exitCode = 1;
} finally {
  await page.close();
}