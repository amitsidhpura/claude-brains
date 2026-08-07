"""Run JS inside the LIVE chat webview, over the Chrome DevTools Protocol.

    python tools/cdp.py "document.querySelectorAll('.turn').length"
    python tools/cdp.py -f probe.js          # anything multi-line
    python tools/cdp.py --targets            # what DevTools can see right now
    python tools/cdp.py --screenshot out.png # PNG of the panel as it renders in the IDE

Needs `./gradlew runIde` up with the Claude Brains tool window OPEN — CEF starts with the
first JBCefBrowser, so before that the page does not exist and there is nothing to attach to.
The debug port is set by the build (`runIde` passes -Dide.browser.jcef.debug.port=9222), so no
sandbox Registry edit is needed; a value set by hand in the sandbox still takes precedence.

Why this exists: it is the only way to inspect the renderer under REAL JCEF. The headless-Chrome
probes in docs/limits.md measure a different browser — no compositor, so rAF fires ~2.5x/second
and ResizeObserver never fires — and design/mockup.html is a static fixture with no bridge and no
CLI behind it. This talks to the actual panel: real frame timing, real LIMITS splice, real state.

Complements, not replaces:
  ./gradlew probe   — replay blocks for a session, no IDE. Splits a PARSER bug from a RENDERER bug.
  Ctrl+Alt+G        — the gallery: every transient state drawn without driving the CLI.
  this              — the live DOM as it actually is, mid-session.

Only Python's `websockets` is required (no CDP client library). One shot per invocation: it
connects, evaluates, prints, disconnects — so there is no streaming console capture here; add a
`Runtime.consoleAPICalled` listener if you need one.

Gotcha: the expression runs in the page's GLOBAL scope, so a bare `const log = ...` collides with
chat.html's own globals and throws "Identifier already declared". Wrap anything with declarations
in an IIFE: `(() => { const el = ...; return JSON.stringify(...); })()`.
"""
import json
import sys
import urllib.request

from websockets.sync.client import connect

PORT = 9222
# JBCefBrowser.loadHTML serves the panel from a synthetic file URL — the panel has no real
# address, so this prefix, not the title, is what identifies it among DevTools' own windows.
PANEL_URL_PREFIX = "file:///jbcefbrowser/"


def targets():
    raw = urllib.request.urlopen(f"http://localhost:{PORT}/json/list", timeout=8).read()
    return json.loads(raw)


def panel_target():
    found = targets()
    for t in found:
        if t.get("type") == "page" and t.get("url", "").startswith(PANEL_URL_PREFIX):
            return t
    listed = "\n".join(f"  [{t.get('type')}] {t.get('url', '')[:70]}" for t in found) or "  (none)"
    raise SystemExit(
        f"No chat-panel target among {len(found)}:\n{listed}\n"
        "Open the Claude Brains tool window — CEF creates the page with the first browser."
    )


def evaluate(expr):
    target = panel_target()
    print(f"# target: {target.get('title') or target.get('url')[:60]}", file=sys.stderr)
    with connect(target["webSocketDebuggerUrl"], max_size=64 * 1024 * 1024) as ws:
        ws.send(json.dumps({
            "id": 1,
            "method": "Runtime.evaluate",
            "params": {
                "expression": expr,
                "returnByValue": True,   # plain JSON back, not remote object handles
                "awaitPromise": True,
                "allowUnsafeEvalBlockedByCSP": True,
            },
        }))
        while True:   # skip unsolicited events; ours is the frame carrying the id
            msg = json.loads(ws.recv(timeout=30))
            if msg.get("id") == 1:
                break

    if "error" in msg:
        raise SystemExit(f"CDP error: {msg['error']}")
    result = msg["result"]
    if "exceptionDetails" in result:
        exc = result["exceptionDetails"]
        raise SystemExit(f"JS threw: {exc.get('exception', {}).get('description') or exc.get('text')}")
    value = result.get("result", {}).get("value")
    print(value if isinstance(value, str) else json.dumps(value, indent=2, default=str))


def screenshot(path):
    """PNG of the panel exactly as the IDE renders it — real JCEF, real fonts, real theme.

    Beats an OS window-grab: no focus stealing, no window chrome, no cursor, and it is the
    webview's own pixels rather than whatever happened to be on top of it.
    """
    target = panel_target()
    with connect(target["webSocketDebuggerUrl"], max_size=64 * 1024 * 1024) as ws:
        ws.send(json.dumps({"id": 1, "method": "Page.enable"}))
        ws.send(json.dumps({
            "id": 2,
            "method": "Page.captureScreenshot",
            "params": {"format": "png", "captureBeyondViewport": False},
        }))
        while True:
            msg = json.loads(ws.recv(timeout=30))
            if msg.get("id") == 2:
                break
    if "error" in msg:
        raise SystemExit(f"CDP error: {msg['error']}")
    import base64
    png = base64.b64decode(msg["result"]["data"])
    with open(path, "wb") as f:
        f.write(png)
    print(f"{path} ({len(png):,} bytes)")


if __name__ == "__main__":
    args = sys.argv[1:]
    if not args:
        raise SystemExit(__doc__)
    if args[0] == "--targets":
        for t in targets():
            print(f"[{t.get('type')}] {t.get('title')!r}\n    {t.get('url', '')[:90]}")
    elif args[0] == "--screenshot":
        screenshot(args[1])
    elif args[0] == "-f":
        evaluate(open(args[1], encoding="utf-8").read())
    else:
        evaluate(args[0])
