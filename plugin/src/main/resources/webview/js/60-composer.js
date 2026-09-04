  /* ---------- attachments (composer chips) ---------- */
  let pending = []; // { kind:'image'|'pdf'|'text', media_type, data(base64), name, w, h }

  // File categorisation, ported from the reference webview (sit / ait / ybe / wbe / cit). Images send
  // as base64 image blocks; PDFs and any text/code file send as `document` blocks. Code files carry an
  // empty MIME in the browser, so the extension allowlist is what lets .php/.py/.js/… through.
  const IMG_MIME = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'];
  const TEXT_MIME = ['application/json', 'application/xml', 'application/javascript', 'application/typescript',
    'application/x-javascript', 'application/x-typescript', 'application/x-yaml', 'application/yaml',
    'application/x-sh', 'application/x-shellscript', 'application/sql', 'application/graphql', 'application/toml', 'application/x-toml'];
  const TEXT_EXT = new Set(('json yaml yml toml ini cfg conf config env properties js jsx ts tsx mjs cjs mts cts ' +
    'py pyw rb go rs java kt kts scala c h cpp hpp cc cxx cs fs fsx swift php pl pm lua r jl ex exs erl hrl clj ' +
    'cljs cljc elm hs ml mli v sv vhd vhdl asm s html htm xhtml xml svg css scss sass less vue svelte astro sh ' +
    'bash zsh fish ps1 psm1 psd1 bat cmd csv tsv sql graphql gql prisma md mdx markdown rst txt text rtf tex ' +
    'latex org adoc asciidoc makefile cmake gradle dockerfile containerfile vagrantfile rakefile gemfile podfile ' +
    'fastfile brewfile procfile lock sum log diff patch gitignore gitattributes editorconfig prettierrc eslintrc ' +
    'babelrc npmrc nvmrc yarnrc').split(' '));
  const NAME_TEXT = ['license', 'readme', 'changelog', 'authors', 'contributors', 'copying'];
  function isTextFile(mime, name) {
    if (mime.indexOf('text/') === 0) return true;
    if (TEXT_MIME.indexOf(mime) >= 0) return true;
    const low = (name || '').toLowerCase();
    return TEXT_EXT.has(low.split('.').pop()) || TEXT_EXT.has(low) || NAME_TEXT.indexOf(low) >= 0;
  }
  function fileKind(file) {
    const mime = (file.type || '').toLowerCase();
    if (IMG_MIME.indexOf(mime) >= 0) return 'image';
    if (mime === 'application/pdf') return 'pdf';
    if (isTextFile(mime, file.name || '')) return 'text';
    return null; // unsupported binary — skipped
  }
  // non-image attachments download via a native save dialog on the Kotlin side; images open the lightbox
  function downloadAtt(p) {
    if (p.data) bridge({ kind: 'download', name: p.name || 'file', media_type: p.media_type, data: p.data });
  }
  // click an attachment chip (composer or sent message): image → lightbox, other file → download
  function openAtt(p) {
    if (!p.data) return;   // past-budget replay chip: name only, nothing to open
    if (p.kind === 'image') openLB('data:' + p.media_type + ';base64,' + p.data, p.name || 'file');
    else downloadAtt(p);
  }
  // chip size label: byte count straight from the base64 length (the base64 IS the file's bytes).
  // fmtSize() (shared with the history panel) renders it as B / KB / MB — B below 1 KB so a tiny file
  // reads "17 B", not a misleading "0 KB"; MB at 1024 KB. Binary thresholds throughout.
  function b64Bytes(b64) {
    if (!b64) return 0;
    let pad = 0;
    if (b64.charCodeAt(b64.length - 1) === 61) pad++;   // '='
    if (b64.charCodeAt(b64.length - 2) === 61) pad++;
    return Math.floor(b64.length * 3 / 4) - pad;
  }

  function attChipHtml(p, withRm, i) {
    // image → thumbnail (or, past the transcript budget, nothing); any other file → a file icon
    const lead = p.kind === 'image'
      ? (p.data ? '<span class="thumb"><img src="data:' + p.media_type + ';base64,' + p.data + '"></span>' : '')
      : SVG_FILE;
    return lead +
      esc(p.name || 'file') +
      (p.data ? '<span class="meta">' + fmtSize(b64Bytes(p.data)) + '</span>' : '') +
      (withRm ? '<button class="rm" title="Remove" data-i="' + i + '">' + SVG_X + '</button>' : '');
  }
  function renderAttachments() {
    chips.innerHTML = '';
    pending.forEach(function (p, i) {
      const chip = document.createElement('span');
      chip.className = p.data ? 'att click' : 'att';
      chip.innerHTML = attChipHtml(p, true, i);
      chip.onclick = function (e) { if (!e.target.closest('.rm')) openAtt(p); };  // image → lightbox, file → download; rm handles itself
      chips.appendChild(chip);
    });
    chips.style.display = pending.length ? 'flex' : 'none';
    Array.prototype.forEach.call(chips.querySelectorAll('.rm'), function (b) {
      b.onclick = function () { pending.splice(+b.dataset.i, 1); renderAttachments(); };
    });
  }
  function addFile(file) {
    const kind = fileKind(file);
    if (!kind) { console.warn('Unsupported file skipped:', file.name); return; }
    const reader = new FileReader();
    reader.onload = function () {
      const url = String(reader.result);      // data:<mt>;base64,<data>
      const data = url.slice(url.indexOf(',') + 1);
      // images keep their real type; pdf/text are normalised (a code file's browser MIME is empty)
      const mt = kind === 'pdf' ? 'application/pdf'
        : kind === 'text' ? 'text/plain'
        : (url.slice(5, url.indexOf(';')) || 'image/png');
      pending.push({ kind: kind, media_type: mt, data: data, name: file.name || 'file' });
      renderAttachments();
    };
    reader.readAsDataURL(file);
  }
  input.addEventListener('paste', function (e) {
    const items = (e.clipboardData && e.clipboardData.items) || [];
    let took = false;
    for (const it of items) {
      if (it.kind === 'file') {
        const f = it.getAsFile();
        if (f && fileKind(f)) { addFile(f); took = true; }
      }
    }
    if (took) e.preventDefault();   // otherwise let a normal text paste through
  });
  ['dragover', 'drop'].forEach(function (ev) { document.addEventListener(ev, function (e) { e.preventDefault(); }); });
  document.addEventListener('drop', function (e) {
    const fs = (e.dataTransfer && e.dataTransfer.files) || [];
    for (const f of fs) addFile(f);   // addFile categorises and skips unsupported
  });
  // Delivery layer for OS file drags (manual-test 2.9): JCEF-on-Linux never turns them into
  // DOM drop events, so ChatPanel's AWT DropTarget reads the files and calls this with
  // [{name, mime, data(b64)}]. Same normalisation as addFile from here; fileKind still
  // gatekeeps, so unsupported binaries are skipped identically to paste/picker.
  window.__dropFiles = function (files) {
    (files || []).forEach(function (f) {
      const kind = fileKind({ type: (f.mime || '').toLowerCase(), name: f.name || '' });
      if (!kind) { console.warn('Unsupported file skipped:', f.name); return; }
      const mt = kind === 'pdf' ? 'application/pdf'
        : kind === 'text' ? 'text/plain'
        : (f.mime || 'image/png');
      pending.push({ kind: kind, media_type: mt, data: f.data, name: f.name || 'file' });
    });
    renderAttachments();
  };
  const fileInput = document.getElementById('fileInput');
  document.getElementById('attachBtn').onclick = function (e) { tg('attachMenu', e); };
  Array.prototype.forEach.call(document.querySelectorAll('#attachMenu .popup-item'), function (item) {
    item.addEventListener('click', function () {
      closeMenus();
      if (item.dataset.act === 'upload') fileInput.click();
      else if (item.dataset.act === 'context') {
        const pos = input.selectionStart || input.value.length;
        input.value = input.value.slice(0, pos) + '@' + input.value.slice(pos);
        input.setSelectionRange(pos + 1, pos + 1);
        input.focus(); updateAuto();
      }
    });
  });
  fileInput.onchange = function () {
    Array.from(fileInput.files).forEach(function (f) { addFile(f); });
    fileInput.value = '';
  };
  document.getElementById('slashBtn').onclick = function (e) { tg('slashMenu', e); };

  /* ---------- send / stop / retry ---------- */
  /**
   * Fold an over-long block: collapsed shows 2 full lines + a fading 3rd, and the WHOLE block is
   * the click-toggle (both directions, no separate button). Measured after a frame so the real
   * height is known; content of ≤3 lines gets no fold and no affordance at all. A click that ends
   * a text selection never toggles (or copying from code/output would be impossible), and clicks
   * on interactive children (links, attachment chips) pass through.
   */
  function foldBlock(elm) {
    if (!elm || elm.dataset.folded) return;
    elm.dataset.folded = '1';
    // An attachment-chip row is overhead, not content: widen the collapsed cap by its height so
    // "3 lines" means 3 TEXT lines — otherwise a chip row leaves ~1 visible line of text.
    // Measured BEFORE .fold is applied (and synchronously, not in the rAF below): deferring it
    // made the box paint one frame at the un-widened cap and then grow, and a sticky element
    // resizing right after it appears is exactly what leaves a stale tile in JCEF. The layout
    // flush this costs is paid only by messages that HAVE attachments, which are rare.
    // Height comes from getBoundingClientRect (offsetHeight rounds, and a rounded-up cap shows
    // a sliver of the next line); the margin is read rather than hardcoded because
    // .msg-atts:last-child (image-only message) drops it to 0.
    const atts = elm.querySelector(':scope > .msg-atts');
    let syncExtra = function () {};
    if (atts) {
      syncExtra = function () {
        const mb = parseFloat(getComputedStyle(atts).marginBottom) || 0;
        elm.style.setProperty('--fold-extra', (atts.getBoundingClientRect().height + mb) + 'px');
      };
      syncExtra();
      // ResizeObserver covers rewraps with no interaction; it does NOT fire in headless
      // Chrome (no compositor), so the toggle below re-measures as well.
      if (window.ResizeObserver) new ResizeObserver(syncExtra).observe(atts);
    }
    elm.classList.add('fold');
    requestAnimationFrame(function () {
      if (elm.scrollHeight <= elm.clientHeight + 4) { elm.classList.remove('fold'); return; }
      elm.title = 'Show more';
      elm.addEventListener('click', function (e) {
        if (e.target.closest('a, button, input, .att')) return;
        if (window.getSelection && String(window.getSelection())) return;
        syncExtra();   // the chip row may have rewrapped since the cap was last measured
        elm.title = elm.classList.toggle('open') ? 'Show less' : 'Show more';
      });
    });
  }
  function foldCode(root) {
    if (root) root.querySelectorAll('.codeblock pre').forEach(foldBlock);
  }

  function addUserMessage(text, imgs) {
    const turnEl = newTurn();
    const d = document.createElement('div');
    d.className = 'msg-user';
    if (imgs.length) {
      const atts = document.createElement('span');
      atts.className = 'msg-atts';
      imgs.forEach(function (p) {
        const chip = document.createElement('span');
        chip.className = p.data ? 'att click' : 'att';
        chip.innerHTML = attChipHtml(p, false, 0);
        if (p.data) chip.onclick = function () { openAtt(p); };   // image → lightbox, file → download
        atts.appendChild(chip);
      });
      d.appendChild(atts);
    }
    if (text) {
      // an element, not a bare text node — .msg-atts:last-child (which drops the chip row's
      // bottom margin on image-only messages) is blind to text nodes
      const t = document.createElement('span');
      t.textContent = text;
      d.appendChild(t);
    }
    turnEl.prepend(d); maybeScroll();   // before .turn-body, and outside its containment
    foldBlock(d);   // long prompts fold to 2 lines + fade; the box itself toggles
    return d;
  }

  let busy = false, stopping = false, effortMuted = false; // effortMuted: swallow a silent /effort turn
  function setBusy(b) {
    busy = b;
    send.title = b ? 'Stop' : 'Send';
    send.classList.toggle('stop', b);
    if (b) showWorking();
    else {
      hideWorking(); workStart = 0; awaitingUser = false;
      // Nothing is in flight once the turn is over. An interrupt, an API error or a process exit
      // ends a turn with a tool_use that never gets a tool_result at all, and that line would go on
      // pulsing for the rest of the session — the same failure .t-prog.run is guarded against
      // above, and the one that left a hidden bg chip pulsing forever (manual-test 7.3, see the
      // .chip-btn[hidden] note in css/60-composer.css). Every setBusy(false) caller is a real end of turn: the
      // one intermediate `result`, where a suspending background task is still pending, returns
      // before reaching here on purpose.
      log.querySelectorAll('.tool-line.run').forEach(function (el) { el.classList.remove('run'); });
    }
  }

  let lastUser = null;                 // { text, images } — for Retry
  let retrySeen = null;                // last "attempt/max" retry line drawn — the wire emits each retry twice (see api_retry)
  let history = [], histIdx = 0, histDraft = ''; // sent-message history for ↑/↓ recall

  function sendTurn(t, imgs) {
    pendingPlanMode = null;   // a parked plan-row mode switch dies with its turn
    addUserMessage(t, imgs);
    lastUser = { text: t, images: imgs };
    if (t && history[history.length - 1] !== t) history.push(t); // record for ↑/↓ recall
    histIdx = history.length; histDraft = '';
    turnTokens = 0; msgTokens = 0; reqTokens = 0; pendingBgTasks = 0; reqSeed = null; retrySeen = null;
    msgStreamed = false;   // this turn's answer must prove its own streaming (or render whole)
    // The roster is deliberately NOT reset here. A background shell outlives the turn that started
    // it — that is what run_in_background means — so wiping it on every message hid work that was
    // still running, and nothing re-advertised it: the CLI emits this level signal only when
    // membership CHANGES, so the roster stayed wrong until the next start/exit. The reset belongs
    // to the CLI process lifetime instead (clearLogUI); see the schema quote there.
    bridge({ kind: 'user', text: t, images: imgs.map(function (p) { return { kind: p.kind, media_type: p.media_type, data: p.data, name: p.name }; }) });
    stopping = false;
    setBusy(true);
    smoothToBottom();
  }

  function clearComposer() { input.value = ''; pending = []; renderAttachments(); autoGrow(); closeMenus(); hideMenu(); slashEscaped = false; mentionEscaped = false; }

  /**
   * Messages typed while a turn is in flight (client-parity item 24).
   *
   * Held CLIENT-side, deliberately. There is no CLI queue to drive: `queue-operation` records exist
   * (2287 locally) but they are the CLI's own message-pipeline bookkeeping — `enqueue` and
   * `dequeue` come in matched pairs, one per turn, whether or not anything was ever queued by a
   * human. The only user-facing operation is `remove` (5 records). So the terminal's queue is a
   * client feature too, and ours holds the text until the turn ends.
   *
   * Sending mid-turn was not merely unsupported, it was HARMFUL: `submit()` had no busy check, so
   * Ctrl+Enter during a turn called `sendTurn`, which resets `reqTokens`, `reqSeed` and the
   * background roster — corrupting the accounting of the request still running, and mis-seeding its
   * completion summary.
   */
  let queue = [];
  function renderQueue() {
    const q = document.getElementById('queue');
    if (!q) return;
    q.hidden = !queue.length;
    q.textContent = '';
    queue.forEach(function (item, i) {
      const row = document.createElement('div');
      row.className = 'q-row';
      const txt = document.createElement('span');
      txt.className = 'q-text';
      // One line each: the composer is not the place to re-read a long draft, and the row is
      // clickable to bring it back for editing anyway.
      txt.textContent = (item.text || '').replace(/\s+/g, ' ');
      txt.title = 'Click to edit';
      txt.onclick = function () {
        // Editing takes the item OUT of the queue: leaving a copy behind would send it twice.
        queue.splice(i, 1);
        input.value = item.text;
        pending = (item.images || []).slice();
        renderAttachments(); renderQueue(); autoGrow(); input.focus();
      };
      const n = (item.images || []).length;
      if (n) {
        const c = document.createElement('span');
        c.className = 'q-att'; c.textContent = n === 1 ? '1 image' : n + ' images';
        txt.appendChild(c);
      }
      const x = document.createElement('button');
      // Same control as the attachment chip's remove: class .rm, same glyph, hover-revealed.
      x.className = 'rm'; x.type = 'button'; x.title = 'Remove'; x.innerHTML = SVG_X;
      x.onclick = function () { queue.splice(i, 1); renderQueue(); };
      row.appendChild(txt); row.appendChild(x);
      q.appendChild(row);
    });
  }
  /**
   * Send the next queued message once the turn that was running has finished.
   *
   * NOT after a Stop: interrupting is a request for everything to stop, and firing the queue into
   * that would be the opposite. The messages stay queued and visible, so nothing typed is lost —
   * dropping them silently is the failure this item exists to fix.
   */
  function drainQueue(interrupted) {
    if (interrupted || busy || !queue.length) return;
    const next = queue.shift();
    renderQueue();
    sendTurn(next.text, next.images || []);
  }

  function submit() {
    let t = input.value.trim();
    if (!t && !pending.length) return;
    // slash-command routing (only for a bare "/cmd [args]" turn with no attachments): native
    // commands are handled in the IDE; TUI-only built-ins are refused rather than sent as literal
    // text to the model; everything else falls through and is sent as a turn for the CLI to expand.
    // The name must be the ENTIRE first token (whitespace or end after it) — a message starting
    // with a path ("/home/x …") is ordinary text, not a command named "home".
    const sc = pending.length ? null : t.match(/^\/([a-z][\w-]*)(?:\s+([\s\S]*))?$/i);
    if (sc) {
      // Aliases resolve to their command before the gate: the CLI advertises `/review` for
      // `/code-review`, and a typed alias must reach the same branch as the name it stands for. The turn is sent under the canonical name too.
      const name = canonicalCmd(sc[1]);
      if (name !== sc[1].toLowerCase()) t = '/' + name + (sc[2] != null ? ' ' + sc[2] : '');
      if (name === 'btw') { clearComposer(); sideOpen(sc[2] || ''); return; }             // side question (8.11)
      if (cmdKind(name) === 'tui') {
        clearComposer();
        statusLine("'/" + name + "' isn't available in the IDE.", SVG_ALERT, 'status err');
        return;
      }
      // else 'text' → fall through and send as a normal turn (CLI expands custom/prompt commands)
    }
    const imgs = pending;
    clearComposer();
    // A turn is already running: hold this one instead of racing it (item 24).
    if (busy) { queue.push({ text: t, images: imgs }); renderQueue(); smoothToBottom(); return; }
    sendTurn(t, imgs);
  }
  send.onclick = function () { if (busy) { stopping = true; bridge({ kind: 'stop' }); } else submit(); };

  function addRetryLine() {
    if (!lastUser) return;
    const r = el('retry', '');
    r.innerHTML = '<a href="#">' + SVG_RETRY + 'Retry</a>';
    r.querySelector('a').onclick = function (e) {
      e.preventDefault();
      if (!busy && lastUser) { r.remove(); sendTurn(lastUser.text, lastUser.images); }
    };
  }

  /* ---------- auto-grow textarea + composer-height tracking ---------- */
  const LINE = 20, MAXLINES = 10;
  function autoGrow() {
    const max = LINE * MAXLINES;
    if (!input.value) {                 // empty: rest at exactly one line (ignore placeholder metrics)
      input.style.height = LINE + 'px';
      input.style.overflowY = 'hidden';
      return;
    }
    input.style.height = 'auto';
    input.style.height = Math.min(input.scrollHeight, max) + 'px';
    input.style.overflowY = input.scrollHeight > max ? 'auto' : 'hidden';
  }
  function syncGutter() {
    const sb = log.offsetWidth - log.clientWidth;   // actual scrollbar width (0 if none)
    composerEl.style.paddingRight = (14 + sb) + 'px';
    // #scrollBtn centres on #app (full panel), but in-block buttons centre inside #log's content
    // box, which the scrollbar narrows — without this they sit sb/2 apart.
    if (scrollBtn) scrollBtn.style.left = sb ? 'calc(50% - ' + (sb / 2) + 'px)' : '50%';
  }
  new ResizeObserver(function () {
    const h = composerEl.offsetHeight;
    const wasAtBottom = atBottom();
    log.style.paddingBottom = (h + 12) + 'px';
    fadeEl.style.height = (h + 18) + 'px';
    if (scrollBtn) scrollBtn.style.bottom = (h + 20) + 'px'; // float just above the composer
    if (wasAtBottom) log.scrollTop = log.scrollHeight;   // stay pinned while typing
    syncGutter(); updateScrollBtn();
  }).observe(composerEl);
  new ResizeObserver(syncGutter).observe(log);
  syncGutter();
  // keep the top fade pinned just under the header (header height can change with the title/actions)
  function syncHeadFade() {
    document.documentElement.style.setProperty('--head-h', document.getElementById('head').offsetHeight + 'px');
  }
  new ResizeObserver(syncHeadFade).observe(document.getElementById('head'));
  syncHeadFade();
  autoGrow();

