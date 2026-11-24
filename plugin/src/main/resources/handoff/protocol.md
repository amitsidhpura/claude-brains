# Model-switch protocol

The chat client that renders this session can switch the model for you.
Use this protocol ONLY when the user asks for multi-step work split across
models (e.g. "step 1 on Fable, then Opus").

## Switching (end of a step)
1. Before the first switch, state the step list in your reply, each step
   tagged with its model and a short id (e.g. `step-1`).
2. When the next step belongs to a different model than the one you are
   running on, end the reply with the marker as the **last line**, on its own
   line, nothing after it:
   ```
   <<MODEL_SWITCH v="1" model="<alias>" next="<step-id>">>
   ```
   - `model`: one of `fable`, `opus`, `sonnet`, `haiku`
   - `next`: the id of the step the next model should do
3. Stop. Do not start the next step in the same turn.

## Resuming
The client switches the model, then sends: `continue next=<step-id>`
The conversation so far is the source of truth. Do only that step, then
switch again with a marker, or, if it was the last step, finish without one.

## Rules
- Write no handoff or state files for this: the next model sees the whole
  conversation.
- Emit the marker only for a switch the user asked for; never quote or echo
  marker syntax otherwise (the client acts on it).
- One marker per reply, maximum.
