// pdfshot.mjs <pdf> <outDir> <page...> : screenshots pages of a PDF through Chrome's built-in PDF viewer (one tab per page).
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { CdpPage, sleep } from './lib/cdp.mjs';
const [pdf, outDir, ...pages] = process.argv.slice(2);
fs.mkdirSync(outDir, { recursive: true });
for (const n of pages) {
  const url = pathToFileURL(path.resolve(pdf)).href + `#page=${n}&zoom=page-fit&toolbar=0&navpanes=0&scrollbar=0`;
  const page = await CdpPage.open(url);
  await page.send('Emulation.setDeviceMetricsOverride', { width: 760, height: 1075, deviceScaleFactor: 1, mobile: false });
  await sleep(2200);
  const r = await page.send('Page.captureScreenshot', { format: 'png' });
  fs.writeFileSync(path.join(outDir, `p${String(n).padStart(3, '0')}.png`), Buffer.from(r.data, 'base64'));
  await page.close();
}
