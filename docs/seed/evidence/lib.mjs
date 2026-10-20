// Exploratory QA over HTTP for every role. Prints PASS/FAIL per check; any 5xx is a failure.
export const BASE = process.argv[2];
export const results = [];
export class Client {
  constructor(name) { this.name = name; this.jar = new Map(); this.csrf = null; }
  cookieHeader() { return [...this.jar].map(([k, v]) => `${k}=${v}`).join('; '); }
  store(res) {
    for (const c of res.headers.getSetCookie?.() ?? []) {
      const [kv] = c.split(';'); const i = kv.indexOf('='); const k = kv.slice(0, i); const v = kv.slice(i + 1);
      if (/max-age=0/i.test(c) || v === '') this.jar.delete(k); else this.jar.set(k, v);
    }
  }
  async req(method, path, form, headers = {}) {
    const init = { method, redirect: 'manual', headers: { Cookie: this.cookieHeader(), Accept: 'text/html', ...headers } };
    if (form !== undefined) {
      const p = new URLSearchParams();
      for (const [k, v] of Object.entries(form)) (Array.isArray(v) ? v : [v]).forEach(x => p.append(k, x));
      if (this.csrf && !('_csrf' in form)) p.append('_csrf', this.csrf);
      init.body = p.toString(); init.headers['Content-Type'] = 'application/x-www-form-urlencoded';
    }
    const res = await fetch(BASE + path, init);
    this.store(res);
    const body = await res.text();
    const m = body.match(/name="_csrf" value="([^"]+)"/); if (m) this.csrf = m[1];
    return { status: res.status, loc: res.headers.get('location') || '', body };
  }
  get(p) { return this.req('GET', p); }
  post(p, f = {}) { return this.req('POST', p, f); }
  async login(id, pw = 'OneShop@123') {
    await this.get('/login');
    return this.post('/login', { identifier: id, password: pw });
  }
}
export function check(name, ok, detail = '') { results.push({ name, ok: !!ok, detail }); console.log((ok ? 'PASS ' : 'FAIL ') + name + (ok ? '' : '  -> ' + detail)); }
export const is = (r, ...codes) => codes.includes(r.status);
export const noServerError = (name, r) => check(name + ' (no 5xx)', r.status < 500, `status ${r.status} ${r.body.slice(0, 200).replace(/\s+/g, ' ')}`);

