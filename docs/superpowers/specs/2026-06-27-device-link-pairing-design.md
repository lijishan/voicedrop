# 设备配对：新设备登录老账号（6 位定位 + 4 位验证 + 长链接推送）

日期：2026-06-27
状态：设计已批准，待写实现计划

## 1. 背景与目标

VoiceDrop 的匿名身份是**不可重新签发**的：scope = `users/anon-<sha256(token)[:32]>/`，服务器不存
token，**持有 `anon_…` 密钥本身就是身份**（`functions/lib/auth.js:75` `anonScopeFromToken`）。因此服务器
**无法凭空给新设备签发一个指向老账号的 token**（等于求 sha256 原像）。

目标：让一台新设备登录到已有的匿名账号，看到老账号的录音/文章。本质 = **把老设备钥匙串里的
`anon_…` 密钥安全地搬到新设备**。机制采用用户指定的三段式：

1. 新设备输入老账号的 **6 位短码**（= 设置页已显示的 `sha256(token)` 前 6 位十六进制，`SettingsView.swift:279`）。
2. 服务器把一条带 **4 位验证码** 的消息，经现有实时长链接推送给「ID 以这 6 位开头」的所有用户的所有在线客户端。
3. 用户把老设备上显示的 4 位码输入新设备 → 验证通过 → 老设备把密钥**端到端加密**后经服务器中转给新设备 → 新设备 adopt，完成登录。

> 注：「现在的 SSE 长链接」在代码里其实是 **WebSocket** —— `StatusHub` Durable Object（每用户一个实例
> `status:<scope>`，app 连 `wss://jianshuo.dev/agent/status`，服务器经 `/agent/notify` 广播给该用户所有在线
> 客户端）。机制等价，本设计复用它，不引入 SSE。

### 已知但**有意不采用**的更简方案（记录决策依据）

- **Apple 登录其实已是 `/login` 同款模型**：`functions/files/api/[[path]].js:54-86` 把 `links/apple-<sub>.json`
  持久化为 Apple ID → scope 的映射；任何设备用同一 Apple ID 登录都会复用老 scope 并 `mintSession(老scope)`。
  唯一缺口是 app 默认用 anon token 当数据 bearer（session 仅用于社区写），所以新设备 Apple 登录拿到的
  session 没被当成数据 bearer。**用户明确选择不走 Apple 路线。**
- **QR 直传**（老设备把 token 显示成二维码、新设备扫码 adopt，密钥不过服务器）更简单、更安全，但**用户明确
  不想用摄像头扫码**。

用户在权衡后选择本 6+4 推送方案（理由：不想用 Apple、也不想用摄像头）。本 spec 据此设计。

## 2. 非目标（Out of scope）

- **老设备已丢/已售/离线**：本方案要求老设备开机、在线、在手边。老设备离线 → 推不到 → 新设备等待超时并提示。
  离线恢复（Apple 绑定 / 备份恢复码）是另一个更大的设计，本期不做。
- **纯数字 6 位码**：保持现有十六进制前缀（零新增设施）。不建「数字 → scope」注册表。
- **iOS 自动化测试**：仓库无 iOS 单测，iOS 侧给手测清单。
- **CLI / headless 客户端登录**：方向已定（方案 A，见 §11），但**本期不实现**——先把 iOS 设备配对落地，CLI 作为下一个独立 skill 复用同一组路由。

## 3. 总体架构（全部复用现有积木）

```
新设备(未登录)            agent Worker                老设备(已登录, 在线)
   │                    /agent/link/*                    │ 已连 wss /agent/status (StatusHub)
   │  ① POST start ───▶ R2 list 前缀解析匹配 scope        │
   │     {prefix,pubkey} 建 LinkBroker DO(pairingId)      │
   │                    向每个匹配 scope 的 StatusHub 推 ─▶ ② link_request{pairingId,code,pubkey}
   │  ③ WS link/socket ─▶ (挂着等 blob)                   │   弹卡：验证码 1234 · [不是我]
   │  ◀────────── 人眼搬运 1234 ─────────────────────────│
   │  ④ POST verify ──▶ LinkBroker 校验 code             │
   │     {pairingId,code} 命中→向该 scope StatusHub 推 ─▶ ⑤ link_release{pairingId}
   │                                                      │   X25519+AESGCM 加密 token
   │                    ⑥ POST complete ◀────────────────│   {pairingId,blob}
   │  ◀── ⑦ link_ready{blob} (经 socket) ── LinkBroker    │   (鉴权: 调用方 scope==命中 scope)
   │  解密 blob → adopt anon_… → 刷新列表 ✓               │
```

**新增/改动的部件：**

