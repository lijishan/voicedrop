# VoiceDrop — tap-to-record, manual delete, no-delete mining, empty-marking, profiling

Date: 2026-06-20

Five focused, independent changes across the iOS app, the server miner, and the
Mac mining skill. No cross-coupling beyond the shared R2 marker convention.

## Marker convention (shared)

A recording is one `VoiceDrop-<stem>.m4a` object in the user's R2 space. Its
processing state is represented entirely by sidecar files under
`<prefix>/articles/`:

| Marker | Meaning | App badge |
|---|---|---|
| `articles/<stem>.json` (with `articles[]`) | mined into article(s) | 已成文 (green) |
| `articles/<stem>.empty` | processed, no usable speech (silent / corrupt / 0-byte) | 无语音 (gray) |
| neither | not processed yet | 待处理 (orange) |

**"已处理" (skip / don't re-mine) = `.json` OR `.empty` exists.** Audio is never
deleted by any pipeline — only by the user's explicit delete in the app.

`.empty` body: `{"status":"empty","reason":"silent|corrupt|no-speech","schema":2}`.

## 1. Tap-to-record (app — `ContentView.swift`)

- Add `.idle` to `Phase`; make it the initial phase.
- `begin()` no longer calls `startRecording()`. It still requests mic permission
  and still kicks off the background `drainQueue()`, then lands on `.idle`.
- `.idle` renders `readyScreen(checkmark: false)` — just the red 开始录音 button.
- Tapping the button calls the existing `startRecording()`. No other change to
  the record/stop/upload flow.

## 2. Swipe-to-delete (app — `LibraryView.swift` + `Library.swift`)

- `LibraryStore.delete(_ rec:)`: issue `DELETE /file/<key>` for the audio
  (`rec.audioName`), `articles/<stem>.json`, `articles/<stem>.srt`, and
  `articles/<stem>.empty`. Audio delete must succeed; the sidecars are
  best-effort (ignore 404). On success remove the row from `recordings`.
- `LibraryView`: `.swipeActions(edge: .trailing)` with a destructive 删除 button
  → sets `@State confirmDelete: Recording?` → `.alert` "删除这条录音？云端不可恢复"
  → on confirm `await store.delete(rec)`.

## 3. Skill stops deleting; processed = the marker (`wjs-mining-voicedrop`)

- Remove the "delete from R2 after success" red-line step entirely.
- `voicedrop-inbox.sh list` filters out recordings that already have
  `articles/<stem>.json` or `articles/<stem>.empty` (computed from the same list
  response), so the skill only sees unprocessed ones.
- On successful mining, the skill writes `articles/<stem>.json` (v2 schema) back
  to R2 via a new `inbox.sh mark <stem> <jsonfile>` (PUT) — marking processed +
  surfacing the articles in the app. Never deletes audio.
- SKILL.md "删除安全红线" section is rewritten to "标记安全红线": archive → mine →
  write marker; never delete.

## 4. Empty / no-speech / corrupt recordings get marked (mine.py + skill + app)

- **mine.py**: replace the `skip (empty transcript)` branch with a write of
  `articles/<stem>.empty` (`reason:"no-speech"`; `reason:"corrupt"` when the file
  won't decode / ffprobe fails). Skip condition becomes "`.json` or `.empty`
  exists".
- **Skill**: ffprobe corrupt/0-byte → `.empty` reason `corrupt`; `<1s` →
  `.empty` reason `silent`; ASR returns empty → `.empty` reason `no-speech`.
  Each via `inbox.sh mark-empty <stem> <reason>`. Never leaves unprocessed.
- **App** (`Library.swift` + `LibraryView.swift` + `RecordingDetailView.swift`):
  `Recording` gains `isEmpty` (names contains `articles/<stem>.empty`) and
  `emptyReason` (fetched lazily in detail). Row badge: 已成文 / 无语音 / 待处理.
  Detail view: when empty, show "这条录音没检测到语音（<reason>）" instead of the
  "还没成文" pending screen.

## 5. Timestamped profiling logs (mine.py)

- A `log()` helper prefixes every line with wall-clock `[HH:MM:SS]`.
- Per recording, time and print each phase: download (+bytes), ASR (+word count),
  Claude mine (+article count), upload. Print a per-recording total.
- End-of-run summary line: `DONE: N mined · M empty · Ts total
  [ASR x% · LLM y% · net+other z%]` so the bottleneck is obvious.

## Out of scope / non-goals

- No change to the recording/upload race fix (already merged).
- No change to the v2 article schema or the 王建硕 voice prompt.
- No retry/backoff redesign of ASR or the LLM call — only instrumentation.

## Verification

- App: `xcodegen generate` + `xcodebuild build` for an iOS simulator → BUILD
  SUCCEEDED, with the combined tree.
- mine.py: `MINE_DRY=1` run prints the new timestamped list/skip lines without
  calling ASR/LLM.
- Skill: `inbox.sh list` omits processed; `mark` / `mark-empty` round-trip a
  marker that `list` then filters.
