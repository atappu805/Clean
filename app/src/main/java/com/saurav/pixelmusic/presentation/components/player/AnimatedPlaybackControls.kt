package com.saurav.pixelmusic.presentation.components.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.saurav.pixelmusic.presentation.components.LocalMaterialTheme
import kotlinx.coroutines.delay
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import android.os.Build
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme

private enum class PlaybackButtonType { NONE, PREVIOUS, PLAY_PAUSE, NEXT }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AnimatedPlaybackControls(
    isPlayingProvider: () -> Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 90.dp,
    baseWeight: Float = 1f,
    expansionWeight: Float = 1.1f,
    compressionWeight: Float = 0.65f,
    pressAnimationSpec: AnimationSpec<Float>,
    releaseDelay: Long = 220L,
    playPauseCornerPlaying: Dp = 60.dp,
    playPauseCornerPaused: Dp = 26.dp,
    colorOtherButtons: Color = LocalMaterialTheme.current.secondaryContainer,
    colorPlayPause: Color = LocalMaterialTheme.current.primary,
    tintPlayPauseIcon: Color = LocalMaterialTheme.current.onPrimary,
    tintOtherIcons: Color = LocalMaterialTheme.current.onSecondaryContainer,
    colorPreviousButton: Color = colorOtherButtons,
    colorNextButton: Color = colorOtherButtons,
    tintPreviousIcon: Color = tintOtherIcons,
    tintNextIcon: Color = tintOtherIcons,
    playPauseIconSize: Dp = 36.dp,
    iconSize: Dp = 32.dp,
    isLiquidGlass: Boolean = false,
    backdrop: Backdrop? = null,
) {
    val isPlaying = isPlayingProvider()
    var lastClicked by remember { mutableStateOf<PlaybackButtonType?>(null) }
    val latestIsPlayingProvider by rememberUpdatedState(newValue = isPlayingProvider)
    val latestLastClicked by rememberUpdatedState(newValue = lastClicked)
    val isPlayPauseLocked =
        lastClicked == PlaybackButtonType.NEXT || lastClicked == PlaybackButtonType.PREVIOUS
    var playPauseVisualState by remember { mutableStateOf(isPlaying) }
    var pendingPlayPauseState by remember { mutableStateOf<Boolean?>(null) }
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val prevPress = remember { Animatable(0f) }
    val prevJiggle = remember { Animatable(0f) }
    val prevScaleX = 1f + (prevPress.value * 0.10f) + (prevJiggle.value * 0.16f)
    val prevScaleY = 1f - (prevPress.value * 0.08f) - (prevJiggle.value * 0.14f)

    val playPausePress = remember { Animatable(0f) }
    val playPauseJiggle = remember { Animatable(0f) }
    val playPauseScaleX = 1f + (playPausePress.value * 0.10f) + (playPauseJiggle.value * 0.16f)
    val playPauseScaleY = 1f - (playPausePress.value * 0.08f) - (playPauseJiggle.value * 0.14f)

    val nextPress = remember { Animatable(0f) }
    val nextJiggle = remember { Animatable(0f) }
    val nextScaleX = 1f + (nextPress.value * 0.10f) + (nextJiggle.value * 0.16f)
    val nextScaleY = 1f - (nextPress.value * 0.08f) - (nextJiggle.value * 0.14f)

    LaunchedEffect(lastClicked) {
        if (lastClicked != null) {
            delay(releaseDelay)
            lastClicked = null
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            pendingPlayPauseState = true
            return@LaunchedEffect
        }

        val shouldDelay = latestLastClicked != PlaybackButtonType.PLAY_PAUSE
        if (shouldDelay) {
            delay(releaseDelay)
        }
        if (!latestIsPlayingProvider()) {
            pendingPlayPauseState = false
        }
    }

    LaunchedEffect(isPlayPauseLocked, pendingPlayPauseState) {
        if (!isPlayPauseLocked) {
            pendingPlayPauseState?.let {
                playPauseVisualState = it
                pendingPlayPauseState = null
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            fun weightFor(button: PlaybackButtonType): Float = when (lastClicked) {
                button -> expansionWeight
                null -> baseWeight
                else -> compressionWeight
            }

            val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
            val isGlassActive = isLiquidGlass && backdrop != null

            val prevShape = CircleShape
            val prevBorderBrush = remember(isDark) {
                Brush.verticalGradient(
                    colors = if (isDark) listOf(
                        Color.White.copy(alpha = 0.70f),
                        Color.White.copy(alpha = 0.20f)
                    ) else listOf(
                        Color.White.copy(alpha = 0.90f),
                        Color.White.copy(alpha = 0.35f)
                    )
                )
            }

            val prevWeight by animateFloatAsState(
                targetValue = weightFor(PlaybackButtonType.PREVIOUS),
                animationSpec = pressAnimationSpec,
                label = "prevWeight"
            )
            Box(
                modifier = Modifier
                    .weight(prevWeight)
                    .fillMaxHeight()
                    .graphicsLayer {
                        scaleX = prevScaleX
                        scaleY = prevScaleY
                    }
                    .then(
                        if (isGlassActive) {
                            Modifier
                                .shadow(elevation = 10.dp, shape = prevShape)
                                .drawBackdrop(
                                    backdrop = backdrop,
                                    shape = { prevShape },
                                    effects = {
                                        vibrancy()
                                        blur(12.dp.toPx())
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            lens(
                                                refractionHeight = 14.dp.toPx(),
                                                refractionAmount = 30.dp.toPx()
                                            )
                                        }
                                    },
                                    onDrawSurface = {
                                        drawRect(if (isDark) Color(0x351E1E22) else Color(0x40FFFFFF))
                                    }
                                )
                                .border(BorderStroke(1.2.dp, prevBorderBrush), prevShape)
                                .clip(prevShape)
                        } else {
                            Modifier
                                .clip(prevShape)
                                .background(colorPreviousButton)
                                .border(
                                    border = BorderStroke(
                                        1.dp,
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.White.copy(alpha = 0.50f),
                                                Color.White.copy(alpha = 0.15f)
                                            )
                                        )
                                    ),
                                    shape = prevShape
                                )
                        }
                    )
                    .pointerInput(onPrevious) {
                        detectTapGestures(
                            onPress = {
                                val pressSpec = spring<Float>(dampingRatio = 0.5f, stiffness = 450f)
                                val jiggleSpec = spring<Float>(dampingRatio = 0.32f, stiffness = 280f)
                                val job = launch { prevPress.animateTo(1f, pressSpec) }
                                val released = tryAwaitRelease()
                                job.cancel()
                                launch {
                                    prevPress.animateTo(0f, pressSpec)
                                    if (released) {
                                        prevJiggle.snapTo(1f)
                                        prevJiggle.animateTo(0f, jiggleSpec)
                                    }
                                }
                            },
                            onTap = {
                                lastClicked = PlaybackButtonType.PREVIOUS
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onPrevious()
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isGlassActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.5f)
                            .align(Alignment.TopCenter)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = if (isDark) 0.18f else 0.32f),
                                        Color.Transparent
                                    )
                                ),
                                shape = prevShape
                            )
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = "Anterior",
                    tint = if (isGlassActive) (if (isDark) Color.White else Color(0xFF1C1C1E)) else tintPreviousIcon,
                    modifier = Modifier.size(iconSize)
                )
            }

            val playWeight by animateFloatAsState(
                targetValue = weightFor(PlaybackButtonType.PLAY_PAUSE),
                animationSpec = pressAnimationSpec,
                label = "playWeight"
            )
            // Tween (matching the Crossfade duration) instead of a spring with
            // StiffnessMedium. The old spring took ~600 ms to settle and read
            // playCorner in the composition phase, recomposing AnimatedPlaybackControls
            // every frame for the entire settle. A bounded 220 ms tween that completes
            // alongside the icon Crossfade keeps the recomposition window small enough
            // that it doesn't overlap with a subsequent sheet-collapse gesture.
            val playCorner by animateDpAsState(
                targetValue = if (!playPauseVisualState) playPauseCornerPlaying else playPauseCornerPaused,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                label = "playCorner"
            )
            val playShape = AbsoluteSmoothCornerShape(
                cornerRadiusTL = playCorner,
                smoothnessAsPercentTR = 60,
                cornerRadiusBL = playCorner,
                smoothnessAsPercentTL = 60,
                cornerRadiusTR = playCorner,
                smoothnessAsPercentBL = 60,
                cornerRadiusBR = playCorner,
                smoothnessAsPercentBR = 60
            )
            val playBorderBrush = remember(isDark) {
                Brush.verticalGradient(
                    colors = if (isDark) listOf(
                        Color.White.copy(alpha = 0.90f),
                        Color.White.copy(alpha = 0.30f)
                    ) else listOf(
                        Color.White.copy(alpha = 0.95f),
                        Color.White.copy(alpha = 0.40f)
                    )
                )
            }

            Box(
                modifier = Modifier
                    .weight(playWeight)
                    .fillMaxHeight()
                    .graphicsLayer {
                        scaleX = playPauseScaleX
                        scaleY = playPauseScaleY
                    }
                    .then(
                        if (isGlassActive) {
                            Modifier
                                .shadow(elevation = 12.dp, shape = playShape)
                                .drawBackdrop(
                                    backdrop = backdrop,
                                    shape = { playShape },
                                    effects = {
                                        vibrancy()
                                        blur(12.dp.toPx())
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            lens(
                                                refractionHeight = 14.dp.toPx(),
                                                refractionAmount = 30.dp.toPx()
                                            )
                                        }
                                    },
                                    onDrawSurface = {
                                        drawRect(colorPlayPause, blendMode = BlendMode.Hue)
                                        drawRect(colorPlayPause.copy(alpha = 0.50f))
                                    }
                                )
                                .border(BorderStroke(1.5.dp, playBorderBrush), playShape)
                                .clip(playShape)
                        } else {
                            Modifier
                                .clip(playShape)
                                .background(colorPlayPause)
                                .border(
                                    border = BorderStroke(
                                        1.5.dp,
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.White.copy(alpha = 0.70f),
                                                Color.White.copy(alpha = 0.20f)
                                            )
                                        )
                                    ),
                                    shape = playShape
                                )
                        }
                    )
                    .pointerInput(onPlayPause) {
                        detectTapGestures(
                            onPress = {
                                val pressSpec = spring<Float>(dampingRatio = 0.5f, stiffness = 450f)
                                val jiggleSpec = spring<Float>(dampingRatio = 0.32f, stiffness = 280f)
                                val job = launch { playPausePress.animateTo(1f, pressSpec) }
                                val released = tryAwaitRelease()
                                job.cancel()
                                launch {
                                    playPausePress.animateTo(0f, pressSpec)
                                    if (released) {
                                        playPauseJiggle.snapTo(1f)
                                        playPauseJiggle.animateTo(0f, jiggleSpec)
                                    }
                                }
                            },
                            onTap = {
                                lastClicked = PlaybackButtonType.PLAY_PAUSE
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onPlayPause()
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isGlassActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.5f)
                            .align(Alignment.TopCenter)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.25f),
                                        Color.Transparent
                                    )
                                ),
                                shape = playShape
                            )
                    )
                }
                MorphingPlayPauseIcon(
                    isPlaying = playPauseVisualState,
                    tint = tintPlayPauseIcon,
                    size = playPauseIconSize
                )
            }

            val nextWeight by animateFloatAsState(
                targetValue = weightFor(PlaybackButtonType.NEXT),
                animationSpec = pressAnimationSpec,
                label = "nextWeight"
            )
            val nextShape = CircleShape
            Box(
                modifier = Modifier
                    .weight(nextWeight)
                    .fillMaxHeight()
                    .graphicsLayer {
                        scaleX = nextScaleX
                        scaleY = nextScaleY
                    }
                    .then(
                        if (isGlassActive) {
                            Modifier
                                .shadow(elevation = 10.dp, shape = nextShape)
                                .drawBackdrop(
                                    backdrop = backdrop,
                                    shape = { nextShape },
                                    effects = {
                                        vibrancy()
                                        blur(12.dp.toPx())
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            lens(
                                                refractionHeight = 14.dp.toPx(),
                                                refractionAmount = 30.dp.toPx()
                                            )
                                        }
                                    },
                                    onDrawSurface = {
                                        drawRect(if (isDark) Color(0x351E1E22) else Color(0x40FFFFFF))
                                    }
                                )
                                .border(BorderStroke(1.2.dp, prevBorderBrush), nextShape)
                                .clip(nextShape)
                        } else {
                            Modifier
                                .clip(nextShape)
                                .background(colorNextButton)
                                .border(
                                    border = BorderStroke(
                                        1.dp,
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.White.copy(alpha = 0.50f),
                                                Color.White.copy(alpha = 0.15f)
                                            )
                                        )
                                    ),
                                    shape = nextShape
                                )
                        }
                    )
                    .pointerInput(onNext) {
                        detectTapGestures(
                            onPress = {
                                val pressSpec = spring<Float>(dampingRatio = 0.5f, stiffness = 450f)
                                val jiggleSpec = spring<Float>(dampingRatio = 0.32f, stiffness = 280f)
                                val job = launch { nextPress.animateTo(1f, pressSpec) }
                                val released = tryAwaitRelease()
                                job.cancel()
                                launch {
                                    nextPress.animateTo(0f, pressSpec)
                                    if (released) {
                                        nextJiggle.snapTo(1f)
                                        nextJiggle.animateTo(0f, jiggleSpec)
                                    }
                                }
                            },
                            onTap = {
                                lastClicked = PlaybackButtonType.NEXT
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onNext()
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isGlassActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.5f)
                            .align(Alignment.TopCenter)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = if (isDark) 0.18f else 0.32f),
                                        Color.Transparent
                                    )
                                ),
                                shape = nextShape
                            )
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "Siguiente",
                    tint = if (isGlassActive) (if (isDark) Color.White else Color(0xFF1C1C1E)) else tintNextIcon,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}

@Composable
private fun MorphingPlayPauseIcon(
    isPlaying: Boolean,
    tint: Color,
    size: Dp,
) {
    Crossfade(
        targetState = isPlaying,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "playPauseCrossfade"
    ) { playing ->
        Icon(
            imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = if (playing) "Pausar" else "Reproducir",
            tint = tint,
            modifier = Modifier.size(size)
        )
    }
}
