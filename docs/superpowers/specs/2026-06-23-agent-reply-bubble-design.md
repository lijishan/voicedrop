# Agent reply bubble (transient feedback for non-edit tools)

Date: 2026-06-23
Status: approved design, ready for implementation plan

## Problem

The voice-edit agent now has 7 tools, but only `write_article` produces visible feedback (the
article body changes). Every other tool — `publish_wechat`, `share_to_community`, `read_style`,
`write_style`, `list_articles`, `read_article` — and every recoverable failure is silent: the
agent's closing one-liner (`finalText`) is computed by the loop and then discarded (`void
result` in the Worker's `onMessage`). The user wants the agent's reply surfaced as a transient
chat-style bubble, like a back-and-forth with their own spoken instruction.

## Decisions (from brainstorming)

- **Lifetime: transient.** The reply appears when a turn finishes and auto-fades; nothing is
  stored. The article remains the durable record of edits. (No session/durable thread.)
- **Content: the agent's one-liner** (`finalText`). Success and error use the same text source.
- **Errors are visually distinct and sticky.** Success fades after ~3s; a failure shows in a
  subtle warning style (muted-red border on the light bubble — not assertive filled amber) and
  stays until the user taps to dismiss. A missed failure is worse than a missed success.

## Current state (baseline)

- Worker `~/code/jianshuo.dev/agent/src/loop.js`: `runAgentLoop(...)` returns
  `{calledTools, finalText, steps}`. It already executes every tool and sees each result.
- Worker `src/index.js` `ArticleEditor.onMessage`: runs the loop, re-reads the doc, always sends
  `{type:"updated", article}`, inserts history, and does `void result` (discards `finalText`).
  Thrown exceptions send `{type:"error", message}`.
- App `VoiceDropApp/AgentSession.swift` `ArticleAgentSession`: handles `status` / `updated` /
  `error`. The serial `queue` resolves (dequeue + pump) on `updated`. Exposes `onUpdate`.
- App `VoiceDropApp/RecordingDetailView.swift` `voiceBar`: floating bottom bar that stacks
  pending instruction cards (`queueRow`, which vanish on completion), a dark live-transcript
  bubble (`darkBubble`) while recording, and the press-to-talk `pill`.

## Design

### 1. Worker — surface `finalText` as a reply

- `runAgentLoop` additionally returns `hadError: boolean` = true if ANY tool result this turn
  had an `error` property (`results.some(r => r && r.error)`), accumulated across the loop.
  Signature becomes `{calledTools, finalText, steps, hadError}`.
- `onMessage`: after sending `{type:"updated", article}`, send a reply message derived from the
  loop result:
  - If `finalText` is non-empty → `{type:"reply", text: finalText, ok: !hadError}`.
  - Else if `hadError` (terse model, but something failed) → `{type:"reply", text: "操作没完成",
    ok: false}` (fallback so failures never go silent).
  - Else (empty text, no error — e.g. a plain edit) → send no reply; the article update is the
    feedback.
- The existing `{type:"error", message}` path (thrown exceptions) is unchanged.
- Strengthen the `SYSTEM` prompt: instruct Claude to always end its turn with one short
  sentence stating the result (it mostly does already), so `finalText` is reliably present.

### 2. App — receive the reply, hold it transiently

- `ArticleAgentSession`: add `case "reply"` in `handle` → read `text` (String) and `ok` (Bool),
  invoke a new `var onReply: ((String, Bool) -> Void)?` on the main actor. The reply is
  display-only: it does NOT touch the queue (the queue still resolves on `updated`).
- `RecordingDetailView`: add `@State private var agentReply: AgentReply?` where
  `struct AgentReply: Identifiable { let id = UUID(); let text: String; let ok: Bool }`. Wire
  `agent.onReply = { text, ok in agentReply = AgentReply(text: text, ok: ok); ... }` next to the
  existing `agent.onUpdate` setup (around line 84). On a successful reply (`ok`), start a ~3s
  task that clears `agentReply` if it's still the same id. On an error reply (`!ok`), leave it
  until dismissed.

### 3. App — the bubble UI

In `voiceBar`, render the reply (when `agentReply != nil`) above the `pill`, above the queue /
transcript stack:
- A light bubble on the agent's side (leading-aligned), distinct from the user's dark transcript
  bubble and the light instruction cards. Small leading glyph (e.g. `sparkles`/`checkmark` for
  success) in `Theme.accent`; body text in `Theme.ink`.
- **Success:** neutral light card (`Theme.card` background, `Theme.borderRead` border). Auto-
  fades after ~3s.
- **Error (`!ok`):** same light card, but a muted-red border + a warning glyph
  (`exclamationmark.triangle`) tinted muted-red. Tappable to dismiss (tap clears `agentReply`).
  Stays until tapped.
- Animate appearance/dismissal with the bar's existing easing.

## Out of scope (YAGNI)

- No persistence (no session thread, no durable history reload).
- No tappable affordances inside the bubble (no share-link button, no "去设置" deep link) — the
  one-liner conveys it.
- No new app→agent messages; reply is one-way agent→app.

## Testing

- Worker unit test (`test/loop.test.js`): `runAgentLoop` returns `hadError === true` when a
  scripted tool returns `{error}`, and `hadError === false` for an all-success chain.
- App: manual — 发公众号 (success → fading bubble), 发公众号 with no WeChat config (error →
  sticky muted-red bubble, tap to dismiss), 合并最近两篇 (reply bubble + article updates), an
  edit-only instruction (article changes, no bubble).
