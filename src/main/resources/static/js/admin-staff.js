// OneShop Admin + Staff console behaviour (loaded only by the admin/staff decorators).
// Page-specific scripts in templates run before this file (SiteMesh places the shared
// script last), so every feature below waits for DOMContentLoaded.
document.addEventListener('DOMContentLoaded', function () {
    'use strict';

    /* ------------------------------------------------------------------
     * 1. Confirm-with-reason: forms marked data-confirm-reason open a modal
     *    asking for a rationale before submitting. The typed reason is sent
     *    as an extra hidden field (name from data-reason-field, default
     *    "reason") so the server can store it like any other form field.
     * ------------------------------------------------------------------ */
    var reasonModal = null;

    function buildReasonModal(form) {
        var title = form.getAttribute('data-reason-title') || 'Xác nhận thao tác';
        var desc = form.getAttribute('data-reason-desc') ||
            'Thao tác này sẽ thay đổi dữ liệu. Vui lòng nhập lý do để lưu vào lịch sử.';
        var label = form.getAttribute('data-reason-label') || 'Lý do';
        var required = form.hasAttribute('data-reason-required');
        var confirmText = form.getAttribute('data-reason-button') || 'Xác nhận';
        var danger = form.hasAttribute('data-reason-danger');

        var modal = document.createElement('div');
        modal.className = 'modal fade';
        modal.id = 'osReasonModal';
        modal.tabIndex = -1;
        modal.innerHTML = ''
            + '<div class="modal-dialog modal-dialog-centered">'
            + '  <div class="modal-content">'
            + '    <div class="modal-header">'
            + '      <h2 class="modal-title h6 mb-0">' + esc(title) + '</h2>'
            + '      <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Đóng"></button>'
            + '    </div>'
            + '    <form class="os-reason-form">'
            + '      <div class="modal-body">'
            + '        <p class="mb-3">' + esc(desc) + '</p>'
            + '        <label class="form-label" for="osReasonInput">' + esc(label)
            + (required ? ' <span class="text-danger">*</span>' : '') + '</label>'
            + '        <textarea class="form-control os-reason-input" id="osReasonInput" rows="3"'
            + (required ? ' required' : '') + '></textarea>'
            + '        <div class="invalid-feedback">Vui lòng nhập lý do.</div>'
            + '      </div>'
            + '      <div class="modal-footer py-2">'
            + '        <button type="button" class="btn btn-outline-secondary btn-sm" data-bs-dismiss="modal">Hủy</button>'
            + '        <button type="button" class="btn ' + (danger ? 'btn-danger' : 'btn-primary') + ' btn-sm os-reason-confirm" id="osReasonConfirm">' + esc(confirmText) + '</button>'
            + '      </div>'
            + '    </form>'
            + '  </div>'
            + '</div>';
        document.body.appendChild(modal);
        return modal;
    }

    function esc(text) {
        return String(text)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    }

    document.querySelectorAll('form[data-confirm-reason]').forEach(function (form) {
        form.addEventListener('submit', function (event) {
            event.preventDefault();
            var node = document.getElementById('osReasonModal') || buildReasonModal(form);
            var bsModal = bootstrap.Modal.getOrCreateInstance(node);
            var input = node.querySelector('#osReasonInput');
            var confirmBtn = node.querySelector('#osReasonConfirm');

            input.value = '';
            input.classList.remove('is-invalid');

            // one-shot confirm handler bound per open (replaces the previous listener)
            if (confirmBtn._osHandler) {
                confirmBtn.removeEventListener('click', confirmBtn._osHandler);
            }
            confirmBtn._osHandler = function () {
                var value = input.value.trim();
                if (form.hasAttribute('data-reason-required') && !value) {
                    input.classList.add('is-invalid');
                    input.focus();
                    return;
                }
                var hidden = document.createElement('input');
                hidden.type = 'hidden';
                hidden.name = form.getAttribute('data-reason-field') || 'reason';
                hidden.value = value;
                form.appendChild(hidden);
                bsModal.hide();
                // remove the submit listener so the hidden submit passes through freely
                form.removeEventListener('submit', form._osSubmitGuard);
                delete form._osSubmitGuard;
                form.submit();
            };
            confirmBtn.addEventListener('click', confirmBtn._osHandler);

            // guard keep the submit prevention attached only until confirmed
            form._osSubmitGuard = function (evt) { evt.preventDefault(); };
            form.addEventListener('submit', form._osSubmitGuard);

            bsModal.show();
        });
    });

    /* ------------------------------------------------------------------
     * 2. Stock adjust preview: an ABSOLUTE "measured quantity" input with a
     *    live delta + result hint inside the modal or the dedicated page.
     * ------------------------------------------------------------------ */
    document.querySelectorAll('[data-qty-preview]').forEach(function (container) {
        var before = parseInt(container.getAttribute('data-qty-before'), 10);
        var input = container.closest('form, .modal-content, .os-stock-adjust') ?
            container.closest('form, .modal-content, .os-stock-adjust').querySelector('[data-qty-input]') :
            container.querySelector('[data-qty-input]');
        if (!input && container.previousElementSibling && container.previousElementSibling.matches('[data-qty-input]')) {
            input = container.previousElementSibling;
        }
        var afterEl = container.querySelector('[data-qty-after]');
        var deltaEl = container.querySelector('[data-qty-delta]');
        if (isNaN(before) || !input || !afterEl || !deltaEl) {
            return;
        }
        var warnedEl = container.querySelector('[data-qty-warned]');

        function recalc() {
            var value = parseInt(input.value, 10);
            if (isNaN(value)) {
                afterEl.textContent = '—';
                deltaEl.textContent = '—';
                deltaEl.classList.remove('os-delta-pos', 'os-delta-neg');
                deltaEl.classList.add('os-delta-neutral');
                if (warnedEl) warnedEl.classList.add('d-none');
                return;
            }
            var delta = value - before;
            afterEl.textContent = String(value);
            afterEl.classList.toggle('text-danger', value <= 0);
            deltaEl.textContent = delta === 0 ? 'không đổi' : (delta > 0 ? '+' + delta : String(delta));
            deltaEl.classList.toggle('os-delta-pos', delta > 0);
            deltaEl.classList.toggle('os-delta-neg', delta < 0);
            deltaEl.classList.toggle('os-delta-neutral', delta === 0);
            if (warnedEl) {
                var text = '';
                if (delta === 0) {
                    text = container.getAttribute('data-qty-same-text') || 'Số lượng không thay đổi: hệ thống không ghi biến động mới.';
                } else if (value === 0) {
                    text = 'Số lượng này sẽ đưa SKU về hết hàng.';
                }
                if (text) {
                    var textEl = warnedEl.querySelector('[data-qty-warned-text]') || warnedEl;
                    textEl.textContent = text;
                    warnedEl.classList.remove('d-none');
                } else {
                    warnedEl.classList.add('d-none');
                }
            }
        }

        input.addEventListener('input', recalc);
        recalc();
    });

    /* ------------------------------------------------------------------
     * 3. Filter reset buttons: links marked data-filter-reset clear every
     *    field of their closest filter form, then jump to the raw URL.
     * ------------------------------------------------------------------ */
    document.querySelectorAll('[data-filter-reset]').forEach(function (link) {
        link.addEventListener('click', function (event) {
            var form = link.closest('form');
            if (!form) {
                return;
            }
            event.preventDefault();
            window.location.href = window.location.pathname;
        });
    });
});
