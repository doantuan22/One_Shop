/*
 * Show / hide password buttons, shared by the login, register, reset-password and account pages.
 *
 * A button is `.pw-toggle[data-toggle=<input id>]` (auth pages) or `.acc-pw__toggle[data-toggle-password=<input id>]`
 * (account page); its two icons are `.i-show` / `.i-hide` or `.icon-show` / `.icon-hide`, and the labels come from
 * `data-show` / `data-hide` or `data-label-show` / `data-label-hide`.
 *
 * - The typed text and the caret stay where they are; a mouse or touch press does not take the focus away from the
 *   field (so a phone keyboard stays open), and a keyboard user keeps the focus on the button.
 * - The label of the button says what the next press does, so no `aria-pressed` is used (the two would contradict).
 * - A visible password is hidden again when the form is really submitted and when the page is left or restored from
 *   the back/forward cache, so it never stays on screen in plain text.
 * - While the field is plain text the browser must not capitalise, "correct" or spell-check the password.
 */
(function () {
    'use strict';

    var BUTTONS = '.pw-toggle[data-toggle], .acc-pw__toggle[data-toggle-password]';
    var pairs = [];

    function attr(el, names) {
        for (var i = 0; i < names.length; i++) {
            var value = el.getAttribute(names[i]);
            if (value) return value;
        }
        return null;
    }

    function render(button, input, visible) {
        var label = attr(button, visible ? ['data-hide', 'data-label-hide'] : ['data-show', 'data-label-show']);
        if (label) {
            button.setAttribute('aria-label', label);
            button.setAttribute('title', label);
        }
        var show = button.querySelector('.i-show, .icon-show');
        var hide = button.querySelector('.i-hide, .icon-hide');
        if (show) show.hidden = visible;
        if (hide) hide.hidden = !visible;
    }

    function setVisible(button, input, visible) {
        if ((input.type === 'text') === visible) return;
        var focused = document.activeElement === input;
        var start = input.selectionStart, end = input.selectionEnd;
        input.type = visible ? 'text' : 'password';
        if (focused) {
            try { input.setSelectionRange(start, end); } catch (e) { /* not supported by this input type */ }
        }
        render(button, input, visible);
    }

    function hideAll() {
        pairs.forEach(function (pair) { setVisible(pair.button, pair.input, false); });
    }

    document.querySelectorAll(BUTTONS).forEach(function (button) {
        var input = document.getElementById(attr(button, ['data-toggle-password', 'data-toggle']));
        if (!input) return;
        pairs.push({button: button, input: input});

        input.setAttribute('autocapitalize', 'off');
        input.setAttribute('autocorrect', 'off');
        input.setAttribute('spellcheck', 'false');
        button.removeAttribute('aria-pressed');
        render(button, input, input.type === 'text');

        // keep the focus in the field while the button is pressed with a mouse or a finger
        button.addEventListener('mousedown', function (event) { event.preventDefault(); });
        button.addEventListener('click', function (event) {
            setVisible(button, input, input.type === 'password');
            if (event.detail > 0 && document.activeElement !== input) {
                input.focus();
                var end = input.value.length;
                try { input.setSelectionRange(end, end); } catch (e) { /* not supported by this input type */ }
            }
        });

        // a form that really goes to the server sends and keeps the password hidden (a refused one stays as it is)
        var form = input.form;
        if (form && !form.hasAttribute('data-pw-toggle-bound')) {
            form.setAttribute('data-pw-toggle-bound', '');
            form.addEventListener('submit', function (event) {
                if (!event.defaultPrevented) hideAll();
            });
        }
    });

    window.addEventListener('pagehide', hideAll);
    window.addEventListener('pageshow', function (event) { if (event.persisted) hideAll(); });
})();
