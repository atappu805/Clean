package com.saurav.pixelmusic.ui.modifiers

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.flow.collectLatest
import org.intellij.lang.annotations.Language
import kotlin.math.abs

/**
 * Global motion blur intensity (0f = off, 1f = normal, 2f = extreme).
 * Provided at the app root from user preferences; screens using scrollMotionBlur
 * pick it up automatically without needing to thread the value through.
 */
val LocalMotionBlurIntensity = compositionLocalOf { 1f }

/**
 * Whether motion blur is enabled globally. Provided at the app root from user
 * preferences; screens without direct SettingsViewModel access can use this.
 */
val LocalMotionBlurEnabled = compositionLocalOf { true }

@Language("AGSL")
const val DIRECTIONAL_BLUR_AGSL = """
    uniform shader content;
    uniform float2 resolution;
    uniform float2 direction;
    uniform float blurAmount;

    half4 main(float2 fragCoord) {
        if (blurAmount <= 0.5) {
            return content.eval(fragCoord);
        }
        
        half4 color = half4(0.0);
        float totalWeight = 0.0;
        const int samples = 7;
        float step = blurAmount / float(samples);
        
        for (int i = -3; i <= 3; i++) {
            float offset = float(i) * step;
            float2 sampleCoord = fragCoord + direction * offset;
            
            if (sampleCoord.x >= 0.0 && sampleCoord.x <= resolution.x &&
                sampleCoord.y >= 0.0 && sampleCoord.y <= resolution.y) {
                float weight = 1.0 - (abs(float(i)) / 4.0);
                color += content.eval(sampleCoord) * weight;
                totalWeight += weight;
            }
        }
        
        if (totalWeight < 0.001) {
            return content.eval(fragCoord);
        }
        
        return color / totalWeight;
    }
"""

/**
 * Modifier that applies a dynamic vertical or horizontal motion blur based on scroll speed.
 * Origin: Essentials (https://github.com/sameerasw/essentials)
 * Uses AGSL RuntimeShader on Android 13+ (API 33+) with fallback to RenderEffect on Android 12 (API 31+).
 */
fun Modifier.scrollMotionBlur(
    scrollState: ScrollState,
    enabled: Boolean? = null,
    isHorizontal: Boolean = false,
    intensity: Float? = null,
    maxBlurPx: Float = 25f,
    velocityThreshold: Float = 50f
): Modifier = composed {
    val isBlurEnabled = enabled ?: LocalMotionBlurEnabled.current
    val currentIntensity = intensity ?: LocalMotionBlurIntensity.current
    if (!isBlurEnabled || currentIntensity <= 0f || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return@composed Modifier
    }

    val effectiveMaxBlur = (maxBlurPx * currentIntensity).coerceAtLeast(1f)
    var previousPosition by remember { mutableIntStateOf(scrollState.value) }
    var previousTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val blurRadius = remember { Animatable(0f) }

    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value }.collectLatest { currentPosition ->
            val currentTime = System.currentTimeMillis()
            val timeDelta = (currentTime - previousTime).coerceAtLeast(1)
            val positionDelta = abs(currentPosition - previousPosition)

            // Velocity in pixels per second
            val velocity = (positionDelta.toFloat() / timeDelta) * 1000f

            if (velocity > velocityThreshold) {
                val targetBlur = ((velocity - velocityThreshold) / 2000f * effectiveMaxBlur)
                    .coerceAtMost(effectiveMaxBlur)
                blurRadius.snapTo(targetBlur)
            }

            blurRadius.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 150, easing = LinearEasing)
            )

            previousPosition = currentPosition
            previousTime = currentTime
        }
    }

    LaunchedEffect(scrollState.isScrollInProgress) {
        if (!scrollState.isScrollInProgress) {
            blurRadius.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 100, easing = LinearEasing)
            )
        }
    }

    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            RuntimeShader(DIRECTIONAL_BLUR_AGSL)
        } else null
    }
    val runtimeEffect = remember(shader) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null) {
            RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
        } else null
    }

    this.graphicsLayer {
        val radius = blurRadius.value
        if (radius > 0.1f) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null && runtimeEffect != null) {
                shader.setFloatUniform("resolution", size.width, size.height)
                val dirX = if (isHorizontal) 1.0f else 0.0f
                val dirY = if (isHorizontal) 0.0f else 1.0f
                shader.setFloatUniform("direction", dirX, dirY)
                shader.setFloatUniform("blurAmount", radius)
                renderEffect = runtimeEffect
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val blurX = if (isHorizontal) radius else 0.1f
                val blurY = if (isHorizontal) 0.1f else radius
                renderEffect = RenderEffect.createBlurEffect(
                    blurX,
                    blurY,
                    Shader.TileMode.DECAL
                ).asComposeRenderEffect()
            }
        } else {
            renderEffect = null
        }
    }
}

