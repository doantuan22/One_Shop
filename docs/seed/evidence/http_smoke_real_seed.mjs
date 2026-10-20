// Real-product seed smoke test over HTTP on the running app + an independent database (read only).
import { Client, check, noServerError, results } from './lib.mjs';
const guest = new Client('guest');
const find = (h, re) => [...h.matchAll(re)].map(m => m[1]);

// ---------------------------------------------------------------- catalog pages and pagination
const first = await guest.get('/products');
check('catalog page loads', first.status === 200);
const countText = (first.body.match(/id="catalog-count"[^>]*>\s*([^<]+)</) || [])[1] || '';
console.log('   catalog count text:', countText.trim().replace(/\s+/g, ' '));
let ids = new Set(), imgs = new Set(), page = 0;
while (page < 6) {
  const r = await guest.get('/products?page=' + page);
  const here = find(r.body, /href="\/products\/(\d+)"[^>]*class="product-title-link"|class="product-title-link"[^>]*href="\/products\/(\d+)"/g);
  const cards = [...r.body.matchAll(/href="\/products\/(\d+)"/g)].map(m => m[1]);
  cards.forEach(i => ids.add(i));
  [...r.body.matchAll(/src="(https:\/\/res\.cloudinary\.com\/[^"]+)"/g)].forEach(m => imgs.add(m[1]));
  if (!/page-link[^>]*>\s*(Sau|›|»|Tiếp)/i.test(r.body) && page > 0 && cards.length === 0) break;
  page++;
}
check('catalog lists the 26 ACTIVE products (the discontinued one is hidden)', ids.size === 26, `${ids.size}`);
check('every catalog card shows a OneShop Cloudinary product image', [...imgs].filter(u => /\/oneshop\/v3\/products\//.test(u)).length >= 26, `${imgs.size}`);

// ---------------------------------------------------------------- each product page and its image
let okPages = 0, okImages = 0, hidden = 0;
for (let id = 1; id <= 27; id++) {
  const r = await guest.get('/products/' + id);
  if (r.status === 404) { hidden++; continue; }
  if (r.status === 200) okPages++;
  const img = (r.body.match(/id="product-main-image"/) && (r.body.match(/<img[^>]*src="(https:\/\/res\.cloudinary\.com\/[^"]+)"[^>]*id="product-main-image"|<img[^>]*id="product-main-image"[^>]*src="(https:\/\/res\.cloudinary\.com\/[^"]+)"/) || []));
  const url = img[1] || img[2];
  if (url) { const h = await fetch(url, { method: 'HEAD' }); if (h.status === 200 && (h.headers.get('content-type') || '').startsWith('image/')) okImages++; }
  if (!/id="image-credit-line"/.test(r.body) && r.status === 200) check('product ' + id + ' shows the credit line', false);
}
check('26 product pages render (200) and 1 discontinued product is 404', okPages === 26 && hidden === 1, `${okPages} ok, ${hidden} 404`);
check('every product page serves a real image from Cloudinary (HTTP 200, image/*)', okImages === 26, `${okImages}`);

// ---------------------------------------------------------------- search, filters
const cerave = await guest.get('/products?q=cerave');
check('search "cerave" finds the CeraVe products only', (cerave.body.match(/class="product-title-link"/g) || []).length === 4, String((cerave.body.match(/class="product-title-link"/g) || []).length));
const none = await guest.get('/products?q=' + encodeURIComponent('không có sản phẩm này'));
check('search without a match shows the empty message', /Không tìm thấy sản phẩm phù hợp/.test(none.body));
const brandId = (first.body.match(/name="brandId"[\s\S]*?<option value="(\d+)"[^>]*>\s*Maybelline/) || [])[1] || '6';
const maybe = await guest.get('/products?brandId=' + brandId);
check('brand filter returns the Maybelline products', (maybe.body.match(/class="product-title-link"/g) || []).length === 6, String((maybe.body.match(/class="product-title-link"/g) || []).length));
const cat = await guest.get('/products?categoryId=2');
check('category filter "Trang điểm" returns products', (cat.body.match(/class="product-title-link"/g) || []).length >= 6);

// ---------------------------------------------------------------- credits and home
const cr = await guest.get('/image-credits');
check('credits page is public and lists 27 credited images', cr.status === 200 && (cr.body.match(/world\.openbeautyfacts\.org\/product\//g) || []).length >= 27);
const home = await guest.get('/');
check('home loads and links to the credits', home.status === 200 && /href="\/image-credits"/.test(home.body));

// ---------------------------------------------------------------- roles still see their screens with the new data
const login = async e => { const c = new Client(e); await c.login(e); return c; };
const [cust, staff, adm] = await Promise.all(['khachhang1@example.com', 'staff.thuduc@oneshop.vn', 'admin@oneshop.vn'].map(login));
for (const [n, c, ps] of [['customer', cust, ['/orders', '/orders/1', '/orders/2', '/cart']], ['staff', staff, ['/staff', '/staff/fulfillments', '/staff/stock']], ['admin', adm, ['/admin', '/admin/orders', '/admin/products', '/admin/brands', '/admin/categories', '/admin/stores', '/admin/payments']]]) {
  for (const p of ps) { const r = await c.get(p); check(`${n} ${p} loads`, r.status === 200, String(r.status)); }
}
const o7 = await adm.get('/admin/orders/7');
check('Admin order 7 (cancelled, simulated refund) shows the ledger', o7.status === 200 && /id="order-refunds"/.test(o7.body) && /388\.000/.test(o7.body));
const o3 = await cust.get('/orders/1');
check('customer order 1 shows the real product name and the total 524.000 (items 494.000 + 30.000 fee)', /Simple Purifying Gel Wash 150ml/.test(o3.body) && /524\.000/.test(o3.body));

const f = results.filter(z => !z.ok);
console.log(`\nSEED-SMOKE ${results.length - f.length}/${results.length} passed`);
f.forEach(z => console.log(' FAIL:', z.name, '->', z.detail));
