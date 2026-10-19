// Shared behaviour for every page. SiteMesh places this script after the page content,
// so page-specific scripts should wait for DOMContentLoaded before touching the DOM.
document.addEventListener('DOMContentLoaded', function () {
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
