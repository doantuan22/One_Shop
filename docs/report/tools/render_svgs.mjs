// Rasterise SVG files to PNG (2x) with the already-running headless Chrome.
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { CdpPage, sleep } from './lib/cdp.mjs';
const files = process.argv.slice(2);
const page = await CdpPage.open('about:blank');
for (const f of files) {
  const svg = fs.readFileSync(f, 'utf8');
  const m = svg.match(/width="(\d+(?:\.\d+)?)" height="(\d+(?:\.\d+)?)"/);
  const w = Math.ceil(+m[1]), h = Math.ceil(+m[2]);
  await page.send('Emulation.setDeviceMetricsOverride', { width: w, height: h, deviceScaleFactor: 2, mobile: false });
  const loaded = page.nextEvent('Page.loadEventFired', 20000);
  await page.send('Page.navigate', { url: pathToFileURL(path.resolve(f)).href });
  await loaded;
  await sleep(150);
  const r = await page.send('Page.captureScreenshot', { format: 'png', clip: { x: 0, y: 0, width: w, height: h, scale: 1 } });
  fs.writeFileSync(f.replace(/\.svg$/, '.png'), Buffer.from(r.data, 'base64'));
  console.log('png', f, w, h);
}
await page.close();
