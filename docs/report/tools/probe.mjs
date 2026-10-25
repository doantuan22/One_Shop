// probe.mjs <role|guest> <url> : print structure of the page (landmarks, forms, tables, headings) with rects.
import { CdpPage, sleep } from './lib/cdp.mjs';
const [role, url] = process.argv.slice(2);
const LOGIN = { customer: 'khachhang1@example.com', admin: 'admin@example.com', staff: 'staff.thuduc@example.com' };
const page = await CdpPage.open('about:blank');
await page.setViewport(1280, 900); await page.clearSession();
if (LOGIN[role]) await page.login(LOGIN[role], process.env.ONESHOP_SEED_PASSWORD);
await page.navigate(url); await sleep(600);
const res = await page.eval(`(() => {
  const out = [];
  const sel = 'header,nav,aside,main,footer,form,section,table,.card,.pagination,h1,h2,h3,.alert,.breadcrumb,[class*=kpi-chip],[class*=hero]';
  for (const e of document.querySelectorAll(sel)) {
    const r = e.getBoundingClientRect();
    if (r.width < 30 || r.height < 12) continue;
    const t = (e.innerText || '').trim().split(String.fromCharCode(10)).join(' / ').slice(0, 60);
    out.push([e.tagName.toLowerCase() + (e.id ? '#' + e.id : '') + (e.className && typeof e.className === 'string' ? '.' + e.className.trim().split(' ').slice(0, 3).join('.') : ''), Math.round(r.left), Math.round(r.top + scrollY), Math.round(r.width), Math.round(r.height), t]);
  }
  return out;
})()`);
for (const r of res) console.log(r.join(' | '));
await page.close();
