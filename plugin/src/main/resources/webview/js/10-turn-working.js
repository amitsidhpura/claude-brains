  /* ---------- turn structure: user box + everything that answers it ---------- */
  let curTurn = null;                // .turn container for the current exchange

  function el(cls, text) {
    if (welcome) welcome.style.display = 'none';
    const d = document.createElement('div');
    d.className = cls; if (text != null) d.textContent = text;
    const parent = curTurn || log;
    // keep the working spinner pinned to the very bottom — new content goes above it
    if (workingEl && workingEl.parentNode === parent) parent.insertBefore(d, workingEl);
    else parent.appendChild(d);
    maybeScroll(); return d;
  }
  function newTurn() {
    if (welcome) welcome.style.display = 'none';
    const turn = document.createElement('div');
    turn.className = 'turn';
    // Everything except the user message lives in .turn-body, which is what gets
    // content-visibility. The message must stay OUTSIDE it: containment makes the contained
    // element a stacking context, which would scope .msg-user's z-index and let #fade-top
    // paint over the pinned message.
    const body = document.createElement('div');
    body.className = 'turn-body';
    turn.appendChild(body);
    log.appendChild(turn);
    curTurn = body;
    return turn;
  }

  /* ---------- working line: flower spinner + whimsical verbs + meta ---------- */
  const FRAMES = ['✢', '✳', '✶', '✻', '✽', '✻', '✶', '✳'];
  const VERBS = ['Wandering','Drizzling','Pondering','Percolating','Noodling','Simmering','Conjuring',
    'Cogitating','Marinating','Wibbling','Frolicking','Puttering','Ruminating','Spelunking','Vibing',
    'Reticulating','Brewing','Churning','Mulling','Musing','Schlepping','Manifesting','Transmuting',
    'Honking','Booping','Concocting','Finagling','Moseying','Shimmying','Tinkering','Whirring','Germinating'];
  let workingEl = null, workMeta = null, workMetaHtml = '', workTimers = [], workStart = 0,
      turnTokens = 0, msgTokens = 0, msgChars = 0;
  let awaitingUser = false; // a permission/plan/question card is waiting on the user
  function showWorking() {
    if (workingEl) return;
    workingEl = el('generating', '');
    if (!workStart) workStart = Date.now(); // cumulative across gaps within a turn
    workingEl.innerHTML = '<span class="spin">✳</span><span class="verb"></span><span class="meta"></span>';
    const spin = workingEl.querySelector('.spin'), verb = workingEl.querySelector('.verb');
    workMeta = workingEl.querySelector('.meta'); workMetaHtml = '';
    updateWorkTokens();                     // composes the meta; empty until there is something to say
    let fi = 0, vi = Math.floor(Math.random() * VERBS.length);
    verb.textContent = verbHold || (VERBS[vi] + '…');
    workTimers.push(setInterval(function () { fi = (fi + 1) % FRAMES.length; spin.textContent = FRAMES[fi]; }, 175));
    workTimers.push(setInterval(function () {
      if (verbHold) { verb.textContent = verbHold; return; }   // a real state outranks whimsy
      vi = (vi + 1) % VERBS.length; verb.textContent = VERBS[vi] + '…';
    }, 1800));
    workTimers.push(setInterval(updateWorkTokens, 1000));
  }
  /**
   * Pin the working verb to a real state ("Compacting…") instead of the whimsical rotation, or
   * release it with null. Item 35: while the CLI compacts, the spinner should say so — the CLI
   * announces the state via system/status, and any other status frame releases the pin.
   */
  let verbHold = null;
  function setWorkVerb(text) {
    verbHold = text || null;
    if (workingEl && verbHold) {
      const v = workingEl.querySelector('.verb');
      if (v) v.textContent = verbHold;
    }
  }
  /**
   * Compose "(3s · ↓ 60 tokens)" from whichever parts are non-zero. Rebuilt rather than toggled so
   * the separator can't strand: no "(0s)" in the first second, no "( · ↓ 60 tokens)" if tokens
   * land first, and no empty parens (or its flex gap) when there is nothing to report at all.
   */
  function updateWorkTokens() {
    if (!workingEl || !workMeta) return;
    const secs = workStart ? Math.round((Date.now() - workStart) / 1000) : 0;
    const total = turnTokens + msgTokens;
    const parts = [];
    if (secs > 0) parts.push('<span class="w-el">' + fmtDur(secs * 1000) + '</span>');
    if (total > 0) parts.push(SVG_DOWN + '<span class="w-tok">' + fmtTok(total) + '</span> tokens');
    const html = parts.length ? '(' + parts.join(' · ') + ')' : '';
    if (html === workMetaHtml) return;   // called per delta — skip the DOM write when nothing changed
    workMetaHtml = html;
    workMeta.innerHTML = html;
    workMeta.hidden = parts.length === 0;
  }
  // No authoritative usage arrives until message_delta (end of message), so show a live
  // chars/4 estimate as text/thinking streams; message_delta then reconciles to the real count.
  function estimateMsgTokens() { msgTokens = Math.round(msgChars / 4); updateWorkTokens(); }
  // Rate-limit vocabulary, verbatim from the CLI/VS Code so the panel names a limit the way the
  // rest of the product does. `resetsAt` is UNIX SECONDS and is shown relatively — an absolute
  // clock time reads as precision the value does not have.
  const LIMIT_LABELS = {
    five_hour: 'session limit', seven_day: 'weekly limit', seven_day_opus: 'weekly Opus limit',
    seven_day_sonnet: 'weekly Sonnet limit', seven_day_overage_included: 'Fable 5 limit',
    overage: 'usage credit limit',
  };
  let rateLimitKey = null;   // last status:type shown, so an unchanged limit is not restated
  /**
   * The ACCOUNT half of the CLI's ten-code `error` enum, each with the route out. Wording follows
   * the CLI's own router (`case "authentication_failed": … needs:"login required — run /login"`,
   * `case "oauth_org_not_allowed": … needs:"org disabled OAuth — use API key or ask admin"`),
   * re-pointed at the terminal because this plugin has no login flow by design.
   *
   * The other eight codes — rate_limit, overloaded, billing_error, server_error, invalid_request,
   * model_not_found, max_output_tokens, unknown — are deliberately ABSENT. They are transient or
   * request-shaped rather than account-shaped: the `result` error block already states them and
   * offers Retry, which is the correct affordance. Adding them here would replace a specific CLI
   * message with a vaguer one of ours.
   */
  // Null-prototype so an unknown code can only ever miss: a plain literal would answer
  // AUTH_BLOCKED['constructor'] with a truthy function and render it as the fix.
  const AUTH_BLOCKED = Object.assign(Object.create(null), {
    authentication_failed:
      'Authentication failed. Run `claude` in a terminal, sign in there, then press Refresh.',
    oauth_org_not_allowed:
      'Your organization has disabled OAuth. Use an API key or ask your admin — signing in again will not help.',
  });
  /**
   * The api_retry event's string `error` is NOT prose — it is a five-code ENUM, read verbatim from
   * the 2.1.226 binary's classifier: 529/overloaded_error → "overloaded", 429 → "rate_limit",
   * 401|403 → "authentication_failed", any other status ≥ 408 → "server_error", and no status at
   * all — which is what a network failure looks like — → the literal "unknown" (9.1's storm read
   * "unknown — retrying (n/10)" because that word was echoed to the screen). The rich text the TUI
   * shows on late attempts (`error.formatted`) exists only in-process and never rides the stream,
   * so a readable retry line has to come from this table; "API error" for unknown is the TUI's own
   * early-attempt wording — honest, since the wire genuinely says no more. This is a DIFFERENT
   * enum from the ten-code assistant-frame one above (AUTH_BLOCKED's); don't merge the tables:
   * a retry line repeats, so it gets the short name, not the full route-out sentence.
   * Null-prototype for the AUTH_BLOCKED reason; an unfamiliar code degrades to the raw code.
   */
  const RETRY_REASONS = Object.assign(Object.create(null), {
    overloaded: 'API overloaded',
    rate_limit: 'Rate limited',
    authentication_failed: 'Authentication failed',
    server_error: 'API server error',
    unknown: 'API error',
  });
  /**
   * The UNHEALTHY half of the CLI's MCP status enum, which is
   * `["connected","failed","needs-auth","pending","disabled"]` (from the wire schema, and confirmed
   * live: a `--mcp-config` naming a missing binary reports `status:"failed"`).
   *
   * `pending` is still connecting and `disabled` is a deliberate choice, so neither is a problem to
   * announce; `connected` plainly isn't. That leaves two — and they get DIFFERENT sentences because
   * they have different fixes, which is why this is a table rather than a boolean.
   *
   * Null-prototype for the same reason as AUTH_BLOCKED: an unfamiliar status must miss, not match
   * an inherited key. Statuses added by a future CLI therefore stay silent, which is the right
   * default for a notice — a wrong alarm about tooling is worse than a missing one.
   */
  const MCP_BAD = Object.assign(Object.create(null), {
    failed: 'failed to start',
    'needs-auth': 'needs authentication',
  });
  let mcpNoticeKey = null;   // last set announced, so a re-init with the same failures is not restated
  /**
   * Item 13a: say when MCP tools are missing. Explicitly a NOTICE and not a server-management UI —
   * 13b is the terminal's half of the split (CLAUDE.md § Philosophy), so this names what broke and
   * points at `claude mcp`, and offers no buttons.
   *
   * Servers are grouped BY FAULT rather than listed one per line: five dead servers from one bad
   * config file is one fact, and five red lines would read as five separate incidents.
   */
  function mcpNotice(list) {
    if (!Array.isArray(list)) return;
    const byFault = Object.create(null);
    list.forEach(function (s) {
      const fault = s && typeof s.status === 'string' ? MCP_BAD[s.status] : null;
      if (!fault || !s.name) return;
      (byFault[fault] || (byFault[fault] = [])).push(String(s.name));
    });
    const faults = Object.keys(byFault).sort();
    if (!faults.length) { mcpNoticeKey = null; return; }   // recovered: allow a later relapse to speak
    // Keyed on the SET, not the count, so a re-init announces a genuinely different failure and
    // stays quiet about an identical one.
    const key = faults.map(function (f) { return f + ':' + byFault[f].slice().sort().join(','); }).join('|');
    if (key === mcpNoticeKey) return;
    mcpNoticeKey = key;
    // One line PER FAULT, styled by what it is (user report 2026-09-05, fixture 72): a fresh
    // machine's unauthenticated claude.ai connectors were sharing one red line with real
    // failures and read as "the plugin is broken". failed = an incident, red; needs-auth = an
    // expected state with a known fix, the plain muted status dress.
    faults.forEach(function (f) {
      const names = byFault[f];
      statusLine('MCP ' + (names.length === 1 ? 'server' : 'servers') + ': ' + names.join(', ') +
        ' ' + f + ' — those tools are unavailable. Check with `claude mcp` in a terminal.',
        SVG_ALERT, f === MCP_BAD.failed ? 'status err' : undefined);
    });
  }
  function fmtResets(secs) {
    const ms = secs * 1000 - Date.now();
    if (ms <= 0) return 'soon';
    const m = Math.floor(ms / 60000);
    if (m < 1) return 'soon';
    if (m < 60) return 'in ' + m + 'm';
    const h = Math.floor(m / 60);
    return h < 24 ? 'in ' + h + 'h' : 'in ' + Math.floor(h / 24) + 'd';
  }
  /**
   * The thinking block's token count. The CLI sends the REAL figure as `system/thinking_tokens`
   * (estimated_tokens); `thinkChars / 4` is our own guess and stays only as the fallback for when
   * that event does not arrive — it is a live-only event, never written to the transcript, so its
   * absence from local records says nothing about whether it fires.
   */
  let thinkTokReal = null;
  function paintThinkTokens() {
    if (!thinkTok) return;
    const n = (thinkTokReal != null) ? thinkTokReal : Math.max(1, Math.round(thinkChars / 4));
    thinkTok.textContent = ' · ' + fmtTok(n) + ' tokens';
  }
  function hideWorking() {
    workTimers.forEach(clearInterval); workTimers = [];
    if (workingEl) { workingEl.remove(); workingEl = null; }
    workMeta = null; workMetaHtml = '';
    verbHold = null;   // a held state must not leak into the next turn's spinner
  }
  // The working line is shown for the WHOLE turn (like VS Code / the reference plugin): visible
  // whenever Claude is busy, except while a card is waiting on the user. It's pinned to the
  // bottom by el(), so it fills every gap (tool cycles, background subagents, model waits)
  // regardless of which stream events fire. `busy` is set true on send and false only on `result`.
  setInterval(function () {
    // Show the time/token meter for the whole turn — except while a thinking block is live
    // (that line shows its own running token count, so two indicators would be redundant).
    if (busy && !awaitingUser && !curThink) { if (!workingEl) showWorking(); }
    else if (workingEl) hideWorking();
  }, 300);

