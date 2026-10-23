/*
 * Product search box with suggestions (admin/store-products).
 * Typing filters the suggestion list by SKU and name (every word, any order, accent-insensitive).
 * Picking a suggestion fills the box with "SKU - name", stores its id in the hidden productId field and submits.
 * Editing the text clears productId, so the server filters by the typed words instead.
 */
(function () {
    'use strict';

    var root = document.querySelector('[data-product-search]');
    if (!root) { return; }

    var input = root.querySelector('[data-product-search-input]');
    var hidden = root.querySelector('[data-product-search-id]');
    var list = root.querySelector('[data-product-search-list]');
    var options = Array.prototype.map.call(root.querySelectorAll('[data-product-option]'), function (el) {
        var sku = el.getAttribute('data-sku');
        var name = el.getAttribute('data-name');
        return { id: el.getAttribute('data-id'), sku: sku, name: name, label: sku + ' - ' + name, key: fold(sku + ' ' + name) };
    });
    var MAX_ITEMS = 8;
    var active = -1;
    var shown = [];

    function fold(text) {
        return String(text).normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/đ/g, 'd').replace(/Đ/g, 'D').toLowerCase();
    }

    function matches(text) {
        var words = fold(text).split(/\s+/).filter(Boolean);
        if (!words.length) { return []; }
        return options.filter(function (o) {
            return words.every(function (w) { return o.key.indexOf(w) !== -1; });
        });
    }

    function close() {
        list.hidden = true;
        list.innerHTML = '';
        input.setAttribute('aria-expanded', 'false');
        input.removeAttribute('aria-activedescendant');
        active = -1;
        shown = [];
    }

    function highlight(index) {
        var items = list.children;
        for (var i = 0; i < items.length; i += 1) {
            items[i].classList.toggle('active', i === index);
            items[i].setAttribute('aria-selected', i === index ? 'true' : 'false');
        }
        active = index;
        if (index >= 0 && items[index]) {
            input.setAttribute('aria-activedescendant', items[index].id);
            items[index].scrollIntoView({ block: 'nearest' });
        }
    }

    function choose(option) {
        input.value = option.label;
        hidden.value = option.id;
        close();
        input.form.submit();
    }

    function render() {
        var found = matches(input.value);
        shown = found.slice(0, MAX_ITEMS);
        list.innerHTML = '';
        if (!input.value.trim()) { close(); return; }
        shown.forEach(function (o, i) {
            var li = document.createElement('li');
            li.id = 'sp-option-' + i;
            li.setAttribute('role', 'option');
            li.className = 'os-suggest__item';
            var sku = document.createElement('span');
            sku.className = 'os-suggest__sku';
            sku.textContent = o.sku;
            var name = document.createElement('span');
            name.textContent = o.name;
            li.appendChild(sku);
            li.appendChild(name);
            li.addEventListener('mousedown', function (e) { e.preventDefault(); choose(o); });
            list.appendChild(li);
        });
        if (!shown.length) {
            var empty = document.createElement('li');
            empty.className = 'os-suggest__empty';
            empty.textContent = 'Không có sản phẩm khớp. Nhấn Áp dụng để lọc theo từ đã nhập.';
            list.appendChild(empty);
        } else if (found.length > shown.length) {
            var more = document.createElement('li');
            more.className = 'os-suggest__empty';
            more.textContent = 'Còn ' + (found.length - shown.length) + ' sản phẩm khác, nhập thêm từ để thu hẹp.';
            list.appendChild(more);
        }
        list.hidden = false;
        input.setAttribute('aria-expanded', 'true');
        active = -1;
    }

    input.addEventListener('input', function () {
        hidden.value = '';
        render();
    });
    input.addEventListener('focus', function () { if (input.value.trim() && !hidden.value) { render(); } });
    input.addEventListener('blur', function () { close(); });
    input.addEventListener('keydown', function (e) {
        if (list.hidden) { return; }
        if (e.key === 'ArrowDown') { e.preventDefault(); highlight(Math.min(active + 1, shown.length - 1)); }
        else if (e.key === 'ArrowUp') { e.preventDefault(); highlight(Math.max(active - 1, 0)); }
        else if (e.key === 'Enter' && active >= 0) { e.preventDefault(); choose(shown[active]); }
        else if (e.key === 'Escape') { e.preventDefault(); close(); }
    });
})();
