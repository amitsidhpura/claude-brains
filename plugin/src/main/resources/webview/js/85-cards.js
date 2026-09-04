  /* ---------- permission / plan cards ---------- */
  // The plan-card feedback quote, one way for both surfaces (live footer + replay): a single
  // line, middle-agnostic head cut at 72 — the reason is a sentence, its head carries the point.
  function fbQuote(t) {
    const shown = t.length > 72 ? t.slice(0, 71) + '…' : t;
    return ' — “' + esc(shown) + '”';
  }
  // fbPlaceholder (3.7): a reject-note field after the buttons — the plan card's .plan-fb dress,
  // placed inline the way VS Code places its rejectMessageInput beside the reject button. It rides
  // DENY only: the message reaches the model verbatim as the tool_result (probed 2.1.233), while a
  // note on an ordinary allow has no wire to travel on (`feedback` is dropped there — same probe).
  function cardBtns(okLabel, okSvg, noLabel, noSvg, suggBtns, fbPlaceholder) {
    return '<div class="card-b"><button class="ok">' + okSvg + okLabel + '</button>' +
      (suggBtns || '') +
      '<button class="no">' + noSvg + noLabel + '</button>' +
      (fbPlaceholder ? '<input class="plan-fb" placeholder="' + escA(fbPlaceholder) + '">' : '') +
      '</div>';
  }
  // Where a "don't ask again" rule can live (4.8). The grant's OWN destination — localSettings on
  // every rule card measured — stays on the main half of the Always-allow split; the caret lists the
  // others in this order (VS Code's cycle, webview index.js `Ss`; `cliArg` is never offered). Every
  // value was MEASURED honoured on 2.1.260 (2026-09-04, stdio): the echoed destination decides the
  // file — session writes nothing and stops the re-asks for the run — and an UNKNOWN value drops the
  // grant silently, which is why Kotlin forwards only these four (PermissionDestinations).
  const DESTS = {
    session:         { row: 'This session only',       tip: 'Not saved: forgotten when this conversation ends',           done: 'for this session' },
    localSettings:   { row: 'This project, just you',  tip: 'Saves to .claude/settings.local.json (gitignored)',          done: 'for this project (just you)' },
    projectSettings: { row: 'This project, shared',    tip: 'Saves to .claude/settings.json (committed with the project)', done: 'for this project (shared)' },
    userSettings:    { row: 'All projects',            tip: 'Saves to ~/.claude/settings.json',                           done: 'for all projects' },
  };
  const DEST_ORDER = ['session', 'localSettings', 'projectSettings', 'userSettings'];
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
        // dest: the grant's own destination, the split's main half (4.8); only a KNOWN one earns the
        // caret's other-destination rows — an unknown value must not be echoed back changed.
        if (!ruleEntry) { ruleEntry = { idxs: [], rules: [], sigs: [], parts: [], session: s.destination === 'session',
          dest: DESTS[s.destination] ? s.destination : null }; out.push(ruleEntry); }
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
      // Editable in place (3.8) when it is a real command shown WHOLE: a cut command cannot be
      // edited from a box that does not hold all of it, and a description-only preview is not a
      // command. plaintext-only keeps pasted markup out; the original rides dataset.orig so the
      // card can tell a real change from a round trip.
      const editable = inp.command !== undefined && !cut;
      return '<pre class="cmd"' + (editable ? ' contenteditable="plaintext-only" spellcheck="false"' +
          ' title="Edit the command here before accepting" data-orig="' + escA(String(cmd)) + '"' : '') +
        '>' + esc(cut ? cut.shown : String(cmd)) + '</pre>' +
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
      // The caret holds two kinds of row. Per-RULE rows when the grant is compound (a compound
      // command arrives as ONE addRules suggestion holding one rule per sub-command — measured
      // 2026-08-09): the main half still grants the lot, a row grants one rule for a deliberate
      // partial grant. DESTINATION rows (4.8) when the grant has a known destination: each grants
      // the lot, saved somewhere other than the main half's default. A grant with neither (a mode
      // switch) stays a plain button — a dropdown holding nothing is noise.
      const parts = (s.parts && s.parts.length > 1) ? s.parts.map(function (part, j) {
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
        }).join('') : '';
      // Every destination but the grant's own. Under per-rule rows a header marks the switch from
      // "just this one" to "all of these": the rows below name only WHERE, the header says WHAT.
      const dests = s.dest ? (parts ? '<div class="popup-h">All of these</div>' : '') +
        DEST_ORDER.filter(function (d) { return d !== s.dest; }).map(function (d) {
          return '<div class="popup-item dest" data-s="' + i + '" data-d="' + d + '"' +
            ' title="' + escA(DESTS[d].tip) + '"><div class="pi-ic">' + SVG_CHECKS + '</div>' +
            '<div class="pi-body"><div class="pi-title">' + esc(DESTS[d].row) + '</div></div></div>';
        }).join('') : '';
      if (!parts && !dests) return main;
      const caretTitle = parts && dests ? 'Allow just one of these, or choose where the rule is kept'
        : parts ? 'Allow just one of these' : 'Choose where the rule is kept';
      return '<span class="split">' + main +
        '<button class="alt more" data-s="' + i + '" aria-haspopup="true"' +
        ' title="' + caretTitle + '">' + SVG_CHEVRON + '</button>' +
        '<div class="popup card-menu">' + parts + dests + '</div></span>';
    }).join('');
    const card = document.createElement('div');
    if (isPlan) {
      card.className = 'card warn plan'; // .plan anchors the floating comment pill (5.6)
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
      // .cmd-compound (3.8): while a compound card's command is edited its per-rule rows hide —
      // they name the ORIGINAL parts — but Always-allow and the destination rows stay: the grant
      // is rewritten to the edited text, one rule per part (EditProposals.withRulesFor).
      const compound = suggs.some(function (s) { return s.parts && s.parts.length > 1; });
      card.className = 'card warn' + (compound ? ' cmd-compound' : '');
      card.innerHTML =
        '<div class="card-h">Claude wants to run <b>' + esc(toolLabel(ev.tool)) + '</b>' +
          (file ? ' on <code></code>' : '') + '</div>' +
        previewHtml(ev.tool, inp, ev.lineStart) +
        cardBtns('Accept', SVG_CHECK, 'Reject', SVG_X, suggBtns, 'Tell Claude what to do instead');
    }
    // Same renderer as the tool line, so one file is never named two ways in one turn: relative,
    // middle-ellipsised, with the ABSOLUTE path on dataset.path + title (which is what keeps the
    // click and the tooltip whole once the text is no longer openable).
    if (file) { const c = card.querySelector('.card-h code'); if (c) fillPath(c, file); }
    // WHY the CLI is asking (checklist 1.23): can_use_tool's `decision_reason` — "the human-readable
    // reason the ask escalated, for the consent line of the host's dialog" (its own schema text) —
    // e.g. a matched ask-rule or a compound command's nested safety warning. Absent on the plain
    // "mode requires it" ask, which is every card measured so far; present → one amber note line,
    // the same ↳ idiom as a tool's caveat.
    if (ev.reason) { const h = card.querySelector('.card-h'), n = noteLine(ev.reason); if (h && n) h.after(n); }
    (curTurn || log).appendChild(card); maybeScroll();
    foldBlock(card.querySelector('.cmd'));   // whole scripts land here
    foldBlock(card.querySelector('.diff'));  // long edits fold too (was a 240px scroll box)
    // ---- Plan comments (checklist 5.6) ----------------------------------------------------
    // Select text in the plan body → a floating pill → an anchored note row. The anchor is
    // highlighted with a <mark> where the selection sits in one text run (a cross-element
    // selection still records the anchor text — the row is the contract, the mark is sugar).
    // Rows re-render through the shared planCommentRows builder so a decided card and a
    // replayed one draw identically. Enter adds / Esc cancels are the composer input's own
    // semantics — NOT the deferred plan-card decision shortcuts (user, 2026-08-16).
    const comments = [];                     // [{a, t, mark}]
    const blkEl = isPlan ? card.querySelector('.blk') : null;
    let csEl = null, pillEl = null, pendMark = null, pendAnchor = '', pendOcc = 0, selRange = null, composing = false;
    function unwrapMark(m) {
      if (!m || !m.parentNode) return;
      const par = m.parentNode;
      while (m.firstChild) par.insertBefore(m.firstChild, m);
      m.remove();
      par.normalize();   // surroundContents split the text node; heal it or a later selection over the same text can't be marked
    }
    function hidePill() { if (pillEl) { pillEl.remove(); pillEl = null; } }
    function renderComments(withComposer, anchorText) {
      // A rebuild must never lose a live composer's typed text (delete-while-composing bug,
      // user report 2026-08-23): capture before teardown, restore into the rebuilt input.
      const prevIn = csEl && csEl.querySelector('.compose input');
      const keepVal = prevIn ? prevIn.value : '';
      if (csEl) { csEl.remove(); csEl = null; }
      if (!comments.length && !withComposer) return;
      const wrap = document.createElement('div');
      wrap.className = 'plan-cs';
      // Composer is two lines (user pick 2026-08-23): the anchor quote full-width on top,
      // input + Enter button below. Both composer buttons wear the committed-row ✕'s dress —
      // ✕ (cancel) on the anchor line, ⏎ (commit) on the input line (user pick, round 4).
      wrap.innerHTML = planCommentRows(comments, true) +
        (withComposer ? '<div class="plan-c compose"><span class="c-a" title="' + escA(anchorText) + '">“' + esc(anchorText) + '”</span>' +
          '<div class="c-btns"><button class="c-x" title="Cancel comment">' + SVG_X + '</button>' +
          '<button class="c-ok" title="Add comment (Enter)">' + SVG_ENTER + '</button></div>' +
          '<div class="c-row"><input placeholder="Comment on this part of the plan"></div></div>' : '');
      // Rows live BELOW the separator, above the feedback input (user pick 2026-08-23); once the
      // card is decided the separator is gone and the rows re-anchor after the plan body — which
      // is exactly the replayed card's order.
      const sepHere = card.querySelector('.plan-sep');
      (sepHere || blkEl).insertAdjacentElement('afterend', wrap);
      csEl = wrap;
      // Committed-row ✕ only — the composer's own ✕ is CANCEL, and this loop's dataset.i
      // would be NaN for it (splice(NaN, 1) eats element 0: the round-4 bug's second head).
      Array.prototype.forEach.call(wrap.querySelectorAll('.plan-c:not(.compose) .c-x'), function (x) {
        x.onclick = function () {
          const i = +x.parentNode.dataset.i;
          unwrapMark(comments[i] && comments[i].mark);
          comments.splice(i, 1);
          // keep a live composer (and its draft) across the rebuild — dropping it here both
          // destroyed the draft and stranded `composing`, locking the pill out (user report)
          renderComments(composing, pendAnchor);
        };
      });
      const ci = wrap.querySelector('.compose input');
      if (ci) {
        if (keepVal) ci.value = keepVal;
        const commit = function () {
          if (!ci.value.trim()) return;
          comments.push({ a: anchorText, t: ci.value.trim(), n: pendOcc, mark: pendMark });
          pendMark = null; composing = false;
          renderComments(false);
        };
        const cancel = function () {
          unwrapMark(pendMark); pendMark = null; composing = false;
          renderComments(false);
        };
        ci.focus();
        ci.addEventListener('keydown', function (e) {
          e.stopPropagation();               // same contract as .plan-fb: no document handlers
          if (e.key === 'Enter') commit();
          else if (e.key === 'Escape') cancel();
        });
        const ok = wrap.querySelector('.compose .c-ok');
        if (ok) ok.onclick = commit;
        const cx = wrap.querySelector('.compose .c-x');
        if (cx) cx.onclick = cancel;
      }
      maybeScroll();
    }
    function showPill(range) {
      hidePill();
      selRange = range.cloneRange();
      const rect = range.getBoundingClientRect(), crect = card.getBoundingClientRect();
      pillEl = document.createElement('div');
      pillEl.className = 'plan-add';
      pillEl.innerHTML = SVG_COMMENT + 'Comment';
      pillEl.style.left = Math.max(0, Math.min(rect.right - crect.left + 4, card.clientWidth - 110)) + 'px';
      pillEl.style.top = Math.max(0, rect.bottom - crect.top + 4) + 'px';
      pillEl.onclick = function () {
        hidePill();
        // one-line anchor: the [Re: "…"] wire line is line-shaped, so a multi-line selection
        // flattens to spaces — the row and the model still see the full anchor text
        const a = selRange.toString().replace(/\s+/g, ' ').trim();
        if (!a) return;
        pendAnchor = a;
        // WHICH occurrence: count matches starting before the selection, with the SAME matcher
        // the highlighter uses — so decide/replay land exactly where the user selected
        pendOcc = 0;
        const flat = planFlat(blkEl);
        const ms = anchorMatches(flat, a);
        const sp = flatPos(flat, selRange.startContainer, selRange.startOffset);
        if (sp >= 0) for (let i = 0; i < ms.length; i++) { if (ms[i].start < sp) pendOcc = i + 1; else break; }
        try { const m = document.createElement('mark'); m.className = 'plan-anchor'; selRange.surroundContents(m); pendMark = m; }
        catch (err) { pendMark = null; }     // selection crossed element boundaries
        const sel = window.getSelection(); if (sel) sel.removeAllRanges();
        composing = true;
        renderComments(true, a);
      };
      card.appendChild(pillEl);
    }
    if (isPlan) {
      blkEl.addEventListener('mouseup', function () {
        setTimeout(function () {             // selection is finalized after mouseup returns
          if (composing) return;
          const sel = window.getSelection();
          if (!sel || sel.isCollapsed || !sel.rangeCount) { hidePill(); return; }
          const r = sel.getRangeAt(0);
          if (!blkEl.contains(r.commonAncestorContainer) || !r.toString().trim()) { hidePill(); return; }
          showPill(r);
        }, 0);
      });
      card.addEventListener('mousedown', function (e) {
        if (pillEl && e.target !== pillEl && !pillEl.contains(e.target)) hidePill();
      });
    }
    function finishComments() {
      hidePill();
      // A FILLED composer that was never Entered still counts: clicking a decision button is as
      // deliberate as Enter, and silently dropping typed text loses user input (user report,
      // real-IDE pass 2026-08-23).
      const ci = csEl && csEl.querySelector('.compose input');
      if (ci && ci.value.trim()) {
        comments.push({ a: pendAnchor, t: ci.value.trim(), n: pendOcc, mark: pendMark });
        pendMark = null;
      }
      unwrapMark(pendMark); pendMark = null; composing = false;
      // The precise selection marks are unwrapped and the anchors RE-highlighted by the shared
      // text-search highlighter — the same function replay runs on planComments — so the decided
      // card and its replay are produced by one code path and cannot drift (round 9).
      comments.forEach(function (c) { unwrapMark(c.mark); c.mark = null; });
      highlightAnchors(blkEl, comments);
      renderComments(false);
      // decided card keeps the rows read-only — they are the record, and replay rebuilds the
      // same rows from the transcript (planCommentRows both times)
      if (csEl) Array.prototype.forEach.call(csEl.querySelectorAll('.c-x'), function (x) { x.remove(); });
      return comments.slice();
    }
    // ---------------------------------------------------------------------------------------
    const done = function (allow, sugg, viaEditor) {
      delete permCards[ev.id];
      // The command as edited on the card (3.8), only when it differs from what was proposed. An
      // edited command grants nothing beyond this run: the suggestions describe the ORIGINAL
      // command, so they are dropped with the edit (the split is hidden the moment the text
      // changes, see the input handler below — this is the belt to that brace).
      const cmdEl = card.querySelector('.cmd[contenteditable]');
      const cmdEdited = allow && cmdEl && cmdEl.textContent !== cmdEl.dataset.orig ? cmdEl.textContent : null;
      if (cmdEdited && sugg && sugg.rules) {
        // The grant follows the edit: Kotlin rewrites the echoed rules to the edited text, one
        // exact rule per part of a compound (EditProposals.withRulesFor). The chip shows the
        // edited command whole — the split is Kotlin's, and lives once.
        sugg = Object.assign({}, sugg, { rules: [cmdEdited] });
      }
      if (cmdEl) { cmdEl.removeAttribute('contenteditable'); cmdEl.removeAttribute('title'); }
      // The typed reason rides every decision (terminal parity: its allow branches all carry
      // acceptFeedback too). Read before the input leaves with the buttons.
      const fbEl = card.querySelector('.plan-fb');
      // An ordinary card's note rides deny only (see cardBtns): on allow it is dropped here rather
      // than quoted on the line as if it had been delivered.
      const fb = (fbEl && (isPlan || !allow)) ? fbEl.value.trim() : '';
      if (fbEl) fbEl.remove();
      // Plan comments ride whichever answer leaves. Deny wears the reference client's exact
      // shape (prefix + blank line + optional free text + header + [Re: "…"] lines — measured
      // off its transcript 2026-08-23); approve sends the same header + lines through the
      // PLAN_NOTES_MARKER append in Kotlin. SessionStore parses the lines back for replay.
      const cs = isPlan ? finishComments() : [];
      let wireFb = fb;
      if (cs.length) {
        const lines = cs.map(function (c) { return '[Re: "' + c.a + '"' + planOrd(c.n) + '] ' + c.t; }).join('\n');
        const block = LIM.planCommentsHeader + '\n' + lines;
        wireFb = allow ? (fb ? fb + '\n\n' : '') + block
                       : LIM.planDenyPrefix + '\n\n' + (fb ? fb + '\n\n' : '') + block;
      }
      // The separator marks the live decision surface — a decided card has none (replayCard
      // draws plan + footer only, and the two paths must not disagree).
      const sepEl = card.querySelector('.plan-sep');
      if (sepEl) sepEl.remove();
      card.querySelector('.card-b').innerHTML = allow
        ? '<span class="ok-t">✓ ' + (isPlan ? 'Approved' : 'Accepted') + (viaEditor ? ' in the editor' : '') +
            (cmdEdited ? ' · ' + esc(LIM.tweakNote) : '') +
            (fb ? fbQuote(fb) : '') + planCmtNote(cs.length, !!fb) +
            (sugg ? ' · ' + esc(sugg.doneText) : '') +
            (sugg && sugg.rules ? ' ' + sugg.rules.map(ruleChip).join('') : '') + '</span>'
        : '<span class="no-t">✗ ' + (isPlan ? 'Kept planning' : 'Rejected') + (viaEditor ? ' in the editor' : '') +
            (fb ? fbQuote(fb) : '') + planCmtNote(cs.length, !!fb) + '</span>';
      awaitingUser = false;
      // Remember what the deny will say — Kotlin sends the trimmed text, or the stock message when
      // empty — so the tool line can recognise the result as this card's own (onUserEvent).
      if (!allow) cardDenies[(wireFb || '').trim() || LIM.rejectMessage] = true;
      // The editor path already answered the CLI through Kotlin's arbiter — sending again from
      // here would be a duplicate (and would be dropped, but why knock).
      // pickDest is set only by a destination row — the main half sends none, so the CLI keeps the
      // suggestion's own destination and the echo is byte-for-byte what it was before 4.8.
      if (!viaEditor) window.respondPermission(ev.id, allow, sugg ? sugg.idxs : null, wireFb, sugg ? sugg.pickDest : null, cmdEdited);
      // Approve-with-notes rides the approved plan itself (Kotlin appends it to updatedInput.plan
      // — no user message exists), so the quoted footer is the note's whole visible record here,
      // exactly as it is on replay.
      if (busy) showWorking(); // Claude resumes processing
    };
    permCards[ev.id] = function (allow, tweak) {
      // Tweak-travel (3.5): the pane the user accepted is not the block the model proposed.
      // Redraw the diff from what RAN (whole-file old/new — renderEditDiff trims the common
      // prefix/suffix, so only the changed region shows, same as a replayed structuredPatch)
      // and note it under the diff, the way replay draws `tweaked` (LIM.tweakNote).
      // A NEW .diff element rather than innerHTML: foldBlock's state (dataset.folded, the click
      // toggle, the fold verdict taken at the OLD height) lives on the element — the first hand
      // test (2026-08-28) left a 19-row diff that could not collapse. wholeFileHunk gives the
      // 3-line-context rows replay draws from structuredPatch, through the same patchRows.
      if (tweak) {
        const d = card.querySelector('.diff');
        if (d) {
          const nd = document.createElement('div'); nd.className = 'diff';
          nd.innerHTML = patchRows(wholeFileHunk(tweak.oldStr || '', tweak.newStr || ''));
          d.replaceWith(nd); foldBlock(nd);
          const n = noteLine(LIM.tweakNote); if (n) nd.after(n);
        }
      }
      done(allow, null, true);
    };
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
    // A destination row grants the WHOLE set (every idx, not "N.R"): the choice is about where,
    // not which. The decided line records the scope, since the file it went to is not on screen.
    Array.prototype.forEach.call(card.querySelectorAll('.card-menu .popup-item.dest'), function (it) {
      it.onclick = function () {
        const s = suggs[+it.dataset.s], d = it.dataset.d;
        done(true, { idxs: s.idxs, rules: s.rules, doneText: s.doneText + ' ' + DESTS[d].done, pickDest: d });
      };
    });
    Array.prototype.forEach.call(card.querySelectorAll('.card-menu .popup-item:not([data-act]):not(.dest)'), function (it) {
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
    // Editing the command (3.8): while the text differs from the proposal a compound card's
    // per-rule rows hide (they describe the original parts); Always-allow itself stays on every
    // card and grants the edited text.
    // Typing must not reach the document handlers, and the fold toggle must not fire on a click
    // meant to place the caret.
    const cmdIn = card.querySelector('.cmd[contenteditable]');
    if (cmdIn) {
      cmdIn.addEventListener('input', function () {
        card.classList.toggle('cmd-edited', cmdIn.textContent !== cmdIn.dataset.orig);
      });
      cmdIn.addEventListener('keydown', function (e) { e.stopPropagation(); });
      cmdIn.addEventListener('click', function (e) { e.stopPropagation(); });
    }
    // No shortcuts on the plan card's input (user, 2026-08-16) — but typing must not reach the
    // document handlers (Escape would close popups, arrows would move popup cursors). On an
    // ordinary card Enter submits the note as a reject: the form convention for a text field,
    // not a card shortcut (4.9 stays deferred).
    const fbIn = card.querySelector('.plan-fb');
    if (fbIn) fbIn.addEventListener('keydown', function (e) {
      e.stopPropagation();
      if (!isPlan && e.key === 'Enter') { e.preventDefault(); done(false); }
    });
  }

