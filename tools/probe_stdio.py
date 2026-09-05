#!/usr/bin/env python3
"""Stdio probe: spawn the CLI with the panel's exact flags, send one prompt, and record every frame
to a tape (stdin frames included, as {"__stdin": …} lines). Born for checklist 1.26 (which `system`
subtypes reach this wire) and 1.28 (whether the CLI sends control_cancel_request), 2026-09-05.

    python3 tools/probe_stdio.py --cwd <dir> --tape out.jsonl --prompt "…"
      --mode default            # --permission-mode; omitted = the panel's own default (none)
      --on-ask allow|park|mode:<m>   # answer every can_use_tool, never answer, or switch mode over it
      --interrupt-after 12      # seconds after start to send an interrupt control (even post-result)
      --linger 20               # keep reading N seconds after the first result
      --cfg <dir>               # CLAUDE_CONFIG_DIR (a scratch copy: credentials + trust-patched
                                #   .claude.json + settings.json — gotchas § Testing; delete after)

Prints the census of `system` subtypes, the banner-family frames, and any control_cancel_request.
Read the tape for order-of-arrival questions. Trust-dependent probes (project settings, hooks)
need --cfg; a plain run inherits the real ~/.claude and writes a real transcript under
~/.claude/projects/<enc-cwd>/ — delete those afterwards."""
import json, os, subprocess, sys, threading, time, argparse

WATCH = {"notification", "memory_saved", "memory_recall", "agents_killed", "permission_retry",
         "away_summary", "scheduled_task_fire", "stop_hook_summary", "code_change_published",
         "vcs_state_changed", "task_summary", "turn_duration"}

ap = argparse.ArgumentParser()
ap.add_argument("--cwd", required=True)
ap.add_argument("--prompt", required=True)
ap.add_argument("--tape", required=True)
ap.add_argument("--mode", default=None, help="--permission-mode value, omitted when absent (panel default)")
ap.add_argument("--interrupt-after", type=float, default=0, help="seconds after start to send interrupt (even after a result)")
ap.add_argument("--linger", type=float, default=0, help="keep reading this many seconds after the first result")
ap.add_argument("--on-ask", default="allow", help="allow | mode:<mode> (send set_permission_mode instead of answering, allow 6s later if still pending)")
ap.add_argument("--timeout", type=float, default=180)
ap.add_argument("--cfg", default=None, help="CLAUDE_CONFIG_DIR override")
ap.add_argument("--second-prompt", default=None, help="a second turn after the first result")
a = ap.parse_args()

cmd = ["claude", "--input-format", "stream-json", "--output-format", "stream-json",
       "--include-partial-messages", "--verbose", "--permission-prompt-tool", "stdio"]
if a.mode: cmd += ["--permission-mode", a.mode]
env = dict(os.environ)
if a.cfg: env["CLAUDE_CONFIG_DIR"] = a.cfg
p = subprocess.Popen(cmd, cwd=a.cwd, env=env, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                     stderr=subprocess.PIPE, text=True, bufsize=1)
tape = open(a.tape, "w")
lock = threading.Lock()
def send(obj):
    line = json.dumps(obj)
    with lock:
        p.stdin.write(line + "\n"); p.stdin.flush()
    tape.write(json.dumps({"__stdin": obj}) + "\n")

def user(text):
    send({"type": "user", "message": {"role": "user", "content": [{"type": "text", "text": text}]}})

stderr_buf = []
threading.Thread(target=lambda: stderr_buf.extend(p.stderr.readlines()), daemon=True).start()

user(a.prompt)
t0 = time.time(); interrupted = False; results = 0; census = {}; hits = []
second_sent = False; first_result_at = None; pending_ask = None; mode_sent = False
import select
while time.time() - t0 < a.timeout:
    if first_result_at and a.linger and time.time() - first_result_at > a.linger and interrupted: break
    if a.interrupt_after and not interrupted and time.time() - t0 > a.interrupt_after:
        interrupted = True
        send({"type": "control_request", "request_id": "int1", "request": {"subtype": "interrupt"}})
        print("-> interrupt sent at %.0fs" % (time.time() - t0), file=sys.stderr)
    if pending_ask and a.on_ask == "park":
        pass
    elif pending_ask and mode_sent and time.time() - pending_ask[1] > 6:
        ev0 = pending_ask[0]; pending_ask = None
        send({"type": "control_response", "response": {"subtype": "success", "request_id": ev0["request_id"],
              "response": {"behavior": "allow", "updatedInput": ev0["request"].get("input", {})}}})
        print("-> late allow sent for the parked ask", file=sys.stderr)
    r, _, _ = select.select([p.stdout], [], [], 0.5)
    if not r:
        if p.poll() is not None: break
        continue
    line = p.stdout.readline()
    if not line:
        if p.poll() is not None: break
        continue
    tape.write(line); tape.flush()
    try: ev = json.loads(line)
    except Exception: continue
    t = ev.get("type")
    if t == "control_request":
        req = ev.get("request", {})
        if req.get("subtype") == "can_use_tool":
            if a.on_ask == "park":
                pending_ask = (ev, time.time()); print("-> ask PARKED (never answered):", req.get("tool_name"), ev.get("request_id"), file=sys.stderr)
            elif a.on_ask.startswith("mode:") and not mode_sent:
                mode_sent = True; pending_ask = (ev, time.time())
                send({"type": "control_request", "request_id": "mode1", "request": {"subtype": "set_permission_mode", "mode": a.on_ask[5:]}})
                print("-> set_permission_mode", a.on_ask[5:], "sent while", req.get("tool_name"), "ask is parked", file=sys.stderr)
            else:
                send({"type": "control_response", "response": {"subtype": "success", "request_id": ev["request_id"],
                      "response": {"behavior": "allow", "updatedInput": req.get("input", {})}}})
        elif req.get("subtype") == "hook_callback":
            send({"type": "control_response", "response": {"subtype": "success", "request_id": ev["request_id"], "response": {}}})
        else:
            print("control_request", req.get("subtype"), file=sys.stderr)
    elif t == "system":
        st = ev.get("subtype"); census[st] = census.get(st, 0) + 1
        if st in WATCH: hits.append(ev)
    elif t == "control_cancel_request":
        print("<- CONTROL_CANCEL_REQUEST", json.dumps(ev), file=sys.stderr); hits.append(ev)
    elif t == "control_response":
        print("<- control_response", json.dumps(ev)[:200], file=sys.stderr)
    elif t == "result":
        results += 1
        if first_result_at is None: first_result_at = time.time()
        if a.second_prompt and not second_sent:
            second_sent = True; user(a.second_prompt); continue
        if not a.linger and not (a.interrupt_after and not interrupted): break
try:
    p.stdin.close()
except Exception: pass
try: p.wait(timeout=15)
except Exception: p.kill()
tape.close()
print("exit", p.returncode, "| results", results, "| elapsed %.0fs" % (time.time() - t0))
print("system census:", json.dumps(dict(sorted(census.items()))))
print("1.26 frames:", len(hits))
for h in hits: print("  ", json.dumps(h)[:600])
if stderr_buf: print("STDERR tail:", "".join(stderr_buf[-8:]).strip()[:800])
