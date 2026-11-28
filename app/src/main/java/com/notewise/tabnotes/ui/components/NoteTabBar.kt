package com.notewise.tabnotes.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notewise.tabnotes.viewmodel.NotebookTab

@Composable
fun NoteTabBar(
    openTabs: List<NotebookTab>,
    activeTabId: String?,
    currentPageIndex: Int,
    pageCount: Int,
    onNavigateHome: () -> Unit = {},
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTabClick: () -> Unit,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onAddNewPage: () -> Unit,
    onRenameNotebook: () -> Unit = {},
    onDuplicatePage: () -> Unit = {},
    onTogglePinToolbar: () -> Unit = {},
    onExportClick: () -> Unit = {},
    onClearPage: () -> Unit = {},
    isToolbarPinned: Boolean = true,
    isFullscreen: Boolean = false,
    onWindowMinimize: () -> Unit = {},
    onWindowMaximize: () -> Unit = {},
    onWindowClose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showThreeBarMenu by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back to Home Screen Button
            IconButton(
                onClick = onNavigateHome,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Home",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Scrollable Tabs Area
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(8.dp))

                openTabs.forEach { tab ->
                    val isActive = tab.notebookId == activeTabId
                    val tabBackground by animateColorAsState(
                        targetValue = if (isActive) MaterialTheme.colorScheme.surface else Color.Transparent,
                        label = "tabBg"
                    )
                    val textColor = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant

                    Row(
                        modifier = Modifier
                            .padding(vertical = 4.dp, horizontal = 2.dp)
                            .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 6.dp, bottomEnd = 6.dp))
                            .background(tabBackground)
                            .padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Clickable Tab Content (Dot + Title)
                        Row(
                            modifier = Modifier
                                .clickable { onSelectTab(tab.notebookId) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Notebook Cover Color Dot
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(tab.coverColor))
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = tab.title,
                                color = textColor,
                                fontSize = 13.sp,
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 140.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Dedicated Close Tab Button ('x')
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .clickable(
                                    onClick = { onCloseTab(tab.notebookId) },
                                    role = androidx.compose.ui.semantics.Role.Button
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Tab",
                                tint = textColor.copy(alpha = 0.75f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                // New Notebook Tab "+" Button
                IconButton(
                    onClick = onNewTabClick,
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Notebook Tab",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))
            }

            VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))

            // Top Quick Page Navigation & Add Page "+" Button
            if (activeTabId != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    IconButton(
                        onClick = onPrevPage,
                        enabled = currentPageIndex > 0,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Previous Page",
                            modifier = Modifier.size(16.dp),
                            tint = if (currentPageIndex > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        )
                    }

                    Text(
                        text = "Page ${currentPageIndex + 1}/$pageCount",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    IconButton(
                        onClick = onNextPage,
                        enabled = currentPageIndex < pageCount - 1,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Next Page",
                            modifier = Modifier.size(16.dp),
                            tint = if (currentPageIndex < pageCount - 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        )
                    }

                }
            }

            VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))

            // Window Control Buttons (Minimize, Maximize/Fullscreen, Close) & Three-Bar Menu
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 6.dp)
            ) {
                IconButton(
                    onClick = onWindowMinimize,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Minimize,
                        contentDescription = "Minimize Window",
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onWindowMaximize,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.CropSquare,
                        contentDescription = if (isFullscreen) "Exit Fullscreen" else "Maximize Window",
                        modifier = Modifier.size(15.dp),
                        tint = if (isFullscreen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onWindowClose,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Window",
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    )
                }

                VerticalDivider(modifier = Modifier.height(20.dp).padding(horizontal = 2.dp))

                // Three Bar Hamburger Menu (Top Right)
                Box {
                    IconButton(
                        onClick = { showThreeBarMenu = true },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Notebook Options Menu",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    DropdownMenu(
                        expanded = showThreeBarMenu,
                        onDismissRequest = { showThreeBarMenu = false }
                    ) {
                        Text(
                            text = "Notebook Menu",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )

                        DropdownMenuItem(
                            text = { Text("Rename Notebook") },
                            onClick = {
                                showThreeBarMenu = false
                                onRenameNotebook()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        )

                        DropdownMenuItem(
                            text = { Text(if (isToolbarPinned) "Unpin Toolbar" else "Pin Toolbar") },
                            onClick = {
                                showThreeBarMenu = false
                                onTogglePinToolbar()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        )

                        DropdownMenuItem(
                            text = { Text(if (isFullscreen) "Exit Fullscreen" else "Tablet Fullscreen Canvas") },
                            onClick = {
                                showThreeBarMenu = false
                                onWindowMaximize()
                            },
                            leadingIcon = {
                                Icon(
                                    if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )

                        HorizontalDivider()

                        DropdownMenuItem(
                            text = { Text("Duplicate Current Page") },
                            onClick = {
                                showThreeBarMenu = false
                                onDuplicatePage()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Export Note") },
                            onClick = {
                                showThreeBarMenu = false
                                onExportClick()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Clear Page Content", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showThreeBarMenu = false
                                onClearPage()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            }
                        )
                    }
                }
            }
        }
    }
}
