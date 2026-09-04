  /* ---------- retraction: uuid stamping, so withdrawn content can be found again ----------
     Rendering is driven by stream_event deltas, which carry no record uuid; the whole-message
     `assistant` record does, and it is the SAME uuid the CLI writes into the transcript. So blocks
     are collected as they are built and stamped when that record lands. Order is not assumed —
     a block created after the uuid is known is stamped on creation instead.
     NOTHING here has ever run against real data: no local transcript contains a refusal fallback or
     a `supersedes` field. It is written from the VS Code bundle's wire shape, so the failure mode is
     deliberately "does nothing" rather than "removes the wrong thing". */
  let curMsgUuid = null;      // record uuid of the message currently streaming
  let curMsgEls = [];         // blocks built for it, awaiting that uuid
  const evictedUuids = new Set();   // withdrawn before we had rendered them — drop on arrival
  function track(node) {
    if (!node) return node;
    if (curMsgUuid) {
      node.dataset.uuid = curMsgUuid;
      // withdrawn while off-screen or before this block existed: never show it at all
      if (evictedUuids.has(curMsgUuid)) { node.remove(); return node; }
    } else curMsgEls.push(node);
    return node;
  }
  function stampMessage(uuid) {
    if (!uuid) return;
    curMsgUuid = uuid;
    curMsgEls.forEach(function (n) { n.dataset.uuid = uuid; });
    if (evictedUuids.has(uuid)) curMsgEls.forEach(function (n) { n.remove(); });
    curMsgEls = [];
  }

  /**
   * Remove blocks the CLI has withdrawn, and SAY SO. Content vanishing without explanation is the
   * same defect as a silent truncation (docs/limits.md, "no silent caps") — the user would be left
   * wondering what they had just read. Two guards, because this is the one path here that can do
   * harm if the wire shape is not what we inferred:
   *   · only exact `data-uuid` matches are touched, so an unexpected uuid removes nothing;
   *   · a `.msg-user` is never removed — the model cannot withdraw something the user typed.
   */
  function evictUuids(uuids, reason) {
    if (!Array.isArray(uuids) || !uuids.length) return 0;
    let gone = 0;
    uuids.forEach(function (u) {
      if (typeof u !== 'string' || !u) return;
      evictedUuids.add(u);                    // may arrive before the block it names
      Array.prototype.forEach.call(log.querySelectorAll('[data-uuid="' + CSS.escape(u) + '"]'), function (n) {
        if (n.classList.contains('msg-user') || n.closest('.msg-user')) return;
        n.remove(); gone++;
      });
    });
    if (gone) statusLine(gone + (gone === 1 ? ' message' : ' messages') + ' withdrawn' +
      (reason ? ' (' + reason + ')' : ''), SVG_ALERT);
    return gone;
  }

  /**
   * `system/model_refusal_fallback`: a safety classifier flagged the exchange, so the CLI retries on
   * a different model and withdraws what it had already sent. VS Code additionally offers a settings
   * gate ("when off, your session will pause instead") and a prompt dialog behind an experiment
   * flag; neither is ours to own — configuration is the terminal's half (CLAUDE.md § Philosophy).
   * We show what happened, follow the model, and drop the retracted blocks.
   */
  function onRefusalFallback(ev) {
    // Both spellings, deliberately: the CLI's emission site writes camelCase keys while the VS Code
    // validator reads snake_case — the same split compactMetadata/compact_metadata has, handled the
    // same way. Guessing one spelling is how a built-blind handler dies silently.
    const orig = ev.original_model || ev.originalModel || '',
          fb = ev.fallback_model || ev.fallbackModel || '';
    // `scope` decides whether the SESSION model actually changed, and the wire schema states the
    // rule in its own words: "'session': the main thread fell back and the session model is
    // swapped. 'local': a subagent / side-question (/btw) / background fork fell back — only that
    // response came from the fallback model and the session model is unchanged. Absent from older
    // CLIs (treat as 'session')." That last clause is why the default is session, not local.
    //
    // Verified 2026-08-05 against 2.1.222, and it was a REAL BUG: this handler followed the chip
    // unconditionally, so a subagent falling back silently repainted the composer's model and left
    // it there — every later turn then reported a model the session had never switched to.
    const local = (ev.scope || 'session') === 'local';
    function label(v) {
      const m = allModels().find(function (x) { return x.value === v; });
      return m ? chipName(m) : prettyModel(v);
    }
    if (fb && !local) {
      // FOLLOW the CLI, do not instruct it: it has already switched, so going through setModel()
      // would bridge a `model` message back telling it what it just told us. Same display-only
      // path the `__models` handler uses.
      currentModel = fb;
      setModelChip(label(fb), fb);
      renderModels();
    }
    const bits = [];
    bits.push(ev.content ||
      'Safeguards flagged this message. This sometimes happens with safe, normal conversations.');
    // Two different facts, so two different sentences. Saying "Switched to X" for a local fallback
    // would describe a session change that did not happen — the same misreport the chip made.
    if (fb && local) bits.push('That response came from ' + label(fb) + '; the session model is unchanged.');
    else if (orig && fb) bits.push('Switched from ' + label(orig) + ' to ' + label(fb) + '.');
    statusLine(bits.join(' '), SVG_ALERT, 'status err');
    // Retraction is scope-independent: a withdrawn message is withdrawn whether the main thread or
    // a subagent produced it.
    evictUuids(ev.retracted_message_uuids || ev.retractedMessageUuids,
      'withdrawn by ' + (orig ? label(orig) : 'the model'));
  }

  /**
   * The OTHER half of the refusal feature, which item 21 never mentioned and which we did not
   * handle: `system/model_refusal_no_fallback`. Its wire schema says it is "emitted when the model
   * ends the stream with stop_reason 'refusal' and no retry runs: no fallback model is configured,
   * or per-category routing declined the retry".
   *
   * So nothing was retried and nothing is coming — the opposite of the fallback case, where a
   * second model answers. It carries `original_model` and `content` but NO `fallback_model` and no
   * `retracted_message_uuids`, so there is no model to follow and nothing to evict; the whole job
   * is saying that the turn ended and why. Left unhandled, the panel just stops.
   */
  function onRefusalNoFallback(ev) {
    const orig = ev.original_model || ev.originalModel || '';
    const m = allModels().find(function (x) { return x.value === orig; });
    const name = orig ? (m ? chipName(m) : prettyModel(orig)) : 'The model';
    // `content` is a required string in the schema but one emission site sends "", so it cannot be
    // relied on to carry the explanation — hence a sentence of our own behind it.
    statusLine(ev.content ||
      (name + ' declined to answer, and no fallback model was available. Rephrasing usually helps.'),
      SVG_ALERT, 'status err');
    setBusy(false);
  }

  function onStream(e) {
    switch (e.type) {
      case 'message_start': {
        // A turn WE did not start is still a turn. Measured 2026-08-12 (real stream-json capture,
        // CLI 2.1.228): when a background task completes, the CLI injects its notification prompt
        // and streams a response with NO user frame on the wire — the transcript persists one, the
        // live stream does not — so message_start is the only render-time signal there is. Without
        // this the panel kept reading idle: text printing with the button on Send, and Stop (the
        // only interrupt control) unreachable. The counters reset exactly as sendTurn's do, because
        // this IS a new request — the CLI brackets it with system/init + status "requesting" — and
        // carrying them over would bill the previous request's tokens to this done line and reuse
        // its seed. bgTasks/pendingBgTasks stay: the roster is a level signal with its own frames.
        if (!busy) {
          turnTokens = 0; reqTokens = 0; reqSeed = null; retrySeen = null;
          setBusy(true);
        }
        flushMd();
        curBubble = null; curRaw = '';
        curMsgUuid = null; curMsgEls = [];
        msgStreamed = true;   // deltas will draw this message — its assistant frame is only a stamp
        msgTokens = 0; msgChars = 0;
        const u = (e.message || {}).usage || {};   // prompt side only arrives here
        setContext((u.input_tokens || 0) + (u.cache_read_input_tokens || 0) +
          (u.cache_creation_input_tokens || 0));
        break;
      }
      case 'content_block_start': {
        const cb = e.content_block || {};
        // `server_tool_use` is a web search the API ran on its own side. It is shaped exactly like
        // `tool_use` — id, name, and an `input` that streams in as input_json_delta — so it rides
        // the same machinery, and `query` is already in RenderLimits.DESC_KEYS, which is what fills
        // the description at content_block_stop. Item 12: these used to render literally nothing.
        if (cb.type === 'tool_use' || cb.type === 'server_tool_use') {
          const t = track(toolLine(cb.name));
          // In flight until its result lands (or the turn ends). Set HERE and not inside toolLine(),
          // because the replay builder (case 'tool') and __gallery both draw FINISHED tools through
          // that same helper and neither may ever pulse.
          t.classList.add('run');
          openTool = { el: t, name: cb.name || 'tool', json: '', id: cb.id || null };
          if (cb.id) toolsById[cb.id] = openTool;
          flushMd();
          curBubble = null; // text after a tool starts a fresh block
        } else if (cb.type === 'web_search_tool_result') {
          serverToolResult(cb);
        } else if (cb.type === 'redacted_thinking') {
          // no deltas follow and nothing streams — the finished block is the whole event (1.21)
          flushMd(); curBubble = null;
          (curTurn || log).appendChild(track(thinkBlock('', 0, true))); maybeScroll();
        } else if (cb.type === 'thinking') {
          curThink = track(el('think-live', ''));
          curThink.innerHTML = '<div class="th"><span class="shimmer">Thinking…</span>' +
            '<span class="th-el-wrap" hidden> · <span class="th-el"></span></span><span class="tok"></span></div>';
          thinkStart = Date.now(); thinkChars = 0; curThinkRaw = '';
          thinkTok = curThink.querySelector('.tok'); thinkTokReal = null;
          const thEl = curThink.querySelector('.th-el'), thWrap = curThink.querySelector('.th-el-wrap');
          thinkTimer = setInterval(function () {
            if (!curThink) { clearInterval(thinkTimer); thinkTimer = null; return; }
            const secs = Math.round((Date.now() - thinkStart) / 1000);
            thEl.textContent = fmtDur(secs * 1000);
            thWrap.hidden = secs <= 0;   // hide "· 0s" until at least a second has passed
          }, 1000);
        }
        break;
      }
      case 'content_block_delta': {
        const d = e.delta || {};
        if (d.type === 'text_delta') {
          if (!curBubble) { curBubble = track(el('blk', '')); }
          curRaw += d.text;
          if (!mdPending) { mdPending = true; requestAnimationFrame(flushMd); }
          msgChars += d.text.length; estimateMsgTokens();   // live token estimate
        } else if (d.type === 'thinking_delta' && curThink) {
          curThinkRaw += (d.thinking || '');
          thinkChars += (d.thinking || '').length;
          msgChars += (d.thinking || '').length; estimateMsgTokens();
          paintThinkTokens();
          maybeScroll();
        } else if (d.type === 'input_json_delta' && openTool) {
          openTool.json += (d.partial_json || '');
        }
        break;
      }
      case 'message_delta':
        if (e.usage && typeof e.usage.output_tokens === 'number') {
          msgTokens = e.usage.output_tokens;
          updateWorkTokens();
        }
        break;
      case 'content_block_stop':
        if (openTool) {
          try {
            const inp = JSON.parse(openTool.json || '{}');
            // key order, cap and path-ness all come from RenderLimits.kt (see LIM) — the replay
            // parser walks the SAME list, so a tool line reads identically live and on resume
            let descKey = '';
            for (let i = 0; i < LIM.descKeys.length && !descKey; i++) {
              const v = inp[LIM.descKeys[i]];
              if (v && String(v).trim()) descKey = LIM.descKeys[i];
            }
            if (descKey) {
              const dEl = openTool.el.querySelector('.t-desc');
              const raw = String(inp[descKey]);
              // A path is drawn through the shared helper (project-relative, one line, absolute on
              // dataset.path); the cap applies to descriptions, which have no clickable half.
              if (LIM.pathKeys.indexOf(descKey) !== -1) fillPath(dEl, raw, raw.slice(0, LIM.descMax));
              // title carries the SHOWN text, not `raw`: the tooltip exists to reveal what the
              // one-line CSS clamp hid, never what descMax deliberately dropped.
              else dEl.title = dEl.textContent = raw.slice(0, LIM.descMax);
              // Outside the cap and outside .t-desc.path: the range must survive a truncated path,
              // and it is not part of the filename the click handler sends to the editor.
              const sfx = descSuffix(openTool.name, inp.offset, inp.limit);
              if (sfx) {
                const sEl = document.createElement('span');
                sEl.className = 't-sfx'; sEl.textContent = sfx;
                dEl.after(sEl);
                // the NUMBERS ride on the path element, so clicking it selects the slice Claude
                // read rather than dropping you at line 1 of a file it only read the middle of
                const o = (typeof inp.offset === 'number') ? inp.offset : 1;
                dEl.dataset.line = o;
                if (typeof inp.limit === 'number') dEl.dataset.endLine = o + inp.limit - 1;
              }
            }
            if (openTool.name === 'TodoWrite' && Array.isArray(inp.todos)) {
              todoList(inp.todos); maybeScroll();
            }
            // The IN box: what the tool was ASKED. Bash's `command` and a sub-agent's `prompt` are
            // the same idea, so the key order lives in RenderLimits.IN_KEYS rather than being a
            // per-tool branch here — the replay parser walks the same list (item 3).
            let inKey = '';
            for (let i = 0; i < LIM.inKeys.length && !inKey; i++) {
              const v = inp[LIM.inKeys[i]];
              if (typeof v === 'string' && v.trim()) inKey = LIM.inKeys[i];
            }
            if (inKey) {
              const full = String(inp[inKey]), cut = cutInfo(full, LIM.cmdMax);
              const io = ioBox([['IN', cut ? cut.shown : full, cut]]);
              openTool.el.after(io); openTool.io = io; maybeScroll();
            }
            // 4.4: an auto-approved edit gets its diff at tool_result time, built from this
            // INPUT (the live wire carries no structuredPatch — probed 2026-08-05). Stash it
            // on the toolsById record (same object as openTool) and ask Kotlin for the gutter
            // line NOW — pre-apply is the only moment old_string is still findable in the file.
            if (openTool.id && (openTool.name === 'Edit' || openTool.name === 'Write' ||
                                openTool.name === 'MultiEdit')) {
              openTool.edit = inp;
              bridge({ kind: 'lineStart', id: openTool.id, tool: openTool.name, input: openTool.json });
            }
          } catch (_) {}
          openTool = null;
        } else if (curThink) {
          finishThinking();
        }
        break;
      case 'message_stop':
        flushMd();              // the last deltas may still be waiting on a frame
        foldCode(curBubble);    // only once the block is final, not on every delta
        curBubble = null;
        finishThinking();
        turnTokens += msgTokens; msgTokens = 0; msgChars = 0;
        break;
    }
  }

  // tool_result frames: OUT boxes for Bash + red fail dots
  // A LOCAL command that FAILED reports through a `user` frame whose content is a STRING, not the
  // usual block array: `<local-command-stderr>…</local-command-stderr>` (measured 2026-08-15 —
  // /security-review in a repo with no origin/HEAD; the CLI sent the reason and the panel dropped
  // it at the Array.isArray guard below, leaving a completed turn with nothing in it). The stdout
  // spelling reaches this path too. Everything else wrapped in that family — caveats, task
  // notifications, IDE context — stays plumbing the CLI talks to itself with, and draws nothing.
  const LOCAL_OUT_RE = /^\s*<local-command-stdout>([\s\S]*?)<\/local-command-stdout>\s*$/;
  const LOCAL_ERR_RE = /^\s*<local-command-stderr>([\s\S]*?)<\/local-command-stderr>\s*$/;
  function localCommandText(raw) {
    const err = LOCAL_ERR_RE.exec(raw); if (err) return { text: err[1].trim(), isErr: true };
    const out = LOCAL_OUT_RE.exec(raw); if (out) return { text: out[1].trim(), isErr: false };
    return null;
  }

  // Deny messages the cards on this page have sent, keyed by exact text (see onUserEvent).
  const cardDenies = Object.create(null);
  function onUserEvent(ev) {
    const content = (ev.message && ev.message.content) || [];
    if (typeof content === 'string') {
      const lc = localCommandText(content);
      if (lc && lc.text) {
        if (lc.isErr) errorBlock(lc.text);
        else { const k = track(el('blk', '')); k.innerHTML = renderMd(lc.text); foldCode(k); }
      }
      return;
    }
    if (!Array.isArray(content)) return;
    content.forEach(function (b) {
      if (b.type !== 'tool_result') return;
      const t = toolsById[b.tool_use_id];
      if (!t) return;
      // Read the text FIRST: whether this result settles the line depends on what it says.
      let resultRaw = '';
      if (typeof b.content === 'string') resultRaw = b.content;
      else if (Array.isArray(b.content)) resultRaw = b.content.filter(function (x) { return x.type === 'text'; }).map(function (x) { return x.text; }).join('\n');
      // The result is USUALLY the settle — but an async sub-agent's is not. Measured 2026-08-13 in a
      // real session (-home-syncroze-Sites-claude-brains-testing/8a0b1939…): the Agent tool_use at
      // 16:55:53.309 got its tool_result at 16:55:55.158, 1.8s later, reading "Async agent launched
      // successfully … The agent is working in the background." The agent then ran for minutes. So
      // settling here turned the dot green almost immediately while three agents were still working
      // — reported from a screenshot. That launch ack is exactly what isInternalResult already
      // recognises (it is why no OUT box is drawn for it), so the same rule decides both.
      // An ERROR still settles: a launch that failed is not an agent running in the background.
      // What settles a real one is the task lifecycle — see taskLine's `done` branch — and failing
      // that, the sweep at the end of the turn.
      // MUST stay above the early returns further down: LIM.resultSkip covers Edit/Write/TodoWrite/
      // Task*, the most common tools there are, so a removal placed below would leave exactly those
      // lines pulsing until the turn ended. Same reason the tasks bridge call below sits where it does.
      if (b.is_error || !isInternalResult(resultRaw)) t.el.classList.remove('run');
      if (b.is_error) t.el.classList.add('fail');
      // A denial THIS panel sent (a card's Reject, 3.7): the deny message comes back as the
      // tool's error result, so the box would repeat, one line above the card, the note the
      // card's ✗ line already quotes (user's screenshot 2026-09-05). The red dot stays — the
      // tool did fail — the box does not. Matched on the exact text the card sent, so a real
      // error that merely resembles one still draws.
      if (b.is_error && cardDenies[resultRaw.trim()]) return;
      // A Task* call changed the list; ask Kotlin for the current one (the CLI keeps it on disk).
      // MUST be above the RESULT_SKIP return below: TaskCreate/TaskUpdate/TaskList are all in that
      // set, so asking afterwards means never asking at all — which is exactly what happened, and
      // is why only TaskList (skipped later than the others) ever drew a checklist.
      // Live reads the STORE, replay reconstructs from increments — different sources because the
      // store holds only the current state, but both render through the same checklist.
      if (t.name === 'TaskCreate' || t.name === 'TaskUpdate' || t.name === 'TaskList') {
        bridge({ kind: 'tasks', id: b.tool_use_id });   // echoed back so the checklist can sit under THIS line
      }
      // Every tool's result, not just Bash's — except the ones whose outcome is already on screen
      // (LIM.resultSkip, shared with the replay parser). Rendering ALL of them would have added
      // ~2000 boxes saying "The file … has been updated successfully" under the diff that just
      // showed the change. An error is never skipped: a failure is not a restatement of a success.
      // A caveat the CLI appended to an otherwise-successful result (item 11) — "the file had been
      // modified on disk since you last read it". RESULT_SKIP drops Edit/Write text because
      // "updated successfully" restates the diff, but this parenthetical restates nothing: the card
      // still says ✓ Applied and nothing else on screen says the file moved under us. Mirrors
      // RenderLimits.resultNote; read from the TEXT because `staleRecovered` rides on
      // toolUseResult, which the live stream does not carry.
      { const note = resultNote(resultRaw);
        if (note) { const n = noteLine(note); if (n) (t.io || t.el).after(n); } }
      // Auto-approved edit (4.4): no permission card claimed this tool_use (supersedeEdit
      // never matched it), so draw the SAME resolved card replay draws — from the stashed
      // INPUT, with the pre-apply gutter line if the __lineStart answer made it back.
      // Errors keep the existing failure surface (red dot + error OUT box below).
      if (t.edit && !t.superseded && !b.is_error) {
        const card = document.createElement('div'); card.className = 'card warn';
        if (fillAppliedCard(card, { text: t.name, file: t.edit.file_path || t.edit.path,
              edits: t.edit.edits, oldStr: t.edit.old_string, newStr: t.edit.new_string,
              content: t.name === 'Write' ? t.edit.content : null, lineStart: t.lineStart })) {
          (t.io || t.el).after(card); foldBlock(card.querySelector('.diff')); maybeScroll();
        }
        t.edit = null;
      }
      if (LIM.resultSkip.indexOf(t.name) !== -1 && !b.is_error) return;
      // The same rule keyed on CONTENT rather than tool name (7.4): the async sub-agent launch is
      // model-facing bookkeeping end to end, but its tool name is the one whose COMPLETED result is
      // the report worth reading, so only the text can tell them apart. Mirror of
      // RenderLimits.isInternalResult — the Kotlin doc comment owns the rule.
      if (!b.is_error && isInternalResult(resultRaw)) return;
      // Images the tool returned (item 8). Drawn BEFORE the text early-return below, because an
      // image-only result has no text at all — which is exactly how a screenshot used to vanish.
      if (Array.isArray(b.content)) {
        const imgs = b.content.filter(function (x) { return x && x.type === 'image' && x.source; })
          .map(function (x) {
            return { media_type: x.source.media_type, data: x.source.data, name: t.name || 'image' };
          });
        if (imgs.length) {
          const box = toolImages(imgs);
          if (box) { (t.io || t.el).after(box); maybeScroll(); }
        }
      }
      let txt = stripPlumbing((resultRaw || '').trim());   // tags off BEFORE the cut, same as replay (5.9)
      if (!txt) return;
      if (!t.io) { t.io = ioBox([]); t.el.after(t.io); }
      // The CLI's own truncation, above ours. Its facts live on `toolUseResult` for replay, but the
      // live event carries none (probed 2026-08-05) — so read that if it ever appears, and otherwise
      // parse the <persisted-output> wrapper out of the result text, which is where live gets them.
      // Unwrapping also keeps the raw tag off screen and makes what we cut real output.
      const tur = ev.toolUseResult || {};
      const spill = persistedOutput(txt);
      const shown = spill ? spill.preview : txt;
      const cut = cutInfo(shown, LIM.outMax);
      t.io.appendChild(ioRow('OUT', cut ? cut.shown : shown, cut,
        tur.persistedOutputSize || (spill && spill.bytes),
        tur.persistedOutputPath || (spill && spill.path)));
      maybeScroll();
    });
  }

  const DONE_VERBS = ['Baked', 'Brewed', 'Cooked', 'Conjured', 'Simmered', 'Pondered', 'Cogitated',
    'Noodled', 'Percolated', 'Marinated', 'Ruminated', 'Concocted', 'Wrangled', 'Finagled',
    'Assembled', 'Crafted', 'Whipped up', 'Mulled', 'Churned', 'Distilled', 'Tinkered', 'Puttered'];
  // djb2 — used rather than `seed % len` because consecutive requests share their high digits, so a
  // raw modulo walks the list in near-order instead of scattering.
  function verbIdx(seed) {
    const str = String(seed);
    let h = 5381;
    for (let i = 0; i < str.length; i++) h = ((h * 33) ^ str.charCodeAt(i)) >>> 0;
    return h % DONE_VERBS.length;
  }
  // Verb of the summary directly above the next one, so no two in a row can match — a repeat reads as
  // a bug even though random picks collide 1-in-22 by nature. Cleared by clearLogUI; parked and
  // restored around a prepended chunk, which renders out of document order (see renderEarlier).
  let lastDoneVerb = null;
  /* Which verb a summary wears. Both paths hash the request's first assistant-record uuid — the CLI
     emits it on the live `assistant` event AND writes it to the transcript — so a turn keeps the verb
     it was born with, live and after every resume. `prevSeed` covers the first summary of a chunk,
     whose predecessor may not be in the DOM yet; bumping by one on a collision (rather than
     re-rolling) is what keeps all of it deterministic. Live falls back to random only if the uuid
     never arrived. Known imprecision: at a chunk boundary the bump compares against the raw hash of
     `prevSeed` instead of the verb actually displayed, so a verb whose predecessor was ITSELF bumped
     can differ there — ~1/484 of summaries, and only ever at a boundary. */
  function doneVerb(seed, prevSeed) {
    const L = DONE_VERBS.length;
    let i;
    if (seed == null) i = Math.floor(Math.random() * L);
    else i = verbIdx(seed);
    const above = lastDoneVerb != null ? lastDoneVerb
      : (prevSeed != null ? DONE_VERBS[verbIdx(prevSeed)] : null);
    if (DONE_VERBS[i] === above) i = (i + 1) % L;
    return (lastDoneVerb = DONE_VERBS[i]);
  }
  // The completion summary line. The time always shows (a finished request always took some); the
  // token segment is appended only when non-zero, so a 0-token request reads "✻ Baked for 2s" rather
  // than a noisy "↓ 0 tokens". Live and replay share this so a request renders the same either way.
  function doneHtml(durMs, tokens, seed, prevSeed) {
    let s = '<span class="star">✻</span> ' +
      doneVerb(seed, prevSeed) + ' for ' + fmtDur(durMs || 0);
    if (tokens > 0) s += ' · ' + SVG_DOWN + fmtTok(tokens) + ' tokens';
    return s;
  }
  // "Files changed" block under the summary (3.6). [turn] non-null = live, reviewable: the
  // .f-review span ALONE is the action (user request 2026-09-01 — a whole-block target made every
  // stray click on a row open the review). Replay passes null (no baselines survive a resume) and
  // the block is informational — the count is real (from the transcript), the diff is not
  // available.
  // One row per file (user pick 2026-09-01): the old comma-run wrapped into a blob, worst on
  // Windows where a `lastIndexOf('/')` basename never matched a backslash path and the FULL
  // `D:\…` path rendered for every file. Paths draw through fillPath — the same
  // project-relative + .p-head/.p-tail middle-ellipsis dress the tool lines wear, both
  // separators handled — so the two surfaces cannot drift. fillPath's `.path` class carries no
  // click here: the delegated open handler matches only `.t-desc.path` and `.card-h code`.
  function filesLine(files, turn) {
    if (!files || !files.length) return null;
    const d = el('files', '');
    d.innerHTML = SVG_PENCIL + files.length + (files.length === 1 ? ' file changed' : ' files changed') +
      (turn != null ? ' · <span class="f-review">Review</span>' : '');
    files.forEach(function (f) {
      const row = document.createElement('div'); row.className = 'f-row';
      const name = document.createElement('span'); name.className = 'f-name';
      fillPath(name, String(f.path || ''));
      row.appendChild(name);
      if (typeof f.added === 'number' && typeof f.removed === 'number' && (f.added || f.removed)) {
        const a = document.createElement('span'); a.className = 'f-add'; a.textContent = '+' + f.added;
        const r = document.createElement('span'); r.className = 'f-rem'; r.textContent = '−' + f.removed;
        row.appendChild(a); row.appendChild(r);
      }
      d.appendChild(row);
    });
    if (turn != null) {
      const rv = d.querySelector('.f-review');
      rv.title = 'Open a diff of every file this turn changed';
      rv.onclick = function () { bridge({ kind: 'review', turn: turn }); };
    } else d.title = 'Changed in this turn — the diff is only available while the session is live';
    return d;
  }
  // Timeline status line with an optional leading glyph that hangs in the dot column
  // (so icon + text align with .blk / .think / .retry). Text is set safely (textContent).
  // The todo checklist. Three feeders, all whole-list snapshots (never increments by the time
  // they get here): TodoWrite's input at content_block_stop, the Kotlin task store via __tasks
  // after every TaskCreate/TaskUpdate/TaskList result (the frame's `id` relocates the box under
  // the tool line that asked — replay's layout), and replayed records' `todos`. el() appends at
  // the current position; callers that need it elsewhere MOVE the returned box with .after().
  // `activeForm` is the CLI's present-continuous phrasing ("Writing the parser"), which is exactly
  // right for the one item in flight and wrong for the rest — so it is used only there.
  function todoList(items) {
    const box = el('todos', '');
    (items || []).forEach(function (t) {
      if (!t || typeof t !== 'object') return;
      const st = t.status === 'completed' || t.status === 'in_progress' ? t.status : 'pending';
      const row = document.createElement('div');
      row.className = 'todo ' + st;
      const ic = document.createElement('span'); ic.className = 'todo-ic';
      ic.innerHTML = st === 'completed' ? SVG_CHECK : (st === 'in_progress' ? SVG_DOT_OPEN : SVG_CIRCLE);
      const tx = document.createElement('span'); tx.className = 'todo-t';
      tx.textContent = (st === 'in_progress' && t.activeForm) ? t.activeForm : (t.content || '');
      row.appendChild(ic); row.appendChild(tx);
      box.appendChild(row);
    });
    return box;
  }
  // Where the context was compacted. One builder for live and replay, like every other block, so
  // the two cannot drift. The summary is the CLI's own text and runs to 25-41k characters in real
  // sessions — it used to render as a blue USER box, i.e. a message the human never typed — so it
  // folds to three lines under the marker and expands on click.
  function compactBlock(summary, trigger, preTokens) {
    const wrap = el('compact', '');
    const head = document.createElement('div');
    head.className = 'status';
    const ic = document.createElement('span'); ic.className = 's-ic'; ic.innerHTML = SVG_HISTORY;
    head.appendChild(ic);
    const bits = ['Conversation compacted'];
    if (trigger) bits.push(trigger === 'auto' ? 'ran out of context' : 'requested');
    if (preTokens) bits.push(fmtTok(preTokens) + ' before');
    head.appendChild(document.createTextNode(bits.join(' · ')));
    wrap.appendChild(head);
    if (summary) {
      const body = document.createElement('div');
      body.className = 'compact-sum';
      body.textContent = summary;
      wrap.appendChild(body);
      foldBlock(body);   // mounted same-tick by el(), so the post-insert measure lands
    }
    return wrap;
  }
  function statusLine(text, icon, cls) {
    const d = el(cls || 'status');
    if (icon) {
      const s = document.createElement('span'); s.className = 's-ic';
      if (icon.charAt(0) === '<') s.innerHTML = icon; else s.textContent = icon; // SVG vs emoji glyph
      d.appendChild(s);
    }
    d.appendChild(document.createTextNode(text));
    return d;
  }
  // 1847 -> "1.8k", 12345 -> "12.3k", 999 -> "999" (compact once past a thousand)
  // History timestamps: recent ones read relatively (as the mockup does), older ones fall back to
  // "28 Jul 2026, 11:36 PM". Formatted by hand rather than toLocaleString so the webview's locale
  // can't turn it into 7/28/2026, 11:36:02 PM.
  const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
  function fmtWhen(t) {
    if (!t) return '';
    const d = new Date(t);
    if (isNaN(d.getTime())) return '';
    let h = d.getHours();
    const ampm = h >= 12 ? 'PM' : 'AM';
    h = h % 12 || 12;
    const clock = h + ':' + String(d.getMinutes()).padStart(2, '0') + ' ' + ampm;
    const startOf = function (x) { return new Date(x.getFullYear(), x.getMonth(), x.getDate()).getTime(); };
    const days = Math.round((startOf(new Date()) - startOf(d)) / 86400000);
    if (days <= 0) return 'Today, ' + clock;
    if (days === 1) return 'Yesterday, ' + clock;
    if (days < 7) return days + ' days ago';
    return d.getDate() + ' ' + MONTHS[d.getMonth()] + ' ' + d.getFullYear() + ', ' + clock;
  }
