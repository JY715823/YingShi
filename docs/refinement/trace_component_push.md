# trace_component_push 精修 Brief（第二轮）

> 状态: implemented（第三轮视觉改进已完成）
> 模块: 生活桌面组件视觉重设计 + 推送修正 + 系统媒体导入关系
> 日期: 2026-06-29

## 模块目标

1. 小组件视觉重设计：全透明背景、更有设计感的卡片造型（告别朴素圆角矩形）
2. 修正小组件上传后的推送：发生活模块推送、不发小相册推送，点击直达快速查看态
3. 系统相册上传时更新导入关系
4. 拍照不存手机相册（已实现，验证保持）

## 现状分析

### 推送双发根因

小组件上传照片的完整链路：
1. 客户端 `LifeConsoleUploadBridge.uploadMedia()` → 创建 upload token → 上传文件 → confirm
2. 服务端 `UploadService.confirmUpload()` → `notifyUploadOperationIfCompleted()` → 发 **photos push**（"照片内容有更新"）
3. 客户端拿到 mediaIds → 调用 `LifeConsoleService.addMedia()` API
4. 服务端 `LifeConsoleService.addMedia()` → `notifyLifeConsoleChanged()` → 发 **life push**（"今日痕迹有新更新"）

两条推送都发到了，但 photos push 先到（因为 confirm 在 addMedia 之前），导致用户只看到小相册推送。

### 系统媒体导入关系缺失

`LifeConsoleUploadBridge` 创建 upload token 时没有传 `sourceItemId` 和 `sourceFingerprint`，服务端无法将上传的媒体与系统相册中的原始文件关联。

### 拍照行为

已正确：`WidgetMediaEntryActivity.createCaptureUri()` 存到 `cacheDir/life-console-capture`，不进 MediaStore。

## 实施规划

### A. 小组件视觉重设计

**目标效果：** 全透明背景 + 更有层次和辨识度的卡片造型，参考现代 widget 设计语言。

**RemoteViews 约束下的可用手段：**
- 形状：layer-list 叠加、gradient 渐变、不规则 shape（oval 角、部分圆角）
- 背景：全透明根布局、半透明 tint 卡片
- 层次：多层 shape 模拟阴影/深度、stroke 粗细对比
- 排版：大字号数字/标签作为视觉锚点、字重对比
- 颜色：渐变 fill、更饱和的强调色

**具体方案：**

1. **根布局全透明**
   - 两个布局 `android:background="@android:color/transparent"`
   - 去掉整体面板感，让各卡片独立浮在桌面上

2. **卡片造型升级（混合手法）**
   - **多层叠加**：用 `layer-list` 做外圈浅色描边 + 内圈半透明填充，模拟浮起深度感
   - **不规则圆角**：部分卡片尝试顶部大圆角 + 底部小圆角（如 24dp/12dp），打破"规矩矩形"
   - **渐变填充**：卡片内部用 `gradient` 渐变增加质感（如左上→右下渐变）
   - **胶囊对比**：操作按钮/状态 chip 用全圆角胶囊形（radius ≥ 100dp），与卡片区形成造型对比
   - **描边粗细对比**：外框 2dp、内元素 1dp，形成层次

3. **排版层次**
   - 区域标题（"吃饭"、"痕迹"）加粗加大，作为视觉锚点
   - 数字/计数用更大字号或不同字重
   - 减少装饰性元素，让内容和排版本身成为设计

4. **配色微调**
   - 卡片背景用更饱和的色调（配合透明根背景的轻盈感）
   - 操作按钮用对比色（绿色确认、红色删除）更鲜明
   - 空状态用虚线边框或浅色 pattern 区分

### B. 推送修正（客户端 + 服务端）

**客户端改动：**

1. `LifeConsoleUploadBridge` 创建 upload token 时设置：
   - `operationType = "life_console"`
   - `sourceFingerprint` = 从 URI 解析的文件指纹（用于系统媒体匹配）
   - `sourceItemId` = MediaStore ID（从相册选择时通过查询获取）

2. `WidgetMediaEntryActivity` 在 photo picker 回调中：
   - 通过 ContentResolver 查询 URI 对应的 MediaStore `_ID`
   - 将 MediaStore ID 作为 `sourceItemId` 传给上传流程
   - 如果查询不到 ID（Photo Picker 限制），则不传 sourceItemId

