# 映世 App UI 精修落地路线图

> 版本：v1.0 | 日期：2026-06-17 | 状态：待启动

---

## 总体策略

**批次推进，每批可独立验收和回退。** 前一批未完成不启动下一批。

| 批次 | 内容 | 预计迭代轮次 | 验收标准 |
|---|---|---|---|
| 第 0 批 | 设计系统对齐 | 1 轮 | token 与规范一致 |
| 第 1 批 | 登录页 | 2-3 轮 | 对照规范 5.1 全通过 |
| 第 2 批 | 主页 | 2-3 轮 | 对照规范 5.2 全通过 |
| 第 3 批 | 生活页 | 2-3 轮 | 对照规范 5.6 全通过 |
| 第 4 批 | 我的页 | 2-3 轮 | 对照规范 5.7 全通过 |
| 第 5 批 | 照片流 + 相册列表 | 3 轮 | 对照规范 5.3/5.4 全通过 |
| 第 6 批 | 照片查看器（Viewer） | 2-3 轮 | 对照规范 5.8 全通过 |
| 第 7 批 | 底部导航栏 + 全局组件 | 2 轮 | 全 App 一致 |
| 第 8 批 | P0 无障碍修复 | 1 轮 | contentDescription 全补齐 |
| 第 9 批 | 空状态 + 骨架屏 | 2 轮 | 所有列表页有空状态 |

---

## 第 0 批：设计系统对齐（基础批次）

> 在做任何页面之前，先把 token 定义和代码实现对齐。不然每页都会用错颜色。

### 0.1 核对 Color.kt 与规范偏差

| 规范文档中的颜色 | 当前 Color.kt | 状态 |
|---|---|---|
| `memoryAccent = #A94C42` | `YingShiMemoryAccent = #A2473D` | ❌ 不一致 |
| `primaryContainer = #BDEFFF` | `YingShiPrimaryContainer = #BDEFFF` | ✅ 一致 |
| `onPrimaryContainer = #1F2933` | `YingShiOnPrimaryContainer = #1F2933` | ✅ 一致 |
| `raisedSurface = #FFFFFC` | `YingShiSurface = #FFFFFC` | ⚠️ 命名不同，需确认是否同一含义 |
| `dividerSoft = #C7E6EC` | 规范中有，Color.kt 中需确认 | 待核对 |

**行动**：逐行比对 `03-color-system-v1.1.md` 第 3-4 节与 `Color.kt`，输出修正清单。

### 0.2 消除硬编码颜色

以下位置发现硬编码（来自代码评审）：

- `HomeScreen.kt`：照片卡圆角 `32.dp` 超出 Token 范围（最大 `cardLarge = 20.dp`）
- `LifeScreen.kt`：入口卡图标背景色硬编码，未使用 `softGreenContainer` / `softBlueContainer` 等 token
- `HomeScreen.kt`：`HomePhotoCollage` 渐变遮罩颜色需确认是否使用 token

**行动**：搜索全项目 `Color(` 和 `#` 硬编码，逐条替换为 token。

### 0.3 输出交付物

- `02a-color-token-fix-list.md`：逐行修正清单
- `02b-hardcoded-color-audit.md`：全项目硬编码 Audit 报告

---

## 第 1 批：登录页精修

**文件**：`feature/auth/LoginScreen.kt`
**规范参照**：`03-color-system-v1.1.md` 第 5.1 节

### 当前问题清单（待截图确认后更新）

| # | 问题 | 规范对照 | 优先级 |
|---|---|---|---|
| 1 | 无品牌标识区，"映世" 二字孤立 | 5.1: 登录页应有品牌标记 | P1 |
| 2 | 输入框直接浮在 Aurora 背景上，无容器 | 5.1: 表单容器使用 `raisedSurface` | P1 |
| 3 | 主按钮视觉表现需对照规范确认 | 6.1: 主按钮 `primaryContainer` + `onPrimaryContainer` | P1 |
| 4 | `PresetAccountRow`（账号 A/B）应仅在 DEBUG 显示 | — | P2 |
| 5 | 无"创建共同空间"入口，新用户无路径 | 产品流程缺失 | P2 |
| 6 | 验证码登录流程 UI 需确认 | — | P2 |

### 设计交付物

- Ardot 设计稿：登录页精修版（默认态 / 输入中态 / 报错态）
- Token 映射表：`LoginScreen-token-map.md`
- 交互说明：登录流程图（账号密码 → 两步验证 → 首页）

---

## 第 2 批：主页精修

**文件**：`feature/home/HomeScreen.kt`
**规范参照**：`03-color-system-v1.1.md` 第 5.2 节

### 当前问题清单

