package com.notewise.tabnotes.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Dedicated One-Handed Vertical Page Scroller for single fixed-size pages.
 * Supports smooth, continuous one-thumb/finger vertical scrolling up and down,
 * quick jump to top, jump to bottom writing area, and visual scroll progress.
 */
@Composable
fun PageVerticalScroller(
    scrollFraction: Float,
    onScrollToFraction: (Float) -> Unit,
    onScrollToTop: () -> Unit,
    onScrollToEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val trackHeightDp = 180.dp
    val thumbHeightDp = 38.dp

    Box(modifier = modifier.padding(end = 8.dp), contentAlignment = Alignment.CenterEnd) {
        // Floating Tooltip Badge while dragging
        AnimatedVisibility(
            visible = isDragging,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.padding(end = 52.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                shadowElevation = 6.dp
            ) {
                val label = when {
                    scrollFraction <= 0.05f -> "Page Top"
                    scrollFraction >= 0.95f -> "End of Page"
                    else -> "${(scrollFraction * 100).toInt()}%"
                }
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        // Tactile Thumb Scroller Bar
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f),
            shadowElevation = 6.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
            modifier = Modifier.width(42.dp)
        ) {
            Column(
                modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Quick Scroll to Top Button
                IconButton(
                    onClick = onScrollToTop,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Scroll to Top",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Vertical Track Area with Draggable Thumb
                Box(
                    modifier = Modifier
                        .width(34.dp)
                        .height(trackHeightDp)
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val fraction = (offset.y / size.height).coerceIn(0f, 1f)
                                onScrollToFraction(fraction)
                            }
                        }
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragStart = { isDragging = true },
                                onDragEnd = { isDragging = false },
                                onDragCancel = { isDragging = false },
                                onVerticalDrag = { change, _ ->
                                    change.consume()
                                    val currentY = change.position.y
                                    val fraction = (currentY / size.height).coerceIn(0f, 1f)
                                    onScrollToFraction(fraction)
                                }
                            )
                        },
                    contentAlignment = Alignment.TopCenter
                ) {
                    // Track rail
                    Box(
                        modifier = Modifier
                            .width(5.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    )

                    // Draggable Tactile Thumb Pill
                    val maxOffsetDp = trackHeightDp - thumbHeightDp
                    val thumbOffsetDp = maxOffsetDp * scrollFraction.coerceIn(0f, 1f)

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                        shadowElevation = if (isDragging) 6.dp else 3.dp,
                        border = androidx.compose.foundation.BorderStroke(
                            1.5.dp,
                            if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier
                            .offset { IntOffset(0, with(density) { thumbOffsetDp.roundToPx() }) }
                            .width(30.dp)
                            .height(thumbHeightDp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.UnfoldMore,
                                contentDescription = "Scroll Thumb",
                                tint = if (isDragging) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Quick Scroll to End / Bottom Writing Area Button
                IconButton(
                    onClick = onScrollToEnd,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Scroll to Bottom",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
