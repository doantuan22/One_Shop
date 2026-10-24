/*
 * Countdown of the 15-minute payment window.
 *
 * Markup: an element with data-countdown="<seconds left>" (the server computed it, so the browser's clock and time
 * zone never matter). Inside it, [data-countdown-time] shows mm:ss. Anything marked [data-pay-action] is hidden when the
 * time is over, [data-countdown-expired] is shown, and the page reloads once so the server can show the real state
 * (an unpaid order is cancelled and its stock given back when somebody looks at it after the deadline).
 */
(function () {
    'use strict';

    function pad(n) { return (n < 10 ? '0' : '') + n; }

    function format(seconds) {
        var h = Math.floor(seconds / 3600);
        var m = Math.floor((seconds % 3600) / 60);
        var s = seconds % 60;
        return (h > 0 ? h + ':' + pad(m) : pad(m)) + ':' + pad(s);
    }

    document.querySelectorAll('[data-countdown]').forEach(function (box) {
        var left = parseInt(box.getAttribute('data-countdown'), 10);
        if (!isFinite(left)) return;
        var started = Date.now();
        var total = left;
        var timeEls = box.querySelectorAll('[data-countdown-time]');
        var scope = box.closest('[data-countdown-scope]') || document;
        var reloaded = false;

        function render() {
            var remaining = Math.max(0, total - Math.floor((Date.now() - started) / 1000));
            timeEls.forEach(function (el) { el.textContent = format(remaining); });
            box.classList.toggle('is-urgent', remaining > 0 && remaining <= 120);
            if (remaining === 0) {
                box.classList.add('is-expired');
                scope.querySelectorAll('[data-pay-action]').forEach(function (el) { el.hidden = true; });
                scope.querySelectorAll('[data-countdown-expired]').forEach(function (el) { el.hidden = false; });
                if (!reloaded) {
                    reloaded = true;
                    setTimeout(function () { window.location.reload(); }, 2500);
                }
                return false;
            }
            return true;
        }

        if (render()) {
            var timer = setInterval(function () { if (!render()) clearInterval(timer); }, 1000);
        }
    });
})();
