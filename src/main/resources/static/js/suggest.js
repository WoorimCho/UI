/*
 * Progressive-enhancement autocomplete. Any <input> with a data-suggest
 * attribute gets a <datalist> that is refilled from the matching endpoint as
 * the user types. Without JS the field is still a plain text input and the
 * server resolves whatever name/id was typed.
 *
 *   <input name="prefix" data-suggest="/suggest/tags">
 */
(function () {
  "use strict";

  function debounce(fn, ms) {
    var t;
    return function () {
      var args = arguments, self = this;
      clearTimeout(t);
      t = setTimeout(function () { fn.apply(self, args); }, ms);
    };
  }

  function wire(input) {
    var endpoint = input.getAttribute("data-suggest");
    if (!endpoint || input.getAttribute("list")) return;   // already wired

    var list = document.createElement("datalist");
    list.id = "sg-" + Math.random().toString(36).slice(2);
    input.setAttribute("list", list.id);
    input.setAttribute("autocomplete", "off");
    input.parentNode.insertBefore(list, input.nextSibling);

    var lastQ = null;

    var refresh = debounce(function () {
      var q = input.value.trim();
      if (q === lastQ) return;
      lastQ = q;
      var url = endpoint + (q ? "?q=" + encodeURIComponent(q) : "");
      fetch(url, { headers: { "Accept": "application/json" } })
        .then(function (r) { return r.ok ? r.json() : []; })
        .then(function (names) {
          list.innerHTML = "";
          (names || []).slice(0, 25).forEach(function (n) {
            var o = document.createElement("option");
            o.value = n;
            list.appendChild(o);
          });
        })
        .catch(function () { /* offline / down: leave the field plain */ });
    }, 150);

    input.addEventListener("input", refresh);
    input.addEventListener("focus", refresh);
  }

  // Wire any not-yet-wired data-suggest inputs under `root` (default: document).
  // Forms that clone rows at runtime (the recipe builder) call this after adding one.
  window.rewireSuggests = function (root) {
    (root || document).querySelectorAll("input[data-suggest]").forEach(wire);
  };

  document.addEventListener("DOMContentLoaded", function () {
    window.rewireSuggests(document);
  });
})();
