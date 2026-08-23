  // The diff/preview body for a replayed edit, or '' when the tool has nothing to show.
  function replayDiff(it) {
    if (it.patch && it.patch.length) return '<div class="diff">' + patchRows(it.patch) + '</div>';
    if (it.edits && it.edits.length) {
      // MultiEdit: one .diff, per-edit sections joined by the same ⋯ separator patchRows
      // uses between hunks. Gutter only on the first edit — Kotlin locates only edits[0].
      return '<div class="diff">' + it.edits.map(function (e, i) {
        return renderEditDiff(e.old_string || '', e.new_string || '', i === 0 ? it.lineStart : null);
      }).join(diffRow('ctx', '  ', null, '⋯')) + '</div>';
    }
    if (it.oldStr != null || it.newStr != null) {
      return '<div class="diff">' + renderEditDiff(it.oldStr || '', it.newStr || '', it.lineStart) + '</div>';
    }
    if (it.content != null) return writeDiffHtml(it.content, typeof it.lineStart === 'number' ? it.lineStart : 1);
    return '';
  }

  // Fills a .card.warn with the resolved-edit surface: what ran, its diff, and a static
  // outcome instead of buttons. Shared by replay (replayCard) and the live auto-approved
  // path (onUserEvent, 4.4) so the two cannot drift. Returns false when there is no body.
  // "Applied" rather than "Accepted" — the record is that the edit ran, not whether a human
  // approved it or an auto mode did.
  function fillAppliedCard(card, it) {
    const body = replayDiff(it);
    if (!body) return false;
    card.innerHTML = '<div class="card-h"><b>' + esc(toolLabel(it.text)) + '</b>' +
      (it.file ? ' on <code></code>' : '') + '</div>' + body +
      '<div class="card-b">' + (it.denied
        ? '<span class="no-t">✗ Rejected</span>'
        : '<span class="ok-t">✓ Applied</span>') + '</div>';
    // fillPath, exactly as the live card and the tool line do — replay must not spell a path
    // differently from the surface it is replaying.
    if (it.file) { const c = card.querySelector('.card-h code'); if (c) fillPath(c, it.file); }
    return true;
  }
  function replayCard(it) {
    // ExitPlanMode replays as the plan card, same surface and wording as the live one
    if (it.plan) {
      const plan = el('card warn', '');
      // Anchored comments (5.6) replay as the same read-only rows the live card keeps after its
      // decision — planCommentRows is the one builder for both. The footer names the count.
      const pcs = it.planComments || [];
      const note = planCmtNote(pcs.length, !!it.planFeedback);
      plan.innerHTML = planCardHtml(it.plan) +
        (pcs.length ? '<div class="plan-cs">' + planCommentRows(pcs, false) + '</div>' : '') +
        '<div class="card-b">' + (it.denied
          ? '<span class="no-t">✗ Kept planning' + (it.planFeedback ? fbQuote(it.planFeedback) : '') + note + '</span>'
          : '<span class="ok-t">✓ Approved' + (it.planFeedback ? fbQuote(it.planFeedback) : '') + note + '</span>') + '</div>';
      highlightAnchors(plan.querySelector('.blk'), pcs);   // same highlighter as the live decide
      return;
    }
    const card = el('card warn', '');
    if (!fillAppliedCard(card, it)) { card.remove(); return; }
    foldBlock(card.querySelector('.diff'));
  }

  // Answered AskUserQuestion: questions with the chosen options checked, no inputs.
  function replayAsk(it) {
    const qs = it.questions || [];
    if (!qs.length) return;
    const answers = it.answers || {};
    const card = el('ask ask-done', '');
    // Mirror renderAsk's structure exactly — tab header + one panel per question — so a resumed
    // card is indistinguishable from a live one that has just been answered.
    let html = askTabsHtml(qs);
    const freeText = []; // per-question "Other" text, assigned after innerHTML (esc() leaves quotes)
    qs.forEach(function (q, i) {
      // answers arrive as one string (multi-select is comma-joined); prefer an exact label
      // match so a single answer containing a comma isn't split apart
      const raw = String(answers[q.question] == null ? '' : answers[q.question]);
      const labels = (q.options || []).map(function (o) { return o.label; });
      const picked = labels.indexOf(raw) >= 0 ? [raw]
        : raw.split(',').map(function (s) { return s.trim(); }).filter(Boolean);
      const indOn = q.multiSelect ? IND.cOn : IND.rOn;
      const indOff = q.multiSelect ? IND.cOff : IND.rOff;
      html += '<div class="ask-panel" data-q="' + i + '"' + (i === 0 ? '' : ' hidden') + '>';
      html += '<div class="ask-q">' + esc(q.question) + '</div><div class="ask-list">';
      (q.options || []).forEach(function (o) {
        const on = picked.indexOf(o.label) >= 0;
        html += '<div class="ask-opt' + (on ? ' checked' : '') + '">' +
          '<span class="ask-ind">' + (on ? indOn : indOff) + '</span>' +
          '<span class="q-body"><span class="q-t">' + esc(o.label) + '</span>' +
          (o.description ? '<span class="q-d">' + esc(o.description) + '</span>' : '') +
          '</span></div>';
      });
      // a free-text answer matches no option — live renders it as the "Other" row plus the
      // (now disabled) text field, so do the same instead of inventing a bespoke row
      const free = picked.filter(function (p) {
        return !(q.options || []).some(function (o) { return o.label === p; });
      });
      freeText[i] = free.join(', ');
      html += '<div class="ask-opt ask-opt-other' + (free.length ? ' checked' : '') + '">' +
        '<span class="ask-ind">' + (free.length ? indOn : indOff) + '</span>' +
        '<span class="q-body"><span class="q-t">Other</span></span></div>';
      html += '<div class="ask-other"' + (free.length ? '' : ' hidden') +
        '><input type="text" disabled></div>';
      html += '</div></div>';
    });
    card.innerHTML = html + '<div class="ask-b">' + (it.denied
      ? '<span class="no-t">✗ Cancelled</span>'   // interrupted before an answer was sent
      : '<span class="ok-t">✓ Answered</span>') + '</div>';

    card.querySelectorAll('.ask-panel').forEach(function (p, i) {
      if (freeText[i]) p.querySelector('.ask-other input').value = freeText[i];
    });
    // tabs stay navigable, exactly as they do on a live card after it has been answered
    wireAskTabs(card);
  }

  function renderBlocks(items) {
    items.forEach(function (it) {
      switch (it.role) {
        case 'user':
          addUserMessage(it.text || '', it.images || []);
          // seed Retry: without this a resumed failed turn had no way back. Replayed attachments
          // past IMAGE_BUDGET carry no data and are dropped by ChatPanel on resend, so a retry
          // after a budget trim sends the text and whatever bytes survived.
          lastUser = { text: it.text || '', images: it.images || [] };
          break;
        case 'assistant': {
          const b = el('blk', ''); b.innerHTML = renderMd(it.text); foldCode(b);
          break;
        }
        // API errors are persisted as assistant records flagged isApiErrorMessage; live draws the
        // same `.error` block from `onResult`. No Retry line: that needs `lastUser`, which a replay
        // never seeds — a resumed failed turn genuinely can't be retried.
        case 'error':
          errorBlock(it.text);
          break;
        // Item 22 — the CLI's own notices, replayed through the SAME level table the live path
        // uses, so a hook-blocked prompt reads identically on resume. No tool_use_id here: dedupe
        // is a live concern (repeated progress for one running tool); the transcript already
        // holds whichever message won.
        case 'info':
          infoLine(it.text, it.level, null);
          break;
        // The top edge of a windowed transcript. Only ever the FIRST block, and only when the
        // parser actually dropped something, so it appears exactly when the reader has scrolled
        // past everything that was kept — where "load earlier" goes quiet and, without this, a
        // truncated window is indistinguishable from the real start of the conversation.
        // The COUNT travels and the wording lives here, the same split `auth` uses below.
        case 'truncated':
          statusLine((it.dropped || 0).toLocaleString() + ' earlier blocks not loaded', SVG_HISTORY);
          break;
        case 'status':
          // icon 'auth' (8.2): `text` is the CLI's error CODE, not prose — resolve it through the
          // same AUTH_BLOCKED map the live path uses, so the wording exists once. The `|| it.text`
          // fallback shows the raw code rather than nothing if the map ever loses a key
          // (RenderLimitsTest pins the alignment with RenderLimits.AUTH_BLOCKED_CODES).
          if (it.icon === 'auth') {
            statusLine(AUTH_BLOCKED[it.text] || it.text, SVG_ALERT, 'status err');
            break;
          }
          statusLine(it.text, it.icon === 'stop' ? SVG_STOP : (it.icon === 'alert' ? SVG_ALERT : null),
            it.icon === 'alert' ? 'status err' : null);
          break;
        case 'tasks':
          // A reconstructed snapshot of the Task* list at this point in the turn. Same renderer as
          // TodoWrite's, because it is the same thing — only the source differs.
          todoList(it.todos || []);
          break;
        case 'compact':
          compactBlock(it.text || '', it.trigger, it.tokens || 0);
          break;
        case 'done': {
          const d = el('done', '');
          d.innerHTML = doneHtml(it.durMs || 0, it.tokens || 0, it.seed, it.prevSeed);
          break;
        }
        case 'thinking': {
          // durMs is record-to-record wall time — an approximation of the live timer — clamped to
          // the same 1s floor live uses, and shown only when a gap exists.
          const secs = it.durMs ? Math.max(1, Math.round(it.durMs / 1000)) : 0;
          (curTurn || log).appendChild(thinkBlock(it.text || '', secs));
          break;
        }
        case 'ask':
          replayAsk(it);
          break;
        case 'tool': {
          const t = toolLine(it.text);
          if (it.isError) t.classList.add('fail');
          if (it.desc) {
            const d = t.querySelector('.t-desc');
            // `fullPath` is the UNCAPPED path (SessionStore sends it beside the DESC_MAX-capped
            // desc) — the click needs the whole thing, the display does not.
            if (it.isPath) fillPath(d, it.fullPath || it.desc, it.desc);
            else d.title = d.textContent = it.desc;   // shown text, not the raw value — see live
            if (it.suffix) {
              const sEl = document.createElement('span');
              sEl.className = 't-sfx'; sEl.textContent = it.suffix;
              d.after(sEl);
              if (it.line) d.dataset.line = it.line;
              if (it.endLine) d.dataset.endLine = it.endLine;
            }
          }
          if (it.todos) todoList(it.todos);
          if (it.cmd || it.out) {
            const rows = [];
            // cut metadata rides the same rows into the same builder as live, so a truncated result
            // reads identically whether it is streaming now or replayed from the transcript
            if (it.cmd) rows.push(['IN', it.cmd, it.cmdCut]);
            if (it.out) rows.push(['OUT', it.out, it.outCut, it.outTotal, it.outFile, it.note, it.interrupted]);
            t.after(ioBox(rows));
          } else if (it.note) {
            // No IN/OUT box to carry it — an Edit or Write, whose result text RESULT_SKIP drops —
            // so the caveat gets its own line (item 11). With a box, the note already rides in the
            // OUT row and must not be drawn twice.
            const n = noteLine(it.note);
            if (n) t.after(n);
          }
          // Tool-returned images (item 8), through the same builder live uses. Kotlin already
          // applied the replay image budget, so an over-budget screenshot arrives without `data`
          // and renders as a "not kept" chip rather than disappearing.
          if (it.images && it.images.length) {
            const box = toolImages(it.images);
            if (box) t.after(box);
          }
          replayCard(it);
          break;
        }
      }
    });
  }

  function renderTranscript(items, more) {
    renderBlocks(items);
    // A session that ended on an API error is retryable now that lastUser is seeded. Only the
    // LAST block qualifies: an error mid-transcript was already recovered from, and its Retry
    // would resend a later message.
    const tail = items[items.length - 1];
    if (tail && tail.role === 'error') addRetryLine();
    if (items.length) { statusLine('Resumed', SVG_HISTORY); }
    updateLoadMore(more || 0);
    // The tail must carry REAL heights before we can land on the true bottom: a turn still showing
    // its contain-intrinsic-size placeholder resolves to a different height the moment it becomes
    // visible, which moves the bottom out from under us. Un-skip the last few so the measurement
    // is honest; the rest keep their estimates and stay cheap.
    Array.prototype.slice.call(log.querySelectorAll('.turn-body')).slice(-8)
      .forEach(function (b) { b.style.contentVisibility = 'visible'; });
    settleToBottom(); // heights still resolve over several frames: images, highlighting, folds
    updateTopFade();  // a transcript too short to scroll fires no scroll event at all
    setTimeout(maybeLoadEarlier, 250);   // tail shorter than the viewport: auto-fill upward
  }

  /* Earlier history loads silently as the user nears the top (600px preload margin), from the
   * existing scroll handler. No visible affordance — chunks are near-instant (Kotlin already holds
   * the parsed list) and prepends are viewport-anchored, so there is nothing to indicate. */
  let earlierRemaining = 0, earlierInFlight = false;
  function requestEarlier() {
    if (!earlierRemaining || earlierInFlight) return;
    earlierInFlight = true;
    bridge({ kind: 'more' });
  }
  function maybeLoadEarlier() {
    if (log.scrollTop <= 600) requestEarlier();
  }
  function updateLoadMore(n) {
    earlierRemaining = n; earlierInFlight = false;
  }


  /**
   * Prepend an earlier chunk without moving what the user is looking at. Blocks are rendered
   * through the normal path (which appends), then moved above the previously-topmost element;
   * scrollTop is shifted by exactly how far that element moved, so the viewport stays put.
   */
  function renderEarlier(items, more) {
    // anchor on the topmost VISIBLE content element — #welcome is present but hidden, and a
    // hidden element's rect is all zeros, which would compute a zero shift and jump the viewport
    let ref = log.firstElementChild;
    while (ref && ref.id === 'welcome') ref = ref.nextElementSibling;
    const savedTurn = curTurn, savedPinned = pinned, savedVerb = lastDoneVerb;
    pinned = false;                              // renderers must not auto-scroll to the bottom
    curTurn = null;                              // a mid-turn fallback chunk must not append to the tail turn
    // This chunk sits ABOVE everything loaded, so the live tail's verb is not what precedes it — null
    // it out and let the chunk's first summary fall back to its own `prevSeed`.
    lastDoneVerb = null;
    const prevCount = log.childNodes.length;
    renderBlocks(items);
    const added = Array.prototype.slice.call(log.childNodes, prevCount);
    const beforeTop = ref ? ref.getBoundingClientRect().top : 0;
    added.forEach(function (n) { log.insertBefore(n, ref); });
    if (ref) log.scrollTop += ref.getBoundingClientRect().top - beforeTop;
    curTurn = savedTurn; pinned = savedPinned;   // live streaming keeps appending to the real tail
    lastDoneVerb = savedVerb;                    // ...and still must not repeat the verb at the bottom
    updateLoadMore(more || 0);
    updateScrollBtn();
    // A prepend can leave the log sitting AT the top with no scroll event to follow — which is
    // exactly the moment the `truncated` marker becomes the first thing on screen.
    updateTopFade();
    maybeLoadEarlier();   // still near the top (fast scroll / short chunk): chain the next one
  }

