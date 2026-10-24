/*
 * Custom dropdown list for single-choice <select> elements.
 * The <select> itself stays the visible control (so every page keeps its own look, value, validation, labels and
 * "change" handlers); only the browser's native popup is replaced by a styled list.
 * Opt out with data-native. Multi / size>1 selects are left alone.
 */
(function () {
    'use strict';

    var openState = null;

    function closeOpen(restoreFocus) {
        if (!openState) { return; }
        var s = openState;
        openState = null;
        s.panel.remove();
        s.select.removeAttribute('aria-expanded');
        document.removeEventListener('mousedown', s.onDoc, true);
        window.removeEventListener('resize', s.onClose);
        window.removeEventListener('scroll', s.onScroll, true);
        if (restoreFocus) { s.select.focus(); }
    }

    function choose(select, index) {
        if (select.selectedIndex !== index) {
            select.selectedIndex = index;
            select.dispatchEvent(new Event('input', { bubbles: true }));
            select.dispatchEvent(new Event('change', { bubbles: true }));
        }
        closeOpen(true);
    }

    function open(select) {
        if (select.disabled) { return; }
        closeOpen(false);
        var panel = document.createElement('ul');
        panel.className = 'os-select__panel';
        panel.setAttribute('role', 'listbox');
        var items = [];
        var active = select.selectedIndex;
        Array.prototype.forEach.call(select.options, function (opt, i) {
            var li = document.createElement('li');
            li.className = 'os-select__option';
            li.setAttribute('role', 'option');
            li.textContent = opt.text;
            if (opt.disabled) { li.classList.add('is-disabled'); li.setAttribute('aria-disabled', 'true'); }
            if (i === select.selectedIndex) { li.classList.add('is-selected'); li.setAttribute('aria-selected', 'true'); }
            li.addEventListener('mousedown', function (e) { e.preventDefault(); });
            li.addEventListener('click', function () { if (!opt.disabled) { choose(select, i); } });
            li.addEventListener('mousemove', function () { setActive(i); });
            panel.appendChild(li);
            items.push(li);
        });

        function setActive(i) {
            if (i < 0 || i >= items.length) { return; }
            if (items[active]) { items[active].classList.remove('is-active'); }
            active = i;
            items[i].classList.add('is-active');
            items[i].scrollIntoView({ block: 'nearest' });
        }

        document.body.appendChild(panel);
        var r = select.getBoundingClientRect();
        var below = window.innerHeight - r.bottom;
        var maxH = 280;
        panel.style.minWidth = r.width + 'px';
        panel.style.left = Math.max(8, Math.min(r.left, window.innerWidth - Math.max(r.width, panel.offsetWidth) - 8)) + 'px';
        var h = Math.min(panel.scrollHeight, maxH);
        if (below < h + 12 && r.top > below) {
            panel.style.bottom = (window.innerHeight - r.top + 6) + 'px';
            panel.style.maxHeight = Math.min(maxH, r.top - 16) + 'px';
        } else {
            panel.style.top = (r.bottom + 6) + 'px';
            panel.style.maxHeight = Math.min(maxH, below - 16) + 'px';
        }
        select.setAttribute('aria-expanded', 'true');

        var state = {
            panel: panel, select: select,
            onDoc: function (e) { if (!panel.contains(e.target) && e.target !== select) { closeOpen(false); } },
            onClose: function () { closeOpen(false); },
            onScroll: function (e) { if (!panel.contains(e.target)) { closeOpen(false); } },
            setActive: setActive,
            getActive: function () { return active; }
        };
        openState = state;
        document.addEventListener('mousedown', state.onDoc, true);
        window.addEventListener('resize', state.onClose);
        window.addEventListener('scroll', state.onScroll, true);
        setActive(select.selectedIndex < 0 ? 0 : select.selectedIndex);
    }

    function eligible(el) {
        return el && el.tagName === 'SELECT' && !el.multiple && el.size <= 1 && !el.hasAttribute('data-native');
    }

    var typed = '';
    var typedTimer = null;

    document.addEventListener('mousedown', function (e) {
        var select = e.target;
        if (!eligible(select) || e.button !== 0) { return; }
        e.preventDefault(); // no native popup
        select.focus();
        if (openState && openState.select === select) { closeOpen(false); } else { open(select); }
    }, true);

    document.addEventListener('keydown', function (e) {
        var select = e.target;
        if (!eligible(select)) { return; }
        var isOpen = openState && openState.select === select;
        var key = e.key;
        if (key === 'ArrowDown' || key === 'ArrowUp') {
            e.preventDefault();
            if (!isOpen) { open(select); return; }
            var dir = key === 'ArrowDown' ? 1 : -1;
            var i = openState.getActive();
            do { i += dir; } while (i >= 0 && i < select.options.length && select.options[i].disabled);
            openState.setActive(i);
        } else if (key === 'Home' || key === 'End') {
            if (isOpen) { e.preventDefault(); openState.setActive(key === 'Home' ? 0 : select.options.length - 1); }
        } else if (key === 'Enter' || key === ' ') {
            if (isOpen) { e.preventDefault(); choose(select, openState.getActive()); }
            else if (key === ' ') { e.preventDefault(); open(select); }
        } else if (key === 'Escape') {
            if (isOpen) { e.preventDefault(); closeOpen(true); }
        } else if (key === 'Tab') {
            closeOpen(false);
        } else if (key.length === 1 && !e.ctrlKey && !e.metaKey && !e.altKey) {
            typed += key.toLowerCase();
            clearTimeout(typedTimer);
            typedTimer = setTimeout(function () { typed = ''; }, 600);
            for (var j = 0; j < select.options.length; j++) {
                if (!select.options[j].disabled && select.options[j].text.toLowerCase().indexOf(typed) === 0) {
                    e.preventDefault();
                    if (isOpen) { openState.setActive(j); } else { choose(select, j); }
                    break;
                }
            }
        }
    }, true);
})();