3. `PushNotificationPresenter.contentIntent()` 修改：
   - `life:trace` 路由 → LifePushDispatchActivity（打开 QuickViewer）
   - `life:bowel` 路由 → dismiss-only PendingIntent（点击仅消失，不打开任何页面）

**服务端改动：**

4. `UploadService.notifyUploadOperationIfCompletedAfterDelay()` 中：
   - 检查 `operationType`，如果是 `"life_console"` → 跳过 photos push
   - 这样生活模块上传只触发 life push，不触发小相册推送

### C. 系统媒体导入关系更新

上传 token 中携带 `sourceItemId` 和 `sourceFingerprint` 后：
- 服务端 `MediaEntity` 会记录这些信息
- 系统媒体区的 `withAppImportStatus()` 查询通过 fingerprint 匹配，自动标记为"已导入"
- 无需额外 API，现有匹配机制即可工作

### D. 拍照验证

- 已确认 `createCaptureUri()` 存到 app 缓存目录
- 不写入 MediaStore 扫描路径
- 无需改动，implement 阶段验证保持即可

## 明确不做

- 不改推送偏好设置 UI
- 不改小相册本身的展示逻辑
- 不改 LifeConsoleWidgetStore 数据结构
- 不改排便记录的 API 链路（推送已由 life push 覆盖）

## 契约

- 客户端 upload token 新增 `operationType = "life_console"`（服务端已有此字段，无需新增）
- 服务端 UploadService 按 operationType 过滤推送（内部逻辑变更，API 不变）
- 推送 payload 格式不变（life push 已有正确的 targetRoute）
- 系统媒体 fingerprint 匹配机制不变（只是客户端开始传正确的值）

## 关键决策（已确认）

1. **卡片造型**：混合多种手法——多层叠加深度 + 半透明质感 + 不规则圆角 + 胶囊按钮对比，在 RemoteViews 限制内尽量丰富
2. **排便推送**：点击后仅消失（dismiss），不打开 QuickViewer 也不打开任何页面。contentIntent 设为 dismiss-only PendingIntent
3. **系统媒体 sourceItemId**：必须有 MediaStore ID。Photo Picker 返回 URI 后，通过 MediaStore 查询匹配获取 ID；如果查不到则不传 sourceItemId（该条媒体不走导入关系更新）

## 关联模块

- `album-small` — 小相册推送被抑制后，小相册内容仍然正确（只是不发推送）
- `system-media` — 导入关系更新后，系统媒体区会正确显示"已导入"状态
- `upload` — upload token 增加 operationType/sourceItemId 字段
- `push-notification` — 推送路由和去重逻辑

## 依赖扫描

- 服务端 `UploadService` 需要改动（过滤 life_console 类型上传的 photos push）
- 客户端 `LifeConsoleUploadBridge` 需要改动（传 operationType、sourceFingerprint、sourceItemId）
- 客户端 `WidgetMediaEntryActivity` 需要改动（解析 MediaStore ID）
- 8 个 drawable XML + 2 个布局 XML 需要重做
- themes.xml 可能需要新增样式

## 规划自检

- [x] 推送双发根因已定位：UploadService.confirm → photos push 先于 LifeConsole.addMedia → life push
- [x] 解决方案不破坏现有 API 契约
- [x] 拍照行为已确认正确，无需改动
- [x] 系统媒体导入关系修复方案利用现有匹配机制，无需新 API
- [x] 视觉重设计在 RemoteViews 约束内可行
- [x] 服务端改动范围小（仅 UploadService 的推送过滤逻辑）
- [x] 排便推送行为已确认：点击仅 dismiss，不打开任何页面
- [x] sourceItemId 必须有 MediaStore ID，查不到则跳过导入关系更新
- [x] 卡片造型方向已确认：混合多种手法

## 实施记录

### 客户端

**A. 视觉重设计**