| 部件 | 位置 | 改动 |
|---|---|---|
| `/agent/link/*` 路由 | `agent/src/index.js` | 新增 5 个端点（start/socket/verify/complete/cancel） |
| `StatusHub` 广播 | `agent/src/index.js` | `/broadcast` 泛化为转发任意 `payload`（`status_update` 走默认保持兼容） |
| `LinkBroker` DO | `agent/src/index.js`（+ `wrangler.jsonc` 绑定/migration） | 新 DO，按 `pairingId` 一个实例，存配对状态 + 持新设备 socket，`alarm()` 2 分钟自清 |
| 前缀解析 | `agent/src/index.js` | `env.FILES.list({prefix:"users/anon-<6hex>",delimiter:"/"})` 列匹配账号，零新注册表 |
| `DeviceLink.swift` | `VoiceDropApp/` | 新文件：新设备流程 `DeviceLinkStore` + 老设备审批卡 |
| `AuthStore.adoptToken(_:)` | `VoiceDropApp/` | 新方法：用收到的 token 替换本机匿名身份并刷新 |
| `StatusSession` 扩展 | `VoiceDropApp/` | 现有长链接消息分发增加 `link_request`/`link_release` |
| 入口 | `SettingsView`/`AccountView` | 新设备「登录已有账号」入口 |

## 4. 协议与 API 契约

所有路由在 agent Worker（`jianshuo.dev/agent/*`，同源 `wss`）。常量：`CODE_TTL_MS = 120_000`、
`MAX_ATTEMPTS = 5`、`MAX_MATCH = 10`。

### 4.1 `POST /agent/link/start`（新设备调）
- 鉴权：需带本机有效 token（anon 或 session）——**仅用于限流，scope 不参与**。
- Body：`{ prefix: "<6 hex>", pubkey: "<b64url X25519 公钥>" }`
- 处理：
  1. 校验 `prefix` 匹配 `/^[0-9a-fA-F]{6}$/`，转小写。
  2. `env.FILES.list({ prefix:"users/anon-"+prefixLower, delimiter:"/" })` → `delimitedPrefixes` =
     `["users/anon-<hash>/", ...]`，截断到 `MAX_MATCH`。0 个 → `{ ok:false, reason:"no_match" }`。
  3. `pairingId` = 16 字节随机 b64url。
  4. 为每个匹配 scope 生成一个 4 位码（`0000`–`9999`），**保证该 pairing 内互不相同**（撞了重摇）。
  5. 建 `env.LinkBroker.idFromName(pairingId)`，`{op:"create", pubkey, entries:[{scope,code}], ttlMs:CODE_TTL_MS}`。
  6. 对每个匹配 scope，向 `env.StatusHub.idFromName("status:"+scope)` 推
     `{ payload:{ type:"link_request", pairingId, code, pubkey } }`。
  7. 返回 `{ ok:true, pairingId, matchCount }`（**不返回任何 code**）。
- 限流：按 IP + token，活跃 pairing 数超限拒绝。

### 4.2 `GET /agent/link/socket?pairingId=<id>`（新设备 WebSocket）
- 新设备 start 成功后立刻连上，挂着等 blob。
- Worker 解析 `env.LinkBroker.idFromName(pairingId)` 转发 upgrade；DO `acceptWebSocket`。
- DO 在 complete 时发 `{ type:"link_ready", blob }`；cancel→`{type:"link_cancelled"}`；过期→`{type:"link_expired"}`。
- 若连上时 blob 已就绪（竞态），立即补发。

### 4.3 `POST /agent/link/verify`（新设备调）
- 鉴权：同 start（本机 token，仅限流）。
- Body：`{ pairingId, code }`
- Worker → DO `{op:"verify", code}`：检查未过期、`attempts < MAX_ATTEMPTS`；timing-safe 比对找 entry；`attempts++`。
  - 未命中 → `{ ok:false, remaining }`；耗尽 → `{ ok:false, dead:true }`。
  - 命中 → `status="verified"`、`releasingScope=entry.scope`，DO 返回 `{ ok:true, scope }`（**scope 仅服务器内部用**）。
- Worker 命中后向 `status:<releasingScope>` 推 `{ payload:{ type:"link_release", pairingId } }`；
  **对新设备只返回 `{ ok:true }`**（不含 scope）。

### 4.4 `POST /agent/link/complete`（老设备调）
- 鉴权：**必需**。Worker 解析调用方 scope（`anonScopeFromToken` 或 `verifySession`），**必须等于
  `releasingScope`**，否则 403。这是放行闸门——只有命中账号的真主人能放行。
- Body：`{ pairingId, blob }`，`blob` = 端到端加密后的 token（见 §5）。
- Worker → DO `{op:"complete", callerScope, blob}`：校验 `status==="verified" && callerScope===releasingScope`；
  存 blob；向新设备 socket 发 `link_ready`；`status="done"`；排期 purge。返回 `{ ok:true }`。

