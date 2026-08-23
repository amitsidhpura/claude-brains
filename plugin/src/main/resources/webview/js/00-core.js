  const log = document.getElementById('log');
  const input = document.getElementById('input');
  const send = document.getElementById('send');
  const modeChip = document.getElementById('modeChip');
  const modelChip = document.getElementById('modelChip');
  const modeItemsEl = document.getElementById('modeItems');
  const modelItems = document.getElementById('modelItems');
  const histPanel = document.getElementById('histPanel');
  const histList = document.getElementById('histList');
  const chips = document.getElementById('chips');
  const welcome = document.getElementById('welcome');
  const composerEl = document.getElementById('composer');
  const fadeEl = document.getElementById('fade');
  const slashList = document.getElementById('slashList');
  const lightbox = document.getElementById('lightbox');

  // Caps and the description key order, spliced into <head> from RenderLimits.kt so the live
  // renderer and the replay parser (SessionStore) cannot drift apart. Throw rather than fall back
  // to defaults: a default would BE a second copy, and every use below sits inside a try/catch
  // that would swallow the mistake and quietly render half a UI.
  // NB: never write the marker's literal text anywhere but the one spot in <head> — ChatPanel
  // replaces EVERY occurrence, and the replacement carries a closing script tag, which the HTML
  // parser honours even inside a JS comment and would silently truncate this whole block.
  const LIM = window.LIMITS;
  if (!LIM) throw new Error('chat.html: the LIMITS marker in <head> was not spliced — see RenderLimits.kt');

  /* ---------- shared icons ---------- */
  // plain check (lucide check) — confirm buttons, selection marks, copy flash (circled variants retired 2026-07-31)
  const SVG_CHECK = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6 9 17l-5-5"/></svg>';
  // checklist glyphs (lucide circle / circle-dot) — SVG_CHECK above covers `completed`
  const SVG_CIRCLE = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="9"/></svg>';
  const SVG_DOT_OPEN = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="3.5" fill="currentColor" stroke="none"/></svg>';
  // double check (lucide check-check) — the permission-suggestion buttons ("accept all")
  const SVG_CHECKS = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M18 6 7 17l-5-5"/><path d="m22 10-7.5 7.5L13 16"/></svg>';
  const SVG_X = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M18 6 6 18"/><path d="m6 6 12 12"/></svg>';
  const SVG_COMMENT = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M7.9 20A9 9 0 1 0 4 16.1L2 22Z"/></svg>';
  const SVG_ENTER = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 10 4 15 9 20"/><path d="M20 4v7a4 4 0 0 1-4 4H4"/></svg>';
  const SVG_STEP = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M10.029 4.285A2 2 0 0 0 7 6v12a2 2 0 0 0 3.029 1.715l9.997-5.998a2 2 0 0 0 .003-3.432z"/><path d="M3 4v16"/></svg>';
  const SVG_RETRY = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/><path d="M3 3v5h5"/></svg>';
  const SVG_COPY = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect width="14" height="14" x="8" y="8" rx="2" ry="2"/><path d="M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2"/></svg>';
  const SVG_ALERT = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" x2="12" y1="8" y2="12"/><line x1="12" x2="12.01" y1="16" y2="16"/></svg>';
  const SVG_DOWN = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 5v14"/><path d="m19 12-7 7-7-7"/></svg>';
  const SVG_STOP = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" stroke="none"><rect x="2" y="2" width="20" height="20" rx="5"/></svg>';
  // model chip (lucide sparkles) — mirrored in the static #modelChip markup and design/mockup.html
  const SVG_SPARK = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9.937 15.5A2 2 0 0 0 8.5 14.063l-6.135-1.582a.5.5 0 0 1 0-.962L8.5 9.936A2 2 0 0 0 9.937 8.5l1.582-6.135a.5.5 0 0 1 .963 0L14.063 8.5A2 2 0 0 0 15.5 9.937l6.135 1.581a.5.5 0 0 1 0 .964L15.5 14.063a2 2 0 0 0-1.437 1.437l-1.582 6.135a.5.5 0 0 1-.963 0z"/><path d="M20 3v4"/><path d="M22 5h-4"/><path d="M4 17v2"/><path d="M5 18H3"/></svg>';
  const SVG_TRASH ='<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 6h18"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6"/><path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/><line x1="10" x2="10" y1="11" y2="17"/><line x1="14" x2="14" y1="11" y2="17"/></svg>';
  // same glyph as the top-bar history button — a replayed thread is history
  const SVG_HISTORY = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/><path d="M3 3v5h5"/><path d="M12 7v5l4 2"/></svg>';
  const SVG_CHEVRON = '<svg class="chev" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m9 18 6-6-6-6"/></svg>'; // chevron-right; CSS rotates it down when open
  const SVG_FILE = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7z"/><path d="M14 2v4a2 2 0 0 0 2 2h4"/></svg>'; // non-image attachment chip
  const IND = {
    rOff: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/></svg>',
    rOn:  '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21.801 10A10 10 0 1 1 17 3.335"/><path d="m9 11 3 3L22 4"/></svg>', // lucide circle-check-big: filled-in radio
    cOff: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect width="18" height="18" x="3" y="3" rx="2"/></svg>',
    cOn:  '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 10.656V19a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h12.344"/><path d="m9 11 3 3L22 4"/></svg>'
  };

  function esc(s) { const d = document.createElement('div'); d.textContent = s == null ? '' : String(s); return d.innerHTML; }
  // Attribute-context escape: esc() covers < > & but NOT quotes — a value with `"` inside
  // title="..." silently truncates the attribute and leaks the rest into the markup.
  function escA(s) { return esc(s).replace(/"/g, '&quot;'); }

  // Auto-scroll only when the user is already near the bottom (scroll pinning).
  let pinned = true;
  const scrollBtn = document.getElementById('scrollBtn');
  function atBottom() { return log.scrollHeight - log.scrollTop - log.clientHeight < 40; }
  function updateScrollBtn() { if (scrollBtn) scrollBtn.classList.toggle('show', !atBottom()); }
  function scrollToBottom() { pinned = true; log.scrollTop = log.scrollHeight; updateScrollBtn(); }
  /**
   * Scroll to the bottom and keep re-asserting until the height stops changing. Off-screen turns
   * report an ESTIMATED height (content-visibility + contain-intrinsic-size), so the document grows
   * as the real ones resolve near the viewport and a single rAF lands short of the true bottom.
   * Bails out the moment the user scrolls themselves, so it can never fight them.
   */
  function settleToBottom(maxFrames) {
    let last = -1, stable = 0, n = 0, expected = -1;
    (function step() {
      if (expected >= 0 && Math.abs(log.scrollTop - expected) > 4) return; // user took over
      // Target the last ELEMENT, not scrollHeight: with content-visibility, scrollHeight includes
      // estimated heights for skipped turns, so a computed target lands short. scrollIntoView makes
      // the browser resolve the real position of a real element.
      if (log.lastElementChild) log.lastElementChild.scrollIntoView(false);
      scrollToBottom();
      expected = log.scrollTop;
      const h = log.scrollHeight;
      stable = (h === last) ? stable + 1 : 0;
      last = h; n++;
      if (stable < 2 && n < (maxFrames || 30)) requestAnimationFrame(step);
    })();
    // images and highlighting can land well after layout has settled, so re-check a few times
    [120, 350, 800].forEach(function (ms) {
      setTimeout(function () {
        if (expected >= 0 && Math.abs(log.scrollTop - expected) > 4) return; // user took over
        scrollToBottom(); expected = log.scrollTop;
      }, ms);
    });
  }
  // Scroll now, then re-assert on the next frame — many blocks are appended empty via el() (which
  // scrolls) and filled with innerHTML right after, growing ~a line; the rAF lands on final height.
  let scrollRaf = 0;
  function maybeScroll() {
    if (pinned) {
      log.scrollTop = log.scrollHeight;
      if (!scrollRaf) scrollRaf = requestAnimationFrame(function () { scrollRaf = 0; if (pinned) log.scrollTop = log.scrollHeight; });
    }
    updateScrollBtn();
    // A render that leaves the log too short to scroll fires no scroll event, so the toggle would
    // otherwise never be set on a fresh conversation — and the first block drawn WITHOUT a sticky
    // user bubble above it (a local command's stdout, e.g. `/model` on a new conversation) sat
    // washed out under #fade-top (user report 2026-08-16). Same mechanism as the `truncated`
    // marker; el() → maybeScroll() is the one path every render takes.
    updateTopFade();
  }
  // animated glide to bottom (manual easeOutCubic — native smooth-scroll doesn't animate reliably
  // in every CEF build). Recomputes the target each frame so it still lands if content grows.
  function smoothToBottom() {
    const start = log.scrollTop, t0 = performance.now(), dur = 320;
    pinned = true;
    (function step(now) {
      const p = Math.min(1, (now - t0) / dur);
      const target = log.scrollHeight - log.clientHeight;
      log.scrollTop = start + (target - start) * (1 - Math.pow(1 - p, 3));
      if (p < 1) requestAnimationFrame(step);
      else { log.scrollTop = log.scrollHeight; updateScrollBtn(); }
    })(t0);
  }
  // File references open in the editor. Delegated from #log so it covers live, replayed and
  // gallery-rendered paths without every render site wiring its own handler.
  log.addEventListener('click', function (e) {
    // A truncation marker carries its path in a data attribute, not its text: what it SHOWS is
    // prose ("… open full output"), so textContent would send nonsense as a path.
    const cutRef = e.target.closest('.io-cut[data-path]');
    if (cutRef) { bridge({ kind: 'open', path: cutRef.dataset.path }); return; }
    const ref = e.target.closest('.t-desc.path, .card-h code');
    if (!ref) return;
    // dataset.path first: a tool line SHOWS the project-relative path (and an ellipsis where the
    // middle was), so its text is no longer something the editor could open. Same idiom as the
    // .io-cut branch above. The text stays the fallback for surfaces that carry no data attribute.
    const p = (ref.dataset.path || ref.textContent || '').trim();
    // A Read that named a range selects it; everything else opens at the top exactly as before.
    if (p) bridge({ kind: 'open', path: p, line: ref.dataset.line, endLine: ref.dataset.endLine });
  });
  // Direction-based pin (5.14). Only an UPWARD move — user intent; programmatic pin-scrolls
  // only ever go down — releases it, and only when it actually leaves the bottom zone.
  // The old `pinned = atBottom()` re-derived the pin from position on EVERY event, so when
  // content grew between a programmatic scroll and its async scroll event, the handler
  // measured the new taller height, read "not at bottom", and silently unpinned mid-stream —
  // the heavy task-list turn made that race constant. Growth never moves scrollTop, so with
  // the direction rule it can never unpin. renderEarlier only ever INCREASES scrollTop
  // (prepended content pushes the anchor down), so windowed replay can't false-trigger it.
  let lastScrollTop = 0;
  // The top fade only means something while content is scrolled under the header — see
  // `body.at-top #fade-top` in chat.css. Called from the scroll handler AND after any render,
  // because a render can leave the log at the top without a scroll event ever firing.
  function updateTopFade() { document.body.classList.toggle('at-top', log.scrollTop <= 1); }
  log.addEventListener('scroll', function () {
    const up = log.scrollTop < lastScrollTop - 1;
    lastScrollTop = log.scrollTop;
    if (up && !atBottom()) pinned = false;       // wheel/drag/keys away from the bottom
    else if (atBottom()) pinned = true;          // any route back to the bottom re-pins
    updateScrollBtn(); maybeLoadEarlier(); updateTopFade();
  });
  if (scrollBtn) scrollBtn.onclick = function () { smoothToBottom(); input.focus(); };

