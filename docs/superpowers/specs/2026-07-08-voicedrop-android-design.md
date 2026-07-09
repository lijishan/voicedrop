# VoiceDrop Android 版 — 设计文档

**日期**: 2026-07-08
**版本**: 1.0

## 概述

将 VoiceDrop iOS 应用完整移植到 Android 平台，以 iOS 版本的功能、接口作为标准。服务端 API 已是通用 REST + WebSocket，无需改动。

## 技术栈

| 层 | 选型 | 对标 iOS |
|---|---|---|
| 语言 | Kotlin 2.0+ | Swift 6.0 |
| UI | Jetpack Compose + Material3 | SwiftUI |
| 状态管理 | `mutableStateOf` + `CompositionLocal` | `@Observable` + `@Environment` |
| 网络 (REST) | OkHttp 4.x | URLSession |
| 网络 (WS) | OkHttp WebSocket | URLSessionWebSocketTask |
| JSON | Gson | Codable |
| 安全存储 | EncryptedSharedPreferences (AndroidX Security) | iCloud Keychain |
| 音频录制 | MediaRecorder (AAC/M4A) | AVAudioRecorder |
| 流式音频 | AudioRecord (PCM streaming to ASR) | AVAudioEngine + installTap |
| 音频播放 | ExoPlayer (Media3) | AVAudioPlayer |
| 后台上传 | WorkManager | UIBackgroundTaskIdentifier |
| 图片加载 | Coil | AsyncImage |
| 图片处理 | Bitmap + Matrix (center crop, scale) | UIImage + CoreGraphics |
| 导航 | Compose Navigation (single-Activity) | NavigationStack |
| 构建 | Gradle KTS + Version Catalog | XcodeGen project.yml |
| 最低 SDK | API 26 (Android 8.0) | iOS 18.0 |

## 项目结构

```
android/
├── build.gradle.kts            # 根构建 (AGP + Kotlin 插件版本)
├── settings.gradle.kts         # 模块声明 + maven repos
├── gradle.properties           # JVM + Android 编译属性
├── gradle/
│   └── libs.versions.toml      # Version Catalog
└── app/
    ├── build.gradle.kts        # app 模块构建
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── kotlin/com/wangjianshuo/voicedrop/
        │   │   ├── VoiceDropApp.kt          # Application class (DI 容器)
        │   │   ├── MainActivity.kt           # 单一入口 Activity
        │   │   ├── AppRouter.kt              # Deep link 路由
        │   │   ├── Theme.kt                  # 颜色/字体/形状
        │   │   ├── Formatting.kt             # 日期格式化
        │   │   ├── Model.kt                  # 全部数据模型
        │   │   ├── ArticleBody.kt            # 正文 photo marker 解析
        │   │   ├── RecordingName.kt          # 文件名构造/解析
        │   │   ├── Networking.kt             # API 基础 + HTTP 客户端
        │   │   ├── Auth.kt                   # 匿名 token + 安全存储
        │   │   ├── Uploader.kt               # 后台上传队列
        │   │   ├── AudioRecorder.kt          # MediaRecorder 封装
        │   │   ├── VoiceEdit.kt              # PCM 流式录音 + ASR WS
        │   │   ├── VolcASRProtocol.kt        # 火山 ASR 二进制协议
        │   │   ├── RecordingPromoter.kt      # staging→正式文件名
        │   │   ├── StatusSession.kt          # 挖矿状态 WS
        │   │   ├── AgentSession.kt           # 文章编辑 WS Agent
        │   │   ├── VoiceAgentSession.kt      # VoiceAgent 公共协议
        │   │   ├── LibraryCommandSession.kt   # 库级语音指令 WS
        │   │   ├── EditQueueStore.kt          # 编辑队列持久化
        │   │   ├── CommandQueueStore.kt       # 指令队列持久化
        │   │   ├── Library.kt                # LibraryStore: 录音列表
        │   │   ├── LibraryView.kt            # 首页 (Tab: 我的录音/VD社区)
        │   │   ├── RecordSession.kt          # 全屏录音 takeover
        │   │   ├── RecordingDetailView.kt     # 文章详情页
        │   │   ├── PushToTalkBar.kt          # 按住说话条
        │   │   ├── FollowupQuestions.kt      # 追问逻辑 + UI
        │   │   ├── Community.kt              # 社区 Store + PostView
        │   │   ├── SettingsView.kt           # 设置页
        │   │   ├── UsageView.kt              # 算力余额
        │   │   ├── AccountView.kt            # 账户管理
        │   │   ├── PhotoCapture.kt           # 系统相机
        │   │   ├── PhotoService.kt           # 照片上传
        │   │   ├── UIConfigStore.kt          # UI 配置缓存
        │   │   ├── ConfigMenu.kt             # 长按操作菜单
        │   │   ├── PhotoTile.kt              # 图片块组件
        │   │   ├── PlayerBar.kt              # 音频播放条
        │   │   ├── ShareSheet.kt             # 系统分享
        │   │   ├── WeChatPublish.kt          # 公众号发布
        │   │   ├── ShareIntake.kt            # 接受分享 (ACTION_SEND)
        │   │   └── ShareIntakeCompose.kt     # 分享接收 Compose UI
        │   └── res/
        │       ├── values/
        │       │   ├── strings.xml
        │       │   └── themes.xml
        │       └── drawable/ (icons)
        └── test/   (单元测试)
```

