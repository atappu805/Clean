package com.saurav.pixelmusic.presentation.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.saurav.pixelmusic.R
import com.saurav.pixelmusic.data.model.Song
import com.saurav.pixelmusic.ui.theme.GoogleSansRounded
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

/**
 * Modern iOS-inspired stacked album arts section for Your Mix on the Home screen.
 * Shows "Your Mix" on the left and stacked organic-shaped album arts on the right.
 * Tapping the stack expands the album arts horizontally one by one with spring physics.
 * Scrolling on the Home screen automatically folds them back together into the stack.
 */
@Composable
fun YourMixSection(
    subtitle: String,
    songs: List<Song>,
    isHomeScreenScrolling: Boolean,
    modifier: Modifier = Modifier,
    onPlaySong: (Song) -> Unit,
    onPlayAll: () -> Unit
) {
    if (songs.isEmpty()) return

    val haptic = LocalHapticFeedback.current
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    val displaySongs = remember(songs) { songs.take(5) }
    val scrollState = rememberScrollState()

    // Collapse stack automatically when the user scrolls the Home screen
    LaunchedEffect(isHomeScreenScrolling) {
        if (isHomeScreenScrolling && isExpanded) {
            isExpanded = false
        }
    }

    // Curated distinct expressive shapes for each card in the stack
    val shapes = remember {
        listOf(
            // Shape 0: Smooth organic asymmetric squircle
            AbsoluteSmoothCornerShape(
                cornerRadiusTL = 26.dp, smoothnessAsPercentTL = 80,
                cornerRadiusTR = 12.dp, smoothnessAsPercentTR = 60,
                cornerRadiusBL = 12.dp, smoothnessAsPercentBL = 60,
                cornerRadiusBR = 26.dp, smoothnessAsPercentBR = 80
            ),
            // Shape 1: Circle / Disc
            CircleShape,
            // Shape 2: Teardrop
            RoundedCornerShape(topStart = 28.dp, topEnd = 8.dp, bottomStart = 28.dp, bottomEnd = 28.dp),
            // Shape 3: Smooth uniform squircle
            AbsoluteSmoothCornerShape(cornerRadius = 20.dp, smoothnessAsPercent = 70),
            // Shape 4: Diagonal rounded pebble
            RoundedCornerShape(topStart = 8.dp, topEnd = 26.dp, bottomStart = 26.dp, bottomEnd = 8.dp)
        )
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Column: Your Mix title, subtitle & Play / Collapse actions
            Column(
                modifier = Modifier
                    .widthIn(min = 100.dp, max = 150.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        if (isExpanded) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            isExpanded = false
                        } else {
                            onPlayAll()
                        }
                    }
                    .padding(end = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.home_your_mix_title),
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    lineHeight = 30.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2
                )

                if (subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        onClick = onPlayAll
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Play",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    if (isExpanded) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isExpanded = false
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.UnfoldLess,
                                    contentDescription = "Collapse",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Stack",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Right: Stacked Album Arts (tapping expands horizontally one by one)
            val targetBoxWidth = if (isExpanded) {
                ((displaySongs.size - 1) * 80 + 74).dp
            } else {
                124.dp
            }
            val animatedBoxWidth by animateDpAsState(
                targetValue = targetBoxWidth,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "yourMixBoxWidth"
            )

            Box(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .horizontalScroll(scrollState, enabled = isExpanded)
            ) {
                Box(
                    modifier = Modifier
                        .width(animatedBoxWidth)
                        .height(86.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    displaySongs.forEachIndexed { index, song ->
                        val shape = shapes.getOrElse(index % shapes.size) { CircleShape }

                        val targetOffsetX = if (isExpanded) {
                            (index * 80).dp
                        } else {
                            when (index) {
                                0 -> 0.dp
                                1 -> 14.dp
                                2 -> 26.dp
                                3 -> 36.dp
                                else -> 44.dp
                            }
                        }

                        val targetOffsetY = if (isExpanded) {
                            0.dp
                        } else {
                            when (index) {
                                0 -> 0.dp
                                1 -> 2.dp
                                2 -> 4.dp
                                3 -> 6.dp
                                else -> 8.dp
                            }
                        }

                        val targetRotation = if (isExpanded) {
                            0f
                        } else {
                            when (index) {
                                0 -> -2f
                                1 -> 5f
                                2 -> -6f
                                3 -> 7f
                                else -> -4f
                            }
                        }

                        val targetScale = if (isExpanded) {
                            1.0f
                        } else {
                            when (index) {
                                0 -> 1.0f
                                1 -> 0.94f
                                2 -> 0.88f
                                3 -> 0.82f
                                else -> 0.76f
                            }
                        }

                        // Staggered spring stiffness so cards expand and arrive one by one through the right
                        val springStiffness = if (isExpanded) {
                            Spring.StiffnessMediumLow + (index * 35f)
                        } else {
                            Spring.StiffnessMediumLow + ((displaySongs.size - 1 - index) * 35f)
                        }

                        val animatedOffsetX by animateDpAsState(
                            targetValue = targetOffsetX,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = springStiffness
                            ),
                            label = "yourMixCardOffsetX_$index"
                        )
                        val animatedOffsetY by animateDpAsState(
                            targetValue = targetOffsetY,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = springStiffness
                            ),
                            label = "yourMixCardOffsetY_$index"
                        )
                        val animatedRotation by animateFloatAsState(
                            targetValue = targetRotation,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = springStiffness
                            ),
                            label = "yourMixCardRotation_$index"
                        )
                        val animatedScale by animateFloatAsState(
                            targetValue = targetScale,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = springStiffness
                            ),
                            label = "yourMixCardScale_$index"
                        )

                        val cardZIndex = (displaySongs.size - index).toFloat()

                        val interactionSource = remember(song.id) { MutableInteractionSource() }

                        Box(
                            modifier = Modifier
                                .zIndex(cardZIndex)
                                .offset(x = animatedOffsetX, y = animatedOffsetY)
                                .graphicsLayer {
                                    rotationZ = animatedRotation
                                    scaleX = animatedScale
                                    scaleY = animatedScale
                                    this.shape = shape
                                    clip = true
                                }
                                .shadow(
                                    elevation = if (isExpanded) 4.dp else (index * 1.5f + 2).dp,
                                    shape = shape
                                )
                                .size(72.dp)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest, shape)
                                .border(2.dp, MaterialTheme.colorScheme.surfaceContainer, shape)
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null
                                ) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (!isExpanded) {
                                        isExpanded = true
                                    } else {
                                        onPlaySong(song)
                                    }
                                }
                        ) {
                            SmartImage(
                                model = song.albumArtUriString,
                                contentDescription = song.title,
                                contentScale = ContentScale.Crop,
                                crossfadeDurationMillis = 0,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}
