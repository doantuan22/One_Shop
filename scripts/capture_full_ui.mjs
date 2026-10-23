import path from 'node:path';
import fs from 'node:fs';
import { APP, CdpPage, sleep } from './lib/cdp.mjs';

// FULL UI capture for every role and every page of the running app (GET-only).
// - 4 roles: guest (not logged in), customer, staff, admin.
// - 4 authentic device ratios: 1440x900 (desktop), 1366x768 (laptop), 768x1024 (tablet), 390x844 (phone).
// - Pages long enough are split into several numbered parts (p01, p02, ...) without resetting scroll.
// - Login-page states, form/new/edit pages, detail pages, 403/404 states and client-side modal
//   states are all captured. The only POST that may run is adding ONE cart item when the
//   customer cart is empty, and that item is removed again in the finally block.
//
// Requires: Chrome headless with --remote-debugging-port=9222, app running at ONESHOP_APP_URL.

const ROOT = path.resolve('screenshots');
const VIEWPORTS = [
  { label: 'desktop', w: 1440, h: 900 },
  { label: 'laptop', w: 1366, h: 768 },
  { label: 'tablet', w: 768, h: 1024 },
  { label: 'phone', w: 390, h: 844 },
];
const ADDRESS = encodeURIComponent('1 Nguyen Trai, TP. Ho Chi Minh');
const ADDRESS_PLAIN = '1 Nguyen Trai, TP. Ho Chi Minh';

const CUSTOMER = 'khachhang1@example.com';
const STAFF = 'staff.thuduc@oneshop.vn';
const ADMIN = 'admin@oneshop.vn';

const safeName = (value) => String(value).replace(/[^a-z0-9-]+/gi, '-').replace(/^-|-$/g, '').toLowerCase();

// Direct CDP screenshot: does NOT reset scroll (unlike lib page.screenshot).
async function shoot(page, file) {
  const result = await page.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
  fs.writeFileSync(file, Buffer.from(result.data, 'base64'));
}

async function pageInfo(page) {
  return page.eval(`(() => ({
    url: location.pathname + location.search,
    title: document.title,
    height: document.documentElement.scrollHeight,
    scrollWidth: document.documentElement.scrollWidth,
    viewport: window.innerHeight,
    width: document.documentElement.clientWidth,
    overflow: document.documentElement.scrollHeight > window.innerHeight,
  }))()`);
}

// Scroll the page in innerHeight chunks and save one file per chunk.
async function captureSegments(page, dir, entry, baseFile, info) {
  const files = [];
  const vh = info.viewport;
  const sh = info.height;
  const positions = [];
  for (let y = 0; y + vh < sh - 2; y += vh) positions.push(y);
  if (positions.length === 0 && sh > vh) positions.push(0);
  if (positions.length > 0 && positions[positions.length - 1] < sh - vh) positions.push(Math.max(0, sh - vh));
  if (positions.length === 0) positions.push(0);

  let part = 1;
  for (const y of positions) {
    await page.eval(`window.scrollTo(0, ${y})`);
    await sleep(200);
    const file = `${baseFile}-p${String(part).padStart(2, '0')}.png`;
    await shoot(page, file);
    files.push({
      file: path.relative(ROOT, file),
      part,
      scrollY: y,
      route: entry.path,
      height: sh,
    });
    part += 1;
  }
  // Refresh url/title on the final pass (same page for all parts).
  const meta = await pageInfo(page);
  for (const f of files) {
    f.url = meta.url;
    f.title = meta.title;
    f.clientWidth = meta.width;
    f.horizontalOverflow = meta.scrollWidth > meta.width;
  }
  await page.eval('window.scrollTo(0, 0)');
  return files;
}

// Capture one route at one viewport (navigate + settle + segments).
async function captureAtViewport(page, roleOut, entry, viewport) {
  const dir = path.join(ROOT, roleOut, entry.dir);
  fs.mkdirSync(dir, { recursive: true });
  await page.setViewport(viewport.w, viewport.h);
  await page.navigate(entry.path);
  await sleep(420);
  const info = await pageInfo(page);

  // Scroll in viewport-height chunks and shoot each segment directly (no scroll reset).
  const baseFile = path.join(dir, `${entry.seq}-${entry.name}-${viewport.w}x${viewport.h}`);
  const files = await captureSegments(page, dir, entry, baseFile, info);
  for (const f of files) {
    f.viewport = viewport.label;
    f.px = `${viewport.w}x${viewport.h}`;
    f.httpStatus = page.lastDocumentStatus ?? null;
  }
  return files;
}

