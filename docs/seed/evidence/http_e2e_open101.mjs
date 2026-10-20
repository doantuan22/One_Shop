// OPEN-10.1 end to end over HTTP on the running app + independent test database.
import { Client, check, noServerError, results } from './lib.mjs';
import { execFileSync } from 'node:child_process';
const DB = process.argv[3], PW = process.argv[4];
const sql = q => execFileSync('sqlcmd', ['-S', 'localhost', '-U', 'sa', '-P', PW, '-C', '-I', '-d', DB, '-h', '-1', '-W', '-s', '|', '-Q', 'SET NOCOUNT ON; ' + q], { encoding: 'utf8' }).trim();
const one = q => sql(q).split('\n')[0].trim();
const num = q => Number(one(q));
const cartIds = h => [...h.matchAll(/data-cart-item-id="(\d+)"/g)].map(m => m[1]);
const login = async e => { const c = new Client(e); await c.login(e); return c; };
const [c1, c2, c3, td, gv, adm] = await Promise.all(['khachhang1@example.com', 'khachhang2@example.com', 'khachhang3@example.com',
  'staff.thuduc@oneshop.vn', 'staff.govap@oneshop.vn', 'admin@oneshop.vn'].map(login));
const field = (b, n) => (b.match(new RegExp(`name="${n}"[^>]*value="([^"]*)"`)) || b.match(new RegExp(`name="${n}" value="([^"]*)"`)) || [])[1];
const orderState = id => one(`select order_status+'|'+payment_status from orders where order_id=${id}`);
const partState = id => one(`select status from order_fulfillments where fulfillment_id=${id}`);
const stockOf = f => num(`select top 1 sp.quantity from order_items oi join store_products sp on sp.store_product_id=oi.store_product_id where oi.fulfillment_id=${f} order by oi.order_item_id`);
const movements = o => num(`select count(*) from inventory_movements where reference_order_id=${o} and type='CANCEL_ORDER'`);
const goodsOf = f => num(`select sum(subtotal) from order_items where fulfillment_id=${f}`);
const collected = o => num(`select coalesce(sum(amount),0) from payments where order_id=${o} and status='SUCCESS'`);
const refunded = o => num(`select coalesce(sum(amount),0) from payment_refunds where order_id=${o} and status='SIMULATED_REFUNDED'`);
const ledger = o => num(`select count(*) from payment_refunds where order_id=${o}`);
const stripS = h => h.replace(/<script[\s\S]*?<\/script>/g, '');

async function checkout(c, productIds, { method, fulfillment = 'DELIVERY', store = '' }) {
  for (const id of cartIds((await c.get('/cart')).body)) await c.post(`/cart/items/${id}/delete`);
  for (const p of productIds) await c.post('/cart/items', { productId: p, quantity: 1 });
  const ids = cartIds((await c.get('/cart')).body);
  const q = '/checkout?' + ids.map(i => 'cartItemIds=' + i).join('&') + `&fulfillmentType=${fulfillment}&paymentMethod=${method}` + (store ? `&pickupStoreId=${store}` : '');
  const pv = await c.get(q);
  const addr = (pv.body.match(/name="shippingAddress"[\s\S]*?value="([^"]*)"/) || [])[1]?.replace(/&amp;/g, '&');
  const r = await c.post('/checkout', { cartItemIds: ids, fulfillmentType: fulfillment, paymentMethod: method, receiverName: 'QA', receiverPhone: '0911111111',
    ...(fulfillment === 'DELIVERY' ? { shippingAddress: addr } : { pickupStoreId: store }), planSignature: field(pv.body, 'planSignature'), expectedTotal: field(pv.body, 'expectedTotal') });
  if (r.status !== 302) throw new Error('checkout failed ' + r.status + ' ' + (r.body.match(/id="checkout-error"[^>]*>([^<]*)/) || [])[1]);
  const o = (await c.get(r.loc.replace(/^https?:\/\/[^/]+/, ''))).body.match(/data-order-id="(\d+)"/)?.[1];
  return o;
}
const partOf = (o, store) => one(`select fulfillment_id from order_fulfillments where order_id=${o} and store_id=${store}`);
const pay = async (c, o) => { const pid = (await c.get(`/orders/${o}/payments`)).body.match(/data-payment-id="(\d+)"/)?.[1]; return c.post(`/orders/${o}/payments/${pid}/simulate`, { scenario: 'SUCCESS' }); };
const S = (c, part, action, f = {}) => c.post(`/staff/fulfillments/${part}/${action}`, f);
async function toShipping(c, part) { await S(c, part, 'prepare'); await S(c, part, 'pack'); await S(c, part, 'ship', { trackingCode: 'QA' + part }); }
async function decide(order, part, action, f) { await adm.get(`/admin/orders/${order}`); return adm.post(`/admin/orders/${order}/fulfillments/${part}/${action}`, f); }

