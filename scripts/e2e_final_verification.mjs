import fs from 'node:fs';
import path from 'node:path';
import { APP, CdpPage, DEMO_PASSWORD, sleep } from './lib/cdp.mjs';

const OUT = path.resolve('screenshots', 'e2e-final-20261020');
fs.mkdirSync(OUT, { recursive: true });

const CUSTOMER = 'khachhang1@example.com';
const STAFF_STORE_1 = 'staff.thuduc@oneshop.vn';
const STAFF_STORE_2 = 'staff.govap@oneshop.vn';
const ADMIN = 'admin@oneshop.vn';

const PRODUCT_STORE_1 = 2; // CeraVe 177ml: stocked only by Store 1
const PRODUCT_STORE_2 = 9; // Bioderma 250ml: stocked only by Store 2

const evidence = {
  startedAt: new Date().toISOString(),
  app: APP,
  database: 'oneshop_v3_e2e_20261020_test',
  steps: [],
  orders: {},
  refund: {},
  pickup: {},
  codSplit: {},
  screenshots: [],
  browserErrors: [],
};

function assert(condition, message) {
  if (!condition) throw new Error(`ASSERTION FAILED: ${message}`);
}

function step(name, details = {}) {
  const row = { name, at: new Date().toISOString(), ...details };
  evidence.steps.push(row);
  console.log(`PASS ${name}`, details.note ? `- ${details.note}` : '');
  return row;
}

async function shot(page, name) {
  const file = path.join(OUT, `${name}.png`);
  await page.setViewport(1440, 900);
  await page.screenshot(file);
  evidence.screenshots.push(path.relative(process.cwd(), file));
  return file;
}

async function ordersOnPage(page) {
  return page.eval(`Array.from(document.querySelectorAll('.os-order')).map(card => ({
    id: Number(card.dataset.orderId),
    storeId: card.dataset.storeId ? Number(card.dataset.storeId) : null,
    status: card.dataset.status,
    fulfillmentType: card.dataset.fulfillment,
    paymentMethod: card.dataset.payment,
    total: card.querySelector('.os-order-total')?.innerText.trim() || '',
    fee: card.querySelector('.os-order-fee')?.innerText.trim() || '',
    fulfillments: Array.from(card.querySelectorAll('.os-fulfillment')).map(part => ({
      id: Number(part.dataset.fulfillmentId),
      storeId: Number(part.dataset.storeId),
      text: part.innerText.replace(/\\s+/g, ' ').trim()
    }))
  }))`);
}

async function addToCart(page, productId, quantity = 1) {
  await page.navigate(`/products/${productId}`);
  await page.waitFor(`Boolean(document.querySelector('#add-to-cart form'))`, `add-to-cart form for product ${productId}`);
  await page.fill('#add-quantity', String(quantity));
  await page.clickAndWait('#add-to-cart form button[type="submit"]');
  const added = await page.text('body');
  assert(added.includes('Đã thêm sản phẩm') || added.includes('giỏ hàng'), `product ${productId} was added to the cart`);
}

async function clearCart(page) {
  await page.navigate('/cart');
  for (let attempt = 0; attempt < 20; attempt += 1) {
    const count = await page.eval(`document.querySelectorAll('.os-cart-item').length`);
    if (count === 0) return;
    await page.clickAndWait('.os-cart-item form[action$="/delete"] button[type="submit"]');
    await page.waitFor(`document.querySelectorAll('.os-cart-item').length < ${count}`, 'cart line removed');
  }
  throw new Error('Could not empty the seeded cart');
}

