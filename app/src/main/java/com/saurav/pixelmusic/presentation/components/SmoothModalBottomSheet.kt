package com.saurav.pixelmusic.presentation.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetDefaults
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import com.saurav.pixelmusic.ui.theme.LocalLiquidGlassBlurIntensity
import com.saurav.pixelmusic.ui.theme.LocalLiquidGlassEnabled
import kotlinx.coroutines.launch

/**
 * CompositionLocal providing a helper lambda to trigger a smooth animated dismiss
 * from any child composable inside [SmoothModalBottomSheet].
 */
val LocalSmoothDismiss = compositionLocalOf<((() -> Unit)?) -> Unit> {
    { it?.invoke() }
}

private fun findDialogWindow(view: View): Window? {
    var p = view.parent
    while (p != null) {
        if (p is DialogWindowProvider) {
            return p.window
        }
        p = p.parent
    }
    return null
}

/**
 * A wrapper around Material 3 [ModalBottomSheet] that ensures smooth dismiss animations when:
 * 1. Tapping the back button or performing system back swipe gestures
 * 2. Tapping the scrim / outside the sheet
 * 3. Dragging the sheet down to dismiss
 * 4. Dismissing programmatically via [LocalSmoothDismiss]
 *
 * This prevents the sheet from vanishing instantly in a single frame without exit animation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmoothModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    ),
    sheetMaxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = 0.dp,
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.modalWindowInsets },
    properties: ModalBottomSheetProperties = ModalBottomSheetProperties(shouldDismissOnBackPress = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    var isDismissing by remember { mutableStateOf(false) }

    fun dismissSmoothly(afterDismiss: (() -> Unit)? = null) {
        if (isDismissing) return
        isDismissing = true
        scope.launch {
            try {
                sheetState.hide()
            } catch (_: Throwable) {
            } finally {
                onDismissRequest()
                afterDismiss?.invoke()
                isDismissing = false
            }
        }
    }

    val isLiquidGlass = LocalLiquidGlassEnabled.current
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val colorScheme = MaterialTheme.colorScheme
    val view = LocalView.current
    val glassBlurIntensity = LocalLiquidGlassBlurIntensity.current

    LaunchedEffect(isLiquidGlass, glassBlurIntensity, sheetState.isVisible) {
        if (isLiquidGlass && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                findDialogWindow(view)?.let { win ->
                    win.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    val radiusPx = (95 * glassBlurIntensity).toInt().coerceIn(40, 200)
                    win.attributes.blurBehindRadius = radiusPx
                    win.setAttributes(win.attributes)
                }
            } catch (_: Throwable) {}
        }
    }

    val effectiveContainerColor = if (isLiquidGlass) {
        if (isDark) {
            Color(0x8218181F).compositeOver(colorScheme.primary.copy(alpha = 0.12f))
        } else {
            Color(0x88FFFFFF).compositeOver(colorScheme.primary.copy(alpha = 0.10f))
        }
    } else {
        containerColor
    }

    val effectiveScrimColor = if (isLiquidGlass) {
        Color.Black.copy(alpha = 0.28f)
    } else {
        scrimColor
    }

    val glassBorder = remember(isLiquidGlass, isDark) {
        if (!isLiquidGlass) null
        else BorderStroke(
            0.85.dp,
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = if (isDark) 0.22f else 0.40f),
                    Color.White.copy(alpha = if (isDark) 0.04f else 0.08f)
                )
            )
        )
    }

    val effectiveDragHandle: @Composable (() -> Unit)? = if (dragHandle == null) null else {
        {
            if (isLiquidGlass) {
                Box(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .size(width = 38.dp, height = 5.dp)
                        .clip(CircleShape)
                        .background(
                            if (isDark) Color.White.copy(alpha = 0.30f)
                            else colorScheme.onSurface.copy(alpha = 0.25f)
                        )
                )
            } else {
                dragHandle()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (!isDismissing && sheetState.isVisible) {
                dismissSmoothly()
            } else if (!isDismissing) {
                onDismissRequest()
            }
        },
        modifier = modifier
            .then(if (glassBorder != null) Modifier.border(glassBorder, shape) else Modifier)
            .then(
                if (isLiquidGlass && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Modifier.graphicsLayer {
                        val cm = android.graphics.ColorMatrix().apply { setSaturation(1.3f) }
                        renderEffect = android.graphics.RenderEffect.createColorFilterEffect(
                            android.graphics.ColorMatrixColorFilter(cm)
                        ).asComposeRenderEffect()
                    }
                } else Modifier
            ),
        sheetState = sheetState,
        sheetMaxWidth = sheetMaxWidth,
        shape = shape,
        containerColor = effectiveContainerColor,
        contentColor = contentColor,
        tonalElevation = if (isLiquidGlass) 0.dp else tonalElevation,
        scrimColor = effectiveScrimColor,
        dragHandle = effectiveDragHandle,
        contentWindowInsets = contentWindowInsets,
        properties = properties,
        content = {
            // Intercept system back within the bottom sheet's dialog window
            BackHandler(enabled = !isDismissing) {
                dismissSmoothly()
            }

            CompositionLocalProvider(
                LocalSmoothDismiss provides { afterDismiss -> dismissSmoothly(afterDismiss) }
            ) {
                content()
            }
        }
    )
}
