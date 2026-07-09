# VoiceDrop Android 开发日志

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
- 模拟器: Intel Mac 上 ANGLE 不兼容, 无法启动 (Failed to restore previous context: 12297)。真机测试无此问题
- 服务器挖矿需手动触发: 录音后点「立即处理」按钮或等 6 小时 cron
- 列表刷新需切 tab 触发, 没有下拉刷新手势

### 待后续
- 语音编辑真机验证 (PushToTalk / ASR / AgentSession)
- 社区 & 追问真机验证
- 下拉刷新
- 语音指令 (LibraryCommandSession)
- 设备配对 (DeviceLink)
- 长按操作菜单 (ConfigMenu)
- 单元测试
