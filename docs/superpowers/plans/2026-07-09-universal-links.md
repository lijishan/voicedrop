# Universal Links — voicedrop.cn 链接直接拉起 VoiceDrop App

日期：2026-07-09 · 状态：计划（未实施）

## 目标

- `https://voicedrop.cn/`（落地页）、`https://voicedrop.cn/<分享id>`（文章/社区分享短链）等常用链接，装了 App 的 iPhone 上点开直接进 VoiceDrop 对应页面；没装 App 照常打开网页。
- 旧分享链接 `https://jianshuo.dev/voicedrop/<token>` 一并生效。
- 讲清边界：**微信内点链接不会拉起 App**（微信禁用 universal link，需另接微信开放标签，列为可选 Phase 5）。

## 现状盘点（2026-07-09 已核实）

| 项 | 现状 |
|---|---|
| Bundle / Team | `com.wangjianshuo.VoiceDrop` / `97XBW2A43H` → AASA appID = `97XBW2A43H.com.wangjianshuo.VoiceDrop` |
| 深链 | 已有 `voicedrop://` scheme + `AppRouter.swift`（recordings/community/settings/record/article/<stem>），`VoiceDropApp.swift` `.onOpenURL` 接入 |
| entitlements | `VoiceDropApp/VoiceDrop.entitlements` **无** associated-domains |
| 签名 | fastlane match 手动签名；已有 `refresh_profiles` lane（2026-07-08 加 Push capability 时用过同一套流程） |
| 分享链接 | App `Networking.sharePage` → `https://voicedrop.cn/<id>`（文章 10 位 / 社区 12 位）；服务端 mint 在 files API `share` 端点 |
| voicedrop.cn | 腾讯云 49.235.147.96 Caddy 反代 → CF Pages `jianshuo-dev`；路由：`/files/*` 透传、`/voicedrop/*` 301 去前缀、其余**补前缀**取 `jianshuo.dev/voicedrop/*` |
| AASA 现状 | `voicedrop.cn/.well-known/apple-app-site-association` → **404**（实测）；`jianshuo.dev` 同样 404 |
| shareId 解析 | **无** app 可用的 resolve API——只有 `functions/[token].js`/`voicedrop/[token].js` 服务端渲染 HTML |
| ⚠️ 发现的坑 | `~/code/jianshuo.dev/infra/voicedrop-cn/Caddyfile` 是 **0 字节空文件**，README 声称「本目录是全部真相可 10 分钟重建」不成立——真配置只在线上机器。本次一并回填 |

## Phase 1 — 服务端：AASA 文件（先行，无风险）

repo：`~/code/jianshuo.dev`

1. 新增静态文件（两份内容分开）：
   - `voicedrop/.well-known/apple-app-site-association` —— 经 Caddy 补前缀规则映射为 `voicedrop.cn/.well-known/...`：
     ```json
     {"applinks":{"details":[{"appIDs":["97XBW2A43H.com.wangjianshuo.VoiceDrop"],
       "components":[
         {"/":"/files/*","exclude":true},
         {"/":"/privacy/*","exclude":true},
         {"/":"/*"}
       ]}]}}
     ```
     覆盖策略：整站进 App（落地页 `/`、分享短链 `/<id>`、`/help/` 等），只排除 `/files/*`（API/图片）和 `/privacy/*`（审核要求网页可读）。App 端对认不出的路径兜底开 in-app Safari，不会死链。
   - 根 `.well-known/apple-app-site-association` —— 给 jianshuo.dev 旧链接，components 只含 `{"/":"/voicedrop/*"}`（jianshuo.dev 上其他业务不受影响）。
2. `_headers` 加两条：`Content-Type: application/json`（Pages 对无扩展名文件默认 octet-stream，Apple 虽宽容但别赌）。
3. 部署：`npx wrangler pages deploy . --project-name jianshuo-dev --branch main`（**必须带 --branch main**，见 STATE.md 部署坑）。
4. 验证：
   - `curl -i https://voicedrop.cn/.well-known/apple-app-site-association` → 200、application/json、**无 301/302**（Caddy 是 rewrite 反代不是跳转，应当没问题；若发现 301 则转 Phase 2 在 Caddy 加 handle）。
   - `curl -i https://jianshuo.dev/.well-known/apple-app-site-association` → 同上。
   - Apple CDN 视角：`curl https://app-site-association.cdn-apple.com/a/v1/voicedrop.cn`（可能要等几分钟～几小时刷新）。

## Phase 2 — 腾讯云 Caddy 治理（顺手修坑）

需要 ssh `ubuntu@49.235.147.96`（本次会话被权限拦，需用户放行或自己跑）。

1. 拉线上 `/etc/caddy/Caddyfile` 回填 repo 空文件，让 README 的「10 分钟重建」承诺重新成立。
2. 确认 `/.well-known/*` 走补前缀规则正常透传且无跳转；如有问题加一条显式 `handle /.well-known/apple-app-site-association` 反代（不要用 `respond` 内嵌 JSON——内容真源保持在 Pages，一处维护）。
3. 提交 repo。

## Phase 3 — iOS 工程：entitlement + 签名

repo：`~/code/voicedrop`

