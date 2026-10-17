package com.saurav.pixelmusic.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun LiquidGlassToggle(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val primaryColor = MaterialTheme.colorScheme.primary
    val coroutineScope = rememberCoroutineScope()
    val pressAnim = remember { Animatable(0f) }

    // iOS 52x32dp capsule track geometry
    val trackWidth = 52.dp
    val trackHeight = 32.dp
    val thumbBaseSize = 24.dp
    val trackPadding = 4.dp
    val maxOffset = trackWidth - thumbBaseSize - (trackPadding * 2)

    // Dynamic spring physics for thumb offset and stretch
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) maxOffset else 0.dp,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "toggle_thumb_offset"
    )

    // Thumb stretch width when finger presses down (iOS physics)
    val thumbWidth by animateDpAsState(
        targetValue = if (pressAnim.value > 0.1f) 28.dp else thumbBaseSize,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 450f),
        label = "toggle_thumb_width"
    )

    val trackShape = CircleShape
    val thumbShape = CircleShape

    val trackBgColor = if (checked) {
        if (enabled) primaryColor else primaryColor.copy(alpha = 0.5f)
    } else {
        if (isDark) Color(0x35FFFFFF) else Color(0x25000000)
    }

    val trackBorder = remember(checked, isDark, primaryColor) {
        if (checked) {
            BorderStroke(0.75.dp, primaryColor.copy(alpha = 0.40f))
        } else {
            BorderStroke(
                0.75.dp,
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (isDark) 0.15f else 0.25f),
                        Color.White.copy(alpha = if (isDark) 0.04f else 0.08f)
                    )
                )
            )
        }
    }

    Box(
        modifier = modifier
            .size(width = trackWidth, height = trackHeight)
            .graphicsLayer {
                val s = 1f - (pressAnim.value * 0.05f)
                scaleX = s
                scaleY = s
            }
            .clip(trackShape)
            .background(trackBgColor, trackShape)
            .border(trackBorder, trackShape)
            .then(
                if (enabled && onCheckedChange != null) {
                    Modifier.pointerInput(checked, enabled) {
                        val pressSpec = spring<Float>(dampingRatio = 0.6f, stiffness = 400f)
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            coroutineScope.launch { pressAnim.animateTo(1f, pressSpec) }
                            val up = waitForUpOrCancellation()
                            coroutineScope.launch { pressAnim.animateTo(0f, pressSpec) }
                            if (up != null) {
                                onCheckedChange(!checked)
                            }
                        }
                    }
                } else Modifier
            )
            .padding(trackPadding),
        contentAlignment = Alignment.CenterStart
    ) {
        // iOS Glass Thumb Circle
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .width(thumbWidth)
                .size(thumbBaseSize)
                .shadow(elevation = 3.dp, shape = thumbShape)
                .clip(thumbShape)
                .background(Color.White, thumbShape)
                .border(
                    BorderStroke(0.5.dp, Color.White.copy(alpha = 0.85f)),
                    thumbShape
                )
        )
    }
}
