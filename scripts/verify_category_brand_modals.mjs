import fs from 'node:fs';
import path from 'node:path';
import { APP, CdpPage, sleep } from './lib/cdp.mjs';

const out = path.resolve('artifacts', 'ui-audit', 'category-brand-modals');
const report = { app: APP, generatedAt: new Date().toISOString(), checks: [], errors: [] };
const page = await CdpPage.open(`${APP}/login`);

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

async function shot(name) {
  const file = path.join(out, `${name}.png`);
  await page.screenshot(file);
  return file;
}

async function verify(kind, route, modalId, formId) {
  await page.setViewport(1440, 900);
  await page.navigate(route);
  await sleep(200);
  const base = await page.eval(`(() => ({
    modalForm: Boolean(document.querySelector('#${modalId} form#${formId}')),
    fixedFormOutsideModal: Boolean([...document.querySelectorAll('form#${formId}')].find(form => !form.closest('.modal'))),
    table: Boolean(document.querySelector('.os-table')),
    addButton: Boolean(document.querySelector('[data-bs-target="#${modalId}"]')),
    tableWidth: document.querySelector('.os-table')?.closest('.card')?.getBoundingClientRect().width || 0,
    mainWidth: document.querySelector('main')?.getBoundingClientRect().width || 0
  }))()`);
  assert(base.modalForm && !base.fixedFormOutsideModal && base.table && base.addButton, `${kind}: base layout missing expected modal/table`);
  report.checks.push({ kind, phase: 'layout', ...base });

  await page.eval(`document.querySelector('[data-bs-target="#${modalId}"]').click()`);
  await page.waitFor(`document.getElementById('${modalId}').classList.contains('show')`, `${kind} create modal`);
  await shot(`${kind}-create-desktop`);

  const preview = await page.eval(`(() => {
    const input = document.querySelector('#${modalId} [data-image-preview-input]');
    if (!input) return { supported: false };
    const png = new Uint8Array([137,80,78,71,13,10,26,10]);
    const file = new File([png], 'preview.png', { type: 'image/png' });
    const transfer = new DataTransfer(); transfer.items.add(file); input.files = transfer.files;
    input.dispatchEvent(new Event('change', { bubbles: true }));
    return new Promise(resolve => setTimeout(() => {
      const previews = input.closest('.os-modal-image-tools')?.querySelectorAll('[data-image-preview]') || input.closest('form').querySelectorAll('[data-image-preview]');
      const node = previews[previews.length - 1];
      resolve({ supported: true, visible: !!node && !node.classList.contains('d-none'), src: node?.getAttribute('src')?.startsWith('data:') || false });
    }, 20));
  })()`);
  assert(!preview.supported || (preview.visible && preview.src), `${kind}: image preview did not render`);
  report.checks.push({ kind, phase: 'preview', ...preview });

  await page.navigate(route);
  await page.eval(`document.querySelector('[data-bs-target="#${modalId}"]').click()`);
  await page.waitFor(`document.getElementById('${modalId}').classList.contains('show')`, `${kind} modal before validation`);
  await page.clickJsAndWait(`document.querySelector('#${modalId} button[form="${formId}"][type="submit"]').click()`);
  await page.waitFor(`document.getElementById('${modalId}').classList.contains('show')`, `${kind} validation modal reopen`);
  const validation = await page.eval(`Boolean(document.querySelector('#${modalId} .is-invalid'))`);
  assert(validation, `${kind}: backend validation did not show within the modal`);
  report.checks.push({ kind, phase: 'validation', modalReopened: true, fieldError: validation });
  await shot(`${kind}-validation-error`);

  const editHref = await page.eval(`document.querySelector('.os-table a[href*="/edit"]')?.getAttribute('href') || null`);
  assert(editHref, `${kind}: no edit link found`);
  await page.navigate(editHref);
  await page.waitFor(`document.getElementById('${modalId}').classList.contains('show')`, `${kind} edit modal auto open`);
  const edit = await page.eval(`(() => ({
    action: document.querySelector('#${formId}')?.getAttribute('action'),
    name: document.querySelector('#${formId} input[name="name"]')?.value,
    hasImageTools: Boolean(document.querySelector('#${modalId} .os-modal-image-tools'))
  }))()`);
  assert(edit.action?.match(/\/\d+$/) && Boolean(edit.name), `${kind}: edit form was not populated`);
  report.checks.push({ kind, phase: 'edit', ...edit });
  const editPreview = await page.eval(`(() => {
    const input = document.querySelector('#${modalId} .os-modal-image-tools [data-image-preview-input]');
    if (!input) return { supported: false };
    const png = new Uint8Array([137,80,78,71,13,10,26,10]);
    const file = new File([png], 'preview.png', { type: 'image/png' });
    const transfer = new DataTransfer(); transfer.items.add(file); input.files = transfer.files;
    input.dispatchEvent(new Event('change', { bubbles: true }));
    return new Promise(resolve => setTimeout(() => {
      const previews = input.closest('.os-modal-image-tools').querySelectorAll('[data-image-preview]');
      const node = previews[previews.length - 1];
      resolve({ supported: true, visible: !!node && !node.classList.contains('d-none'), src: node?.getAttribute('src')?.startsWith('data:') || false });
    }, 20));
  })()`);
  assert(!editPreview.supported || (editPreview.visible && editPreview.src), `${kind}: edit image preview did not render`);
  report.checks.push({ kind, phase: 'edit-preview', ...editPreview });
  await shot(`${kind}-edit-desktop`);

  await page.setViewport(390, 844);
  await page.navigate(route);
  await page.eval(`document.querySelector('[data-bs-target="#${modalId}"]').click()`);
  await page.waitFor(`document.getElementById('${modalId}').classList.contains('show')`, `${kind} mobile modal`);
  const mobile = await page.eval(`(() => { const modal = document.querySelector('#${modalId} .modal-dialog'); return { viewport: innerWidth, dialogWidth: Math.round(modal.getBoundingClientRect().width), overflow: document.documentElement.scrollWidth > innerWidth + 1 }; })()`);
  assert(!mobile.overflow && mobile.dialogWidth < mobile.viewport, `${kind}: mobile modal overflow`);
  report.checks.push({ kind, phase: 'mobile', ...mobile });
  await shot(`${kind}-create-mobile`);
}

try {
  await page.clearSession();
  await page.login('admin@oneshop.vn');
  await verify('categories', '/admin/categories', 'category-form-modal', 'category-form');
  await verify('brands', '/admin/brands', 'brand-form-modal', 'brand-form');
  report.result = 'PASS';
} catch (error) {
  report.result = 'FAIL';
  report.error = error.stack || String(error);
  process.exitCode = 1;
} finally {
  report.errors = page.errors;
  fs.mkdirSync(out, { recursive: true });
  fs.writeFileSync(path.join(out, 'report.json'), JSON.stringify(report, null, 2));
  await page.close();
}

console.log(JSON.stringify(report, null, 2));