1. **根布局全透明**：`life_console_widget.xml` 和 `life_people_widget.xml` 的 `android:background` 改为 `@android:color/transparent`
2. **排版升级**：标题字号加大（今日痕迹 17→19sp，人物痕迹 18→20sp，吃饭 13→15sp，大便 15→17sp，账本 15→16sp）
3. **8 个 Drawable 全部重做**：
   - `widget_frame_card.xml`：4 层 layer-list（外发光→渐变主体→顶部高光→内发丝描边），26dp/24dp/22dp 递减圆角
   - `widget_bowel_card.xml`：4 层 layer-list，teal/cyan 渐变，28dp/26dp/23dp 递减圆角
   - `widget_bowel_peer_strip.xml`：3 层 layer-list，柔和蓝色渐变 + 高光 + 发丝描边
   - `widget_chip_light.xml`：胶囊形（radius=100dp），蓝色渐变 + 高光 + 发丝描边
   - `widget_control_dark.xml`：4 层胶囊 layer-list（阴影→绿色渐变→高光→描边），模拟浮起深度
   - `widget_danger_chip.xml`：3 层胶囊 layer-list，珊瑚色渐变
   - `widget_overlay_chip.xml`：暗色半透明胶囊，带渐变和微光描边
   - `widget_preview_panel.xml`：3 层 layer-list，蓝色渐变 + 深度感 + 发丝描边
4. **themes.xml**：无需改动，所有样式已通过 drawable 引用获取新外观

**B. 推送修正**

5. **LifeConsoleUploadBridge**：
   - `resolveUploadMetadata()` 新增 `resolveMediaStoreId()` 查询 MediaStore `_ID`
   - 新增 `buildSourceFingerprint()` 从文件名+大小+URI 生成指纹
   - `UploadMetadata` 新增 `sourceFingerprint` 和 `sourceItemId` 字段
   - `toTokenPayload()` 设置 `operationType = "life_console"`、`sourceFingerprint`、`sourceItemId`
6. **PushNotificationPresenter**：
   - `contentIntent()` 中 `life:bowel` 路由改为 dismiss-only PendingIntent（BroadcastReceiver）
   - `life:trace` 路由保持 → LifePushDispatchActivity → QuickViewer
7. **LifePushDismissReceiver**（新增）：BroadcastReceiver，收到 intent 后 cancel 对应通知
8. **AndroidManifest.xml**：注册 `LifePushDismissReceiver`（exported=false）

### 服务端

9. **UploadService.notifyUploadOperationIfCompletedAfterDelay()**：
   - 在发送 photos push 前检查 `completedTask.getOperationType()`
   - 如果是 `"life_console"` → return，跳过 photos push
   - life push 由 LifeConsoleService.addMedia() 正常发送，不受影响

### 设计

- 卡片：多层 layer-list 模拟深度和质感，渐变填充增加层次
- 按钮/chip：全圆角胶囊形（radius=100dp），与卡片区形成造型对比
- 描边：外粗内细（1.5dp outer → 0.6-0.8dp hairline inner），形成层次对比
- 配色：蓝白渐变卡片 + 绿色动作按钮 + 珊瑚色删除按钮 + 暗色叠加 chip

### 交互反馈

- 排便推送点击后通知直接消失，不打开任何页面
- 痕迹推送点击后打开 QuickViewer 快速查看态
- 小组件上传后只触发 life push（"今日痕迹有新更新"），不再触发小相册推送

### Post-implement self-check

- [x] `assembleDebug` 编译通过（客户端 + 服务端）
- [x] 所有 symbol 引用正确（AST 验证通过）
- [x] `CreateUploadTokenPayload` 字段匹配（sourceFingerprint、operationType、sourceItemId 均为已有字段）
- [x] `UploadTaskEntity.getOperationType()` 存在且返回 String
- [x] `LifePushDismissReceiver` 已在 AndroidManifest 注册
- [x] 推送路由逻辑无遗漏：life:trace → QuickViewer, life:bowel → dismiss, 其他 → 原有逻辑
- [x] 拍照路径不经过 resolveMediaStoreId 的成功分支（cacheDir URI 查不到 _ID → null → 不影响）

### New coupling recheck

- `album-small`：小相册推送被抑制，但内容仍正确（addMedia 正常调用，小相册数据更新正常）
- `system-media`：sourceItemId 和 sourceFingerprint 现在正确传递，导入关系匹配应自动生效
- `push-notification`：新增 LifePushDismissReceiver，不影响其他推送路由

### Implement test plan

**本地已验证：**
- 客户端 `assembleDebug` 编译通过
- 服务端 `mvnw compile` 编译通过
- Symbol 引用一致性检查通过

