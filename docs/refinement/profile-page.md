# profile-page 精修 Brief

> 状态: closed
> 模块: 个人主页 / 编辑资料
> 日期: 2026-06-29

## 模块目标

将个人主页和编辑资料页从"功能可用但视觉简陋"提升到"有氛围感、有质感、上传稳定"的完成度。

## 已交付

### 客户端（4 文件）
- **ProfileAvatarComponents.kt** — 新增 GradientRingAvatar（sweepGradient 彩环：primaryContainer → memoryAccent → goldAccent → glassStroke）
- **ProfileScreens.kt** — PersonalProfileScreen / EditProfileScreen 整体重写：YingShiMistBackground(ME, showWaves=true) + cover 渐变条 + GradientRingAvatar + YingShiMistCard 表单 + YingShiPrimaryMistButton + 自定义 EditBioField（多行）+ 10MB 客户端预检 + yingShiRouteReveal/yingShiSoftReveal 动画
- **MyScreen.kt** — SpaceIdentityCard 头像升级为 GradientRingAvatar(88dp) + yingShiMemoryGlow(warm=true)；PartnerCard 升级为 GradientRingAvatar(50dp, ringWidth=2dp)
- PartnerSection 暖色差异化：memoryWash 底色 + Favorite 心形 icon + GradientRingAvatar(64dp)

### 服务端（1 文件）
- **LocalMediaStorageService.java** — storeAvatarImage 加 10MB 文件大小上限 + 超过 512px 等比缩放（bilinear interpolation）

## 未做（明确排除）

- 头像上传进度条
- 个人主页加入时间等元信息展示

## 契约

- PATCH api/auth/me/profile — 不变
- POST api/auth/me/avatar — 不变（服务端内部加 resize + 10MB 限制）
- GET api/auth/avatar/{userId} — 不变

## 残留风险

- sweepGradient 环视觉可能需要看设备实机效果微调（色宽/环宽）
- Cover 条高度（88dp / 64dp）在不同密度屏幕上可能需要调整
- EditBioField 的 BasicTextField placeholder 对齐可能需要微调

## Carry-Forward

- GradientRingAvatar 现在被 5 处使用（MyScreen×2、PersonalProfileScreen×2、EditProfileScreen×1），如果后续要统一调整环样式，只改 ProfileAvatarComponents.kt 一处
- 服务端 resize 只影响新上传的头像，已存的旧头像不会自动 resize（无需处理，显示时 Coil 会自动缩放）

## Closeout Self-Check

- [x] 所有 P0 + P1 项已实施
- [x] P2 中呼吸光晕已实施，进度条和元信息明确不做
- [x] API 契约无漂移
- [x] 无新依赖引入
- [x] 所有 motion modifier 尊重 reduced-motion 设置
- [x] 回滚安全：不改 DB schema
- [x] verify 阶段发现的 MyScreen 缺彩环问题已修复
