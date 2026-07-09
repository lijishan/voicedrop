# VoiceDrop Android 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan.

**Goal:** 从零构建 VoiceDrop Android 版，对标 iOS 全部核心功能

**Architecture:** 单一 Activity + Compose Navigation，扁平文件结构镜像 iOS，`mutableStateOf` Store 模式，OkHttp 网络层

**Tech Stack:** Kotlin 2.0, Jetpack Compose, Material3, OkHttp 4, Gson, EncryptedSharedPreferences, Coil, ExoPlayer, WorkManager

## 依赖版本

```toml
compose-bom = "2024.05.00"
compose-compiler = "1.5.14"
okhttp = "4.12.0"
gson = "2.11.0"
coil = "2.6.0"
media3 = "1.3.1"
security-crypto = "1.1.0-alpha06"
work = "2.9.0"
navigation = "2.7.7"
activity-compose = "1.9.0"
lifecycle-viewmodel-compose = "2.8.0"
junit = "5.10.2"
mockk = "1.13.11"
```

## Tasks

### Batch 1: 项目骨架 (Task 1-3)
- Gradle 构建文件 (root + app + version catalog)
- AndroidManifest
- VoiceDropApp.kt, MainActivity.kt
- Theme.kt, Model.kt, Networking.kt, Auth.kt

### Batch 2: 录音核心 (Task 4-7)
- AudioRecorder.kt, RecordingName.kt, RecordingPromoter.kt
- Uploader.kt (WorkManager)
- Library.kt (Store), LibraryView.kt (首页)
- RecordSession.kt (全屏录音)
- StatusSession.kt (WS 挖矿状态)
- Formatting.kt

### Batch 3: 文章 + 编辑 (Task 8-10)
- ArticleBody.kt (正文 photo marker 解析)
- RecordingDetailView.kt (文章详情)
- VoiceEdit.kt + VolcASRProtocol.kt (ASR)
- PushToTalkBar.kt (按住说话)
- AgentSession.kt (编辑 WS)
- EditQueueStore.kt, PhotoTile.kt, PlayerBar.kt

### Batch 4: 社区 + 追问 + 设置 (Task 11-14)
- FollowupQuestions.kt
- Community.kt (Store + View)
- SettingsView.kt + UsageView.kt + AccountView.kt
- WeChatPublish.kt
- UIConfigStore.kt + ConfigMenu.kt
- ShareSheet.kt

### Batch 5: 验证 (Task 15)
- 编译通过
- 运行 lint
- 部署文档
