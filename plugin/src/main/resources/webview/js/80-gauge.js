  /* ---------- context gauge ----------
   * Context in use is the LATEST request's prompt size (input + cache read + cache creation), not
   * a running sum: every request re-sends the whole conversation, so the newest number IS the
   * current size. The window is not in the stream — take it from the models payload when the CLI
   * offers one, otherwise infer, since a prompt above the standard window can only mean 1M.
   */
  const CTX_STD = 200000, CTX_1M = 1000000, CTX_WARN_PCT = 50;
  let ctxUsed = 0, ctxWindowFromCli = 0;
  const ctxChip = document.getElementById('ctxChip');
  function ctxWindow() {
    const w = ctxWindowFromCli || CTX_STD;
    return ctxUsed > w ? CTX_1M : w;   // a prompt over the known window can only be the 1M variant
  }
  /**
   * The initialize payload carries no numeric window; the 1M variants are tagged "[1m]" — on
   * `resolvedModel` for default/opus (claude-opus-5[1m]) but on `value` for fable
   * (claude-fable-5[1m], resolvedModel plain), so both fields have to be checked.
   */
  function windowOf(m) {
    if (!m) return 0;
    return /\[1m\]/i.test(String(m.resolvedModel || '') + ' ' + String(m.value || '')) ? CTX_1M : CTX_STD;
  }
  /**
   * Item 17a: the AUTHORITATIVE window, replacing the `[1m]` tag-sniffing above for as long as the
   * CLI keeps sending it — the same "real number beats our estimate" arrangement item 19 uses for
   * thinking tokens, and the tag heuristic stays as the seed because this only arrives later.
   *
   * `modelUsage` rides on the `result` event, so the earliest it can speak is the END of the first
   * turn; there is no window figure in the initialize payload at all (probed 2.1.222 — its keys are
   * account, agents, available_output_styles, commands, models, output_style, pid and the fast-mode
   * / remote-control pair, and nothing else).
   *
   * It is a MAP, not a value, and that is the whole difficulty. One ordinary turn came back keyed by
   * BOTH `claude-opus-5[1m]` (contextWindow 1000000) and `claude-haiku-4-5-20251001` (200000),
   * because the CLI runs small side models for its own errands. Picking the wrong entry would set
   * the denominator to a fifth of the truth and drive the gauge past 100%, so the match must be to
   * OUR model specifically: the raw key first (it carries the `[1m]` tag, exactly as `currentModel`
   * does), then `canonicalModel` for the case where the CLI reports a resolved id we do not hold.
   * No match means NO update — the seed heuristic is a decent guess and a wrong number is not.
   */
  function windowFromUsage(usage, model) {
    if (!usage || typeof usage !== 'object' || !model) return 0;
    const exact = Object.prototype.hasOwnProperty.call(usage, model) ? usage[model] : null;
    let hit = exact;
    if (!hit) {
      const k = Object.keys(usage).find(function (key) {
        const e = usage[key];
        return e && (e.canonicalModel === model || key.replace(/\[1m\]$/i, '') === model);
      });
      hit = k ? usage[k] : null;
    }
    const w = hit && hit.contextWindow;
    return typeof w === 'number' && w > 0 ? w : 0;
  }
  function setContext(used) {
    if (!(used > 0)) return;
    ctxUsed = used;
    renderContext();
  }
  /**
   * The gauge ring in front of the percentage — the same figure as a shape, so the chip reads
   * without parsing digits.
   *
   * Geometry is the composer's Lucide geometry, not its own: a 24 viewBox with the shape spanning
   * units 3..21, which is what `r=9` about (12,12) draws and what the square-slash beside it draws
   * as `<rect x=3 width=18>`. `pathLength="100"` redefines the circumference as 100 units, so the
   * dash array in chat.css is literally the percentage — no 2*pi*r constant to keep in step.
   *
   * Built ONCE and kept: the chip is now [ring, text node], so the `textContent = pct + '%'` this
   * function used to do would delete the <svg> on the first update and never bring it back.
   */
  const CTX_RING = '<svg class="ctx-ring" viewBox="0 0 24 24" aria-hidden="true">' +
    '<circle class="track" cx="12" cy="12" r="9" pathLength="100"></circle>' +
    '<circle class="fill" cx="12" cy="12" r="9" pathLength="100"></circle></svg>';
  let ctxNum = null;
  function ctxLabel() {
    if (ctxNum && ctxNum.isConnected) return ctxNum;
    ctxChip.classList.add('gauge');
    ctxChip.innerHTML = CTX_RING;
    ctxNum = ctxChip.appendChild(document.createTextNode(''));
    return ctxNum;
  }
  function renderContext() {
    if (!ctxChip) return;
    if (!ctxUsed) {   // clear, not just hide, so a stale figure can never flash back
      ctxChip.hidden = true; ctxLabel().nodeValue = ''; ctxChip.title = '';
      ctxChip.classList.remove('warn');
      ctxChip.style.removeProperty('--ctx-pct');   // and no stale arc behind the next first render
      return;
    }
    const win = ctxWindow();
    const pct = Math.min(100, Math.round(ctxUsed / win * 100));
    ctxChip.hidden = false;
    ctxLabel().nodeValue = pct + '%';
    // Set beside the text, from the same number, so the ring and the digits cannot disagree.
    ctxChip.style.setProperty('--ctx-pct', pct);
    ctxChip.classList.toggle('warn', pct >= CTX_WARN_PCT);
    ctxChip.title = fmtTok(ctxUsed) + ' of ' + fmtTok(win) + ' context used — click to /compact';
  }
  // Go through sendTurn, exactly as typing "/compact" and pressing Enter does: a bare
  // bridge({kind:'user'}) reaches the CLI but creates no turn id, no user block and no busy
  // state, so the panel sits there looking like the click did nothing.
  if (ctxChip) ctxChip.onclick = function () {
    if (busy || !ctxUsed) return;     // request in flight, or nothing to compact yet
    sendTurn('/compact', []);
  };
  // The roster opens through the same dropdown system as the other menus, so it closes on Escape,
  // on an outside click, and whenever another menu opens — without repeating any of that here.
  { const bgChip = document.getElementById('bgChip');
    if (bgChip) bgChip.onclick = function (e) { tg('bgMenu', e); }; }

  /**
   * Display name for any tool: drop an `mcp_`/`mcp__` prefix, then Pascal-case what remains,
   * splitting on runs of underscores. One rule for every tool rather than a special case —
   * `mcp__playwright__browser_click` reads as `PlaywrightBrowserClick`, keeping the server for
   * provenance, while names that are already Pascal-case (Read, ExitPlanMode) pass through
   * untouched because only the first letter of each segment is forced up.
   * DISPLAY only: `openTool.name` / `ev.tool` comparisons keep the raw id.
   */
  function toolLabel(name) {
    const raw = String(name || 'tool');
    return raw.replace(/^mcp_+/, '')
      .split(/_+/).filter(Boolean)
      .map(function (w) { return w.charAt(0).toUpperCase() + w.slice(1); })
      .join('') || raw;
  }


  function fmtSize(b) {
    b = Number(b) || 0;
    if (!b) return '';
    if (b < 1024) return b + ' B';
    if (b < 1048576) return Math.round(b / 1024) + ' KB';
    return (b / 1048576 >= 10 ? Math.round(b / 1048576) : Math.round(b / 104857.6) / 10) + ' MB';
  }
  function fmtTok(n) {
    n = Number(n) || 0;
    if (n < 1000) return String(n);
    // whole-session totals reach millions, so roll over rather than printing "23695k"
    if (n >= 1000000) {
      const m = n / 1000000;
      return (m >= 100 ? Math.round(m) : Math.round(m * 10) / 10).toString() + 'M';
    }
    const k = n / 1000;
    return (k >= 100 ? Math.round(k) : Math.round(k * 10) / 10).toString() + 'k';
  }
  function fmtDur(ms) {
    let s = Math.max(1, Math.round(ms / 1000));
    const h = Math.floor(s / 3600); s -= h * 3600;
    const m = Math.floor(s / 60); s -= m * 60;
    const parts = [];
    if (h) parts.push(h + 'h');
    if (m) parts.push(m + 'm');
    if (s || !parts.length) parts.push(s + 's');
    return parts.join(' ');
  }

  /**
   * Everything a `result` frame teaches the footer and the gauge, in one callable piece so the
   * harness can drive it without rendering a whole result's chrome:
   * - item 17a: the AUTHORITATIVE window → gauge denominator;
   * - the same window → the 1M switch (oneMFromCli), so the switch shows the REAL context size
   *   from the first message after a model change (fable reports 1M whatever the tag says);
   * - fast-mode truth (state + reason), snapping an optimistic toggle back when the account
   *   gates it and tracking on/cooldown/off across turns.
   */
  function reconcileFromResult(ev) {
    const w = windowFromUsage(ev.modelUsage || ev.model_usage, currentModel);
    if (w && w !== ctxWindowFromCli) { ctxWindowFromCli = w; renderContext(); }
    if (w) { oneMFromCli = w >= CTX_1M; syncModelFooter(); }
    if (typeof ev.fast_mode_state === 'string') {
      fastModeState = ev.fast_mode_state;
      fastModeReason = ev.fast_mode_disabled_reason || '';
      syncModelFooter();
    }
  }
  // Checklist 1.24: `result.terminal_reason` (19 values, protocol doc § 6) says WHY the turn ended.
  // "completed" is the normal case and stays silent; a user stop already reads "Stopped" (the
  // aborted_* reasons are exactly that); everything else — max_turns, prompt_too_long,
  // budget_exhausted, stop_hook_prevented, … — is a fact the summary line would otherwise hide.
  // Wording is the CLI's own token with underscores as spaces: 19 values is too many to rename
  // by hand, and a reader who greps the protocol doc should find the same word. Measured live
  // 2026-08-29 (2.1.251): a normal turn carries terminal_reason:"completed".
  const SILENT_END = { completed: 1, aborted_streaming: 1, aborted_tools: 1, background_requested: 1 };
  function turnEndReason(ev) {
    const r = ev.terminal_reason;
    if (typeof r !== 'string' || !r || SILENT_END[r] || stopping) return;
    (curTurn || log).appendChild(statusLine('Turn ended early · ' + r.replace(/_/g, ' '), SVG_ALERT, 'status err'));
  }
  function onResult(ev) {
    msgStreamed = false;   // an interrupted stream must not mark the next message as drawn
    const usage = ev.usage || {};
    if (typeof usage.output_tokens === 'number') reqTokens += usage.output_tokens;
    // Item 17a and the footer switches. Deliberately ABOVE the background-task early return
    // below: an intermediate result carries modelUsage too, and the window is worth taking from
    // the first one that offers it rather than waiting for the turn to finish suspending. A miss
    // returns 0 and leaves the tag-derived seed alone.
    reconcileFromResult(ev);
    flushMd();
    curBubble = null;
    finishThinking();
    // A background subagent suspends the turn: the CLI emits an intermediate `result` while the
    // task runs, then resumes and emits the real one after it reports back (same session_id, so
    // that can't discriminate). Don't finalize — no summary, keep the loader spinning — while any
    // SUSPENDING task is still pending. pendingBgTasks already excludes background shells
    // (task_type "local_bash", see background_tasks_changed): a shell outlives its turn, so for
    // those this result IS the true end. background_tasks_changed empties just before the last
    // result in the subagent case.
    if (!ev.is_error && pendingBgTasks > 0) return;
    // Total request wall-clock (workStart persists across suspends since we don't setBusy(false)
    // on the intermediate results); reqTokens is summed across every turn in the request.
    const durMs = workStart ? Date.now() - workStart : (typeof ev.duration_ms === 'number' ? ev.duration_ms : 0);
    const outTok = reqTokens || (turnTokens + msgTokens);
    setBusy(false);
    if (ev.is_error) {
      // error_max_turns / error_max_budget_usd / … arrive with result:null and the readable text
      // in `errors[]` (measured 2026-08-29, 2.1.251: errors:["Reached maximum number of turns (1)"]);
      // the subtype token is the last resort, not the first.
      const errs = Array.isArray(ev.errors) ? ev.errors.filter(function (e) { return typeof e === 'string' && e; }) : [];
      const resultText = (typeof ev.result === 'string' && ev.result) ? ev.result
        : errs.length ? errs.join('\n') : (ev.subtype || 'error');
      // drain stashed synthetic texts the result does NOT literally repeat — chronologically first
      syntheticEcho.forEach(function (t) { if (t !== resultText) errorBlock(t); });
      if (stopping) { statusLine('Stopped', SVG_STOP); }
      else {
        // the one copy of the taped API-error echo — its identical synthetic twin was stashed
        errorBlock(resultText);
        addRetryLine();
      }
    } else {
      // a synthetic echo with no error result at all — drain, so the dedupe can never swallow one
      syntheticEcho.forEach(function (t) { errorBlock(t); });
      const done = el('done', '');
      // reqSeed makes this line's verb survive a resume: the parser hashes the same uuid.
      done.innerHTML = doneHtml(durMs, outTok, reqSeed);   // time always; token segment only when non-zero
    }
    turnEndReason(ev);
    syntheticEcho = [];   // turn-scoped: a stale echo must not leak into the next result
    // The queue waits for the request to END, not for the stream to go quiet: draining here means
    // a follow-up never lands mid-turn. `stopping` is still true for an interrupted turn, which is
    // exactly when the queue must NOT fire — read it before it is cleared.
    const wasStopped = stopping;
    stopping = false;
    drainQueue(wasStopped);
  }

