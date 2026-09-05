  /* ---------- sessions: new / history / resume ---------- */
  // Header title: empty text means an unnamed thread, which reads as "New conversation" rather
  // than a blank strip. Kotlin re-sends after every turn, since the CLI names threads late.
  const convTitle = document.getElementById('convTitle');
  const convTxt = convTitle.querySelector('.t-txt');
  function setTitle(t) {
    const name = (t || '').trim();
    // .t-txt, not the span itself: the rename pencil is its sibling and textContent would eat it.
    convTxt.textContent = name || 'New conversation';
    convTitle.title = name || 'New conversation';
    // An unnamed thread has no transcript on disk yet, so there is no file to append a
    // custom-title record to — the pencil hides rather than offering a click that would fail.
    convTitle.classList.toggle('unnamed', !name);
    if (!name) endRename();
  }

  /**
   * Rename the conversation from the header (the write half of client-parity item 34).
   *
   * The header becomes the editor in place — same idiom as a history row becoming its own delete
   * confirmation, and for the same reason: a dialog for a one-field edit is a mis-click waiting to
   * happen. Kotlin owns WHICH session this is, so the message carries only the name.
   */
  const convEdit = document.getElementById('convEdit');
  const convInput = document.getElementById('convInput');
  const headEl = document.getElementById('head');
  function endRename() { headEl.classList.remove('editing'); }
  function startRename() {
    if (convTitle.classList.contains('unnamed')) return;
    convInput.value = convTxt.textContent;
    headEl.classList.add('editing');
    convInput.focus(); convInput.select();
  }
  function saveRename() {
    const name = convInput.value.trim();
    // Blank is a no-op, not an erase: there is no record that un-names a conversation, and the CLI
    // refuses an empty title too ("title must be non-empty"). Kotlin re-pushes __title on success,
    // so the header shows what actually landed on disk rather than what was typed.
    if (name && name !== convTxt.textContent) bridge({ kind: 'rename', title: name });
    endRename();
  }
  // The whole title opens the editor; the pencil is the hint, not the only way in. One handler on
  // the container covers both, so they can never disagree about what a click does.
  convTitle.onclick = function (e) { e.stopPropagation(); startRename(); };
  convEdit.querySelector('.ok').onclick = saveRename;
  convEdit.querySelector('.no').onclick = endRename;
  convInput.onkeydown = function (e) {
    if (e.key === 'Enter') { e.preventDefault(); saveRename(); }
    // stopPropagation so the document-level Escape rung doesn't also close a menu behind this
    else if (e.key === 'Escape') { e.preventDefault(); e.stopPropagation(); endRename(); }
  };
  document.getElementById('newBtn').onclick = function () { bridge({ kind: 'new' }); setTitle(''); };
  // Reload = re-resume the same session: the transcript on disk is the source of truth, so this
  // both refreshes out-of-band changes and recovers a dead CLI without losing the thread.
  document.getElementById('refreshBtn').onclick = function () { bridge({ kind: 'refresh' }); };
  // Cap a popup list at exactly `n` rows, MEASURED rather than assumed. A row is not a whole number
  // of pixels (54.19px for history here) and its height follows the IDE's font, so a hardcoded
  // "n x row" cap eventually lands a fraction under the real height and shows a scrollbar for a
  // list that visibly fits. Measures the BOTTOM of row n rather than multiplying one row's height,
  // so it also holds where rows differ (a slash command with no description is shorter).
  // MUST be called while the list is VISIBLE: a hidden panel measures 0 and would pin
  // max-height to 0px, silently emptying the list. The CSS value is only a pre-measure fallback.
  function capToRows(list, n, sel) {
    if (!list) return;
    list.style.maxHeight = 'none';   // 'none', not '': clearing the inline style leaves the CSS
                                     // fallback in force and the measurement comes back clamped
    const rows = list.querySelectorAll(sel);
    const box = list.getBoundingClientRect();
    // Never run past either edge of the webview: the history panel grows DOWN, the composer popups
    // grow UP. With no hardcoded cap left, a tall list in a short tool window would otherwise put
    // its last rows out of reach — a cap that scrolls beats rows that can't be seen at all.
    let h = box.height - Math.max(0, -box.top, box.bottom - window.innerHeight);
    // n rows or fewer must never scroll on their own account, so nothing else bounds them — the CSS
    // value is only a pre-measure fallback and is exactly what fails once rows grow with the font.
    if (rows.length > n) {
      const pad = parseFloat(getComputedStyle(list).paddingBottom) || 0;
      h = Math.min(h, rows[n - 1].getBoundingClientRect().bottom - box.top + pad);
    }
    if (h > 0) list.style.maxHeight = Math.ceil(h) + 'px';
  }
  // Set only by the button, so a `sessions` frame that ARRIVES unasked (Kotlin re-pushes the list
  // after a rename or a delete, to keep it true) re-renders the rows without opening the panel.
  // Rendering data and opening a popup used to be the same act here, because asking was the only
  // way a frame could arrive — a rename made that assumption visible by popping the list open.
  let histWanted = false;
  document.getElementById('histBtn').onclick = function (e) {
    e.stopPropagation();
    if (histPanel.classList.contains('show')) { histPanel.classList.remove('show'); return; }
    histWanted = true;
    bridge({ kind: 'history' }); // Kotlin answers with a 'sessions' event -> renderHistory shows the panel
  };
  function renderHistory(items, current) {
    histList.innerHTML = items.length ? '' : '<div class="hist-empty">No past conversations for this project.</div>';
    items.forEach(function (s) {
      const row = document.createElement('div');
      const isCurrent = !!current && s.id === current;
      row.className = isCurrent ? 'hist-item current' : 'hist-item';
      const meta = [fmtWhen(s.time), fmtSize(s.size), fmtTok(s.tokens) + ' tokens'].filter(Boolean).join(' · ');
      row.innerHTML = '<div class="t"><span class="t-txt">' + esc(s.title) + '</span>' +
        (isCurrent ? '<span class="badge">current</span>' : '') +
        '</div><div class="d">' + esc(meta) + '</div>' +
        // Every row deletes, the CURRENT one included: Kotlin routes the live thread through
        // leave-first (fresh conversation, old CLI dies, THEN the file goes — the CLI reopens
        // the transcript per write, so deleting it while live would truncate, not remove).
        '<button class="hist-del" title="Delete conversation">' + SVG_TRASH + '</button>' +
        '<div class="hist-confirm"><span>Delete permanently?</span>' +
          '<button class="yes">' + SVG_CHECK + 'Delete</button>' +
          '<button class="no">' + SVG_X + 'Cancel</button></div>';
      row.onclick = function () { histPanel.classList.remove('show'); bridge({ kind: 'resume', id: s.id }); };
      // arm/disarm in place rather than a modal; stopPropagation so the row doesn't resume
      row.querySelector('.hist-del').onclick = function (e) {
        e.stopPropagation(); row.classList.add('arm');
      };
      row.querySelector('.hist-confirm .no').onclick = function (e) {
        e.stopPropagation(); row.classList.remove('arm');
      };
      row.querySelector('.hist-confirm .yes').onclick = function (e) {
        e.stopPropagation();
        row.remove();                     // optimistic; Kotlin re-pushes the real list
        bridge({ kind: 'delete', id: s.id });
      };
      row.querySelector('.hist-confirm').onclick = function (e) { e.stopPropagation(); };
      histList.appendChild(row);
    });
    // Show only when the user asked, or when the panel is already open (a delete re-pushes the list
    // from under an open panel and must not close it). capToRows measures, so it only runs while
    // the panel is actually visible — a hidden list measures 0 and pins max-height:0px.
    if (histWanted || histPanel.classList.contains('show')) {
      histPanel.style.top = (document.getElementById('head').offsetHeight + 4) + 'px';
      histPanel.classList.add('show');
      capToRows(histList, 5, '.hist-item');   // after .show — see capToRows
    }
    histWanted = false;
  }

  function clearLogUI() {
    pendingPlanMode = null;   // a parked plan-row mode switch dies with its turn
    Array.from(log.children).forEach(function (c) { if (c.id !== 'welcome') c.remove(); });
    if (welcome) welcome.style.display = '';
    updateTopFade();   // emptied log sits at the top; a clamped scrollTop fires no reliable event
    earlierRemaining = 0; earlierInFlight = false;  // never pull a previous session's chunks
    curBubble = null; curRaw = ''; mdPending = false; curThink = null; curThinkRaw = ''; thinkTok = null;
    thinkTokReal = null;   // a stale real count must not leak into the next block's fallback
    curTurn = null; workingEl = null; activeAsk = null;
    // per-session stream state: a stale openTool would keep appending into a detached element,
    // toolsById pins removed DOM for the page lifetime, and a leftover lastUser would let a
    // first-turn Retry resend the PREVIOUS session's message (images included)
    openTool = null; toolsById = {}; lastUser = null;
    infoByTool = {};   // detached lines from the old session would otherwise be updated in place
    taskProg = {};     // same: a stale task_id must not resurrect a removed progress line
    queue = []; renderQueue();   // typed-ahead messages belong to the conversation that is going away
    // retraction state is per-session too: a leftover evicted uuid would delete a NEW session's
    // block if the CLI ever reused one, and pending blocks would be stamped with the old message's id
    curMsgUuid = null; curMsgEls = []; evictedUuids.clear();
    permCards = Object.create(null);   // a cleared session's cards can no longer be answered
    fullTexts = Object.create(null);   // the uncut IN/OUT texts kept for "open in editor" (1.27) go with their rows
    // A stream interrupted by the clear must not mark the NEXT session's first message as already
    // drawn — a /context opening the new conversation would render nothing (found by fixture 47
    // failing only in the full-suite order, exactly the cross-state leak fixture 44 documents).
    msgStreamed = false;
    turnTokens = 0; msgTokens = 0; reqSeed = null; retrySeen = null;
    // The background roster dies with the CLI PROCESS, and __clear is exactly that boundary
    // (ChatPanel pushes it for new conversation, resume, restart and delete-current). The CLI's own
    // schema for the level signal requires this: "The level is per-process: nothing is emitted at
    // startup, so consumers must reset to the empty set whenever the session's CLI process
    // (re)starts and let the next membership change repopulate it." Without it a dead task sat on
    // the chip with no frame ever coming to correct it — and the reset used to live in sendTurn,
    // where it wiped shells that were still running.
    bgTasks = []; pendingBgTasks = 0; stoppingBgTasks = {}; renderBgTasks();
    lastDoneVerb = null;   // the previous session's last verb must not constrain this one's first
    // The MCP notice was cleared off screen with everything else, so the next init must be free to
    // state the same failures again — otherwise a new conversation silently inherits the old one's
    // "already said that" and never mentions its dead servers at all.
    mcpNoticeKey = null;
    // a fresh conversation carries no context; a resume re-seeds it from the __transcript frame,
    // which Kotlin pushes straight after this clear
    ctxUsed = 0; renderContext();
    hideWorking();
    setBusy(false);
  }