### 4.5 `POST /agent/link/cancel`（老设备「不是我」）
- 鉴权：必需；调用方 scope 须是某个匹配 scope。
- Body：`{ pairingId }` → DO 标记 cancelled、通知新设备 socket、purge。

### 4.6 StatusHub `/broadcast` 泛化（唯一对现有代码的改动）
现状写死 `{type:"status_update",stem,status}`。改为：

```js
const body = await request.json();
const msg = JSON.stringify(body.payload ?? { type:"status_update", stem: body.stem, status: body.status });
for (const ws of this.state.getWebSockets()) { try { ws.send(msg); } catch (_) {} }
```

`/agent/notify` 仍发 `{stem,status}` → 命中默认分支，**完全向后兼容**。link 推送发 `{payload:{...}}`。

## 5. 端到端加密（服务器零知识）

搬的是账号长期密钥，故服务器**只过密文、不解密、不落地**。用 Swift CryptoKit。

- **算法**：X25519 ECDH → HKDF-SHA256（`salt="voicedrop-device-link/v1"`、`info="anon-token"`、32 字节）→ AES-GCM。
- **blob 结构**：`{ epk: "<b64url 老设备临时公钥>", sealed: "<b64url AES.GCM.combined>" }`，`combined = nonce(12)+ct+tag(16)`。
- **新设备**：start 前 `priv = Curve25519.KeyAgreement.PrivateKey()`，`pubkey = priv.publicKey.rawRepresentation`（b64url）随 start 上报；收到 blob 后 `priv.sharedSecretFromKeyAgreement(with: epk)` → HKDF → `AES.GCM.open` → `anon_…`。
- **老设备**：收到 `link_request` 拿到 `pubkey`；`link_release` 时 `eph = PrivateKey()`，ECDH(eph, newPub) → HKDF → `AES.GCM.seal(tokenData)`，blob.epk = `eph.publicKey`，blob.sealed = `combined`。
- 服务器只在 start（转发 pubkey）和 complete→link_ready（转发 blob）经手，**从不接触明文 token**。

## 6. iOS 改动（`VoiceDropApp/`）

- **`AuthStore.adoptToken(_ newAnon:String)`**（新增）：把 `newAnon` 写入 iCloud 钥匙串（与现有 anon token 同一存储项），更新内存 `anonToken`/`anonId`/`bearer`，发通知让 `LibraryStore` 重载列表。
- **`DeviceLink.swift`**（新文件，xcodegen 自动纳入）：
  - 新设备 `@Observable DeviceLinkStore`：状态机 `idle→entering→waiting(pairingId)→codeEntry→verifying→receiving→done/error`；持 X25519 私钥；`start(prefix:)`/`submitCode(_:)`；处理 socket 消息；解密 + `adoptToken`。
  - 老设备侧：`StatusSession` 收到 `link_request` → 弹 `DeviceLinkApprovalSheet`（显示 4 位码 + 「不是我」），存 `{pairingId,pubkey}`；收到 `link_release` 对应 pairingId → 加密 token → `complete`；「不是我」→ `cancel`。
- **`StatusSession.swift`**：现有消息 switch 只认 `status_update`，扩展为把 `link_request`/`link_release` 经闭包/delegate 抛给 `DeviceLink`（老设备本就常连 `/agent/status`，无需新连接）。
- **入口**（`SettingsView`/`AccountView`）：新设备账户页加「**登录已有账号**」→ sheet（6 位十六进制输入框，大小写不敏感）→ `DeviceLinkStore`。老设备无需主动入口（审批卡由 `StatusSession` 触发）；账户页可加一句说明：这 6 位就是在新设备上要输入的码。
- 加完 `DeviceLink.swift` 跑 `xcodegen`（项目约定）。

## 7. 安全 / 威胁模型

- **暴力破解 4 位码**：5 次 / 2 分钟 → ≤5/10000 ≈ 0.05%/pairing，且真主人会在自己设备上看到 `link_request` 卡——被攻击是可见的。可接受。
- **token 机密性**：E2E X25519+AES-GCM，服务器仅转密文、不持久。
- **放行鉴权**：`/complete` 强制 `callerScope===releasingScope`，只有真主人已鉴权的设备能放行。
- **乱撒前缀骚扰**：被 16^6≈1677 万空间稀释 + start 限流（IP+token）+ `MAX_MATCH` 截断；命中陌生人时仅弹一张可忽略的卡（也是预警）。
- **已知信任边界（v1 接受）**：`pubkey` 经服务器转发，恶意服务器理论上可替换公钥做 E2E 中间人。当前服务器是自有 Worker + TLS，接受此信任。**未来加固**：把 4 位码从 `hash(pubkey‖transcript)` 派生（SAS 短认证串），公钥被换则两端码不一致——本期不做，记为后续。

## 8. 错误与边界