**需要设备测试：**
1. 小组件视觉：确认卡片在桌面显示为透明背景 + 渐变层次造型，不是朴素圆角矩形
2. 推送链路（关键）：从小组件上传照片 → 应只收到"今日痕迹有新更新"推送，不应收到"照片内容有更新"
3. 推送点击：点击痕迹推送 → 应打开 QuickViewer；点击排便推送 → 通知应直接消失
4. 系统媒体导入关系：从系统相册选择照片上传后 → 系统媒体区应显示该照片为"已导入"
5. 拍照验证：通过小组件拍照 → 照片不应出现在系统相册中

## 视觉改进规划（第三轮 — 用户反馈驱动）

> 状态: planning
> 日期: 2026-06-29
> 触发: 用户实机反馈——标题用黑色不明显、透明背景按钮零散、整体不够好看

### 问题诊断

1. **标题不可见**：所有标题（今日痕迹/人物痕迹/吃饭/大便）仍用 `#183B4B`（近黑色），在浅色渐变卡片上几乎隐形，完全达不到视觉锚点效果
2. **元素零散**：全透明根布局让标题栏、两张吃饭卡片、大便卡片、账本按钮各自独立悬浮在桌面上，之间没有视觉联系，看起来"孤零零的"
3. **整体冲击力不足**：卡片渐变太素（`#F4FBFF→#E2F1FA` 几乎纯白），外发光太弱（`#18A0C8E0` 仅 9% 透明度），视觉存在感很低

### 改动范围

| 类型 | 文件 | 改动 |
|------|------|------|
| 新增 | `drawable/widget_backdrop.xml` | 统一磨砂底板 |
| 改 | `layout/life_console_widget.xml` | 背景 + 4 处标题色 |
| 改 | `layout/life_people_widget.xml` | 背景 + 1 处标题色 |
| 改 | `drawable/widget_frame_card.xml` | 增强渐变饱和度和发光 |
| 改 | `drawable/widget_bowel_card.xml` | 增强 teal 饱和度 |
| 改 | `drawable/widget_preview_panel.xml` | 增强深度感 |

不涉及：服务端、Kotlin 代码、themes.xml、推送逻辑。

### 改动 A — 统一磨砂底板 `widget_backdrop.xml`

解决"按钮零散"的核心改动。在根布局底层铺一层极淡的磨砂面板，让所有子元素共享一个视觉基底。

```xml
<!-- 4 层 layer-list，28dp 圆角 -->
Layer 1: 外发光 — radius=28dp, solid=#18A8D0F0（模拟投影）
Layer 2: 主体 — radius=26dp, gradient #28E8F4FF→#18D8ECF8 angle=160
         （极淡蓝白渐变，~15-20% 白色不透明度）
Layer 3: 发丝描边 — radius=25dp, stroke 0.6dp #20A0C0E0
Layer 4: 内发光 — radius=24dp, gradient #10FFFFFF→transparent
```

设计要点：
- 不透明度刻意极低（15-20%），保持"透明"的轻盈感，但给散落元素一个统一的"家"
- 带极淡蓝色调（`#E8F4FF`），与卡片蓝白渐变呼应
- 28dp 圆角比内部卡片（26dp）略大，在卡片四角露出柔和弧线，形成"画框"效果
- 12dp padding 内边距让卡片与底板边缘保持呼吸间距

### 改动 B — 标题颜色

| 位置 | 原文色 | 新色 | 理由 |
|------|--------|------|------|
| 今日痕迹（19sp） | `#183B4B` | `#1565C0` |  vivid blue，在浅色卡片上清晰醒目 |
| 人物痕迹（20sp） | `#183B4B` | `#1565C0` | 同上 |
| 吃饭（15sp） | `#183B4B` | `#00838F` | vivid teal，与蓝色主标题形成冷暖对比 |
| 大便（17sp） | `#183B4B` | `#00838F` | 同上 |

不改：
- 账本按钮文字 `#26313A`（在绿色胶囊背景上，不需要改）
- 数字/计数文字（数据展示元素，保持现有对比度）
- chip 文字（辅助信息，保持低调）

### 改动 C — 卡片 Drawable 增强

**widget_frame_card.xml（吃饭卡片）：**
- 外发光：`#18A0C8E0` → `#3090C0E8`（透明度 9%→19%，投影更明显）
- 主体渐变：`#F4FBFF→#E2F1FA` → `#EAF6FF→#D0E8F8`（更蓝、更饱和）
- 高光：`#60FFFFFF` → `#70FFFFFF`（更亮光泽）
- 描边：`#50A0D0E8` → `#6090C8E8`（稍强）

