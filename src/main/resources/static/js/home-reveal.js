// Home page: the product cards slide in from the right once, in turn, when the block enters the viewport.
// Loaded in <head> (not deferred) so that the hidden state is in place before the first paint; every failure path
// leaves the cards visible: no IntersectionObserver, reduced motion, or an error all end with no class on <html>.
(function () {
    'use strict';
    var root = document.documentElement;
    var STAGGER_MS = 80;

    try {
        if (!('IntersectionObserver' in window)) {
            return;
        }
        if (window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
            return;
        }
        root.classList.add('reveal-ready');
    } catch (e) {
        return;
    }

    function start() {
        try {
            var items = Array.prototype.slice.call(document.querySelectorAll('[data-reveal]'));
            if (items.length === 0) {
                root.classList.remove('reveal-ready');
                return;
            }
            var observer = new IntersectionObserver(function (entries) {
                // the entries that appear together are staggered, left to right / top to bottom
                var visible = entries.filter(function (entry) { return entry.isIntersecting; })
                    .map(function (entry) { return entry.target; })
                    .sort(function (a, b) { return items.indexOf(a) - items.indexOf(b); });
                visible.forEach(function (el, index) {
                    el.style.transitionDelay = (index * STAGGER_MS) + 'ms';
                    el.classList.add('is-revealed');
                    observer.unobserve(el);
                });
            }, { threshold: 0.12, rootMargin: '0px 0px -5% 0px' });
            items.forEach(function (el) { observer.observe(el); });
        } catch (e) {
            root.classList.remove('reveal-ready');
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', start);
    } else {
        start();
    }
})();