## 架构

### 状态管理层：iOS `@Observable` → Compose 映射

```
iOS                              Android
─────────────────────────────────────────
@Observable class LibraryStore   →  class LibraryStore {
                                      var recordings by mutableStateOf(...)
                                    }
                                    + CompositionLocal 注入

@Environment(Store.self)         →  val store = LocalLibraryStore.current
URLSessionWebSocketTask          →  OkHttp WebSocket
@AppStorage                      →  SharedPreferences / EncryptedSharedPreferences
FileManager.documentsDirectory   →  context.filesDir
```

### 单一 Activity 架构

```
MainActivity
  └── NavHost
       ├── LibraryView (startDestination)
       │    ├── tab: 我的录音
       │    └── tab: VD社区
       ├── RecordingDetailView(stem)
       ├── SettingsView
       ├── RecordSession (全屏 overlay)
       └── ShareIntakeCompose (ACTION_SEND 入口)
```

### Store 生命周期

每个 Store 是普通 Kotlin 类（非 Android ViewModel），在 `VoiceDropApp` 中创建并持有。通过 `CompositionLocal` 注入到 Compose 树。对标 iOS 的 `@State` + `@Environment` 模式，`mutableStateOf` 属性变化自动触发 Compose 重组。

### 进程间共享

Android 的分享接管用单一 Activity + `intent-filter ACTION_SEND`，ShareIntake 直接读 intent 数据，不需要单独的进程/模块。

## 网络层

### HTTP 客户端 (Networking.kt)

```kotlin
object API {
    const val HOST = "jianshuo.dev"
    const val FILES_BASE = "https://$HOST/files/api"
    const val AGENT_BASE = "https://$HOST/agent"
    const val RECO_BASE  = "https://$HOST/reco"

    fun wsEdit(stem: String) = "wss://$HOST/agent/edit?stem=$stem"
    fun wsCommand()          = "wss://$HOST/agent/command"
    fun wsStatus()           = "wss://$HOST/agent/status"
    fun wsAsr()              = "wss://$HOST/agent/asr"
}

class HttpClient(private val auth: AuthStore) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, SECONDS).readTimeout(60, SECONDS).build()

    suspend inline fun <reified T> get(path: String): T
    suspend inline fun <reified T> post(path: String, body: Any? = null): T
    suspend fun put(path: String, body: ByteArray): Response
    suspend fun delete(path: String): Response
    fun webSocket(url: String): WebSocket
}
```

所有请求 Bearer 头由 `HttpClient` 统一注入。

### WebSocket 消息协议（同 iOS）

```json
// Client → Server
{"type":"instruct","id":"...","text":"...","articleIndex":0}

// Server → Client
{"type":"updated","doc":{...}}
{"type":"reply","id":"...","text":"已完成"}
{"type":"error","id":"...","message":"..."}
{"type":"status_update","stem":"...","status":"ready"}
```

## 数据模型 (Model.kt)

全量对标 iOS，字段和 Model.kt 定义中一致（见上文第四节设计）。关键模型：
- `ArticleDoc` — schema v3 (articles + versions + head + questions)
- `MinedArticle` — per-article (title, body, style, wechatMediaId)
- `Recording` — 录音条目 (audioName, phase, tags)
- `CommunityPost` / `CommunityFullPost` — 社区
- `FollowupQuestion` — 追问 sidecar

## Auth + 安全存储

### 流程（对标 iOS）

