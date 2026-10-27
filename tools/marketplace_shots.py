#!/usr/bin/env python3
"""Compose the Marketplace screenshots (design/marketplace/*.png) from the REAL renderer.

Each frame is 1200x760 CSS px at device scale 2 (2400x1520): a headline, a sub-line, and two
420px panels side by side (394 until 2026-10-28, when the 'Default (Opus 5.5)' chip wrapped
beside the gauge). A panel is the spliced chat.html (chat.html + webview/css/*.css +
window.LIMITS + webview/js/*.js, both in manifest order — exactly what WebviewAssets/loadUi
build for the IDE) loaded
in an iframe and driven by a scene script through the same builders and frames the live panel
uses (`renderPermission`, `todoList`, `onClaudeEvent`, …), so a screenshot cannot show a state
the plugin does not produce.

Headless Chrome, not JCEF: JCEF-OSR tiles multiple paints into one capture under an emulated
viewport (gotchas § JCEF). Animations are frozen by injected CSS.

Usage:  python3 tools/marketplace_shots.py [scene-number ...]     (default: all of them)
Needs:  google-chrome on PATH.  Writes design/marketplace/NN-*.png and the intermediate pages
        under $SCRATCH (default: /tmp/claude-brains-shots).
"""
import os, re, subprocess, sys, json, pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
WEB = ROOT / "plugin/src/main/resources/webview"
KT = ROOT / "plugin/src/main/kotlin/io/github/amitsidhpura/claudebrains"
OUT = ROOT / "design/marketplace"
SCRATCH = pathlib.Path(os.environ.get("SCRATCH", "/tmp/claude-brains-shots"))
CHROME = os.environ.get("CHROME", "google-chrome")

W, H = 1200, 760          # CSS px; captured at scale 2
PANEL_W, PANEL_H = 420, 600


def kotlin_list(name: str) -> list:
    """Pull a listOf/setOf string list out of RenderLimits.kt so LIMITS cannot drift."""
    src = (KT / "RenderLimits.kt").read_text()
    m = re.search(r"val %s = (?:listOf|setOf)\((.*?)\)\n" % name, src, re.S)
    return re.findall(r'"([^"]+)"', m.group(1))


def kotlin_const(name: str) -> str:
    src = (KT / "RenderLimits.kt").read_text()
    return re.search(r'const val %s = (.+)' % name, src).group(1).strip()


def limits_js() -> str:
    arr = lambda xs: "[" + ",".join('"%s"' % x for x in xs) + "]"
    return ("{descMax:%s,cmdMax:%s,outMax:%s,pathTailMax:%s,descKeys:%s,pathKeys:%s,resultSkip:%s,"
            "inKeys:%s,plumbingTags:%s,planDenyPrefix:%s,planCommentsHeader:%s,tweakNote:%s}" % (
                kotlin_const("DESC_MAX"), kotlin_const("CMD_MAX"), kotlin_const("OUT_MAX"),
                kotlin_const("PATH_TAIL_MAX"), arr(kotlin_list("DESC_KEYS")), arr(kotlin_list("PATH_KEYS")),
                arr(kotlin_list("RESULT_SKIP")), arr(kotlin_list("IN_KEYS")), arr(kotlin_list("PLUMBING_TAGS")),
                kotlin_const("PLAN_DENY_PREFIX"), kotlin_const("PLAN_COMMENTS_HEADER"), kotlin_const("TWEAK_NOTE")))


def manifest(name: str, ext: str) -> list:
    """Pull a WebviewAssets file manifest (JS_FILES / CSS_FILES) so the order cannot drift."""
    src = (KT / "ui/WebviewAssets.kt").read_text()
    body = src[src.index("val %s = listOf(" % name):]
    body = body[:re.search(r"^\s*\)\s*$", body, re.M).start()]   # the list's own closing paren line
    return re.findall(r'"([^"]+\.%s)"' % ext, body)