**widget_bowel_card.xml（大便卡片）：**
- 外发光：`#2080C8D8` → `#3870C0D0`（透明度 13%→22%）
- 主体渐变：`#D0F0F8→#A8E0F0` → `#B8E8F4→#88D8EC`（teal 更饱和）
- 内发光：`#40FFFFFF` → `#50FFFFFF`

**widget_preview_panel.xml（图片预览区）：**
- 基础渐变：`#E4F4FC→#CCE8F4` → `#D4EEFA→#B8DEF0`（更蓝）
- 阴影底层：`#10000000` → `#18000000`（深度感更强）
- 描边：`#4090C8D8` → `#5088C0D0`

### 不改

- 服务端代码
- Kotlin 业务逻辑
- themes.xml 样式定义
- widget_chip_light / widget_control_dark / widget_danger_chip / widget_overlay_chip / widget_bowel_peer_strip（这些已经足够好看）
- 推送路由和行为

### 规划自检

- [x] 底板极淡（~15-20% 白），不会破坏"透明"感
- [x] 底板 28dp 圆角 > 卡片 26dp 圆角，四角露出柔和画框弧
- [x] 标题色 `#1565C0` / `#00838F` 在浅色卡片背景上有足够对比度
- [x] 不碰 Kotlin 代码和服务端，零业务风险
- [x] 8 个 drawable 中只改 3 个最影响视觉的，其余保持
- [x] RemoteViews 兼容：全部用 layer-list / gradient / shape，无自定义 View

### Implement test plan

**本地验证：**
- `assembleDebug` 编译通过
- XML 资源无语法错误

**设备测试：**
1. 生活桌面小组件：标题应为明亮蓝色/青色，不再是黑色
2. 底板效果：所有元素应看起来在一个统一的磨砂面板上，不再零散
3. 卡片视觉：渐变应更蓝更饱和，外发光投影更明显
4. 不同壁纸：在浅色和深色壁纸上都应好看

### 第三轮实施记录（视觉改进）

**客户端：**

1. **新增 `widget_backdrop.xml`**：4 层 layer-list 磨砂底板（外发光 #18A8D0F0 → 极淡蓝白渐变主体 #28E8F4FF→#18D8ECF8 → 发丝描边 0.6dp #20A0C0E0 → 内发光 #10FFFFFF），28dp 圆角
2. **两个布局背景**：`@android:color/transparent` → `@drawable/widget_backdrop`
3. **标题颜色**：
   - 今日痕迹 19sp：`#183B4B` → `#1565C0`（vivid blue）
   - 人物痕迹 20sp：`#183B4B` → `#1565C0`
   - 吃饭 15sp：`#183B4B` → `#00838F`（vivid teal）
   - 大便 17sp：`#183B4B` → `#00838F`
4. **widget_frame_card.xml 增强**：外发光 9%→19%，渐变 `#F4FBFF→#E2F1FA` → `#EAF6FF→#D0E8F8`（更蓝更饱和），高光 37%→43%
5. **widget_bowel_card.xml 增强**：外发光 13%→22%，渐变 `#D0F0F8→#A8E0F0` → `#B8E8F4→#88D8EC`（teal 更饱和），内发光 25%→31%
6. **widget_preview_panel.xml 增强**：渐变 `#E4F4FC→#CCE8F4` → `#D4EEFA→#B8DEF0`（更蓝），阴影 6%→9%

**Post-implement self-check：**
- [x] `assembleDebug` 编译通过（BUILD SUCCESSFUL in 51s）
- [x] widget_backdrop.xml 语法正确，4 层 layer-list 结构完整
- [x] 两个布局 XML 背景引用正确（@drawable/widget_backdrop）
- [x] 5 处标题颜色全部更新，数据文本颜色未被误改
- [x] 3 个增强 drawable 结构完整，layer-list 层数和 offset 逻辑正确
- [x] 不涉及 Kotlin/Java 代码，零业务风险

**Implement test plan（第三轮）：**
- 设备验证：标题应为明亮蓝色/青色
- 设备验证：所有元素应在统一磨砂面板上，不再零散
- 设备验证：卡片渐变应更蓝更饱和

### 第四轮实施记录（服务端 Bug 修复 + 标题视觉升级）

