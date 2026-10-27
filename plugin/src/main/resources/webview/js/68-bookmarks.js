  /* ---------- bookmarks (checklist 8.15 — VS Code's Bookmarks, as a card) ----------
     Keep chosen replies at hand while working through them. A bookmark is the assistant
     record's uuid — the same `data-uuid` stampMessage puts on live blocks and replay copies
     from the transcript — never a copy of the text: Kotlin keeps {uuid, addedAt, writtenAt}
     per conversation (BookmarkStore, VS Code's file shape) and reads the text back from the
     transcript when it ships the list. The page holds the text of a bookmark it made itself
     (the block's markdown source) until the store's copy arrives, so the detail box never
     flashes empty on a reply whose record the CLI has not flushed yet.
     Card = the side card's shell above the composer (and above the side card when both are
     open); rows oldest first, as the store orders them; one row's reply in the detail box.
     Kotlin → page: `__bookmarks {bookmarks:[{uuid, addedAt, writtenAt, text|null}]}` on
     resume / refresh and after every change. Page → Kotlin: `bookmark {uuid, on}`. */
  const bmCard = document.getElementById('bmCard');
  const bmBtn = document.getElementById('bmBtn');
  const bmCount = document.getElementById('bmCount');
  const bmBody = bmCard.querySelector('.bm-body');
  const BM_EMPTY = 'Bookmark a response with its bookmark button to keep it here.';
  const BM_GONE = "This response can't be found in the conversation's history.";
  let bookmarks = [];   // {uuid, addedAt, writtenAt, text|null}
  let bmSel = null;     // uuid shown in the detail box; null = list only (a row click toggles it)

  // Anchored like #sidePanel (composer height + 8, the composer's scrollbar inset read back);
  // when the side card is open this one sits above it.
  function bmPlace() {
    const side = sidePanel.classList.contains('show') ? sidePanel.offsetHeight + 8 : 0;
    bmCard.style.bottom = (composerEl.offsetHeight + 8 + side) + 'px';
    bmCard.style.right = composerEl.style.paddingRight || '14px';
  }
  // What the card adds to the log's bottom padding (padLog, js/60-composer.js), so the newest
  // output is never under it — the one way it differs from the side card, which floats.
  function bmExtra() { return bmCard.classList.contains('show') ? bmCard.offsetHeight + 8 : 0; }
  function bmOpen() {
    closeMenus(); hideMenu(); histPanel.classList.remove('show');
    bmPlace(); bmCard.classList.add('show'); bmBtn.classList.add('on'); padLog();
  }
  function bmClose() { bmCard.classList.remove('show'); bmBtn.classList.remove('on'); padLog(); }
  function bmToggleCard() { if (bmCard.classList.contains('show')) bmClose(); else bmOpen(); }
  new ResizeObserver(bmPlace).observe(composerEl);
  new ResizeObserver(bmPlace).observe(sidePanel);
  new ResizeObserver(function () { padLog(); }).observe(bmCard);

  function bmHas(uuid) { return bookmarks.some(function (b) { return b.uuid === uuid; }); }
  function bmFind(uuid) { return bookmarks.find(function (b) { return b.uuid === uuid; }) || null; }
  function bmBlock(uuid) { return uuid ? log.querySelector('.blk[data-uuid="' + CSS.escape(uuid) + '"]') : null; }

  // The row label: the reply's first non-empty line with its markdown markers stripped.
  function bmFirstLine(text) {
    const lines = String(text == null ? '' : text).split('\n');
    for (let i = 0; i < lines.length; i++) {
      const l = lines[i].replace(/^\s*(?:#{1,6}\s+|[-*+]\s+|\d+[.)]\s+|>\s*|```.*)+/, '').replace(/[`*_~]/g, '').trim();
      if (l) return l;
    }
    return '(empty response)';
  }
  function fmtAge(t) {
    const s = Math.max(0, Math.round((Date.now() - (t || 0)) / 1000));
    if (!t) return '';
    if (s < 60) return 'just now';
    if (s < 3600) return Math.floor(s / 60) + 'm ago';
    if (s < 86400) return Math.floor(s / 3600) + 'h ago';
    const d = Math.floor(s / 86400);
    return d < 7 ? d + 'd ago' : fmtDate(new Date(t));
  }

  /* Every bookmark control in the log mirrors the list: filled on a bookmarked block. Run on
     each change and whenever a block gets its uuid (stampMessage, replay), since the control is
     added by copyable() before the uuid lands. */
  function bmMarkOne(k) {
    const m = k && k.querySelector(':scope > .blk-mark'); if (!m) return;
    const on = !!(k.dataset.uuid && bmHas(k.dataset.uuid));
    m.classList.toggle('on', on);
    m.title = on ? 'Remove bookmark' : 'Bookmark response';
  }
  function bmMarkSync() { Array.prototype.forEach.call(log.querySelectorAll('.blk-mark'), function (m) { bmMarkOne(m.parentNode); }); }

  function bmRender() {
    bmCount.textContent = bookmarks.length ? String(bookmarks.length) : '';
    bmMarkSync();
    if (!bookmarks.length) { bmSel = null; bmBody.innerHTML = '<div class="bm-empty">' + BM_EMPTY + '</div>'; return; }
    if (bmSel && !bmHas(bmSel)) bmSel = null;
    const list = document.createElement('div'); list.className = 'bm-list';
    bookmarks.forEach(function (b) {
      const row = document.createElement('div');
      row.className = 'bm-row' + (b.uuid === bmSel ? ' sel' : ''); row.dataset.uuid = b.uuid;
      const t = document.createElement('span'); t.className = 'bm-t';
      t.textContent = b.text == null ? BM_GONE : bmFirstLine(b.text);
      const age = document.createElement('span'); age.className = 'bm-age'; age.textContent = fmtAge(b.addedAt);
      row.appendChild(t); row.appendChild(age); list.appendChild(row);
    });
    const b = bmFind(bmSel);
    if (!b) { bmBody.replaceChildren(list); return; }   // nothing open: the rows alone
    const detail = document.createElement('div'); detail.className = 'bm-detail';
    const dh = document.createElement('div'); dh.className = 'bm-dh';
    const jump = document.createElement('span'); jump.className = 'bm-jump'; jump.textContent = 'Show in conversation';
    if (!bmBlock(b.uuid)) jump.style.display = 'none';   // the block is not on screen (older than the replayed window, or withdrawn)
    const rm = document.createElement('button'); rm.type = 'button'; rm.className = 'icon-btn bm-rm'; rm.title = 'Remove bookmark';
    rm.innerHTML = SVG_BM;
    dh.appendChild(jump); dh.appendChild(rm); detail.appendChild(dh);
    if (b.text == null) {
      const g = document.createElement('div'); g.className = 'bm-gone'; g.textContent = BM_GONE; detail.appendChild(g);
    } else {
      const k = document.createElement('div'); k.className = 'blk bm-text';
      k.innerHTML = renderMd(b.text); foldCode(k); detail.appendChild(k);
    }
    bmBody.replaceChildren(list, detail);
  }

  // Scroll the bookmarked block into the middle of the log and flash it. False when it is not
  // in the DOM — the link is hidden then, so this is only reached from code.
  function bmJump(uuid) {
    const n = bmBlock(uuid); if (!n) return false;
    n.scrollIntoView({ block: 'center' });
    n.classList.remove('bm-flash'); void n.offsetWidth; n.classList.add('bm-flash');
    return true;
  }

  // The bookmark control's click (live or replayed block): toggle, draw, tell Kotlin. A block
  // with no uuid yet has no control (css), so `uuid` is always set here.
  function bmToggle(uuid, text) {
    if (!uuid) return;
    const had = bmHas(uuid), wasEmpty = !bookmarks.length;
    if (had) { bookmarks = bookmarks.filter(function (b) { return b.uuid !== uuid; }); }
    else { bookmarks.push({ uuid: uuid, addedAt: Date.now(), writtenAt: null, text: text == null ? null : String(text) }); bmSel = uuid; }
    bmRender();
    if (!had && wasEmpty && !bmCard.classList.contains('show')) bmOpen();   // the first bookmark shows where it went
    bridge({ kind: 'bookmark', uuid: uuid, on: !had });
  }
  // A withdrawn record (evictUuids) takes its bookmark with it — the link would point at nothing.
  function bmDrop(uuid) {
    if (!bmHas(uuid)) return;
    bookmarks = bookmarks.filter(function (b) { return b.uuid !== uuid; });
    bmRender();
    bridge({ kind: 'bookmark', uuid: uuid, on: false });
  }
  // Kotlin's list is the truth; a text it could not read yet keeps the page's own copy.
  function bmSet(list) {
    const old = {}; bookmarks.forEach(function (b) { old[b.uuid] = b; });
    bookmarks = (Array.isArray(list) ? list : []).filter(function (b) { return b && typeof b.uuid === 'string'; })
      .map(function (b) {
        const prev = old[b.uuid];
        return { uuid: b.uuid, addedAt: +b.addedAt || (prev && prev.addedAt) || Date.now(), writtenAt: b.writtenAt == null ? null : +b.writtenAt,
                 text: b.text != null ? String(b.text) : (prev ? prev.text : null) };
      });
    bmRender();
  }
  function bmReset() { bookmarks = []; bmSel = null; bmClose(); bmRender(); }

  bmBtn.addEventListener('click', function (e) { e.stopPropagation(); bmToggleCard(); });
  bmCard.addEventListener('click', function (e) {
    e.stopPropagation();
    if (e.target.closest('#bmClose')) { bmClose(); return; }
    if (e.target.closest('#bmMin')) { bmCard.classList.toggle('min'); padLog(); return; }
    if (e.target.closest('.bm-rm')) { if (bmSel) bmToggle(bmSel, null); return; }
    if (e.target.closest('.bm-jump')) { if (bmSel) bmJump(bmSel); return; }
    const row = e.target.closest('.bm-row');
    // the open row closes again on a second click (user, 2026-10-28: "complete toggle")
    if (row) { bmSel = bmSel === row.dataset.uuid ? null : row.dataset.uuid; bmRender(); }
  });
  log.addEventListener('click', function (e) {
    const btn = e.target.closest('.blk-mark');
    if (!btn) return;
    e.stopPropagation();
    const k = btn.parentNode;
    bmToggle(k.dataset.uuid, typeof k.__md === 'string' ? k.__md : k.textContent);
  });
  // Ages count on while the card is open: re-labelled every 30s rather than re-rendered, so an
  // open reply's scroll position and folds are left alone ("just now" stood still — 2026-10-28).
  setInterval(function () {
    if (!bmCard.classList.contains('show') || bmCard.classList.contains('min')) return;
    Array.prototype.forEach.call(bmCard.querySelectorAll('.bm-row'), function (r) {
      const b = bmFind(r.dataset.uuid), a = r.querySelector('.bm-age');
      if (b && a) a.textContent = fmtAge(b.addedAt);
    });
  }, 30000);
  bmRender();