def panel_html() -> str:
    html = (WEB / "chat.html").read_text()
    css = "".join("/* ===== file: %s ===== */\n" % n + (WEB / "css" / n).read_text()
                  for n in manifest("CSS_FILES", "css"))
    js = "".join("/* ===== file: %s ===== */\n" % n + (WEB / "js" / n).read_text()
                 for n in manifest("JS_FILES", "js"))
    freeze = ("<style>*,*::before,*::after{animation:none!important;transition:none!important}"
              "#log{scrollbar-width:none}#log::-webkit-scrollbar{display:none}</style>")
    version = re.search(r'^version = "([^"]+)"', (ROOT / "plugin/build.gradle.kts").read_text(), re.M).group(1)
    return (html.replace("<!--CSS-->", "<style>\n%s\n</style>%s" % (css, freeze))
                .replace("<!--LIMITS-->", "<script>window.LIMITS=%s;window.__bridge=function(){};</script>" % limits_js())
                .replace("<!--VERSION-->", "v" + version)
                .replace("<!--JS-->", js.rstrip("\n")))


# ----------------------------------------------------------------------------------------------
# Scenes. Each is (file stem, headline, sub-line, [left JS, right JS]); the JS runs inside the
# panel iframe with every top-level chat.html function in scope.

COMMON = r"""
window.onClaudeEvent(JSON.stringify({type:'__project', root:'/home/you/shop'}));
// the roster as CLI 2.1.293 sends it (fixture 97 rows; Default resolved as fixture 67 measured it)
window.onClaudeEvent(JSON.stringify({type:'__models', selected:'default', items:[
  {value:'default', resolvedModel:'claude-opus-5-5', displayName:'Default (recommended)', description:'Opus 5.5 · Best for everyday, complex tasks', supportsEffort:true, supportsFastMode:true},
  {value:'fable', resolvedModel:'claude-fable-5-1', displayName:'Fable 5.1', description:'For your toughest challenges', supportsEffort:true},
  {value:'sonnet', resolvedModel:'claude-sonnet-5-5', displayName:'Sonnet 5.5', description:'Most efficient for simpler tasks', supportsEffort:true},
  {value:'haiku', resolvedModel:'claude-haiku-5-5', displayName:'Haiku 5.5', description:'Fastest for quick answers'}]}));
window.onClaudeEvent(JSON.stringify({type:'__mode', mode:'default'}));
window.onClaudeEvent(JSON.stringify({type:'__commands', items:[
  {name:'compact', description:'Free up context by summarizing the conversation so far', argumentHint:'<optional custom summarization instructions>'},
  {name:'context', description:'Show current context usage'},
  {name:'code-review', description:'Review the current diff for bugs and cleanups', argumentHint:'[target]', aliases:['review']},
  {name:'simplify', description:'Review the changed code for reuse, simplification and efficiency, then apply the fixes'},
  {name:'security-review', description:'Complete a security review of the pending changes on the current branch'},
  {name:'init', description:'Initialize a new CLAUDE.md file with codebase documentation'},
  {name:'deploy', description:'Build, tag and push the release image (project)', argumentHint:'<env>'},
  {name:'standup', description:'Summarise what changed since yesterday (user)'},
  {name:'mcp__github__pr', description:'Draft a pull-request description from the branch'}]}));
// every prompt at 10:42 AM today, so the 1.30 timestamp line shows as it does live
function at(h, m){ const d = new Date(); d.setHours(h, m, 0, 0); return +d; }
function user(t, sel){ return addUserMessage(t, [], at(10, 42), sel || null); }
function md(t){ const b = el('blk',''); b.innerHTML = renderMd(t); foldCode(b); return b; }
function tool(name, desc, isPath){ const t = toolLine(name); const d = t.querySelector('.t-desc'); if (isPath) fillPath(d, desc); else d.textContent = desc; return t; }
function done(ms, tok){ const d = el('done',''); d.innerHTML = doneHtml(ms, tok, 's1'); return d; }
function idle(){ setBusy(false); document.querySelectorAll('.generating').forEach(function(g){ g.remove(); }); }
function working(verb, secs, tok){ setBusy(true); document.querySelectorAll('.generating').forEach(function(g){ g.remove(); });
  const gen = el('generating',''); gen.innerHTML = '<span class="spin">✳</span><span class="verb">' + verb + '</span><span class="meta">(<span>' + secs + '</span> · ' + SVG_DOWN + '<span>' + tok + '</span> tokens)</span>'; }
function clampPopups(){ const st = document.createElement('style'); st.textContent = '#modelMenu,#modeMenu{position:fixed!important;left:12px!important;right:12px!important;top:auto!important;bottom:90px!important;min-width:0!important;width:auto!important}'; document.head.appendChild(st); }
"""

