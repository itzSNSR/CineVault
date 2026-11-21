package com.example.ui.components

import android.animation.ValueAnimator
import android.graphics.PathMeasure
import android.graphics.RectF
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.CinematicRed
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.GlassHighlight
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * CineVault mark that physically stretches into a Play triangle and back.
 * The mark is the existing raster brand asset; the morphing silhouette is a
 * sampled-path interpolation so no image swap is used for the transition.
 */
@Composable
fun CineVaultMorphLoader(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    showLogo: Boolean = true,
    showGlow: Boolean = true
) {
    val context = LocalContext.current
    val reducedMotion = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            !ValueAnimator.areAnimatorsEnabled()
        } else {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        }
    }
    val transition = rememberInfiniteTransition(label = "cinevault-morph")
    val animatedPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "morph-phase"
    )
    // When the system disables animations, hold the recognizable brand mark.
    val phase = if (reducedMotion) 0f else animatedPhase

    // 0 at the logo, 1 at the play mark, then back to 0 seamlessly.
    val morph = 1f - abs(phase * 2f - 1f)
    val logoAlpha = (1f - morph * 1.9f).coerceIn(0f, 1f)
    val playAlpha = ((morph - 0.12f) / 0.55f).coerceIn(0f, 1f)

    // Sample both silhouettes once; the frame loop only interpolates points.
    val morphFrom = remember { sampleShape(roundedShape(), 56) }
    val morphTo = remember { sampleShape(playShape(), 56) }
    val morphTarget = remember { Path() }

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        if (showGlow) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            CinematicRed.copy(alpha = 0.30f * playAlpha),
                            CrimsonRed.copy(alpha = 0.10f * playAlpha),
                            Color.Transparent
                        ),
                        center = center,
                        radius = this.size.minDimension * 0.52f
                    ),
                    radius = this.size.minDimension * 0.52f
                )
            }
        }

        if (showLogo) {
            Image(
                painter = painterResource(R.drawable.ic_cinevault_mark),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize(0.78f)
                    .graphicsLayer {
                        alpha = logoAlpha
                        val settle = 1f - 0.06f * sin(morph * PI).toFloat()
                        scaleX = settle
                        scaleY = settle
                        rotationZ = -3f * morph
                    }
            )
        }

        Canvas(Modifier.fillMaxSize(0.72f)) {
            if (playAlpha <= 0.01f) return@Canvas
            buildMorphPath(morphTarget, morph, morphFrom, morphTo)
            val brush = Brush.linearGradient(
                colors = listOf(GlassHighlight, CinematicRed, CrimsonRed),
                start = Offset.Zero,
                end = Offset(this.size.width, this.size.height)
            )

            // Soft liquid halo.
            drawPath(
                path = morphTarget,
                color = CinematicRed.copy(alpha = 0.22f * playAlpha),
                style = Stroke(width = 10.dp.toPx(), join = StrokeJoin.Round)
            )
            drawPath(path = morphTarget, brush = brush, alpha = playAlpha, style = Fill)
        }
    }
}

private fun roundedShape(): android.graphics.Path = android.graphics.Path().apply {
    addRoundRect(
        RectF(-0.72f, -0.72f, 0.72f, 0.72f),
        0.34f,
        0.34f,
        android.graphics.Path.Direction.CW
    )
}

private fun playShape(): android.graphics.Path = android.graphics.Path().apply {
    moveTo(-0.40f, -0.72f)
    lineTo(0.78f, 0.0f)
    lineTo(-0.40f, 0.72f)
    close()
}

private fun sampleShape(source: android.graphics.Path, samples: Int): Array<Offset> {
    val measure = PathMeasure(source, true)
    val length = measure.length.coerceAtLeast(0.001f)
    return Array(samples + 1) { index ->
        val position = FloatArray(2)
        measure.getPosTan(length * (index.toFloat() / samples), position, null)
        // Normalize the sampled silhouette into the canvas.
        Offset(position[0] * 0.5f + 0.5f, position[1] * 0.5f + 0.5f)
    }
}

private fun buildMorphPath(
    target: Path,
    amount: Float,
    from: Array<Offset>,
    to: Array<Offset>
) {
    target.reset()
    from.indices.forEach { index ->
        val a = from[index]
        val b = to[index]
        val x = a.x + (b.x - a.x) * amount
        val y = a.y + (b.y - a.y) * amount
        if (index == 0) target.moveTo(x, y) else target.lineTo(x, y)
    }
    target.close()
}

/** Compact, gesture-transparent buffering indicator for the player overlay. */
@Composable
fun BufferingMorphIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(58.dp)
            .liquidGlass(radius = 29.dp, alpha = 0.28f),
        contentAlignment = Alignment.Center
    ) {
        CineVaultMorphLoader(
            size = 40.dp,
            showLogo = true,
            showGlow = false,
            modifier = Modifier.fillMaxSize()
        )
    }
}
