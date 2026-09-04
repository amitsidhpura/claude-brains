  /* ---------- JS -> Kotlin bridge ---------- */
  function bridge(obj) { if (window.__bridge) window.__bridge(JSON.stringify(obj)); }
  window.respondPermission = function (id, allow, suggIdxs, text) {
    // text: the plan card's typed feedback — rides EVERY decision (deny → the message field,
    // allow → steered in right after the response). Empty string keeps today's behavior.
    bridge({ kind: 'perm', id: id, allow: allow ? 'true' : 'false',
             sugg: (suggIdxs && suggIdxs.length) ? suggIdxs.join(',') : '-1',
             text: text || '' });
  };

  /* ---------- dropdown system (mode / model / slash / attach) ---------- */
  const MENUS = ['modeMenu', 'modelMenu', 'slashMenu', 'attachMenu', 'bgMenu'];
  // Escape on the slash menu must STICK: the composer's input handler re-asserts .show on
  // every keystroke while the text still looks like "/cmd", so removing the class alone
  // lasts exactly one keypress. Re-armed when the text leaves the /-shape, when the slash
  // button opens the menu explicitly, and when the composer is cleared.
  let slashEscaped = false;
  function menuEl(id) { return document.getElementById(id); }
  function tg(id, e) {
    if (e) e.stopPropagation();
    hideMenu();   // composer popups and the @-mention menu are one exclusive layer
    MENUS.forEach(function (x) {
      menuEl(x).classList.toggle('show', x === id && !menuEl(x).classList.contains('show'));
    });
    const p = menuEl(id);
    if (!p.classList.contains('show')) return;
    if (id === 'slashMenu') {
      slashEscaped = false;                     // explicit open overrides an earlier Escape
      renderSlash(input.value.charAt(0) === '/' ? input.value.slice(1) : '');
      capToRows(slashList, 5, '.popup-item');   // .show was set above, so rows measure
      input.focus();
    } else if (id === 'modelMenu') {
      if (modelSearch) modelSearch.value = '';   // fresh, unfiltered each open
      renderModels(); syncModelFooter();
      const cur = p.querySelector('.popup-item.on') || p.querySelector('.popup-item');
      if (cur) cur.classList.add('sel');
      if (modelSearch) modelSearch.focus();       // type to filter or enter a model straight away
    } else {
      const items = Array.prototype.slice.call(p.querySelectorAll('.popup-item'));
      items.forEach(function (x) { x.classList.remove('sel'); });
      // A `nosel` popup is a status readout, not a picker (the background roster): seeding a cursor
      // on open advertises a selection that can never be acted on.
      if (p.classList.contains('nosel')) return;
      const cur = p.querySelector('.popup-item.on') || items[0];
      if (cur) cur.classList.add('sel');
    }
  }
  function activeMenu() {
    for (let i = 0; i < MENUS.length; i++) if (menuEl(MENUS[i]).classList.contains('show')) return menuEl(MENUS[i]);
    return null;
  }
  function closeMenus() { MENUS.forEach(function (x) { menuEl(x).classList.remove('show'); }); }
  // Permission-card split-button menus are transient DOM (one per live card), so they are
  // found by class rather than living in MENUS like the fixed composer popups.
  function closeCardMenus() {
    Array.prototype.forEach.call(document.querySelectorAll('.card-menu.show'),
      function (m) { m.classList.remove('show'); m.parentNode.classList.remove('open'); });
  }

  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape') {
      if (lightbox.classList.contains('show')) { lightbox.classList.remove('show'); return; }
      // card menus live OUTSIDE MENUS (transient DOM per permission card), so activeMenu()
      // can't see them — without this rung Escape skips them entirely
      if (document.querySelector('.card-menu.show')) { closeCardMenus(); return; }
      const m = activeMenu();
      if (m) { if (m.id === 'slashMenu') slashEscaped = true; m.classList.remove('show'); return; }
      if (histPanel.classList.contains('show')) { histPanel.classList.remove('show'); return; }
      if (sidePanel.classList.contains('show')) { sideClose(); return; }
      if (activeAsk) { cancelAsk(); return; }
      return;
    }
    const p = activeMenu(); if (!p) return;
    if (p.classList.contains('nosel')) return;   // nothing to select, and Enter would "click" a dead row
    const items = Array.prototype.slice.call(p.querySelectorAll('.popup-item'));
    if (!items.length) return;
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      e.preventDefault();
      let i = items.findIndex(function (x) { return x.classList.contains('sel'); });
      i = (i + (e.key === 'ArrowDown' ? 1 : -1) + items.length) % items.length;
      items.forEach(function (x, n) { x.classList.toggle('sel', n === i); });
      items[i].scrollIntoView({ block: 'nearest' });
    } else if (e.key === 'Enter') {
      const s = p.querySelector('.popup-item.sel');
      if (s) { e.preventDefault(); s.click(); }
    }
  });
  document.addEventListener('mouseover', function (e) {
    const item = e.target.closest('.popup-item');
    if (!item) return;
    const p = item.closest('.popup');
    if (!p || !p.classList.contains('show') || item.classList.contains('sel')) return;
    // `sel` is a keyboard CURSOR, and this painter has no mouseout counterpart — it only ever moves
    // the class, never clears it. Correct for a picker (hover moves the cursor so Enter acts on what
    // you point at); wrong for a status readout, where the highlight stuck to the last row the
    // pointer crossed. Reported 2026-08-15 on the background roster.
    if (p.classList.contains('nosel')) return;
    p.querySelectorAll('.popup-item').forEach(function (x) { x.classList.remove('sel'); });
    item.classList.add('sel');
  });
  document.addEventListener('click', function (e) {
    if (!e.target.closest('#histPanel') && !e.target.closest('#histBtn')) histPanel.classList.remove('show');
    // Clicks into the textarea RE-EVALUATE both text-driven menus (button popups close like
    // any outside click first — the order matters, closeMenus() would kill a menu the
    // re-evaluation just opened). updateAuto recomputes the @-token at the new caret,
    // slashAuto the whole-line /-shape; both respect their Escape flags. Any other outside
    // click closes the @-menu.
    if (e.target === input) {
      closeCardMenus(); closeMenus();
      updateAuto(); slashAuto();
      return;
    }
    if (!e.target.closest('#mention')) hideMenu();
    if (e.target.closest('.popup')) return;
    closeCardMenus();
    closeMenus();
  });
  // An outside click DISCARDS the rename, like Escape and ✕ — never commits, since a stray click
  // would append a custom-title record with no way back.
  //
  // CAPTURE, unlike every other dismissal in this file, and the one place the popup idiom does not
  // fit: the controls nearest the title (#histBtn, the model/mode chips, the effort dots) all
  // stopPropagation in their own handlers, so their clicks never reach the bubbling listener above
  // — the editor would survive exactly the clicks most likely to follow it. Capture runs before any
  // target handler, so no stopPropagation can hide a click from this. Excluding #convTitle is what
  // keeps the opening click from closing the editor it just opened.
  document.addEventListener('click', function (e) {
    if (!e.target.closest('#convEdit') && !e.target.closest('#convTitle')) endRename();
  }, true);
  lightbox.addEventListener('click', function (e) {
    if (e.target === lightbox || e.target.closest('.lb-x')) lightbox.classList.remove('show');
  });
  function openLB(src, name) {
    document.getElementById('lbImg').src = src;
    document.getElementById('lbCap').textContent = name || '';
    lightbox.classList.add('show');
  }

  /* ---------- mode picker ---------- */
  let currentMode = 'manual';   // no effort suffix since 2026-08-26 — see setModelChip
  function syncModeUI() {
    // the composer's focus ring and Send button follow the mode — see #app[data-mode] in css/00-tokens.css
    document.getElementById('app').dataset.mode = currentMode;
    // Don't ask (4.7) is DISPLAYED, never offered — VS Code's rule exactly (its picker unshifts
    // dontAsk only while it IS the current mode). The row exists so the unknown-mode guard in
    // applyCliMode accepts the CLI's broadcast, and it hides the moment any other mode is current,
    // so the menu never hands out a mode the user can only enter from their settings
    // (`permissions.defaultMode`, honoured on first run when nothing is persisted — no __mode push).
    const dontAsk = modeItemsEl.querySelector('.popup-item[data-v="dontAsk"]');
    if (dontAsk) dontAsk.hidden = currentMode !== 'dontAsk';
    Array.prototype.forEach.call(modeItemsEl.querySelectorAll('.popup-item'), function (item) {
      item.classList.toggle('on', item.dataset.v === currentMode);
    });
    const cur = modeItemsEl.querySelector('.popup-item.on');
    // the mode NAME only: the effort level used to trail it as "(High)", but the slider moved
    // into the model menu's footer on 2026-08-26 and the level is no longer mirrored onto ANY
    // chip — it shows on the popup's own "Effort <b>" label. See setModelChip.
    if (cur) modeChip.innerHTML = cur.querySelector('.pi-ic').innerHTML + ' ' +
      esc(cur.querySelector('.pi-title').textContent);
  }
  Array.prototype.forEach.call(modeItemsEl.querySelectorAll('.popup-item'), function (item) {
    item.addEventListener('click', function () {
      if (item.dataset.v === currentMode) { closeMenus(); return; }  // re-pick = no-op
      currentMode = item.dataset.v;
      syncModeUI();
      bridge({ kind: 'mode', mode: currentMode });
      closeMenus();
    });
  });
  modeChip.onclick = function (e) { tg('modeMenu', e); };
  syncModeUI();
  // The CLI is the authority on the mode: __mode seeds the persisted choice at startup, and
  // system/init + system/status broadcast every change — including ones the CLI makes itself
  // (approving a plan RESTORES prePlanMode, the mode from before plan mode was entered, falling
  // back to default when none was recorded or the restored mode is gated — 2.1.233; older CLIs
  // always dropped to default). Without this the chip lies.
  // A plan-card mode row cannot switch modes immediately: the CLI restores prePlanMode when the
  // approved ExitPlanMode executes, which is always AFTER our set_permission_mode arrives — the
  // restore clobbered an immediate switch every time (measured live 2026-08-16). So the row
  // parks the wish here, and the CLI's own post-approval broadcast (plan → X is always a change,
  // so it always fires) is what sends it — after the restore, so it sticks, and before the first
  // implementation edit, so it covers the turn it was chosen for.
  let pendingPlanMode = null;
  function applyCliMode(m) {
    // 'default' is the CLI's internal name for Manual ('manual' merely replaced it as the
    // ADVERTISED name) — and it is what the plan-exit restore broadcasts (measured 2026-08-16:
    // manual → plan → approve broadcast permissionMode='default'). Without the alias the
    // unknown-mode guard below dropped it and the chip stayed on Plan while the real mode was
    // manual — the exact lie the guard exists to prevent.
    if (m === 'default') m = 'manual';
    if (m && m !== currentMode && modeItemsEl.querySelector('.popup-item[data-v="' + m + '"]')) {
      currentMode = m; syncModeUI();   // unknown modes keep the chip
    }
    if (pendingPlanMode) {
      const want = pendingPlanMode; pendingPlanMode = null;
      if (want !== currentMode) { currentMode = want; syncModeUI(); bridge({ kind: 'mode', mode: want }); }
    }
  }

  /* ---------- model picker ---------- */
  let models = [], currentModel = null;
  let customModels = [];   // user-defined, persisted via Kotlin PropertiesComponent (see the search field)
  // footer-switch state (model menu): fast mode is CLI truth (initialize + result frames, an
  // optimistic click in between); thinking is Kotlin-owned preference, seeded on every load.
  // oneMFromCli: the REAL window from the first result's modelUsage[].contextWindow — overrides
  // the tag-derived 1M switch state (fable runs 1M whatever the tag says); null = no result yet
  // for the current selection, cleared on every model change so stale truth can't leak.
  let fastModeState = 'off', fastModeReason = '', thinkingOn = true, oneMFromCli = null;
  const modelSearch = document.getElementById('modelSearch');
  function allModels() { return models.concat(customModels); }
  // parse the search field, mirroring the CLI model shape: "value : Display Name : desc" (2 colons) /
  // "value : Display Name" (1) / "value" (none → displayName defaults to a prettified label)
  function parseCustomModel(t) {
    const p = t.split(':');
    const value = p[0].trim();
    if (p.length >= 3) return { value: value, displayName: p[1].trim(), description: p.slice(2).join(':').trim(), custom: true };
    if (p.length === 2) return { value: value, displayName: p[1].trim(), custom: true };
    return { value: value, displayName: prettyModel(value), custom: true };
  }
  function persistCustom() { bridge({ kind: 'customModels', json: JSON.stringify(customModels) }); }
  function addCustomModel(cm) {
    if (!cm.value) return;
    const i = customModels.findIndex(function (x) { return x.value === cm.value; });
    if (i >= 0) customModels[i] = cm; else customModels.push(cm);   // update existing / add new
    persistCustom();
  }
  function removeCustomModel(value) {
    customModels = customModels.filter(function (x) { return x.value !== value; });
    persistCustom(); renderModels();
  }
  function chipName(m) {
    const base = (m.displayName || m.value).replace(/\s*\(.*\)$/, '');   // "Opus" / "Sonnet" / "Default"
    // the CLI leads each description with the concrete model, e.g. "Opus 4.8", "Sonnet 5", "Haiku 4.5"
    const ver = ((m.description || '').match(/^\s*([A-Za-z]+ [0-9][0-9.]*)/) || [])[1];
    // Default resolves to a real model — show which one in brackets, e.g. "Default (Opus 4.8)"
    if (base === 'Default' || m.value === 'default') return ver ? 'Default (' + ver + ')' : base;
    return ver || base;                                 // named model: "Sonnet 5", "Opus 4.8", …
  }
  // Friendly chip label for a model set by raw id/name (not in the dropdown): "claude-fable-5[1m]"
  // -> "Fable 5 (1M)", "opus[1m]" -> "Opus (1M)", "haiku" -> "Haiku". Falls back to the raw string.
  function prettyModel(v) {
    const oneM = /\[1m\]/i.test(v);
    const s = String(v).replace(/\[1m\]/i, '').replace(/^claude-/i, '');
    const m = s.match(/^([a-z]+)(?:-([0-9][0-9-]*))?/i);
    if (!m) return v;
    const fam = m[1].charAt(0).toUpperCase() + m[1].slice(1).toLowerCase();
    const ver = m[2] ? m[2].replace(/-/g, '.') : '';
    return (ver ? fam + ' ' + ver : fam) + (oneM ? ' (1M)' : '');
  }
  function renderModels() {
    const q = modelSearch ? modelSearch.value.trim().toLowerCase() : '';
    const shown = allModels().filter(function (m) {
      if (!q) return true;
      return (m.value || '').toLowerCase().indexOf(q) !== -1 ||
             (m.displayName || '').toLowerCase().indexOf(q) !== -1 ||
             (m.description || '').toLowerCase().indexOf(q) !== -1;
    });
    modelItems.innerHTML = shown.map(function (m) {
      // the ✓ ignores the [1m] tag: "sonnet[1m]" (set by the footer switch) is still the Sonnet row.
      // Safe because roster values are unique once stripped; an id matching no row marks nothing.
      return '<div class="popup-item' + (strip1m(m.value) === strip1m(currentModel) ? ' on' : '') + (m.custom ? ' custom' : '') + '" data-v="' + escA(m.value) + '">' +
        '<div class="pi-body"><div class="pi-title">' + esc(m.displayName || m.value) + '</div>' +
        (m.description ? '<div class="pi-desc">' + esc(m.description) + '</div>' : '') +
        '</div>' +
        // every row shows the selected checkmark; a custom row's remove (×) lives INSIDE the
        // check span, overlaying the ✓'s own box so their centers coincide by construction
        // (offset arithmetic is a trap here: the #inputbar 18px ID rule resizes every glyph).
        // Hover swaps ✓ for × via css/70-popups.css (#modelMenu .popup-item.custom:hover .pi-check).
        '<span class="pi-check">' + SVG_CHECK +
        (m.custom ? '<button class="model-del" title="Remove model" data-del="' + escA(m.value) + '">' + SVG_X + '</button>' : '') +
        '</span>' +
        '</div>';
    }).join('');
    Array.prototype.forEach.call(modelItems.children, function (c) {
      c.onclick = function (e) {
        const del = e.target.closest('.model-del');
        if (del) { e.stopPropagation(); removeCustomModel(del.dataset.del); return; }
        setModel(c.dataset.v);
      };
    });
  }
  if (modelSearch) {
    // filter as you type; keep the top match highlighted so Enter picks it
    modelSearch.addEventListener('input', function () {
      renderModels();
      const first = modelItems.querySelector('.popup-item');
      if (first) first.classList.add('sel');
    });
    // Enter: an exact listed value, else the highlighted filtered row, else the raw text (any model,
    // even one not in the list — e.g. opus[1m]). stopPropagation so the menu's Enter handler doesn't
    // also fire and click a different row.
    modelSearch.addEventListener('keydown', function (e) {
      if (e.key !== 'Enter') return;
      e.preventDefault(); e.stopPropagation();
      const raw = modelSearch.value.trim(); if (!raw) return;
      // no colon and it matches an existing model (or the highlighted filter row) → just select it
      if (raw.indexOf(':') === -1) {
        const exact = allModels().find(function (m) { return (m.value || '').toLowerCase() === raw.toLowerCase(); });
        if (exact) { setModel(exact.value); return; }
        const sel = modelItems.querySelector('.popup-item.sel');
        if (sel && sel.dataset.v) { setModel(sel.dataset.v); return; }
      }
      // otherwise it's a new custom model (colon form, or a bare id matching nothing) → save + select
      const cm = parseCustomModel(raw);
      addCustomModel(cm);
      setModel(cm.value);
    });
  }
  // The one writer for the model chip — icon + label, label ESCAPED (custom model names are user
  // input). All three callers (setModel, the CLI's model event, the retraction fallback) go
  // through it so they can't drift.
  // NO effort suffix, on this chip or the mode chip: the level shows ONLY on the model menu's own
  // "Effort <b>" label. User decision 2026-08-26, after both a bracketed and a middot form were
  // rendered in the real panel and rejected — "better is to hide effort". The chip is one fact
  // wide, and the level is one click away in the popup that owns the slider.
  function setModelChip(label, title) {
    modelChip.innerHTML = SVG_SPARK + ' ' + esc(label);
    modelChip.title = title;
  }
  // Display half of a model change: selection, chip, gauge denominator, menu, footer — and NO
  // bridge. setModel adds the bridge; __model_rejected (9.11) uses this half alone to FOLLOW the
  // CLI back to the model it kept, the same rule the retraction fallback obeys.
  function showModel(v) {
    currentModel = v; oneMFromCli = null;   // new selection: tag-derived until its first result
    const m = allModels().find(function (x) { return x.value === v; });
    // custom carries the same {value, displayName, description} shape as built-in, so both draw the
    // Model+Version through chipName; an unlisted id falls back to a prettified label. The exact id is
    // kept on hover so nothing is lost.
    setModelChip(m ? chipName(m) : prettyModel(v), v);
    // switching between a 1M model and a 200k one changes the denominator; when we have no built-in
    // metadata, infer 1M from a [1m] tag on the value
    { const w = (m && !m.custom) ? windowOf(m) : (/\[1m\]/i.test(v) ? CTX_1M : 0); if (w) { ctxWindowFromCli = w; renderContext(); } }
    renderModels(); syncModelFooter();
  }
  function setModel(v, keepOpen) {
    showModel(v);
    bridge({ kind: 'model', model: v });
    if (!keepOpen) closeMenus();   // the footer's 1M switch re-selects in place; row clicks still close
  }
  modelChip.onclick = function (e) { tg('modelMenu', e); };

  /* ---------- model-menu footer: 1M / fast / thinking switches ---------- */
  const tgl1m = document.getElementById('tgl1m'), tglFast = document.getElementById('tglFast'),
        tglThink = document.getElementById('tglThink');
  function strip1m(v) { return String(v || '').replace(/\[1m\]/ig, ''); }
  // The roster item for a value, ignoring the [1m] tag on either side — maps plain "opus" (the
  // 1M switch turned off) back to the "opus[1m]" roster entry, and raw ids to their family row.
  function rosterFor(v) {
    const s = strip1m(v);
    return models.find(function (m) { return strip1m(m.value) === s || strip1m(m.resolvedModel || '') === s; });
  }
  // The switch's ON state: the [1m] tag on the selection itself, or — only for the exact roster
  // selection (i.e. "default") — on what the CLI says it resolves to. The value===currentModel
  // guard keeps a stripped variant ("opus") from inheriting its roster row's tag.
  function is1mOn() {
    if (/\[1m\]/i.test(currentModel || '')) return true;
    const m = rosterFor(currentModel);
    return !!(m && m.value === currentModel && /\[1m\]/i.test(m.resolvedModel || ''));
  }
  function setTgl(btn, on) { btn.classList.toggle('on', !!on); btn.setAttribute('aria-checked', String(!!on)); }
  // What the 1M switch DISPLAYS: the API-confirmed window once a result has spoken for this
  // selection, the [1m] tag sniff until then.
  function shown1m() { return oneMFromCli !== null ? oneMFromCli : is1mOn(); }
  // The one writer for all three switches. No validity logic on the 1M switch (user decision
  // 2026-08-24): any model can be flipped; an unsupported combination fails on the next turn with
  // the API's own error (measured: haiku[1m] → "400 The long context beta is not yet available").
  function syncModelFooter() {
    if (!tgl1m) return;
    setTgl(tgl1m, shown1m());
    const m = rosterFor(currentModel);
    const fastOk = !!(m && m.supportsFastMode);
    tglFast.disabled = !fastOk;
    setTgl(tglFast, fastOk && fastModeState !== 'off');
    tglFast.classList.toggle('cooldown', fastOk && fastModeState === 'cooldown');
    tglFast.title = !fastOk ? 'Fast mode — not supported by this model (Opus only)'
      : fastModeReason ? 'Fast mode: ' + fastModeReason
      : fastModeState === 'cooldown' ? 'Fast mode: cooling down' : 'Faster responses (Opus only)';
    setTgl(tglThink, thinkingOn);
  }
  if (tgl1m) {
    tgl1m.onclick = function (e) {
      e.stopPropagation();
      const on = !shown1m();   // direction from the DISPLAYED state, so a CLI-snapped switch toggles honestly
      // operate on the value; for "default" (tagless value, tagged resolvedModel) toggling OFF
      // pins the resolved model without the tag — stripping "default" itself would be a noop
      let base = currentModel || '';
      if (on === false && !/\[1m\]/i.test(base)) {
        const m = rosterFor(base);
        if (m && m.value === base && /\[1m\]/i.test(m.resolvedModel || '')) base = m.resolvedModel;
      }
      const nv = on ? strip1m(base) + '[1m]' : strip1m(base);
      setModel(nv, true);
      // setModel's own sniff leaves the denominator alone when toggling OFF to an unlisted value
      // (its w=0 path) — state the window explicitly. Fable is natively 1M even untagged.
      ctxWindowFromCli = (on || /fable/i.test(nv)) ? CTX_1M : CTX_STD;
      renderContext();
    };
    tglFast.onclick = function (e) {
      e.stopPropagation();
      if (tglFast.disabled) return;
      // optimistic: the CLI acks apply_flag_settings with no state; truth arrives on the next
      // result's fast_mode_state and re-syncs (a gated account snaps the switch back there)
      const on = fastModeState === 'off';
      fastModeState = on ? 'on' : 'off'; fastModeReason = '';
      bridge({ kind: 'fastMode', on: on });
      syncModelFooter();
    };
    tglThink.onclick = function (e) {
      e.stopPropagation();
      thinkingOn = !thinkingOn;
      bridge({ kind: 'thinking', on: thinkingOn });
      syncModelFooter();
    };
  }

  /* ---------- effort slider (sends /effort <level>) ----------
     Lives in the MODEL menu's footer since 2026-08-26 (user request); it used to sit in the mode
     menu's, and is kept next to the model picker here for the same reason. #efName is the ONLY
     place the level is displayed — no chip carries it (see setModelChip). */
  const effortEl = document.getElementById('effort'), efName = document.getElementById('efName');
  const efDots = Array.prototype.slice.call(effortEl.querySelectorAll('.d'));
  function setEffortUI(i) {
    efDots.forEach(function (d, n) { d.classList.toggle('on', n <= i); d.classList.toggle('cur', n === i); });
    efName.textContent = efDots[i].dataset.l;
    // efName is the whole display: the level is deliberately not mirrored onto any chip
  }
  efDots.forEach(function (d, i) {
    d.addEventListener('click', function (e) {
      e.stopPropagation();
      setEffortUI(i);
      // there is no silent control request for the effort level, so it rides a /effort turn; mute
      // that turn's UI except the CLI's own "Set effort level to …" confirmation, which the gate in
      // onClaudeEvent draws as a block — an effort change shows like a model change (2026-08-25).
      // Only when idle — mid-turn we couldn't tell the effort result apart from the real one.
      if (!busy) effortMuted = true;
      bridge({ kind: 'user', text: '/effort ' + d.dataset.v });
    });
  });
  setEffortUI(2); // default: High