S1_LEFT = COMMON + r"""
window.onClaudeEvent(JSON.stringify({type:'__title', text:'Rate limiting for the API'}));
user('Rate-limit /api/search to 60 requests per minute.');
tool('Read', '/home/you/shop/src/api/router.js', true);
tool('Bash', 'Run the rate-limit tests').after(ioBox([['IN','npm test -- --grep "rate limit"'],['OUT','✓ 60/min allowed · 429 above the limit']]));
md('Added a token-bucket limiter — over the limit is a **429**:\n\n```js\nconst limiter = rateLimit({ windowMs: 60_000, max: 60 });\n```');
done(41000, 1200);
window.onClaudeEvent(JSON.stringify({type:'__files_changed', turn:1, files:[
  {path:'/home/you/shop/src/api/router.js', added:9, removed:1, isNew:false},
  {path:'/home/you/shop/src/api/limiter.js', added:24, removed:0, isNew:true}]}));
idle(); setContext(52000);
"""

S1_RIGHT = COMMON + r"""
window.onClaudeEvent(JSON.stringify({type:'__title', text:'Rate limiting for the API'}));
user('Now apply the same limit to /api/export and /api/import.');
(curTurn||log).appendChild(thinkBlock('Reuse the limiter; only the two routes and their tests change.', 3));
const tt = tool('Update Todos', '');
todoList([
  {content:'Reuse the limiter on /api/export', status:'completed', activeForm:'Reusing the limiter'},
  {content:'Cover /api/import', status:'in_progress', activeForm:'Covering /api/import'},
  {content:'Extend the 429 tests', status:'pending', activeForm:'Extending the tests'}]);
tool('Read', '/home/you/shop/src/api/export.js', true);
{ const tg2 = track(toolLine('Agent')); tg2.querySelector('.t-desc').textContent = 'Find every route without a limiter';
  toolsById['tu-agent'] = { el: tg2, name: 'Agent', io: null }; taskProg = {};
  taskLine({task_id:'tk1', tool_use_id:'tu-agent', description:'Scanning src/api', subagent_type:'Explore', usage:{total_tokens:8063, tool_uses:4}}, false); }
{ const tr = track(toolLine('Bash')); tr.classList.add('run'); tr.querySelector('.t-desc').textContent = 'npm test — re-running the suite'; }
working('Simmering…', '12s', '412'); setContext(64000);
"""

S2_LEFT = COMMON + r"""
window.onClaudeEvent(JSON.stringify({type:'__title', text:'Add CSV export'}));
window.onClaudeEvent(JSON.stringify({type:'__mode', mode:'plan'}));
user('Plan a CSV export for the reports page.');
(curTurn||log).appendChild(thinkBlock('Two touch points: a route and a serializer. Tests last.', 2));
renderPermission({ tool:'ExitPlanMode', id:'plan-1', input: JSON.stringify({ plan:
  '1. Add `GET /reports/:id.csv` next to the JSON route.\n2. Stream rows with a `csvSerializer()` — no full buffer.\n3. Unit tests for quoting, UTF-8 BOM and an empty report.' }),
  suggestions: JSON.stringify([{type:'setMode', mode:'acceptEdits', behavior:'allow', destination:'session'}]) });
idle();
const fb = document.querySelector('.plan-fb'); if (fb) fb.value = 'Skip the BOM — the importer chokes on it.';
"""