| 情况 | 行为 |
|---|---|
| `no_match` | 新设备：「没找到这个 ID，确认老设备设置页的 6 位码」 |
| 验证码错 | 「验证码不对，还可试 N 次」；耗尽 → 「已失效，请重新发起」 |
| 2 分钟过期 | 两端优雅复位 |
| 老设备离线 | 推不到 → 新设备等待超时 → 「没有设备响应，确认老设备已开机且联网」（方案固有限制） |
| 多账号命中 | 各老设备显示各自不同码；输入的码精确路由到一个；其余卡过期/忽略 |
| 老设备「不是我」 | cancel → 新设备显示「对方已拒绝」 |
| 老设备 app 后台/被杀 | StatusHub hibernation；socket 断则收不到推送。happy path 中老设备应在前台。 |

## 9. 测试计划

改动前后都跑（项目约定）：`cd ~/code/jianshuo.dev/agent && npm test`。

- **`LinkBroker` DO 单测**：create / verify(对、错、次数耗尽、过期) / complete(scope 闸门、blob 中转) / cancel。
- **路由测试** `/agent/link/*`：mock `env.FILES.list` 验前缀解析；mock StatusHub 断言 `link_request`/`link_release` 的 `payload`；mock LinkBroker。
- **StatusHub 泛化广播**：断言 `status_update` 向后兼容 **且** 任意 `payload` 透传。
- 跑完整 `npm test` 确认无回归。
- **iOS 手测**（两台设备/模拟器）：happy path、验证码错、过期、no_match、「不是我」。

## 10. 部署

- **Worker**：`wrangler.jsonc` 加 `LinkBroker` 的 durable_objects 绑定 + migration（`new_sqlite_classes`/`new_classes`）；`cd ~/code/jianshuo.dev/agent && npx wrangler deploy`。
- **iOS**：加 `DeviceLink.swift` 后跑 `xcodegen`；推 `main` → GitHub Actions → TestFlight。
- **Pages**：无改动（所有新路由在 agent Worker；共享的 `functions/lib/auth.js` 不变）。Worker 已引用 `anonScopeFromToken`/`verifySession`（现有 `/agent/status` 鉴权即用），复用即可。

## 11. 未来扩展：CLI / headless 客户端登录（方案 A，本期不实现）

目标：以后做一个 skill，让 Claude Code / 命令行以「你」的身份直接操作 VoiceDrop 账号（例如自动挖文章），
不再用 `~/code/.env` 里的管理员 `FILES_TOKEN`（全量），而是登录到**某一个具体用户**。

已定方向 = **方案 A：把账号本体 `anon_…` 整把复制到机器上**（同一把全权令牌，简单够用，**不可吊销**——
代价见 §7。若以后要可吊销的"分身"，需另起方案 B：服务器存令牌 + app 签发命名 app 令牌 + 吊销 UI）。

**关键性质：服务器零改动。** CLI 只是扮演协议里通用的「新设备」角色，server 端（§4）已经全覆盖。只有两处纯客户端适配：

1. **可移植加密**：把 §5 的 X25519+AES-GCM 用 node WebCrypto / python `cryptography` 实现一遍（标准件），
   blob 结构与常量（`salt="voicedrop-device-link/v1"`、`info="anon-token"`）与 iOS 完全一致。
2. **start/verify 的限流鉴权**：CLI 本地随手生成一个一次性 `anon_<随机≥15 字符>` 当 bearer 发 start/verify
   （`anonScopeFromToken` 接受任何 `anon_` 开头、长度 ≥20 的串，且此处 scope 本就不参与）——**无需 server 放宽**。

**流程**（CLI = 新设备；4 位码读手机、粘终端）：

```
CLI: 生成 X25519 keypair + 一次性 anon bearer
  → POST /agent/link/start {prefix:<你的6位>, pubkey}   (Bearer: 一次性 anon)
你的手机弹「验证码 1234」
  → 你把 1234 粘进终端
CLI → POST /agent/link/verify {pairingId, code:"1234"}
手机收 link_release → 加密本体 anon_… → POST complete
CLI ← link_ready{blob}（经 socket）→ 解密 → 得到本体 anon_…
  → 存到 ~/.config/voicedrop/credentials（chmod 600，或 macOS 钥匙串）
之后 CLI 用这把 anon_… 调 Files API，以你的身份干活。
```

**存储卫生（因为方案 A 不可吊销）**：`~/.config/voicedrop/` 文件 `0600`；优先塞 macOS 钥匙串
（`security add-generic-password`）；**绝不**进 dotfiles 公开仓库、绝不同步到任何公共可读位置。

**实现顺序**：iOS 设备配对（§1–§10）先落地、跑通、上 TestFlight；CLI 客户端作为**下一个独立 skill 项目**，
复用同一组 `/agent/link/*` 路由，单独 spec + 单独实现计划。
