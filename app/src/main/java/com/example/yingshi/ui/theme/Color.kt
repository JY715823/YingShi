package com.example.yingshi.ui.theme

import androidx.compose.ui.graphics.Color

val YingShiAppBackground = Color(0xFFF1FBFD)
val YingShiSectionBackground = Color(0xFFDFF5F4)
val YingShiRaisedSurface = Color(0xFFFFFFFC)
val YingShiSelectedPillBg = Color(0xFFBDEFFF)
val YingShiPrimaryContainer = Color(0xFFBDEFFF)
val YingShiOnPrimaryContainer = Color(0xFF1F2933)
val YingShiPrimaryAction = Color(0xFFBDEFFF)
val YingShiPrimaryActionPressed = Color(0xFFA7E9FF)
val YingShiTitleAccent = Color(0xFF26313A)
val YingShiSoftGreenContainer = Color(0xFFD8F2E6)
val YingShiSoftGreenAction = Color(0xFF26313A)
val YingShiGoldAccent = Color(0xFF9A6A2A)
val YingShiMemoryAccent = Color(0xFFA2473D)
val YingShiMemoryContainer = Color(0xFFFFE1DA)
val YingShiOnMemoryContainer = Color(0xFF3A2724)
val YingShiMemoryWash = Color(0xFFFFF1EE)
val YingShiDividerSoft = Color(0xFFC7E6EC)
val YingShiGlassStroke = Color(0xFFA9E5F2)
val YingShiGlowWash = Color(0xFFE7FAFF)
val YingShiTextPrimary = Color(0xFF1F2933)
val YingShiTextSecondary = Color(0xFF556B75)

val YingShiViewerBackground = Color(0xFF101F26)
val YingShiViewerSurface = Color(0xFF1D333C)
val YingShiViewerAccent = Color(0xFFBDEFFF)
val YingShiViewerText = Color(0xFFF4FBFC)
val YingShiViewerTextSecondary = Color(0xFFA9C3CC)

// Viewer Overlay 材质 token（派生自 viewerAccent，不引入新色相）
val YingShiViewerOverlayEdgeGlow = YingShiViewerAccent.copy(alpha = 0.15f)
val YingShiViewerOverlayBorder = YingShiViewerAccent.copy(alpha = 0.22f)

val YingShiBlue = YingShiPrimaryContainer
val YingShiBlueLight = YingShiPrimaryContainer
val YingShiAirBlue = YingShiSectionBackground
val YingShiBlueGray = YingShiTextSecondary
val YingShiMist = YingShiSectionBackground
val YingShiMistLight = YingShiAppBackground
val YingShiSurface = YingShiRaisedSurface
val YingShiSurfaceRaised = YingShiRaisedSurface
val YingShiBackground = YingShiAppBackground
val YingShiInk = YingShiTextPrimary
val YingShiMuted = YingShiTextSecondary
val YingShiDivider = YingShiDividerSoft
val YingShiLifeTint = YingShiSoftGreenContainer

val YingShiBlueDark = YingShiTitleAccent
val YingShiNight = YingShiViewerBackground
val YingShiNightSurface = YingShiViewerSurface
val YingShiNightMuted = YingShiViewerTextSecondary
val YingShiNightDivider = Color(0xFF2F4850)

// 标题光晕颜色
val YingShiTitleAuraWarmGlow = Color(0xD0FFF4D9)
val YingShiTitleAuraCoolGlow = Color(0xC4AEEBFF)
val YingShiTitleAuraWarmSparkle = Color(0xFFFFE8C6)
val YingShiTitleAuraCoolSparkle = Color(0xFFB4F5FF)
val YingShiTitleAuraBottomCool = Color(0x99CBEFFF)
val YingShiTitleAuraBottomWarm = Color(0x7CFFEAC2)

// 标题文字层颜色
val YingShiTitleTextShadowGlow = Color(0xFF85DFFF)
val YingShiTitleTextShadowWarm = Color(0xFFFFE8C6)
val YingShiTitleTextGlowWarm = Color(0xFFFFF2DE)
val YingShiTitleTextShadowCool = Color(0xFFB4F5FF)
val YingShiTitleTextGlowCool = Color(0xFFD7FBFF)

// Destructive 色系（匹配 Material3 默认 Light 主题 error 色）
val YingShiDestructiveContainer = Color(0xFFFFDAD4)
val YingShiDestructive = Color(0xFFBA1A1A)
val YingShiOnDestructiveContainer = Color(0xFF410002)

// 玻璃表面
val YingShiGlassSurfaceBase = Color.White.copy(alpha = 0.62f)
