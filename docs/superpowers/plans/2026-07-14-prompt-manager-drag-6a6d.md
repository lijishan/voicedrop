# Prompt Manager 拖拽交互（第 6 轮 6a–6d）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 PromptManagerView 的排序模式从原生 `onMove`/`editMode` 换成第 6 轮设计的自定义拖拽：≡ 手柄发起、拖起行跟手、琥珀虚线落点缝隙、folder 悬停张口收纳、「移到分组外」落点区。

**Architecture:** 编辑态脱离 List 的原生排序：行帧用 PreferenceKey 收集，`DragGesture`（挂在 ≡ 手柄上）驱动一个纯逻辑的 `DragEngine`（手指 Y → 落点 index / folder 悬停 / 移出区判定，全部可单测），拖起行画在 overlay 跟手。普通态保持现状（List + 左滑删除）。数据落盘不变：`draft` + 「完成」整树 PUT + baseline 冲突检查（Task 7 已有，全部保留）。

**Tech Stack:** SwiftUI（iOS 18 target）、XCTest（VoiceDropTests）、xcodegen。**纯 iOS 改动，服务端零变更。**

**设计参考：** `/Users/jianshuo/Downloads/design_handoff_prompt_manager 2/Prompt Manager.dc.html` 第 6 轮四帧。上一轮 1d 的视觉语法（常开分组 + 常驻虚线框 + 分区标题）**作废**。

## Global Constraints

- 仓库 `~/code/voicedrop`，分支从 main 新开 `prompt-drag-6a6d`。新/删文件后跑 `xcodegen`。
- 构建：`xcodebuild build -project VoiceDrop.xcodeproj -scheme VoiceDrop -destination 'generic/platform=iOS' CODE_SIGNING_ALLOWED=NO`。
  单测：`xcodebuild test … -only-testing:VoiceDropTests -destination 'platform=iOS Simulator,name=iPhone 16'`（首跑可能抖，重试一次）。基线 **68 绿**。
- **保留不动**：普通态的一切（5a 行/标/左滑删除/导入入口/恢复默认/高亮）；`draft` 本地副本 + `applyReorder(draft, baseline:)` 冲突检查；`isMutating` 门；两级封顶（`PromptLogic.movingIntoGroup` 对 group→group 返回 nil）；退出确认。
- **发版规矩：TestFlight 上传是 opt-in** —— commit message 带 `[tf]` 或 `gh workflow run build.yml -f destination=testflight`。CI 绿 ≠ 出包。
- 交互契约（从设计 HTML 逐帧提取，实现按此逐字对照）：

### 6a 进编辑态
- 长按任一行进入。顶栏：**返回方块隐藏**、＋ 换成「完成」（15/semibold `#D8593B`，纯文字无底）。
- 列表顶部提示行：「拖 ≡ 手柄调顺序；拖到 folder 标题收进去。」12.5 `#8A8175`。
- 每行左侧 ≡ 手柄：三横线（`M4 6h12M4 10h12M4 14h12` 风格，SF 可用 `line.3.horizontal`），16pt，静止 `#C9C0B1` / stroke 1.6；**这是唯一拖动发起点**。
- 行右尖角、「适用于」标、origin 标**全部隐藏**——编辑态行 = 手柄 + 图标块 + 名字。
- **folder 默认收起**：行 = 手柄 + folder 图标（30×30 块 `#F1ECE3`，图标 `#7A6E5C`）+ 名字 15 + 「N 项」12 `#b8ae9e` + ⌄（`#CFC6B6`）。folder 行自身也可拖（排序），但**不能被拖进另一个 folder**。
- 导入虚线框、恢复默认按钮在编辑态隐藏（设计四帧均未出现）。