async function openCartAndCheckout(page, expectedItems) {
  await page.navigate('/cart');
  await page.waitFor(`document.querySelectorAll('.os-cart-item').length >= ${expectedItems}`, 'cart rows');
  const count = await page.eval(`document.querySelectorAll('.os-cart-item').length`);
  assert(count >= expectedItems, `cart contains ${expectedItems} selected line(s), found ${count}`);
  await page.eval(`(() => {
    const all = document.getElementById('select-all-items');
    if (all && !all.checked) all.click();
    return true;
  })()`);
  await page.waitFor(`document.getElementById('checkout-submit') && !document.getElementById('checkout-submit').disabled`, 'checkout enabled');
  await page.clickAndWait('#checkout-submit');
  await page.waitFor(`location.pathname === '/checkout'`, 'checkout page');
}

async function chooseWithReload(page, selector) {
  const checked = await page.eval(`Boolean(document.querySelector(${JSON.stringify(selector)})?.checked)`);
  if (checked) return false;
  await page.clickAndWait(selector);
  return true;
}

async function placeOrder(page) {
  await page.waitFor(`Boolean(document.getElementById('place-order'))`, 'place order button');
  const enabled = await page.eval(`!document.getElementById('place-order').disabled`);
  assert(enabled, 'place-order button is enabled');
  await page.clickAndWait('#place-order');
  await page.waitFor(`location.pathname.startsWith('/checkout/') && new URLSearchParams(location.search).get('success') === 'placed'`, 'checkout result');
}

async function staffAction(page, fulfillmentId, action, fields = {}) {
  await page.navigate(`/staff/fulfillments/${fulfillmentId}`);
  assert(page.lastDocumentStatus === 200, `staff can open fulfillment ${fulfillmentId}`);
  await page.waitFor(`Boolean(document.querySelector('form[data-action="${action}"]'))`, `staff action ${action}`);
  for (const [selector, value] of Object.entries(fields)) await page.fill(selector, value);
  await page.clickAndWait(`form[data-action="${action}"] button[type="submit"]`, 30000);
}

async function expectForbidden(page, url, label) {
  await page.navigate(url);
  const status = page.lastDocumentStatus;
  const body = await page.text('body');
  assert(status === 403, `${label} returns HTTP 403 (actual ${status})`);
  assert(body.includes('403'), `${label} renders the 403 page`);
}

async function loginFresh(page, email) {
  await page.clearSession();
  await page.login(email);
  assert(!(await page.pathname()).startsWith('/login'), `${email} is authenticated`);
}

const page = await CdpPage.open(`${APP}/login`);
await page.setViewport(1440, 900);

