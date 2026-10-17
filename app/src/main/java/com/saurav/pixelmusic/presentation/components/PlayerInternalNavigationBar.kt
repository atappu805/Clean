package com.saurav.pixelmusic.presentation.components

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import com.saurav.pixelmusic.ui.theme.GoogleSansRounded
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.navigation.NavHostController
import com.saurav.pixelmusic.BottomNavItem
import com.saurav.pixelmusic.R
import com.saurav.pixelmusic.presentation.navigation.Screen
import com.saurav.pixelmusic.presentation.navigation.navigateToTopLevelSafely
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.animateFloatAsState



internal val NavBarContentHeight = 76.dp
internal val NavBarCompactContentHeight = 64.dp
internal val NavBarContentHeightFullWidth = NavBarContentHeight
private val MainScreenBottomGradientExtraHeight = 64.dp + MiniPlayerBottomSpacer + 8.dp
internal val MaxNavigationBarBottomInset = 96.dp

internal fun sanitizeNavigationBarBottomInset(systemNavBarInset: Dp): Dp {
    if (!systemNavBarInset.value.isFinite()) return 0.dp
    return systemNavBarInset.coerceIn(0.dp, MaxNavigationBarBottomInset)
}

internal fun calculatePlayerSheetCollapsedTargetY(
    containerHeightPx: Float,
    collapsedContentHeightPx: Float,
    bottomMarginPx: Float,
    bottomSpacerPx: Float
): Float {
    val safeContainerHeightPx = containerHeightPx.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    val safeCollapsedContentHeightPx = collapsedContentHeightPx.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    val safeBottomMarginPx = bottomMarginPx.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    val safeBottomSpacerPx = bottomSpacerPx.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    val maxTargetY = (safeContainerHeightPx - safeCollapsedContentHeightPx).coerceAtLeast(0f)

    return (safeContainerHeightPx - safeCollapsedContentHeightPx - safeBottomMarginPx - safeBottomSpacerPx)
        .coerceIn(0f, maxTargetY)
}

internal fun resolveNavBarContentHeight(compactMode: Boolean): Dp =
    if (compactMode) NavBarCompactContentHeight else NavBarContentHeight

internal fun resolveMainScreenBottomGradientHeight(compactMode: Boolean): Dp =
    resolveNavBarContentHeight(compactMode) + MainScreenBottomGradientExtraHeight

internal fun resolveNavBarSurfaceHeight(
    navBarStyle: String,
    systemNavBarInset: Dp,
    compactMode: Boolean
): Dp {
    return resolveNavBarContentHeight(compactMode)
}