/**
 * Modifier that applies a dynamic vertical or horizontal motion blur based on LazyList scroll speed.
 * Origin: Essentials (https://github.com/sameerasw/essentials)
 */
fun Modifier.scrollMotionBlur(
    lazyListState: LazyListState,
    enabled: Boolean? = null,
    isHorizontal: Boolean = false,
    intensity: Float? = null,
    maxBlurPx: Float = 25f,
    velocityThreshold: Float = 50f
): Modifier = composed {
    val isBlurEnabled = enabled ?: LocalMotionBlurEnabled.current
    val currentIntensity = intensity ?: LocalMotionBlurIntensity.current
    if (!isBlurEnabled || currentIntensity <= 0f || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return@composed Modifier
    }

    val effectiveMaxBlur = (maxBlurPx * currentIntensity).coerceAtLeast(1f)
    var previousIndex by remember { mutableIntStateOf(lazyListState.firstVisibleItemIndex) }
    var previousOffset by remember { mutableIntStateOf(lazyListState.firstVisibleItemScrollOffset) }
    var previousTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val blurRadius = remember { Animatable(0f) }

    LaunchedEffect(lazyListState) {
        snapshotFlow {
            Pair(lazyListState.firstVisibleItemIndex, lazyListState.firstVisibleItemScrollOffset)
        }.collectLatest { (currentIndex, currentOffset) ->
            val currentTime = System.currentTimeMillis()
            val timeDelta = (currentTime - previousTime).coerceAtLeast(1)

            val positionDelta = if (currentIndex == previousIndex) {
                abs(currentOffset - previousOffset)
            } else {
                abs((currentIndex - previousIndex) * 200 + (currentOffset - previousOffset))
            }

            val velocity = (positionDelta.toFloat() / timeDelta) * 1000f

            if (velocity > velocityThreshold) {
                val targetBlur = ((velocity - velocityThreshold) / 2000f * effectiveMaxBlur)
                    .coerceAtMost(effectiveMaxBlur)
                blurRadius.snapTo(targetBlur)
            }

            blurRadius.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 150, easing = LinearEasing)
            )

            previousIndex = currentIndex
            previousOffset = currentOffset
            previousTime = currentTime
        }
    }

    LaunchedEffect(lazyListState.isScrollInProgress) {
        if (!lazyListState.isScrollInProgress) {
            blurRadius.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 100, easing = LinearEasing)
            )
        }
    }

    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            RuntimeShader(DIRECTIONAL_BLUR_AGSL)
        } else null
    }
    val runtimeEffect = remember(shader) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null) {
            RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
        } else null
    }

    this.graphicsLayer {
        val radius = blurRadius.value
        if (radius > 0.1f) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null && runtimeEffect != null) {
                shader.setFloatUniform("resolution", size.width, size.height)
                val dirX = if (isHorizontal) 1.0f else 0.0f
                val dirY = if (isHorizontal) 0.0f else 1.0f
                shader.setFloatUniform("direction", dirX, dirY)
                shader.setFloatUniform("blurAmount", radius)
                renderEffect = runtimeEffect
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val blurX = if (isHorizontal) radius else 0.1f
                val blurY = if (isHorizontal) 0.1f else radius
                renderEffect = RenderEffect.createBlurEffect(
                    blurX,
                    blurY,
                    Shader.TileMode.DECAL
                ).asComposeRenderEffect()
            }
        } else {
            renderEffect = null
        }
    }
}

/**
 * Modifier that applies a dynamic vertical motion blur based on LazyGrid scroll speed.
 * Origin: Essentials (https://github.com/sameerasw/essentials)
 */
