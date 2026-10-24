/* Shared modal behaviour for the Category and Brand admin forms. */
document.addEventListener('DOMContentLoaded', function () {
    'use strict';

    var autoOpen = document.querySelector('[data-os-open-modal]');
    if (autoOpen && window.bootstrap) {
        var modalElement = document.getElementById(autoOpen.getAttribute('data-os-open-modal'));
        if (modalElement) {
            bootstrap.Modal.getOrCreateInstance(modalElement).show();
        }
    }

    document.querySelectorAll('[data-image-preview-input]').forEach(function (input) {
        input.addEventListener('change', function () {
            var file = input.files && input.files[0];
            if (!file || !file.type.startsWith('image/')) return;

            var scope = input.closest('.os-modal-image-tools') || input.closest('form');
            if (!scope) return;
            var previews = scope.querySelectorAll('[data-image-preview]');
            var preview = previews[previews.length - 1];
            if (!preview) return;

            var reader = new FileReader();
            reader.addEventListener('load', function () {
                preview.src = String(reader.result);
                preview.classList.remove('d-none');
            });
            reader.readAsDataURL(file);
        });
    });

    document.querySelectorAll('form[data-os-save-form]').forEach(function (form) {
        form.addEventListener('submit', function (event) {
            if (form.dataset.osSubmitting === 'true') {
                event.preventDefault();
                return;
            }
            form.dataset.osSubmitting = 'true';
            var submitter = event.submitter || document.querySelector('[form="' + form.id + '"][type="submit"]');
            if (submitter) {
                submitter.disabled = true;
                submitter.setAttribute('aria-busy', 'true');
            }
        });
    });
});