try {
  // ------------------------------------------------------------------ A. ONLINE multi-Store order + mock payment
  await loginFresh(page, CUSTOMER);
  await clearCart(page);
  await addToCart(page, PRODUCT_STORE_1, 1);
  await addToCart(page, PRODUCT_STORE_2, 1);
  await page.navigate('/cart');
  await shot(page, '01-customer-cart-multi-store');
  await openCartAndCheckout(page, 2);
  await page.waitFor(`Boolean(document.querySelector('input[name="paymentMethod"][value="ONLINE"]'))`, 'ONLINE option');
  await chooseWithReload(page, 'input[name="paymentMethod"][value="ONLINE"]');
  await page.waitFor(`document.querySelector('input[name="paymentMethod"][value="ONLINE"]')?.checked`, 'ONLINE selected');
  assert(await page.eval(`!document.querySelector('input[name="fulfillmentType"][value="DELIVERY"]')?.disabled`), 'delivery is available');
  await page.fill('#receiverName', 'Phạm Thu Hà');
  await page.fill('#receiverPhone', '0912345678');
  await page.fill('#shippingAddress', '12 Võ Văn Ngân, Thủ Đức, TP. Hồ Chí Minh');
  const preview = await page.eval(`({
    orderCount: document.getElementById('order-count')?.innerText.trim(),
    fulfillmentCount: document.getElementById('fulfillment-count')?.innerText.trim(),
    planStores: Array.from(document.querySelectorAll('#checkout-plan [data-store-id]')).map(el => Number(el.dataset.storeId)),
    planSignature: Boolean(document.querySelector('input[name="planSignature"]')?.value)
  })`);
  assert(preview.orderCount === '1', 'ONLINE multi-Store checkout produces one Order');
  assert(preview.fulfillmentCount === '2', 'ONLINE multi-Store checkout has two fulfillments');
  assert(preview.planSignature, 'checkout plan signature is present');
  await shot(page, '02-customer-checkout-online-multi-store');
  await placeOrder(page);

  const onlineOrders = await ordersOnPage(page);
  assert(onlineOrders.length === 1, 'ONLINE checkout created exactly one Order');
  const online = onlineOrders[0];
  assert(online.fulfillments.length === 2, 'the ONLINE Order has two Store fulfillments');
  assert(online.fulfillments.some((p) => p.storeId === 1), 'Store 1 supplies one part');
  assert(online.fulfillments.some((p) => p.storeId === 2), 'Store 2 supplies one part');
  assert(online.paymentMethod === 'ONLINE', 'the Order uses ONLINE payment');
  assert(online.status === 'PENDING_PAYMENT', 'unpaid ONLINE Order starts in PENDING_PAYMENT');
  evidence.orders.online = online;
  await shot(page, '03-customer-order-created-online');

  await page.clickAndWait('.os-order .btn-order-online');
  await page.waitFor(`location.pathname === '/orders/${online.id}/payments'`, 'payment page');
  if (await page.eval(`Boolean(document.getElementById('start-payment'))`)) {
    await page.clickAndWait('#start-payment');
  }
  await page.waitFor(`Boolean(document.querySelector('.os-payment-attempt'))`, 'payment attempt');
  await page.clickJsAndWait(`(() => {
    const input = document.querySelector('.os-payment-attempt input[value="SUCCESS"]');
    if (!input) throw new Error('No SUCCESS simulation');
    input.closest('form').querySelector('button').click();
    return true;
  })()`);
  await page.waitFor(`new URLSearchParams(location.search).get('outcome') === 'CONFIRMED'`, 'mock gateway confirmation');
  const paidStatus = await page.eval(`document.querySelector('#payment-order-status')?.dataset.status`);
  const paidOutcome = await page.text('#payment-outcome');
  assert(paidStatus === 'PAID', 'mock payment marks the Order PAID');
  assert(paidOutcome.includes('xác nhận đã thu'), 'the customer sees the gateway confirmation');
  evidence.orders.online.paymentOutcome = paidOutcome.trim();
  await shot(page, '04-customer-payment-success');

  // ------------------------------------------------------------------ B. Staff Store scope + fulfillment
  const partStore1 = online.fulfillments.find((p) => p.storeId === 1);
  const partStore2 = online.fulfillments.find((p) => p.storeId === 2);
  evidence.orders.online.partStore1 = partStore1.id;
  evidence.orders.online.partStore2 = partStore2.id;

  await loginFresh(page, STAFF_STORE_1);
  await page.navigate(`/staff/fulfillments/${partStore1.id}`);
  assert(page.lastDocumentStatus === 200, 'Store 1 Staff opens the Store 1 part');
  const crossDenied1 = await expectForbidden(page, `/staff/fulfillments/${partStore2.id}`, 'Store 1 Staff on the Store 2 part');
  await shot(page, '05-staff-store1-cross-store-403');

  await staffAction(page, partStore1.id, 'prepare');
  await staffAction(page, partStore1.id, 'pack');
  await staffAction(page, partStore1.id, 'ship', { '#trackingCode': 'E2E-TD-20261020' });
  await staffAction(page, partStore1.id, 'complete-delivery');
  const store1Detail = await page.text('body');
  assert(store1Detail.includes('Hoàn tất') || store1Detail.includes('COMPLETED'), 'Store 1 part is completed');
  await shot(page, '06-staff-store1-delivery-completed');

  await loginFresh(page, STAFF_STORE_2);
  await page.navigate(`/staff/fulfillments/${partStore2.id}`);
  assert(page.lastDocumentStatus === 200, 'Store 2 Staff opens the Store 2 part');
  const crossDenied2 = await expectForbidden(page, `/staff/fulfillments/${partStore1.id}`, 'Store 2 Staff on the Store 1 part');
  await staffAction(page, partStore2.id, 'prepare');
  await staffAction(page, partStore2.id, 'pack');
  await staffAction(page, partStore2.id, 'ship', { '#trackingCode': 'E2E-GV-20261020' });
  await staffAction(page, partStore2.id, 'fail-delivery', { '#reason': 'Khách từ chối nhận hàng tại địa chỉ' });
  const failedDetail = await page.text('body');
  assert(failedDetail.includes('Không thành công') || failedDetail.includes('Thất bại') || failedDetail.includes('FAILED'), 'Store 2 part is failed');
  await shot(page, '07-staff-store2-delivery-failed');

  // ------------------------------------------------------------------ C. Admin partial refund
  await loginFresh(page, ADMIN);
  await page.navigate(`/admin/orders/${online.id}`);
  await page.waitFor(`Boolean(document.querySelector('#fulfillment-exception'))`, 'admin exception form');
  const exceptionText = await page.text('#fulfillment-exception');
  assert(exceptionText.includes('xác nhận không thể giao'), 'admin sees the undeliverable decision');
  await shot(page, '08-admin-order-failed-part');
  await page.fill('#undeliverable-form select[name="goods"]', 'RETURNED');
  await page.fill('#undeliverable-form textarea[name="reason"]', 'Hàng đã về kho, xác nhận không giao được');
  await page.clickAndWait('#undeliverable-form button[type="submit"]');
  await page.waitFor(`location.pathname === '/admin/orders/${online.id}'`, 'admin order after refund');
  const exceptionResult = await page.text('#exception-result');
  const refundText = await page.text('#order-refunds');
  assert(refundText.includes('Đã hoàn') || refundText.includes('hoàn'), 'admin Order page shows the simulated refund');
  evidence.refund.adminSummary = refundText.replace(/\s+/g, ' ').trim();
  evidence.refund.exceptionResult = exceptionResult.replace(/\s+/g, ' ').trim();
  await shot(page, '09-admin-partial-refund-ledger');
  await page.navigate(`/admin/payments/${online.id}`);
  const paymentCase = await page.text('body');
  evidence.refund.paymentConsole = paymentCase.replace(/\s+/g, ' ').trim().slice(0, 2000);
  await shot(page, '10-admin-payment-console-refund');

  // ------------------------------------------------------------------ D. Customer sees partial refund + unpaid cancellation
  await loginFresh(page, CUSTOMER);
  await page.navigate(`/orders/${online.id}`);
  await page.waitFor(`Boolean(document.querySelector('#order-refunds'))`, 'customer refund section');
  const customerRefund = await page.text('#order-refunds');
  assert(customerRefund.includes('Đã hoàn'), 'customer sees the refunded amount');
  assert(customerRefund.includes('phí giao') || customerRefund.includes('giữ lại'), 'customer sees the retained shipping fee');
  evidence.refund.customerSummary = customerRefund.replace(/\s+/g, ' ').trim();
  await shot(page, '11-customer-partial-refund');

  await addToCart(page, PRODUCT_STORE_1, 1);
  await openCartAndCheckout(page, 1);
  await chooseWithReload(page, 'input[name="paymentMethod"][value="ONLINE"]');
  await page.waitFor(`document.querySelector('input[name="paymentMethod"][value="ONLINE"]')?.checked`, 'ONLINE selected for cancellation');
  await page.fill('#receiverName', 'Phạm Thu Hà');
  await page.fill('#receiverPhone', '0912345678');
  await page.fill('#shippingAddress', '12 Võ Văn Ngân, Thủ Đức, TP. Hồ Chí Minh');
  await placeOrder(page);
  const cancelOrder = (await ordersOnPage(page))[0];
  assert(cancelOrder.status === 'PENDING_PAYMENT', 'the cancellation Order is unpaid');
  evidence.orders.cancelled = cancelOrder;
  await page.navigate(`/orders/${cancelOrder.id}`);
  await page.waitFor(`Boolean(document.querySelector('form[action$="/cancel"]'))`, 'cancel form');
  await shot(page, '12-customer-unpaid-order-before-cancel');
  await page.clickAndWait('form[action$="/cancel"] button[type="submit"]');
  await page.waitFor(`new URLSearchParams(location.search).has('cancelled')`, 'cancellation redirect');
  const cancelledText = await page.text('body');
  assert(cancelledText.includes('Đơn đã hủy'), 'customer sees the cancelled confirmation');
  await shot(page, '13-customer-unpaid-order-cancelled');

  // ------------------------------------------------------------------ E. Store pickup with code handover
  await addToCart(page, PRODUCT_STORE_1, 1);
  await openCartAndCheckout(page, 1);
  await chooseWithReload(page, 'input[name="fulfillmentType"][value="STORE_PICKUP"]');
  await page.waitFor(`document.querySelector('input[name="fulfillmentType"][value="STORE_PICKUP"]')?.checked`, 'pickup selected');
  await chooseWithReload(page, 'input[name="pickupStoreId"][value="1"]');
  await page.waitFor(`document.querySelector('input[name="pickupStoreId"][value="1"]')?.checked`, 'pickup Store 1 selected');
  await page.fill('#receiverName', 'Phạm Thu Hà');
  await page.fill('#receiverPhone', '0912345678');
  await shot(page, '14-customer-checkout-pickup');
  await placeOrder(page);
  const pickupOrder = (await ordersOnPage(page))[0];
  assert(pickupOrder.fulfillmentType === 'STORE_PICKUP', 'pickup Order uses STORE_PICKUP');
  assert(pickupOrder.paymentMethod === 'PAY_AT_STORE', 'pickup Order pays at the Store');
  assert(pickupOrder.fulfillments.length === 1 && pickupOrder.fulfillments[0].storeId === 1, 'pickup Order has one Store 1 fulfillment');
  evidence.pickup.orderId = pickupOrder.id;
  evidence.pickup.fulfillmentId = pickupOrder.fulfillments[0].id;

  await loginFresh(page, STAFF_STORE_1);
  await staffAction(page, evidence.pickup.fulfillmentId, 'prepare');
  await staffAction(page, evidence.pickup.fulfillmentId, 'ready-for-pickup');
  const readyText = await page.text('body');
  assert(readyText.includes('Sẵn sàng') || readyText.includes('READY_FOR_PICKUP'), 'pickup part is ready');
  assert(readyText.includes('khách giữ mã'), 'staff page confirms the customer holds the code');

  await loginFresh(page, CUSTOMER);
  await page.navigate(`/orders/${pickupOrder.id}`);
  await page.waitFor(`Boolean(document.querySelector('#pickup-code, .os-fulfillment-pickup-code'))`, 'customer pickup code');
  const pickupCode = (await page.eval(`document.querySelector('#pickup-code, .os-fulfillment-pickup-code')?.innerText.trim() || ''`)).trim();
  assert(/^[A-Z0-9]{6,20}$/.test(pickupCode), `pickup code has a valid shape (${pickupCode})`);
  evidence.pickup.code = pickupCode;
  await shot(page, '15-customer-pickup-code');

  await loginFresh(page, STAFF_STORE_1);
  await page.navigate(`/staff/fulfillments/${evidence.pickup.fulfillmentId}`);
  await page.waitFor(`Boolean(document.querySelector('form[data-action="complete-pickup"]'))`, 'pickup handover form');
  await page.fill('#pickupCode', pickupCode);
  await page.clickAndWait('form[data-action="complete-pickup"] button[type="submit"]');
  const handedOver = await page.text('body');
  assert(handedOver.includes('Hoàn tất') || handedOver.includes('COMPLETED'), 'staff completes the pickup with the code');
  await shot(page, '16-staff-pickup-handover-completed');

  await loginFresh(page, CUSTOMER);
  await page.navigate(`/orders/${pickupOrder.id}`);
  const pickupDone = await page.text('body');
  assert(pickupDone.includes('Hoàn tất') || pickupDone.includes('COMPLETED'), 'customer sees the completed pickup Order');
  await shot(page, '17-customer-pickup-completed');

  // ------------------------------------------------------------------ F. COD split into two independent Orders
  await addToCart(page, PRODUCT_STORE_1, 1);
  await addToCart(page, PRODUCT_STORE_2, 1);
  await openCartAndCheckout(page, 2);
  await chooseWithReload(page, 'input[name="paymentMethod"][value="COD"]');
  await page.waitFor(`document.querySelector('input[name="paymentMethod"][value="COD"]')?.checked`, 'COD selected');
  await page.waitFor(`Boolean(document.querySelector('#cod-split'))`, 'COD split consent');
  const splitPlan = await page.eval(`({
    orderCount: document.getElementById('order-count')?.innerText.trim(),
    fulfillmentCount: document.getElementById('fulfillment-count')?.innerText.trim(),
    consentVisible: Boolean(document.getElementById('splitCodConsent')),
    submitDisabledBefore: document.getElementById('place-order')?.disabled
  })`);
  assert(splitPlan.orderCount === '2', 'COD multi-Store preview creates two Orders');
  assert(splitPlan.consentVisible, 'COD split asks for explicit consent');
  assert(splitPlan.submitDisabledBefore === true, 'COD split submit is disabled before consent');
  await page.fill('#receiverName', 'Phạm Thu Hà');
  await page.fill('#receiverPhone', '0912345678');
  await page.fill('#shippingAddress', '12 Võ Văn Ngân, Thủ Đức, TP. Hồ Chí Minh');
  await page.eval(`(() => {
    const consent = document.getElementById('splitCodConsent');
    if (!consent.checked) consent.click();
    return true;
  })()`);
  await page.waitFor(`!document.getElementById('place-order').disabled`, 'COD split submit enabled');
  await shot(page, '18-customer-checkout-cod-split-consent');
  await placeOrder(page);
  const splitOrders = await ordersOnPage(page);
  assert(splitOrders.length === 2, 'COD split created two independent Orders');
  assert(new Set(splitOrders.map((o) => o.id)).size === 2, 'the two COD Orders have different ids');
  assert(splitOrders.every((o) => o.paymentMethod === 'COD'), 'both split Orders use COD');
  assert(splitOrders.every((o) => o.status === 'CONFIRMED'), 'both split Orders are CONFIRMED');
  assert(splitOrders.every((o) => o.fee.includes('30.000')), 'each split Order pays its own 30,000 shipping fee');
  assert(new Set(splitOrders.map((o) => o.storeId)).size === 2, 'the two Orders belong to two different Stores');
  evidence.codSplit.orderIds = splitOrders.map((o) => o.id);
  evidence.codSplit.orders = splitOrders;
  await shot(page, '19-customer-cod-split-two-orders');

  evidence.finishedAt = new Date().toISOString();
  evidence.browserErrors = page.errors.slice();
  evidence.result = 'PASS';
  fs.writeFileSync(path.join(OUT, 'e2e-result.json'), JSON.stringify(evidence, null, 2));
  console.log(`E2E PASS: ${path.join(OUT, 'e2e-result.json')}`);
} catch (error) {
  evidence.finishedAt = new Date().toISOString();
  evidence.result = 'FAIL';
  evidence.error = error.stack || String(error);
  evidence.browserErrors = page.errors.slice();
  try {
    await shot(page, '99-e2e-failure');
  } catch {
    // Ignore a screenshot failure while already failing.
  }
  fs.writeFileSync(path.join(OUT, 'e2e-result.json'), JSON.stringify(evidence, null, 2));
  console.error(error.stack || error);
  process.exitCode = 1;
} finally {
  await page.close();
}
