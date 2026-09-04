  /* ---------- Kotlin -> page ---------- */
  let curBubble = null, curRaw = '';
  let curThink = null, curThinkRaw = '', thinkStart = 0, thinkChars = 0, thinkTimer = null,
      thinkTok = null; thinkTokReal = null;

  /**
   * Markdown re-render throttle: renderMd(curRaw) re-parses (and re-highlights) the WHOLE
   * accumulated message, so doing it per text_delta is O(n²) over message length. Deltas only
   * mark dirty; the render runs once per animation frame. Anything that finalizes or replaces
   * curBubble must call flushMd() first so the last deltas aren't lost.
   */
  let mdPending = false;
  function flushMd() {
    if (!mdPending) return;
    mdPending = false;
    if (curBubble) { curBubble.innerHTML = renderMd(curRaw); maybeScroll(); }
  }
  let openTool = null;        // tool block currently streaming its input
  let pendingBgTasks = 0;     // background subagent tasks still running (from background_tasks_changed)
  let bgTasks = [];           // and WHICH ones: [{task_id, task_type, description}] — item 4's roster
  /**
   * The background-task roster (client-parity item 4). The event already carries
   * `{task_id, task_type, description}` per task, so this needed nothing from item 1 — the doc's
   * "the real roster rides on 1" was wrong, checked against the 2.1.222 wire schema before building.
   *
   * A chip rather than timeline lines, because the CLI calls this "a LEVEL signal, unlike the
   * task_started/task_notification edge bookends": it describes what is running NOW, and membership
   * changes repeatedly within one turn. Printed as events it would be a stream of near-duplicate
   * lines; as a chip it answers "what is running?" at a glance and disappears when nothing is.
   */
  // task_ids whose ✕ was clicked and whose roster frame has not yet come back. The roster is a
  // LEVEL signal (REPLACE semantics below), so the row is never removed optimistically — the CLI's
  // next frame is the only truth — but the row must stop offering a second click and say it heard.
  // Reset at the CLI boundary with the roster itself (40-sessions.js).
  let stoppingBgTasks = {};
  function renderBgTasks() {
    const chip = document.getElementById('bgChip'), list = document.getElementById('bgList');
    if (!chip || !list) return;
    if (!bgTasks.length) stoppingBgTasks = {};
    if (!bgTasks.length) {
      chip.hidden = true;
      chip.textContent = '';               // stale "1 task" must not flash if the chip re-shows
      const m = document.getElementById('bgMenu');
      if (m) m.classList.remove('show');   // an open roster must not outlive its last task
      list.textContent = '';
      return;
    }
    chip.hidden = false;
    chip.textContent = bgTasks.length + (bgTasks.length === 1 ? ' task' : ' tasks');
    list.textContent = '';
    bgTasks.forEach(function (t) {
      const row = document.createElement('div');
      row.className = 'popup-item bg-row';
      const body = document.createElement('div'); body.className = 'pi-body';
      const title = document.createElement('div'); title.className = 'pi-title';
      // `description` is the human-readable one; task_id is opaque and only useful as a fallback.
      title.textContent = (t && (t.description || t.task_id)) || 'background task';
      // The CSS ellipsises this to one line (fixed-width popup, hover gutter) — the tooltip is
      // where the full name lives.
      title.title = title.textContent;
      body.appendChild(title);
      const type = t && t.task_type;
      if (type) {
        const d = document.createElement('div'); d.className = 'pi-desc';
        d.textContent = String(type);
        body.appendChild(d);
      }
      row.appendChild(body);
      // Kill (11.3): `stop_task{task_id}` — the conversations-list hover-gutter idiom, no arm/confirm
      // step: a killed task costs one re-ask, a deleted conversation is gone (the reason that list
      // confirms). While the CLI has not answered with a new roster the row reads "stopping".
      if (t && t.task_id) {
        const id = String(t.task_id);
        if (stoppingBgTasks[id]) row.classList.add('stopping');
        const x = document.createElement('button');
        x.className = 'bg-x'; x.title = 'Stop this task'; x.innerHTML = SVG_X;
        x.onclick = function (e) {
          e.stopPropagation();
          if (stoppingBgTasks[id]) return;
          stoppingBgTasks[id] = true; row.classList.add('stopping');
          bridge({ kind: 'stopTask', id: id });
        };
        row.appendChild(x);
      }
      list.appendChild(row);
    });
  }
  let reqTokens = 0;          // output tokens summed across every result turn in one user request
  // uuid of this request's FIRST assistant record. The CLI writes the very same uuid into the
  // transcript, so seeding the summary verb on it is what makes a turn keep its verb across resumes
  // (SessionStore.flushSummary reads the same field). First one wins — later blocks of the same
  // message, and later turns of the request, must not move it.
  let reqSeed = null;
  // Whether the current message rendered via stream deltas. Set at message_start, consumed by the
  // whole-message `assistant` frame; also cleared at `result` so an interrupted stream can't make
  // the NEXT turn's un-streamed frame (a local command's answer) look already-drawn.
  let msgStreamed = false;
  // A message.model "<synthetic>" assistant frame is one of TWO things, and only its RESULT says
  // which. (a) An API-error echo: the error reaches the live wire twice — the synthetic message
  // and then the result's is_error text, identical strings (taped 2026-08-24, haiku[1m] turn).
  // (b) A local built-in's output (/context, /list-agents, …): synthetic message, then a result
  // with NO is_error (measured 2026-09-04, CLI 2.1.260 — on 2.1.233 this was a plain frame, and
  // the tag's arrival painted every /context table red for a while). So the whole-message branch
  // stashes synthetic texts here instead of rendering prose, and the result drains the stash by
  // its own is_error: an error result draws error blocks, dropping ONLY a text identical to its
  // own is_error text (the taped echo — dedupe never swallows a message it hasn't re-shown); a
  // successful result draws the stash as the prose blocks they always were. Fixtures 56 + 70.
  let syntheticEcho = [];
  let toolsById = {};         // tool_use id -> {el, name, io} for OUT boxes; reset by clearLogUI
  // Marks the newest un-superseded pending edit matching (tool, file) as card-owned (4.4).
  // permission_request frames carry no tool_use_id, so tool+file is the only correlation;
  // for parallel same-file edits any pairing is equivalent — each card claims exactly one.
  function supersedeEdit(tool, file) {
    let match = null;
    for (const id in toolsById) {
      const r = toolsById[id];
      if (r.edit && !r.superseded && r.name === tool &&
          (r.edit.file_path || r.edit.path || '') === (file || '')) match = r;
    }
    if (match) match.superseded = true;
  }

  window.onClaudeEvent = function (raw) {
    let ev; try { ev = JSON.parse(raw); } catch (_) { return; }
    // A /effort change rides a real turn. The CLI answers it with a SYNTHETIC assistant
    // whole-message carrying the confirmation ("Set effort level to …") and a result with the
    // SAME text — nothing else on the wire (measured 2026-08-25, CLI 2.1.245, panel flags:
    // init → assistant model:"<synthetic>" → result success, num_turns 0). Draw that
    // confirmation here, like a model change's "Set model to …" line — the NORMAL assistant
    // path would stash a synthetic into syntheticEcho for the error dedupe and draw nothing —
    // and keep swallowing the rest (echo, streams, and the result: no turn summary, matching
    // the model chip's silence). Cleared on the turn's result. (setBusy was never called for
    // it — the effort click uses a raw bridge, not sendTurn — so no spinner or stuck busy.)
    if (effortMuted) {
      if (ev.type === 'assistant' && !ev.parent_tool_use_id) {
        ((ev.message || {}).content || []).forEach(function (b) {
          if (b && b.type === 'text' && b.text) { const k = track(el('blk', '')); k.innerHTML = renderMd(b.text); foldCode(k); }
        });
        stampMessage(ev.uuid);
        return;
      }
      if (ev.type === 'stream_event' || ev.type === 'user') return;
      if (ev.type === 'result') { effortMuted = false; return; }
    }
    switch (ev.type) {
      case 'stream_event':       return onStream(ev.event || {});
      // Rendering is driven entirely by stream_event deltas, so the whole-message `assistant` record
      // is only here for its uuid — the one field that ties a live summary to its replayed twin.
      case 'assistant':
        // Same guard as the `user` case below: a sub-agent's own assistant messages must not be
        // stamped as this conversation's, or a retraction aimed at the parent could evict a child's
        // blocks (and reqSeed would take the child's uuid, mis-seeding the completion summary).
        if (ev.parent_tool_use_id) return;
        // An auth failure is a DEAD END here: the plugin has no login flow by design
        // (CLAUDE.md § Philosophy), so the message has to carry the route out. VS Code hangs its
        // own login off this exact field — `if (e.error === "authentication_failed") showLogin()`
        // in webview/index.js — and we point at the terminal instead.
        //
        // Verified against 2.1.222 (this branch shipped BLIND and two of its three assumptions were
        // wrong). `error` is the CLI's ten-code enum, serialized verbatim onto EVERY assistant
        // frame: `yield {type:"assistant", …, timestamp:n.timestamp, error:n.error, …}`. There is no
        // `subtype` on an assistant frame — the old check for one was a guess and is gone.
        //
        // This line is ROUTING, not diagnosis, which is why it no longer opens with "Not signed in":
        // `authentication_failed` also covers expired AWS/GCP credentials and managed keys, where
        // signing in is the wrong instruction. The CLI's own specific explanation still lands right
        // after this, as the `result` error block, so restating the cause here can only contradict it.
        if (AUTH_BLOCKED[ev.error]) {
          setBusy(false);
          return void statusLine(AUTH_BLOCKED[ev.error], SVG_ALERT, 'status err');
        }
        if (reqSeed == null && ev.uuid) reqSeed = ev.uuid;
        // A LOCAL built-in (/context, /recap, /list-agents, …) answers as a bare whole-message
        // assistant frame with ZERO stream_events (measured 2026-08-15, CLI 2.1.233) — the one
        // live shape that must render from the whole message, because there are no deltas to
        // drive it. msgStreamed says whether deltas already drew this message; without this
        // branch /context "Puttered for 1s" and showed nothing. Text blocks only: tool_use and
        // thinking have their own streamed machinery and never arrive un-streamed.
        // The flag is NEVER cleared here, and deliberately not at message_stop either. The CLI
        // emits an `assistant` frame per CONTENT BLOCK, not per message (taped live 2026-08-15),
        // and those frames straddle message_stop in both directions — so any mid-turn reset
        // re-draws text the deltas already rendered. That is what made every message after the
        // first appear twice in a /security-review run, both copies carrying one uuid because
        // stampMessage stamps all pending blocks. It is a TURN-level fact: set at message_start,
        // cleared only at result / sendTurn / clearLogUI. A local command's turn never streams at
        // all, which is exactly what leaves it false and lets its whole message draw itself.
        if (!msgStreamed) {
          ((ev.message || {}).content || []).forEach(function (b) {
            if (b && b.type === 'text' && b.text) {
              // the CLI's API-error echo — stash for the result, never draw as prose (see
              // syntheticEcho's declaration); tool_use content in synthetic frames is untouched
              if ((ev.message || {}).model === '<synthetic>') { syntheticEcho.push(b.text); return; }
              const k = track(el('blk', '')); k.innerHTML = renderMd(b.text); foldCode(k);
            }
          });
        }
        // The uuid the transcript will carry — stamp it on the blocks this message just drew, so a
        // later retraction can find them.
        stampMessage(ev.uuid);
        // The SECOND retraction lane: an assistant message can supersede earlier ones directly,
        // without a refusal event. Same eviction, different door.
        evictUuids(ev.supersedes, 'superseded');
        return;
      // A sub-agent's OWN messages arrive here, tagged with the tool_use that launched them. They
      // are deliberately ignored: their tool calls and results belong to the sub-agent, not to this
      // conversation, and the progress line (taskLine) is how that work is reported instead.
      //
      // Today this guard is belt-and-braces — rendering is driven entirely by `stream_event`
      // deltas, and deltas NEVER carry a parent (probed 2.1.222: 28 stream events from a real
      // sub-agent run, zero with `parent_tool_use_id`), so child work is already excluded
      // structurally. The guard makes that intentional rather than a happy accident of the
      // architecture: the moment anything renders from whole messages, child text would otherwise
      // interleave into the main transcript undistinguished.
      case 'user':
        if (ev.parent_tool_use_id) return;
        return onUserEvent(ev);
      case 'result':             return onResult(ev);
      case 'permission_request': return renderPermission(ev);
      case 'files':              files = ev.items || []; return;
      case '__mention':          insertMentions(ev.items || []); return;   // 6.5: IDE context menu → composer
      case '__commands':         slashCommands = markCustom(ev.items || []); return;
      case '__customModels':
        customModels = ev.items || [];   // pushed before __models so a restored custom selection resolves
        renderModels();
        return;
      case '__models':
        models = ev.items || [];
        currentModel = ev.selected || (models[0] && models[0].value) || null;
        oneMFromCli = null;   // fresh roster/selection: the 1M switch is tag-derived until a result speaks
        { const cm = allModels().find(function (m) { return m.value === currentModel; });
          const w = (cm && !cm.custom) ? windowOf(cm) : (/\[1m\]/i.test(currentModel || '') ? CTX_1M : 0);
          if (w) { ctxWindowFromCli = w; renderContext(); }
          if (cm) { setModelChip(chipName(cm), cm.value); }
          else if (currentModel) { setModelChip(prettyModel(currentModel), currentModel); } }
        renderModels(); syncModelFooter();
        return;
      case '__fastMode':
        // CLI truth from the initialize payload; a pref-only frame (CLI not started yet) shows
        // the persisted preference until the real state arrives. Reconciled again on every result.
        fastModeState = ev.state || (ev.pref ? 'on' : 'off');
        fastModeReason = ev.reason || '';
        syncModelFooter();
        return;
      case '__thinking':
        thinkingOn = ev.on !== false;
        syncModelFooter();
        return;
      case 'system':
        if (typeof ev.permissionMode === 'string') applyCliMode(ev.permissionMode);
        if (ev.subtype === 'init') {
          // The cwd the CLI is actually resolving paths against — the same value ChatPanel pushed
          // as __project, but from the authority. Only ever arrives at the first turn.
          if (ev.cwd) setProjectRoot(ev.cwd);
          if (ev.slash_commands && !slashCommands.length) {
            slashCommands = ev.slash_commands.map(function (n) { return { name: n }; });
          }
          // Item 13a. `mcp_servers: [{name, status}]` rides on the init EVENT — NOT on the
          // initialize control response, which the audit assumed and which was probed against
          // 2.1.222 and does not carry it (see docs/client-parity.md § 13a). The practical
          // consequence of that correction: this can only speak from the first turn onward,
          // because that is when the CLI emits `system/init`.
          mcpNotice(ev.mcp_servers);
        } else if (ev.subtype === 'model_fallback') {
          // WATCH (checklist 9.7, deferred 2026-08-29): the CLI switched the turn to its fallback
          // model — `{trigger: model_not_found|permission_denied|overloaded|server_error|
          // last_resort|model_blocked, original_model, fallback_model, content}` (schema read from
          // the 2.1.251 binary; NEVER observed on this wire). The panel declares no
          // supportedDialogKinds, so the overage consent never reaches it and the chip would keep
          // the old name. No render until one is seen: the first lands here for CDP
          // (window.__modelFallbackSeen) and once in the console, exactly like __ambientSeen.
          if (!window.__modelFallbackSeen) {
            window.__modelFallbackSeen = ev;
            console.warn('first model_fallback frame (9.7 watch):', JSON.stringify(ev));
          }
        } else if (ev.subtype === 'thinking_tokens') {
          // The CLI's own count, replacing our chars/4 guess for as long as it keeps sending them.
          const n = ev.estimated_tokens;
          if (typeof n === 'number' && n >= 0) { thinkTokReal = n; paintThinkTokens(); }
        } else if (ev.subtype === 'api_error' || ev.subtype === 'api_retry') {
          // The CLI is retrying a failed request. Without this the panel just stalls, and there is
          // no terminal to check — this session's CLI is ours. Two spellings of one fact (item 32):
          // the LIVE wire sends `api_retry` with {attempt, max_retries, error_status,
          // error:"<code string>"}; transcripts persist the internal twin `api_error` with
          // {retryAttempt, maxRetries, error:{message, formatted}}. Accept both, or the live
          // banner never fires — probed 2026-08-06 with a real 401 storm.
          //
          // Two consequences of that both-spellings rule (9.1, read verbatim from the 2.1.226
          // binary): the string `error` is a five-code enum, not prose — resolve it through
          // RETRY_REASONS or a network storm reads "unknown — retrying" — and the stream-json
          // translator yields the raw api_error AND its api_retry twin for the SAME attempt
          // (the raw frame falls through the chain's `else yield` before the twin is emitted),
          // so a repeat of the attempt just reported is the second half of one retry, not a new
          // one. Twins arrive raw-first, so where the shapes differ the richer text is the one
          // that stays on screen.
          const e = ev.error || {};
          const text = (typeof ev.error === 'string' && ev.error)
            ? (RETRY_REASONS[ev.error] || ev.error) + (ev.error_status ? ' (' + ev.error_status + ')' : '')
            : (e.formatted || e.message || 'API error');
          const at = ev.retryAttempt || ev.attempt, max = ev.maxRetries || ev.max_retries;
          const key = at && max ? at + '/' + max : null;
          if (!key || key !== retrySeen) {
            retrySeen = key;
            statusLine(text + (key ? ' — retrying (' + key + ')' : ''), SVG_ALERT, 'status err');
          }
        } else if (ev.subtype === 'model_refusal_fallback') {
          onRefusalFallback(ev);
        } else if (ev.subtype === 'task_started' || ev.subtype === 'task_progress') {
          taskLine(ev, false);
        } else if (ev.subtype === 'task_notification') {
          taskLine(ev, true);
        } else if (ev.subtype === 'task_updated') {
          // Carries only {task_id, patch:{status,…}} — no tool_use_id and no usage — so it can
          // finalize a line that already exists but can never create one.
          const p = ev.patch || {};
          const st = taskProg[ev.task_id];
          if (p.status && p.status !== 'in_progress' && st) {
            if (st.el) st.el.classList.remove('run');
            // and the Agent tool line above it — this frame has no tool_use_id, which is why
            // taskLine stashes the tool on `st`. Same named-states rule as taskLine's done branch.
            if (st.tool && st.tool.el) {
              st.tool.el.classList.remove('run');
              if (p.status === 'failed' || p.status === 'killed') st.tool.el.classList.add('fail');
            }
          }
        } else if (ev.subtype === 'informational') {
          infoLine(ev.content, ev.level, ev.tool_use_id || ev.toolUseId);
          // prevent_continuation means the turn stops HERE. A `result` does still follow (probed),
          // so this is belt-and-braces — but a spinner left running after the CLI has given up is
          // the worst of the failure modes, so it is not left to that.
          if (ev.prevent_continuation || ev.preventContinuation) setBusy(false);
        } else if (ev.subtype === 'model_refusal_no_fallback') {
          onRefusalNoFallback(ev);
        } else if (ev.subtype === 'compact_boundary') {
          // Read defensively: whether the live stream carries this subtype is unprobed (toolUseResult
          // turned out not to be), so if it never arrives replay still draws the marker and nothing
          // here breaks. The gauge reset is the part that MUST happen live — the user just asked for
          // the context to shrink and the chip was still showing the pre-compact figure until the
          // next turn's message_start corrected it. renderContext treats 0 as "clear the chip
          // entirely", and the chip's own click handler already no-ops while ctxUsed is 0.
          const cm = ev.compact_metadata || ev.compactMetadata || {};
          compactBlock('', cm.trigger, cm.pre_tokens || cm.preTokens || 0);
          ctxUsed = 0; renderContext();
        } else if (ev.subtype === 'background_tasks_changed') {
          // The roster mixes two kinds of task, and only one suspends the turn. A background
          // SUBAGENT does: the CLI emits an intermediate `result` and resumes with the real one
          // when the task reports back — `pendingBgTasks` is what lets onResult tell that suspend
          // from the request's true end. A background SHELL (task_type "local_bash") does NOT:
          // measured 2026-08-12 (CLI 2.1.228 — its busy set deliberately excludes local_bash), the
          // CLI goes idle and the `result` that arrives IS the true end. Counting shells here
          // parked busy=true for the life of the process: stuck Stop, no summary, queued messages
          // never draining.
          //
          // A deny-list on purpose, not the CLI's allow-list: an UNKNOWN task_type must count as
          // suspending. Finalizing a live request early corrupts its accounting and drains the
          // queue into it (the harm sendTurn's busy guard exists to prevent); failing to
          // un-suspend is only a visible spinner that heals when the roster empties.
          //
          // REPLACE semantics, in the schema's own words: "Every live background task after the
          // change. REPLACE semantics: swap your set for this payload." So this assigns — it must
          // never merge, or a finished task would stay on the roster forever. It is a LEVEL signal,
          // not an edge one, which is why the roster is a chip that reflects the present rather
          // than a timeline entry that accumulates.
          // `ambient` (2.1.250 schema): "housekeeping tasks the CLI does not surface as user
          // work … hosts should exclude them from activity indicators" — so they are neither
          // on the roster nor in the suspend count. Not yet seen on a live frame; the filter
          // is the schema's own instruction and costs nothing when the field is absent.
          const rawTasks = Array.isArray(ev.tasks) ? ev.tasks : [];
          bgTasks = rawTasks.filter(function (t) { return !(t && t.ambient === true); });
          // Unmeasured as of 2026-08-29 (no live frame has carried the field): keep the FIRST
          // ambient task verbatim so the fact gets measured the day it appears — readable over
          // CDP as window.__ambientSeen, and once in the console (tools/cdp.py --console).
          if (!window.__ambientSeen) {
            const a = rawTasks.filter(function (t) { return t && t.ambient === true; })[0];
            if (a) { window.__ambientSeen = a; console.warn('first ambient background task:', JSON.stringify(a)); }
          }
          pendingBgTasks = bgTasks.filter(function (t) {
            return !t || t.task_type !== 'local_bash';
          }).length;
          // Forget a "stopping" id once the roster no longer lists it (hand-test 2026-08-28:
          // a killed shell's id lingered until the set emptied — harmless, but a set that only
          // grows is a leak by another name).
          const live = {};
          bgTasks.forEach(function (t) { if (t && t.task_id) live[String(t.task_id)] = true; });
          Object.keys(stoppingBgTasks).forEach(function (id) { if (!live[id]) delete stoppingBgTasks[id]; });
          renderBgTasks();
        } else if (ev.subtype === 'status') {
          // Item 35. `permissionMode` on this frame is already read above, like every system event.
          // `status:"requesting"` fires ~3x per ordinary turn (probed 2026-08-06) — the routine
          // case, and it must stay silent. While the CLI compacts, the working verb says so; any
          // other status frame releases the pin. Live-only: zero status records are ever persisted.
          setWorkVerb(ev.status === 'compacting' ? 'Compacting…' : null);
          const cr = ev.compact_result || ev.compactResult;
          if (cr === 'failed') {
            // The user just asked for the context to shrink — without this line a failed
            // compaction is a silent no-op (we enabled /compact, so this state is ours to own).
            const why = ev.compact_error || ev.compactError;
            statusLine('Compaction failed' + (why ? ' — ' + why : ''), SVG_ALERT, 'status err');
          }
        } else if (ev.subtype === 'commands_changed') {
          // Item 36. The schema's own instruction: "Clients should REPLACE their cached command
          // list with this payload" — skills discovered mid-session change the roster, and our
          // cache otherwise dates from the initialize response. REPLACE, not merge, and
          // unconditionally — unlike the init seed, which only fills an empty list. The menu's
          // allowlist (cmdKind) filters at render time, so an updated roster passes the same gate.
          if (Array.isArray(ev.commands)) {
            slashCommands = markCustom(ev.commands.map(function (c) {
              return (typeof c === 'string') ? { name: c } : c;
            }));
          }
        }
        return;
      case '__tasks': {
        // The checklist sits under the Task* tool line that asked for it — same layout as
        // replay, where each labelled line (TaskUpdate completed / in_progress) carries its
        // own snapshot. Without the relocation every snapshot appended at the turn's end, so
        // a parallel-call turn stacked detached duplicate lists there. todoList's el() has
        // already appended the box; .after() MOVES it. maybeScroll keeps the pin honest
        // (5.14): this path renders without any other append to trigger a scroll.
        const box = todoList(ev.items || []);
        const r = ev.id && toolsById[ev.id];
        if (r) (r.io || r.el).after(box);
        maybeScroll(); return;
      }
      case '__lineStart': {      // answer to the pre-apply gutter request (4.4); may be late or absent
        const r = toolsById[ev.id];
        if (r && typeof ev.line === 'number') r.lineStart = ev.line;
        return;
      }
      // The editor diff answered an edit permission first (dual-surface, first answer wins):
      // paint the card decided WITHOUT sending a second response — Kotlin already answered the
      // CLI, and its arbiter would drop a duplicate anyway. Unknown id = the card answered
      // first (or a stale frame): nothing to do.
      // `tweaked` (3.5): the user edited the diff pane before accepting — oldStr/newStr are the
      // edit that actually ran, so the card redraws its diff and says so.
      // Files this turn changed (3.6): Kotlin's TurnChanges settled at `result`, so the frame
      // lands right after the turn's ✻ summary line. Review → the IDE opens a diff chain of the
      // turn's (baseline, final) pairs — live turns only; replay draws the same line from the
      // transcript without the action (see 55-replay's done case).
      case '__files_changed': return filesLine(ev.files || [], ev.turn);
      case '__perm_answered': {
        const fn = permCards[ev.id];
        if (fn) fn(ev.allow === true || ev.allow === 'true', ev.tweaked ? { oldStr: ev.oldStr, newStr: ev.newStr } : null);
        return;
      }
      // A usage limit was hit or is close. No terminal to check — the terminal is not running this
      // session. Shape read out of the CLI binary and the VS Code validator (2.1.222) after a first
      // guess fired a FALSE ALARM in a live session: the payload is `rate_limit_info`, not
      // `rate_limit`, and crucially `status:"allowed"` is the ROUTINE case that must say NOTHING.
      // Warning on every event told the user they were near a limit when they were not.
      case 'rate_limit_event': {
        // Both spellings: the CLI's emission sites are inconsistent (originalModel vs
        // original_model bit us on the refusal fallback), and an unknown shape yields no `status`,
        // which falls through to SILENCE below — the safe direction for a claim about the user's
        // own account. Announcing a limit that is not there is worse than announcing nothing.
        const r = ev.rate_limit_info || ev.rateLimitInfo || {};
        if (!r.status || r.status === 'allowed') { rateLimitKey = null; return; }
        // VS Code keys the warning by status+type and refuses to repeat it; we append to a log, so
        // without the same guard an unchanged limit would restate itself on every turn.
        const key = r.status + ':' + (r.rateLimitType || '');
        if (key === rateLimitKey) return;
        rateLimitKey = key;
        const label = LIMIT_LABELS[r.rateLimitType] || 'usage limit';
        const when = r.resetsAt ? ' · resets ' + fmtResets(r.resetsAt) : '';
        const credits = r.rateLimitType === 'seven_day_overage_included'
          ? ' · continue with usage credits' : '';
        let msg;
        if (r.status === 'rejected') {
          if (!r.rateLimitType) return;          // nothing specific to say; stay quiet
          msg = "You've hit your " + label + when;
        } else {
          const pct = r.utilization ? Math.floor(r.utilization * 100) : 0;
          msg = pct ? "You've used " + pct + '% of your ' + label + when + credits
                    : 'Approaching ' + label + when + credits;
        }
        return void statusLine(msg, SVG_ALERT, 'status err');
      }
      case 'sessions':           return renderHistory(ev.items || [], ev.current);
      case '__transcript':       setContext(ev.context || 0); return renderTranscript(ev.items || [], ev.more || 0);
      case '__transcript_more':  return renderEarlier(ev.items || [], ev.more || 0);
      case '__clear':            sideReset(); return clearLogUI();
      case '__side':             return sideAnswer(ev);   // side-question answer (8.11)
      case '__title':            return setTitle(ev.text);
      case '__mode':             return applyCliMode(ev.mode);   // persisted mode, at startup
      case '__project':          return setProjectRoot(ev.root); // shortens tool-line paths
      case '__ctl_error':        return errorBlock(ev.error || 'control request failed');
      case '__model_rejected': {
        // The CLI refused set_model (a PreModelSwitch hook, 2.1.251 — checklist 9.11). The chip
        // flipped optimistically; put it back to what the CLI kept. `previous` absent = a refused
        // restart re-apply: the CLI is on its default, shown as the roster head (the __models rule).
        showModel(ev.previous || (models[0] && models[0].value) || '');
        return errorBlock(ev.error || 'model switch refused');
      }
      case '__exit': {
        setBusy(false);
        const line = statusLine('claude process exited (' + ev.code + ')', SVG_ALERT, 'status err');
        // WHY it died, when it said anything. Folded, because a crash dump is long and the first
        // lines are rarely the useful ones — but present, which it was not before.
        if (ev.stderr) {
          const box = ioBox([['ERR', ev.stderr]]);
          line.after(box);
        }
        // `early` = the process died before producing a single protocol frame (set host-side) —
        // an argument-parse rejection, which in practice is an old CLI meeting a newer flag
        // vocabulary (a pre-2.1.220 install rejects `--permission-mode manual`). Advice, not a
        // second error, so the plain muted dress; a mid-session crash never carries the flag.
        if (ev.early) {
          statusLine('Your Claude CLI may be out of date — run `claude update` in a terminal.', SVG_ALERT);
        }
        return;
      }
    }
  };

  function finishThinking() {
    if (!curThink) return;
    if (thinkTimer) { clearInterval(thinkTimer); thinkTimer = null; }
    const secs = Math.max(1, Math.round((Date.now() - thinkStart) / 1000));
    curThink.replaceWith(thinkBlock(curThinkRaw, secs));
    curThink = null; curThinkRaw = ''; thinkTok = null; thinkTokReal = null;
  }