S2_RIGHT = COMMON + r"""
window.onClaudeEvent(JSON.stringify({type:'__title', text:'Add CSV export'}));
user('Approved — go ahead.');
renderPermission({ tool:'AskUserQuestion', id:'ask-1', input: JSON.stringify({ questions: [
  { header:'Delimiter', question:'Which CSV dialect should the export use?', options:[
    {label:'Comma (RFC 4180)', description:'The safe default — opens everywhere.', preview:'id,name,total\n42,"Acme, Inc.",19.90'},
    {label:'Semicolon', description:'Excel-friendly in European locales.', preview:'id;name;total\n42;Acme, Inc.;19,90'}]},
  { header:'Headers', question:'Include a header row?', options:[{label:'Yes'},{label:'No'}]}]}) });
idle();
"""

S3_LEFT = COMMON + r"""
window.onClaudeEvent(JSON.stringify({type:'__title', text:'Add CSV export'}));
user('Approved — go ahead with the plan.');
(curTurn||log).appendChild(thinkBlock('Route first, then the serializer.', 2));
renderPermission({ tool:'Edit', id:'edit-1', lineStart: 12, input: JSON.stringify({
  file_path:'/home/you/shop/src/api/reports.js',
  old_string:'router.get("/reports/:id", reportHandler);',
  new_string:'router.get("/reports/:id", reportHandler);\nrouter.get("/reports/:id.csv", csvHandler);' }),
  suggestions: JSON.stringify([{type:'setMode', mode:'acceptEdits', destination:'session'}]) });
renderPermission({ tool:'Bash', id:'bash-1', input: JSON.stringify({ command:'mkdir -p reports/tmp && npm test -- --grep csv', description:'Run the CSV tests' }),
  suggestions: JSON.stringify([
    {type:'addRules', rules:[{toolName:'Bash', ruleContent:'mkdir -p:*'}], behavior:'allow', destination:'localSettings'},
    {type:'addRules', rules:[{toolName:'Bash', ruleContent:'npm test:*'}], behavior:'allow', destination:'localSettings'}]) });
idle();
"""

S3_RIGHT = COMMON + r"""
window.onClaudeEvent(JSON.stringify({type:'__title', text:'Add CSV export'}));
user('Approved — go ahead with the plan.');
// 1.29: a Bash command that edits a file gets its diff drawn under the IN/OUT box, through the
// real builder — the CLI's bashEditDiff sidecar shape, one card per file.
{ const box = ioBox([['IN','sed -i "s/max: 100/max: 500/" src/api/csv.js && npm test -- --grep csv'],['OUT','2 passing (96ms)']]);
  tool('Bash', 'Raise the row cap, re-run the CSV tests').after(box);
  appendBashDiff(box, { files: [{ filePath: '/home/you/shop/src/api/csv.js', created: false,
    hunks: [{ oldStart: 8, oldLines: 1, newStart: 8, newLines: 1, lines: ['-const MAX_ROWS = { max: 100 };', '+const MAX_ROWS = { max: 500 };'] }] }], moreFiles: 0, changedFiles: [] }); }
window.onClaudeEvent(JSON.stringify({type:'__files_changed', turn:1, files:[
  {path:'/home/you/shop/src/api/reports.js', added:1, removed:0, isNew:false},
  {path:'/home/you/shop/src/api/csv.js', added:38, removed:0, isNew:true}]}));
working('Wiring…', '9s', '304'); setContext(78000);
"""

S4_LEFT = COMMON + r"""
window.onClaudeEvent(JSON.stringify({type:'__title', text:'Release 2.4'}));
user('Run the checks before I tag it.');
md('Suite is green and the diff is clean. Say the word and I will tag `v2.4.0`.');
done(21000, 640);
idle();
input.value = '/p';
tg('slashMenu');
"""

