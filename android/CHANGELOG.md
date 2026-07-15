# VoiceDrop Android 开发日志

## 2026-07-15 — 下拉刷新 + App 图标 & 多项打磨

### 下拉刷新（瀑布流）
- **CommunityFeedView.kt**: `nestedScroll(nestedConn)` 挂在 LazyColumn 上，`onPreScroll` + `onPostScroll` 在 `listState` 顶部下拉时捕获 `available.y > 0`，累积 pullOffsetPx → `Modifier.offset` 推动内容下移 + 顶部 `CircularProgressIndicator` 按进度增长 → 松手 80dp 阈值触发 `store.refresh()`

### 社区详情页修复
- **Community.kt**: `CommunityPostView` 添加 `Scaffold` + `TopAppBar`（回退按钮 + 状态栏安全区）+ `verticalScroll` 可滚动阅读；移除旧的 `CommunityList`（已由 CommunityFeedView 替代）

### App 图标
- 从 iOS `icon-1024.png` 生成各密度 mipmap + 自适应图标（红底 + iOS 图标前景）

### 提示词分享码
- **Model.kt**: 新增 `ShareState` / `ShareStatesResponse`
- **LibraryStore.kt**: 新增 `fetchShareStates()` / `setSharing()` API
- **SettingsView.kt**: 新增「提示词分享」卡 — Toggle 开关 + 7 位码 + 链接 + 复制/分享

## 2026-07-14 — 社区瀑布流 + 长按菜单 & 稳定性修复

### Code Review 修复（CRITICAL）
- **RecordingDetailView.kt**: `doc!!` 强制解包 → `val d = doc` 安全调用 + `DisposableEffect` 释放 AgentSession（对标 crash-risk 审计项）
- **AgentSession.kt**: `mutableListOf` → `ArrayDeque` + `Mutex` 保护编辑队列线程安全
- **RealtimeSession.kt**: 裸 `Thread` 重连 → `CoroutineScope` + `launch` + `delay`；`disconnect()` 增加 `generation.incrementAndGet()` 防旧 WS 回调
- **Library.kt**: `articleTitle!!` → 局部 `val title` 安全赋值

### Code Review 修复（MEDIUM）
- **Networking.kt**: 所有 `get/post/put/delete/patch` 加 `.use {}` 关闭 response body 防连接泄漏
- **RealtimeInterviewer.kt**: 裸 `Thread`(300ms AI 播放后恢复上行) → `CoroutineScope.launch`
- **AudioRecorder.kt + VoiceEdit.kt**: `e.printStackTrace()` → `Log.e()`
- **LibraryStore.kt**: 新增 `release()` 取消 scope
- **RealtimeInterviewer.kt**: 修复 init/toggleInterview 缩进不一致

### 功能
- **社区瀑布流** (3 新文件): `CommunityFeedView.kt` (双排 masonry 贪心布局 + 推荐/最新/回应 tab + PhotoCard/TextCard + FeedMetaRow)、`Community.kt` 改用 `/reco/feed` 端点、`Model.kt` 新增 `CommunityFeedResp`
- **长按操作菜单** (2 新文件): `LongpressMenuOverlay.kt` (自绘覆盖层：暖纸菜单卡 + 二级子菜单导航 + scrim)、`MenuConfig.kt` (硬编码：图片风格 6 项 / 改写 4 项 / 公众号题图 + 拷贝)

### UI / 打磨
- **LibraryView.kt**: 移除硬编码 "VoiceDrop 口述" / "下拉刷新" → `stringResource()`

## 2026-07-09 — 上游功能复刻 & 体验打磨

### 上游 iOS 功能复刻
- **VoiceEdit 错误可见** (`VoiceEdit.kt`, `PushToTalkBar.kt`): dictation 故障不再静默。lastError 优先显示, PushToTalkBar 红色渲染错误文本（对标 `0550b9c`）
- **Library HTTP 风暴修复** (`Library.kt`): 文章标题磁盘缓存 (SharedPreferences) + Semaphore(5) 并发限制。冷启动从 10+ HTTP → 0~2 HTTP（对标 `e54eb66`）
- **AI 采访员** (3 新文件): `EngineRecorder.kt` (PCM 旁路采集), `RealtimeInterviewer.kt` (开关/半双工/断线重连), `RealtimeSession.kt` (WS relay)。录音页停止键左侧 Forum 按钮一键开关（对标 `bddcaa9`, `a3e4fa1`, `e869d6e`）
- **分享域名**: 服务器已处理，无需客户端改动（对标 `bffcaaa`）

### UI / UX 打磨
- **分享合并**: 顶栏一个分享图标 → 三选一弹窗（分享链接/VD社区/公众号草稿）
- **录音按钮**: 纯红圆 + 浅灰描边 + 半透明白环 (Icons.Outlined.Circle)
- **详情页**: 标题下移内容区, 日期后加作者名
- **社区详情**: 标题优先 → 作者 · 日期 → 正文
- **下拉刷新**: graphicsLayer + animateFloatAsState(spring) 自定义弹簧动画

### 数据修复
- **社区列表**: `CommunityPost.replyTo` 改为 `Any?` 兼容字符串/对象两种格式
- **社区列表**: `List<CommunityPost>` → `CommunityListResp` 包装类修复 JSON 解析
- **新录音插入**: `addLocalRecording()` 服务器同步延迟 3s 等待 Uploader
- **WECHAT/CLAUDE 保存**: 上传 key 去掉 scope 前缀重复

