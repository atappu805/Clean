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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.saurav.pixelmusic.ui.theme.LocalLiquidGlassEnabled
import kotlinx.coroutines.launch

/**
 * CompositionLocal providing a helper lambda to trigger a smooth animated dismiss
 * from any child composable inside [SmoothModalBottomSheet].
 */
val LocalSmoothDismiss = compositionLocalOf<((() -> Unit)?) -> Unit> {
    { it?.invoke() }
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

    val effectiveContainerColor = if (isLiquidGlass) {
        if (isDark) Color(0x351C1C22) else Color(0x55FFFFFF)
    } else {
        containerColor
    }

    val effectiveScrimColor = if (isLiquidGlass) {
        Color.Black.copy(alpha = 0.35f)
    } else {
        scrimColor
    }

    val glassBorder = remember(isLiquidGlass, isDark) {
        if (!isLiquidGlass) null
        else BorderStroke(
            0.75.dp,
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = if (isDark) 0.12f else 0.22f),
                    Color.White.copy(alpha = if (isDark) 0.02f else 0.05f)
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
                        .size(width = 36.dp, height = 4.5.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = if (isDark) 0.25f else 0.40f))
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
        modifier = modifier.then(if (glassBorder != null) Modifier.border(glassBorder, shape) else Modifier),
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
