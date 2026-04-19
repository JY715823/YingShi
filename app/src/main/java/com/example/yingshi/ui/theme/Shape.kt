package com.example.yingshi.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes

internal fun yingShiShapes(radius: YingShiRadius) = Shapes(
    small = RoundedCornerShape(radius.sm),
    medium = RoundedCornerShape(radius.md),
    large = RoundedCornerShape(radius.lg),
    extraLarge = RoundedCornerShape(radius.xl),
)