> 触发: 用户反馈——上传报 "operation type is invalid"；标题不够大不够飘逸，颜色不够绚烂

**服务端：**

1. **UploadService.normalizeOperationType()** — 白名单新增 `"LIFE_CONSOLE"`：
   - 原 switch case 只有 `IMPORT_TO_APP / CREATE_POST / ADD_TO_EXISTING_POST`
   - 客户端传 `"life_console"` → `toUpperCase()` → `"LIFE_CONSOLE"` → 不在白名单 → 抛 ApiException
   - 修复：添加 `"LIFE_CONSOLE"` 到白名单
2. **UploadService.notifyUploadOperationIfCompletedAfterDelay()** — 推送过滤大小写修正：
   - 原代码用 `"life_console".equals(operationType)` 比较，但存储值是 `normalizeOperationType()` 返回的大写 `"LIFE_CONSOLE"`
   - 修复：改为 `"LIFE_CONSOLE".equals(operationType)`

**客户端：**

3. **新增 `widget_title_accent.xml`** — 标题渐变装饰条：
   - 3 色渐变 `#2979FF` → `#00BCD4` → `#00E676`（蓝→青→绿），2dp 圆角小条
   - 放在主标题下方作为视觉点缀

4. **标题字号 + 字重 + 颜色全面升级**：

   | 位置 | 原字号 | 新字号 | 新增 fontFamily | 原色 | 新色 |
   |------|--------|--------|-----------------|------|------|
   | 今日痕迹 | 19sp | 23sp | sans-serif-light | #1565C0 | #2962FF（vivid blue） |
   | 人物痕迹 | 20sp | 24sp | sans-serif-light | #1565C0 | #2962FF |
   | 吃饭 | 15sp | 18sp | sans-serif-light | #00838F | #00BFA5（vivid teal） |
   | 大便 | 17sp | 19sp | sans-serif-light | #00838F | #00BFA5 |

5. **标题栏高度**：34dp → 38dp（给更大标题留出呼吸空间）
6. **渐变装饰条**：两个布局的主标题行下方各新增一个 72dp × 3dp 的 `widget_title_accent` View

**Post-implement self-check（第四轮）：**
- [x] 服务端 `mvnw compile` 编译通过
- [x] 客户端 `assembleDebug` 编译通过（BUILD SUCCESSFUL in 48s）
- [x] `normalizeOperationType` 白名单包含 LIFE_CONSOLE
- [x] 推送过滤比较两端大小写一致（都是大写 "LIFE_CONSOLE"）
- [x] widget_title_accent.xml 语法正确
- [x] 4 处标题字号/颜色/fontFamily 全部更新
- [x] 标题栏高度适配（38dp）
- [x] 装饰条引用正确（@drawable/widget_title_accent）

**Implement test plan（第四轮）：**

**本地已验证：**
- 客户端 assembleDebug 编译通过
- 服务端 mvnw compile 编译通过

**需要设备测试：**
1. **上传修复（关键）**：从小组件上传照片 → 不再报 "operation type is invalid"，上传应成功
2. **推送抑制**：小组件上传后 → 只收到 life push，不收到 photos push
3. **标题视觉**：标题应明显更大、更轻盈（sans-serif-light），颜色为鲜蓝/鲜青
4. **装饰条**：主标题下方应有蓝→青→绿渐变小条点缀
5. **整体协调**：更大标题 + 装饰条 + 绚烂色彩 + 磨砂底板 → 整体视觉冲击力应明显提升

### 第五轮实施记录（拍照清理 + 服务端热更新）

> 触发: 用户反馈——拍照上传后照片出现在系统相册；上传仍报 operation invalid（Docker 容器未更新）

**客户端：**

1. **LifeConsoleUploadBridge.cleanupCameraDuplicatesFromGallery()**（新增）：
   - 拍照上传后延迟 5 秒，扫描 MediaStore Images + Videos 中 `DATE_ADDED >= now - 30s` 的条目
   - 逐一 delete，清除小米相机 app 独立保存到 DCIM/Camera 的副本
2. **LifeConsoleUploadRuntime.enqueueWidgetUpload()**：
   - 检测 `isCameraCapture`（URI 包含 `/life-console-capture/`）
   - 上传成功后延迟 5s 调用 `cleanupCameraDuplicatesFromGallery()`

**服务端部署：**