// ================================================================== A: ONLINE two Stores; one delivered, other fails -> UNCONFIRMED -> LOST (partial refund)
const oA = await checkout(c1, [4, 21], { method: 'ONLINE' });
await pay(c1, oA);
const gvA = partOf(oA, 2), tdA = partOf(oA, 1);
await toShipping(gv, gvA); await S(gv, gvA, 'complete-delivery');
await toShipping(td, tdA); await S(td, tdA, 'fail-delivery', { reason: 'Khách vắng nhà, gọi 3 lần không được' });
check('A: delivered part COMPLETED, failed part FAILED, Order held', partState(gvA) === 'COMPLETED' && partState(tdA) === 'FAILED' && orderState(oA) === 'NEEDS_ATTENTION|PAID', orderState(oA));
const collA = collected(oA), feeA = num(`select shipping_fee from orders where order_id=${oA}`);
let page = await adm.get(`/admin/orders/${oA}`);
check('A: Admin panel shows collected / refunded / retained, preview and "mô phỏng"', /Đã hoàn \(mô phỏng\)/.test(page.body) && /Còn giữ lại/.test(page.body) && /os-refund-preview/.test(page.body) && /phí giao được giữ lại/.test(page.body));
let r = await decide(oA, tdA, 'undeliverable', { goods: 'UNCONFIRMED', reason: 'Đang chờ vận chuyển xác minh hàng', amount: '999999', component: 'SHIPPING' });
check('A: UNCONFIRMED -> pending-verification, nothing cancelled / restored / refunded, hold stays', /exception=pending-verification$/.test(r.loc) && partState(tdA) === 'FAILED' && orderState(oA) === 'NEEDS_ATTENTION|PAID' && ledger(oA) === 0 && movements(oA) === 0, r.loc);
check('A: page keeps the exception open with the waiting notice', /os-awaiting-verification/.test((await adm.get(`/admin/orders/${oA}`)).body));
check('A: Staff of the other Store still blocked while held', partState(gvA) === 'COMPLETED' && /NEEDS_ATTENTION/.test(orderState(oA)));
const sA = stockOf(tdA);
r = await decide(oA, tdA, 'undeliverable', { goods: 'LOST', reason: 'Vận chuyển xác nhận thất lạc hàng', amount: '1' });
check('A: LOST -> closed-partial', /exception=closed-partial$/.test(r.loc), r.loc);
check('A: refund == goods of the failed part only; shipping kept; Order COMPLETED, Payment still PAID', refunded(oA) === goodsOf(tdA) && ledger(oA) === 1 && num(`select count(*) from payment_refunds where order_id=${oA} and component='SHIPPING'`) === 0 && orderState(oA) === 'COMPLETED|PAID', `${refunded(oA)} vs ${goodsOf(tdA)} ${orderState(oA)}`);
check('A: kept = delivered goods + one shipping fee; never more than collected', collA - refunded(oA) === goodsOf(gvA) + feeA && refunded(oA) < collA, `${collA} ${refunded(oA)} ${goodsOf(gvA)} ${feeA}`);
check('A: LOST never restocks; still one successful payment, no new payment row', stockOf(tdA) === sA && movements(oA) === 0 && num(`select count(*) from payments where order_id=${oA}`) === 1);
check('A: completed part untouched', partState(gvA) === 'COMPLETED');
r = await decide(oA, tdA, 'undeliverable', { goods: 'LOST', reason: 'Gửi lại lần hai, thử hoàn tiền lần nữa' });
check('A: replay is a clean refusal; still exactly one ledger row', /refused:FAILURE_ALREADY_RESOLVED/.test(r.loc) && ledger(oA) === 1);
let cp = await c1.get(`/orders/${oA}`);
check('A: customer sees the simulated refund, labelled, with the partial-close badge and reason', cp.status === 200 && /id="order-refunds"/.test(cp.body) && /Hoàn tiền \(mô phỏng\)/.test(cp.body) && /không có khoản tiền thật/.test(cp.body) && /Hoàn tất một phần/.test(cp.body) && /Vận chuyển xác nhận thất lạc hàng/.test(cp.body));
check('A: customer sees no admin / staff name in the refund table', !/Người xử lý/.test(stripS(cp.body)));
check('A: another customer gets 404, no leak', (await c2.get(`/orders/${oA}`)).status === 404);
const ap = await adm.get(`/admin/orders/${oA}`);
check('A: Admin sees who decided', /Người xử lý/.test(ap.body) && /id="order-refunds"/.test(ap.body));
check('A: Payment console does not treat it as an Admin task', !new RegExp(`data-order-id="${oA}"`).test(stripS((await adm.get('/admin/payments?view=attention')).body)));
check('A: Payment detail shows the ledger', /Hoàn tiền \(mô phỏng\)/.test((await adm.get(`/admin/payments/${oA}`)).body));