// Vanilla runner for a route: every viewport (desktop -> phone), segments per viewport.
async function captureRoute(page, roleOut, entry) {
  const all = [];
  for (const viewport of VIEWPORTS) {
    all.push(...await captureAtViewport(page, roleOut, entry, viewport));
  }
  return all;
}

// Client-side-only modal states: navigate fresh, open modal, capture (desktop only), close.
async function captureModal(page, roleOut, entry) {
  const viewport = VIEWPORTS[0];
  const dir = path.join(ROOT, roleOut, entry.dir);
  fs.mkdirSync(dir, { recursive: true });
  await page.setViewport(viewport.w, viewport.h);
  await page.navigate(entry.path);
  await sleep(500);
  await page.eval(entry.openExpr);
  await page.waitFor(entry.openWait, 'modal open');
  await sleep(350);
  const info = await pageInfo(page);
  const baseFile = path.join(dir, `${entry.seq}-${entry.name}-${viewport.w}x${viewport.h}`);
  const file = `${baseFile}-p01.png`;
  await shoot(page, file);
  const meta = [
    {
      file: path.relative(ROOT, file),
      part: 1,
      viewport: viewport.label,
      px: `${viewport.w}x${viewport.h}`,
      route: entry.path,
      url: info.url,
      title: info.title,
      modal: true,
      httpStatus: page.lastDocumentStatus ?? null,
    },
  ];
  try {
    await page.eval(entry.closeExpr);
    await page.waitFor(entry.closedWait, 'modal closed', 5000).catch(() => null);
    await sleep(200);
  } catch { /* navigation away closes it anyway */ }
  return meta;
}

// Resolve a dynamic detail path by looking for a link on a list page (desktop only).
async function resolveHref(page, srcPath, matchExpr) {
  await page.setViewport(VIEWPORTS[0].w, VIEWPORTS[0].h);
  await page.navigate(srcPath);
  await sleep(350);
  const found = await page.eval(matchExpr);
  return found ?? null;
}

const PROBE_FAILED = (page, list) => {
  page.on('Network.responseReceived', (event) => {
    const status = event.response?.status ?? 0;
    const url = event.response?.url ?? '';
    if (status >= 400 && !url.startsWith('data:') && !url.includes('favicon')) {
      if (!list.some(l => l.url === url)) list.push({ status, url });
    }
  });
};

async function readCartIds(page) {
  return page.eval(`[...document.querySelectorAll('input[name="cartItemIds"]:not(:disabled), input[name="cartItemIds"][type="checkbox"]')]
      .map(e => e.value).filter(v => v)`).catch(() => []);
}

function checkoutUrl(ids, variant) {
  const base = ids.map(id => `cartItemIds=${encodeURIComponent(id)}`).join('&');
  if (variant === 'pickup') {
    return `/checkout?${base}&fulfillmentType=STORE_PICKUP&paymentMethod=PAY_AT_STORE&pickupStoreId=1`;
  }
  return `/checkout?${base}&fulfillmentType=DELIVERY&paymentMethod=ONLINE&shippingAddress=${ADDRESS}`;
}

