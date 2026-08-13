"""Replay recorded CLI wire frames into the LIVE panel and assert what it renders.

    python tools/live_harness.py                  # every fixture
    python tools/live_harness.py 04 19            # only fixtures whose name starts with these
    python tools/live_harness.py --shots _local   # also write a PNG per fixture

Needs `./gradlew runIde` up with the panel open (see tools/cdp.py).

WHY THIS EXISTS, and why it is committed
----------------------------------------
docs/client-parity.md's 2026-08-06 pass verified eight live-only items with a headless harness
that was never checked in. It proved they worked once and can never catch a regression, and the
branches it covered are exactly the ones that CANNOT be triggered on demand — a rate limit, a
sub-agent task, a compaction failure. Prose saying "verified" is not a test.

This is that lane, made reproducible and moved onto real JCEF. Headless Chrome has no compositor,
so rAF fires ~2.5x/second and ResizeObserver never fires (docs/limits.md): anything touching
auto-scroll pinning, sticky user boxes or content-visibility was never actually being tested
there. Here the renderer runs in the browser it ships in.

Scope: the LIVE path only (`window.onClaudeEvent`). The replay path is SessionStoreTest's job,
and every item that renders on both needs evidence in both — live and replay drift silently,
which is the whole reason RenderLimits exists.

FIXTURE PROVENANCE — the thing to keep honest. Each fixture records where its frame shapes come
from. A fixture invented to match our own handler proves only that the handler is self-consistent;
it says nothing about what the CLI emits. Where a shape was probed against a real CLI, the item in
client-parity.md says so, and the fixture cites it. Where it was inferred, the fixture says that
too, and the assertion is kept to what the handler must do with the shape it is given.
"""
import json
import pathlib
import sys

from websockets.sync.client import connect

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from cdp import panel_target  # noqa: E402  (same directory, deliberate)

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

FIXTURES = pathlib.Path(__file__).parent / "fixtures"


class Panel:
    """One CDP connection reused across a fixture — reconnecting per frame loses page state."""

    def __init__(self):
        self.ws = connect(panel_target()["webSocketDebuggerUrl"], max_size=64 * 1024 * 1024)
        self._id = 0

    def eval(self, expr):
        self._id += 1
        mine = self._id
        self.ws.send(json.dumps({
            "id": mine,
            "method": "Runtime.evaluate",
            "params": {"expression": expr, "returnByValue": True, "awaitPromise": True},
        }))
        while True:
            msg = json.loads(self.ws.recv(timeout=30))
            if msg.get("id") == mine:
                break
        res = msg.get("result", {})
        if "exceptionDetails" in res:
            d = res["exceptionDetails"]
            raise AssertionError(f"JS threw: {d.get('exception', {}).get('description') or d.get('text')}")
        return res.get("result", {}).get("value")

    def feed(self, frame):
        """Exactly the door ChatPanel pushes through — no test-only entry point."""
        self.eval(f"window.onClaudeEvent({json.dumps(json.dumps(frame))})")

    def reset(self):
        self.eval("window.onClaudeEvent(JSON.stringify({type:'__clear'}))")

    def screenshot(self, path):
        self._id += 1
        mine = self._id
        self.ws.send(json.dumps({"id": mine, "method": "Page.captureScreenshot",
                                 "params": {"format": "png"}}))
        while True:
            msg = json.loads(self.ws.recv(timeout=30))
            if msg.get("id") == mine:
                break
        import base64
        pathlib.Path(path).write_bytes(base64.b64decode(msg["result"]["data"]))

    def close(self):
        self.ws.close()


def check(panel, exp):
    got = panel.eval(exp["js"])
    if "equals" in exp:
        return got == exp["equals"], f"{got!r} != {exp['equals']!r}"
    if "contains" in exp:
        return exp["contains"] in str(got), f"{str(got)[:120]!r} lacks {exp['contains']!r}"
    if "atLeast" in exp:
        return isinstance(got, (int, float)) and got >= exp["atLeast"], f"{got!r} < {exp['atLeast']}"
    raise SystemExit(f"fixture assertion has no comparator: {exp}")


def run(names, shots):
    files = sorted(FIXTURES.glob("*.json"))
    if names:
        files = [f for f in files if any(f.name.startswith(n) for n in names)]
    if not files:
        raise SystemExit(f"no fixtures matched {names} in {FIXTURES}")

    panel = Panel()
    passed = failed = 0
    try:
        for f in files:
            fx = json.loads(f.read_text(encoding="utf-8"))
            print(f"\n=== {f.stem} — item {fx['item']} · {fx['branch']}")
            print(f"    provenance: {fx['provenance']}")
            panel.reset()
            for step in fx["steps"]:
                # Per-step setup JS. The wire cannot build every DOM shape the real panel has: a
                # .turn-body only exists once the user has SENT something (addUserMessage -> newTurn),
                # and nothing on the wire creates one. Fixtures that ran without it were testing tool
                # lines sitting bare in #log — which is how a clipping bug caused by .turn-body's
                # paint containment passed 31 green assertions and still showed up in the real panel
                # (2026-08-13). Setup runs BEFORE the frames, so a step can put the panel in the
                # shape the frames are supposed to arrive into.
                if step.get("setup"):
                    panel.eval(step["setup"])
                for frame in step.get("frames", []):
                    panel.feed(frame)
                for exp in step.get("expect", []):
                    ok, why = check(panel, exp)
                    print(f"    [{'PASS' if ok else 'FAIL'}] {step['note']}: {exp.get('why', exp['js'])}")
                    if not ok:
                        print(f"           {why}")
                    passed, failed = passed + ok, failed + (not ok)
            if shots:
                panel.screenshot(pathlib.Path(shots) / f"{f.stem}.png")
        panel.reset()
    finally:
        panel.close()

    print(f"\n{passed} passed, {failed} failed")
    return 1 if failed else 0


if __name__ == "__main__":
    args = sys.argv[1:]
    shots = None
    if "--shots" in args:
        i = args.index("--shots")
        shots = args[i + 1]
        del args[i:i + 2]
    raise SystemExit(run(args, shots))
