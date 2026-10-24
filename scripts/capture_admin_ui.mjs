import path from 'node:path';
import fs from 'node:fs';
import { APP, CdpPage, sleep } from './lib/cdp.mjs';

// Admin screenshots at the real web ratio (viewport 1440x900, one image per screenful, no reset of the scroll).
// Usage: ONESHOP_APP_URL=http://127.0.0.1:8096 node scripts/capture_admin_ui.mjs <output-dir> [route ...]
// Requires: Chrome headless with --remote-debugging-port=9222 and the app running at ONESHOP_APP_URL (GET only).

const OUT = path.resolve(process.argv[2] || 'screenshots/admin-ui');
const ONLY = process.argv.slice(3);
const ADMIN = 'admin@oneshop.vn';
const W = 1440;
const H = 900;
const MAX_PARTS = 4;

const ROUTES = [
  ['dashboard', '/admin'],
  ['stores', '/admin/stores'],
  ['store-new', '/admin/stores/new'],
  ['categories', '/admin/categories'],
  ['brands', '/admin/brands'],
  ['products', '/admin/products'],
  ['product-new', '/admin/products/new'],
  ['store-products', '/admin/store-products'],
  ['store-product-new', '/admin/store-products/new'],
  ['staff-assignments', '/admin/staff-assignments'],
  ['inventory', '/admin/inventory'],
  ['orders', '/admin/orders'],
  ['order-detail', '@order'],
  ['payments', '/admin/payments'],
  ['returns', '/admin/returns'],
  ['promotions', '/admin/promotions'],
  ['promotion-new', '/admin/promotions/new'],
  ['reviews', '/admin/reviews'],
  ['users', '/admin/users'],
  ['user-new', '/admin/users/new'],
];

async function shoot(page, file) {
  const result = await page.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
  fs.writeFileSync(file, Buffer.from(result.data, 'base64'));
}

async function main() {
  fs.mkdirSync(OUT, { recursive: true });
  const page = await CdpPage.open('about:blank');
  const manifest = [];
  try {
    await page.setViewport(W, H);
    await page.clearSession();
    await page.login(ADMIN);
    let seq = 0;
    for (const [name, route] of ROUTES) {
      seq += 1;
      if (ONLY.length && !ONLY.includes(name)) continue;
      let target = route;
      if (route === '@order') {
        await page.navigate('/admin/orders');
        target = await page.eval(`[...document.querySelectorAll('a[href^="/admin/orders/"]')].map(a => a.getAttribute('href')).find(h => /^\\/admin\\/orders\\/\\d+$/.test(h)) || null`);
        if (!target) { console.log(`  [skip] ${name}`); continue; }
      }
      await page.navigate(target);
      await sleep(450);
      const info = await page.eval(`({ title: document.title, height: document.documentElement.scrollHeight,
        scrollWidth: document.documentElement.scrollWidth, width: document.documentElement.clientWidth })`);
      const positions = [];
      for (let y = 0; y + H < info.height - 2 && positions.length < MAX_PARTS - 1; y += H) positions.push(y);
      if (!positions.length || positions[positions.length - 1] + H < info.height - 2) {
        if (positions.length < MAX_PARTS) positions.push(Math.max(0, info.height - H));
      }
      if (info.height <= H) positions.splice(0, positions.length, 0);
      const files = [];
      let part = 1;
      for (const y of [...new Set(positions)]) {
        await page.eval(`window.scrollTo(0, ${y})`);
        await sleep(220);
        const file = path.join(OUT, `${String(seq).padStart(2, '0')}-${name}-${W}x${H}-p${String(part).padStart(2, '0')}.png`);
        await shoot(page, file);
        files.push(path.basename(file));
        part += 1;
      }
      await page.eval('window.scrollTo(0, 0)');
      manifest.push({ name, route: target, title: info.title, pageHeight: info.height, horizontalOverflow: info.scrollWidth > info.width, files });
      console.log(`  ${name} ${target} -> ${files.length} image(s)${info.scrollWidth > info.width ? ' [horizontal overflow]' : ''}`);
    }
  } finally {
    fs.writeFileSync(path.join(OUT, 'manifest.json'), JSON.stringify({ app: APP, viewport: `${W}x${H}`, pages: manifest }, null, 2));
    await page.close();
  }
}

main().catch((error) => { console.error(error); process.exit(1); });
