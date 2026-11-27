package com.notewise.tabnotes.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notewise.tabnotes.data.model.NotePage
import com.notewise.tabnotes.data.model.PageTemplate
import com.notewise.tabnotes.data.model.PaperSize

/**
 * Slide-out Vertical Page Thumbnail Strip matching the tablet note-taking layout.
 * Displays page ratio (e.g. 1/15), collapse toggle, vertical thumbnail cards with
 * page number badges, 3-dots page action menus, and "+ Add Page".
 */
@Composable
fun VerticalPageStrip(
    isOpen: Boolean,
    currentPageIndex: Int,
    pageCount: Int,
    pages: List<NotePage>,
    paperSize: PaperSize,
    onSelectPage: (Int) -> Unit,
    onToggleOpen: () -> Unit,
    onAddNewPage: () -> Unit,
    onDuplicatePage: (Int) -> Unit,
    onDeletePage: (Int) -> Unit,
    onClearPage: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Auto-scroll to current page when page index changes
    LaunchedEffect(currentPageIndex) {
        if (currentPageIndex in 0 until pageCount) {
            listState.animateScrollToItem(currentPageIndex)
        }
    }

    AnimatedVisibility(
        visible = isOpen,
        enter = slideInHorizontally(initialOffsetX = { it }),
        exit = slideOutHorizontally(targetOffsetX = { it }),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .width(220.dp)
                .fillMaxHeight(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 4.dp,
            shadowElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 1. Top Panel Header: "1/15" Cyan Badge + Collapse Toggle Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Cyan Page Counter Pill (e.g. 1/15 as in photo)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF0284C7),
                        shadowElevation = 2.dp
                    ) {
                        Text(
                            text = "${currentPageIndex + 1}/$pageCount",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }

                    // Panel Collapse Icon Button
                    IconButton(
                        onClick = onToggleOpen,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ViewSidebar,
                            contentDescription = "Collapse Pages Panel",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                // 2. Vertical Scrollable List of Page Cards
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    items(count = pageCount) { idx ->
                        val isSelected = idx == currentPageIndex
                        val pageData = pages.find { it.pageIndex == idx }
                        val template = pageData?.template ?: PageTemplate.LINED
                        val paperColor = pageData?.paperColor ?: 0xFFFFFFFF
                        val strokeCount = pageData?.strokes?.size ?: 0
                        var showMenu by remember { mutableStateOf(false) }

                        val isDarkPaper = (Color(paperColor).red * 0.299f +
                                           Color(paperColor).green * 0.587f +
                                           Color(paperColor).blue * 0.114f) < 0.48f

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Thumbnail Paper Card Container
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(paperColor),
                                shadowElevation = if (isSelected) 6.dp else 2.dp,
                                modifier = Modifier
                                    .fillMaxWidth(0.92f)
                                    .aspectRatio(1f / paperSize.aspectRatio.coerceIn(1.1f, 1.7f))
                                    .clip(RoundedCornerShape(8.dp))
                                    .then(
                                        if (isSelected) Modifier.border(2.5.dp, Color(0xFF0284C7), RoundedCornerShape(8.dp))
                                        else Modifier.border(1.dp, Color(0x33888888), RoundedCornerShape(8.dp))
                                    )
                                    .clickable { onSelectPage(idx) }
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    // Simulated miniature template pattern
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(8.dp),
                                        verticalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        val lineCol = if (isDarkPaper) Color(0x35FFFFFF) else Color(0x25000000)
                                        repeat(6) {
                                            HorizontalDivider(thickness = 1.dp, color = lineCol)
                                        }
                                    }

                                    // Stroke count indicator badge if page has handwriting
                                    if (strokeCount > 0) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0x99000000),
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(4.dp)
                                        ) {
                                            Text(
                                                text = "✏ $strokeCount",
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Bottom Bar of Card: Left Page Number Pill + Right Three-Dot Menu
                            Row(
                                modifier = Modifier.fillMaxWidth(0.92f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Page number
                                Surface(
                                    shape = CircleShape,
                                    color = if (isSelected) Color(0xFF0284C7) else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${idx + 1}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // Three-Dot Options Menu
                                Box {
                                    IconButton(
                                        onClick = { showMenu = true },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "Page Options",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = showMenu,
                                        onDismissRequest = { showMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Duplicate Page") },
                                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                            onClick = {
                                                onDuplicatePage(idx)
                                                showMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Insert Page After") },
                                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                            onClick = {
                                                onAddNewPage()
                                                showMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Clear Page Ink") },
                                            leadingIcon = { Icon(Icons.Default.LayersClear, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                            onClick = {
                                                onClearPage(idx)
                                                showMenu = false
                                            }
                                        )
                                        if (pageCount > 1) {
                                            HorizontalDivider()
                                            DropdownMenuItem(
                                                text = { Text("Delete Page", color = MaterialTheme.colorScheme.error) },
                                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp)) },
                                                onClick = {
                                                    onDeletePage(idx)
                                                    showMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                // 3. Bottom Action: "+ Add Page" Button
                Button(
                    onClick = onAddNewPage,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .height(44.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Page", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
    }
}
