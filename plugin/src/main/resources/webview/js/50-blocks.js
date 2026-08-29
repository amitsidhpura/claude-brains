  /* ---------- transcript replay ----------
     Rebuilds a past conversation with the same blocks the live path draws. The CLI's JSONL
     carries everything needed: thinking, tool inputs, tool_result (stdout / is_error) and
     `toolUseResult.structuredPatch` — an authoritative diff with real line numbers, so replayed
     edits don't need the live path's prefix/suffix heuristic. Cards replay resolved (no
     controls): a tool that appears in the transcript is one that actually ran. */
  const MAX_DIFF_ROWS = 400;

  // structuredPatch hunks -> diff rows. Removed lines number off the old file, kept/added off the new.
  /**
   * One structuredPatch-shaped hunk from two whole-file texts: the changed region plus [ctx]
   * lines of context either side, {oldStart, newStart, lines:[' ctx','-del','+add']}. Used by the
   * tweak-travel card redraw (3.5), whose `updatedInput` is a WHOLE-FILE old/new — rendering that
   * through renderEditDiff shows every unchanged line; this gives the card the same 3-line-context
   * rows replay draws from the CLI's own structuredPatch. Empty array when the texts are equal.
   */
  function wholeFileHunk(oldStr, newStr, ctx) {
    const O = String(oldStr).split('\n'), N = String(newStr).split('\n');
    if (ctx == null) ctx = 3;
    let p = 0;
    while (p < O.length && p < N.length && O[p] === N[p]) p++;
    let s = 0;
    while (s < O.length - p && s < N.length - p && O[O.length - 1 - s] === N[N.length - 1 - s]) s++;
    if (p === O.length && p === N.length) return [];
    const from = Math.max(0, p - ctx), lines = [];
    for (let i = from; i < p; i++) lines.push(' ' + O[i]);
    for (let i = p; i < O.length - s; i++) lines.push('-' + O[i]);
    for (let i = p; i < N.length - s; i++) lines.push('+' + N[i]);
    const tailO = O.length - s, tailEnd = Math.min(O.length, tailO + ctx);
    for (let i = tailO; i < tailEnd; i++) lines.push(' ' + O[i]);
    return [{ oldStart: from + 1, newStart: from + 1, lines: lines }];
  }
  function patchRows(hunks) {
    let rows = '', n = 0, truncated = false;
    for (let h = 0; h < hunks.length && !truncated; h++) {
      const hunk = hunks[h] || {};
      if (h > 0) rows += diffRow('ctx', '  ', null, '⋯');
      let oldLine = hunk.oldStart || 1, newLine = hunk.newStart || 1;
      const lines = hunk.lines || [];
      for (let i = 0; i < lines.length; i++) {
        if (n++ >= MAX_DIFF_ROWS) { truncated = true; break; }
        const raw = lines[i], mark = raw.charAt(0), body = raw.slice(1);
        if (mark === '-') rows += diffRow('del', '- ', oldLine++, body);
        else if (mark === '+') rows += diffRow('add', '+ ', newLine++, body);
        else { rows += diffRow('ctx', '  ', newLine, body); oldLine++; newLine++; }
      }
    }
    if (truncated) rows += diffRow('ctx', '  ', null, '… diff truncated');
    return rows;
  }

  /* ---------- shared block builders ----------
     Live and replay draw the same structures through these, so the two paths cannot drift —
     several renderer-parity findings were exactly that drift. */
  // one IN/OUT row; kind is "IN" | "OUT", text set safely
  // Mirror of RenderLimits.descSuffix — that Kotlin doc comment owns the rule. A Read of lines
  // 40-80 rendered identically to a Read of the whole file, which is the difference between
  // "Claude read this" and "Claude read a slice of this". Deliberately not a per-tool matrix:
  // Grep/Glob never occur locally, and Bash's command and Edit's strings are already on screen.
  function descSuffix(tool, offset, limit) {
    if (tool !== 'Read') return '';
    const o = (typeof offset === 'number') ? offset : null;
    const l = (typeof limit === 'number') ? limit : null;
    // -1 because `limit` counts lines: 40 + 80 ends at 119, not 120
    if (o !== null && l !== null) return ' (lines ' + o + '-' + (o + l - 1) + ')';
    if (o !== null) return ' (from line ' + o + ')';
    if (l !== null) return ' (first ' + l + ' lines)';
    return '';
  }
  // THE cut rule, mirrored from RenderLimits.cut (RenderLimits.kt) — that Kotlin doc comment owns
  // it. Live truncates in JS and replay in Kotlin, so the ALGORITHM has to match, not just the cap:
  // otherwise the same output reports a different amount dropped while streaming than after a
  // resume. `lines` counts WHOLE dropped lines; the first fragment after the cut is the tail of the
  // last line still on screen, not a line of its own. RenderLimitsTest pins the cases.
  function cutInfo(text, max) {
    if (text.length <= max) return null;                 // nothing dropped, no marker
    const rest = text.slice(max);
    // a single trailing newline ends the last dropped line, it does not begin another
    const body = rest.charAt(rest.length - 1) === '\n' ? rest.slice(0, -1) : rest;
    let lines = 0;
    for (let i = 0; i < body.length; i++) if (body.charCodeAt(i) === 10) lines++;
    // `shown` is live-only convenience; the wire shape Kotlin sends for replay is {lines, bytes},
    // because there the truncated text already travels as `out`/`cmd`.
    return { shown: text.slice(0, max), lines: lines, bytes: rest.length };
  }
  // Mirror of RenderLimits.persistedOutput (RenderLimits.kt owns the rule). The CLI truncates before
  // we do: an oversized Bash result is replaced by a wrapper naming the file it was spilled to.
  // Replay gets the same facts as real fields on toolUseResult, but the LIVE event carries no
  // toolUseResult at all (probed 2026-08-05), so parsing this is the only way live can match replay.
  // BOTH tags are required at the very start and end: a Bash result that merely MENTIONS the tag
  // (grepping for it, dumping a transcript) must not have its body eaten.
  const SPILL_PATH = /saved to:[ \t]*(\S.*?)[ \t]*(?:\r?\n|$)/;
  const SPILL_SIZE = /\(([\d.]+)\s*([KMGT]?)B\)/i;
  const SPILL_PREVIEW_HEAD = /^Preview\b[^\n]*:[ \t]*\r?\n/m;
  function persistedOutput(text) {
    const t = (text || '').trim();
    if (t.indexOf('<persisted-output>') !== 0) return null;
    if (t.lastIndexOf('</persisted-output>') !== t.length - 19) return null;
    const body = t.slice(18, t.length - 19);
    const p = SPILL_PATH.exec(body), sz = SPILL_SIZE.exec(body);
    // binary units: 412387 bytes is reported as "402.7KB" (412387/1024), not 412.4KB
    const mult = { '': 1, K: 1024, M: 1048576, G: 1073741824, T: 1099511627776 };
    const head = SPILL_PREVIEW_HEAD.exec(body);
    let preview = (head ? body.slice(head.index + head[0].length) : body).trim();
    // the CLI's own trailing "..." goes — our marker is the canonical statement of what is missing
    if (preview.slice(-3) === '...') preview = preview.slice(0, -3).replace(/\s+$/, '');
    return {
      preview: preview,
      bytes: sz ? Math.round(parseFloat(sz[1]) * mult[sz[2].toUpperCase()]) : null,
      path: p ? p[1] : null,
    };
  }
  // "this much was thrown away", NOT the fold's "click to see the rest" — the fold keeps its content
  // and this content is gone, so the two must read differently. `total`/`path` describe the CLI's
  // own earlier truncation (it caps stdout and spills the whole result to a file).
  // One wording, two builders: the DOM one below and previewHtml's string one, which cannot drift.
  function cutText(cut, total, path) {
    // lines is 0 for output with no newlines at all (minified JSON, one long line) — size alone is
    // the honest thing to show there rather than a meaningless "+0 lines"
    const s = total
      ? '⋯ ' + fmtSize(total) + ' total, not shown here'
      : '⋯ +' + (cut.lines ? cut.lines.toLocaleString() + ' lines · ' : '') + fmtSize(cut.bytes) + ' not shown';
    return s + (path ? ' — open full output' : '');
  }
  function cutEl(cut, total, path) {
    const e = document.createElement('span');
    e.className = 'io-cut';
    if (path) { e.dataset.path = path; e.classList.add('click'); }
    e.textContent = cutText(cut, total, path);
    return e;
  }
  // What the CLI said about the result itself, as against what the result WAS: an exit-code
  // explanation ("No matches found" for a grep that matched nothing), or that the command was
  // killed. Same geometry as the truncation marker — below the value, outside .io-v — because it is
  // the same kind of thing: one line of prose about the row. Replay-only; the live stream carries no
  // toolUseResult (probed 2026-08-05), and unlike the spill wrapper there is no prose fallback.
  function noteEl(note, interrupted) {
    const e = document.createElement('span');
    e.className = 'io-note';
    // ⏹ is the stop glyph statusLine() already uses for an interrupted turn; ↳ reads as "which means"
    e.textContent = interrupted ? '⏹ Interrupted' : '↳ ' + note;
    return e;
  }
  // `cut` is a {lines, bytes} from cutInfo() (live) or the identically-shaped one Kotlin puts on the
  // wire (replay). Marker and note are both SIBLINGS of .io-v, never children: foldBlock collapses
  // .io-v to three lines, so either one inside it would itself be folded away — the notice that
  // content was cut, cut.
  function ioRow(kind, text, cut, total, path, note, interrupted) {
    const frag = document.createDocumentFragment();
    const row = document.createElement('div'); row.className = 'io-row';
    row.innerHTML = '<span class="io-k"></span><span class="io-v"></span>';
    row.querySelector('.io-k').textContent = kind;
    row.querySelector('.io-v').textContent = text;
    frag.appendChild(row);
    // Fold the ROW, not the value inside it — the row is the scroll box, and .fold:not(.open)'s
    // `overflow: hidden` only crops the element it is applied to. Folding .io-v instead left a
    // collapsed row still scrolling sideways, where the permission card's .diff (one box that is
    // padded, scrollable AND foldable) crops while folded and scrolls only once expanded. Same
    // element for both is what makes that behaviour fall out for free.
    foldBlock(row);                          // measured on the post-insert frame (callers mount same-tick)
    // Marker and note are siblings of the ROW, not children of it: the row is the horizontal
    // scroll box, so anything inside it slides out of view with the text — and a `flex: 0 0 100%`
    // item is 32px wider than the container once indented, which gave every row carrying one a
    // phantom 32px of scroll. Outside it they stay put and cost nothing. Still outside .io-v too,
    // for the older reason: foldBlock collapses it to three lines and would fold the notice away.
    if (cut || total) frag.appendChild(cutEl(cut || { lines: 0, bytes: 0 }, total, path));
    if (note || interrupted) frag.appendChild(noteEl(note, interrupted));
    return frag;   // a fragment, so every caller's appendChild still mounts row + markers together
  }
  function ioBox(rows) {
    const io = document.createElement('div'); io.className = 'io';
    rows.forEach(function (r) { io.appendChild(ioRow(r[0], r[1], r[2], r[3], r[4], r[5], r[6])); });
    return io;
  }
  /**
   * A server-side tool's results, as one OUT box under its tool line (item 12).
   *
   * Shape taken from the CLI's own reader, which is the only place it is written down:
   *   if (a.type === "web_search_tool_result") {
   *     if (!Array.isArray(a.content)) { `Web search error: ${a.content.error_code}` ; continue }
   *     let l = a.content.map(c => ({title: c.title, url: c.url}))
   *   }
   * So `content` is EITHER an array of results OR an error object — the array check is the
   * discriminator, not a `type` field, and getting that backwards would print "[object Object]".
   *
   * THE RULE ITSELF lives in RenderLimits.searchResults (Kotlin) — this is the mirror, exactly as
   * `cutInfo` mirrors `cut`. Equal caps do not help if the two sides format the body differently:
   * one OUT box would then say something the other does not, and a resume would silently rewrite
   * what the search returned. RenderLimitsTest pins the pair.
   */
  function searchResultText(content) {
    if (!Array.isArray(content)) {
      const code = content && (content.error_code || content.errorCode);
      return { text: 'Web search error: ' + (code || 'unknown'), isError: true };
    }
    if (!content.length) return { text: 'No results.', isError: false };
    // Title over url, blank line between, so a long list stays scannable inside the fold.
    const body = content.map(function (r) {
      const title = (r && (r.title || r.url)) || 'untitled';
      const url = (r && r.url) || '';
      return url && url !== title ? title + '\n' + url : String(title);
    }).join('\n\n');
    return { text: content.length + (content.length === 1 ? ' result\n\n' : ' results\n\n') + body,
             isError: false };
  }
  function serverToolResult(cb) {
    const t = cb.tool_use_id ? toolsById[cb.tool_use_id] : null;
    const res = searchResultText(cb.content);
    // An unmatched result must still be visible: without its tool line there is nothing on screen
    // to hang it under, and silently dropping it is the very hole this item exists to close.
    const line = t ? t.el : track(toolLine('web_search'));
    const holder = t || { el: line, io: null };
    // A server_tool_use was marked in flight at content_block_start and its result arrives HERE,
    // as a content block — it never reaches onUserEvent, so this is the only place that can settle
    // it. On the unmatched-result branch above this is a no-op: toolLine() never sets .run.
    line.classList.remove('run');
    if (res.isError) line.classList.add('fail');
    if (!holder.io) { holder.io = ioBox([]); line.after(holder.io); }
    const cut = cutInfo(res.text, LIM.outMax);
    holder.io.appendChild(ioRow('OUT', cut ? cut.shown : res.text, cut));
    maybeScroll();
  }
  /**
   * `system/informational` — the CLI's own notices (client-parity item 22). The one that made this
   * worth building is a hook denying a non-tool event: probed on 2.1.222, a `UserPromptSubmit` hook
   * exiting 2 emits
   *
   *   {subtype:"informational", level:"warning", prevent_continuation:true,
   *    content:"UserPromptSubmit operation blocked by hook:\n[…]: REASON\n\nOriginal prompt: …"}
   *
   * and NOTHING else — no assistant turn, no text. Unhandled, the user pressed Enter and the panel
   * simply sat there. There is no terminal to check either; this CLI is ours (rule 1).
   *
   * `level` is the CLI's own prominence scale, described as: "'info' shows only in transcript mode;
   * 'notice' renders in inactive gray; 'suggestion' and 'warning' are more prominent." We have no
   * second mode to hide `info` in, and silently dropping a message is the exact failure this audit
   * exists to correct, so it renders muted rather than not at all.
   *
   * `tool_use_id` is documented as "Dedupes progress messages for the same tool use", so repeats
   * REPLACE their predecessor instead of stacking — otherwise a chatty tool would paper the
   * timeline with near-identical lines.
   */
  const INFO_LEVELS = Object.assign(Object.create(null), {
    warning:    { cls: 'status err', icon: true },
    suggestion: { cls: 'status',     icon: true },
    notice:     { cls: 'status',     icon: false },
    info:       { cls: 'status',     icon: false },
  });
  let infoByTool = {};   // tool_use_id -> the line to update in place; reset per session
  function infoLine(content, level, toolUseId) {
    const text = String(content == null ? '' : content).trim();
    if (!text) return;   // an empty notice is not a notice
    const lv = INFO_LEVELS[level] || INFO_LEVELS.notice;   // unknown level degrades to the quiet one
    const prev = toolUseId ? infoByTool[toolUseId] : null;
    if (prev && prev.isConnected) {
      // Replace the text, keep the element: re-appending would scroll the timeline for what is
      // logically the same message saying something new.
      prev.className = lv.cls;
      prev.textContent = '';
      if (lv.icon) {
        const s = document.createElement('span'); s.className = 's-ic';
        s.innerHTML = SVG_ALERT; prev.appendChild(s);
      }
      prev.appendChild(document.createTextNode(text));
      return;
    }
    const el2 = statusLine(text, lv.icon ? SVG_ALERT : null, lv.cls);
    if (toolUseId) infoByTool[toolUseId] = el2;
  }
  /**
   * Sub-agent progress (client-parity item 1) — what a `Task`/`Agent` sub-agent is doing while it
   * runs, on the tool line that launched it.
   *
   * Driven by the CLI's task lifecycle, probed on 2.1.222 with a real synchronous sub-agent:
   *   task_started      {task_id, tool_use_id, description, subagent_type, task_type, prompt}
   *   task_progress     {task_id, tool_use_id, description, subagent_type, last_tool_name,
   *                      usage:{total_tokens, tool_uses, duration_ms}}
   *   task_updated      {task_id, patch:{status, end_time}}
   *   task_notification {task_id, tool_use_id, status, summary, output_file, usage}
   *
   * DELIBERATELY NOT the nested tool-call tree the item described. The child messages do exist —
   * `assistant`/`user` events carrying `parent_tool_use_id` — but this line is what the terminal
   * itself shows ("N tool uses · M tokens"), and it answers the question actually being asked while
   * a sub-agent runs, which is "is it still going, and on what?".
   *
   * LIVE ONLY, and unavoidably so: none of these events is ever persisted (measured — zero
   * `task_started`/`task_progress`/`task_notification` in any transcript, and zero
   * `isSidechain:true` across 23,123 records). A resumed session keeps the Agent tool line, its
   * prompt and its result, and loses this line. Recorded in docs/renderer-parity.md as an accepted
   * divergence rather than a bug, because the alternative is showing nothing while a sub-agent runs.
   */
  let taskProg = {};   // task_id -> {el, type}; reset per session
  function taskLine(ev, done) {
    const st = ev.task_id ? (taskProg[ev.task_id] || (taskProg[ev.task_id] = {})) : {};
    // `subagent_type` rides on task_started/task_progress but NOT on task_notification (checked
    // against the real frames), so it is remembered — otherwise the finished line drops the one
    // word that says which kind of agent ran.
    if (ev.subagent_type) st.type = String(ev.subagent_type);
    // Fetched here rather than at element-creation time because the tool line's OWN description is
    // needed while `bits` is built — see the duplicate check below.
    const tool = ev.tool_use_id ? toolsById[ev.tool_use_id] : null;
    // Remember the tool line this task belongs to. task_notification/task_updated are the frames
    // that say a sub-agent is DONE, and task_updated carries no tool_use_id at all — so without
    // this the finishing frame cannot reach the line that has to stop pulsing.
    if (tool) st.tool = tool;
    // The sub-agent's own line settles when the task does, not when it was launched — and the task
    // is also the ONLY place a sub-agent's failure can come from. Its tool_result was the launch ack
    // and said nothing about the outcome, so without this an agent that died would still settle
    // green. The status vocabulary is read verbatim out of the CLI binary (`strings`): 2.1.228 wrote
    // `<status>completed|failed|killed</status>`; 2.1.250's typed schema is `completed|failed|stopped`
    // for task_notification while task_updated's patch still says `killed` — MEASURED 2026-08-29
    // (a panel ✕ on a shell sent task_updated{killed} then task_notification{stopped}). Both words
    // stay: the two frames arrive together and a version that drops one must not lose the colour.
    // Named states only, NOT "anything that is not completed": the binary also carries `running` and
    // `pending` for other task kinds, and painting a still-running line red is a worse failure than
    // leaving an unknown state green.
    // What this CANNOT tell: whether the agent's WORK succeeded. Measured 2026-08-29 (2.1.250): an
    // Explore agent whose Bash returned is_error:true and which replied "FAILED: …" still ended
    // task_updated{completed} + task_notification{completed}; the summary prose is the only signal,
    // and colouring from prose was rejected 2026-08-13 (checklist 11.4).
    if (done && st.tool && st.tool.el) {
      st.tool.el.classList.remove('run');
      if (ev.status === 'failed' || ev.status === 'killed' || ev.status === 'stopped') st.tool.el.classList.add('fail');
    }
    const u = ev.usage || {};
    const bits = [];
    if (st.type) bits.push(st.type);
    // `summary` is the finished answer, `description` the running commentary — so a completed line
    // stops saying "Reading words.txt" and says what came back instead.
    // stripPlumbing FIRST, then collapse whitespace (7.4): the harness envelope the CLI prepends to
    // a flagged sub-agent's output is one line ending in `]`, and collapsing newlines first would
    // weld it to the summary and leave nothing to anchor on. Untouched it WAS the finished line —
    // "Explore · [harness: subagent output matched instruction-shaped pattern(s): settings-json …]"
    // — because the marker is longer than descMax, so the real summary never made it on screen.
    const what = stripPlumbing(String((done ? (ev.summary || ev.description) : ev.description) || ''));
    // The task lifecycle is NOT sub-agent-only — the CLI's emit site sits among `runningSubagents`,
    // `isBackgrounded` and `local_workflow` (2.1.226, read with `strings`), so an ordinary tracked
    // tool use produces task_started carrying a `description` and no subagent_type or usage. For a
    // Bash that description is the very string `.t-desc` already shows on the tool line, so the
    // whole progress line came out a word-for-word copy of the line above it.
    // EXACT match only, and not "never show the description": for a real sub-agent it is running
    // commentary that changes as it works (see the note above), and dropping it would gut item 1.
    // The check has to cover the IN box too, and that is the half this originally missed. Bash's
    // `.t-desc` is blank BY DESIGN — its command belongs in the IN box, which is why `command` is
    // in LIM.inKeys and not LIM.descKeys — so a Bash called WITHOUT a `description` has an empty
    // `.t-desc`, the frame's description falls back to the command itself, and the copy sailed
    // straight past a guard that only ever looked at the tool line. Reported from a screenshot
    // 2026-08-12: the command in full inside the box, and again underneath it cut to descMax
    // mid-word. Located by the `IN` key rather than by position, since a box can hold OUT alone.
    const norm = function (s) { return String(s || '').replace(/\s+/g, ' ').trim(); };
    const td = tool && tool.el ? tool.el.querySelector('.t-desc') : null;
    const inRow = tool && tool.io ? Array.prototype.filter.call(
      tool.io.querySelectorAll('.io-row'),
      function (r) { const k = r.querySelector('.io-k'); return k && norm(k.textContent) === 'IN'; })[0] : null;
    const dup = function (e) { return !!e && norm(what) === norm(e.textContent); };
    if (what && !dup(td) && !dup(inRow && inRow.querySelector('.io-v'))) {
      bits.push(what.replace(/\s+/g, ' ').slice(0, LIM.descMax));
    }
    const n = u.tool_uses;
    if (typeof n === 'number' && n > 0) bits.push(n + (n === 1 ? ' tool use' : ' tool uses'));
    if (typeof u.total_tokens === 'number' && u.total_tokens > 0) bits.push(fmtTok(u.total_tokens) + ' tokens');
    if (done && ev.status && ev.status !== 'completed') bits.push(String(ev.status));
    // Decided BEFORE touching the DOM: building the element first left an empty div behind whenever
    // a frame carried nothing worth printing (a task_started with no description, zeroed counts).
    if (!bits.length) {
      // A finished frame with nothing left to say must not leave an earlier line wearing the
      // in-flight colour (.t-prog.run) forever.
      if (done && st.el) st.el.classList.remove('run');
      return;
    }
    if (!st.el || !st.el.isConnected) {
      if (!tool) return;   // no tool line to hang it on (the Agent line was never opened)
      st.el = document.createElement('div');
      st.el.className = 't-prog run';
      // After the IN box when there is one, so it reads: what it is · what it was asked · how it
      // is going.
      (tool.io || tool.el).after(st.el);
    }
    st.el.textContent = bits.join(' · ');
    st.el.classList.toggle('run', !done);
    maybeScroll();
  }
  /**
   * An image a TOOL returned — a Playwright screenshot, or `Read` on a PNG (client-parity item 8).
   *
   * Shape, from a real record rather than the audit's guess:
   *   live   tool_result block  {type:"image", source:{media_type:"image/png", data:<base64>}}
   *   replay toolUseResult      {type:"image", file:{base64, type:"image/png", dimensions,
   *                                                  originalSize}}
   * The discriminator is `type === "image"`. It is NOT `isImage`, which the item named: that field
   * is real but belongs to BASH results, where it is always false — which is why measuring it found
   * zero and the gap looked unreachable.
   *
   * Without this the result renders as NOTHING: the text path filters for `type === 'text'` blocks,
   * an image-only result yields an empty string, and the early return drops it — leaving a bare
   * `Read` tool line under a reply that says "screenshot displayed above".
   *
   * `dimensions` reserves the box BEFORE the data URI decodes, so a screenshot landing mid-turn
   * does not shove the timeline down as it paints.
   */
  function toolImages(imgs) {
    const box = document.createElement('div');
    box.className = 'tool-imgs';
    (imgs || []).forEach(function (im) {
      if (!im) return;   // one malformed entry must not take out the whole result
      const name = im.name || 'image';
      if (!im.data) {
        // Past the replay image budget: the chip still records that an image was here, because a
        // silently absent screenshot is the very thing this item fixes.
        const miss = document.createElement('div');
        miss.className = 'ti-missing';
        miss.textContent = name + ' — image not kept in this replay';
        box.appendChild(miss);
        return;
      }
      const img = document.createElement('img');
      img.className = 'ti';
      img.alt = name;
      // Real key names, taken from an actual record: the CLI sends
      // {originalWidth, originalHeight, displayWidth, displayHeight} — NOT {width, height}, which
      // an earlier guess here used, so the box was never reserved and the timeline reflowed.
      const d = im.dimensions || {};
      const w = d.displayWidth || d.originalWidth || d.width;
      const h = d.displayHeight || d.originalHeight || d.height;
      if (w && h) { img.width = w; img.height = h; }
      img.src = 'data:' + (im.media_type || 'image/png') + ';base64,' + im.data;
      img.onclick = function () { openLB(img.src, name); };
      box.appendChild(img);
    });
    return box.childNodes.length ? box : null;
  }
  // Mirror of RenderLimits.stripPlumbing — the Kotlin doc comment owns the rule (5.9, 7.4). Tag
  // names ride LIMITS.plumbingTags so the two renderers cannot disagree on the list; the leading
  // `[harness: …]` envelope is a regex on both sides, like cut/cutInfo.
  const HARNESS_MARKER = /^\[harness:[^\]]*\]\r?\n?/;
  function stripPlumbing(t) {
    if (!t) return t;
    t = String(t).replace(/^\s+/, '').replace(HARNESS_MARKER, '');
    for (const tag of LIM.plumbingTags) {
      t = t.split('<' + tag + '>').join('').split('</' + tag + '>').join('');
    }
    return t.trim();
  }
  /**
   * A result the CLI wrote for the MODEL, which therefore gets no OUT box (7.4). Mirror of
   * `RenderLimits.isInternalResult` — the Kotlin doc comment owns the rule, including why this is
   * keyed on content where RESULT_SKIP is keyed on the tool name.
   */
  function isInternalResult(text) {
    const first = String(text || '').replace(/^\s+/, '').split('\n')[0];
    return /\(This tool result is internal metadata\b[^)]*\)\s*$/.test(first);
  }
  /**
   * The `(note: …)` the CLI appends to an otherwise-successful tool result (item 11). Mirror of
   * `RenderLimits.resultNote` — same two-language contract as `cut`/`cutInfo`, pinned by tests.
   * Anchored to the END so a parenthetical inside ordinary output cannot be mistaken for it.
   */
  function resultNote(text) {
    const m = /\(note:\s*([\s\S]+?)\)\s*$/.exec(String(text || '').trim());
    const s = m ? m[1].replace(/\s+/g, ' ').trim() : '';
    return s || null;
  }
  /** Standalone caveat line, for tools whose result is otherwise skipped (Edit, Write). */
  function noteLine(note) {
    if (!note) return null;
    const d = document.createElement('div');
    d.className = 't-note';
    d.textContent = '↳ ' + note;   // same "which means" glyph as the IN/OUT note from item 9
    return d;
  }
  /**
   * The project root, for shortening the paths on tool lines.
   *
   * Pushed by ChatPanel as `__project` when the panel opens, and refreshed from `system/init`'s
   * `cwd` (the CLI builds that frame as `subtype:"init",cwd:e.cwd,…` — read from the 2.1.226
   * binary). The IDE is the primary because init only arrives at the first TURN, and a resumed
   * transcript renders before that.
   */
  let projectRoot = '';
  function setProjectRoot(r) {
    r = String(r || '').replace(/[\/\\]+$/, '');
    if (r) projectRoot = r;
  }
  /**
   * Where the middle-ellipsis goes: everything before the cut may shrink, everything after never
   * does. Two cuts are considered — before the filename, and before the segment above it — and the
   * parent joins the tail only when the pair fits LIM.pathTailMax, because a tail that outgrows the
   * line is one the panel clips, and it clips the filename. Each cut lands BEFORE its separator so
   * the slash leads the span it belongs to ("…/controls/x.ts" rather than "…control…/x.ts").
   * Shared by fillPath and the @-mention menu so the two cannot disagree about where to cut.
   */
  function pathParts(disp) {
    disp = String(disp || '');
    const nameAt = Math.max(0, Math.max(disp.lastIndexOf('/'), disp.lastIndexOf('\\')));
    const upTo = disp.slice(0, nameAt);
    const parentAt = Math.max(0, Math.max(upTo.lastIndexOf('/'), upTo.lastIndexOf('\\')));
    const cut = (disp.length - parentAt) <= LIM.pathTailMax ? parentAt : nameAt;
    return { head: disp.slice(0, cut), tail: disp.slice(cut) };
  }
  /**
   * Draw a file path into a `.t-desc`, project-relative and on ONE line.
   *
   * Three things have to stay true at once, and each was a bug waiting to happen:
   *   1. the click must open the file — so the ABSOLUTE path rides on `dataset.path`, never the
   *      shortened text. That also fixes a path longer than LIM.descMax, which used to be clicked
   *      in its truncated form;
   *   2. the filename must survive the clamp, and so should its PARENT FOLDER — `controls/preview.d.ts`
   *      says what `preview.d.ts` alone does not. So the display is cut in two: `.p-tail` is the
   *      part that never shrinks, and `.p-head` is everything before it, which the CSS ellipsis eats
   *      from its END — putting the ellipsis in the MIDDLE of the path. The parent joins the tail
   *      only when the two together fit LIM.pathTailMax, because a tail that outgrows the line is
   *      one the panel clips, and it clips the filename. Letting CSS decide that instead was tried
   *      and measured on real JCEF: flex shrinks proportionally, so any factor large enough to save
   *      the filename on a long path also nibbles the parent on a short one;
   *   3. `textContent` must still read as the whole displayed path, since that is the fallback the
   *      click handler and every text assertion use.
   * Used by BOTH the live and the replay site, so the two cannot drift.
   */
  function fillPath(dEl, abs, shown) {
    const full = String(abs || shown || '');
    let disp = String(shown || full);
    // Only inside the project, and only on a SEGMENT boundary — /home/me/project-notes must not be
    // reported as living inside /home/me/project.
    if (projectRoot && full.indexOf(projectRoot + '/') === 0) disp = full.slice(projectRoot.length + 1);
    else if (projectRoot && full.indexOf(projectRoot + '\\') === 0) disp = full.slice(projectRoot.length + 1);
    // Two cuts: before the filename, and before the segment above it. A path with fewer segments
    // than that just leaves the earlier spans empty rather than inventing structure.
    // Each cut lands BEFORE its separator, so the slash leads the span it belongs to. That matters
    // once a span is ellipsised: cutting after the slash turned "controls/" into "control…" and the
    // line read as loose fragments — leading slashes keep it reading as a path, "…/control…/x.ts".
    const parts = pathParts(disp);
    dEl.textContent = '';
    dEl.classList.add('path');
    dEl.dataset.path = full;
    dEl.title = full;
    const part = function (cls, text) {
      const s = document.createElement('span');
      s.className = cls; s.textContent = text;
      dEl.appendChild(s);
      return s;
    };
    part('p-head', parts.head);
    part('p-tail', parts.tail);
    return dEl;
  }
  // timeline tool line: bold label + (empty) description span, appended to the current turn
  function toolLine(name) {
    const t = el('tool-line', '');
    t.innerHTML = '<b>' + esc(toolLabel(name)) + '</b><span class="t-desc"></span>';
    return t;
  }
  // API/stream error block, appended to the current turn
  function errorBlock(text) {
    const e = el('error', '');
    e.innerHTML = SVG_ALERT + '<div></div>';
    e.querySelector('div').textContent = text;
    return e;
  }
  // Finished thinking block: an empty body (the CLI often persists only a signature) renders as a
  // non-expandable `think no-body` line, a real one as a collapsible <details>. secs 0 hides the
  // duration ("Thought", not "Thought for 0s").
  // redacted (checklist 1.21): an API `redacted_thinking` block — encrypted `data`, no text, ever.
  // Same no-body line, labelled so it is not mistaken for "the CLI persisted only a signature".
  // Never seen locally (2026-08-29); the shape is the API's documented one.
  function thinkBlock(text, secs, redacted) {
    const empty = redacted || !(text || '').trim();
    const det = document.createElement(empty ? 'div' : 'details');
    det.className = empty ? 'think no-body' + (redacted ? ' redacted' : '') : 'think';
    det.innerHTML = '<summary>' + (redacted ? 'Thought (redacted)' : 'Thought') +
      (secs ? ' for ' + fmtDur(secs * 1000) : '') +
      (empty ? '' : SVG_CHEVRON) + '</summary>' + (empty ? '' : '<div class="body"></div>');
    if (!empty) det.querySelector('.body').textContent = text;
    return det;
  }
  // plan-card body (header + markdown); the footer — live buttons or replayed outcome — is the caller's
  function planCardHtml(planMd) {
    return '<div class="card-h"><b>Ready to code?</b> Here is my plan:</div>' +
      '<div class="blk">' + renderMd(planMd) + '</div>';
  }
  // Plan-comment rows (checklist 5.6): the SAME rows on a live card (removable ✕) and on a
  // decided or replayed one (read-only) — one builder so the two surfaces cannot drift.
  // cs: [{a, t}] — anchor text and note, in the order they were added / parsed.
  function planCommentRows(cs, removable) {
    return (cs || []).map(function (c, i) {
      return '<div class="plan-c" data-i="' + i + '"><span class="c-a" title="' + escA(c.a) + '">“' +
        esc(c.a) + '”</span><span class="c-t">' + esc(c.t) + '</span>' +
        (removable ? '<button class="c-x" title="Remove comment">' + SVG_X + '</button>' : '') +
        '</div>';
    }).join('');
  }
  // The decided-card footer suffix when comments were sent — live and replay must word it
  // identically. hasFb: a quoted free-text reason already sits before it (fbQuote), so the
  // separator steps down from an em dash to a middot.
  function planCmtNote(n, hasFb) {
    return n ? (hasFb ? ' · ' : ' — ') + 'sent ' + n + ' plan comment' + (n > 1 ? 's' : '') : '';
  }
  // Flat view of a plan body (concatenated text + per-node segments) and every match of an
  // anchor in it. ONE matcher serves the capture side (WHICH occurrence did the user select)
  // and the highlight side (live decide + replay), so the occurrence counts cannot drift —
  // substring hits (e.g. "one" inside "standalone") are counted identically on both ends
  // (round 10, user report: replay picked the first occurrence).
  function planFlat(blk) {
    const segs = []; let text = '';
    const walker = document.createTreeWalker(blk, NodeFilter.SHOW_TEXT);
    for (let n = walker.nextNode(); n; n = walker.nextNode()) {
      segs.push({ node: n, start: text.length });
      text += n.nodeValue;
    }
    return { segs: segs, text: text };
  }
  function anchorMatches(flat, anchor) {
    const a = (anchor || '').trim();
    if (!a) return [];
    const pat = new RegExp(a.replace(/[.*+?^${}()|[\]\\]/g, '\\$&').replace(/ /g, '\\s+'), 'g');
    const out = []; let m;
    while ((m = pat.exec(flat.text))) {
      out.push({ start: m.index, end: m.index + m[0].length });
      if (pat.lastIndex === m.index) pat.lastIndex++;   // zero-width safety
    }
    return out;
  }
  function flatPos(flat, node, off) {   // flat offset of (node, off), or -1 when node is foreign
    for (let i = 0; i < flat.segs.length; i++) if (flat.segs[i].node === node) return flat.segs[i].start + off;
    return -1;
  }
  function segAt(flat, pos) {
    for (let i = flat.segs.length - 1; i >= 0; i--) if (flat.segs[i].start <= pos) return flat.segs[i];
    return null;
  }
  // " (2nd occurrence)" for n=1, '' for n=0 — the wire marker an AMBIGUOUS anchor carries so
  // replay can land on the same occurrence. Kotlin's parsePlanComments reads it back; the two
  // spellings are pinned against each other by fixture 53 (JS) and RenderLimitsTest (Kotlin).
  function planOrd(n) {
    if (!n) return '';
    const k = n + 1;
    const s = (k % 10 === 1 && k % 100 !== 11) ? 'st'
      : (k % 10 === 2 && k % 100 !== 12) ? 'nd'
      : (k % 10 === 3 && k % 100 !== 13) ? 'rd' : 'th';
    return ' (' + k + s + ' occurrence)';
  }
  // Re-highlights comment anchors in a DECIDED plan body — the ONE function both surfaces run
  // (live decide after unwrapping its precise selection marks, and replay), so a decided card
  // and its replay cannot drift. Each comment's occurrence index (c.n, default 0) picks the
  // match; an anchor spanning element boundaries is skipped on BOTH surfaces alike.
  function highlightAnchors(blk, cs) {
    if (!blk || !cs || !cs.length) return;
    cs.forEach(function (c) {
      const flat = planFlat(blk);       // re-scan per comment: earlier wraps re-shaped the nodes
      const ms = anchorMatches(flat, c.a);
      if (!ms.length) return;
      const m = ms[Math.min(c.n || 0, ms.length - 1)];
      const sSeg = segAt(flat, m.start), eSeg = segAt(flat, m.end - 1);
      if (!sSeg || sSeg !== eSeg) return;                       // crossed a boundary — skip
      if (sSeg.node.parentNode && sSeg.node.parentNode.closest('mark.plan-anchor')) return;
      const r = document.createRange();
      r.setStart(sSeg.node, m.start - sSeg.start); r.setEnd(sSeg.node, m.end - sSeg.start);
      try {
        const mk = document.createElement('mark');
        mk.className = 'plan-anchor';
        r.surroundContents(mk);
      } catch (e) { /* boundary after all — skip, same on both surfaces */ }
    });
  }
  // Write preview: whole file as additions, capped like every other diff
  function writeDiffHtml(content, startLine) {
    const lines = String(content).split('\n');
    const shown = lines.slice(0, MAX_DIFF_ROWS);
    let rows = diffLines('+ ', 'add', shown.join('\n'), startLine);
    if (lines.length > shown.length) rows += diffRow('ctx', '  ', null, '… diff truncated');
    return '<div class="diff">' + rows + '</div>';
  }
  // ask-card tab header + the tab-switch wiring, shared verbatim by live and replayed cards
  function askTabsHtml(qs) {
    let h = '<div class="ask-h"><div class="ask-tabs">';
    qs.forEach(function (q, i) {
      h += '<button class="ask-tab' + (i === 0 ? ' active' : '') + '" data-q="' + i + '">' +
        esc(q.header || ('Q' + (i + 1))) + '</button>';
    });
    return h + '</div></div>';
  }
  function wireAskTabs(card) {
    const tabs = card.querySelectorAll('.ask-tab');
    const panels = card.querySelectorAll('.ask-panel');
    function selectTab(idx) {
      tabs.forEach(function (t, n) { t.classList.toggle('active', n === idx); });
      panels.forEach(function (p, n) { p.hidden = n !== idx; });
    }
    tabs.forEach(function (tab, idx) { tab.addEventListener('click', function () { selectTab(idx); }); });
    return { panels: panels, selectTab: selectTab };
  }
  // resolve a live ask card into its non-interactive record state (matches the replayed twin)
  function resolveAsk(card, resultHtml) {
    card.querySelectorAll('input').forEach(function (i) { i.disabled = true; });
    card.classList.add('ask-done');   // hover/cursor off
    card.querySelector('.ask-b').innerHTML = resultHtml;
    const foot = card.querySelector('.ask-foot'); if (foot) foot.remove();
    activeAsk = null; awaitingUser = false;
  }

