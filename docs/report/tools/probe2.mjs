import { CdpPage, sleep } from './lib/cdp.mjs';
const [role, url, ...texts] = process.argv.slice(2);
const LOGIN = { customer: 'khachhang1@example.com', admin: 'admin@example.com', staff: 'staff.thuduc@example.com' };
const page = await CdpPage.open('about:blank'); await page.setViewport(1280, 900); await page.clearSession();
await page.login(LOGIN[role], process.env.ONESHOP_SEED_PASSWORD);
await page.navigate(url); await sleep(600);
for (const t of texts) {
  console.log('##', t, await page.eval(`(() => {
    const els = [...document.querySelectorAll('main *')].filter(e => e.children.length === 0 && (e.innerText || '').trim().indexOf(${JSON.stringify(t)}) === 0);
    return els.slice(0, 2).map(e => { const chain = []; let n = e; for (let i = 0; i < 6 && n && n.tagName !== 'MAIN'; i++) { chain.push(n.tagName.toLowerCase() + '.' + String(n.className || '').split(' ').slice(0, 2).join('.')); n = n.parentElement; } return chain.join(' < '); }).join(' || ');
  })()`));
}
await page.close();