1. `VoiceDropApp/VoiceDrop.entitlements` 加：
   ```xml
   <key>com.apple.developer.associated-domains</key>
   <array>
     <string>applinks:voicedrop.cn</string>
     <string>applinks:www.voicedrop.cn</string>
     <string>applinks:jianshuo.dev</string>
   </array>
   ```
   （Share Extension 不需要。真机开发调试期可临时用 `applinks:voicedrop.cn?mode=developer` 绕过 Apple CDN 缓存，发布前去掉 `?mode=developer`。）
2. Apple Developer portal：App ID `com.wangjianshuo.VoiceDrop` 勾 Associated Domains capability → 旧 profile 会被置 Invalid → 跑现成的 `bundle exec fastlane refresh_profiles`（和 2026-07-08 加 Push 完全同一套流程；Fastfile 里 match `readonly:false` 的注释同样适用）。
3. `xcodegen generate`。

## Phase 4 — App 端路由：https 链接进 AppRouter

1. **服务端加一个公开 resolve API**（`functions/files/api/[[path]].js`）：
   `GET /files/api/link/<id>`（无鉴权——返回的信息本来就在公开 HTML 页上）→
   `{type:"article"|"community", owner:"users/<sub>/", stem, shareId}`；
   shares/<id> 未命中回落 community/<id>.json（照 `[token].js` 的既有双查逻辑）；被举报帖 404。
2. **iOS `AppRouter.swift` 扩展**：`handle(_:)` 识别 https URL：
   - host ∈ {voicedrop.cn, www.voicedrop.cn} 或 jianshuo.dev 且 path 前缀 `/voicedrop/`；
   - `/` → 首页（我的录音）；
   - `/<id>` → 调 resolve API：owner == 本人 `whoami` scope → 打开自己文章详情（`?s=<i>` 段参数 → 对应篇）；type == community → 打开社区帖子视图（复用 CommunityPostView，按 shareId）；解析失败/其他 → in-app SFSafariViewController 兜底；
   - 其他路径（/help/ 等）→ in-app Safari 兜底。
   - SwiftUI 里 universal link 经 `.onOpenURL` 同一入口进来（scene-based SwiftUI 会把 NSUserActivity 转投）；保险起见同时挂 `.onContinueUserActivity(NSUserActivityTypeBrowsingWeb)` 走同一个 `router.handle`。
   - 保持既有守卫：**录音进行中丢弃深链**（STATE.md 2026-07-08 的行为，不动）。
3. **分享页加 Smart App Banner**（`functions/voicedrop/[token].js` 的 HTML `<head>`）：
   `<meta name="apple-itunes-app" content="app-id=<AppStore数字id>, app-argument=https://voicedrop.cn/<id>">`
   ——这是微信「在 Safari 中打开」后能一键进 App 的关键：Safari 里**同域名页内点链接不触发** universal link，唯有系统横幅可靠。App Store 数字 id 从 App Store Connect 取。
4. 测试：`agent/test/` 加 resolve API 用例；iOS 手测矩阵见 Phase 6。

## Phase 5（可选，另立项）— 微信内拉起 App

微信内置浏览器屏蔽 universal link。要在微信里一键进 App，需微信开放平台「开放标签」`wx-open-launch-app`：认证服务号 + JS-SDK 签名 + 开放平台把公众号与 App 关联。已有微信开放平台 App（安卓微信登录在用 WECHAT_OPEN_APP_ID），具备前置条件，但涉及服务号 JS 签名后端，工作量独立评估。**本期不做**，分享页顶部横幅文案引导「点右上角 ⋯ → 在 Safari 中打开」即可。

## Phase 6 — 上线顺序与验证

顺序（严格）：Phase 1 服务端 AASA → Phase 2 Caddy 核实 → Phase 3 entitlement + profile → Phase 4 合入 → push main → TestFlight。服务端先行无副作用；App 后行，装机时 iOS 才去 CDN 取 AASA。

真机验证矩阵（TestFlight build）：
- 备忘录/短信里贴 `https://voicedrop.cn/<自己文章的分享id>` → 点击直接进 App 文章详情；
- 社区帖分享链接 → 进社区帖子视图；
- `https://voicedrop.cn/` → 进 App 首页；
- 旧链接 `https://jianshuo.dev/voicedrop/<token>` → 进 App；
- 未装 App 的设备/长按链接选「在 Safari 打开」→ 网页照常；
- 微信内点链接 → 停留 H5（预期），「在 Safari 打开」后见 Smart App Banner → 点「打开」进 App；
- 录音进行中点链接 → 被丢弃不打断录音。

排障备忘：设置→开发者→Universal Links 诊断（iOS 16+）；`swcutil`（macOS 模拟器）；AASA 变更后重装 App 或等 CDN 刷新；**在 Safari 地址栏手输 URL 不触发**（必须是点击的链接），同域名页内跳转不触发。

## 风险

- **Apple CDN 取不到 voicedrop.cn**：Apple CDN 从海外拉源站，腾讯云机器国际链路一般可达但无 SLA；万一不可达，备选 = Caddy 对 `/.well-known/` 单独稳定服务 + 观察 `app-site-association.cdn-apple.com/a/v1/voicedrop.cn`。现有 voicedrop-agent 5 分钟探活可加一条 AASA 路径监控。
- **`/*` 全站声明**：Safari 里浏览 voicedrop.cn 会常驻「在 App 中打开」顶栏，网页党可能觉得烦——iOS 用户可长按回退，接受。
- **profile 重签**：capability 变更会再次 invalidate 全部 profile，CI 第一次构建前必须先跑 refresh_profiles（7-08 Push 已趟过一遍）。
