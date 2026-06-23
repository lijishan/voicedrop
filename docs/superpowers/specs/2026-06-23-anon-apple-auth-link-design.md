# VoiceDrop auth: anonymous-first, Apple as a linked second key

Date: 2026-06-23 · Status: approved design (pre-implementation)

## Context & goal

VoiceDrop today has two parallel auth systems:
- **Anonymous** (default): the app generates `anon_<hex>` on first launch, stores it in the
  iCloud Keychain, and the server scopes the user to `users/anon-<sha256hex(token)[:32]>/`.
  Possession of the token = access. Because it lives in iCloud Keychain it already syncs across
  one person's devices (same Apple ID).
- **Sign in with Apple**: `POST /files/api/auth/apple` verifies an Apple identity token and mints
  a session JWT (HS256, `SESSION_SECRET`) scoped to `users/<sub>/`. Currently unused in practice
  and the `voicedrop-agent` worker is even missing `SESSION_SECRET`.

The community (VD社区) makes identity matter: anonymous posting has no accountability, bans don't
stick (wipe Keychain → new identity), and we want cross-device to be solid for participants — all
without putting a login wall in front of the core record→article flow.

**Goal:** keep the core app 100% anonymous and zero-login, and make Apple Sign-In an *optional,
stronger key bound to the same data box*, required only at the community boundary.

## The model (one box, two keys)

A user's data lives in one R2 scope. Anon and Apple are **two keys to the same box**, not two
accounts. Signing in with Apple **moves no data** — it attaches an Apple-verified identity to the
existing `users/anon-X/` scope via a binding record. Afterwards both the anon token (Keychain) and
the Apple JWT resolve to that same scope.

Rejected alternative — **migrate** (copy anon data into a fresh `users/<sub>/` on sign-in): the
scope contains audio `.m4a` files, so this is a bulk multi-hundred-MB R2 copy per sign-in — slow,
costly, racy. Aliasing is instant and moves nothing.

## Data link (alias / binding)

Records (R2, bucket `jianshuo-dev-files`):
- `links/apple-<sub>.json` = `{ "scope": "users/anon-X/", "linkedAt": <ms> }` — reverse lookup, the
  source of truth for "which box does this Apple identity own".
- `<scope>ACCOUNT.json` = `{ "appleSub": "<sub>", "linkedAt": <ms> }` — marks the scope as
  Apple-verified and records the anchor identity (for future moderation).

`POST /files/api/auth/apple` (extend the existing handler):
1. Request carries `{identityToken}` in the body **and** the caller's current anon token in
   `Authorization: Bearer <anonToken>` (the app's normal bearer).
2. Verify `identityToken` with Apple JWKS → `sub` (existing `verifyAppleIdentityToken`).
3. Resolve the binding:
   - `links/apple-<sub>.json` **exists** → `scope` = its stored value (returning user / new device).
   - **absent** (first link) → `scope` = the anon token's scope (`users/anon-<hash>/`) when a valid
     anon token was supplied, else a fresh `users/<sub>/`. Write both binding records.
4. Mint a JWT carrying `{ scope, apple: true, iat, exp }` (long-lived, e.g. 180d). Return
   `{ session, scope }`.

**Scope resolution** (files API `[[path]].js` *and* agent worker `agent/src/index.js`): a session
JWT now yields `{scope, apple}` from `verifySession` and the request is scoped to `scope` (instead
of deriving `users/<sub>/`). Anon tokens and temp tokens are unchanged (`apple:false`).

> Breaking change to the JWT payload (was `{sub}` → `users/<sub>/`; now `{scope, apple}`). Safe:
> there are ~no real Apple users yet, and `SESSION_SECRET` is being rotated anyway (below).

## Community gate

`functions/files/api/[[path]].js`:
- **Read** (`GET community/list` / `get/<id>` / `shared/<key>`) — unchanged, open (anon or none).
- **Write** (`POST community/share/<articleKey>`, `POST community/unshare/<id>`) — require an
  **Apple-verified JWT** (`apple === true`). A bare anon/temp token → `403 {error:"needs_apple_signin"}`.
- `owner` / `mine` / author stay keyed on `scope` (now Apple-backed). **Author display name = the
  user's existing 名字** from `<scope>CLAUDE.md` (unchanged from today). No new handle system.

## iOS app

- `AuthStore` (+ existing `AppleAuth.swift`): anon stays the everyday `bearer`. Add the Apple flow
  (`ASAuthorizationController`): on success, `POST auth/apple` with `{identityToken}` + the current
  anon token, store the returned JWT. Once a JWT is present, **prefer it** as `bearer` (it also
  unlocks the agent worker's voice-edit + live status); fall back to the anon token if the JWT is
  missing/expired (and re-prompt Apple at the community boundary if needed).
- **Trigger points:**
  1. Tapping **分享到 VD社区** while not yet Apple-linked → a one-line explainer
     ("分享到社区需要用 Apple 登录，确认你是同一个人") → Apple sheet → bind → retry the share. Driven by
     the server's `403 needs_apple_signin`.
  2. **设置 → 用 Apple 登录（同步设备 · 参与社区）** — proactive entry for users who want solid
     cross-device before they ever touch the community.
- Settings account section reflects state: 匿名 vs 已用 Apple 登录.
- Ensure the **Sign in with Apple** capability is enabled in `VoiceDrop.entitlements`.

## SESSION_SECRET (must fix as part of this)

Apple JWTs are verified by **both** the Pages function and the `voicedrop-agent` worker, so
`SESSION_SECRET` must be set and **identical** on both. It's currently present on Pages but
**missing on the worker**. Since the value isn't recoverable and there are ~no Apple users, rotate
to one fresh value and set it on both (`wrangler pages secret put` + `wrangler secret put`).
(This also fixes Apple users' voice-edit/status, which 401 today.)

## Edge cases

- Second device (same iCloud Keychain + Apple ID): same anon token, `apple:<sub>` already bound →
  same box. Automatic.
- Apple-first user (signs in before recording): no anon box → binding target is `users/<sub>/`.
- "Hide My Email": we key on `sub`, never need the email.
- JWT expiry: long-lived; if expired, anon still covers the core app and Apple is re-prompted only
  at the community boundary.

## Out of scope (YAGNI)

No data migration/copy; no Apple-to-*read* community; no separate username/handle; no moderation UI
in v1 (the Apple-`sub` anchor is the foundation for it later).

## Verification

- **Server:** with a fresh anon token, `POST community/share/...` → `403 needs_apple_signin`. Run
  `auth/apple` (anon token + a valid Apple identity token) → `{session}`; the same article share
  with that JWT → `200`. Confirm `links/apple-<sub>.json` + `<scope>ACCOUNT.json` written, and that
  the JWT resolves to the **same** `users/anon-X/` scope (a `GET list` with the JWT shows the anon
  user's existing recordings — proving no data moved). A second `auth/apple` for the same `sub`
  re-resolves to the same scope. Apple JWT also works on `wss://…/agent/status` (SESSION_SECRET set
  on the worker).
- **App:** anonymous user records + reads normally with no login. Tap 分享到社区 → Apple sheet →
  post appears authored by their 名字, `mine=true`. Re-open on a second device (same Apple ID) →
  same recordings + same community ownership. 设置 → 用 Apple 登录 links without touching the
  community.
