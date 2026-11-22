package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CinematicRed
import com.example.ui.theme.GlassHighlight
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Translucent Midnight Amethyst surface with a specular edge. This is a
 * lightweight native-Compose glass treatment: it never blurs or samples the
 * video SurfaceView, and it degrades to a plain translucent gradient on every
 * API level. True backdrop lensing is intentionally not claimed here.
 */
fun Modifier.liquidGlass(
    radius: Dp = 22.dp,
    tint: Color = ObsidianCard,
    alpha: Float = 0.58f,
    elevation: Dp = 0.dp
): Modifier {
    val shape = RoundedCornerShape(radius)
    return shadow(elevation, shape, clip = false)
        .clip(shape)
        .background(
            Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.14f),
                    GlassHighlight.copy(alpha = 0.10f),
                    tint.copy(alpha = alpha),
                    Color.Black.copy(alpha = 0.18f)
                )
            )
        )
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.55f),
                    GlassHighlight.copy(alpha = 0.24f),
                    Color.Transparent
                )
            ),
            shape = shape
        )
}

@Composable
fun LiquidGlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Color = CinematicRed,
    enabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.955f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 600f),
        label = "glass-button-scale"
    )

    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .liquidGlass(radius = 24.dp, tint = accent, alpha = 0.52f, elevation = 5.dp)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(
            text = text,
            color = if (enabled) TextPrimary else TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun LiquidFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    val background by animateColorAsState(
        targetValue = if (selected) CinematicRed.copy(alpha = 0.62f) else ObsidianCard.copy(alpha = 0.62f),
        animationSpec = tween(220),
        label = "chip-fill"
    )
    val border by animateColorAsState(
        targetValue = if (selected) GlassHighlight.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.14f),
        animationSpec = tween(220),
        label = "chip-border"
    )
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(background)
            .border(1.dp, border, CircleShape)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else TextSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
fun LiquidGlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    size: Dp = 40.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .liquidGlass(radius = size / 2, alpha = 0.42f)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(size * 0.48f))
    }
}

@Composable
fun LiquidDots(
    modifier: Modifier = Modifier,
    color: Color = CinematicRed,
    dotSize: Dp = 7.dp
) {
    val transition = rememberInfiniteTransition(label = "liquid-dots")
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 620, delayMillis = index * 110),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot-alpha-$index"
            )
            Box(
                Modifier
                    .size(dotSize)
                    .background(color.copy(alpha = alpha), CircleShape)
            )
        }
    }
}

@Composable
fun CineVaultWordmark(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 26.sp
) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = TextPrimary)) { append("Cine") }
            withStyle(SpanStyle(color = CinematicRed)) { append("Vault") }
        },
        modifier = modifier,
        fontSize = fontSize,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.6).sp
    )
}
