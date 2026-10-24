import { CdpPage, APP } from './lib/cdp.mjs';

const page = await CdpPage.open(`${APP}/login`);
try {
  await page.waitReady();
  console.log('title=', await page.eval('document.title'));
  console.log('url=', await page.url());
  console.log('login form=', await page.eval(`Boolean(document.querySelector('#identifier'))`));
  console.log('csrf=', await page.eval(`document.querySelector('form input[name="_csrf"]')?.value?.length || 0`));
  await page.login('khachhang1@example.com');
  console.log('after login url=', await page.url());
  console.log('after login title=', await page.eval('document.title'));
  console.log('body has cart=', await page.eval(`document.body.innerText.includes('Giỏ hàng')`));
} finally {
  await page.close();
}
