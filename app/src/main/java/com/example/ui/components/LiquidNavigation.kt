package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CinematicRed
import com.example.ui.theme.GlassHighlight
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

data class LiquidNavItem(
    val id: String,
    val label: String,
    val icon: ImageVector
)

@Composable
fun LiquidNavigationBar(
    items: List<LiquidNavItem>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    val safeSelected = selectedIndex.coerceIn(0, items.lastIndex)

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        val density = LocalDensity.current
        val fullWidthPx = with(density) { maxWidth.toPx() }
        val tabWidthPx = (fullWidthPx / items.size).coerceAtLeast(1f)
        val lastIndex = (items.size - 1).toFloat()

        val indicator = remember { Animatable(safeSelected.toFloat()) }
        val selectionStretch = remember { Animatable(0f) }
        var dragValue by remember { mutableStateOf<Float?>(null) }
        var dragStretch by remember { mutableFloatStateOf(0f) }
        val stretch by animateFloatAsState(
            targetValue = if (dragValue != null) dragStretch else 0f,
            animationSpec = spring(dampingRatio = 0.55f, stiffness = 420f),
            label = "nav-stretch"
        )
        val scope = rememberCoroutineScope()

        LaunchedEffect(safeSelected) {
            if (dragValue == null) {
                val distance = abs(safeSelected - indicator.value)
                selectionStretch.snapTo(
                    (distance * tabWidthPx * 0.18f).coerceAtMost(tabWidthPx * 0.24f)
                )
                launch {
                    indicator.animateTo(
                        targetValue = safeSelected.toFloat(),
                        animationSpec = spring(dampingRatio = 0.74f, stiffness = 380f)
                    )
                }
                selectionStretch.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(dampingRatio = 0.58f, stiffness = 420f)
                )
            }
        }

        val position = dragValue ?: indicator.value
        val indicatorWidth = tabWidthPx + max(stretch, selectionStretch.value)
        val indicatorX = position * tabWidthPx + (tabWidthPx - indicatorWidth) / 2f

        Box(
            Modifier
                .fillMaxWidth()
                .height(62.dp)
                .liquidGlass(radius = 34.dp, tint = CinematicRed, alpha = 0.30f, elevation = 8.dp)
                .pointerInput(tabWidthPx, items.size) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            dragValue = indicator.value
                            dragStretch = 0f
                            scope.launch { selectionStretch.snapTo(0f) }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val current = (dragValue ?: indicator.value) + dragAmount / tabWidthPx
                            dragValue = current.coerceIn(0f, lastIndex)
                            dragStretch = (abs(dragAmount) * 2.2f).coerceIn(0f, tabWidthPx * 0.30f)
                        },
                        onDragEnd = {
                            val current = dragValue ?: indicator.value
                            val target = current.roundToInt().coerceIn(0, items.lastIndex)
                            dragStretch = 0f
                            scope.launch {
                                indicator.snapTo(current)
                                dragValue = null
                                indicator.animateTo(
                                    targetValue = target.toFloat(),
                                    animationSpec = spring(dampingRatio = 0.65f, stiffness = 360f)
                                )
                            }
                            if (target != safeSelected) onSelected(target)
                        },
                        onDragCancel = {
                            val current = dragValue ?: indicator.value
                            dragStretch = 0f
                            scope.launch {
                                indicator.snapTo(current)
                                dragValue = null
                                indicator.animateTo(
                                    targetValue = safeSelected.toFloat(),
                                    animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f)
                                )
                            }
                        }
                    )
                }
        ) {
            // Stretching liquid selector.
            Box(
                Modifier
                    .offset { IntOffset(indicatorX.roundToInt(), 0) }
                    .width(with(density) { indicatorWidth.toDp() })
                    .fillMaxHeight()
                    .padding(vertical = 5.dp)
                    .liquidGlass(
                        radius = 28.dp,
                        tint = CinematicRed,
                        alpha = 0.48f,
                        elevation = 6.dp
                    )
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                GlassHighlight.copy(alpha = 0.24f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )

            Row(
                Modifier
                    .fillMaxSize()
                    .selectableGroup()
            ) {
                items.forEachIndexed { index, item ->
                    val selected = index == safeSelected
                    val iconScale by animateFloatAsState(
                        targetValue = if (selected) 1.10f else 1f,
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
                        label = "nav-icon-${item.id}"
                    )
                    val labelAlpha by animateFloatAsState(
                        targetValue = if (selected) 1f else 0.72f,
                        label = "nav-label-${item.id}"
                    )
                    // The tab background is (and stays) transparent: the only
                    // selection cue is the spring-driven liquid pill above. The
                    // default M3 ripple drew an un-clipped square over the glass
                    // pill on every tap, so indication is disabled explicitly.
                    val interactionSource = remember(item.id) { MutableInteractionSource() }
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .testTag("nav_${item.id}")
                            .selectable(
                                selected = selected,
                                interactionSource = interactionSource,
                                indication = null,
                                role = Role.Tab,
                                onClick = { onSelected(index) }
                            ),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = if (selected) Color.White else TextSecondary,
                            modifier = Modifier
                                .size(21.dp)
                                .graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                }
                        )
                        Text(
                            text = item.label,
                            color = if (selected) Color.White else TextSecondary,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.graphicsLayer { alpha = labelAlpha }
                        )
                    }
                }
            }
        }
    }
}
