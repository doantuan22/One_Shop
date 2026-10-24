(function () {
    'use strict';

    function validateFileInput(input) {
        if (!input || !input.hasAttribute('data-max-bytes')) return true;
        const maxBytes = parseInt(input.getAttribute('data-max-bytes'), 10);
        if (isNaN(maxBytes) || maxBytes <= 0) return true;

        const maxSizeLabel = input.getAttribute('data-max-size') || '5MB';
        let errorEl = input.closest('.mb-3, .input-group, form')?.querySelector('.upload-file-error');
        if (!errorEl) {
            errorEl = input.parentElement.querySelector('.upload-file-error');
        }

        if (input.files && input.files[0]) {
            const file = input.files[0];
            if (file.size > maxBytes) {
                if (errorEl) {
                    errorEl.textContent = 'Ảnh vượt quá ' + maxSizeLabel + '. Vui lòng chọn tệp nhỏ hơn.';
                    errorEl.style.display = 'block';
                }
                input.value = '';
                input.setCustomValidity('Ảnh vượt quá dung lượng cho phép.');
                return false;
            }
        }

        if (errorEl) {
            errorEl.textContent = '';
            errorEl.style.display = 'none';
        }
        input.setCustomValidity('');
        return true;
    }

    document.addEventListener('DOMContentLoaded', function () {
        document.addEventListener('change', function (e) {
            if (e.target && e.target.tagName === 'INPUT' && e.target.type === 'file' && e.target.hasAttribute('data-max-bytes')) {
                validateFileInput(e.target);
            }
        });

        document.addEventListener('submit', function (e) {
            const form = e.target;
            const fileInputs = form.querySelectorAll('input[type="file"][data-max-bytes]');
            for (let i = 0; i < fileInputs.length; i++) {
                if (!validateFileInput(fileInputs[i])) {
                    e.preventDefault();
                    e.stopPropagation();
                    return false;
                }
            }
        });
    });
})();

