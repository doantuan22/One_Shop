import fs from 'node:fs';
import path from 'node:path';

export const APP = process.env.ONESHOP_APP_URL || 'http://127.0.0.1:8095';
export const DEBUG_URL = process.env.ONESHOP_CDP_URL || 'http://127.0.0.1:9222';
// Password of the automated-test fixture accounts (database/test/test_fixture_seed.sql, *_test databases only).
// For any other database export ONESHOP_DEMO_PASSWORD; no credential of a real deployment belongs in this repository.
export const DEMO_PASSWORD = process.env.ONESHOP_DEMO_PASSWORD ?? 'OneShop@123';

export const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

export class CdpPage {
  static async open(url = 'about:blank') {
    const response = await fetch(`${DEBUG_URL}/json/new?${encodeURIComponent(url)}`, { method: 'PUT' });
    if (!response.ok) throw new Error(`Cannot create a Chrome tab: HTTP ${response.status}`);
    const target = await response.json();
    const page = new CdpPage(target.webSocketDebuggerUrl);
    await page.connect();
    return page;
  }

  constructor(webSocketUrl) {
    this.webSocketUrl = webSocketUrl;
    this.nextId = 1;
    this.pending = new Map();
    this.listeners = new Map();
    this.errors = [];
    this.lastDocumentStatus = null;
  }

  async connect() {
    await new Promise((resolve, reject) => {
      this.ws = new WebSocket(this.webSocketUrl);
      this.ws.addEventListener('open', resolve, { once: true });
      this.ws.addEventListener('error', () => reject(new Error('Chrome DevTools websocket failed')), { once: true });
      this.ws.addEventListener('message', (event) => this.onMessage(event));
    });
    await this.send('Page.enable');
    await this.send('Runtime.enable');
    await this.send('Network.enable');
    await this.send('Log.enable');
    this.on('Network.responseReceived', (event) => {
      if (event.type === 'Document') {
        this.lastDocumentStatus = event.response?.status ?? null;
      }
    });
    this.on('Page.javascriptDialogOpening', () => {
      // Every confirmation in the flows is an intentional business action; accept it.
      this.send('Page.handleJavaScriptDialog', { accept: true }).catch(() => {});
    });
    this.on('Runtime.exceptionThrown', (event) => {
      this.errors.push(event.exceptionDetails?.text || 'Runtime exception');
    });
    this.on('Log.entryAdded', (event) => {
      if (event.entry?.level === 'error') this.errors.push(event.entry.text || 'Console error');
    });
  }

  onMessage(event) {
    const message = JSON.parse(event.data);
    if (message.id) {
      const pending = this.pending.get(message.id);
      if (!pending) return;
      this.pending.delete(message.id);
      if (message.error) pending.reject(new Error(`${pending.method}: ${message.error.message}`));
      else pending.resolve(message.result);
      return;
    }
    const handlers = this.listeners.get(message.method) || [];
    for (const handler of handlers) handler(message.params || {});
  }

