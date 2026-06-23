# Voice-driven article actions

Date: 2026-06-23
Status: approved design, ready for implementation plan

## Problem

When the user edits a mined article by voice, every spoken instruction is currently
interpreted as "rewrite the prose." We want some spoken instructions to instead
trigger **actions** the user already wants to do hands-free:

- 发公众号 — publish the article as a WeChat 公众号草稿
- 分享到社区 — share the article to the VoiceDrop community
- 合并最近三篇 — weave the N most-recent articles into the one being edited

The user should just *say* it — no buttons, no menu. New actions should be cheap to add.

## Current flow (baseline)

```
mic → SpeechDictation (zh-CN ASR) → transcript
    → ArticleAgentSession.enqueue(text)  [serial queue, one in flight]
    → wss://jianshuo.dev/agent/edit?stem=<stem>   {type:"instruct", text}
    → ArticleEditor (Durable Object): load article + style from R2,
      ask Claude to rewrite the WHOLE doc, write back to R2,
      push {type:"updated", article}
    → app reloads the doc in place
```

Relevant files:
- `VoiceDropApp/VoiceEdit.swift` — `SpeechDictation` (ASR, unchanged).
- `VoiceDropApp/AgentSession.swift` — `ArticleAgentSession` (WebSocket client; handles `status` / `updated` / `error`).
- `VoiceDropApp/Community.swift` — `CommunityStore.share(_:)` → `POST /files/api/community/share/<articleKey>` (handles Apple sign-in, returns `shareId`).
- `~/code/jianshuo.dev/agent/src/index.js` — `ArticleEditor` Durable Object (`onMessage` → `_rewrite` → `_callClaude`, structured-output json_schema).
- WeChat publish: `POST /files/api/wechat/<articleKey>` (existing, used by RecordingDetailView ⋯ → 发布公众号草稿).

## Design

### 1. Routing — one Claude call, tool-use decides

`ArticleEditor` stops being rewrite-only. Each `instruct` message goes to Claude with a
tool set and `tool_choice: {type:"any"}` (Claude must pick exactly one tool):

| Tool | Input schema | Handler |
|---|---|---|
| `rewrite_articles` | `{articles:[{title,body}]}` (today's json_schema, now the tool input) | agent writes back, pushes `updated` |
| `merge_recent_articles` | `{count:int}` (default 3) | agent merges, writes current doc, pushes `updated` |
| `publish_wechat` | `{}` | agent emits `action` directive (app executes) |
| `share_to_community` | `{}` | agent emits `action` directive (app executes) |

Mapping examples: "把开头改紧凑点" → `rewrite_articles`; "发公众号" / "帮我发出去吧" →
`publish_wechat`; "推送到社区" → `share_to_community`; "合并最近三篇" →
`merge_recent_articles{count:3}`.

The system prompt tells Claude: the user is editing an article by voice; classify the
instruction and call exactly one tool; default to `rewrite_articles` when it is a content
edit. `rewrite_articles` carries the full owner-voice DNA (today's `REVISE_SYSTEM`), so the
common edit path stays a single round-trip — no separate classify call.

### 2. Two classes of action — who executes

**Content actions (`rewrite_articles`, `merge_recent_articles`) — the agent executes.**
It has the article, the voice DNA, and R2. It runs the Claude work, writes the doc to R2,
and pushes `{type:"updated", article}`. The app reloads in place exactly as today. Both
fire immediately.

**Distribution actions (`publish_wechat`, `share_to_community`) — the app executes.** The
agent does NOT perform the outward side-effect; it emits a directive over the socket and the
app runs its existing, tested endpoint. This keeps irreversible/outward calls on the
well-trodden path (`CommunityStore.share`, the WeChat endpoint) and reuses their
Apple-sign-in / error handling.

Per-action confirmation policy (a table in the Worker; one-line to change):

| Action | Policy |
|---|---|
| `share_to_community` | immediate |
| `publish_wechat` | confirm first |

### 3. WebSocket protocol additions

Agent → app (new):
- `{type:"action", action:"share_to_community", confirm:false}` — app runs immediately, then shows the result (share URL / toast).
- `{type:"action", action:"publish_wechat", confirm:true, prompt:"要把《<title>》发布为公众号草稿吗？"}` — app shows 确认 / 取消; only 确认 calls the endpoint.

Existing messages unchanged: `{type:"status",state:"working"}`, `{type:"updated",article}`, `{type:"error",message}`.

The `action` message resolves the in-flight queue item the same way `updated` does (dequeue
head, `processing=false`, pump next) — so an action ends the turn cleanly and the queue keeps
draining. Confirmation is handled entirely app-side (a SwiftUI confirm dialog → call the
endpoint); the agent does not wait for a confirm reply, so no new app→agent message type is
needed.

### 4. Merge specifics

`merge_recent_articles({count})` — Claude extracts the count from speech ("最近三篇"→3,
default 3 when unspecified). The agent:

1. Lists `users/<sub>/articles/` (its existing R2 scope).
2. Sorts entries by the `<ts>` embedded in each stem (`VoiceDrop-<ts>-...`), newest first.
3. Takes the current article (the DO's own `stem`) plus the next `count-1` most-recent
   `.json` articles. Skips `.empty` markers.
4. Asks Claude to weave their `articles[]` (and transcripts, for fact discipline) into one
   combined set in the owner's voice.
5. Writes the result **into the current doc** and pushes `{type:"updated", article}`.

The other source articles are left untouched (choice C — non-destructive; may leave
redundant originals, which the user can delete in-app).

## App-side changes (`AgentSession.swift` + detail view)

- `ArticleAgentSession.handle` gains a `case "action"`: dequeue/pump like `updated`, and
  surface the directive via a new callback, e.g. `var onAction: ((AgentAction) -> Void)?`
  where `AgentAction` carries `action`, `confirm`, `prompt`.
- The detail view (owner of the session) implements `onAction`:
  - `share_to_community` → `await CommunityStore.share(rec)`, toast the result.
  - `publish_wechat` → if `confirm`, present a confirm dialog; on 确认, call the existing
    WeChat publish path (same code as ⋯ → 发布公众号草稿).
- No change to `SpeechDictation`.

## Worker-side changes (`~/code/jianshuo.dev/agent/src/index.js`)

- Replace the single `output_config` rewrite call with a tools array + `tool_choice:any`.
- Dispatch on the returned tool name:
  - `rewrite_articles` → existing merge-back + R2 write + return doc (push `updated`).
  - `merge_recent_articles` → list/sort/load recent, merge via Claude, write current doc,
    push `updated`.
  - `publish_wechat` / `share_to_community` → look up the title, send the `action` message
    (with `confirm`/`prompt` from the policy table). No R2 write, no outward call.
- Keep the history table; record the instruction for every turn (including actions).

## Out of scope (YAGNI)

- No visible command list / chips (discoverability is natural-language-only).
- No new app→agent confirm message — confirmation is app-local.
- No new distribution endpoints — reuse existing WeChat + community share.
- No merge variants beyond "into current, originals kept."

## Testing

- Worker: unit-test the tool-dispatch (mock Claude returning each tool) — correct branch,
  correct R2 writes for content actions, correct `action` message for distribution actions,
  confirm policy applied.
- Worker: `merge_recent_articles` selects current + N-1 newest, skips `.empty`, writes only
  the current doc.
- App: `ArticleAgentSession` routes `action` to `onAction`, dequeues, pumps next; confirm
  dialog gates `publish_wechat`.
- Manual: speak each of the four intents end-to-end against a test user.
