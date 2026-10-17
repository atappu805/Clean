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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
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
import com.saurav.pixelmusic.ui.theme.LocalLiquidGlassBlurIntensity
import com.saurav.pixelmusic.ui.theme.LocalLiquidGlassRefractionIntensity
import com.saurav.pixelmusic.ui.theme.GoogleSansRounded
import kotlinx.coroutines.launch

@Composable
fun LiquidGlassHeaderBar(
    pillText: String,
    onPillClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    pillIcon: (@Composable () -> Unit)? = null,
    pillTextSize: androidx.compose.ui.unit.TextUnit = 20.sp,
    isLiquidGlass: Boolean = true,
    backdrop: Backdrop? = null,
    alpha: Float = 1f
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val glassBlurIntensity = LocalLiquidGlassBlurIntensity.current
    val glassRefractionIntensity = LocalLiquidGlassRefractionIntensity.current

    // iOS Liquid Glass specular rim gradient
    val glassBorderBrush = remember(isDark) {
        Brush.verticalGradient(
            colors = if (isDark) listOf(
                Color.White.copy(alpha = 0.07f),
                Color.White.copy(alpha = 0.02f)
            ) else listOf(
                Color.White.copy(alpha = 0.14f),
                Color.White.copy(alpha = 0.04f)
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
        val headerBarHeight = if (pillTextSize >= 24.sp) 52.dp else 48.dp

        val pillModifier = if (isGlassActive) {
            Modifier
                .height(headerBarHeight)
                .graphicsLayer {
                    scaleX = pillScaleX
                    scaleY = pillScaleY
                }
                .drawBackdrop(
                    backdrop = activeBackdrop,
                    shape = { pillShape },
                    effects = {
                        vibrancy()
                        val blurRadius = (10.dp.toPx() * glassBlurIntensity).coerceAtLeast(1f)
                        blur(blurRadius)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && glassRefractionIntensity > 0.01f) {
                            lens(
                                refractionHeight = 18.dp.toPx() * glassRefractionIntensity,
                                refractionAmount = 42.dp.toPx() * glassRefractionIntensity
                            )
                        }
                    },
                    onDrawSurface = {
                        drawRect(if (isDark) Color(0x351E1E22) else Color(0x40FFFFFF))
                    }
                )
                .border(BorderStroke(0.75.dp, glassBorderBrush), pillShape)
                .clip(pillShape)
        } else {
            Modifier
                .height(headerBarHeight)
                .graphicsLayer {
                    scaleX = pillScaleX
                    scaleY = pillScaleY
                }
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
                                    Color.White.copy(alpha = if (isDark) 0.04f else 0.08f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (pillIcon != null) 10.dp else 0.dp)
            ) {
                if (pillIcon != null) {
                    pillIcon()
                }
                Text(
                    text = pillText,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = pillTextSize,
                    letterSpacing = 0.5.sp
                )
            }
        }

        // ── 2. SETTINGS ICON BUTTON ──────────────────────────────────
        val settingsShape = CircleShape
        val settingsModifier = if (isGlassActive) {
            Modifier
                .size(headerBarHeight)
                .graphicsLayer {
                    scaleX = settingsScaleX
                    scaleY = settingsScaleY
                }
                .drawBackdrop(
                    backdrop = activeBackdrop,
                    shape = { settingsShape },
                    effects = {
                        vibrancy()
                        val blurRadius = (10.dp.toPx() * glassBlurIntensity).coerceAtLeast(1f)
                        blur(blurRadius)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && glassRefractionIntensity > 0.01f) {
                            lens(
                                refractionHeight = 18.dp.toPx() * glassRefractionIntensity,
                                refractionAmount = 42.dp.toPx() * glassRefractionIntensity
                            )
                        }
                    },
                    onDrawSurface = {
                        drawRect(if (isDark) Color(0x351E1E22) else Color(0x40FFFFFF))
                    }
                )
                .border(BorderStroke(0.75.dp, glassBorderBrush), settingsShape)
                .clip(settingsShape)
        } else {
            Modifier
                .size(headerBarHeight)
                .graphicsLayer {
                    scaleX = settingsScaleX
                    scaleY = settingsScaleY
                }
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
                                    Color.White.copy(alpha = if (isDark) 0.04f else 0.08f),
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
