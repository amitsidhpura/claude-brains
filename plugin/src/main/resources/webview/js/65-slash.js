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
  // at a time (see docs/slash-commands.md). Everything else stays hidden until confirmed.
  // CUSTOM entries are auto-enabled by two wire signals instead (both measured, CLI 2.1.228):
  //  · customCmds — the " (project)"/" (user)" description suffix parsed off by markCustom();
  //    covers .claude/commands/**.md files and .claude/skills/*/SKILL.md, either base
  //  · the mcp__ name prefix — MCP-server prompts (roster-named mcp__<server>__<prompt>)
  // Both classes are prompt expansions the CLI performs on an ordinary turn (verified live with
  // /dummy-cmd, 2026-08-15), which is exactly what "text" means here.
  const CMD_NATIVE  = new Set(['clear', 'btw']);   // handled in the IDE, never forwarded
  // Commands the panel supplies ITSELF, shown only when the roster has no entry of that name.
  // The stdio roster carries no /btw (54 entries, CLI 2.1.251, measured 2026-08-29) — the TUI
  // registers it locally and so does VS Code (`registerAction({id:"slash-command-btw"…})`).
  // The hint makes it insert-not-run on click (cmdTakesArg), like every hinted command.
  const CMD_LOCAL = [{ name: 'btw', description: 'Ask a quick side question without interrupting the conversation', argumentHint: '[question]' }];
  // The IDE-development set (user-picked 2026-08-15, each smoke-verified headless: the CLI
  // accepts and expands every one as a turn — docs/slash-commands.md for the grouping logic).
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
    // native/allowlist first: a project file named clear.md must not shadow the IDE's /clear
    if (CMD_NATIVE.has(n)) return 'native';
    if (CMD_ALLOWED.has(n)) return 'text';
    if (customCmds[n]) return 'text';              // project/user command file or skill
    if (n.indexOf('mcp__') === 0) return 'text';   // MCP prompt
    return 'tui';   // hidden until verified — see docs/slash-commands.md
  }
  // Does the command take an argument AT ALL? Any non-empty argumentHint counts — required
  // ("<x>") and optional ("[x]") alike. The earlier rule split those two and ran optional-arg
  // commands on click, which is the wrong question to ask at a menu: a hint like
  // "[init | load | save]" is a MENU OF SUB-MODES, so clicking the row fired the bare form
  // (user, 2026-08-16, on /context) when the point of the click was to pick one. Only a
  // hint-less command acts on click now — same as the terminal, where picking a command
  // completes it into the prompt and you decide when to send.
  function cmdTakesArg(c) { return !!((c && c.argumentHint) || '').trim(); }

  function renderSlash(q) {
    const ql = (q || '').toLowerCase();
    // rank: exact name (0) → name prefix (1) → name substring (2) → description-only (3), so an
    // exact match like "/model" leads instead of sinking below commands that merely mention it in
    // their description. Array.sort is stable, so ties keep the CLI's original order. An alias
    // scores like the name it stands for, so typing "/review" surfaces /code-review at rank 0 —
    // it used to fall through to the description-only tier or vanish (checklist 7.7).
    const roster = slashCommands.concat(CMD_LOCAL.filter(function (l) {
      return !slashCommands.some(function (c) { return (c.name || '').toLowerCase() === l.name; });
    }));
    const hits = roster.map(function (c) {
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
      .sort(function (a, b) { return a.score - b.score; })
      .map(function (x) { return x.c; }).slice(0, 40);
    if (!hits.length) { slashList.innerHTML = '<div class="popup-empty">No matching commands</div>'; return 0; }
    slashList.innerHTML = hits.map(function (c, i) {
      // Source badge: markCustom() recorded src for suffix-marked entries; MCP prompts are
      // recognisable by name alone. Built-ins get none — quiet is the default.
      const src = c.src || ((c.name || '').indexOf('mcp__') === 0 ? 'mcp' : null);
      // Aliases ride the title, muted, so the row that matched "/review" says why it is here.
      const aliases = Array.isArray(c.aliases) && c.aliases.length
        ? '<span class="pi-alias">' + c.aliases.map(function (a) { return '/' + esc(String(a)); }).join(' ') + '</span>' : '';
      return '<div class="popup-item' + (i === 0 ? ' sel' : '') + '" data-cmd="/' + esc(c.name) + '"' +
        (cmdTakesArg(c) ? ' data-takesarg="1"' : '') + '>' +
        '<div class="pi-body"><div class="pi-title">/' + esc(c.name) + aliases +
        (src ? '<span class="pi-src">' + esc(src) + '</span>' : '') + '</div>' +
        (c.description ? '<div class="pi-desc">' + esc(c.description) + '</div>' : '') + '</div></div>';
    }).join('');
    return hits.length;
  }
  slashList.addEventListener('click', function (e) {
    const item = e.target.closest('.popup-item'); if (!item || !item.dataset.cmd) return;
    menuEl('slashMenu').classList.remove('show');
    if (item.dataset.takesarg) {              // takes an argument → insert and let the user type it
      input.value = item.dataset.cmd + ' ';
      input.focus(); autoGrow();
    } else {                                   // takes no argument at all → a command menu should act, run now
      input.value = item.dataset.cmd;
      submit();
    }
  });

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
        .map(function (f) { return { ins: f, disp: f }; }));
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
    // ↑/↓ recall sent messages — only at the edges so line movement still works in a multi-line draft
    const caret = input.selectionStart;
    const onFirstLine = input.value.slice(0, caret).indexOf('\n') === -1;
    const onLastLine = input.value.indexOf('\n', caret) === -1;
    if (e.key === 'ArrowUp' && onFirstLine && histIdx > 0) {
      e.preventDefault();
      if (histIdx === history.length) histDraft = input.value; // stash the in-progress draft
      histIdx--;
      input.value = history[histIdx]; autoGrow();
      const end = input.value.length; input.setSelectionRange(end, end);
    } else if (e.key === 'ArrowDown' && onLastLine && histIdx < history.length) {
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