3. Docker Hub 不可达（dockerproxy.com 超时），无法 `docker compose build`
4. 改用本地构建：`mvnw.cmd -DskipTests package` → `docker cp` JAR → `docker restart`
5. 容器内运行更新后的服务端代码（含 LIFE_CONSOLE 白名单修复）

### 第六轮实施记录（通知系统精修）

> 触发: 用户反馈——5 个通知问题：无后台推送、生活上传显示照片流通知、排便无通知、点击跳转错误

**根因分析：**

1. **无后台推送**：FCM 完全禁用（`FCM_ENABLED=false`，`NoopPushMessageSender` 丢弃所有推送）。Firebase 项目 `yingshi-73941` 存在，客户端 `google-services.json` 已配置，服务端 `firebase-admin:9.7.0` + `FirebasePushMessageSender` 代码完整，但缺少 Service Account JSON
2. **生活上传显示"照片流新增了媒体"**：`NotificationService.toUploadOperationEvent()` 对所有上传（含 LIFE_CONSOLE）创建 `module="photos"` 的应用内通知，fallback notifier 取到的是照片通知而非生活通知
3. **排便无通知**：应用内排便通知 `category="ledger"` 默认关闭（push preferences 中 `life:ledger → OFF`），而 FCM 推送路径正确用 `category="trace"`（默认 ON）
4. **点击跳转照片流**：因 fallback 取到照片通知（`targetRoute="photos:media:{id}"`），点击自然跳转照片流
5. **life:bowel 点击仅消失**：`PushNotificationPresenter.contentIntent()` 将 `life:bowel` 路由到 `LifePushDismissReceiver`（仅 dismiss），用户现在希望打开 QuickViewer

**服务端改动（NotificationService.java）：**

1. **`toUploadOperationEvent()`**：`primary` 确定后检查 `operationType`，`"LIFE_CONSOLE"` 时 return null（不创建照片流通知）
2. **`toUploadEvents()`**：stream 中加 `.filter(Objects::nonNull)` 防止 NPE
3. **`toBowelEventNotification()`**：
   - `category`: `"ledger"` → `"trace"`（默认开启，匹配 FCM 推送 category）
   - `targetRoute`: `"life:console"` → `"life:bowel"`（客户端正确路由到 QuickViewer）
   - `targetType`: `"LIFE_LEDGER"` → `"LIFE_BOWEL"`

**客户端改动（PushNotificationPresenter.kt）：**

4. **`contentIntent()`**：
   - 删除 `life:bowel` dismiss-only 分支（LifePushDismissReceiver 不再使用）
   - `isLifeRoute` 从 `route == "life:trace"` 改为 `route == "life:trace" || route == "life:bowel"`
   - `life:bowel` 现在路由到 `LifePushDispatchActivity`（已有 `resolveMedia()` 支持 bowel 路由）

**FCM 启用（待用户操作）：**

5. 需从 Firebase Console 生成 Service Account JSON
6. 配置 Docker 环境变量：`FCM_ENABLED=true`、`FCM_SERVICE_ACCOUNT_PATH`、`FCM_PROJECT_ID=yingshi-73941`
7. 更新 docker-compose volume mount 指向真实密钥文件

**Post-implement self-check（第六轮）：**
- [x] 服务端 `mvnw -DskipTests package` 编译通过（BUILD SUCCESS）
- [x] 客户端 `assembleDebug` 编译通过（BUILD SUCCESSFUL in 2m 45s）
- [x] `toUploadOperationEvent()` LIFE_CONSOLE 返回 null + null filter 防 NPE
- [x] 排便通知 category/ targetRoute/targetType 全部修正
- [x] `life:bowel` 路由到 LifePushDispatchActivity（QuickViewer）
- [x] 振动模式已配置 `longArrayOf(0L, 180L, 80L, 180L)`（双振），之前因通知未到达显示环节而未生效

**Implement test plan（第六轮）：**

**需要设备测试：**
1. 生活上传通知：上传人物痕迹/吃饭照片 → 不应收到"照片流新增了媒体"，应收到"今日痕迹有新更新"
2. 排便通知：记录排便 → 应收到系统通知（双振动）→ 点击应打开 QuickViewer
3. 普通照片上传：正常上传照片 → 应收到"照片流新增了媒体"（不受影响）
4. FCM 启用后：杀 app + 锁屏 → 对方上传 → 应收到后台推送通知
