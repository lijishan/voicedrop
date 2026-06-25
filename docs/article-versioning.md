# 文章版本控制与撤销/重做

## 数据模型

每篇文章存为 R2 上的一个 JSON 文件（`users/<sub>/articles/<stem>.json`），版本历史内嵌其中：

```json
{
  "head": 2,
  "versions": [
    { "v": 1, "savedAt": 1719200000000, "source": "mine",  "articles": [...] },
    { "v": 2, "savedAt": 1719201000000, "source": "agent", "articles": [...] }
  ],
  "transcript": "...",
  "createdAt": "...",
  ...
}
```

- `versions` 按 v 升序排列（最旧在前），最多保留 `MAX_VERSIONS = 10` 条。
- `head` 是当前生效版本的 v 值，类比 git HEAD。
- 当前内容 = `versions.find(e => e.v === head).articles`。

`source` 字段记录写入来源：`mine`（初次挖矿）、`agent`（语音编辑）、`wechat`（发布公众号后写回 wechatMediaId）。

## 写入新版本

每次语音编辑完成，`writeArticleDoc` 执行：

1. 截断 `head` 之后的所有版本（undo 后如果写了新内容，"未来"被丢弃，同 git）
2. 追加新版本 `v = head + 1`
3. `head` 更新为新 v

## 撤销 / 重做

**不写新版本**，只移动 `head` 指针。

| 操作 | 服务端 | iOS |
|---|---|---|
| 撤销 | `PATCH /files/api/articles/<stem>/head` `{head: head-1}` | 本地立即移动 head，更新 UI；异步发 PATCH |
| 重做 | `PATCH /files/api/articles/<stem>/head` `{head: head+1}` | 同上 |

iOS 端 `performUndo/performRedo` 是同步函数——先本地切换，再后台同步服务端——所以按下按钮毫秒级响应，不卡网络。

## 旧格式自动升级

旧版（schema 2）文档格式为顶层 `articles` + `history: []`（newest-first）。`readArticleDoc` 读取时检测到旧格式会在内存中自动升级为 schema 3，下次 `writeArticleDoc` 时以新格式落盘。无需批量迁移脚本。

## API 路由

| 路由 | 说明 |
|---|---|
| `GET  /articles/<stem>/history` | 返回 `{head, versions}`（oldest-first） |
| `PATCH /articles/<stem>/head`   | 移动 head 指针，body `{head: N}`，不写新版本 |
| `PUT  /articles/<stem>`         | 写新版本（截断 head 后 → 追加 → head++） |

## 核心代码位置

| 文件 | 内容 |
|---|---|
| `jianshuo.dev/functions/lib/article-store.js` | `writeArticleDoc` / `setHead` / `migrateToV3` |
| `jianshuo.dev/functions/files/api/[[path]].js` | `PATCH /head` 路由 |
| `VoiceDropApp/RecordingDetailView.swift` | `performUndo` / `performRedo` / `applyVersion` |
| `VoiceDropApp/Library.swift` | `fetchVersionHistory` / `patchHead` |
