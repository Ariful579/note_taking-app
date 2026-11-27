package com.notewise.tabnotes.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.notewise.tabnotes.data.model.Notebook
import com.notewise.tabnotes.data.model.PaperSize

/**
 * Modern notebook header.
 * Provides back navigation, notebook title with rename, quick page panel toggle badge (1/N),
 * paper size selector, share PDF button, and right-side three-dot menu bar.
 */
@Composable
fun NotebookHeader(
    notebook: Notebook?,
    currentPageIndex: Int,
    pageCount: Int,
    isPagePanelOpen: Boolean,
    paperSize: PaperSize,
    isFullscreen: Boolean,
    isToolbarPinned: Boolean,
    onNavigateBack: () -> Unit,
    onRenameNotebook: () -> Unit,
    onTogglePagePanel: () -> Unit,
    onSelectPaperSize: (PaperSize) -> Unit,
    onToggleFullscreen: () -> Unit,
    onTogglePinToolbar: () -> Unit,
    onSharePdf: () -> Unit,
    onDuplicatePage: () -> Unit = {},
    onClearPage: () -> Unit = {},
    onImportPdf: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showPaperSizeMenu by remember { mutableStateOf(false) }
    var showThreeDotMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Back Arrow + Notebook Title (Click to Rename) + Pages Strip Toggle Pill (1/N)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Back Button to Library / Home
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Library",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Notebook Title with edit pencil (clickable to rename)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onRenameNotebook() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = notebook?.title ?: "Untitled Notebook",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Rename Notebook",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(14.dp)
                    )
                }

                VerticalDivider(modifier = Modifier.height(20.dp).padding(horizontal = 2.dp))

                // Page Count Indicator Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.height(30.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    ) {
                        Text(
                            text = "${currentPageIndex + 1} / $pageCount Pages",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Right: Paper Size + Share PDF + Right Three-Dot Menu
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Paper Size Selector Dropdown (A4, A3, Letter, Legal, Standard)
                Box {
                    OutlinedButton(
                        onClick = { showPaperSizeMenu = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(paperSize.name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(14.dp))
                    }

                    DropdownMenu(
                        expanded = showPaperSizeMenu,
                        onDismissRequest = { showPaperSizeMenu = false }
                    ) {
                        Text(
                            text = "Choose Paper Size",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                        PaperSize.entries.forEach { size ->
                            DropdownMenuItem(
                                text = { Text(size.displayName) },
                                onClick = {
                                    onSelectPaperSize(size)
                                    showPaperSizeMenu = false
                                },
                                leadingIcon = {
                                    if (size == paperSize) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            )
                        }
                    }
                }

                // Fullscreen Mode Toggle Button
                IconButton(
                    onClick = onToggleFullscreen,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = if (isFullscreen) "Exit Fullscreen" else "Enter Fullscreen",
                        tint = if (isFullscreen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Pin/Unpin Toolbar Toggle Button
                IconButton(
                    onClick = onTogglePinToolbar,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isToolbarPinned) Icons.Default.PushPin else Icons.Default.HorizontalRule,
                        contentDescription = if (isToolbarPinned) "Unpin Toolbar" else "Pin Toolbar",
                        tint = if (isToolbarPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Import PDF Button
                FilledTonalButton(
                    onClick = onImportPdf,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = "Import PDF",
                        tint = Color(0xFFDC2626),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Import PDF", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                }

                // Share / Download as PDF Button
                FilledTonalButton(
                    onClick = onSharePdf,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share PDF", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                }

                // Right Side Three-Dot Menu Button (beside Share option)
                Box {
                    IconButton(
                        onClick = { showThreeDotMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Notebook Options Menu",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showThreeDotMenu,
                        onDismissRequest = { showThreeDotMenu = false }
                    ) {
                        Text(
                            text = "Notebook Options",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )

                        DropdownMenuItem(
                            text = { Text("Import PDF into Notebook") },
                            onClick = {
                                showThreeDotMenu = false
                                onImportPdf()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Download / Share as PDF") },
                            onClick = {
                                showThreeDotMenu = false
                                onSharePdf()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Duplicate Current Page") },
                            onClick = {
                                showThreeDotMenu = false
                                onDuplicatePage()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Rename Notebook") },
                            onClick = {
                                showThreeDotMenu = false
                                onRenameNotebook()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        )

                        DropdownMenuItem(
                            text = { Text(if (isFullscreen) "Exit Fullscreen" else "Fullscreen Canvas") },
                            onClick = {
                                showThreeDotMenu = false
                                onToggleFullscreen()
                            },
                            leadingIcon = {
                                Icon(if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        )

                        DropdownMenuItem(
                            text = { Text(if (isToolbarPinned) "Unpin Toolbar" else "Pin Toolbar") },
                            onClick = {
                                showThreeDotMenu = false
                                onTogglePinToolbar()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        )

                        HorizontalDivider()

                        DropdownMenuItem(
                            text = { Text("Clear Page Ink", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showThreeDotMenu = false
                                onClearPage()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            }
                        )
                    }
                }

                // Sidebar Toggle Icon Button (Top-Right Corner)
                IconButton(
                    onClick = onTogglePagePanel
                ) {
                    Icon(
                        imageVector = Icons.Default.ViewSidebar,
                        contentDescription = if (isPagePanelOpen) "Close Pages Sidebar" else "Open Pages Sidebar",
                        tint = if (isPagePanelOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