async function main() {
  fs.mkdirSync(ROOT, { recursive: true });
  const page = await CdpPage.open('about:blank');
  const failedResponses = [];
  PROBE_FAILED(page, failedResponses);

  const manifest = { generatedAt: new Date().toISOString(), app: APP, roles: {}, skipped: [], failedResponses, runtimeErrors: page.errors };

  try {
    // ---------------------------------------------------------------- GUEST (không đăng nhập)
    const guestResolvedProduct = await resolveHref(page, '/products',
      `[...document.querySelectorAll('a[href^="/products/"]')].map(a => a.getAttribute('href')).find(h => /^\\/products\\/\\d+$/.test(h))`);
    if (guestResolvedProduct) console.log(`  [resolve] guest product-detail -> ${guestResolvedProduct}`);
    else { manifest.skipped.push('guest/product-detail (no product link on /products)'); console.log('  [skip] guest product-detail'); }

    const guestRoutes = [
      { seq: '010', name: 'home', dir: '010-trang-chu', path: '/' },
      { seq: '020', name: 'login', dir: '020-dang-nhap', path: '/login' },
      { seq: '030', name: 'login-error', dir: '030-dang-nhap-bi-loi', path: '/login?error' },
      { seq: '040', name: 'login-logout', dir: '040-dang-xuat', path: '/login?logout' },
      { seq: '050', name: 'register', dir: '050-dang-ky', path: '/register' },
      { seq: '060', name: 'products', dir: '060-san-pham', path: '/products' },
      { seq: '070', name: 'products-filtered', dir: '070-san-pham-loc', path: '/products?q=serum' },
      { seq: '080', name: 'product-detail', dir: '080-chi-tiet-san-pham', path: guestResolvedProduct },
      { seq: '090', name: 'stores', dir: '090-he-thong-cua-hang', path: '/stores' },
      { seq: '100', name: 'stores-filtered', dir: '100-cua-hang-tinh-thanh', path: '/stores?provinceCity=%C4%90%C3%A0%20N%E1%BA%B5ng' },
      { seq: '110', name: 'image-credits', dir: '110-ghi-cong-hinh-anh', path: '/image-credits' },
      { seq: '120', name: 'error-404', dir: '120-loi-404', path: '/products/999999' },
    ].filter(r => r.path);

    await page.clearSession();
    const guestFiles = [];
    for (const entry of guestRoutes) {
      console.log(`  guest ${entry.name} ${entry.path}`);
      try { guestFiles.push(...await captureRoute(page, 'guest', entry)); }
      catch (e) { manifest.skipped.push(`guest/${entry.name}: ${e.message}`); console.log(`    [skip] ${e.message}`); }
    }
    manifest.roles.guest = { label: 'Khách (chưa đăng nhập)', pages: guestRoutes.length, files: guestFiles };

    // ---------------------------------------------------------------- CUSTOMER (khách hàng)
    await page.clearSession();
    await page.login(CUSTOMER);
    await page.navigate('/cart');
    await sleep(400);
    let cartIdsBefore = await readCartIds(page);
    let addedCartItemId = null;

    const customerRoutes = [
      { seq: '010', name: 'home', dir: '010-trang-chu', path: '/' },
      { seq: '020', name: 'account', dir: '020-tai-khoan', path: '/account' },
      { seq: '030', name: 'products', dir: '030-san-pham', path: '/products' },
      { seq: '040', name: 'product-detail', dir: '040-chi-tiet-san-pham', path: guestResolvedProduct },
      { seq: '050', name: 'cart', dir: '050-gio-hang', path: '/cart' },
      { seq: '060', name: 'checkout-delivery', dir: '060-dat-hang-giao-tan-noi', path: null },
      { seq: '070', name: 'checkout-pickup', dir: '070-dat-hang-nhan-tai-cua-hang', path: null },
      { seq: '080', name: 'orders', dir: '080-don-hang-cua-toi', path: '/orders' },
      { seq: '090', name: 'order-detail', dir: '090-chi-tiet-don-hang', path: null },
      { seq: '100', name: 'order-payments', dir: '100-thanh-toan-don-hang', path: null },
      { seq: '110', name: 'error-404', dir: '110-loi-404', path: '/orders/999999' },
    ];

    // Resolve order detail + payments from the real customer data.
    const orderId = await resolveHref(page, '/orders',
      `[...document.querySelectorAll('a[href^="/orders/"]')].map(a => a.getAttribute('href')).find(h => /^\\/orders\\/\\d+$/.test(h))`);
    if (orderId) {
      customerRoutes.find(r => r.name === 'order-detail').path = orderId;
      customerRoutes.find(r => r.name === 'order-payments').path = `${orderId}/payments`;
      console.log(`  [resolve] customer order-detail -> ${orderId}`);
    } else manifest.skipped.push('customer/order-detail + order-payments (no order link on /orders)');

    // Guarantee a non-empty cart for cart/checkout captures (adds ONE item, removed at the end).
    if (cartIdsBefore.length === 0) {
      console.log('  cart empty -> adding one product (restored afterwards)');
      await page.navigate(guestResolvedProduct.startsWith('http') ? guestResolvedProduct : (guestResolvedProduct ?? '/products/1'));
      await sleep(350);
      try {
        await page.fill('#add-quantity', '1');
        await page.clickJsAndWait(`(() => {
          const form = [...document.querySelectorAll('form')].find(f => f.getAttribute('action') === '/cart/items');
          if (!form) throw new Error('add-to-cart form not found');
          form.querySelector('button[type="submit"]').click();
          return true;
        })()`);
        await page.waitFor("location.pathname === '/cart'", 'redirect to cart');
        await sleep(350);
        const idsAfter = await readCartIds(page);
        addedCartItemId = idsAfter.find(id => !cartIdsBefore.includes(id)) ?? idsAfter[0] ?? null;
        console.log(`  added cart item id=${addedCartItemId}`);
      } catch (e) {
        manifest.skipped.push(`customer cart add failed: ${e.message}`);
        console.log(`    [skip] ${e.message}`);
      }
    } else console.log(`  cart already has ${cartIdsBefore.length} item(s), no mutation`);

    // Use the cart ids read on the real cart page above; a re-read here would
    // evaluate against whatever page the order-detail resolution left open.
    const cartIdsNow = cartIdsBefore;
    if (cartIdsNow.length > 0) {
      customerRoutes.find(r => r.name === 'checkout-delivery').path = checkoutUrl(cartIdsNow, 'delivery');
      customerRoutes.find(r => r.name === 'checkout-pickup').path = checkoutUrl(cartIdsNow, 'pickup');
    } else {
      manifest.skipped.push('customer/checkout-delivery + checkout-pickup (cart empty)');
    }

    const customerFiles = [];
    for (const entry of customerRoutes) {
      if (!entry.path) continue;
      console.log(`  customer ${entry.name} ${entry.path}`);
      try { customerFiles.push(...await captureRoute(page, 'customer', entry)); }
      catch (e) { manifest.skipped.push(`customer/${entry.name}: ${e.message}`); console.log(`    [skip] ${e.message}`); }
    }
    manifest.roles.customer = { label: 'Khách hàng (khachhang1@example.com)', pages: customerRoutes.filter(r => r.path).length, files: customerFiles };

    // Restore the cart to its original state when we had to add an item.
    if (addedCartItemId) {
      try {
        await page.navigate('/cart');
        await page.clickJsAndWait(`(() => {
          const btn = document.querySelector('form[action="/cart/items/${addedCartItemId}/delete"] button');
          if (!btn) throw new Error('delete form missing');
          btn.click();
          return true;
        })()`);
        await page.waitFor("location.search.includes('success=removed')", 'cart item removed');
        console.log(`  restored: removed cart item id=${addedCartItemId}`);
      } catch (e) {
        manifest.skipped.push(`customer cart restore failed for id=${addedCartItemId}: ${e.message}`);
      }
    }

    // ---------------------------------------------------------------- STAFF (nhân viên chi nhánh)
    await page.clearSession();
    await page.login(STAFF);

    const fulfillmentId = await resolveHref(page, '/staff/fulfillments',
      `(() => { const n = document.querySelector('[data-fulfillment-id]'); return n ? '/staff/fulfillments/' + n.dataset.fulfillmentId : null; })()`);
    const staffOrderId = await resolveHref(page, '/staff/orders',
      `(() => { const n = document.querySelector('[data-order-id]'); return n ? '/staff/orders/' + n.dataset.orderId : null; })()`);
    const staffReviewId = await resolveHref(page, '/staff/reviews',
      `[...document.querySelectorAll('a[href^="/staff/reviews/"]')].map(a => a.getAttribute('href')).find(h => /^\\/staff\\/reviews\\/\\d+$/.test(h))`);

    const staffRoutes = [
      { seq: '010', name: 'dashboard', dir: '010-tong-quan', path: '/staff' },
      { seq: '020', name: 'queue', dir: '020-don-can-xu-ly', path: '/staff/fulfillments' },
      { seq: '030', name: 'queue-filtered', dir: '030-loc-theo-khach-hang', path: '/staff/fulfillments?q=Kh%C3%A1nh' },
      { seq: '040', name: 'queue-delivery', dir: '040-giao-tan-noi', path: '/staff/fulfillments/delivery' },
      { seq: '050', name: 'queue-pickup', dir: '050-nhan-tai-cua-hang', path: '/staff/fulfillments/pickup' },
      { seq: '060', name: 'fulfillment-detail', dir: '060-chi-tiet-phan-giao', path: fulfillmentId },
      { seq: '070', name: 'staff-orders', dir: '070-don-hang-tong-hop', path: '/staff/orders' },
      { seq: '080', name: 'staff-orders-delivery', dir: '080-don-hang-giao-tan-noi', path: '/staff/orders/delivery' },
      { seq: '090', name: 'staff-orders-pickup', dir: '090-don-hang-nhan-tai-cua-hang', path: '/staff/orders/pickup' },
      { seq: '100', name: 'staff-order-detail', dir: '100-chi-tiet-don-hang', path: staffOrderId },
      { seq: '110', name: 'reviews', dir: '110-danh-gia', path: '/staff/reviews' },
      { seq: '120', name: 'review-detail', dir: '120-chi-tiet-danh-gia', path: staffReviewId },
      { seq: '130', name: 'stock', dir: '130-ton-kho', path: '/staff/stock' },
      { seq: '140', name: 'stock-filtered', dir: '140-ton-kho-loc', path: '/staff/stock?q=Purify' },
      { seq: '150', name: 'stock-adjust-page', dir: '150-dieu-chinh-ton', path: '/staff/stock/1/adjust' },
      { seq: '160', name: 'inventory-history', dir: '160-lich-su-ton-kho', path: '/staff/inventory-history' },
      { seq: '170', name: 'inventory-history-select', dir: '170-lich-su-ton-kho-select', path: '/staff/inventory-history?view=select' },
      { seq: '180', name: 'inventory-history-product', dir: '180-lich-su-theo-san-pham', path: '/staff/inventory-history/1' },
      { seq: '190', name: 'error-403', dir: '190-loi-403', path: '/admin' },
    ].filter(r => r.path);

    const staffExtras = [
      { seq: '200', name: 'stock-modal-open', dir: '200-ton-kho-mo-cua-so-dieu-chinh', path: '/staff/stock',
        openExpr: "[...document.querySelectorAll('button[data-bs-toggle=modal]')].find(b => (b.getAttribute('data-bs-target')||'').startsWith('#adjust-modal-'))?.click(); true",
        openWait: "!!document.querySelector('.modal.show[id^=adjust-modal-]')",
        closeExpr: "document.querySelector('.modal.show[id^=adjust-modal-] [data-bs-dismiss=modal]')?.click(); true",
        closedWait: "!document.querySelector('.modal.show[id^=adjust-modal-]')" },
    ];

    [fulfillmentId, staffOrderId, staffReviewId].forEach((v, i) => {
      if (!v) manifest.skipped.push(['staff/fulfillment-detail', 'staff/staff-order-detail', 'staff/review-detail'][i]);
      else console.log(`  [resolve] ${['fulfillment', 'order', 'review'][i]} -> ${v}`);
    });

    const staffFiles = [];
    for (const entry of staffRoutes) {
      console.log(`  staff ${entry.name} ${entry.path}`);
      try { staffFiles.push(...await captureRoute(page, 'staff', entry)); }
      catch (e) { manifest.skipped.push(`staff/${entry.name}: ${e.message}`); console.log(`    [skip] ${e.message}`); }
    }
    for (const entry of staffExtras) {
      console.log(`  staff ${entry.name} (modal, desktop)`);
      try { staffFiles.push(...await captureModal(page, 'staff', entry)); }
      catch (e) { manifest.skipped.push(`staff/${entry.name}: ${e.message}`); console.log(`    [skip] ${e.message}`); }
    }
    manifest.roles.staff = { label: 'Nhân viên (staff.thuduc@oneshop.vn)', pages: staffRoutes.length + staffExtras.length, files: staffFiles };

    // ---------------------------------------------------------------- ADMIN (quản trị toàn chuỗi)
    await page.clearSession();
    await page.login(ADMIN);

    const adminOrderId = await resolveHref(page, '/admin/orders',
      `[...document.querySelectorAll('a[href]')].map(a => a.getAttribute('href')).find(h => /^\\/admin\\/orders\\/\\d+$/.test(h))`);
    const adminPaymentId = await resolveHref(page, '/admin/payments',
      `[...document.querySelectorAll('a[href]')].map(a => a.getAttribute('href')).find(h => /^\\/admin\\/payments\\/\\d+$/.test(h))`);
    const adminPaths = {};
    const editSources = [
      ['store-product-edit', 'storeProductIdEdit', '/admin/store-products'],
      ['product-edit', 'productEdit', '/admin/products'],
      ['category-edit', 'categoryEdit', '/admin/categories'],
      ['brand-edit', 'brandEdit', '/admin/brands'],
      ['user-edit', 'userEdit', '/admin/users'],
      ['store-edit', 'storeEdit', '/admin/stores'],
    ];
    const editExpr = (base) =>
      `[...document.querySelectorAll('a[href]')].map(a => a.getAttribute('href')).find(h => new RegExp('^' + ${JSON.stringify(base)} + '/\\\\d+/edit$').test(h))`;

    for (const [name, key, src] of editSources) {
      const value = await resolveHref(page, src, editExpr(src));
      adminPaths[key] = value;
      if (value) console.log(`  [resolve] ${name} -> ${value}`);
      else manifest.skipped.push(`admin/${name} (no ${src}/{id}/edit link on ${src})`);
    }

    const adminRoutes = [
      { seq: '010', name: 'dashboard', dir: '010-tong-quan', path: '/admin' },
      { seq: '020', name: 'orders', dir: '020-don-hang', path: '/admin/orders' },
      { seq: '030', name: 'order-detail', dir: '030-chi-tiet-don-hang', path: adminOrderId },
      { seq: '040', name: 'payments', dir: '040-thanh-toan', path: '/admin/payments' },
      { seq: '050', name: 'payment-detail', dir: '050-chi-tiet-thanh-toan', path: adminPaymentId },
      { seq: '060', name: 'inventory', dir: '060-ton-kho', path: '/admin/inventory' },
      { seq: '070', name: 'store-products', dir: '070-san-pham-theo-chi-nhanh', path: '/admin/store-products' },
      { seq: '080', name: 'store-products-new', dir: '080-them-san-pham-chi-nhanh', path: '/admin/store-products/new' },
      { seq: '090', name: 'store-product-edit', dir: '090-sua-san-pham-chi-nhanh', path: adminPaths.storeProductIdEdit },
      { seq: '100', name: 'products', dir: '100-san-pham', path: '/admin/products' },
      { seq: '110', name: 'products-new', dir: '110-them-san-pham', path: '/admin/products/new' },
      { seq: '120', name: 'product-edit', dir: '120-sua-san-pham', path: adminPaths.productEdit },
      { seq: '130', name: 'categories', dir: '130-danh-muc', path: '/admin/categories' },
      { seq: '140', name: 'category-edit', dir: '140-sua-danh-muc', path: adminPaths.categoryEdit },
      { seq: '150', name: 'brands', dir: '150-thuong-hieu', path: '/admin/brands' },
      { seq: '160', name: 'brand-edit', dir: '160-sua-thuong-hieu', path: adminPaths.brandEdit },
      { seq: '170', name: 'users', dir: '170-nguoi-dung', path: '/admin/users' },
      { seq: '180', name: 'users-new', dir: '180-them-nguoi-dung', path: '/admin/users/new' },
      { seq: '190', name: 'user-edit', dir: '190-sua-nguoi-dung', path: adminPaths.userEdit },
      { seq: '200', name: 'stores', dir: '200-chi-nhanh', path: '/admin/stores' },
      { seq: '210', name: 'stores-new', dir: '210-them-chi-nhanh', path: '/admin/stores/new' },
      { seq: '220', name: 'store-edit', dir: '220-sua-chi-nhanh', path: adminPaths.storeEdit },
      { seq: '230', name: 'staff-assignments', dir: '230-phan-cong-nhan-vien', path: '/admin/staff-assignments' },
      { seq: '240', name: 'reviews', dir: '240-danh-gia', path: '/admin/reviews' },
      { seq: '250', name: 'error-403', dir: '250-loi-403', path: '/staff' },
    ].filter(r => r.path);

    const adminExtras = [
      { seq: '260', name: 'reviews-reason-modal', dir: '260-danh-gia-mo-cua-so-ly-do', path: '/admin/reviews',
        openExpr: "document.querySelector('tr[data-review-id] form[data-confirm-reason] button')?.click(); true",
        openWait: "document.querySelector('#osReasonModal').classList.contains('show')",
        closeExpr: "document.querySelector('#osReasonModal [data-bs-dismiss=modal]')?.click(); true",
        closedWait: "!document.querySelector('#osReasonModal.show')" },
    ];

    const adminFiles = [];
    for (const entry of adminRoutes) {
      console.log(`  admin ${entry.name} ${entry.path}`);
      try { adminFiles.push(...await captureRoute(page, 'admin', entry)); }
      catch (e) { manifest.skipped.push(`admin/${entry.name}: ${e.message}`); console.log(`    [skip] ${e.message}`); }
    }
    for (const entry of adminExtras) {
      console.log(`  admin ${entry.name} (modal, desktop)`);
      try { adminFiles.push(...await captureModal(page, 'admin', entry)); }
      catch (e) { manifest.skipped.push(`admin/${entry.name}: ${e.message}`); console.log(`    [skip] ${e.message}`); }
    }
    manifest.roles.admin = { label: 'Quản trị viên (admin@oneshop.vn)', pages: adminRoutes.length + adminExtras.length, files: adminFiles };

    manifest.runtimeErrors = page.errors;
  } finally {
    manifest.viewports = VIEWPORTS.map(v => ({ label: v.label, px: `${v.w}x${v.h}` }));
    const flat = Object.values(manifest.roles).flatMap(r => r.files);
    manifest.summary = {
      roles: Object.keys(manifest.roles).length,
      pages: Object.values(manifest.roles).reduce((n, r) => n + r.pages, 0),
      files: flat.length,
      skipped: manifest.skipped.length,
      runtimeErrors: manifest.runtimeErrors.length,
      failedResponses: manifest.failedResponses.length,
    };
    fs.writeFileSync(path.join(ROOT, 'manifest.json'), JSON.stringify(manifest, null, 2));
    fs.writeFileSync(path.join(ROOT, 'README.md'),
`# Ảnh màn hình OneShop (bộ chụp đầy đủ)

Sinh bởi \`scripts/capture_full_ui.mjs\` trên server đang chạy (\`${APP}\`), chỉ GET (trừ thao tác
thêm 1 món vào giỏ khi giỏ trống của khách hàng demo — được xóa lại ngay sau khi chụp).

## Role
- \`guest/\`     — chưa đăng nhập: trang chủ, đăng nhập (+ trạng thái lỗi, vừa đăng xuất), đăng ký, sản phẩm,
  chi tiết sản phẩm, hệ thống cửa hàng, ghi công hình ảnh, lỗi 404.
- \`customer/\`  — khách hàng: tài khoản, sản phẩm, giỏ hàng, đặt hàng (giao tận nơi + nhận tại cửa hàng),
  đơn của tôi, chi tiết đơn, trang thanh toán, lỗi 404.
- \`staff/\`     — nhân viên: tổng quan, hàng đợi phần giao (+ lọc, 2 tab, đơn tổng hợp), chi tiết, đánh giá,
  ton kho (+ lọc, trang điều chỉnh, modal mở), lịch sử tồn kho (3 dạng), lỗi 403.
- \`admin/\`     — quản trị: 12 trang chính, form thêm/sửa (sản phẩm, SKU theo chi nhánh, danh mục,
  thương hiệu, người dùng, chi nhánh), chi tiết đơn/thanh toán, đánh giá (+ modal lý do), lỗi 403.

## Tỉ lệ chuẩn từng thiết bị (viewport thật)
driver = desktop 1440×900 · laptop 1366×768 · tablet 768×1024 · phone 390×844

## Trang dài
Chụp thành nhiều phần \`-p01, -p02, ...\` theo từng lần cuộn bằng chiều cao viewport (không thẳng lại,
mỗi phần hiển thị chính xác phần đó + thanh chủ đề dính).

## Tên file
\`<role>/<tự số>-<trang>/<tự số>-<trang>-<RỘTONGxCAO>-p<phan>.png\`

Danh sách chi tiết từng file (URL, tiêu đề, tỷ lệ, scrollY, HTTP status) nằm trong \`manifest.json\`.
Đếm nhanh: \`manifest.summary\`.
`);

    try { await page.close(); } catch { /* best effort */ }
  }

  console.log('SUMMARY ' + JSON.stringify(manifest.summary));
  console.log('done');
}

main().catch((e) => { console.error(e); process.exit(1); });
