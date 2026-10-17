package com.saurav.pixelmusic.presentation.components

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.saurav.pixelmusic.R
import com.saurav.pixelmusic.ui.theme.GoogleSansRounded
import kotlinx.coroutines.launch

@Composable
fun LiquidGlassHeaderBar(
    pillText: String,
    onPillClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    pillIcon: @Composable () -> Unit,
    isLiquidGlass: Boolean = true,
    backdrop: Backdrop? = null,
    alpha: Float = 1f
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    // iOS Liquid Glass specular rim gradient
    val glassBorderBrush = remember(isDark) {
        Brush.verticalGradient(
            colors = if (isDark) listOf(
                Color.White.copy(alpha = 0.45f),
                Color.White.copy(alpha = 0.10f)
            ) else listOf(
                Color.White.copy(alpha = 0.85f),
                Color.White.copy(alpha = 0.35f)
            )
        )
    }

    // Interactive press and jiggle animatables for the pill
    val pillPress = remember { Animatable(0f) }
    val pillJiggle = remember { Animatable(0f) }
    val pillScaleX = 1f + (pillPress.value * 0.08f) + (pillJiggle.value * 0.12f)
    val pillScaleY = 1f - (pillPress.value * 0.06f) - (pillJiggle.value * 0.10f)

    // Interactive press and jiggle animatables for the settings button
    val settingsPress = remember { Animatable(0f) }
    val settingsJiggle = remember { Animatable(0f) }
    val settingsScaleX = 1f + (settingsPress.value * 0.10f) + (settingsJiggle.value * 0.15f)
    val settingsScaleY = 1f - (settingsPress.value * 0.08f) - (settingsJiggle.value * 0.13f)

    Row(
        modifier = modifier
            .graphicsLayer { this.alpha = alpha },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // ── 1. HEADER PILL ──────────────────────────────────────────
        val pillShape = CircleShape
        val activeBackdrop = backdrop
        val isGlassActive = isLiquidGlass && activeBackdrop != null

        val pillModifier = if (isGlassActive) {
            Modifier
                .graphicsLayer {
                    scaleX = pillScaleX
                    scaleY = pillScaleY
                }
                .shadow(elevation = 10.dp, shape = pillShape)
                .drawBackdrop(
                    backdrop = activeBackdrop,
                    shape = { pillShape },
                    effects = {
                        vibrancy()
                        blur(10.dp.toPx())
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            lens(
                                refractionHeight = 18.dp.toPx(),
                                refractionAmount = 42.dp.toPx()
                            )
                        }
                    },
                    onDrawSurface = {
                        drawRect(if (isDark) Color(0x351E1E22) else Color(0x40FFFFFF))
                    }
                )
                .border(BorderStroke(1.2.dp, glassBorderBrush), pillShape)
                .clip(pillShape)
        } else {
            Modifier
                .graphicsLayer {
                    scaleX = pillScaleX
                    scaleY = pillScaleY
                }
                .shadow(elevation = 0.dp, shape = pillShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f), pillShape)
                .clip(pillShape)
        }

        Box(
            modifier = pillModifier
                .pointerInput(alpha) {
                    val pressSpec = spring<Float>(dampingRatio = 0.5f, stiffness = 450f)
                    val jiggleSpec = spring<Float>(dampingRatio = 0.32f, stiffness = 280f)
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        coroutineScope.launch {
                            pillJiggle.snapTo(1f)
                            pillJiggle.animateTo(0f, jiggleSpec)
                        }
                        coroutineScope.launch { pillPress.animateTo(1f, pressSpec) }
                        val up = waitForUpOrCancellation()
                        coroutineScope.launch { pillPress.animateTo(0f, pressSpec) }
                        if (up != null && alpha > 0.05f) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onPillClick()
                        }
                    }
                }
        ) {
            if (isGlassActive) {
                // Top specular highlight sheen
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = if (isDark) 0.18f else 0.35f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                pillIcon()
                Text(
                    text = pillText,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 20.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }

        // ── 2. SETTINGS ICON BUTTON ──────────────────────────────────
        val settingsShape = CircleShape
        val settingsModifier = if (isGlassActive) {
            Modifier
                .size(44.dp)
                .graphicsLayer {
                    scaleX = settingsScaleX
                    scaleY = settingsScaleY
                }
                .shadow(elevation = 10.dp, shape = settingsShape)
                .drawBackdrop(
                    backdrop = activeBackdrop,
                    shape = { settingsShape },
                    effects = {
                        vibrancy()
                        blur(10.dp.toPx())
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            lens(
                                refractionHeight = 18.dp.toPx(),
                                refractionAmount = 42.dp.toPx()
                            )
                        }
                    },
                    onDrawSurface = {
                        drawRect(if (isDark) Color(0x351E1E22) else Color(0x40FFFFFF))
                    }
                )
                .border(BorderStroke(1.2.dp, glassBorderBrush), settingsShape)
                .clip(settingsShape)
        } else {
            Modifier
                .size(44.dp)
                .graphicsLayer {
                    scaleX = settingsScaleX
                    scaleY = settingsScaleY
                }
                .shadow(elevation = 0.dp, shape = settingsShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f), settingsShape)
                .clip(settingsShape)
        }

        Box(
            modifier = settingsModifier
                .pointerInput(alpha) {
                    val pressSpec = spring<Float>(dampingRatio = 0.5f, stiffness = 450f)
                    val jiggleSpec = spring<Float>(dampingRatio = 0.32f, stiffness = 280f)
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        coroutineScope.launch {
                            settingsJiggle.snapTo(1f)
                            settingsJiggle.animateTo(0f, jiggleSpec)
                        }
                        coroutineScope.launch { settingsPress.animateTo(1f, pressSpec) }
                        val up = waitForUpOrCancellation()
                        coroutineScope.launch { settingsPress.animateTo(0f, pressSpec) }
                        if (up != null && alpha > 0.05f) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSettingsClick()
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (isGlassActive) {
                // Top specular highlight sheen
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = if (isDark) 0.18f else 0.35f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }
            Icon(
                painter = painterResource(id = R.drawable.rounded_settings_24),
                contentDescription = stringResource(id = R.string.settings_top_bar_title),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
