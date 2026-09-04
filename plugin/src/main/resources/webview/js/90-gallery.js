  /* ---------- dev gallery: renders every transient UI state in the live webview ----------
     Trigger with window.__gallery() from DevTools or over CDP. Drives the real renderer
     (renderPermission / statusLine / addRetryLine / el) so it exercises production code paths,
     not static copies — the one exception is the working line, which is drawn statically
     because a live one is owned/torn-down by the busy watchdog. */
  function gallery() {
    clearLogUI();
    addUserMessage('Dev gallery — show every UI state, starting from @src/components/App.tsx.', []);

    // assistant text block (markdown + code)
    const b = el('blk', '');
    b.innerHTML = renderMd('An assistant **text block** with `inline code`, a [link](#), a list:\n\n' +
      '- first item\n- second item\n\n```js\nfunction add(a, b) { return a + b; }\n```\n\n' +
      '| Page | URL | Template | Hits |\n|---|---|---|--:|\n' +
      '| Home | `/` | `front-page.php` | 1,204 |\n' +
      '| Shop | `/shop/` | `woocommerce/archive-product.php` | 318 |\n' +
      '| 404 | `/no-such-page/` | `404.php` | 7 |');

    // collapsed thinking
    (curTurn || log).appendChild(thinkBlock('Weighing a couple of approaches before writing the diff…', 4));

    // tool line — success (with path)
    const t1 = el('tool-line', '');
    t1.innerHTML = '<b>Read</b><span class="t-desc"></span>';
    fillPath(t1.querySelector('.t-desc'), 'src/components/App.tsx');
    // …and the state the clamp exists for: a path long enough to wrap the panel three times. It is
    // drawn under the gallery's own root so the project-relative half is visible too.
    const t1b = el('tool-line', '');
    t1b.innerHTML = '<b>Edit</b><span class="t-desc"></span>';
    // Built from the LIVE root so the project-relative half is exercised, and with a tail that adds
    // no segment the root might already end in — appending a fixed "/plugin/…" produced
    // "plugin/plugin/…" in a sandbox whose project IS the plugin directory.
    fillPath(t1b.querySelector('.t-desc'),
      (projectRoot || '/home/you/project') +
      '/src/test/kotlin/io/github/amitsidhpura/claudebrains/session/SessionStoreTest.kt');

    // tool line — Bash with IN/OUT
    const t2 = el('tool-line', '');
    t2.innerHTML = '<b>Bash</b><span class="t-desc">Run the test suite</span>';
    t2.after(ioBox([['IN', 'npm test'], ['OUT', 'PASS  2 passed, 0 failed']]));

    // tool line — failure (red dot)
    const t3 = el('tool-line', ''); t3.classList.add('fail');
    t3.innerHTML = '<b>Bash</b><span class="t-desc">Lint the project</span>';

    // truncation markers — every shape, built through cutInfo() so the gallery shows what the real
    // rule produces rather than hand-written numbers. The marker must stay visible while .io-v is
    // folded; that is the whole point of it being a sibling.
    const t4 = el('tool-line', '');
    t4.innerHTML = '<b>Bash</b><span class="t-desc">List every file (truncated both ways)</span>';
    const longCmd = 'find / -type f -name "*.log" ' + '-o -name "*.tmp" '.repeat(400);
    const longOut = Array.from({ length: 900 }, function (_, i) { return '/var/log/app-' + i + '.log'; }).join('\n');
    const oneLine = '{"rows":[' + '{"id":1,"v":"x"},'.repeat(400) + ']}';   // no newlines → size only
    t4.after(ioBox([
      ['IN', cutInfo(longCmd, LIM.cmdMax).shown, cutInfo(longCmd, LIM.cmdMax)],
      ['OUT', cutInfo(longOut, LIM.outMax).shown, cutInfo(longOut, LIM.outMax)],
    ]));
    // refusal fallback — a safety classifier flagged the exchange, so the CLI retried on another
    // model and withdrew what it had sent. Drawn through the real handler; the retracted uuid names
    // nothing here, so this shows the notice without the eviction (which needs stamped blocks).
    onRefusalFallback({
      original_model: 'claude-opus-5', fallback_model: 'claude-sonnet-5',
      content: "Safeguards flagged this message. This sometimes happens with safe, normal conversations.",
      retracted_message_uuids: [],
    });
    // The LOCAL-scope variant: a subagent or /btw fell back, so that one response came from another
    // model but the session never switched. Deliberately drawn right after the session-scope one —
    // the two must not read alike, and the chip must move for the first and hold for this.
    onRefusalFallback({
      original_model: 'claude-opus-5', fallback_model: 'claude-sonnet-5', scope: 'local',
      content: 'Safeguards flagged a side question.',
    });
    // The refusal with NO retry: nothing is coming, so nothing follows this line.
    onRefusalNoFallback({ original_model: 'claude-opus-5', content: '' });

    // CLI notices (item 22), one per level so the prominence ladder is visible side by side. The
    // warning is the real text a UserPromptSubmit hook produces when it denies a prompt — that turn
    // emits this and nothing else, so without it the panel just sits there.
    infoLine('UserPromptSubmit operation blocked by hook:\n[./guard.sh]: commit messages must ' +
      'reference an issue\n\nOriginal prompt: commit this', 'warning');
    infoLine('Consider running the test suite before committing.', 'suggestion');
    infoLine('Settings reloaded from ~/.claude/settings.json.', 'notice');

    // in-flight states (2026-08-13): the gutter dot is white and breathing while its work has not
    // come back, and takes its verdict colour when it does. Every OTHER tool line in this gallery is
    // finished, so without these the index cannot show the contrast the state exists to draw — a
    // settled GREEN line sits directly under the running white one for exactly that reason. These
    // keep pulsing until the next turn ends (setBusy(false) sweeps .run) or the panel is refreshed,
    // which is right for a demo and is what the gallery's other live-only blocks do too.
    { const tr = track(toolLine('Bash'));
      tr.classList.add('run');
      tr.querySelector('.t-desc').textContent = './gradlew test — still in flight';
      const tf = track(toolLine('Bash'));
      tf.querySelector('.t-desc').textContent = './gradlew compileKotlin — came back'; }
    { const tl = track(el('think-live', ''));
      tl.innerHTML = '<div class="th"><span class="shimmer">Thinking…</span>' +
        '<span class="th-el-wrap"> · <span class="th-el">3s</span></span>' +
        '<span class="tok"> · 132 tokens</span></div>'; }

    // stale-edit caveat (item 11) — the file changed on disk between the read and the write. The
    // card still says Applied, because it did apply; the note is the part nothing else says.
    { const te = track(toolLine('Edit'));
      fillPath(te.querySelector('.t-desc'), '/home/you/project/timestamp.txt');
      const n = noteLine('the file had been modified on disk since you last read it — the edit ' +
        'applied cleanly, but the file contains other changes not in your context.');
      if (n) te.after(n); }

    // tool-returned image (item 8) — a Read on a PNG or a Playwright screenshot. THREE states, and
    // all three are needed to review the sizing: over the height cap, under it, and bytes dropped.
    // The 2x2 sample is not a curiosity — it is the state that was broken, rendering full-panel-width
    // by 320px because a column flex container stretches by default (docs/limits.md).
    { const ti = track(toolLine('Read'));
      fillPath(ti.querySelector('.t-desc'), '/home/you/project/screenshot.png');
      const box = toolImages([
        { media_type: 'image/svg+xml', name: 'screenshot.png',
          data: 'PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI5MDAiIGhlaWdodD0iNTIwIj48cmVjdCB3aWR0aD0iOTAwIiBoZWlnaHQ9IjUyMCIgZmlsbD0iIzFlMjczMyIvPjxyZWN0IHg9IjQwIiB5PSI0MCIgd2lkdGg9IjgyMCIgaGVpZ2h0PSI1NiIgcng9IjgiIGZpbGw9IiMyZjNkNGYiLz48cmVjdCB4PSI0MCIgeT0iMTMwIiB3aWR0aD0iNTAwIiBoZWlnaHQ9IjIyIiByeD0iNiIgZmlsbD0iIzNiNGE1ZSIvPjxyZWN0IHg9IjQwIiB5PSIxNzgiIHdpZHRoPSI2MjAiIGhlaWdodD0iMjIiIHJ4PSI2IiBmaWxsPSIjM2I0YTVlIi8+PHJlY3QgeD0iNDAiIHk9IjIyNiIgd2lkdGg9IjM2MCIgaGVpZ2h0PSIyMiIgcng9IjYiIGZpbGw9IiMzYjRhNWUiLz48cmVjdCB4PSI1OTYiIHk9IjMwMCIgd2lkdGg9IjI2NCIgaGVpZ2h0PSIxODAiIHJ4PSIxMCIgZmlsbD0iI2Q5NjEyYyIvPjwvc3ZnPg==',
          dimensions: { displayWidth: 900, displayHeight: 520 } },
        { media_type: 'image/png', name: 'favicon.png',
          data: 'iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAAFklEQVR4nGP8z4AATAxQxhArAgQAAP//G6QCFVmA6TsAAAAASUVORK5CYII=',
          dimensions: { displayWidth: 2, displayHeight: 2 } }]);
      if (box) ti.after(box);
      const ti2 = track(toolLine('Read'));
      fillPath(ti2.querySelector('.t-desc'), '/home/you/project/huge-capture.png');
      const box2 = toolImages([{ kind: 'image', name: 'huge-capture.png' }]);
      if (box2) ti2.after(box2); }

    // queued messages (item 24) — the composer's pending list, drawn through the real renderer.
    queue = [{ text: 'also add a test for the empty case', images: [] },
             { text: 'and update the changelog', images: [{ kind: 'image' }] }];
    renderQueue();

    // sub-agent progress (item 1), driven through the real handler with the REAL frames a
    // synchronous Explore sub-agent emitted. Live-only by nature: none of these persist.
    { const tg2 = track(toolLine('Agent'));
      tg2.querySelector('.t-desc').textContent = 'Read second line of words.txt';
      toolsById['tu-agent'] = { el: tg2, name: 'Agent', io: null };
      taskProg = {};
      taskLine({ task_id: 'tk1', tool_use_id: 'tu-agent', description: 'Reading words.txt',
        subagent_type: 'Explore', usage: { total_tokens: 8063, tool_uses: 1 } }, false); }

    // background-task roster (item 4) — chip + popup, driven through the real renderer so the
    // gallery cannot drift from it. Click the chip to open the roster.
    bgTasks = [
      { task_id: 'bg_01', task_type: 'Explore', description: 'Audit chat.css for optimization' },
      { task_id: 'bg_02', task_type: 'general-purpose', description: 'Run the full test suite' },
    ];
    renderBgTasks();

    // sub-agent brief (item 3) — the description names the errand, the IN box is the errand itself.
    // Same box and same cut marker as a Bash command, because it is the same idea: what it was ASKED.
    { const ta = track(toolLine('Agent'));
      ta.querySelector('.t-desc').textContent = 'Find flagged API usages';
      ta.after(ioBox([['IN', 'Search the repo for deprecated calls and report each one with a ' +
        'file:line reference. Do not change anything — this is a survey, not a fix.']])); }

    // server-side web search (item 12) — the API ran it, so there is no tool_result user event and
    // no IN box; the query rides on the tool line and the results come back as their own block.
    { const ts = track(toolLine('web_search'));
      ts.querySelector('.t-desc').textContent = 'kotlin coroutines structured concurrency';
      toolsById['srv-1'] = { el: ts, name: 'web_search', json: '' };
      serverToolResult({ type: 'web_search_tool_result', tool_use_id: 'srv-1', content: [
        { title: 'Coroutines basics | Kotlin', url: 'https://kotlinlang.org/docs/coroutines-basics.html' },
        { title: 'Structured concurrency', url: 'https://kotlinlang.org/docs/coroutines-basics.html#structured-concurrency' },
      ] }); }
    // and its failure, which marks the line red — content is an OBJECT here, not an array
    { const tse = track(toolLine('web_search'));
      tse.querySelector('.t-desc').textContent = 'something unsearchable';
      toolsById['srv-2'] = { el: tse, name: 'web_search', json: '' };
      serverToolResult({ type: 'web_search_tool_result', tool_use_id: 'srv-2',
        content: { error_code: 'max_uses_exceeded' } }); }

    // todo checklist — TodoWrite sends the whole list every call, so the newest call IS the state.
    // The in-flight item wears activeForm ("Wiring…"), which is what it is for.
    const tt = el('tool-line', '');
    tt.innerHTML = '<b>Update Todos</b>';
    todoList([
      { content: 'Add the canonical cut rule', status: 'completed', activeForm: 'Adding the cut rule' },
      { content: 'Emit cut metadata from the parser', status: 'completed', activeForm: 'Emitting metadata' },
      { content: 'Wire the marker into the webview', status: 'in_progress', activeForm: 'Wiring the marker into the webview' },
      { content: 'Tests, probe and docs', status: 'pending', activeForm: 'Writing tests' },
    ]);

    // compaction boundary — the summary is the CLI's, folded, and NOT a user message. Real ones run
    // to 25-41k characters; this stub is long enough to exercise the fold.
    compactBlock(
      'This session is being continued from a previous conversation that ran out of context. ' +
      'The summary below covers the earlier portion of the conversation.\n\n' +
      '1. Primary request: port the permission card to the new split-button design.\n' +
      '2. Files changed: chat.html, chat.css, SessionStore.kt.\n' +
      '3. Open item: the caret button needs the hover state while its menu is open.',
      'manual', 375909);

    // result NOTES — what the CLI said about the result, not what a cap removed. The grep case is
    // the one that matters: without the note the box says only "(Bash completed with no output)"
    // and the reason is gone. `interrupted` has never been seen in a real transcript.
    const tn = el('tool-line', '');
    tn.innerHTML = '<b>Bash</b><span class="t-desc">Search for the old class name</span>';
    tn.after(ioBox([
      ['IN', 'grep -rn "ClaudeCodeSyncroze" src/'],
      ['OUT', '(Bash completed with no output)', null, null, null, 'No matches found'],
    ]));
    const ti = el('tool-line', ''); ti.classList.add('fail');
    ti.innerHTML = '<b>Bash</b><span class="t-desc">Wait for the port to open</span>';
    ti.after(ioBox([['OUT', 'waiting for 127.0.0.1:8080…', null, null, null, null, true]]));

    const t5 = el('tool-line', '');
    t5.innerHTML = '<b>Bash</b><span class="t-desc">Dump one long line, and a spilled result</span>';
    t5.after(ioBox([
      ['OUT', cutInfo(oneLine, LIM.outMax).shown, cutInfo(oneLine, LIM.outMax)],
      // the CLI spilled the whole result to a file: true size + a clickable link
      ['OUT', cutInfo(longOut, LIM.outMax).shown, cutInfo(longOut, LIM.outMax), 772297,
        '/home/dev/.claude/projects/demo/tool-results/bdsvs72t4.txt'],
    ]));

    // permission card — Edit diff, with the CLI's accept-all-edits suggestion
    renderPermission({ tool: 'Edit', id: 'gallery-edit', lineStart: 42, input: JSON.stringify({
      file_path: 'src/util/math.ts', old_string: 'export const rate = 0.1;', new_string: 'export const rate = 0.15;' }),
      suggestions: JSON.stringify([{ type: 'setMode', mode: 'acceptEdits', destination: 'session' }]) });

    // permission card — Bash with allow-rule + allow-directory suggestions (Kotlin strips these
    // on sandbox escalations, where no grant stops the re-ask; this is the non-sandbox shape).
    // Two addRules entries (one per sub-command, as the CLI sends for compound commands) merge
    // into a single "Always allow" SPLIT button: the main half grants both, the caret lists them
    // individually. This is the case that exercises the split — one entry renders a plain button.
    renderPermission({ tool: 'Bash', id: 'gallery-bash', input: JSON.stringify({
      command: 'mkdir -p /tmp/report && touch /tmp/report/out.txt', description: 'Create the report file' }),
      suggestions: JSON.stringify([
        { type: 'addRules', rules: [{ toolName: 'Bash', ruleContent: 'mkdir -p:*' }], behavior: 'allow', destination: 'localSettings' },
        { type: 'addRules', rules: [{ toolName: 'Bash', ruleContent: 'touch:*' }], behavior: 'allow', destination: 'localSettings' },
        { type: 'addDirectories', directories: ['/tmp'], destination: 'session' },
        { type: 'setMode', mode: 'acceptEdits', destination: 'session' } ]) });

    // permission card — command longer than CMD_MAX. The consent case: without the marker you
    // approve a command whose tail the card never showed you.
    renderPermission({ tool: 'Bash', id: 'gallery-bash-cut', input: JSON.stringify({
      command: 'rsync -av --delete ' + '/srv/data/chunk-000/ /mnt/backup/chunk-000/ && '.repeat(120) + 'echo done',
      description: 'Sync every chunk to the backup volume' }) });

    // plan card — with the CLI's setMode suggestion, so the auto-edit row rides it (the split
    // caret, the feedback input and both menu rows all render from this one call)
    renderPermission({ tool: 'ExitPlanMode', id: 'gallery-plan', input: JSON.stringify({
      plan: '1. Add a `sqrt()` method to `Calculator`.\n2. Throw on negative input.\n3. Add unit tests.' }),
      suggestions: JSON.stringify([{ type: 'setMode', mode: 'acceptEdits', behavior: 'allow', destination: 'session' }]) });

    // AskUserQuestion — single-select + multiSelect
    renderPermission({ tool: 'AskUserQuestion', id: 'gallery-ask', input: JSON.stringify({ questions: [
      { header: 'Framework', question: 'Which framework should we use?', options: [
        { label: 'React', description: 'Component model, huge ecosystem.' },
        { label: 'Svelte', description: 'Compiler, minimal runtime.' } ] },
      { header: 'Styling', question: 'Pick the styling approaches.', multiSelect: true, options: [
        { label: 'Tailwind' }, { label: 'CSS Modules' }, { label: 'Vanilla CSS' } ] } ] }) });

    // working line (static — a live one is owned by the watchdog)
    const gen = el('generating', '');
    gen.innerHTML = '<span class="spin">✳</span><span class="verb">Simmering…</span>' +
      '<span class="meta">(<span>6s</span> · ' + SVG_DOWN + '<span>88</span> tokens)</span>';

    // completion summary
    const dn = el('done', '');
    dn.innerHTML = '<span class="star">✻</span> Baked for 1m 53s · ' + SVG_DOWN + fmtTok(1847) + ' tokens';

    // error + retry (retry needs lastUser)
    lastUser = { text: 'Dev gallery — show every UI state.', images: [] };
    errorBlock('Build failed — config/app.php was not found because the Write was rejected.');
    addRetryLine();

    // status lines
    statusLine('Stopped', SVG_STOP);
    // Both account-blocked codes. Drawn through the same table the live branch reads, so the
    // gallery cannot drift from it — and so the one state a signed-out user would be stuck on is
    // reviewable without actually being signed out.
    statusLine(AUTH_BLOCKED.authentication_failed, SVG_ALERT, 'status err');
    statusLine(AUTH_BLOCKED.oauth_org_not_allowed, SVG_ALERT, 'status err');
    // MCP notice (item 13a), drawn through mcpNotice itself so the gallery exercises the real
    // grouping — including that healthy servers are silent and both faults share one line.
    mcpNoticeKey = null;
    mcpNotice([
      { name: 'ide', status: 'connected' }, { name: 'brokenstdio', status: 'failed' },
      { name: 'brokenhttp', status: 'failed' }, { name: 'linear', status: 'needs-auth' },
      { name: 'slow', status: 'pending' }, { name: 'off', status: 'disabled' },
    ]);

    // ---- replayed transcript blocks (the resume path, drawn by renderTranscript itself) ----
    renderTranscript([
      { role: 'user', text: 'Replayed turn — bump the rate constant.' },
      { role: 'thinking', text: 'Checking where the constant is used before editing.' },
      { role: 'assistant', text: 'Found it in `src/util/math.ts` — updating now.' },
      { role: 'tool', text: 'Read', desc: 'src/util/math.ts', isPath: true },
      { role: 'tool', text: 'Bash', desc: 'Show the current value', cmd: 'grep rate src/util/math.ts',
        out: 'export const rate = 0.1;' },
      { role: 'tool', text: 'Bash', desc: 'Lint the project', cmd: 'npm run lint',
        out: 'error: unexpected token', isError: true },
      { role: 'tool', text: 'Edit', desc: 'src/util/math.ts', isPath: true, file: 'src/util/math.ts',
        patch: [{ oldStart: 41, newStart: 41, lines: [
          '   // pricing', '-  export const rate = 0.1;', '+  export const rate = 0.15;', '   ' ] }] },
      { role: 'ask',
        questions: [{ header: 'Rollout', question: 'Ship the new rate now?', options: [
          { label: 'Ship it', description: 'Deploy with the next release.' },
          { label: 'Hold', description: 'Wait for approval.' } ] }],
        answers: { 'Ship the new rate now?': 'Ship it' } },
    ]);

    awaitingUser = false; // clear the flag the permission/ask cards set, so real input isn't blocked
    maybeScroll();
  }
  window.__gallery = gallery;
