import {spawn} from 'node:child_process';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import path from 'node:path';
const cfg=JSON.parse(await readFile(process.argv[2],'utf8'));
await mkdir(cfg.output,{recursive:true});
const profile=path.resolve('target/qa-audit/chrome-screen-profile-'+Date.now());
const chrome=spawn(cfg.chrome,['--headless=new','--no-first-run','--no-default-browser-check','--remote-debugging-port=0',
  `--user-data-dir=${profile}`,'about:blank'],{stdio:'ignore',windowsHide:true});
const delay=ms=>new Promise(r=>setTimeout(r,ms));
const results=[],networkFailures=[];let page;
async function check(id,fn){try{const detail=await fn();results.push({id,status:'PASS',detail,time:new Date().toISOString()});}
catch(e){results.push({id,status:'FAIL',error:String(e.stack),time:new Date().toISOString()});}}
try{
  let port;for(let i=0;i<200;i++){try{port=Number((await readFile(path.join(profile,'DevToolsActivePort'),'utf8')).split('\n')[0]);if(port)break;}catch{}await delay(100);}
  if(!port)throw new Error('Isolated Chrome startup failed');
  process.env.ONESHOP_CDP_URL=`http://127.0.0.1:${port}`;process.env.ONESHOP_APP_URL=`http://localhost:${cfg.port}`;
  const {CdpPage}=await import('../lib/cdp.mjs');page=await CdpPage.open();
  page.on('Network.responseReceived',e=>{if(e.response.status>=400)networkFailures.push({url:e.response.url,status:e.response.status,type:e.type});});
  async function screen(url){await page.navigate(url);if(page.lastDocumentStatus!==200)throw new Error(`HTTP ${page.lastDocumentStatus}: ${url}`);
    const info=await page.eval(`({url:location.pathname+location.search,title:document.title,forms:[...document.forms].map(f=>({action:new URL(f.action).pathname,method:f.method})),buttons:document.querySelectorAll('button').length})`);
    await page.screenshot(path.join(cfg.output,`screen-${results.length}.png`));return info;}
  for(const url of ['/','/login','/register','/products','/products?q=khongtimthayqa999','/products?page=1','/products?categoryId=1&brandId=1','/products/1','/stores','/stores?provinceCity=invalid','/image-credits'])await check('guest '+url,()=>screen(url));
  for(const url of ['/products','/products?q=CeraVe'])await check('catalog browser title decodes entities '+url,async()=>{
    await page.navigate(url);const title=await page.eval('document.title');
    if(title!=='Mỹ phẩm & Chăm sóc sắc đẹp - OneShop | OneShop')throw new Error('Unexpected plain-text document.title: '+title);return title;});
  await check('guest search form native submission',async()=>{await page.navigate('/products');
    const selector=await page.eval(`(()=>{const e=document.querySelector('input[name="q"]');if(!e)throw new Error('missing search');e.id='qa-search';return '#qa-search'})()`);
    await page.fill(selector,'CeraVe');await page.clickJsAndWait(`document.querySelector('#qa-search').form.requestSubmit()`);
    if(!new URL(await page.url()).searchParams.get('q')?.includes('CeraVe'))throw new Error('search query lost');return await page.pathname();});
  for(const [role,email,urls] of [
    ['customer','khachhang1@example.com',['/account','/orders','/cart']],
    ['staff','staff.thuduc@example.com',['/account','/staff/dashboard','/staff/fulfillments','/staff/fulfillments/delivery','/staff/fulfillments/pickup','/staff/stock','/staff/stock/1/adjust','/staff/inventory-history','/staff/inventory-history/1','/staff/reviews']],
    ['admin','admin@example.com',['/account','/admin','/admin/users','/admin/users/new','/admin/users/7/edit','/admin/stores','/admin/stores/new','/admin/stores/1/edit','/admin/products','/admin/products/new','/admin/products/1/edit','/admin/categories','/admin/categories/1/edit','/admin/brands','/admin/brands/1/edit','/admin/store-products','/admin/store-products/new','/admin/store-products/1/edit','/admin/staff-assignments','/admin/inventory','/admin/orders','/admin/orders/1','/admin/payments','/admin/reviews']]
  ]){
    await page.clearSession();await check(role+' login',async()=>{await page.login(email);return await page.pathname();});
    for(const url of urls)await check(role+' '+url,()=>screen(url));
    if(role==='customer')await check('profile blank name rejected without write',async()=>{
      await page.navigate('/account');await page.fill('[name="fullName"]','');
      await page.clickJsAndWait(`document.querySelector('form[action="/account"]').submit()`);
      if(await page.pathname()!=='/account'||!await page.eval(`!!document.querySelector('.is-invalid')`))throw new Error('missing profile validation');return 'server validation rendered';});
    if(role==='admin'){
      for(const type of ['category','brand'])await check(type+' modal open/cancel/invalid submit/reopen',async()=>{
        await page.navigate('/admin/'+(type==='category'?'categories':'brands'));
        await page.eval(`document.querySelector('[data-bs-target="#${type}-form-modal"]').click()`);
        await page.waitFor(`document.querySelector('#${type}-form-modal').classList.contains('show')`,'modal open');
        await delay(400);await page.eval(`document.querySelector('#${type}-form-modal .modal-footer [data-bs-dismiss]').click()`);
        await page.waitFor(`!document.querySelector('#${type}-form-modal').classList.contains('show')`,'modal cancel');
        await delay(400);await page.eval(`document.querySelector('[data-bs-target="#${type}-form-modal"]').click()`);
        await delay(400);await page.clickAndWait(`button[form="${type}-form"][type="submit"]`);
        await page.waitFor(`document.querySelector('#${type}-form-modal').classList.contains('show')`,'validation modal reopened');
        if(!await page.eval(`!!document.querySelector('#${type}-form .is-invalid')`))throw new Error('missing field validation');return 'cancel made no request; empty submit refused';});
      await check('admin user filter and reset',async()=>{await page.navigate('/admin/users');await page.fill('#user-q','nobodyqa999');await page.clickAndWait('#user-filter button[type="submit"]');
        if(await page.eval(`document.querySelector('#user-count').textContent.trim()`)!=='0')throw new Error('search not empty');
        await page.clickAndWait('#user-filter a[href="/admin/users"]');return await page.eval(`document.querySelector('#user-count').textContent.trim()`);});
    }
    await check(role+' logout',async()=>{
      await page.navigate('/account');await page.clickAndWait('form[action="/logout"] button');
      await page.navigate('/account');if(await page.pathname()!=='/login')throw new Error('logout did not protect account');return 'authenticated cookie cleared';});
  }
  await check('seed category image resource resolves',async()=>{
    const missing=networkFailures.filter(e=>e.type==='Image'&&e.url.includes('/categories/trang-diem.webp'));
    if(missing.length)throw new Error('Seed category image failed to load: '+JSON.stringify(missing));
    return 'no failed requests for seeded category image';
  });
  const runtimeErrors=page.errors;
  await writeFile(path.join(cfg.output,'results.json'),JSON.stringify({results,runtimeErrors,networkFailures},null,2));
  if(results.some(r=>r.status==='FAIL'))process.exitCode=1;
}catch(e){await writeFile(path.join(cfg.output,'fatal.txt'),String(e.stack));process.exitCode=1;}
finally{if(page){try{await page.send('Browser.close');}catch{}page.ws?.close();}chrome.kill();}