S4_RIGHT = COMMON + r"""
window.onClaudeEvent(JSON.stringify({type:'__title', text:'Release 2.4'}));
user('Run the checks before I tag it.');
{ const tr = track(toolLine('Bash')); tr.classList.add('run'); tr.querySelector('.t-desc').textContent = 'npm run lint && npm test'; }
working('Checking…', '8s', '210');
window.onClaudeEvent(JSON.stringify({type:'system', subtype:'background_tasks_changed', tasks:[
  {task_id:'bg_a', task_type:'local_bash', description:'npm run dev'},
  {task_id:'bg_b', task_type:'Explore', description:'Audit the changelog'}]}));
sideOpen('');
const sq = document.querySelector('#sidePanel textarea, #sidePanel input');
if (sq) { sq.value = 'What does --ff-only do?'; sq.dispatchEvent(new KeyboardEvent('keydown', {key:'Enter', ctrlKey:true, bubbles:true})); }
window.onClaudeEvent(JSON.stringify({type:'__side', id:'sq1', response:'`--ff-only` tells `git merge` (or `git pull`) to proceed only if the merge can be a fast-forward, aborting instead of creating a merge commit if the branches have diverged.', synthetic:false}));
"""

S5_LEFT = COMMON + r"""
window.onClaudeEvent(JSON.stringify({type:'__title', text:'Rate limiting for the API'}));
user('Rate-limit /api/search to 60 requests per minute.');
md('Done — the limiter is in and the tests pass.');
done(41000, 1200);
idle();
const now = Date.now();
window.onClaudeEvent(JSON.stringify({type:'sessions', current:'s-cur', items:[
  {id:'s-cur', title:'Rate limiting for the API', time: at(10, 47), size: 48000, tokens: 12400},
  {id:'s-2', title:'Add CSV export', time: at(10, 47) - 19*3600000, size: 121000, tokens: 38200},
  {id:'s-3', title:'Fix the flaky checkout test', time: at(10, 47) - 44*3600000, size: 23000, tokens: 6100},
  {id:'s-4', title:'Migrate the cart to TypeScript', time: at(10, 47) - 3*86400000, size: 402000, tokens: 96000},
  {id:'s-5', title:'Why does the build take 4 minutes?', time: at(10, 47) - 9*86400000, size: 15000, tokens: 4200}]}));
histWanted = true;
window.onClaudeEvent(JSON.stringify({type:'sessions', current:'s-cur', items:[
  {id:'s-cur', title:'Rate limiting for the API', time: at(10, 47), size: 48000, tokens: 12400},
  {id:'s-2', title:'Add CSV export', time: at(10, 47) - 19*3600000, size: 121000, tokens: 38200},
  {id:'s-3', title:'Fix the flaky checkout test', time: at(10, 47) - 44*3600000, size: 23000, tokens: 6100},
  {id:'s-4', title:'Migrate the cart to TypeScript', time: at(10, 47) - 3*86400000, size: 402000, tokens: 96000},
  {id:'s-5', title:'Why does the build take 4 minutes?', time: at(10, 47) - 9*86400000, size: 15000, tokens: 4200}]}));
"""

S5_RIGHT = COMMON + r"""
window.onClaudeEvent(JSON.stringify({type:'__title', text:'Rate limiting for the API'}));
user('Rate-limit /api/search to 60 requests per minute.');
md('Done — the limiter is in and the tests pass.');
done(41000, 1200);
idle(); clampPopups();
tg('modelMenu');
"""

S6_LEFT = COMMON + r"""
window.onClaudeEvent(JSON.stringify({type:'__title', text:'Release 2.4'}));
user('How do we roll back a bad deploy?');
function reply(uuid, text){ const b = el('blk',''); mdBlock(b, text); b.dataset.uuid = uuid; return b; }
reply('r1', 'Roll back in two steps:\n\n1. `kubectl rollout undo deploy/shop`\n2. Re-run the smoke tests against the old image.');
done(14000, 380);
idle();
bmSet([
  {uuid:'r1', addedAt: Date.now() - 2*60000, text:'Roll back in two steps:\n\n1. `kubectl rollout undo deploy/shop`\n2. Re-run the smoke tests against the old image.'},
  {uuid:'r0', addedAt: Date.now() - 3*3600000, text:'Staging secrets live in the vault under `shop/staging`.'}]);
bmSel = 'r1'; bmRender(); bmOpen();
"""