fun Modifier.scrollMotionBlur(
    gridState: LazyGridState,
    enabled: Boolean? = null,
    intensity: Float? = null,
    maxBlurPx: Float = 25f,
    velocityThreshold: Float = 50f
): Modifier = composed {
    val isBlurEnabled = enabled ?: LocalMotionBlurEnabled.current
    val currentIntensity = intensity ?: LocalMotionBlurIntensity.current
    if (!isBlurEnabled || currentIntensity <= 0f || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return@composed Modifier
    }

    val effectiveMaxBlur = (maxBlurPx * currentIntensity).coerceAtLeast(1f)
    var previousIndex by remember { mutableIntStateOf(gridState.firstVisibleItemIndex) }
    var previousOffset by remember { mutableIntStateOf(gridState.firstVisibleItemScrollOffset) }
    var previousTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val blurRadius = remember { Animatable(0f) }

    LaunchedEffect(gridState) {
        snapshotFlow {
            Pair(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset)
        }.collectLatest { (currentIndex, currentOffset) ->
            val currentTime = System.currentTimeMillis()
            val timeDelta = (currentTime - previousTime).coerceAtLeast(1)

            val positionDelta = if (currentIndex == previousIndex) {
                abs(currentOffset - previousOffset)
            } else {
                abs((currentIndex - previousIndex) * 200 + (currentOffset - previousOffset))
            }

            val velocity = (positionDelta.toFloat() / timeDelta) * 1000f

            if (velocity > velocityThreshold) {
                val targetBlur = ((velocity - velocityThreshold) / 2000f * effectiveMaxBlur)
                    .coerceAtMost(effectiveMaxBlur)
                blurRadius.snapTo(targetBlur)
            }

            blurRadius.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 150, easing = LinearEasing)
            )

            previousIndex = currentIndex
            previousOffset = currentOffset
            previousTime = currentTime
        }
    }

    LaunchedEffect(gridState.isScrollInProgress) {
        if (!gridState.isScrollInProgress) {
            blurRadius.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 100, easing = LinearEasing)
            )
        }
    }

    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            RuntimeShader(DIRECTIONAL_BLUR_AGSL)
        } else null
    }
    val runtimeEffect = remember(shader) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null) {
            RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
        } else null
    }

    this.graphicsLayer {
        val radius = blurRadius.value
        if (radius > 0.1f) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null && runtimeEffect != null) {
                shader.setFloatUniform("resolution", size.width, size.height)
                shader.setFloatUniform("direction", 0.0f, 1.0f)
                shader.setFloatUniform("blurAmount", radius)
                renderEffect = runtimeEffect
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                renderEffect = RenderEffect.createBlurEffect(
                    0.1f,
                    radius,
                    Shader.TileMode.DECAL
                ).asComposeRenderEffect()
            }
        } else {
            renderEffect = null
        }
    }
}

/**
 * Modifier that applies a dynamic vertical motion blur based on LazyStaggeredGrid scroll speed.
 * Origin: Essentials (https://github.com/sameerasw/essentials)
 */
fun Modifier.scrollMotionBlur(
    lazyStaggeredGridState: LazyStaggeredGridState,
    enabled: Boolean? = null,
    intensity: Float? = null,
    maxBlurPx: Float = 25f,
    velocityThreshold: Float = 50f
): Modifier = composed {
    val isBlurEnabled = enabled ?: LocalMotionBlurEnabled.current
    val currentIntensity = intensity ?: LocalMotionBlurIntensity.current
    if (!isBlurEnabled || currentIntensity <= 0f || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return@composed Modifier
    }

    val effectiveMaxBlur = (maxBlurPx * currentIntensity).coerceAtLeast(1f)
    var previousIndex by remember { mutableIntStateOf(lazyStaggeredGridState.firstVisibleItemIndex) }
    var previousOffset by remember { mutableIntStateOf(lazyStaggeredGridState.firstVisibleItemScrollOffset) }
    var previousTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val blurRadius = remember { Animatable(0f) }

    LaunchedEffect(lazyStaggeredGridState) {
        snapshotFlow {
            Pair(lazyStaggeredGridState.firstVisibleItemIndex, lazyStaggeredGridState.firstVisibleItemScrollOffset)
        }.collectLatest { (currentIndex, currentOffset) ->
            val currentTime = System.currentTimeMillis()
            val timeDelta = (currentTime - previousTime).coerceAtLeast(1)

            val positionDelta = if (currentIndex == previousIndex) {
                abs(currentOffset - previousOffset)
            } else {
                abs((currentIndex - previousIndex) * 200 + (currentOffset - previousOffset))
            }

            val velocity = (positionDelta.toFloat() / timeDelta) * 1000f

            if (velocity > velocityThreshold) {
                val targetBlur = ((velocity - velocityThreshold) / 2000f * effectiveMaxBlur)
                    .coerceAtMost(effectiveMaxBlur)
                blurRadius.snapTo(targetBlur)
            }

            blurRadius.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 150, easing = LinearEasing)
            )

            previousIndex = currentIndex
            previousOffset = currentOffset
            previousTime = currentTime
        }
    }

    LaunchedEffect(lazyStaggeredGridState.isScrollInProgress) {
        if (!lazyStaggeredGridState.isScrollInProgress) {
            blurRadius.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 100, easing = LinearEasing)
            )
        }
    }

    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            RuntimeShader(DIRECTIONAL_BLUR_AGSL)
        } else null
    }
    val runtimeEffect = remember(shader) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null) {
            RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
        } else null
    }

    this.graphicsLayer {
        val radius = blurRadius.value
        if (radius > 0.1f) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null && runtimeEffect != null) {
                shader.setFloatUniform("resolution", size.width, size.height)
                shader.setFloatUniform("direction", 0.0f, 1.0f)
                shader.setFloatUniform("blurAmount", radius)
                renderEffect = runtimeEffect
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                renderEffect = RenderEffect.createBlurEffect(
                    0.1f,
                    radius,
                    Shader.TileMode.DECAL
                ).asComposeRenderEffect()
            }
        } else {
            renderEffect = null
        }
    }
}

