/*
 * Token / chip multi-select. Upgrades
 *
 *   <input name="tag" data-tokens="/suggest/tags" placeholder="tags…">
 *
 * into a box that holds each chosen value as a removable chip. You pick from the
 * autocomplete (or press Enter) to add one; it stays until you click its ×; the
 * typing area never holds commas, so the search keeps working as you add more.
 *
 * The original <input> stays in the DOM as a hidden field carrying the
 * comma-joined value, so the server sees exactly what it did before. Without JS
 * the field is a plain comma-separated text input — nothing breaks.
 *
 * Relies on suggest.js for the dropdown (window.rewireSuggests).
 */
(function () {
  "use strict";

  function splitValue(v) {
    return (v || "").split(",").map(function (s) { return s.trim(); }).filter(Boolean);
  }

  function upgrade(carrier) {
    if (carrier.dataset.tokensReady) return;
    carrier.dataset.tokensReady = "1";

    var endpoint = carrier.getAttribute("data-tokens");
    var placeholder = carrier.getAttribute("placeholder") || "";
    var tokens = splitValue(carrier.value);

    // The carrier becomes the hidden value holder.
    carrier.type = "hidden";

    var box = document.createElement("div");
    box.className = "tokenfield";
    if (carrier.style.cssText) box.style.cssText = carrier.style.cssText;

    var entry = document.createElement("input");
    entry.type = "text";
    entry.className = "tokenfield-entry";
    entry.setAttribute("placeholder", placeholder);
    entry.setAttribute("autocomplete", "off");
    if (endpoint) entry.setAttribute("data-suggest", endpoint);

    carrier.parentNode.insertBefore(box, carrier.nextSibling);

    function sync() {
      carrier.value = tokens.join(",");
    }

    function render() {
      box.querySelectorAll(".token").forEach(function (el) { el.remove(); });
      tokens.forEach(function (value, i) {
        var chip = document.createElement("span");
        chip.className = "token";
        chip.textContent = value;
        var x = document.createElement("button");
        x.type = "button";
        x.className = "token-x";
        x.setAttribute("aria-label", "Remove " + value);
        x.textContent = "×";
        x.addEventListener("click", function (e) {
          e.stopPropagation();
          tokens.splice(i, 1);
          sync();
          render();
          entry.focus();
        });
        chip.appendChild(x);
        box.insertBefore(chip, entry);
      });
      sync();
    }

    function add(raw) {
      var added = false;
      splitValue(raw).forEach(function (value) {
        var exists = tokens.some(function (t) { return t.toLowerCase() === value.toLowerCase(); });
        if (!exists) { tokens.push(value); added = true; }
      });
      if (added) render();
      return added;
    }

    function datalistHas(value) {
      var listId = entry.getAttribute("list");
      var list = listId && document.getElementById(listId);
      if (!list) return false;
      var needle = value.trim().toLowerCase();
      return Array.prototype.some.call(list.options, function (o) {
        return o.value.toLowerCase() === needle;
      });
    }

    entry.addEventListener("input", function () {
      if (entry.value.indexOf(",") !== -1) {
        add(entry.value);
        entry.value = "";
      } else if (entry.value.trim() && datalistHas(entry.value)) {
        // a pick from the dropdown (or an exact-typed match)
        add(entry.value);
        entry.value = "";
      }
    });

    entry.addEventListener("keydown", function (e) {
      var isEnter = e.key === "Enter" || e.key === "Return" || e.keyCode === 13;
      var isBackspace = e.key === "Backspace" || e.keyCode === 8;
      if (isEnter) {
        if (entry.value.trim()) {
          e.preventDefault();          // add a token, don't submit the form yet
          add(entry.value);
          entry.value = "";
        }
      } else if (isBackspace && entry.value === "" && tokens.length) {
        tokens.pop();
        render();
      }
    });

    entry.addEventListener("blur", function () {
      if (entry.value.trim()) {
        add(entry.value);
        entry.value = "";
      }
    });

    box.addEventListener("mousedown", function (e) {
      if (e.target === box) { e.preventDefault(); entry.focus(); }
    });

    box.appendChild(entry);
    render();

    if (window.rewireSuggests) window.rewireSuggests(box);
  }

  function upgradeAll(root) {
    (root || document).querySelectorAll("input[data-tokens]").forEach(upgrade);
  }

  window.upgradeTokenFields = upgradeAll;
  document.addEventListener("DOMContentLoaded", function () { upgradeAll(document); });
})();