| # | 问题 | 规范对照 | 优先级 |
|---|---|---|---|
| 1 | 照片卡 32dp 圆角硬编码，超出 Token 范围 | Token 体系一致性 | P1 |
| 2 | `HomePhotoCollage` 4 格拼贴，不等比布局 | 需对照设计规范确认是否允许 | P2 |
| 3 | 渐变遮罩颜色需确认是否使用 token | 5.2 | P2 |
| 4 | 通知铃铛 `PhotoBellButton` 70 行 Canvas 手绘 | 可用 `Icons.Rounded.Notifications` 替代 | P2 |
| 5 | 账本信号卡 `HomeLedgerCard` 颜色需对照规范 | 5.2 | P2 |
| 6 | 顶部 Aurora 背景与内容区过渡需优化 | 视觉层次 | P2 |

### 设计交付物

- Ardot 设计稿：主页（默认态 / 照片选择模式态）
- Token 映射表：`HomeScreen-token-map.md`
- 交互说明：照片选择模式流程（点击选择 → 批量操作栏 → 删除确认）

---

## 第 3 批：生活页精修

**文件**：`feature/life/LifeScreen.kt`
**规范参照**：`03-color-system-v1.1.md` 第 5.6 节

### 当前问题清单

| # | 问题 | 规范对照 | 优先级 |
|---|---|---|---|
| 1 | 三张入口卡图标背景色硬编码 | 5.6: 允许使用 `softGreenContainer` 等 | P1 |
| 2 | 状态 chip 样式需对照规范 | 5.6 | P2 |
| 3 | 三卡等高布局间距需确认 | 间距 Token 对齐 | P2 |
| 4 | 生活页整体信息层级需优化 | 视觉层次 | P2 |

### 设计交付物

- Ardot 设计稿：生活页（默认态 / 各入口有数据态）
- Token 映射表：`LifeScreen-token-map.md`

---

## 第 4 批：我的页精修

**文件**：`feature/me/MyScreen.kt`
**规范参照**：`03-color-system-v1.1.md` 第 5.7 节

### 当前问题清单

| # | 问题 | 规范对照 | 优先级 |
|---|---|---|---|
| 1 | 用户身份卡大头像 88dp 需对照规范确认 | 5.7 | P2 |
| 2 | 伴侣卡样式需对照规范 | 5.7 | P2 |
| 3 | 工具列表卡（设置/缓存）样式需确认 | 5.7 | P2 |
| 4 | 账号状态卡（在线/离线）颜色需确认 | 5.7 | P2 |
| 5 | 退出按钮样式需对照规范 | 5.7 | P1 |

### 设计交付物

- Ardot 设计稿：我的页（默认态 / 伴侣已绑定态 / 离线只读态）
- Token 映射表：`MyScreen-token-map.md`

---

## 第 5 批：照片流 + 相册列表

**文件**：`feature/photos/PhotosRootScreen.kt`（1550 行，建议拆分）
**规范参照**：`03-color-system-v1.1.md` 第 5.3 / 5.4 节

### 当前问题清单

| # | 问题 | 规范对照 | 优先级 |
|---|---|---|---|
| 1 | `PhotoBrandTabs` 选中态叠加 4 层视觉效果，低端机可能卡顿 | 性能 | P1 |
| 2 | 照片网格间距需对照规范 | 5.3 | P2 |
| 3 | 相册列表卡片样式需对照规范 | 5.4 | P2 |
| 4 | 照片选择模式已在主页存在，需与照片流选择模式保持一致 | 交互一致性 | P1 |
| 5 | `PhotosRootScreen.kt` 1550 行，职责过重 | 代码架构 | P2 |

### 设计交付物

- Ardot 设计稿：照片流（网格态 / 时间线态）+ 相册列表
- Token 映射表：`PhotosRootScreen-token-map.md`
- 组件拆分建议：`PhotosRootScreen-refactor.md`

---

## 第 6 批：照片查看器（Viewer）精修

**文件**：`feature/photos/PhotoViewerScreen.kt`
**规范参照**：`03-color-system-v1.1.md` 第 5.8 节

### 当前问题清单

| # | 问题 | 规范对照 | 优先级 |
|---|---|---|---|
| 1 | Viewer 使用独立深色系（`#101F26`），需确认是否与规范一致 | 5.8 | P1 |
| 2 | Viewer 手势操作（双指缩放、滑动切换）需设计说明 | 交互 | P1 |
| 3 | Viewer 顶部工具栏 + 底部操作栏样式需对照规范 | 5.8 | P2 |
| 4 | Viewer 加载状态 / 错误状态需设计 | 空状态 | P2 |

### 设计交付物

- Ardot 设计稿：Viewer（默认态 / 缩放态 / 删除确认态）
- 交互说明：手势操作规格（缩放倍率、滑动阈值、动画曲线）
- Token 映射表：`PhotoViewerScreen-token-map.md`