S6_RIGHT = COMMON + r"""
window.onClaudeEvent(JSON.stringify({type:'__title', text:'Release 2.4'}));
{ const st = document.createElement('style'); st.textContent = '.msg-user .msg-save{opacity:1!important;color:var(--fg)!important}'; document.head.appendChild(st); }
user('Lint, run the full suite and check the diff is clean before I tag a release.');
md('Lint is clean, **214 tests** pass and the diff has no stray debug output.');
done(26000, 540);
idle();
onCmdSaved({name:'pre-tag', scope:'user', description:'Lint, test and diff-check before tagging'});
cmdRecent = {'pre-tag': Date.now(), 'code-review': Date.now() - 3600000};
input.value = '/';
tg('slashMenu');
"""

S7_LEFT = COMMON + r"""
window.onClaudeEvent(JSON.stringify({type:'__title', text:'Cart totals'}));
user('Why does @src/cart/total.ts round twice? Compare with @src/cart/tax.ts.',
  {path:'/home/you/shop/src/cart/total.ts', start:12, end:18});
md('`subtotal()` rounds each line **and** `total()` rounds the sum — two roundings drift by a cent on large carts. Round once, at the end.');
done(18000, 460);
idle();
setSelIncluded(true, false);
setIdeSelection({path:'/home/you/shop/src/cart/total.ts', start:24, end:31, text:'export function total(lines) {'});
input.value = 'Fix it in this function.'; autoGrow();
"""

S7_RIGHT = COMMON + r"""
window.onClaudeEvent(JSON.stringify({type:'__title', text:'Cart totals'}));
addUserMessage('Which tests cover the cart?', [], at(17, 5) - 86400000, null);
md('`cart.spec.ts` and `checkout.e2e.ts` — 38 cases between them.');
user('Add one for a 200-line cart.');
const last = md('Added **cart-large.spec.ts**: 200 lines at 0.333 each must total 66.60.');
done(9000, 210);
idle(); placeDaySeps(); copyable(last); last.classList.add('shot-copy');
{ const st = document.createElement('style'); st.textContent = '.shot-copy .blk-copy{opacity:1!important;color:var(--fg)!important}'; document.head.appendChild(st); }
"""

S8_LEFT = COMMON + r"""
window.onClaudeEvent(JSON.stringify({type:'__title', text:'Release 2.4'}));
user('Start the dev server and the build watcher, then keep going.');
tool('Bash', 'Start the dev server');
tool('Bash', 'Watch the build');
md('Both are running in the background — I will keep an eye on the build output.');
done(12000, 300);
idle();
window.onClaudeEvent(JSON.stringify({type:'system', subtype:'background_tasks_changed', tasks:[
  {task_id:'bg_a', task_type:'local_bash', description:'npm run dev'},
  {task_id:'bg_b', task_type:'local_bash', description:'npm run build -- --watch'}]}));
bgOut = { bg_b: { open: true, got: true, truncated: false, total: 0, err: null, pinned: true, askedAt: Date.now() + 1e9,
  text: '> vite build --watch\n\nvite v5.4.2 building for production...\n✓ 412 modules transformed.\ndist/index.html   0.46 kB\ndist/assets/app.js  148.2 kB\n✓ built in 2.31s\nwatching for file changes...' } };
renderBgTasks();
tg('bgMenu');
"""

S8_RIGHT = COMMON + r"""
window.onClaudeEvent(JSON.stringify({type:'__title', text:'Release 2.4'}));
user('Run the full e2e suite.');
{ const tb = track(toolLine('Bash')); tb.classList.add('run');
  tb.querySelector('.t-desc').textContent = 'Run the e2e suite';
  toolsById['tu-e2e'] = { el: tb, name: 'Bash', io: null };
  tb.after(ioBox([['IN', 'npx playwright test --project=chromium']]));
  offerBackground({ task_id: 'tk-e2e', tool_use_id: 'tu-e2e', is_backgrounded: false }); }
working('Testing…', '1m 12s', '96');
"""