1. 首次启动 → `AuthStore.init()` 从 EncryptedSharedPreferences 读已存 token
2. 无 token → 生成 `anon_<32hex>` = `"anon_" + sha256(randomBytes)[:32]`
3. 存到 EncryptedSharedPreferences (对标 Keychain)
4. 发给服务器 `GET /whoami` 验证 scope
5. 所有后续请求带 `Authorization: Bearer <anon_token>`

```kotlin
class AuthStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(...)
    var anonToken: String
    var scope: String?  // users/<sub>/
}
```

对标 iOS 的 `KeychainStore` + `AppleAuth` 的匿名 token 路径（iCloud Keychain → EncryptedSharedPreferences）。

## 录音 + 上传流程

### 完整对标 iOS

```
1. RecordSession: 全屏 takeover，点录制键 → AudioRecorder 开始录音
2. 录音存 staging 名: recording-<ts>.m4a (不触发上传)
3. 松手 → RecordingPromoter.rename() → VoiceDrop-<ts>-<dur>-<weekday>-<period>.m4a
4. 文件落盘到 filesDir/ → Uploader.drainPending() 探测到新文件
5. Uploader 走 WorkManager 后台上传: PUT /files/api/upload/<key>
6. 上传完成 → 状态切 uploaded
7. 服务端 dispatchMine → 挖矿 → StatusSession 推送 phase 变化
8. ListView 展现: 待处理 → 听录音 → 挖文章 → 已成文
```

### MediaRecorder 配置（对标 iOS `Prefs.recorderSettings`）

```kotlin
val recorderSettings = mapOf(
    MediaRecorder.AudioSource.MIC,
    OutputFormat.MPEG_4,
    AudioEncoder.AAC,
    16000,  // sample rate (标准) / 24000 (高)
    32000,  // bit rate (标准) / 64000 (高)
    1       // mono
)
```

## VoiceEdit: 火山流式 ASR

对标 iOS `VoiceEdit.swift` + `VolcASRProtocol.swift`：

1. `AudioRecord` 取 PCM 16bit/mono/16kHz microphone
2. VolcASRProtocol 二进制协议: header(4B) + payload(gzip flags=0x01) → 带序列号分包
3. WebSocket 连接到 `/agent/asr` (服务端代理，不暴露密钥)
4. 服务端返回文本 → callback 到 UI 显示实时转写

## 功能清单

| 功能 | iOS 文件 | Android 文件 | 状态 |
|------|---------|-------------|------|
| App 入口 | VoiceDropApp.swift | VoiceDropApp.kt | must |
| 路由 | AppRouter.swift | AppRouter.kt | must |
| 首页 | LibraryView.swift + Library.swift | LibraryView.kt + Library.kt | must |
| 录音全屏 | RecordSession.swift | RecordSession.kt | must |
| 文章详情 | RecordingDetailView.swift | RecordingDetailView.kt | must |
| 按住说话 | PushToTalkBar.swift | PushToTalkBar.kt | must |
| 语音编辑 (WS) | AgentSession.swift | AgentSession.kt | must |
| 流式 ASR | VoiceEdit.swift + VolcASRProtocol.swift | VoiceEdit.kt + VolcASRProtocol.kt | must |
| 挖矿状态 | StatusSession.swift | StatusSession.kt | must |
| 追问 | FollowupQuestions.swift | FollowupQuestions.kt | must |
| 社区 | Community.swift | Community.kt | must |
| 设置 | SettingsView.swift | SettingsView.kt | must |
| 算力 | UsageView.swift | UsageView.kt | must |
| 公众号发布 | Networking.swift (wechat 端点) | WeChatPublish.kt | must |
| 风格 | SettingsView.swift (style editor) | SettingsView.kt 内嵌 | must |
| 长按菜单 | ConfigMenu.swift + UIConfigStore.swift | ConfigMenu.kt + UIConfigStore.kt | nice |
| 语音指令 | LibraryCommandSession.swift | LibraryCommandSession.kt | nice |
| 设备配对 | DeviceLink.swift | (暂缓) | later |
| 导出 | ExportManager.swift | (暂缓) | later |
| 分享入口 | Share Extension (独立 target) | ShareIntake.kt (单模块内) | nice |

## 测试策略

- **单元测试**: JUnit5 + MockK (对标 vitest)。测试 Model 解析、RecordingName 构造/解析、VolcASRProtocol 分包、Auth token 生成
- **集成测试**: OkHttp MockWebServer 测试 API 请求/响应
- **UI 测试**: Compose Testing 测试页面渲染（暂缓）