---

## 第 7 批：底部导航栏 + 全局组件

**文件**：`ui/components/AppShell.kt` + 各全局组件
**规范参照**：`03-color-system-v1.1.md` 第 5.5 节

### 当前问题清单

| # | 问题 | 规范对照 | 优先级 |
|---|---|---|---|
| 1 | 中间 Add 按钮渐变蒙版 + 触感反馈需确认规格 | 5.5 | P2 |
| 2 | 底部导航栏浮在 Aurora 背景上的视觉效果需确认 | 5.5 | P2 |
| 3 | 全局 Toast / Snackbar 样式需对照规范 | 全局组件 | P2 |
| 4 | 全局 Loading 状态需设计 | 空状态/加载态 | P1 |

### 设计交付物

- Ardot 设计稿：底部导航栏（默认态 / 各 Tab 选中态）
- 全局组件库：`GlobalComponents.md`（Toast / Snackbar / Loading / Dialog）

---

## 第 8 批：P0 无障碍修复

> 此批不依赖设计稿，可与其他批并行。

### 修复清单

| 文件 | 缺失内容 | 修复内容 |
|---|---|---|
| `HomeScreen.kt` | 所有图标 `contentDescription = null` | 补充语义描述 |
| `LifeScreen.kt` | 所有图标 `contentDescription = null` | 补充语义描述 |
| `MyScreen.kt` | 所有图标 `contentDescription = null` | 补充语义描述 |
| `LoginScreen.kt` | 输入框无 `label` / `placeholder` 语义 | 补充 |
| 全项目 | 无 `focusOrder` / `focusRequester` | 补充键盘导航支持 |

### 交付物

- `accessibility-fix-list.md`：逐文件修复清单
- 修复后验证：TalkBack 走查报告

---

## 第 9 批：空状态 + 骨架屏

> 新用户打开 App 看到空白页会直接流失，此批优先级实际应为 P1，但因依赖前面各页设计完成，故排在后面。

### 需设计空状态的页面

| 页面 | 空状态场景 | 设计规范 |
|---|---|---|
| 主页照片卡 | 两人都还没有上传照片 | 插画 + 引导文案 |
| 生活页记账入口 | 还没有账单 | 插画 + "去记一笔" CTA |
| 生活页聊天入口 | 还没有聊天记录 | 插画 + 引导 |
| 相册列表 | 还没有创建相册 | 插画 + "新建相册" CTA |
| 照片流 | 相册为空 | 插画 + 引导 |

### 交付物

- Ardot 设计稿：各页面空状态
- 骨架屏设计：照片网格加载中骨架屏
- `EmptyStateDesign.md`：空状态设计系统（插画风格 + 文案规范）

---

## 每批工作节奏

```
批次启动
  → 你发当前页截图
  → 我分析 + 对照规范，输出问题清单
  → 我在 Ardot 画精修版
  → 截图发你审核
  → 你提意见 → 我修改 → 再截图
  → （2-3 轮后）你确认 OK
  → 我输出 Token 映射表 + 交互说明
  → 你拿去给 Codex AI 实现
  → 实现后你截图给我验收
  → 批次完成，打 commit
```

---

## 文档输出清单（全过程累计）

| 文档名 | 产生批次 | 用途 |
|---|---|---|
| `01-initial-review-2026-06-17.md` | 评审 | 初始评审报告（已完成） |
| `02-refinement-roadmap.md`（本文件） | — | 路线图 |
| `02a-color-token-fix-list.md` | 第 0 批 | Token 修正清单 |
| `02b-hardcoded-color-audit.md` | 第 0 批 | 硬编码 Audit |
| `03-login-screen-design.md` | 第 1 批 | 登录页设计交付 |
| `04-home-screen-design.md` | 第 2 批 | 主页设计交付 |
| `05-life-screen-design.md` | 第 3 批 | 生活页设计交付 |
| `06-my-screen-design.md` | 第 4 批 | 我的页设计交付 |
| `07-photos-screen-design.md` | 第 5 批 | 照片流设计交付 |
| `08-viewer-screen-design.md` | 第 6 批 | Viewer 设计交付 |
| `09-global-components-design.md` | 第 7 批 | 全局组件设计交付 |
| `10-accessibility-fix-list.md` | 第 8 批 | 无障碍修复清单 |
| `11-empty-state-design.md` | 第 9 批 | 空状态设计系统 |

---

## 备注

- 本路线图基于 `03-color-system-v1.1.md` 和初始代码评审结果制定
- 每批启动前需你提供当前页最新截图，确认实际问题范围
- 批次顺序可根据你的偏好调整（比如你想先看 Viewer 效果，可以把第 6 批提前）
- 估计总工作量：**约 20-25 轮设计迭代**（每批 2-3 轮 × 9 批）
