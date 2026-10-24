/*
 * Search-box suggestions for any <input data-suggest-url="...">.
 * The URL returns [{ "sku": "...", "name": "..." }]. Typing filters the list by SKU and name (every word, any
 * order, accent-insensitive). Picking an item fills the box and submits the form; with no pick, Enter submits the
 * typed words and the server filters the result the same way.
 *
 * data-suggest-label="name" | "sku-name"   what each suggestion shows (default sku-name)
 * data-suggest-fill="name" | "sku"         what a pick puts in the box (default sku)
 */
(function () {
    'use strict';

    var MAX_ITEMS = 8;
    var cache = {};

    function fold(text) {
        return String(text).normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/đ/g, 'd').replace(/Đ/g, 'D').toLowerCase();
    }

    function appendHighlighted(target, text, words) {
        var folded = Array.prototype.map.call(text, function (ch) { return fold(ch); }).join('');
        if (folded.length !== text.length || !words.length) { target.textContent = text; return; }
        var mark = new Array(text.length + 1).join('0').split('');
        words.forEach(function (w) {
            var from = 0;
            var at;
            while ((at = folded.indexOf(w, from)) !== -1) {
                for (var i = at; i < at + w.length; i += 1) { mark[i] = '1'; }
                from = at + w.length;
            }
        });
        var i = 0;
        while (i < text.length) {
            var on = mark[i] === '1';
            var j = i;
            while (j < text.length && (mark[j] === '1') === on) { j += 1; }
            var piece = text.slice(i, j);
            if (on) {
                var m = document.createElement('mark');
                m.textContent = piece;
                target.appendChild(m);
            } else {
                target.appendChild(document.createTextNode(piece));
            }
            i = j;
        }
    }

    function load(url) {
        if (!cache[url]) {
            cache[url] = fetch(url, { headers: { 'Accept': 'application/json' }, credentials: 'same-origin' })
                .then(function (r) { return r.ok ? r.json() : []; })
                .then(function (rows) {
                    return rows.map(function (o) { return { sku: o.sku, name: o.name, key: fold(o.sku + ' ' + o.name) }; });
                })
                .catch(function () { return []; });
        }
        return cache[url];
    }

    function attach(input) {
        var url = input.getAttribute('data-suggest-url');
        var label = input.getAttribute('data-suggest-label') || 'sku-name';
        var fill = input.getAttribute('data-suggest-fill') || 'sku';
        var list = document.createElement('ul');
        var shown = [];
        var active = -1;
        var options = [];
        var seq = 'os-suggest-' + Math.random().toString(36).slice(2, 8);

        var pill = !!(input.form && input.form.classList.contains('home-search__form'));
        list.className = 'os-suggest-pop' + (pill ? ' os-suggest-pop--pill' : '');
        list.id = seq;
        list.setAttribute('role', 'listbox');
        list.hidden = true;
        document.body.appendChild(list);
        input.setAttribute('role', 'combobox');
        input.setAttribute('aria-autocomplete', 'list');
        input.setAttribute('aria-expanded', 'false');
        input.setAttribute('aria-controls', seq);
        input.setAttribute('autocomplete', 'off');

        function place() {
            var r = input.getBoundingClientRect();
            var form = input.closest('form');
            var anchor = input.closest('[data-suggest-anchor]');
            var box = anchor ? anchor.getBoundingClientRect()
                : form && form.classList.contains('home-search__form') ? form.getBoundingClientRect() : r;
            // narrow filter inputs still get a readable list (at least 26rem), kept inside the viewport
            var width = Math.min(Math.max(box.width, 416), document.documentElement.clientWidth - 16);
            var left = Math.min(Math.max(8, box.left), document.documentElement.clientWidth - width - 8);
            list.style.left = Math.round(left) + 'px';
            list.style.top = Math.round(box.bottom + 6) + 'px';
            list.style.width = Math.round(width) + 'px';
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
            var items = list.querySelectorAll('.os-suggest-pop__item');
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
            input.value = fill === 'name' ? option.name : option.sku;
            close();
            if (input.form) { input.form.submit(); }
        }

        function render() {
            var words = fold(input.value).split(/\s+/).filter(Boolean);
            list.innerHTML = '';
            if (!words.length) { close(); return; }
            var found = options.filter(function (o) {
                return words.every(function (w) { return o.key.indexOf(w) !== -1; });
            });
            shown = found.slice(0, MAX_ITEMS);
            shown.forEach(function (o, i) {
                var li = document.createElement('li');
                li.id = seq + '-' + i;
                li.className = 'os-suggest-pop__item';
                li.setAttribute('role', 'option');
                if (pill) {
                    var icon = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
                    icon.setAttribute('class', 'os-suggest-pop__icon');
                    icon.setAttribute('width', '18');
                    icon.setAttribute('height', '18');
                    icon.setAttribute('viewBox', '0 0 24 24');
                    icon.setAttribute('fill', 'none');
                    icon.setAttribute('stroke', 'currentColor');
                    icon.setAttribute('stroke-width', '2');
                    icon.setAttribute('stroke-linecap', 'round');
                    icon.setAttribute('aria-hidden', 'true');
                    icon.innerHTML = '<circle cx="11" cy="11" r="7"></circle><line x1="21" y1="21" x2="16.65" y2="16.65"></line>';
                    li.appendChild(icon);
                }
                if (label === 'sku-name') {
                    var sku = document.createElement('span');
                    sku.className = 'os-suggest-pop__sku';
                    sku.textContent = o.sku;
                    li.appendChild(sku);
                }
                var name = document.createElement('span');
                name.className = 'os-suggest-pop__name';
                appendHighlighted(name, o.name, words);
                li.appendChild(name);
                li.addEventListener('mousedown', function (e) { e.preventDefault(); choose(o); });
                list.appendChild(li);
            });
            if (!shown.length || found.length > shown.length) {
                var note = document.createElement('li');
                note.className = 'os-suggest-pop__note';
                note.textContent = shown.length
                    ? 'Còn ' + (found.length - shown.length) + ' kết quả khác, nhập thêm từ để thu hẹp.'
                    : 'Không có gợi ý khớp. Nhấn Enter để tìm theo từ đã nhập.';
                list.appendChild(note);
            }
            place();
            list.hidden = false;
            input.setAttribute('aria-expanded', 'true');
            active = -1;
        }

        input.addEventListener('focus', function () {
            load(url).then(function (rows) { options = rows; if (input.value.trim()) { render(); } });
        });
        input.addEventListener('input', function () {
            load(url).then(function (rows) { options = rows; render(); });
        });
        input.addEventListener('blur', close);
        input.addEventListener('keydown', function (e) {
            if (list.hidden) { return; }
            if (e.key === 'ArrowDown') { e.preventDefault(); highlight(Math.min(active + 1, shown.length - 1)); }
            else if (e.key === 'ArrowUp') { e.preventDefault(); highlight(Math.max(active - 1, 0)); }
            else if (e.key === 'Enter' && active >= 0) { e.preventDefault(); choose(shown[active]); }
            else if (e.key === 'Escape') { e.preventDefault(); close(); }
        });
        window.addEventListener('resize', close);
        window.addEventListener('scroll', function () { if (!list.hidden) { place(); } }, true);
    }

    function init() {
        Array.prototype.forEach.call(document.querySelectorAll('input[data-suggest-url]'), attach);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