internal fun resolveNavBarOccupiedHeight(
    systemNavBarInset: Dp,
    compactMode: Boolean
): Dp = resolveNavBarContentHeight(compactMode) + systemNavBarInset

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayerInternalNavigationBar(
    navController: NavHostController,
    navItems: ImmutableList<BottomNavItem>,
    currentRoute: String?,
    modifier: Modifier = Modifier,
    navBarStyle: String,
    compactMode: Boolean,
    bottomBarPadding: Dp = 0.dp,
    onSearchIconDoubleTap: () -> Unit = {},
    isLiquidGlass: Boolean = true,
    backdrop: Backdrop? = null
) {
    val latestCurrentRoute by rememberUpdatedState(currentRoute)
    val latestNavigationEnabled by rememberUpdatedState(currentRoute != null)

    val haptic = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current
    val fontScale = LocalDensity.current.fontScale
    val screenWidth = configuration.screenWidthDp

    // Main pill contains Home, Explore, and Library; Search is separated into the detached button.
    val mainItems = remember(navItems) {
        navItems.filter { it.screen.route != Screen.Search.route }.toImmutableList()
    }

    val isLargeFont = fontScale > 1.25f
    val isCompactScreen = screenWidth < 400
    val shouldHideLabel = isLargeFont || (isCompactScreen && mainItems.size > 3) || compactMode

    val selectedIndex = mainItems.indexOfFirst { it.screen.route == latestCurrentRoute }

    // IMPORTANT: these sizes must always sum (with the bottom padding below) to
    // exactly resolveNavBarContentHeight(compactMode), because that is the value
    // parent screens use to reserve layout space (mini player position, gradient
    // height, etc). Previously these were hardcoded regardless of compactMode,
    // so when compactMode flipped to true the reserved space shrank to 64dp but
    // the bar kept drawing at 76dp worth of content -> squished/cut-off nav bar.
    //   non-compact: 64dp settings button + 12dp bottom padding = 76dp = NavBarContentHeight
    //   compact:     52dp settings button + 12dp bottom padding = 64dp = NavBarCompactContentHeight
    val toolbarRowHeight = if (compactMode) 40.dp else 48.dp
    val settingsButtonSize = if (compactMode) 52.dp else 64.dp
    val itemIconSize = if (compactMode) 20.dp else 24.dp
    val horizontalOuterPadding = if (compactMode) 10.dp else 16.dp
    val itemSpacing = if (compactMode) 8.dp else 12.dp
    val selectedItemWeight = if (compactMode) 1.7f else 2.2f
    // bottomBarPadding was previously accepted but silently ignored, which could
    // leave the pill sitting too close to (or clipped by) the gesture nav bar on
    // devices that need extra clearance. It's now applied on top of the base 12dp.
    val bottomPadding = 12.dp

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    // iOS Liquid Glass specular rim gradient and translucent fill colors
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

    val glassContainerColor = remember(isDark) {
        if (isDark) {
            Color(0x18FFFFFF)
        } else {
            Color(0x38FFFFFF)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = horizontalOuterPadding, end = horizontalOuterPadding, bottom = bottomPadding),
        horizontalArrangement = Arrangement.spacedBy(itemSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isLiquidGlass) {
            val glassCapsuleModifier = Modifier
                .weight(1f)
                .height(settingsButtonSize)
                .shadow(elevation = 12.dp, shape = CircleShape)
                .then(
                    if (backdrop != null) {
                        Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { CircleShape },
                            effects = {
                                vibrancy()
                                blur(8.dp.toPx())
                                lens(16.dp.toPx(), 32.dp.toPx())
                            }
                        )
                    } else {
                        Modifier
                    }
                )
                .background(color = glassContainerColor, shape = CircleShape)
                .border(BorderStroke(1.2.dp, glassBorderBrush), shape = CircleShape)
                .clip(CircleShape)

            // iOS 26 Liquid Glass Navigation Capsule with icon-over-label and sliding droplet
            BoxWithConstraints(
                modifier = glassCapsuleModifier,
                contentAlignment = Alignment.CenterStart
            ) {
                val totalWidth = maxWidth
                val tabCount = mainItems.size
                val tabWidth = if (tabCount > 0) totalWidth / tabCount else 0.dp

                // Specular gloss reflection across the top half of the capsule
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.5f)
                        .align(Alignment.TopCenter)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = if (isDark) 0.15f else 0.30f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Fluid sliding glass droplet indicator behind active tab
                if (selectedIndex in 0 until tabCount) {
                    val animatedDropletOffset by animateDpAsState(
                        targetValue = tabWidth * selectedIndex,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "droplet_offset"
                    )

                    val dropletPadding = if (compactMode) 3.dp else 4.dp
                    Box(
                        modifier = Modifier
                            .offset(x = animatedDropletOffset)
                            .width(tabWidth)
                            .fillMaxHeight()
                            .padding(dropletPadding)
                            .shadow(elevation = 4.dp, shape = CircleShape)
                            .then(
                                if (backdrop != null) {
                                    Modifier.drawBackdrop(
                                        backdrop = backdrop,
                                        shape = { CircleShape },
                                        effects = {
                                            blur(4.dp.toPx())
                                            lens(10.dp.toPx(), 20.dp.toPx())
                                        }
                                    )
                                } else Modifier
                            )
                            .background(
                                color = if (isDark) Color.White.copy(alpha = 0.22f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                shape = CircleShape
                            )
                            .border(
                                border = BorderStroke(
                                    1.dp,
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = if (isDark) 0.75f else 0.90f),
                                            Color.White.copy(alpha = if (isDark) 0.25f else 0.40f)
                                        )
                                    )
                                ),
                                shape = CircleShape
                            )
                    )
                }

                // Row of icon-over-label tabs exactly matching iOS 26 tab bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    mainItems.forEachIndexed { index, item ->
                        val isSelected = index == selectedIndex

                        val itemAlpha by animateFloatAsState(
                            targetValue = if (isSelected) 1f else 0.65f,
                            animationSpec = tween(200),
                            label = "tab_alpha_$index"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (latestNavigationEnabled && latestCurrentRoute != item.screen.route) {
                                        navController.navigateToTopLevelSafely(item.screen.route)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                val iconRes = if (isSelected && item.selectedIconResId != null && item.selectedIconResId != 0) {
                                    item.selectedIconResId
                                } else {
                                    item.iconResId
                                }

                                Icon(
                                    painter = painterResource(id = iconRes),
                                    contentDescription = item.label,
                                    tint = (if (isDark) Color.White else Color.Black).copy(alpha = itemAlpha),
                                    modifier = Modifier.size(if (compactMode) 20.dp else 22.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.label,
                                    fontFamily = GoogleSansRounded,
                                    fontSize = if (compactMode) 11.sp else 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = (if (isDark) Color.White else Color.Black).copy(alpha = itemAlpha),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Material 3 Standard Floating Toolbar
            HorizontalFloatingToolbar(
                modifier = Modifier.weight(1f),
                expanded = true,
                colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(
                    toolbarContentColor = MaterialTheme.colorScheme.onSurface,
                    toolbarContainerColor = MaterialTheme.colorScheme.primary
                ),
                content = {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(toolbarRowHeight),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        mainItems.forEachIndexed { index, item ->
                            val isSelected = index == selectedIndex

                            val containerColor by animateColorAsState(
                                targetValue = if (isSelected) MaterialTheme.colorScheme.background else Color.Transparent,
                                animationSpec = tween(200),
                                label = "container_color_$index"
                            )

                            val contentColor by animateColorAsState(
                                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background,
                                animationSpec = tween(200),
                                label = "content_color_$index"
                            )

                            val animatedWeight by animateFloatAsState(
                                targetValue = if (isSelected) selectedItemWeight else 1f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                ),
                                label = "weight_$index"
                            )

                            Surface(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (latestNavigationEnabled && latestCurrentRoute != item.screen.route) {
                                        navController.navigateToTopLevelSafely(item.screen.route)
                                    }
                                },
                                modifier = Modifier
                                    .weight(animatedWeight)
                                    .height(toolbarRowHeight),
                                shape = CircleShape,
                                color = containerColor,
                                contentColor = contentColor
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    val iconRes = if (isSelected && item.selectedIconResId != null && item.selectedIconResId != 0) {
                                        item.selectedIconResId
                                    } else {
                                        item.iconResId
                                    }

                                    Icon(
                                        painter = painterResource(id = iconRes),
                                        contentDescription = item.label,
                                        tint = contentColor,
                                        modifier = Modifier.size(itemIconSize)
                                    )

                                    AnimatedVisibility(
                                        visible = isSelected && !shouldHideLabel,
                                        enter = fadeIn(animationSpec = tween(200)) + expandHorizontally(expandFrom = Alignment.Start),
                                        exit = fadeOut(animationSpec = tween(150)) + shrinkHorizontally(shrinkTowards = Alignment.Start)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = item.label,
                                                fontFamily = GoogleSansRounded,
                                                style = if (compactMode) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                                                maxLines = 1,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            )
        }

        // Detached Search Button
        val isSearchSelected = latestCurrentRoute == Screen.Search.route
        val searchContentColor by animateColorAsState(
            targetValue = if (isSearchSelected) {
                if (isDark) Color.White else MaterialTheme.colorScheme.primary
            } else {
                if (isDark) Color.White.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.75f)
            },
            animationSpec = tween(200),
            label = "search_content_color"
        )

        if (isLiquidGlass) {
            val searchGlassBg = if (isSearchSelected) {
                if (isDark) MaterialTheme.colorScheme.primary.copy(alpha = 0.40f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
            } else {
                glassContainerColor
            }
            val searchGlassBorder = if (isSearchSelected) {
                BorderStroke(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        )
                    )
                )
            } else {
                BorderStroke(1.dp, glassBorderBrush)
            }

            val searchButtonModifier = Modifier
                .size(settingsButtonSize)
                .shadow(elevation = 12.dp, shape = CircleShape)
                .then(
                    if (backdrop != null) {
                        Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { CircleShape },
                            effects = {
                                vibrancy()
                                blur(8.dp.toPx())
                                lens(16.dp.toPx(), 32.dp.toPx())
                            }
                        )
                    } else Modifier
                )
                .background(searchGlassBg, CircleShape)
                .border(searchGlassBorder, CircleShape)
                .clip(CircleShape)
                .clickable {
                    if (!latestNavigationEnabled) return@clickable
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (!isSearchSelected) {
                        navController.navigateToTopLevelSafely(Screen.Search.route)
                    } else {
                        onSearchIconDoubleTap()
                    }
                }

            Box(
                modifier = searchButtonModifier,
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.5f)
                        .align(Alignment.TopCenter)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = if (isDark) 0.15f else 0.30f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                Icon(
                    painter = painterResource(id = R.drawable.rounded_search_24),
                    contentDescription = "Search",
                    tint = searchContentColor,
                    modifier = Modifier.size(itemIconSize)
                )
            }
        } else {
            Surface(
                onClick = {
                    if (!latestNavigationEnabled) return@Surface
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (!isSearchSelected) {
                        navController.navigateToTopLevelSafely(Screen.Search.route)
                    } else {
                        onSearchIconDoubleTap()
                    }
                },
                modifier = Modifier.size(settingsButtonSize),
                shape = CircleShape,
                color = if (isSearchSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                contentColor = if (isSearchSelected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onPrimaryContainer,
                shadowElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        painter = painterResource(id = R.drawable.rounded_search_24),
                        contentDescription = "Search",
                        modifier = Modifier.size(itemIconSize)
                    )
                }
            }
        }
    }
}
