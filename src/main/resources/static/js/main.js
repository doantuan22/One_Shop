// Shared behaviour for every page. SiteMesh places this script after the page content,
// so page-specific scripts should wait for DOMContentLoaded before touching the DOM.
document.addEventListener('DOMContentLoaded', function () {
    // Capture errors from images (including lazy-loaded ones) and recover failures that happened before this
    // script loaded. Apply once only: a missing fallback must never create an endless network-error loop.
    var placeholder = document.querySelector('meta[name="os-image-placeholder"]');
    var fallback = placeholder && placeholder.content;
    function recoverImage(image) {
        if (!fallback || !(image instanceof HTMLImageElement) || image.dataset.osFallbackApplied === 'true') return;
        image.dataset.osFallbackApplied = 'true';
        image.removeAttribute('srcset');
        image.src = fallback;
    }
    document.addEventListener('error', function (event) { recoverImage(event.target); }, true);
    document.querySelectorAll('img').forEach(function (image) {
        if (image.complete && image.naturalWidth === 0 && image.getAttribute('src')) recoverImage(image);
    });

    // Auto-dismiss Bootstrap alerts marked with data-auto-dismiss (milliseconds).
    document.querySelectorAll('.alert[data-auto-dismiss]').forEach(function (alert) {
        var delay = parseInt(alert.getAttribute('data-auto-dismiss'), 10) || 4000;
        setTimeout(function () {
            bootstrap.Alert.getOrCreateInstance(alert).close();
        }, delay);
    });

    // Forms marked with data-confirm ask before they are submitted (for example removing a cart line).
    document.querySelectorAll('form[data-confirm]').forEach(function (form) {
        form.addEventListener('submit', function (event) {
            if (!window.confirm(form.getAttribute('data-confirm'))) {
                event.preventDefault();
            }
        });
    });
});