SCENES = [
    ("01-conversation", "Claude Code, native in your IDE",
     "The official claude CLI streaming into a JetBrains tool window — thinking, tools, todos, sub-agents, live progress.",
     [S1_LEFT, S1_RIGHT]),
    ("02-plan-and-questions", "Plans you can push back on",
     "Approve a plan with notes; Claude's questions become real forms, each option previewed.",
     [S2_LEFT, S2_RIGHT]),
    ("03-control", "You stay in control",
     "Inline diffs, one-click always-allow, and a per-turn review of every file that changed.",
     [S3_LEFT, S3_RIGHT]),
    ("04-commands-agents", "Commands, background work, side questions",
     "Verified slash commands with source badges, a roster of what runs in the background, and /btw for a quick aside.",
     [S4_LEFT, S4_RIGHT]),
    ("05-sessions-models", "Every conversation, every model",
     "Resume any past conversation; pick the model, fast mode, thinking and effort from one menu.",
     [S5_LEFT, S5_RIGHT]),
    ("06-keep-at-hand", "Keep what you reach for",
     "Bookmark the replies you come back to; save a prompt you send every day as a /command — recent ones first.",
     [S6_LEFT, S6_RIGHT]),
    ("07-context-in-out", "Your editor rides along",
     "The selection and @-files go with each prompt; every message is timed and dated; copy any reply.",
     [S7_LEFT, S7_RIGHT]),
    ("08-background", "Long jobs, out of the way",
     "Watch a background task's output live, or send a slow command to the background mid-run.",
     [S8_LEFT, S8_RIGHT]),
]


FRAME = """<!doctype html><html><head><meta charset="utf-8"><style>
html,body{{margin:0;background:#17181b;width:{W}px;height:{H}px;overflow:hidden;
  font-family:"Noto Sans","Segoe UI",Roboto,sans-serif;color:#e7e9ee}}
h1{{position:absolute;left:84px;top:34px;margin:0;font-size:34px;font-weight:600;letter-spacing:-.01em;
  padding-left:20px;border-left:6px solid #d97757;line-height:1.25}}
p{{position:absolute;left:84px;top:88px;margin:0;font-size:17px;color:#9aa0a9}}
.panel{{position:absolute;top:139px;width:{PW}px;height:{PH}px;border:1px solid #2c2f34;border-radius:8px;
  overflow:hidden;background:#1a1a1a}}
.panel iframe{{border:0;width:{PW}px;height:{PH}px;display:block}}
#l{{left:167px}} #r{{left:613px}}
</style></head><body>
<h1>{title}</h1><p>{sub}</p>
<div class="panel" id="l"><iframe src="panel.html"></iframe></div>
<div class="panel" id="r"><iframe src="panel.html"></iframe></div>
<script>
const CODE = {code_json};
document.querySelectorAll('iframe').forEach(function (f, i) {{
  f.addEventListener('load', function () {{
    try {{ f.contentWindow.eval(CODE[i]); }} catch (e) {{ document.title = 'SCENE ERROR ' + i + ': ' + e; }}
  }});
}});
</script></body></html>"""


def render(idx: int):
    stem, title, sub, code = SCENES[idx]
    SCRATCH.mkdir(parents=True, exist_ok=True)
    (SCRATCH / "panel.html").write_text(panel_html())
    page = SCRATCH / ("%s.html" % stem)
    page.write_text(FRAME.format(W=W, H=H, PW=PANEL_W, PH=PANEL_H, title=title, sub=sub,
                                 code_json=json.dumps(code)))
    out = OUT / ("%s.png" % stem)
    cmd = [CHROME, "--headless=new", "--no-sandbox", "--disable-gpu", "--hide-scrollbars",
           "--force-device-scale-factor=2", "--window-size=%d,%d" % (W, H),
           "--virtual-time-budget=4000", "--allow-file-access-from-files",
           "--screenshot=%s" % out, page.as_uri()]
    subprocess.run(cmd, check=True, capture_output=True)
    print(out, out.stat().st_size, "bytes")


if __name__ == "__main__":
    picks = [int(a) - 1 for a in sys.argv[1:]] or range(len(SCENES))
    for i in picks:
        render(i)
