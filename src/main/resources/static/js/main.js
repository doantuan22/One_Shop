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

    initCartSelection();
});

// Cart page: "Chọn cả chi nhánh" checks or unchecks the lines of its Store, and reflects them back
// (checked / unchecked / partly). Display only: which lines may be bought is decided by the server.
function initCartSelection() {
    var groupToggles = document.querySelectorAll('.os-cart-group-toggle');
    if (groupToggles.length === 0) {
        return;
    }

    function itemsOf(storeId) {
        return Array.prototype.slice.call(
            document.querySelectorAll('.os-cart-item-toggle[data-store-id="' + storeId + '"]:not(:disabled)'));
    }

    function syncGroup(groupToggle) {
        var items = itemsOf(groupToggle.getAttribute('data-store-id'));
        var checked = items.filter(function (item) { return item.checked; }).length;
        groupToggle.checked = items.length > 0 && checked === items.length;
        groupToggle.indeterminate = checked > 0 && checked < items.length;
    }

    groupToggles.forEach(function (groupToggle) {
        var storeId = groupToggle.getAttribute('data-store-id');
        groupToggle.addEventListener('change', function () {
            itemsOf(storeId).forEach(function (item) { item.checked = groupToggle.checked; });
            syncGroup(groupToggle);
        });
        itemsOf(storeId).forEach(function (item) {
            item.addEventListener('change', function () { syncGroup(groupToggle); });
        });
        syncGroup(groupToggle);
    });
}
