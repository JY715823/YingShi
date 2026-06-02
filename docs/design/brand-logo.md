# 映世 Logo 与品牌标志

Updated: 2026-06-02

## 核心概念

映世的标志以“映照记忆”为核心意象。主图形由两张轻微错位的圆角相片 / 记忆页组成，表达双人共同空间；前景中间的柔和折光线表达“映照”；右下角的小暖色记忆点用于保留生活记录里的温度。

标志应保持安静、亲密、可信，服务私密相册和生活记录，而不是社交 feed、情侣符号、效率工具或高饱和概念海报。

## 颜色

| 角色 | 色值 | 用法 |
| --- | --- | --- |
| 珍珠浅蓝 | `#F1FBFD` | App 图标背景与轻色品牌底 |
| 清透浅蓝 | `#BDEFFF` | 选中、光面、映照感 |
| 珍珠白 | `#FFFFFC` | 记忆页 / 相片面 |
| 石墨强调 | `#26313A` | 标志主形、结构强调 |
| 浅玉绿 | `#D8F2E6` | 图形内的轻辅助层 |
| 暖记忆色 | `#A94C42` | 小状态点、情绪记忆点 |
| 旧金细节 | `#9A6A2A` | 低频细节，不作为主色 |

## 资源位置

- Adaptive icon 背景：`app/src/main/res/drawable/ic_launcher_background.xml`
- Adaptive icon 前景：`app/src/main/res/drawable/ic_launcher_foreground.xml`
- Android 13+ 主题图标：`app/src/main/res/drawable/ic_launcher_monochrome.xml`
- Adaptive icon 壳：`app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Legacy launcher PNG：`app/src/main/res/mipmap-mdpi` 到 `mipmap-xxxhdpi`

## 使用建议

- Launcher 图标只使用图形标志，不放入“映世”或 `YingShi` 文字。
- 品牌展示、启动页、关于页可使用“映世”中文主字标，必要时在下方小字号搭配 `YingShi`。
- 图形标志优先放在浅色、低噪声背景上；如果用于深色 Viewer 场景，应使用单色或珍珠白版本。
- 小尺寸使用时保留相片轮廓、折光线和记忆点，不增加新装饰。

## 禁用项

- 不使用心形、人物头像、情侣剪影等直白恋爱符号。
- 不把背景改成全局深蓝、荧光蓝紫或高饱和渐变。
- 不在 launcher 图标里放中文或英文小字。
- 不把暖记忆色扩大为大面积主视觉。
- 不增加复杂照片纹理、滤镜、玻璃堆叠或过重阴影。