  send(method, params = {}) {
    const id = this.nextId++;
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject, method });
      this.ws.send(JSON.stringify({ id, method, params }));
    });
  }

  on(method, handler) {
    const handlers = this.listeners.get(method) || [];
    handlers.push(handler);
    this.listeners.set(method, handlers);
    return () => {
      this.listeners.set(method, (this.listeners.get(method) || []).filter((h) => h !== handler));
    };
  }

  nextEvent(method, timeoutMs = 25000) {
    return new Promise((resolve, reject) => {
      const off = this.on(method, (params) => {
        clearTimeout(timer);
        off();
        resolve(params);
      });
      const timer = setTimeout(() => {
        off();
        reject(new Error(`Timed out waiting for ${method}`));
      }, timeoutMs);
    });
  }

  async eval(expression) {
    const response = await this.send('Runtime.evaluate', {
      expression,
      returnByValue: true,
      awaitPromise: true,
      userGesture: true,
    });
    if (response.exceptionDetails) {
      const detail = response.exceptionDetails.exception?.description || response.exceptionDetails.text;
      throw new Error(`Page script failed: ${detail}`);
    }
    return response.result?.value;
  }

  async waitReady(timeoutMs = 25000) {
    const deadline = Date.now() + timeoutMs;
    while (Date.now() < deadline) {
      try {
        if (await this.eval(`document.readyState === 'complete' && Boolean(document.body)`)) {
          await sleep(100);
          return;
        }
      } catch {
        // The execution context is replaced during navigation; retry.
      }
      await sleep(100);
    }
    throw new Error('Timed out waiting for document.readyState=complete');
  }

  async waitFor(expression, label, timeoutMs = 20000) {
    const deadline = Date.now() + timeoutMs;
    while (Date.now() < deadline) {
      try {
        if (await this.eval(expression)) return;
      } catch {
        // Retry while the page is navigating.
      }
      await sleep(100);
    }
    throw new Error(`Timed out waiting for ${label || expression}`);
  }

  async navigate(url) {
    const absolute = url.startsWith('http') ? url : `${APP}${url}`;
    const loaded = this.nextEvent('Page.loadEventFired', 30000);
    this.lastDocumentStatus = null;
    const result = await this.send('Page.navigate', { url: absolute });
    if (result.errorText) throw new Error(`Navigation to ${absolute} failed: ${result.errorText}`);
    await loaded;
    await this.waitReady();
    await sleep(150);
  }

  async clickAndWait(selector, timeoutMs = 25000) {
    return this.clickJsAndWait(`(() => {
      const el = document.querySelector(${JSON.stringify(selector)});
      if (!el) throw new Error('Missing element: ' + ${JSON.stringify(selector)});
      el.scrollIntoView({ block: 'center', inline: 'center' });
      el.click();
      return true;
    })()`, timeoutMs);
  }

  async clickJsAndWait(expression, timeoutMs = 25000) {
    const loaded = this.nextEvent('Page.loadEventFired', timeoutMs);
    this.lastDocumentStatus = null;
    try {
      await this.eval(expression);
    } catch (error) {
      loaded.catch(() => {});
      throw error;
    }
    await loaded;
    await this.waitReady();
    await sleep(150);
  }

  async fill(selector, value) {
    await this.eval(`(() => {
      const el = document.querySelector(${JSON.stringify(selector)});
      if (!el) throw new Error('Missing field: ' + ${JSON.stringify(selector)});
      const prototype = el instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype
        : el instanceof HTMLSelectElement ? HTMLSelectElement.prototype : HTMLInputElement.prototype;
      const setter = Object.getOwnPropertyDescriptor(prototype, 'value').set;
      setter.call(el, ${JSON.stringify(value)});
      el.dispatchEvent(new Event('input', { bubbles: true }));
      el.dispatchEvent(new Event('change', { bubbles: true }));
      return true;
    })()`);
  }

  async choose(selector) {
    await this.eval(`(() => {
      const el = document.querySelector(${JSON.stringify(selector)});
      if (!el) throw new Error('Missing choice: ' + ${JSON.stringify(selector)});
      if (!el.checked) el.click();
      return true;
    })()`);
  }

  async setViewport(width, height = 900) {
    await this.send('Emulation.setDeviceMetricsOverride', {
      width,
      height,
      deviceScaleFactor: 1,
      mobile: width <= 480,
      screenWidth: width,
      screenHeight: height,
    });
  }

  async screenshot(file) {
    await this.eval('window.scrollTo(0, 0)');
    await sleep(120);
    const result = await this.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.writeFileSync(file, Buffer.from(result.data, 'base64'));
  }

  async url() {
    return this.eval('location.href');
  }

  async pathname() {
    return this.eval('location.pathname');
  }

  async text(selector = 'body') {
    return this.eval(`document.querySelector(${JSON.stringify(selector)})?.innerText || ''`);
  }

  async login(email, password = DEMO_PASSWORD) {
    await this.navigate('/login');
    await this.waitFor(`Boolean(document.querySelector('#identifier'))`, 'login form');
    await this.fill('#identifier', email);
    await this.fill('#password', password);
    await this.clickAndWait('form button[type="submit"]');
    await this.waitFor(`location.pathname !== '/login'`, 'login redirect');
  }

  async clearSession() {
    await this.send('Network.clearBrowserCookies');
  }

  async close() {
    try {
      await this.send('Page.close');
    } catch {
      // The target may already be closed.
    }
    try {
      this.ws.close();
    } catch {
      // Ignore.
    }
  }
}

export async function capture(page, name, file) {
  const info = await page.eval(`({
    path: location.pathname + location.search,
    title: document.title,
    width: window.innerWidth,
    scrollWidth: document.documentElement.scrollWidth,
    bodyScrollWidth: document.body.scrollWidth,
    overflow: document.documentElement.scrollWidth > window.innerWidth + 1
  })`);
  await page.screenshot(file);
  return { name, file, ...info };
}