### 基础设施
- **单元测试**: 14 个用例 (ModelParse, RecordingName, CommunityStore)
- **i18n**: 60+ 字符串提取到 `strings.xml`, 全量 `stringResource()` 引用
- **Code Review**: `!!` 空指针加固 4 处, 空 catch 加 `Log.w` 23 处

## 2026-07-08 — 项目初始化 & 核心功能验证通过

### 项目骨架
- Gradle KTS 构建系统 (Kotlin 2.0, Compose BOM 2024.05, minSdk 26)
- Version Catalog 依赖管理 (`libs.versions.toml`)
- 单一 Activity + Compose Navigation 架构
- Material3 主题 (VDTheme 配色)

### 数据层
- **模型** (`Model.kt`): ArticleDoc, MinedArticle, Recording, CommunityPost 等 20+ 数据类
- **状态管理**: `mutableStateOf` + `CompositionLocal` Store 模式，镜像 iOS `@Observable`
- **Auth** (`Auth.kt`): 匿名 token 生成 (sha256), EncryptedSharedPreferences 安全存储
- **网络** (`Networking.kt`): OkHttp REST 客户端 (GET/POST/PUT/DELETE/PATCH) + WebSocket

### 录音 & 上传
- **AudioRecorder**: MediaRecorder AAC/M4A, 16kHz/32kbps mono, 兼容 API 26+
- **RecordingName**: 文件名构造/解析 (VoiceDrop-<ts>-<dur>-<weekday>-<period>.m4a)
- **RecordingPromoter**: staging → 正式文件名, Files.move 原子操作
- **Uploader**: 后台上传队列, retry backoff, drain 序列化
- `AudioRecorder` MediaRecorder 构造器兼容低版本 Android (API 26-30)
- `RecordingPromoter.promote()` 改用 `Files.move()` 替换不可靠的 `File.renameTo()`

### 列表 & 状态
- **LibraryView**: 我的录音 / VD社区双 Tab, 状态徽标 (待处理/听录音/挖文章/已成文/无语音)
- **LibraryStore**: 本地文件优先 → 服务器异步同步, 删除乐观更新
- **StatusSession**: WebSocket 挖矿状态实时推送 (asr/mining/ready/empty)
- 服务器 list API 返回 R2 格式 `{"files": [{"name": ...}]}` 适配
- 服务器 list key 带路径前缀, `substringAfterLast("/")` 提取纯文件名
- `createdAt` 等时间字段从 `Long` 改为 `Any?` 兼容 ISO date string 和 epoch number

### 录音 & 详情
- **RecordSession**: 全屏 Dialog takeover, 权限申请, 录音计时
- **RecordingDetailView**: 已挖矿文章展示 / 待处理状态页 / 无语音状态页
- **ArticleBody**: `[[photo:KEY]]` marker 解析, 正文/图片混排渲染
- **PendingStateView**: 「立即处理」按钮 → `POST /files/api/mine` 触发挖矿
- 详情页 when 分支优先级: doc 加载成功 > 无语音 > 待处理

### 删除
- 乐观删除 (立即从列表移除, 避免等待网络)
- 服务器端删除: 用 `Recording.serverKey` 匹配上传时的真实 key
- Uploader 路径: key 去掉多余的 `"upload/"` 前缀, 统一为纯文件名
- 每个 delete 调用独立 try/catch, 不因第一条失败阻塞后续

### 语音编辑 (代码已写, 待真机验证)
- **VoiceEdit**: AudioRecord PCM 16bit/mono/16kHz 流式录音
- **VolcASRProtocol**: 火山 ASR 二进制协议 (gzip 分帧 + 4字节 header)
- **PushToTalkBar**: 按住说话手势 UI
- **AgentSession**: WebSocket 文章编辑 Agent (OkHttp WS)
- **EditQueueStore**: 编辑队列持久化

### 社区 & 追问 (代码已写, 待真机验证)
- **CommunityStore**: 社区列表 / 帖子详情 / Like / 举报 / 排名
- **CommunityList / CommunityPostView**: 社区 Compose UI
- **FollowupCard / FollowupBadge**: 追问卡片 + 角标

### 设置 (代码已写, 待真机验证)
- **SettingsView**: 名字 / 文风 / 公众号 AppID+AppSecret / 账户
- **UsageView**: 算力余额 + 明细
- **AccountView**: 账号删除
- **WeChatPublish**: 公众号草稿发布

### 分享接收 (代码已写, 待真机验证)
- **ShareIntake**: ACTION_SEND intent filter, 文字/图片/音频接收

### 已知问题
- 模拟器: Intel Mac 上 ANGLE 不兼容, 无法启动。真机测试无此问题
- 服务器挖矿需手动触发: 录音后点「立即处理」按钮或等 6 小时 cron
- pull-to-refresh: 自定义弹簧动画可用, 但 Material3 PullToRefreshBox 需 BOM >= 2024.12.00

### 待后续
- 语音编辑真机验证 (PushToTalk / ASR / AgentSession)
- AI 采访真机验证 (EngineRecorder / RealtimeInterviewer / RealtimeSession)
- Push 通知 (FCM)
- i18n 英文翻译
- 下拉刷新
- 语音指令 (LibraryCommandSession)
- 设备配对 (DeviceLink)
- 长按操作菜单 (ConfigMenu)
- 单元测试
