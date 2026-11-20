package com.example.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val GlassSpacing = 12.dp
private val glassFill = Brush.verticalGradient(listOf(GlassHighlight.copy(alpha = .12f), ObsidianCard.copy(alpha = .96f)))

fun Modifier.glass(radius: Dp = 18.dp, elevation: Dp = 0.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return shadow(elevation, shape).clip(shape).background(ObsidianCard.copy(alpha = .91f))
        .background(glassFill).border(1.dp, GlassHighlight.copy(alpha = .24f), shape)
}
