import {spawn} from 'node:child_process';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import path from 'node:path';
const cfg=JSON.parse(await readFile(process.argv[2],'utf8'));
await mkdir(cfg.output,{recursive:true});
const profile=path.resolve('target/qa-fix/chrome-'+cfg.mode+'-'+Date.now());
const chrome=spawn(cfg.chrome,['--headless=new','--no-first-run','--no-default-browser-check','--remote-debugging-port=0',`--user-data-dir=${profile}`,'about:blank'],{stdio:'ignore',windowsHide:true});
const pause=ms=>new Promise(r=>setTimeout(r,ms));
let page;const results=[],network=[];
async function check(id,run){try{const actual=await run();results.push({id,status:'PASS',actual,timestamp:new Date().toISOString()});}
catch(e){results.push({id,status:'FAIL',error:String(e.stack),timestamp:new Date().toISOString()});}}
try {
  let port;for(let i=0;i<200;i++){try{port=Number((await readFile(path.join(profile,'DevToolsActivePort'),'utf8')).split('\n')[0]);if(port)break;}catch{}await pause(100);}
  if(!port)throw new Error('Chrome did not start');
  process.env.ONESHOP_APP_URL=`http://localhost:${cfg.port}`;process.env.ONESHOP_CDP_URL=`http://127.0.0.1:${port}`;
  const {CdpPage}=await import('../lib/cdp.mjs');page=await CdpPage.open();
  page.on('Network.responseReceived',e=>{if(e.type==='Document'||e.type==='Image')network.push({url:e.response.url,status:e.response.status,type:e.type});});
  async function screenshot(name){await page.screenshot(path.join(cfg.output,name+'.png'));}
  async function loadedCategory(label){
    await page.waitFor(`!!document.querySelector('img[alt="Trang điểm"]')`,'category image');
    await page.eval(`document.querySelector('img[alt="Trang điểm"]').scrollIntoView({block:'center'})`);
    await page.waitFor(`(()=>{const i=document.querySelector('img[alt="Trang điểm"]');return i.complete&&i.naturalWidth>0})()`,'category loaded');
    const image=await page.eval(`(()=>{const i=document.querySelector('img[alt="Trang điểm"]');return {src:i.currentSrc,complete:i.complete,naturalWidth:i.naturalWidth,fallback:i.dataset.osFallbackApplied}})()`);
    if(!image.src.endsWith('/images/site/categories/category-placeholder.svg'))throw new Error('seed reference is not the verified local placeholder: '+image.src);
    await screenshot(label);return image;
  }
  if(cfg.mode==='resources') {
    await check('QA008 Home seed image naturalWidth',async()=>{await page.navigate('/');return loadedCategory('home-category');});
    await check('QA008 Admin Categories seed image naturalWidth',async()=>{await page.login('admin@oneshop.vn');await page.clickAndWait('a[href="/admin/categories"]');return loadedCategory('admin-category');});
    await check('QA008 failed resource recovers to fallback without error loop',async()=>{
      await page.eval(`(()=>{const i=document.querySelector('img[alt="Trang điểm"]');delete i.dataset.osFallbackApplied;i.src='/images/qa-intentionally-missing-fallback-test.png';})()`);
      await page.waitFor(`(()=>{const i=document.querySelector('img[alt="Trang điểm"]');return i.dataset.osFallbackApplied==='true'&&i.complete&&i.naturalWidth>0})()`,'fallback loads');
      await screenshot('fallback');return await page.eval(`({src:document.querySelector('img[alt="Trang điểm"]').currentSrc,naturalWidth:document.querySelector('img[alt="Trang điểm"]').naturalWidth})`);
    });
    await check('QA008 no broken canonical Cloudinary category reference',async()=>{
      const bad=network.filter(r=>r.url.includes('/categories/trang-diem.webp'));if(bad.length)throw new Error(JSON.stringify(bad));return 'no request to removed broken seed URL';
    });
  } else {
    await check('QA001 actual Customer profile form',async()=>{
      await page.login('khachhang1@example.com');await page.navigate('/account');
      await page.fill('[name="fullName"]','QA UI Profile');await page.fill('[name="phone"]','0987654321');
      await page.clickAndWait('form[action="/account"] button[type="submit"]');
      const actual=await page.eval(`({name:document.querySelector('[name="fullName"]').value,phone:document.querySelector('[name="phone"]').value,url:location.pathname+location.search})`);
      if(actual.name!=='QA UI Profile'||actual.phone!=='0987654321'||!actual.url.includes('success'))throw new Error(JSON.stringify(actual));await screenshot('profile');return actual;
    });
    await check('QA002 actual Admin upload/select/delete primary forms',async()=>{
      await page.clearSession();await page.login('admin@oneshop.vn');await page.navigate(`/admin/products/${cfg.productId}/edit`);
      const png=path.join(cfg.output,'fixture.png');await writeFile(png,Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=','base64'));
      const {root}=await page.send('DOM.getDocument');const {nodeId}=await page.send('DOM.querySelector',{nodeId:root.nodeId,selector:`form[action="/admin/products/${cfg.productId}/images"] input[type="file"]`});
      await page.send('DOM.setFileInputFiles',{nodeId,files:[png]});
      await page.clickAndWait(`form[action="/admin/products/${cfg.productId}/images"] button[type="submit"]`);
      const choose=await page.eval(`(()=>{const f=[...document.querySelectorAll('form[action$="/primary"]')].at(-1);if(!f)throw new Error('uploaded image missing');return f.getAttribute('action')})()`);
      const id=Number(choose.split('/').at(-2));await page.clickAndWait(`form[action="${choose}"] button`);
      if(await page.eval(`!!document.querySelector('form[action="${choose}"]')`))throw new Error('selected image is not primary');await screenshot('primary');
      await page.clickAndWait(`form[action="/admin/products/${cfg.productId}/images/${id}/delete"] button`);
      if(await page.eval(`!!document.querySelector('form[action="/admin/products/${cfg.productId}/images/${id}/delete"]')`))throw new Error('deleted image remains');
      await screenshot('image-deleted');return {uploadedAndDeletedImage:id};
    });
    for(const url of ['/products','/products?q=CeraVe'])await check('QA007 catalog title '+url,async()=>{
      await page.navigate(url);const title=await page.eval('document.title');const expected='Mỹ phẩm & Chăm sóc sắc đẹp - OneShop | OneShop';
      if(title!==expected)throw new Error(JSON.stringify({expected,actual:title}));return title;
    });
    for(const title of ['A & B','Tiếng Việt có dấu','<b>Text only</b>','</title><script>window.qaXss=1</script>','Literal &amp;'])await check('QA007 plain-text title '+title,async()=>{
      await page.navigate('/products/qa-title?text='+encodeURIComponent(title));
      const actual=await page.eval(`({title:document.title,text:document.querySelector('#qa-title-text')?.textContent,injected:!!window.qaXss,scripts:[...document.scripts].filter(s=>s.textContent.includes('window.qaXss')).length})`);
      if(actual.title!==title+' | OneShop'||actual.text!==title||actual.injected||actual.scripts)throw new Error(JSON.stringify(actual));return actual;
    });
    await check('Staff Dashboard still renders',async()=>{await page.clearSession();await page.login('staff.thuduc@oneshop.vn');await page.navigate('/staff/dashboard');
      if(page.lastDocumentStatus!==200)throw new Error('HTTP '+page.lastDocumentStatus);await screenshot('staff');return await page.eval('document.title');});
  }
  await writeFile(path.join(cfg.output,'results.json'),JSON.stringify({mode:cfg.mode,results,network,errors:page.errors},null,2));
  if(results.some(r=>r.status==='FAIL'))process.exitCode=1;
} catch(e){await writeFile(path.join(cfg.output,'fatal.txt'),String(e.stack));process.exitCode=1;}
finally{if(page){try{await page.send('Browser.close');}catch{}page.ws?.close();}chrome.kill();}