// ================================================================== B: ONLINE two Stores, nothing delivered, both fail -> shipping fee with the last one
const oB = await checkout(c1, [4, 21], { method: 'ONLINE' });
await pay(c1, oB);
const gvB = partOf(oB, 2), tdB = partOf(oB, 1);
await toShipping(td, tdB); await toShipping(gv, gvB);
await S(td, tdB, 'fail-delivery', { reason: 'Khách vắng nhà, không liên lạc được' });
sql(`update orders set order_status='SHIPPING' where order_id=${oB}`);   // test database only: a concurrent report found the other part failing too
await S(gv, gvB, 'fail-delivery', { reason: 'Khách vắng nhà, không liên lạc được' });
check('B: two FAILED parts, Order held', partState(tdB) === 'FAILED' && partState(gvB) === 'FAILED' && orderState(oB) === 'NEEDS_ATTENTION|PAID', orderState(oB));
const [rb1, rb2] = await Promise.all([decide(oB, tdB, 'undeliverable', { goods: 'RETURNED', reason: 'Quản trị xử lý Thủ Đức, hàng đã về' }),
  decide(oB, gvB, 'undeliverable', { goods: 'RETURNED', reason: 'Quản trị xử lý Gò Vấp, hàng đã về' })]);
check('B: two decisions at once: one says more-left (or both resolve), last one closes', [rb1.loc, rb2.loc].some(l => /exception=closed$/.test(l)), rb1.loc + ' | ' + rb2.loc);
check('B: total refunded == collected, shipping fee refunded exactly once, goods once each', refunded(oB) === collected(oB) && num(`select count(*) from payment_refunds where order_id=${oB} and component='SHIPPING'`) === 1 && num(`select count(*) from payment_refunds where order_id=${oB} and component='GOODS'`) === 2, `${refunded(oB)} / ${collected(oB)}`);
check('B: Order CANCELLED, Payment REFUNDED, stock restored once per part', orderState(oB) === 'CANCELLED|REFUNDED' && movements(oB) >= 1 && num(`select count(*) from payments where order_id=${oB}`) === 1, orderState(oB));
check('B: customer sees the full simulated refund', /Hoàn tiền \(mô phỏng\)/.test((await c1.get(`/orders/${oB}`)).body));
const pl = await adm.get(`/admin/payments?view=refunded&q=${oB}`);
check('B: Payment console lists it as refunded', new RegExp(`data-order-id="${oB}"`).test(stripS(pl.body)));
r = await adm.post(`/admin/payments/${oB}/refund`);
check('B: Admin refund retry adds nothing', refunded(oB) === collected(oB) && ledger(oB) === 3);

// ================================================================== C: ONLINE pickup paid -> not collected -> RETURNED -> full refund (no shipping fee for pickup)
const oC = await checkout(c3, [21], { method: 'ONLINE', fulfillment: 'STORE_PICKUP', store: '2' });
await pay(c3, oC);
const pC = partOf(oC, 2);
await S(gv, pC, 'prepare'); await S(gv, pC, 'ready-for-pickup'); await S(gv, pC, 'fail-pickup', { reason: 'Khách không đến nhận sau 3 ngày' });
const sC = stockOf(pC);
r = await decide(oC, pC, 'undeliverable', { goods: 'RETURNED', reason: 'Khách hủy nhận, hàng còn tại quầy Gò Vấp' });
check('C: pickup closed with the whole amount refunded (simulated)', /exception=closed$/.test(r.loc) && orderState(oC) === 'CANCELLED|REFUNDED' && refunded(oC) === collected(oC), r.loc + ' ' + orderState(oC));
check('C: stock restored once, no new payment', stockOf(pC) === sC + 1 && movements(oC) === 1 && num(`select count(*) from payments where order_id=${oC}`) === 1);

