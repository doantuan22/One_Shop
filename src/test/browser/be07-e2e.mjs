// Real Chromium CDP automation using Node built-ins. All mutations are native UI form submissions.
import {spawn} from 'node:child_process';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import path from 'node:path';
const config=JSON.parse(await readFile(process.argv[2],'utf8'));
await mkdir(config.output,{recursive:true});
const browser=spawn(config.chrome,['--headless=new','--disable-gpu','--no-first-run','--no-default-browser-check',
  '--remote-debugging-port=0',`--user-data-dir=${path.resolve(config.output,'profile')}`,'about:blank'],{windowsHide:true,stdio:'ignore'});
const pause=ms=>new Promise(r=>setTimeout(r,ms));
let ws,seq=0;const pending=new Map();const trace=[];
async function until(fn,label,timeout=20000){let last;const end=Date.now()+timeout;while(Date.now()<end){try{const v=await fn();if(v)return v;}catch(e){last=e;}await pause(100);}throw new Error(`Timeout: ${label} ${last?.message??''}`);}
try{
  const port=await until(async()=>Number((await readFile(path.join(config.output,'profile','DevToolsActivePort'),'utf8')).split('\n')[0]),'Chromium startup');
  const version=await(await fetch(`http://127.0.0.1:${port}/json/version`)).json();
  ws=new WebSocket(version.webSocketDebuggerUrl);await new Promise((ok,no)=>{ws.onopen=ok;ws.onerror=no;});
  ws.onmessage=e=>{const m=JSON.parse(e.data);if(m.id){const p=pending.get(m.id);if(p){pending.delete(m.id);m.error?p.reject(new Error(m.error.message)):p.resolve(m.result);}}};
  function call(method,params={},sessionId){const id=++seq;return new Promise((resolve,reject)=>{pending.set(id,{resolve,reject});ws.send(JSON.stringify({id,method,params,...(sessionId?{sessionId}:{})}));});}
  async function tab(){const {browserContextId}=await call('Target.createBrowserContext');const {targetId}=await call('Target.createTarget',{url:'about:blank',browserContextId});const {sessionId}=await call('Target.attachToTarget',{targetId,flatten:true});await call('Page.enable',{},sessionId);return sessionId;}
  async function evaluate(s,expression){const r=await call('Runtime.evaluate',{expression,returnByValue:true,awaitPromise:true},s);if(r.exceptionDetails)throw new Error(r.exceptionDetails.text+': '+r.exceptionDetails.exception?.description);return r.result.value;}
  async function nav(s,url){await call('Page.navigate',{url:`http://localhost:${config.port}${url}`},s);await until(()=>evaluate(s,'document.readyState==="complete"'),'page ready');await pause(100);}
  async function has(s,selector){return evaluate(s,`!!document.querySelector(${JSON.stringify(selector)})`);}
  async function field(s,selector,value){await until(()=>has(s,selector),selector);await evaluate(s,`(()=>{const e=document.querySelector(${JSON.stringify(selector)});e.value=${JSON.stringify(String(value))};e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));})()`);}
  async function click(s,selector){console.log("click",selector);await call("Page.bringToFront",{},s);await until(()=>has(s,selector),selector);
    // The storefront sets CSS scroll-behavior:smooth, so scrollIntoView() animates and an immediate
    // getBoundingClientRect() still reports the off-screen position. Force an instant scroll before
    // measuring, then assert the point really belongs to the target (guard against overlays).
    const point=await evaluate(s,`(()=>{const e=document.querySelector(${JSON.stringify(selector)});document.documentElement.style.scrollBehavior='auto';e.scrollIntoView({block:'center',inline:'center',behavior:'instant'});const r=e.getBoundingClientRect();if(e.disabled)throw new Error('disabled ${selector}');if(r.top<0||r.bottom>innerHeight)throw new Error('target still outside viewport: '+JSON.stringify({top:r.top,bottom:r.bottom,h:innerHeight}));const x=r.x+r.width/2,y=r.y+r.height/2;const hit=document.elementFromPoint(x,y);if(!hit||!(hit===e||e.contains(hit)))throw new Error('target covered at click point: '+(hit?hit.outerHTML.slice(0,120):'null'));return {x,y};})()`);
    await call('Input.dispatchMouseEvent',{type:'mousePressed',button:'left',clickCount:1,...point},s);await call('Input.dispatchMouseEvent',{type:'mouseReleased',button:'left',clickCount:1,...point},s);await pause(250);}
  async function submit(s,selector){const old=await evaluate(s,'document.documentElement.outerHTML');await click(s,selector);await until(async()=>await evaluate(s,'document.readyState==="complete" && document.documentElement.outerHTML')!==old,'form response');await pause(150);}
  async function screenshot(s,label){const r=await call('Page.captureScreenshot',{format:'png',captureBeyondViewport:false},s);await writeFile(path.join(config.output,label+'.png'),Buffer.from(r.data,'base64'));trace.push({step:label,url:await evaluate(s,'location.pathname+location.search'),title:await evaluate(s,'document.title')});}
  async function login(email){const s=await tab();await nav(s,'/login');await pause(1200);await field(s,'#identifier',email);await field(s,'#password','OneShop@123');await screenshot(s,'00-login-filled');await submit(s,'form[action="/login"] button[type="submit"]');await until(()=>evaluate(s,'location.pathname!=="/login"'),'login');return s;}
  const customer=await login('khachhang1@example.com');
  await nav(customer,`/products/${config.productId}`);
  await field(customer,'#add-quantity',config.quantity);await submit(customer,'form[action="/cart/items"] button[type="submit"]');
  await nav(customer,'/cart');
  await click(customer,'#select-all-items');
  if(!await evaluate(customer,`document.querySelector('#select-all-items').checked && [...document.querySelectorAll('input[name="cartItemIds"]:not(:disabled)')].every(e=>e.checked)`))throw new Error('Central cart select-all state is inconsistent');
  await click(customer,'#select-all-items');
  if(await evaluate(customer,`[...document.querySelectorAll('input[name="cartItemIds"]:not(:disabled)')].some(e=>e.checked)`))throw new Error('Central cart select-all cannot clear the selection');
  await click(customer,'input[name="cartItemIds"]');
  await submit(customer,'#cart-selection-form button[type="submit"]');
  await until(()=>has(customer,'#receiverName'),'checkout page');
  if(config.mode==='PICKUP')await nav(customer,await evaluate(customer,`'/checkout?'+[...document.querySelectorAll('input[name="cartItemIds"]')].map(e=>'cartItemIds='+e.value).join('&')+'&fulfillmentType=STORE_PICKUP&paymentMethod=PAY_AT_STORE&pickupStoreId='+${config.storeId}`));
  else if(['ONLINE','FAILED','REFUND','REVIEW'].includes(config.mode)){
    await click(customer,'input[name="paymentMethod"][value="ONLINE"]');await until(()=>evaluate(customer,'location.search.includes("ONLINE")'),'ONLINE preview');await until(()=>has(customer,'#receiverName'),'online fields');
  }
  await field(customer,'#receiverName','Browser E2E Customer');await field(customer,'#receiverPhone','0912345678');
  if(config.mode!=='PICKUP'){await field(customer,'#shippingAddress','1 Nguyen Trai, TP. Ho Chi Minh');await until(()=>evaluate(customer,`new URL(location.href).searchParams.get('shippingAddress')==='1 Nguyen Trai, TP. Ho Chi Minh'`),'address preview');await until(()=>has(customer,'#place-order'),'refreshed checkout');await pause(300);}
  // Refresh the preview with the submitted address so the server-issued fingerprint matches.
  if(config.mode==='COD_SPLIT'){
    const query=await evaluate(customer,`'/checkout?'+[...document.querySelectorAll('input[name="cartItemIds"]')].map(e=>'cartItemIds='+e.value).join('&')+'&fulfillmentType=DELIVERY&paymentMethod=COD&shippingAddress='+encodeURIComponent('1 Nguyen Trai, TP. Ho Chi Minh')`);
    await nav(customer,query);await field(customer,'#receiverName','Browser E2E Customer');await field(customer,'#receiverPhone','0912345678');await click(customer,'#splitCodConsent');if(!await evaluate(customer,`document.querySelector('#splitCodConsent').checked`)){await evaluate(customer,`document.querySelector('#splitCodConsent').focus()`);await call('Input.dispatchKeyEvent',{type:'keyDown',key:' ',code:'Space',windowsVirtualKeyCode:32},customer);await call('Input.dispatchKeyEvent',{type:'keyUp',key:' ',code:'Space',windowsVirtualKeyCode:32},customer);}if(!await evaluate(customer,`document.querySelector('#splitCodConsent').checked`))throw new Error('Customer consent checkbox not selected');
  }
  await screenshot(customer,'01-checkout');await submit(customer,'#place-order');
  await until(()=>evaluate(customer,'/^\\/checkout\\/\\d+$/.test(location.pathname)'),'checkout created');
  const orderIds=await evaluate(customer,`[...new Set([...document.querySelectorAll('a[href]')].map(a=>a.getAttribute('href')).filter(x=>x.startsWith('/orders/') && Number(x.substring(8))>0).map(x=>Number(x.split('/')[2])))]`);
  if(!orderIds.length)throw new Error('No order links in checkout result');
  const fulfillmentIds=await evaluate(customer,`[...document.querySelectorAll('[data-fulfillment-id]')].map(e=>Number(e.dataset.fulfillmentId))`);
  await screenshot(customer,'02-created');const id=orderIds[0];
  if(['ONLINE','REVIEW'].includes(config.mode)){
    await nav(customer,`/orders/${id}/payments`);
    await submit(customer,'#start-payment');
    await until(()=>evaluate(customer,'location.search.includes("outcome=")'),'payment result');
    await screenshot(customer,'03-payment-success');
  }
  if(config.mode==='REFUND'){
    await nav(customer,`/orders/${id}/payments`);
    await submit(customer,'#start-payment');
    await until(()=>evaluate(customer,'location.search.includes("outcome=CONFIRMED")'),'payment success before refund');
    await screenshot(customer,'03-payment-success');
  }
  if(config.mode==='FAILED'){
    // The customer payment page intentionally exposes only the happy-path "pay" button; the
    // FAILED/UNKNOWN scenarios have no UI control. Exercise them through the real HTTP endpoint instead,
    // from the logged-in customer page: its own session cookie and its own CSRF token, no payment status sent.
    await nav(customer,`/orders/${id}/payments`);
    const paymentId=await evaluate(customer,`Number(document.querySelector('[data-payment-id]')?.dataset.paymentId)||0`);
    if(!paymentId)throw new Error('No payment attempt to fail on /orders/'+id+'/payments');
    const posted=await evaluate(customer,`(async()=>{const token=document.querySelector('input[name="_csrf"]')?.value;if(!token)throw new Error('no csrf token on page');const r=await fetch('/orders/${id}/payments/${paymentId}/simulate',{method:'POST',credentials:'same-origin',redirect:'follow',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:'scenario=FAILED&_csrf='+encodeURIComponent(token)});return {status:r.status,url:r.url};})()`);
    if(posted.status!==200||!posted.url.includes('outcome='))throw new Error('FAILED scenario was not accepted: '+JSON.stringify(posted));
    await nav(customer,`/orders/${id}/payments`);await screenshot(customer,'03-payment-failed');
  }
  if(config.mode==='REFUND') {await nav(customer,`/orders/${id}`);await submit(customer,`form[action="/orders/${id}/cancel"] button`);await screenshot(customer,'04-refund');}
  if(['PICKUP','ONLINE','REVIEW'].includes(config.mode)){
    const staff=await login('staff.thuduc@example.com');const f=fulfillmentIds[0];await nav(staff,`/staff/fulfillments/${f}`);
    const actions=config.mode==='PICKUP'?['prepare','ready-for-pickup']:['prepare','pack','ship','complete-delivery'];
    for(const a of actions){await submit(staff,`form[data-action="${a}"] button`);await until(()=>evaluate(staff,'document.readyState==="complete"'),'staff transition');}
    if(config.mode==='PICKUP'){
      await nav(customer,`/orders/${id}`);const code=await evaluate(customer,`document.querySelector('.os-fulfillment-pickup-code, #pickup-code').textContent.trim()`);
      if((await evaluate(staff,'document.body.innerText')).includes(code))throw new Error('Pickup code leaked to Staff');
      await field(staff,'#pickupCode',code);await submit(staff,'form[data-action="complete-pickup"] button');
    }
    await screenshot(staff,'04-staff-completed');
    if(config.mode==='REVIEW'){
      await nav(customer,`/orders/${id}`);await field(customer,'form[action="/reviews"] [name="rating"]',5);await field(customer,'form[action="/reviews"] [name="comment"]','BE07 browser review');await submit(customer,'form[action="/reviews"] button');
      await nav(staff,'/staff/reviews');const reviewPath=await evaluate(staff,`[...document.querySelectorAll('a[href]')].map(a=>a.getAttribute('href')).find(x=>x.startsWith('/staff/reviews/') && Number(x.substring(15))>0)`);if(!reviewPath)throw new Error('Review missing from Staff queue');await nav(staff,reviewPath);await field(staff,'#reply','BE07 browser staff reply');await submit(staff,'form[action$="/reply"] button');await screenshot(staff,'05-staff-reply');
      const admin=await login('admin@example.com');await nav(admin,'/admin/reviews');const reviewId=Number(reviewPath.split('/').pop());
      // Moderation goes through the confirm-with-reason modal: Ẩn opens #osReasonModal, a
      // mandatory reason must be typed, then #osReasonConfirm performs the real POST.
      await click(admin,`tr[data-review-id="${reviewId}"] form[action$="/status"] button`);
      await until(()=>evaluate(admin,`document.querySelector('#osReasonModal').classList.contains('show')`),'reason modal');
      await field(admin,'#osReasonInput','BE07 browser hide reason');
      await submit(admin,'#osReasonConfirm');
      await until(()=>evaluate(admin,'location.pathname==="/admin/reviews" && location.search.includes("success")'),'moderation saved');
      await screenshot(admin,'06-admin-moderation');
    }
  }
  await nav(customer,`/orders/${id}`);await screenshot(customer,'07-customer-final');
  await writeFile(path.join(config.output,'result.json'),JSON.stringify({browser:version.Browser,mode:config.mode,orderIds,fulfillmentIds,trace},null,2));
  await call('Browser.close');
}catch(e){await writeFile(path.join(config.output,'error.txt'),String(e.stack));throw e;}finally{if(ws)ws.close();browser.kill();}
