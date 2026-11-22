  /* ---------- slash commands (popup) + @-mentions (legacy popup) ---------- */
  let files = [];
  let slashCommands = [];
  let customCmds = {};   // name (lowercased) -> "project" | "user"; rebuilt at every roster store

  // The CLI's only wire marker for a custom command or skill is a description SUFFIX — the entry
  // schema ({name, description, argumentHint, aliases?}) has no type field, but every entry
  // sourced from .claude/commands/**.md or .claude/skills/*/SKILL.md (either base, even
  // description-less files) arrives with " (project)" or " (user)" appended. Measured 2026-08-15
  // on CLI 2.1.228 across initialize AND commands_changed payloads: zero false positives over
  // 107 built-in entries. Strip the suffix (renderSlash shows a badge instead) and record the
  // source on the entry + in customCmds. Wholesale rebuild, so a command whose file was deleted
  // loses its enablement with the same roster replace that dropped it.
  function markCustom(items) {
    customCmds = {};
    items.forEach(function (c) {
      const m = /^([\s\S]*) \((project|user)\)$/.exec(c.description || '');
      if (m) { c.description = m[1]; c.src = m[2]; customCmds[(c.name || '').toLowerCase()] = m[2]; }
    });
    return items;
  }

  // We drive the CLI over --input-format stream-json (headless SDK) — there is no interactive
  // terminal, so a picked command is only ever sent as a user-message text block. Three buckets:
  //  · NATIVE  — handled in the IDE via a control request / UI action, never forwarded as text
  //  · TUI     — interactive-only built-ins (auth, config UI, terminal setup, IDE pickers) that
  //              have no headless effect; shown greyed + disabled and refused if typed
  //  · text    — everything else (custom/project/user/plugin/MCP-prompt commands + prompt-style
  //              built-ins like /compact, /effort, /init) — the CLI expands these as a turn
  // BUILT-INS are enabled by ALLOWLIST, not denylist: the schema has no type field, so rather
  // than guess which built-ins work headlessly we start with only the verified ones and add one
  // at a time (see cxtify panel/reference.md § Slash commands). Everything else stays hidden until confirmed.
  // CUSTOM entries are auto-enabled by two wire signals instead (both measured, CLI 2.1.228):
  //  · customCmds — the " (project)"/" (user)" description suffix parsed off by markCustom();
  //    covers .claude/commands/**.md files and .claude/skills/*/SKILL.md, either base
  //  · the mcp__ name prefix — MCP-server prompts (roster-named mcp__<server>__<prompt>)
  // Both classes are prompt expansions the CLI performs on an ordinary turn (verified live with
  // /dummy-cmd, 2026-08-15), which is exactly what "text" means here.
  const CMD_NATIVE  = new Set(['btw']);   // handled in the IDE, never forwarded. /clear (and its
  // /new, /reset aliases) removed 2026-08-29: the header's New-conversation button IS the panel's
  // /clear, so the typed form is refused like /model (checklist 7.6).
  // Commands the panel supplies ITSELF, shown only when the roster has no entry of that name.
  // The stdio roster carries no /btw (54 entries, CLI 2.1.251, measured 2026-08-29) — the TUI
  // registers it locally and so does VS Code (`registerAction({id:"slash-command-btw"…})`).
  // The hint makes it insert-not-run on click (cmdTakesArg), like every hinted command.
  const CMD_LOCAL = [{ name: 'btw', description: 'Ask a quick side question without interrupting the conversation', argumentHint: '[question]' }];
  // The IDE-development set (user-picked 2026-08-15, each smoke-verified headless: the CLI
  // accepts and expands every one as a turn — cxtify panel/reference.md § Slash commands for the grouping logic).
  const CMD_ALLOWED = new Set([
    'compact', 'reload-skills',
    'context', 'code-review', 'simplify', 'verify', 'run', 'security-review',   // core dev
    'init', 'recap', 'goal',                                                    // session workflow
    'loop', 'batch', 'deep-research', 'list-agents',                            // orchestration
  ]);   // forwarded to the CLI as a turn
  // An alias the roster advertises (`aliases: [...]` on the entry) → the command it names;
  // anything else passes through unchanged. Case-insensitive, like every other name check.
  function canonicalCmd(name) {
    const n = (name || '').toLowerCase();
    for (let i = 0; i < slashCommands.length; i++) {
      const al = slashCommands[i].aliases;
      if (Array.isArray(al) && al.some(function (a) { return String(a).toLowerCase() === n; })) {
        return (slashCommands[i].name || n).toLowerCase();
      }
    }
    return n;
  }
  function cmdKind(name) {
    const n = canonicalCmd(name);
    // native/allowlist first: a project file named btw.md must not shadow the IDE's /btw
    if (CMD_NATIVE.has(n)) return 'native';
    if (CMD_ALLOWED.has(n)) return 'text';
    if (customCmds[n]) return 'text';              // project/user command file or skill
    if (n.indexOf('mcp__') === 0) return 'text';   // MCP prompt
    return 'tui';   // hidden until verified — see cxtify panel/reference.md § Slash commands
  }
  // Does the command take an argument AT ALL? Any non-empty argumentHint counts — required
  // ("<x>") and optional ("[x]") alike. The earlier rule split those two and ran optional-arg
  // commands on click, which is the wrong question to ask at a menu: a hint like
  // "[init | load | save]" is a MENU OF SUB-MODES, so clicking the row fired the bare form
  // (user, 2026-08-16, on /context) when the point of the click was to pick one. Only a
  // hint-less command acts on click now — same as the terminal, where picking a command
  // completes it into the prompt and you decide when to send.
  function cmdTakesArg(c) { return !!((c && c.argumentHint) || '').trim(); }

  // 7.12 Recent first: name (lowercased, canonical) -> epoch ms of its last send. Kotlin owns the
  // store (application-level, so it survives restarts and spans projects) and seeds it on every
  // load as __cmdRecent; a send updates the local copy at once so the next menu already ranks it.
  let cmdRecent = {};
  function noteCmdUsed(name) {
    cmdRecent[name] = Date.now();
    bridge({ kind: 'cmdUsed', name: name });
  }

  // 7.11 Save as command: Kotlin owns the dialog and the file. `taken` is every name the panel
  // resolves BEFORE a custom file (built-ins, aliases, the panel's own /btw) — a file under one of
  // those would never run, so the dialog refuses the name instead of writing a dead command.
  function saveAsCommand(text) {
    const taken = [];
    slashCommands.concat(CMD_LOCAL).forEach(function (c) {
      if (c.src || (c.name || '').indexOf('mcp__') === 0) return;
      taken.push((c.name || '').toLowerCase());
      if (Array.isArray(c.aliases)) c.aliases.forEach(function (a) { taken.push(String(a).toLowerCase()); });
    });
    bridge({ kind: 'saveCommand', text: text, taken: taken });
  }
  // The file is written. A project file reaches the roster through the CLI's own watcher
  // (commands_changed, ~2.5s); the entry added here makes the command pickable before that, and
  // the watcher's roster REPLACE then supersedes it.
  function onCmdSaved(ev) {
    const name = (ev.name || '').toLowerCase();
    if (!name) return;
    if (!slashCommands.some(function (c) { return (c.name || '').toLowerCase() === name; })) {
      slashCommands.push({ name: name, description: ev.description || '', argumentHint: ev.argumentHint || '', src: ev.scope });
    }
    customCmds[name] = ev.scope;
    statusLine('Saved /' + name + ' for ' + (ev.scope === 'user' ? 'all projects' : 'this project') +
      ' · type / to send it', SVG_CHECK);
  }

  function renderSlash(q) {
    const ql = (q || '').toLowerCase();
    // rank: exact name (0) → name prefix (1) → name substring (2) → description-only (3), so an
    // exact match like "/model" leads instead of sinking below commands that merely mention it in
    // their description. Array.sort is stable, so ties keep the CLI's original order. An alias
    // scores like the name it stands for, so typing "/review" surfaces /code-review at rank 0 —
    // it used to fall through to the description-only tier or vanish (checklist 7.7).
    const hits = cmdRoster().map(function (c) {
      const names = [(c.name || '').toLowerCase()].concat(
        Array.isArray(c.aliases) ? c.aliases.map(function (a) { return String(a).toLowerCase(); }) : []);
      const desc = (c.description || '').toLowerCase();
      let score = -1;
      names.forEach(function (name) {
        let sc = -1;
        if (name === ql) sc = 0;
        else if (name.indexOf(ql) === 0) sc = 1;
        else if (name.indexOf(ql) !== -1) sc = 2;
        if (sc >= 0 && (score < 0 || sc < score)) score = sc;
      });
      if (score < 0 && desc.indexOf(ql) !== -1) score = 3;
      return { c: c, score: score };
    }).filter(function (x) { return x.score >= 0 && cmdKind(x.c.name) !== 'tui'; })  // hide unconfirmed
      // 7.12: inside a tier the most recently sent command leads; never-sent ones keep the CLI's order
      .sort(function (a, b) {
        return a.score - b.score ||
          (cmdRecent[(b.c.name || '').toLowerCase()] || 0) - (cmdRecent[(a.c.name || '').toLowerCase()] || 0);
      })
      .map(function (x) { return x.c; }).slice(0, 40);
    if (!hits.length) { slashList.innerHTML = '<div class="popup-empty">No matching commands</div>'; return 0; }
    slashList.innerHTML = hits.map(function (c, i) {
      // Source badge: markCustom() recorded src for suffix-marked entries; MCP prompts are
      // recognisable by name alone. Built-ins get none — quiet is the default.
      const src = c.src || ((c.name || '').indexOf('mcp__') === 0 ? 'mcp' : null);
      // Aliases ride the title, muted, so the row that matched "/review" says why it is here.
      const aliases = Array.isArray(c.aliases) && c.aliases.length
        ? '<span class="pi-alias">' + c.aliases.map(function (a) { return '/' + esc(String(a)); }).join(' ') + '</span>' : '';
      // 7.13: the pin toggle — hover-only on a plain row, always shown (filled) on a pinned one
      const pinned = isPinned(c.name);
      return '<div class="popup-item' + (i === 0 ? ' sel' : '') + (pinned ? ' pinned' : '') + '" data-cmd="/' + esc(c.name) + '"' +
        (cmdTakesArg(c) ? ' data-takesarg="1"' : '') + '>' +
        '<div class="pi-body"><div class="pi-title">/' + esc(c.name) + aliases +
        (src ? '<span class="pi-src">' + esc(src) + '</span>' : '') + '</div>' +
        (c.description ? '<div class="pi-desc">' + esc(c.description) + '</div>' : '') + '</div>' +
        '<button class="pi-pin" type="button" title="' + (pinned ? 'Unpin' : 'Pin above the message box') + '">' + SVG_PIN + '</button></div>';
    }).join('');
    return hits.length;
  }
  slashList.addEventListener('click', function (e) {
    const item = e.target.closest('.popup-item'); if (!item || !item.dataset.cmd) return;
    if (e.target.closest('.pi-pin')) {         // 7.13: a pin toggle never runs the command or closes the menu
      // The redraw below REMOVES the clicked button, so by the time the click reaches the
      // document-level handler its target is outside every .popup and reads as an outside click,
      // which closed the menu (fixture 107 step 1). Stop it here.
      e.stopPropagation();
      const top = slashList.scrollTop;
      togglePin(item.dataset.cmd.slice(1));
      renderSlash(/^\/[\w-]*$/.test(input.value) ? input.value.slice(1) : '');
      slashList.scrollTop = top;
      const again = slashList.querySelector('.popup-item[data-cmd="' + item.dataset.cmd.replace(/"/g, '') + '"]');
      if (again) { slashList.querySelectorAll('.popup-item.sel').forEach(function (x) { x.classList.remove('sel'); }); again.classList.add('sel'); }
      input.focus();
      return;
    }
    menuEl('slashMenu').classList.remove('show');
    if (item.dataset.takesarg) {              // takes an argument → insert and let the user type it
      input.value = item.dataset.cmd + ' ';
      input.focus(); autoGrow();
    } else {                                   // takes no argument at all → a command menu should act, run now
      input.value = item.dataset.cmd;
      submit();
    }
  });

  /* ---------- 7.13 pinned commands ----------
     A row of chips above the input card: one click sends a command the user pinned, with no typing
     and no menu (user, 2026-11-22). Pinned and unpinned ONLY from the / menu's rows (the .pi-pin
     toggle renderSlash draws) — the strip itself carries commands and the +N overflow, nothing
     else. Kotlin owns the store (application-level like the model pick, so the pins are the same
     in every project) and seeds it on every load as __cmdPins; a toggle sends the whole list back.
     A pin is DRAWN only while its command is in the roster and enabled here (cmdKind), so a
     project without that command shows no dead chip — the pin itself is kept for the projects
     that have it. No keyboard shortcuts: the plugin binds none (decision 2026-08-09). Fixture 107. */
  let cmdPins = [];   // canonical lowercased names, in pin order
  const pinsEl = document.getElementById('pins'), pinMore = document.getElementById('pinMore'),
        pinMoreWrap = pinMore.parentNode, pinList = document.getElementById('pinList');
  // Everything the / menu can offer: the CLI's roster plus the commands the panel supplies itself.
  function cmdRoster() {
    return slashCommands.concat(CMD_LOCAL.filter(function (l) {
      return !slashCommands.some(function (c) { return (c.name || '').toLowerCase() === l.name; });
    }));
  }
  function isPinned(name) { return cmdPins.indexOf(canonicalCmd(name)) !== -1; }
  function setPins(list) {
    cmdPins = (Array.isArray(list) ? list : []).filter(function (n) { return typeof n === 'string' && n; });
    renderPins();
  }
  function togglePin(name) {
    const n = canonicalCmd(name), i = cmdPins.indexOf(n);
    if (i === -1) cmdPins.push(n); else cmdPins.splice(i, 1);
    bridge({ kind: 'cmdPins', pins: cmdPins });
    renderPins();
  }
  // The chip's label is the command's own text — its description ("Let's commit it"), first line
  // only; CSS ellipsises a long one. A command with no description shows its /name.
  function pinLabel(c) {
    const d = String(c.description || '').split('\n')[0].trim();
    return d || '/' + c.name;
  }
  // A click on a pin. A command that takes NO argument is sent at once, alone: the draft and its
  // attachments stay in the composer exactly as they are (user's rule, 2026-11-22), and a turn
  // already running queues it like any typed message. A command that takes an argument cannot be
  // sent bare (cmdTakesArg — the same rule a / menu click follows): "/name " goes in front of
  // whatever is in the box, so a draft becomes its argument, and the user sends when ready.
  function runPinned(name) {
    const n = canonicalCmd(name);
    const c = cmdRoster().find(function (x) { return (x.name || '').toLowerCase() === n; });
    closeMenus(); hideMenu();
    if (!c || cmdKind(n) === 'tui') return;
    if (cmdTakesArg(c)) {
      const draft = input.value;
      input.value = '/' + n + ' ' + (new RegExp('^/' + n.replace(/[^\w-]/g, '\\$&') + '(\\s|$)', 'i').test(draft) ? draft.replace(/^\S+\s*/, '') : draft);
      histIdx = history.length;
      autoGrow(); input.focus();
      const end = input.value.length; input.setSelectionRange(end, end);
      return;
    }
    noteCmdUsed(n);                                                // 7.12: a pin click is a use
    const text = '/' + n;
    if (busy) { queue.push({ text: text, images: [], selection: null }); renderQueue(); smoothToBottom(); return; }
    sendTurn(text, [], null);
  }
  function renderPins() {
    pinsEl.querySelectorAll(':scope > .pin').forEach(function (b) { b.remove(); });
    const roster = cmdRoster();
    let shown = 0;
    cmdPins.forEach(function (n) {
      const c = roster.find(function (x) { return (x.name || '').toLowerCase() === n; });
      if (!c || cmdKind(n) === 'tui') return;                      // not in this project's roster: not drawn
      const b = document.createElement('button');
      b.type = 'button'; b.className = 'pin' + (cmdTakesArg(c) ? ' takes-arg' : '');
      b.dataset.cmd = n;
      b.title = '/' + n + (cmdTakesArg(c) ? ' — writes it into the message box' : ' — sends it now');
      // two glyphs, one shown: what a click does, or — while the chip is dragged — the grip (CSS, .pin.drag)
      b.innerHTML = (cmdTakesArg(c) ? SVG_TEXT_CURSOR : SVG_ARROW_R) + SVG_GRIP + '<span class="pin-t"></span>';
      b.lastChild.textContent = pinLabel(c);
      pinsEl.insertBefore(b, pinMoreWrap);
      shown++;
    });
    pinsEl.hidden = !shown;
    if (!shown) menuEl('pinMenu').classList.remove('show');
    layoutPins();
  }
  // Fit the chips to the row: everything visible when it fits, otherwise as many as fit beside the
  // +N chip, the rest [hidden] and listed in #pinMenu. Measured, never assumed — a chip's width is
  // its label in the IDE's font. Runs on every render and whenever the row's width changes.
  function layoutPins() {
    if (pinsEl.hidden) return;
    const chips = Array.prototype.slice.call(pinsEl.querySelectorAll(':scope > .pin'));
    chips.forEach(function (c) { c.hidden = false; });
    pinMoreWrap.hidden = false; pinMore.textContent = '+' + chips.length;   // widest it can be
    const gap = parseFloat(getComputedStyle(pinsEl).columnGap) || 0, avail = pinsEl.clientWidth;
    const widths = chips.map(function (c) { return c.getBoundingClientRect().width; });
    const total = function (k) { let w = 0; for (let i = 0; i < k; i++) w += widths[i] + (i ? gap : 0); return w; };
    let n = chips.length;
    if (!avail) return;                                           // not laid out yet (hidden tool window)
    if (total(n) <= avail + 0.5) pinMoreWrap.hidden = true;
    else {
      const moreW = pinMoreWrap.getBoundingClientRect().width + gap;
      while (n > 0 && total(n) + moreW > avail + 0.5) n--;
      chips.forEach(function (c, i) { c.hidden = i >= n; });
      pinMore.textContent = '+' + (chips.length - n);
    }
    pinList.textContent = '';
    chips.slice(n).forEach(function (c) {
      const r = document.createElement('div');
      r.className = 'popup-item'; r.dataset.cmd = c.dataset.cmd;
      r.innerHTML = '<span class="pi-ic">' + (c.classList.contains('takes-arg') ? SVG_TEXT_CURSOR : SVG_ARROW_R) + '</span>' +
        '<div class="pi-body"><div class="pi-title"></div></div>';
      const title = r.querySelector('.pi-title'), alias = document.createElement('span');
      title.textContent = c.querySelector('.pin-t').textContent;
      alias.className = 'pi-alias'; alias.textContent = '/' + c.dataset.cmd;
      title.appendChild(alias);
      pinList.appendChild(r);
    });
    if (pinMoreWrap.hidden) menuEl('pinMenu').classList.remove('show');
  }
  pinsEl.addEventListener('click', function (e) {
    if (pinDropped) { e.stopPropagation(); return; }              // the click that ends a drag sends nothing
    if (e.target.closest('#pinMore')) return tg('pinMenu', e);
    const hit = e.target.closest('#pinList .popup-item, #pins > .pin');
    if (!hit || !hit.dataset.cmd) return;
    e.stopPropagation();
    runPinned(hit.dataset.cmd);
  });
  // Drag a chip to reorder (user, 2026-11-22). Plain mouse events, not HTML5 drag-and-drop: the
  // panel renders off-screen and OS-level drags never reach this DOM (gotchas § JCEF). A press
  // becomes a drag only after 5px of travel, so an ordinary click still sends; from then on the
  // chip follows the pointer by changing PLACE — it is re-inserted before the first visible chip
  // whose midpoint the pointer has not passed — and the release commits. The browser fires a
  // click after that release (same element under the pointer), which pinDropped swallows: a drag
  // must never send the command. Only the chips on the row take part; pins folded into +N keep
  // their place behind them.
  let pinDrag = null, pinDropped = false;
  pinsEl.addEventListener('mousedown', function (e) {
    const chip = e.button === 0 && e.target.closest('#pins > .pin');
    if (chip) pinDrag = { chip: chip, x: e.clientX, moved: false };
  });
  document.addEventListener('mousemove', function (e) {
    if (!pinDrag) return;
    if (pinDrag.moved && e.buttons === 0) return endPinDrag();     // released outside the window
    if (!pinDrag.moved) {
      if (Math.abs(e.clientX - pinDrag.x) < 5) return;
      pinDrag.moved = true;
      pinDrag.chip.classList.add('drag'); pinsEl.classList.add('sorting');
      closeMenus();
    }
    const chip = pinDrag.chip;
    const others = Array.prototype.filter.call(pinsEl.querySelectorAll(':scope > .pin'), function (c) { return c !== chip; });
    const visible = others.filter(function (c) { return !c.hidden; });
    let before = others.filter(function (c) { return c.hidden; })[0] || pinMoreWrap;   // end of the visible run
    for (let i = 0; i < visible.length; i++) {
      const r = visible[i].getBoundingClientRect();
      if (e.clientX < r.left + r.width / 2) { before = visible[i]; break; }
    }
    if (chip.nextElementSibling !== before) pinsEl.insertBefore(chip, before);
  });
  document.addEventListener('mouseup', function () { if (pinDrag) endPinDrag(); });
  function endPinDrag() {
    const d = pinDrag; pinDrag = null;
    if (!d.moved) return;
    d.chip.classList.remove('drag'); pinsEl.classList.remove('sorting');
    pinDropped = true; setTimeout(function () { pinDropped = false; }, 0);
    reorderPins(Array.prototype.map.call(pinsEl.querySelectorAll(':scope > .pin'), function (c) { return c.dataset.cmd; }));
  }
  // `drawn` is the chips' names in their new order. The pins this roster cannot draw keep the
  // slots they had in the list — only the drawn ones trade places — so reordering in one project
  // never shuffles a pin that belongs to another.
  function reorderPins(drawn) {
    const set = {}; drawn.forEach(function (n) { set[n] = 1; });
    let k = 0;
    const next = cmdPins.map(function (n) { return set[n] ? drawn[k++] : n; });
    if (next.join('\n') === cmdPins.join('\n')) return;
    cmdPins = next;
    bridge({ kind: 'cmdPins', pins: cmdPins });
    renderPins();
  }
  if (window.ResizeObserver) new ResizeObserver(function () { layoutPins(); }).observe(pinsEl);

  const menu = document.getElementById('mention');
  let menuItems = [], menuSel = -1, menuStart = 0; // menuItems: [{ins, disp}]
  // Same contract as slashEscaped: updateAuto() re-opens on every keystroke while the caret
  // sits in an @-token, so Escape must stick until the caret leaves the token.
  let mentionEscaped = false;

  function currentMention() {
    const m = input.value.slice(0, input.selectionStart).match(/(?:^|\s)@([^\s@]*)$/);
    return m ? { q: m[1], start: input.selectionStart - m[1].length } : null;
  }
  function hideMenu() { menu.style.display = 'none'; menuItems = []; menuSel = -1; }

  function openMenu(start, entries) {
    if (!entries.length) return hideMenu();
    closeMenus(); closeCardMenus();   // the @-menu replaces any open popup, same as tg() in reverse
    menuStart = start; menuItems = entries; menuSel = 0;
    menu.innerHTML = entries.map(function (e, i) {
      // Split like every other path surface: these rows are already project-relative (Kotlin strips
      // basePath), but a plain ellipsis ate the FILENAME, which is the one part that must survive.
      const pp = pathParts(e.disp);
      return '<div class="mi' + (i === 0 ? ' sel' : '') + '" data-i="' + i + '" title="' + esc(e.disp) + '">' +
        '<span class="p-head">' + esc(pp.head) + '</span><span class="p-tail">' + esc(pp.tail) + '</span></div>';
    }).join('');
    const r = input.getBoundingClientRect();
    menu.style.display = 'block';
    menu.style.left = r.left + 'px';
    menu.style.width = r.width + 'px';
    menu.style.top = (r.top - Math.min(240, menu.scrollHeight) - 4) + 'px';
    Array.prototype.forEach.call(menu.children, function (c) { c.onclick = function () { pick(+c.dataset.i); }; });
  }
  function updateAuto() {
    const men = currentMention();
    if (!men) mentionEscaped = false;           // leaving the @-token re-arms auto-open
    if (men && !mentionEscaped) {
      const q = men.q.toLowerCase();
      return openMenu(men.start, files.filter(function (f) { return f.toLowerCase().includes(q); }).slice(0, 20)
        .map(function (f) { return { ins: mentionToken(f).slice(1), disp: f }; }));   // the typed `@` stays; `"a b.txt"` for a spaced path (fixture 91)
    }
    hideMenu();
  }
  function pick(idx) {
    const e = menuItems[idx];
    if (!e) return hideMenu();
    const v = input.value, pos = input.selectionStart;
    input.value = v.slice(0, menuStart) + e.ins + ' ' + v.slice(pos);
    const caret = menuStart + e.ins.length + 1;
    input.setSelectionRange(caret, caret);
    input.focus(); hideMenu(); autoGrow();
  }
  function moveSel(d) {
    if (!menuItems.length) return;
    menuSel = (menuSel + d + menuItems.length) % menuItems.length;
    Array.prototype.forEach.call(menu.children, function (c, i) { c.classList.toggle('sel', i === menuSel); });
    const selEl = menu.children[menuSel]; if (selEl) selEl.scrollIntoView({ block: 'nearest' });
  }
  // Returning focus to a caret that sits in an @-token reopens the menu — outside-click is a
  // soft dismissal (Escape's mentionEscaped still sticks, checked inside updateAuto). The
  // activeMenu() guard matters: tg('slashMenu') calls input.focus() after opening, and
  // without it this handler would open the @-menu, whose exclusivity rule would then close
  // the slash menu the user just asked for.
  input.addEventListener('focus', function () { if (!activeMenu()) { updateAuto(); slashAuto(); } });

  input.addEventListener('input', function (e) {
    autoGrow();
    updateAuto();
    // slash-command autocomplete: open + filter while the line is a single "/token". A second
    // "/" (a path like /home/…) or any whitespace ends the command shape, and a filter with no
    // hits closes the menu too — a dead "No matching commands" popup would swallow Enter.
    const slashOpen = menuEl('slashMenu').classList.contains('show');
    let v = input.value;
    // if the menu was opened via the button, the first typed character gets the leading slash —
    // but never on a deletion, or erasing the "/" would just re-insert it (un-deletable slash)
    if (slashOpen && v.length && v.charAt(0) !== '/' && !/^delete/.test(e.inputType || '') &&
        v.indexOf(' ') === -1 && v.indexOf('\n') === -1) {
      input.value = '/' + v;
      const end = input.value.length; input.setSelectionRange(end, end);
      v = input.value;
    }
    slashAuto();
  });
  // The slash evaluation, shared by typing, refocus, and click-into-composer (same soft-reopen
  // contract as the @-menu: outside-click reopens on return, Escape's slashEscaped sticks).
  // The /-shape is whole-line, so unlike updateAuto there is no caret math to redo.
  function slashAuto() {
    const v = input.value;
    const isSlash = /^\/[\w-]*$/.test(v);
    if (!isSlash) slashEscaped = false;         // leaving the /-shape re-arms auto-open
    if (isSlash && !slashEscaped && renderSlash(v.slice(1))) {
      ['modeMenu', 'modelMenu', 'attachMenu'].forEach(function (x) { menuEl(x).classList.remove('show'); });
      menuEl('slashMenu').classList.add('show');
      capToRows(slashList, 5, '.popup-item');   // AFTER .show: renderSlash ran while still hidden
    } else {
      menuEl('slashMenu').classList.remove('show');
    }
  }

  input.addEventListener('keydown', function (e) {
    if (menu.style.display === 'block') {           // legacy @-mention popup
      if (e.key === 'ArrowDown') { e.preventDefault(); return moveSel(1); }
      if (e.key === 'ArrowUp')   { e.preventDefault(); return moveSel(-1); }
      if (e.key === 'Enter' || e.key === 'Tab') { e.preventDefault(); return pick(menuSel); }
      if (e.key === 'Escape')    { e.preventDefault(); mentionEscaped = true; return hideMenu(); }
    }
    if (activeMenu()) return;                       // popup menus own the keys (document handler)
    if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) { e.preventDefault(); submit(); } // Ctrl/Cmd+Enter sends; plain Enter = newline
    // ↑/↓ recall sent messages — ONLY from the two ends of the draft: ↑ with the caret at the very
    // first character, ↓ at the very last (user, 2026-11-22). Anywhere else a plain arrow is the
    // browser's. On a draft that WRAPS it moves one visual row at a time — a wrapped row has no
    // newline, so the old newline test took every row for the first (and last) line and swapped
    // the draft for another prompt while the user was only trying to reach the top row. From the
    // top row ↑ goes to offset 0, from the bottom row ↓ goes to the end: measured with real keys
    // in the 242 sandbox, 287 → 167 → 52 → 0 on a three-row draft. Whether that last hop happens
    // at all is platform editing behaviour, so it is not relied on: if the key left the caret
    // where it was, there was no row to move to and we finish the move ourselves. A selection, or
    // Shift (extending one), is never touched. After a recall the caret stays at the end it came
    // from (↑ → offset 0, ↓ → the end), so walking on in the same direction is one press per
    // prompt. Fixture 106.
    const caret = input.selectionStart, len = input.value.length;
    const plain = !e.shiftKey && caret === input.selectionEnd;
    const up = plain && e.key === 'ArrowUp', down = plain && e.key === 'ArrowDown';
    if ((up && caret > 0) || (down && caret < len)) {
      const text = input.value, edge = up ? 0 : len;
      setTimeout(function () {          // after the browser's own caret move
        if (input.value === text && input.selectionStart === caret && input.selectionEnd === caret) input.setSelectionRange(edge, edge);
      }, 0);
    } else if (up && histIdx > 0) {
      e.preventDefault();
      if (histIdx === history.length) histDraft = input.value; // stash the in-progress draft
      histIdx--;
      input.value = history[histIdx]; autoGrow();
      input.setSelectionRange(0, 0);
    } else if (down && histIdx < history.length) {
      e.preventDefault();
      histIdx++;
      input.value = histIdx === history.length ? histDraft : history[histIdx]; autoGrow();
      const end = input.value.length; input.setSelectionRange(end, end);
    }
  });
  input.addEventListener('input', function () { histIdx = history.length; }); // typing exits history mode

  /* ---------- JCEF Delete-key workaround ----------
     On Linux JCEF, the Delete key's AWT event carries keyChar 0x7F and CEF INSERTS that
     control char as text instead of forward-deleting — a tofu box only backspace can remove
     (with a selection, it REPLACES the selection). Two layers, document-level so the model
     search field is covered too:
     1. keydown: do the forward-delete ourselves and cancel the native event.
     2. capture-phase input: strip any control char (except \n \t) from every text field,
        caret preserved — the net stays right even if JCEF ignores the cancel, and it kills
        any sibling of the same bug (e.g. an Escape 0x1B) before the bubbling handlers see
        the value. */
  document.addEventListener('keydown', function (e) {
    if (e.key !== 'Delete') return;
    const t = e.target;
    if (!t || (t.tagName !== 'TEXTAREA' && t.tagName !== 'INPUT')) return;
    e.preventDefault();
    const s = t.selectionStart, se = t.selectionEnd, v = t.value;
    let to = se;
    if (s === se) {                                       // no selection: one char, Ctrl = word
      to = s + 1;
      if (e.ctrlKey) { const m = v.slice(s).match(/^\s*\S*/); to = s + Math.max(1, m ? m[0].length : 1); }
      if (to > v.length) return;
    }
    t.value = v.slice(0, s) + v.slice(to);
    t.setSelectionRange(s, s);
    t.dispatchEvent(new InputEvent('input', { bubbles: true, inputType: 'deleteContentForward' }));
  });
  document.addEventListener('input', function (e) {
    const t = e.target;
    if (!t || typeof t.value !== 'string' || !/[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/.test(t.value)) return;
    const p = t.selectionStart;
    const removedBefore = (t.value.slice(0, p).match(/[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/g) || []).length;
    t.value = t.value.replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/g, '');
    const np = Math.max(0, p - removedBefore);
    t.setSelectionRange(np, np);
  }, true);