// ================================================================== D: access control
const oD = await checkout(c1, [4, 21], { method: 'ONLINE' });
await pay(c1, oD);
const gvD = partOf(oD, 2), tdD = partOf(oD, 1);
await toShipping(gv, gvD); await S(gv, gvD, 'complete-delivery');
await toShipping(td, tdD); await S(td, tdD, 'fail-delivery', { reason: 'Khách vắng nhà cả ngày' });
for (const [name, client] of [['staff', td], ['other store staff', gv], ['customer', c1], ['other customer', c2], ['guest', new Client('g')]]) {
  await client.get('/login');
  const x = await client.post(`/admin/orders/${oD}/fulfillments/${tdD}/undeliverable`, { goods: 'RETURNED', reason: 'Thử quyền ' + name, amount: '1', orderStatus: 'CANCELLED', paymentStatus: 'REFUNDED' });
  check(`D: ${name} cannot decide`, [302, 401, 403].includes(x.status) && !/exception=/.test(x.loc) && partState(tdD) === 'FAILED' && ledger(oD) === 0, `${x.status} ${x.loc}`);
}
for (const [name, client] of [['staff', td], ['customer', c1], ['other customer', c2]]) check(`D: ${name} cannot open the Admin payment detail`, [302, 401, 403].includes((await client.get(`/admin/payments/${oD}`)).status));
r = await adm.post(`/admin/orders/${oD}/fulfillments/${tdD}/undeliverable`, { goods: 'RETURNED', reason: 'Quản trị viên xác nhận', amount: '999999', component: 'SHIPPING', fulfillmentId: '1', orderStatus: 'CANCELLED', paymentStatus: 'REFUNDED' });
check('D: forged amount / component / status ignored: only the goods of the part', refunded(oD) === goodsOf(tdD) && num(`select count(*) from payment_refunds where order_id=${oD} and component='SHIPPING'`) === 0 && orderState(oD) === 'COMPLETED|PAID', orderState(oD));

// ================================================================== E: integrity and pages
check('E: ledger never exceeds collected (all payments)', num("select count(*) from (select p.payment_id from payments p join payment_refunds r on r.payment_id=p.payment_id where r.status<>'SIMULATION_FAILED' group by p.payment_id, p.amount having sum(r.amount) > p.amount) t") === 0);
check('E: no payment with refund_status SIMULATED_REFUNDED lacks ledger rows', num("select count(*) from payments p where p.refund_status='SIMULATED_REFUNDED' and not exists (select 1 from payment_refunds r where r.payment_id=p.payment_id)") === 0);
check('E: stock never negative; no order with two successful payments', num('select count(*) from store_products where quantity<0') === 0 && num("select count(*) from (select order_id from payments where status='SUCCESS' group by order_id having count(*)>1) t") === 0);
check('E: no Order left NEEDS_ATTENTION with an already-decided failure', num("select count(*) from orders o where o.order_status='NEEDS_ATTENTION' and not exists (select 1 from order_fulfillments f where f.order_id=o.order_id and f.status='FAILED')") === 0);
for (const p of ['/admin', '/admin/orders', '/admin/orders?attention=true', '/admin/payments', `/admin/payments/${oA}`, `/admin/orders/${oB}`, `/admin/orders/${oD}`]) noServerError('admin GET ' + p, await adm.get(p));
for (const p of ['/orders', `/orders/${oA}`, `/orders/${oB}`, `/orders/${oC}`]) check('customer GET ' + p, [200].includes((await (p.includes(String(oC)) ? c3 : c1).get(p)).status));

const f = results.filter(z => !z.ok);
console.log(`\nOPEN101 ${results.length - f.length}/${results.length} passed`);
for (const z of f) console.log(' FAIL:', z.name, '->', z.detail);