### 6b 拖动排序
- 按住 ≡ 拖起：该行浮起跟手——`scale(1.03)`、投影 `0 14 30 rgba(60,48,30,0.26)`、边 1px `#EBD9B8`、圆角 9、手柄变 `#D8A25B`（stroke 1.9）、名字变 semibold。原位置的行让路。
- 落点显示**琥珀虚线缝隙**：高 44、`margin 5×10`、1.5pt dashed `#D8A25B`、圆角 8、底 `#FBF3E9`、**无文字**。松手插入。
- 其余行 `opacity 0.9`。只在同一列表内上下移动（顶层项在顶层间、组内项在组内间；跨界走 6c/6d）。

### 6c 拖进 folder
- 拖行悬停在 folder 标题上 **~0.3s** → folder 张口：底 `#FBF3E9`、1.5pt `#D8A25B` 边、**圆角 5→9**、外发光 `0 0 0 4px rgba(216,162,91,0.18)`、内距 12→13；图标块变 `#F6EBD6`、图标 `#C98A2E` 且**加 +**（`folder.badge.plus`）；标题替换为「放进「{X}」」15/semibold `#B98A3E`，尾部（原 ⌄ 位置）「松手收纳」12 `#C98A2E`。
- 此状态下拖起行加码：`scale(1.04) rotate(-1°)`、投影加深 `0 16 32 rgba(60,48,30,0.28)`。
- 其他卡 `opacity 0.55`（比 6b 的 0.9 更暗）。
- 松手收进该组末尾。**拖的是 folder 时悬停另一 folder 不高亮不接受**（两级封顶）。
- 悬停不足 0.3s 移开 → 高亮取消（计时器复位）。

### 6d 展开 folder / 拖出
- 编辑态点 folder 标题行 → 展开：尾部 ⌄ 变 ⌃；**展开时不显示「N 项」**；标题行底色 `#F7F2E9` + 底边 1px `#F0E8DA`。
- 组内行：左内边距 **30**（顶层 14）、图标块缩 **26×26 圆角 6**、名字 **14.5**、各带自己的 ≡ 手柄，组内可互相调序（同 6b 机制）。
- 拖组内行时，folder 卡**下方**出现带字落点区：「移到分组外」12 `#C98A2E`，高 44、1.5pt dashed `#D8A25B`、圆角 8、底 `#FBF3E9`。松手移到顶层（插在该 folder 之后）。
- 「完成」→ 现有 `commitReorder`（一次整树 PUT + baseline 检查）。

---

### Task 1: DragEngine 纯逻辑 + 6a/6b（编辑态重构 + 同列表排序）

**Files:**
- Create: `VoiceDropApp/PromptDragEngine.swift`（纯逻辑，无 UI）
- Modify: `VoiceDropApp/PromptManagerView.swift`（编辑态整体换掉：去 `editMode`/`onMove`/`.draggable`/`.dropDestination`，换自定义）
- Test: `VoiceDropTests/PromptDragEngineTests.swift`

**Interfaces:**
- Produces（纯逻辑，全部可单测；`RowFrame = {id: String, frame: CGRect, kind: .action|.groupTitle|.child(parent: String)}`）：
  - `DragEngine.dropIndex(fingerY:rows:draggedID:) -> DropTarget` —— `DropTarget = .reorder(index: Int, scope: Scope) | .intoGroup(id: String) | .outOfGroup(parent: String) | .none`；`Scope = .topLevel | .group(String)`。判定规则：手指在某 folder 标题帧内 → 候选 `.intoGroup`（由 View 层计时 0.3s 后生效）；拖的是组内行且手指越出组边界 → `.outOfGroup`；否则按行帧中点算插入 index，同 scope 内。
  - `DragEngine.apply(_ target: DropTarget, draggedID: String, items: [PromptNode]) -> [PromptNode]?` —— 落地成新树（复用 `PromptLogic.moving*` 系列；`.intoGroup` 且 dragged 是 group → nil）。
- Consumes: `PromptLogic.moving/movingWithinGroup/movingIntoGroup/movingOutOfGroup/flattenIDs`（已有，已测）。