/**
 * Modifier that applies a dynamic horizontal motion blur based on Pager scroll speed.
 */
fun Modifier.scrollMotionBlur(
    pagerState: PagerState,
    enabled: Boolean? = null,
    intensity: Float? = null,
    maxBlurPx: Float = 25f,
    velocityThreshold: Float = 50f
): Modifier = composed {
    val isBlurEnabled = enabled ?: LocalMotionBlurEnabled.current
    val currentIntensity = intensity ?: LocalMotionBlurIntensity.current
    if (!isBlurEnabled || currentIntensity <= 0f || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return@composed Modifier
    }

    val effectiveMaxBlur = (maxBlurPx * currentIntensity).coerceAtLeast(1f)
    var previousPage by remember { mutableIntStateOf(pagerState.currentPage) }
    var previousOffset by remember { mutableFloatStateOf(pagerState.currentPageOffsetFraction) }
    var previousTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val blurRadius = remember { Animatable(0f) }

    LaunchedEffect(pagerState) {
        snapshotFlow {
            Pair(pagerState.currentPage, pagerState.currentPageOffsetFraction)
        }.collectLatest { (currentPage, currentOffset) ->
            val currentTime = System.currentTimeMillis()
            val timeDelta = (currentTime - previousTime).coerceAtLeast(1)

            val pageDelta = (currentPage - previousPage) + (currentOffset - previousOffset)
            val positionDelta = abs(pageDelta * 1000f)

            val velocity = (positionDelta / timeDelta) * 1000f

            if (velocity > velocityThreshold) {
                val targetBlur = ((velocity - velocityThreshold) / 2000f * effectiveMaxBlur)
                    .coerceAtMost(effectiveMaxBlur)
                blurRadius.snapTo(targetBlur)
            }

            blurRadius.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 150, easing = LinearEasing)
            )

            previousPage = currentPage
            previousOffset = currentOffset
            previousTime = currentTime
        }
    }

    LaunchedEffect(pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress) {
            blurRadius.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 100, easing = LinearEasing)
            )
        }
    }

    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            RuntimeShader(DIRECTIONAL_BLUR_AGSL)
        } else null
    }
    val runtimeEffect = remember(shader) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null) {
            RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
        } else null
    }

    this.graphicsLayer {
        val radius = blurRadius.value
        if (radius > 0.1f) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null && runtimeEffect != null) {
                shader.setFloatUniform("resolution", size.width, size.height)
                shader.setFloatUniform("direction", 1.0f, 0.0f)
                shader.setFloatUniform("blurAmount", radius)
                renderEffect = runtimeEffect
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                renderEffect = RenderEffect.createBlurEffect(
                    radius,
                    0.1f,
                    Shader.TileMode.DECAL
                ).asComposeRenderEffect()
            }
        } else {
            renderEffect = null
        }
    }
}
