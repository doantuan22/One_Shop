import { APP, CdpPage, sleep } from './lib/cdp.mjs';

// Quick GET-only verification of the late-stage UI fixes on the live app.
// 1. /admin/stores renders with "Thêm chi nhánh" + area filter (regression: empty body)
// 2. /admin/categories placeholder keeps the legacy neutral class string
// 3. /admin/reviews badge text for unverified-store reviews
const ADMIN = 'admin@oneshop.vn';

const page = await CdpPage.open(`${APP}/login`);
// Network probe: capture every failing response so a 404 is traceable to its URL.
const failedResponses = [];
page.on('Network.responseReceived', (event) => {
  const status = event.response?.status ?? 0;
  const url = event.response?.url ?? '';
  if (status >= 400 && !url.startsWith('data:')) failedResponses.push(`${status} ${url}`);
});
try {
  await page.setViewport(1440, 1024);
  await page.clearSession();
  await page.login(ADMIN);

  const out = {};

  // --- /admin/stores
  await page.navigate('/admin/stores');
  await sleep(400);
  out.stores = await page.eval(`(() => {
    const text = document.body.innerText;
    return {
      title: document.title,
      hasAddStore: text.includes('Thêm chi nhánh'),
      areaOptions: document.querySelectorAll('#store-filter select[name="area"] option').length,
      rows: document.querySelectorAll('[data-store-id]').length,
      rowSorted: [...document.querySelectorAll('[data-store-id]')].slice(0, 5).map(r => r.dataset.storeId),
      emptyState: text.includes('Không có chi nhánh nào khớp bộ lọc.'),
      layout: document.documentElement.outerHTML.includes('data-layout="admin"'),
      tokens: text.includes('sku not found') ? 'bad' : 'ok',
    };
  })()`);

  // --- /admin/categories
  await page.navigate('/admin/categories');
  await sleep(400);
  out.categories = await page.eval(`(() => {
    const legacy = 'rounded border d-inline-flex align-items-center justify-content-center bg-light text-secondary small';
    const nodes = [...document.querySelectorAll('div')].filter(d => String(d.className).includes('os-thumb-ph'));
    return {
      legacyPlaceholderFound: nodes.some(d => String(d.className).includes(legacy)),
      placeholders: nodes.length,
      thumbs: document.querySelectorAll('.os-thumb').length,
    };
  })()`);

  // --- /admin/reviews
  await page.navigate('/admin/reviews');
  await sleep(400);
  out.reviews = await page.eval(`(() => {
    const text = document.body.innerText;
    return {
      rows: document.querySelectorAll('[data-review-id]').length,
      unverifiedBadge: text.includes('Chưa xác minh nguồn chi nhánh'),
      kpis: document.querySelectorAll('.os-kpi-chip').length,
      filterForm: !!document.querySelector('#review-filter'),
    };
  })()`);

  out.runtimeErrors = page.errors.length;
  out.runtimeErrorsSample = page.errors.slice(0, 10);
  out.failedResponses = failedResponses;

  console.log('VERIFY-FIXED ' + JSON.stringify(out, null, 2));
} finally {
  try { await page.close(); } catch { /* best effort */ }
}
