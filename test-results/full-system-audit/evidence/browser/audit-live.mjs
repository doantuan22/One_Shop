import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

const repo = path.resolve(import.meta.dirname, '../../../..');
const { APP, CdpPage, sleep } = await import(pathToFileURL(path.join(repo, 'scripts/lib/cdp.mjs')));
const output = import.meta.dirname;
const routes = [
  ['home', '/', 'body'],
  ['login', '/login', '#identifier'],
  ['register', '/register', 'form'],
  ['catalog', '/products', 'body'],
  ['stores', '/stores', 'body'],
  ['credits', '/image-credits', 'body'],
];
const result = { app: APP, startedAt: new Date().toISOString(), visits: [], errors: [], failedResponses: [] };
const page = await CdpPage.open(APP);
page.on('Network.responseReceived', ({ response }) => {
  if ((response?.status ?? 0) >= 400 && !response.url.startsWith('data:')) result.failedResponses.push(`${response.status} ${response.url}`);
});
try {
  for (const width of [1440, 768, 390]) {
    await page.setViewport(width, width === 390 ? 844 : 900);
    for (const [name, route, selector] of routes) {
      await page.navigate(route);
      await sleep(150);
      const details = await page.eval(`({title:document.title, path:location.pathname, target:!!document.querySelector(${JSON.stringify(selector)}), width:innerWidth, scrollWidth:document.documentElement.scrollWidth, bodyText:document.body.innerText.slice(0,120)})`);
      const screenshot = path.join(output, `${name}-${width}.png`);
      await page.screenshot(screenshot);
      result.visits.push({ name, route, status: page.lastDocumentStatus, screenshot: path.relative(repo, screenshot), overflow: details.scrollWidth > details.width + 1, ...details });
    }
  }
} finally {
  result.errors = page.errors;
  result.finishedAt = new Date().toISOString();
  fs.writeFileSync(path.join(output, 'browser-run.json'), JSON.stringify(result, null, 2));
  await page.close();
}
console.log(JSON.stringify({ visits: result.visits.length, errors: result.errors.length, failedResponses: result.failedResponses.length, overflow: result.visits.filter(x => x.overflow).length }, null, 2));
if (result.errors.length || result.failedResponses.length || result.visits.some(x => !x.target || x.status >= 400)) process.exitCode = 1;
