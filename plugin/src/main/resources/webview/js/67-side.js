  /* ---------- side question (checklist 8.11 — the TUI's /btw) ----------
     A scratch surface. A question goes to the CLI as a `side_question` control request, which it
     answers on a one-turn fork of the conversation — tools denied, transcript write skipped
     (measured 2026-08-29: `system/control_request_progress{started}` then
     `control_response{response, synthetic}`; no record on disk, no turn on the wire). The answer
     lives only here. The panel keeps its own answered {question, response} pairs and sends them
     as `history` so a follow-up reads as one — the CLI keeps none, and neither does the log.
     Rows are keyed (sq1, sq2, …) and the `__side` frame names the row it answers, so an answer
     that arrives late, or out of order, still lands where it belongs. A new or resumed
     conversation resets the panel: its answers were about the thread that just went away. */
  const sidePanel = document.getElementById('sidePanel');
  const sideList = document.getElementById('sideList');
  const sideInput = document.getElementById('sideInput');
  const sideSend = document.getElementById('sideSend');
  const SIDE_EMPTY = 'Ask a quick question without interrupting the conversation.';
  const SIDE_NO_ANSWER = "Claude didn't return an answer for this side question.";
  let sideHistory = [];   // answered pairs, oldest first — the `history` of the next request
  let sideSeq = 0;        // row ids
  let sideRows = {};      // id -> { q, el } while the answer is pending

  // Anchored to the composer: the box's bottom edge tracks the composer's height (queue rows,
  // attachment chips and a grown textarea all move it), the way #log's padding does — and its
  // right inset COPIES the composer's (syncGutter's scrollbar compensation, read back rather
  // than recomputed, so the two boxes agree even while that value lags a scrollbar change), so
  // they share both edges whether or not the log scrolls (user, 2026-08-29: misaligned in the
  // mockup, which always has a scrollbar; the sandbox check had happened on a short log).
  function sidePlace() {
    sidePanel.style.bottom = (composerEl.offsetHeight + 8) + 'px';
    sidePanel.style.right = composerEl.style.paddingRight || '14px';
  }
  new ResizeObserver(sidePlace).observe(composerEl);
  new ResizeObserver(sidePlace).observe(log);

  /** Open the panel; with a question, ask it at once (the `/btw question` form). */
  function sideOpen(question) {
    closeMenus(); hideMenu(); histPanel.classList.remove('show');
    sidePlace();
    sidePanel.classList.add('show');
    if ((question || '').trim()) sideAsk(question); else sideInput.focus();
  }
  function sideClose() { sidePanel.classList.remove('show'); input.focus(); }
  function sideAsk(q) {
    q = (q || '').trim(); if (!q) return;
    const empty = sideList.querySelector('.side-empty'); if (empty) empty.remove();
    const qEl = document.createElement('div'); qEl.className = 'side-q'; qEl.textContent = q;
    const aEl = document.createElement('div'); aEl.className = 'side-a pending';
    aEl.innerHTML = '<span class="shimmer">Thinking…</span>';
    sideList.appendChild(qEl); sideList.appendChild(aEl);
    sideList.scrollTop = sideList.scrollHeight;
    const id = 'sq' + (++sideSeq);
    sideRows[id] = { q: q, el: aEl };
    bridge({ kind: 'side', id: id, question: q, history: sideHistory.slice() });
    sideInput.value = ''; sideGrow(); sideInput.focus();
  }
  // Kotlin -> page: `__side {id, response|null, synthetic}` or `__side {id, error}`.
  // response:null is the CLI's own "no answer" (its contract, not a transport failure).
  function sideAnswer(ev) {
    const row = sideRows[ev.id]; if (!row) return;
    delete sideRows[ev.id];
    const el = row.el; el.classList.remove('pending');
    if (ev.error) { el.classList.add('err'); el.textContent = ev.error; return; }
    if (ev.response == null || ev.response === '') { el.classList.add('err'); el.textContent = SIDE_NO_ANSWER; return; }
    el.classList.add('blk'); el.innerHTML = renderMd(ev.response); foldCode(el);
    sideHistory.push({ question: row.q, response: ev.response });
    sideList.scrollTop = sideList.scrollHeight;
  }
  function sideClear() {
    sideHistory = []; sideRows = {};
    sideList.innerHTML = '<div class="side-empty">' + SIDE_EMPTY + '</div>';
  }
  function sideReset() { sideClear(); sidePanel.classList.remove('show'); }
  // one line at rest (20px, as #input), grows to 6 — the composer's autoGrow idiom. height is
  // content-box, so scrollHeight (which includes the 4px paddings) is corrected by 8.
  function sideGrow() {
    if (!sideInput.value) { sideInput.style.height = '20px'; sideInput.style.overflowY = 'hidden'; return; }
    sideInput.style.height = 'auto';
    const h = Math.min(sideInput.scrollHeight - 8, 120);
    sideInput.style.height = Math.max(20, h) + 'px';
    sideInput.style.overflowY = sideInput.scrollHeight - 8 > 120 ? 'auto' : 'hidden';
  }
  sideInput.addEventListener('input', sideGrow);
  sideInput.addEventListener('keydown', function (e) {
    if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) { e.preventDefault(); sideAsk(sideInput.value); }
  });
  sideSend.onclick = function () { sideAsk(sideInput.value); };
  document.getElementById('sideClose').onclick = function (e) { e.stopPropagation(); sideClose(); };
  document.getElementById('sideClear').onclick = function (e) { e.stopPropagation(); sideClear(); sideInput.focus(); };
