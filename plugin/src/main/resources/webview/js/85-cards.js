  /* ---------- permission / plan cards ---------- */
  // The plan-card feedback quote, one way for both surfaces (live footer + replay): a single
  // line, middle-agnostic head cut at 72 — the reason is a sentence, its head carries the point.
  function fbQuote(t) {
    const shown = t.length > 72 ? t.slice(0, 71) + '…' : t;
    return ' — “' + esc(shown) + '”';
  }
  function cardBtns(okLabel, okSvg, noLabel, noSvg, suggBtns) {
    return '<div class="card-b"><button class="ok">' + okSvg + okLabel + '</button>' +
      (suggBtns || '') +
      '<button class="no">' + noSvg + noLabel + '</button></div>';
  }
  // The CLI sends permission_suggestions with each can_use_tool — ready-made "don't ask again"
  // options (accept-all-edits / allow-rule / allow-directory) it persists when echoed back on the
  // allow response. Map to buttons; entries we don't understand are skipped. `idxs` holds positions
  // in the ORIGINAL array — Kotlin echoes those entries verbatim, so they must survive filtering.
  // Compound commands arrive as one addRules suggestion PER sub-command; those merge into a single
  // "Always allow" button that grants the whole set (matches the CLI's own TUI), because granting
  // half the rules would just re-prompt on the next compound command.
  function permSuggestions(raw, isPlan) {
    let arr = []; try { arr = JSON.parse(raw) || []; } catch (_) {}
    const out = [];
    let ruleEntry = null;
    arr.forEach(function (s, idx) {
      if (s.behavior && s.behavior !== 'allow') return;             // never offer deny/ask shortcuts
      if (s.type === 'setMode' && s.mode === 'acceptEdits') {
        out.push({ idxs: [idx], mode: 'acceptEdits',   // marker: the plan card's auto-edit row rides this
          label: isPlan ? 'Approve, auto-edit' : 'Accept all edits',
          title: 'Auto-approve every edit for the rest of this session',
          doneText: 'auto-approving edits' });
      } else if (s.type === 'addRules' && s.rules && s.rules.length) {
        if (!ruleEntry) { ruleEntry = { idxs: [], rules: [], sigs: [], parts: [], session: s.destination === 'session' }; out.push(ruleEntry); }
        ruleEntry.idxs.push(idx);
        // parts is one entry per RULE, so the split menu can grant a single sub-command.
        // MEASURED 2026-08-09 against CLI 2.1.226: a compound command arrives as ONE addRules
        // suggestion whose rules[] holds one rule per sub-command (mixed payloads with several
        // one-rule suggestions exist too — a command rule plus a Read path rule). The old
        // per-SUGGESTION parts turned a compound card into a single part, so the split caret
        // never appeared (manual-test 6.4). Each part carries "sugIdx.ruleIdx" picks — the
        // grant echoes a SUBSET of that suggestion's rules, which the CLI accepts and persists
        // exactly (probed: a subset echo wrote only the one rule to settings.local.json).
        // rules stays flat for the merged button and the done-text chips. Both are DEDUPED by
        // the WHOLE grant — tool, raw rule and destination, not the text shown: two different
        // grants can render the same string (Read(//p/**) and Grep(//p/**) both display as
        // //p/**, and one rule can be offered both for the session and persisted), and folding
        // those would grant wider scope than the row's single line admits. Dedupe is
        // display-only: every contributing pick is kept, so granting a folded row still echoes
        // each source suggestion's copy of the rule.
        const txts = [], sigs = [];
        s.rules.forEach(function (r) {
          txts.push(r.ruleContent ? r.ruleContent.replace(/:\*$/, '') : r.toolName);
          sigs.push((r.toolName || '') + '\u0001' + (r.ruleContent || '') + '\u0001' + (s.destination || ''));
        });
        sigs.forEach(function (sig, n) {
          const pick = idx + '.' + n;
          if (ruleEntry.sigs.indexOf(sig) === -1) {
            ruleEntry.sigs.push(sig); ruleEntry.rules.push(txts[n]);
          }
          const twin = ruleEntry.parts.filter(function (p) { return p.key === sig; })[0];
          if (twin) twin.picks.push(pick);
          else ruleEntry.parts.push({ key: sig, picks: [pick], rules: [txts[n]] });
        });
      } else if (s.type === 'addDirectories' && s.directories && s.directories.length) {
        out.push({ idxs: [idx], label: 'Allow ' + s.directories.join(', '),
          title: 'Allow file access to ' + s.directories.join(', ') +
            (s.destination === 'session' ? ' for this session' : ''),
          doneText: 'directory allowed' });
      }
    });
    if (ruleEntry) {
      ruleEntry.label = 'Always allow';
      ruleEntry.title = 'Allow' + (ruleEntry.session
        ? ' for this session' : ' from now on (saved to .claude/settings.local.json)') +
        ':\n' + ruleEntry.rules.join('\n');
      ruleEntry.doneText = 'always allowed';   // rules render as chips, see doneHtmlFor
    }
    return out;
  }
  // Middle-truncate a rule for chip display — both ends of a command matter.
  function ruleChip(r) {
    const shown = r.length > 46 ? r.slice(0, 28) + '…' + r.slice(-15) : r;
    return '<code title="' + escA(r) + '">' + esc(shown) + '</code>';
  }
  function diffRow(cls, prefix, num, text) {
    const g = (typeof num === 'number') ? '<span class="ln">' + num + '</span>' : '';
    return '<span class="' + cls + '">' + g + prefix + esc(text) + '</span>';
  }
  function diffLines(prefix, cls, s, startLine) {
    const hasNum = typeof startLine === 'number';
    return String(s).split('\n').map(function (l, i) { return diffRow(cls, prefix, hasNum ? startLine + i : null, l); }).join('');
  }
  // Claude's Edit anchors appends/prepends by repeating unchanged lines in both old_string and
  // new_string. Trim the common prefix/suffix so those render as context (no -/+) and only the
  // real changes are marked — otherwise a 2-line append looks like "remove 2, add 4".
  function renderEditDiff(oldStr, newStr, lineStart) {
    const O = String(oldStr).split('\n'), N = String(newStr).split('\n');
    const num = typeof lineStart === 'number' ? lineStart : null;
    const at = function (i) { return num == null ? null : num + i; };
    let p = 0;
    while (p < O.length && p < N.length && O[p] === N[p]) p++;
    let s = 0;
    while (s < O.length - p && s < N.length - p && O[O.length - 1 - s] === N[N.length - 1 - s]) s++;
    // Same MAX_DIFF_ROWS cap as replayed structuredPatch diffs: whole files land here live, and
    // an uncapped multi-thousand-row diff is exactly the layout cost the perf work exists to avoid.
    let rows = '', n = 0;
    const emit = function (cls, prefix, ln, text) { if (n++ < MAX_DIFF_ROWS) rows += diffRow(cls, prefix, ln, text); };
    for (let i = 0; i < p; i++) emit('ctx', '  ', at(i), O[i]);            // common prefix
    for (let i = p; i < O.length - s; i++) emit('del', '- ', at(i), O[i]); // removed
    for (let i = p; i < N.length - s; i++) emit('add', '+ ', at(i), N[i]); // added
    const added = (N.length - s) - p;
    for (let i = 0; i < s; i++) emit('ctx', '  ', at(p + added + i), N[N.length - s + i]); // common suffix
    if (n > MAX_DIFF_ROWS) rows += diffRow('ctx', '  ', null, '… diff truncated');
    return rows;
  }
  function previewHtml(tool, inp, lineStart) {
    // Edit / Write / MultiEdit previews delegate to the same replayDiff the resolved cards
    // use — one diff producer for card preview, live auto-approved, and replay (4.4). Also
    // gives MultiEdit a preview at all: its {edits:[…]} shape matched no branch here, so a
    // multi-hunk edit used to ask for approval showing NOTHING.
    const d = replayDiff({ edits: inp.edits, oldStr: inp.old_string, newStr: inp.new_string,
                           content: inp.content, lineStart: lineStart });
    if (d) return d;
    const cmd = inp.command || inp.description;
    // slice BEFORE escaping: slicing the escaped string can cut an entity (&amp;) in half and
    // makes the real limit depend on how many specials the command happens to contain
    if (cmd !== undefined) {
      const cut = cutInfo(String(cmd), LIM.cmdMax);
      // This card is a CONSENT surface — approving a command whose tail is hidden is the worst case
      // of a silent cut, so the marker sits outside .cmd (which .card .cmd.fold also collapses).
      return '<pre class="cmd">' + esc(cut ? cut.shown : String(cmd)) + '</pre>' +
        (cut ? '<div class="io-cut cmd-cut">' + esc(cutText(cut)) + '</div>' : '');
    }
    return '';
  }

  /* ---------- AskUserQuestion — tabbed card ---------- */
  let activeAsk = null; // { card, ev, cancel }
  function cancelAsk() {
    if (!activeAsk) return;
    const a = activeAsk;
    resolveAsk(a.card, '<span class="no-t">✗ Cancelled</span>');
    window.respondPermission(a.ev.id, false);
    if (busy) showWorking(); // Claude resumes processing
  }
  function renderAsk(ev, inp) {
    const questions = inp.questions || [];
    const card = document.createElement('div');
    card.className = 'ask';
    let html = askTabsHtml(questions);
    questions.forEach(function (q, i) {
      html += '<div class="ask-panel" data-q="' + i + '"' + (i === 0 ? '' : ' hidden') + '>';
      html += '<div class="ask-q">' + esc(q.question) + '</div><div class="ask-list">';
      const type = q.multiSelect ? 'checkbox' : 'radio';
      (q.options || []).forEach(function (o, oi) {
        html += '<label class="ask-opt"><input type="' + type + '" name="aq' + i + '" data-oi="' + oi + '">' +
          '<span class="ask-ind"></span><span class="q-body"><span class="q-t">' + esc(o.label) + '</span>' +
          (o.description ? '<span class="q-d">' + esc(o.description) + '</span>' : '') + '</span></label>';
      });
      html += '<label class="ask-opt ask-opt-other"><input type="' + type + '" name="aq' + i + '" data-oi="-1">' +
        '<span class="ask-ind"></span><span class="q-body"><span class="q-t">Other</span></span></label>';
      html += '<div class="ask-other" hidden><input type="text" placeholder="Type your answer…"></div>';
      html += '</div></div>';
    });
    html += '<div class="ask-b"><button class="ask-go ok">' + SVG_CHECK + 'Submit answers</button>' +
      '<button class="no">' + SVG_X + 'Cancel</button></div>';
    html += '<div class="ask-foot">Esc to cancel</div>';
    card.innerHTML = html;
    (curTurn || log).appendChild(card); maybeScroll();

    const wired = wireAskTabs(card);
    const panels = wired.panels, selectTab = wired.selectTab;
    const go = card.querySelector('.ask-go');
    function refresh() {
      card.querySelectorAll('.ask-opt').forEach(function (opt) {
        const inpEl = opt.querySelector('input');
        opt.classList.toggle('checked', inpEl.checked);
        const chk = inpEl.type === 'checkbox';
        opt.querySelector('.ask-ind').innerHTML = chk ? (inpEl.checked ? IND.cOn : IND.cOff) : (inpEl.checked ? IND.rOn : IND.rOff);
      });
      panels.forEach(function (p) {
        const other = p.querySelector('.ask-opt-other input');
        const box = p.querySelector('.ask-other');
        if (box) box.hidden = !(other && other.checked);
      });
      const allAnswered = Array.prototype.every.call(panels, function (p, i) {
        return card.querySelector('input[name="aq' + i + '"]:checked');
      });
      go.disabled = !allAnswered;
    }
    card.querySelectorAll('.ask-opt input').forEach(function (i) {
      i.addEventListener('change', function () {
        refresh();
        const opt = i.closest('.ask-opt');
        if (i.type === 'radio' && !opt.classList.contains('ask-opt-other')) {
          const idx = Array.prototype.indexOf.call(panels, i.closest('.ask-panel'));
          if (idx > -1 && idx < panels.length - 1) selectTab(idx + 1);
        }
      });
    });
    go.onclick = function () {
      const answers = {};
      questions.forEach(function (q, i) {
        const panel = panels[i];
        const labels = [];
        panel.querySelectorAll('.ask-opt input:checked').forEach(function (inpEl) {
          const oi = +inpEl.dataset.oi;
          if (oi >= 0) { labels.push((q.options[oi] || {}).label); }
          else {
            const typed = (panel.querySelector('.ask-other input') || {}).value || '';
            if (typed.trim()) labels.push(typed.trim());
          }
        });
        answers[q.question] = labels.filter(Boolean).join(', ');
      });
      resolveAsk(card, '<span class="ok-t">✓ Answered</span>');
      bridge({ kind: 'answer', id: ev.id, answers: JSON.stringify(answers) });
      if (busy) showWorking(); // Claude resumes processing
    };
    card.querySelector('.ask-b .no').onclick = cancelAsk;
    refresh();
    activeAsk = { card: card, ev: ev };
  }

  // Live permission cards by request id, so `__perm_answered` (the editor diff answered first)
  // can retire the matching card. Entries die with the card: answered, cleared, or superseded.
  let permCards = Object.create(null);
  function renderPermission(ev) {
    hideWorking(); awaitingUser = true; // Claude is now waiting on the user, not processing
    let inp = {}; try { inp = JSON.parse(ev.input); } catch (_) {}
    if (ev.tool === 'AskUserQuestion' && inp.questions) return renderAsk(ev, inp);
    const isPlan = ev.tool === 'ExitPlanMode' || inp.plan !== undefined;
    const file = inp.file_path || inp.path || inp.notebook_path || '';   // NotebookEdit names its file differently
    // This card owns the edit's diff now — the optimistic tool-line record (4.4) must not
    // ALSO render one at tool_result time, or manual mode double-renders every edit.
    if (ev.tool === 'Edit' || ev.tool === 'Write' || ev.tool === 'MultiEdit') supersedeEdit(ev.tool, file);
    const suggs = permSuggestions(ev.suggestions, isPlan);
    const suggBtns = suggs.map(function (s, i) {
      const main = '<button class="alt" data-s="' + i + '" title="' + escA(s.title) + '">' +
        SVG_CHECKS + esc(s.label) + '</button>';
      // One grant stays a plain button — a dropdown holding a single item is noise. With several
      // (a compound command arrives as ONE addRules suggestion holding one rule per sub-command —
      // measured 2026-08-09) the main half still grants the lot, and the caret opens the rules
      // individually for a deliberate partial grant.
      if (!s.parts || s.parts.length < 2) return main;
      return '<span class="split">' + main +
        '<button class="alt more" data-s="' + i + '" aria-haspopup="true"' +
        ' title="Allow just one of these">' + SVG_CHEVRON + '</button>' +
        '<div class="popup card-menu">' + s.parts.map(function (part, j) {
          const txt = part.rules.join(', ');
          // The double-check icon carries the meaning, as it already does on the button above:
          // without a marker a row is a bare command and reads like something about to RUN rather
          // than a permission about to be granted. The tooltip spells out the rule's SCOPE —
          // a trailing "*" is the CLI's wildcard (prefix rule: any arguments), everything else
          // grants exactly that command; the distinction is the CLI's choice, shown verbatim.
          const wild = / \*$/.test(txt);
          const scope = wild
            ? 'Always allow any command starting with "' + txt.replace(/ \*$/, '') + '"'
            : 'Always allow exactly "' + txt + '"';
          return '<div class="popup-item" data-s="' + i + '" data-p="' + j + '"' +
            ' title="' + escA(scope) + '"><div class="pi-ic">' + SVG_CHECKS + '</div>' +
            '<div class="pi-body"><div class="pi-title">' + esc(txt) + '</div></div></div>';
        }).join('') + '</div></span>';
    }).join('');
    const card = document.createElement('div');
    if (isPlan) {
      card.className = 'card warn'; // same feedback surface as permission / ask cards
      // The terminal's plan dialog, translated: a feedback line that rides WHICHEVER button
      // answers, and the Yes variants folded behind the Approve caret (the flat suggestion
      // button used to sit between the primaries). bypassPermissions is not offered — the CLI
      // refuses to be raised into it at runtime. No keyboard shortcuts (user, 2026-08-16).
      card.innerHTML = planCardHtml(inp.plan || '') +
        '<div class="plan-sep"></div>' +
        '<input class="plan-fb" placeholder="Tell Claude what to change">' +
        '<div class="card-b"><span class="split">' +
          '<button class="ok">' + SVG_CHECK + 'Approve &amp; implement</button>' +
          '<button class="ok more" aria-haspopup="true" title="More ways to approve">' + SVG_CHEVRON + '</button>' +
          '<div class="popup card-menu">' +
            '<div class="popup-item" data-act="acceptEdits" title="Approve, and auto-approve every edit for the rest of this session">' +
              '<div class="pi-ic">' + SVG_CHECKS + '</div><div class="pi-body"><div class="pi-title">Approve, auto-edit</div></div></div>' +
            '<div class="popup-item" data-act="auto" title="Approve, and let Auto mode pass safe actions, pausing for anything risky">' +
              '<div class="pi-ic">' + SVG_CHECKS + '</div><div class="pi-body"><div class="pi-title">Approve, auto mode</div></div></div>' +
          '</div>' +
        '</span>' +
        '<button class="no">' + SVG_STEP + 'Keep planning</button></div>';
    } else {
      card.className = 'card warn';
      card.innerHTML =
        '<div class="card-h">Claude wants to run <b>' + esc(toolLabel(ev.tool)) + '</b>' +
          (file ? ' on <code></code>' : '') + '</div>' +
        previewHtml(ev.tool, inp, ev.lineStart) +
        cardBtns('Accept', SVG_CHECK, 'Reject', SVG_X, suggBtns);
    }
    // Same renderer as the tool line, so one file is never named two ways in one turn: relative,
    // middle-ellipsised, with the ABSOLUTE path on dataset.path + title (which is what keeps the
    // click and the tooltip whole once the text is no longer openable).
    if (file) { const c = card.querySelector('.card-h code'); if (c) fillPath(c, file); }
    (curTurn || log).appendChild(card); maybeScroll();
    foldBlock(card.querySelector('.cmd'));   // whole scripts land here
    foldBlock(card.querySelector('.diff'));  // long edits fold too (was a 240px scroll box)
    const done = function (allow, sugg, viaEditor) {
      delete permCards[ev.id];
      // The typed reason rides every decision (terminal parity: its allow branches all carry
      // acceptFeedback too). Read before the input leaves with the buttons.
      const fbEl = card.querySelector('.plan-fb');
      const fb = fbEl ? fbEl.value.trim() : '';
      if (fbEl) fbEl.remove();
      // The separator marks the live decision surface — a decided card has none (replayCard
      // draws plan + footer only, and the two paths must not disagree).
      const sepEl = card.querySelector('.plan-sep');
      if (sepEl) sepEl.remove();
      card.querySelector('.card-b').innerHTML = allow
        ? '<span class="ok-t">✓ ' + (isPlan ? 'Approved' : 'Accepted') + (viaEditor ? ' in the editor' : '') +
            (fb ? fbQuote(fb) : '') +
            (sugg ? ' · ' + esc(sugg.doneText) : '') +
            (sugg && sugg.rules ? ' ' + sugg.rules.map(ruleChip).join('') : '') + '</span>'
        : '<span class="no-t">✗ ' + (isPlan ? 'Kept planning' : 'Rejected') + (viaEditor ? ' in the editor' : '') +
            (fb ? fbQuote(fb) : '') + '</span>';
      awaitingUser = false;
      // The editor path already answered the CLI through Kotlin's arbiter — sending again from
      // here would be a duplicate (and would be dropped, but why knock).
      if (!viaEditor) window.respondPermission(ev.id, allow, sugg ? sugg.idxs : null, fb);
      // Approve-with-notes rides the approved plan itself (Kotlin appends it to updatedInput.plan
      // — no user message exists), so the quoted footer is the note's whole visible record here,
      // exactly as it is on replay.
      if (busy) showWorking(); // Claude resumes processing
    };
    permCards[ev.id] = function (allow) { done(allow, null, true); };
    card.querySelector('.ok').onclick = function () { done(true); };
    card.querySelector('.no').onclick = function () { done(false); };
    Array.prototype.forEach.call(card.querySelectorAll('.card-b .alt:not(.more)'), function (b) {
      b.onclick = function () { done(true, suggs[+b.dataset.s]); };
    });
    Array.prototype.forEach.call(card.querySelectorAll('.card-b .alt.more, .card-b .ok.more'), function (b) {
      b.onclick = function (e) {
        e.stopPropagation();                       // the document handler would close it again
        const pop = b.parentNode.querySelector('.card-menu');
        const wasOpen = pop.classList.contains('show');
        closeCardMenus(); closeMenus();
        pop.classList.remove('up');
        pop.classList.toggle('show', !wasOpen);
        b.parentNode.classList.toggle('open', !wasOpen);
        if (!wasOpen) {
          // flip above the button only when the menu would run under the composer, and cap the
          // list the same way the history/slash popups are capped
          const limit = composerEl ? composerEl.getBoundingClientRect().top : window.innerHeight;
          if (pop.getBoundingClientRect().bottom > limit - 8) pop.classList.add('up');
          capToRows(pop, 6, '.popup-item');
        }
      };
    });
    Array.prototype.forEach.call(card.querySelectorAll('.card-menu .popup-item:not([data-act])'), function (it) {
      it.onclick = function () {
        const s = suggs[+it.dataset.s], part = s.parts[+it.dataset.p];
        // only THIS row's picks go back — "sugIdx.ruleIdx" tokens; Kotlin narrows each
        // source suggestion to just the picked rule(s), so the CLI persists exactly this row
        // (several picks when duplicate rules were folded into one row)
        done(true, { idxs: part.picks, rules: part.rules, doneText: s.doneText });
      };
    });
    // Plan-card rows are the plugin's own (data-act), not renders of suggestions. Auto-edit
    // rides the CLI's setMode suggestion when one arrived — the CLI OMITS it when prePlanMode
    // was already elevated (auto/acceptEdits/dontAsk, 2.1.233) — else it answers plainly and
    // asks Kotlin for the mode. Auto is never suggested, so it always takes the bridge
    // round-trip. Perm answer FIRST, mode second: the response must not race the mode change,
    // and the chip follows the CLI's own permissionMode broadcast either way.
    const planModeSugg = isPlan ? suggs.filter(function (s) { return s.mode === 'acceptEdits'; })[0] : null;
    Array.prototype.forEach.call(card.querySelectorAll('.card-menu .popup-item[data-act]'), function (it) {
      it.onclick = function () {
        const act = it.dataset.act;
        const s = act === 'acceptEdits' ? planModeSugg : null;
        done(true, s || null);
        // No suggestion to ride → park the switch for the post-approval broadcast (see
        // pendingPlanMode) — an immediate bridge always lost to the CLI's prePlanMode restore.
        if (!s) pendingPlanMode = act;
      };
    });
    // No shortcuts on the input (user, 2026-08-16) — but typing must not reach the document
    // handlers (Escape would close popups, arrows would move popup cursors).
    const fbIn = card.querySelector('.plan-fb');
    if (fbIn) fbIn.addEventListener('keydown', function (e) { e.stopPropagation(); });
  }