- [ ] **Step 1:** 写 `PromptDragEngineTests`（失败先行）：顶层拖到顶层各 index；组内拖组内；手指落 folder 标题帧 → `.intoGroup`；拖 group 悬停 group → apply 返回 nil；组内行拖出组边界 → `.outOfGroup`；边界（列表最顶/最底、空组、单行组）；apply 后 `flattenIDs` 无重复无丢失。
- [ ] **Step 2:** 实现 `PromptDragEngine.swift`，测试绿。
- [ ] **Step 3:** 重构 `PromptManagerView` 编辑态：行帧 PreferenceKey 收集（named coordinateSpace）；≡ 手柄挂 `DragGesture(minimumDistance: 2)`；拖起行从流中隐去（`opacity 0` 占位或移除+缝隙补位），overlay 画浮起行跟手（6b 样式）；落点缝隙 44pt 动画插入；其余行 opacity 0.9；顶栏/提示行/隐藏项照 6a 契约；folder 收起态（保留 `expandedGroups` 但编辑态入场时折叠全部）。普通态（List + 左滑）不动——**编辑态切换容器**（List ↔ ScrollView/VStack）可接受，两态视觉一致即可。
- [ ] **Step 4:** `xcodegen` + 单测（68+新）+ 构建绿 + 提交。

### Task 2: 6c/6d（folder 收纳 + 展开 + 拖出）

**Files:**
- Modify: `VoiceDropApp/PromptManagerView.swift`、`VoiceDropApp/PromptDragEngine.swift`
- Test: `VoiceDropTests/PromptDragEngineTests.swift`（追加）

- [ ] **Step 1:** folder 悬停判定测试（进入/离开帧复位计时的状态机若在 View 层则测 Engine 的帧判定 + apply）；`.outOfGroup` 插入位置 = 该 folder 之后（测）。
- [ ] **Step 2:** 6c UI：悬停 0.3s 计时（`isTargeted` 等价状态 + `Task.sleep` 取消式计时）；张口样式全套（边/发光/圆角/图标+/文案替换）；拖起行加码样式（1.04 + rotate -1° + 深投影）；其他卡 0.55。拖 group 时 folder 不响应。
- [ ] **Step 3:** 6d UI：编辑态点标题展开/收起（⌃/⌄、去 N 项、`#F7F2E9` 标题底）；组内行 30pt 缩进 + 26pt 图标 + 14.5 名字 + 自有手柄；拖组内行时 folder 卡下方出「移到分组外」区；松手落顶层。**删掉 Task 7 的「移出分组」左滑动作**（被 6d 取代）。
- [ ] **Step 4:** 单测 + 构建绿 + 提交。

### Task 3: 收尾 —— review + 手测 + 发版

- [ ] **Step 1:** 全分支终审（对照本契约逐条 + 回归：普通态左滑删除/导入高亮/appliesTo 过滤不受影响；「完成」PUT + baseline 检查仍在；取消丢弃 draft）。
- [ ] **Step 2:** 模拟器尽力验证 + 截图；无法脚本化的手势列进人工清单（预计：拖拽全链路都要真机）。
- [ ] **Step 3:** STATE.md 更新（第 6 轮拖拽上线、DragEngine 文件、1d 语法作废）。
- [ ] **Step 4:** 合 main，**提交带 `[tf]`** 或 dispatch 发 TestFlight（用户确认后执行）。

## Self-Review
- 6a 四条（顶栏/提示行/手柄/隐藏项/folder 收起）→ Task 1 Step 3 ✓；6b 全部样式值 → Task 1 ✓；6c 悬停张口 + 加码样式 + 两级封顶不高亮 → Task 2 ✓；6d 展开/缩进/移出区/组内调序 → Task 2 ✓。
- 数据层零改动：落地仍走 `PromptLogic.moving*` + `applyReorder`（Task 7 资产全复用）✓。
- 「排序不改 appliesTo」天然成立（拖动只动树结构）✓。
- 风险点：自定义拖拽的滚动联动（拖到屏幕边缘自动滚动）设计未提——**不做**，列表短（≤200 项封顶、典型 15），记入已知取舍。
