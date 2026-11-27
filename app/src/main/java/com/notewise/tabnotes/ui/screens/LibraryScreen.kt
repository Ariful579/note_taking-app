package com.notewise.tabnotes.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notewise.tabnotes.data.model.Folder
import com.notewise.tabnotes.data.model.Notebook
import com.notewise.tabnotes.data.model.PageTemplate
import com.notewise.tabnotes.data.model.PaperColor
import com.notewise.tabnotes.viewmodel.LibraryViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onOpenNotebook: (Notebook) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importPdfAsNotebook(
                context = context,
                uri = uri,
                onSuccess = { createdNotebook ->
                    Toast.makeText(context, "PDF imported: ${createdNotebook.title}", Toast.LENGTH_SHORT).show()
                    onOpenNotebook(createdNotebook)
                },
                onError = { errMsg ->
                    Toast.makeText(context, "Error importing PDF: $errMsg", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    var showCreateNotebookDialog by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }

    var notebookTitleInput by remember { mutableStateOf("") }
    var selectedCoverColor by remember { mutableStateOf(0xFF2563EB) }
    var selectedTemplate by remember { mutableStateOf(PageTemplate.LINED) }

    var folderNameInput by remember { mutableStateOf("") }
    var selectedFolderColor by remember { mutableStateOf(0xFF3B82F6) }

    val coverColors = listOf(
        0xFF2563EB, // Notewise Blue
        0xFF4F46E5, // Indigo
        0xFF059669, // Emerald
        0xFFD97706, // Amber
        0xFFDC2626, // Crimson
        0xFF7C3AED, // Violet
        0xFF0284C7, // Sky
        0xFF475569  // Slate
    )

    val folderColors = listOf(
        0xFF3B82F6, // Blue
        0xFF6366F1, // Indigo
        0xFF10B981, // Emerald
        0xFFF59E0B, // Amber
        0xFFEF4444, // Red
        0xFF8B5CF6, // Purple
        0xFF06B6D4, // Cyan
        0xFF64748B  // Slate
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.currentFolderId != null) {
                            IconButton(onClick = { viewModel.navigateUp() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        // Breadcrumb Navigation
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (state.showOnlyFavorites) "Starred Notes" else "Home",
                                fontWeight = if (state.folderBreadcrumbs.isEmpty()) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 18.sp,
                                color = if (state.folderBreadcrumbs.isEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { viewModel.setViewMode(com.notewise.tabnotes.viewmodel.LibraryViewMode.ALL_FILES) }
                            )

                            state.folderBreadcrumbs.forEachIndexed { index, folder ->
                                Text(" / ", color = MaterialTheme.colorScheme.outline, fontSize = 16.sp)
                                val isLast = index == state.folderBreadcrumbs.size - 1
                                Text(
                                    text = folder.name,
                                    fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 18.sp,
                                    color = if (isLast) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { viewModel.navigateToFolder(folder.id) }
                                )
                            }
                        }
                    }
                },
                actions = {
                    // Search field
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("Search notes...", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (state.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .width(220.dp)
                            .height(48.dp)
                            .padding(end = 8.dp)
                    )

                    // Import PDF Button
                    FilledTonalButton(
                        onClick = { pdfPickerLauncher.launch("application/pdf") },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFFDC2626))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import PDF", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    // Add Folder Button
                    OutlinedButton(
                        onClick = {
                            folderNameInput = ""
                            showCreateFolderDialog = true
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("New Folder", fontSize = 13.sp)
                    }

                    // Add Notebook Button
                    Button(
                        onClick = {
                            notebookTitleInput = ""
                            showCreateNotebookDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("New Notebook", fontSize = 13.sp)
                    }
                }
            )
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp)
        ) {
            // Segmented Filter Tabs: All Files vs Folders
            if (!state.showOnlyFavorites && state.searchQuery.isBlank()) {
                Row(
                    modifier = Modifier
                        .padding(top = 4.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = state.viewMode == com.notewise.tabnotes.viewmodel.LibraryViewMode.ALL_FILES,
                        onClick = { viewModel.setViewMode(com.notewise.tabnotes.viewmodel.LibraryViewMode.ALL_FILES) },
                        label = { Text("All Files (${state.totalNotebookCount})", fontWeight = FontWeight.SemiBold) },
                        leadingIcon = {
                            Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                    FilterChip(
                        selected = state.viewMode == com.notewise.tabnotes.viewmodel.LibraryViewMode.FOLDERS,
                        onClick = { viewModel.setViewMode(com.notewise.tabnotes.viewmodel.LibraryViewMode.FOLDERS) },
                        label = { Text("Folders (${state.allFolders.size})", fontWeight = FontWeight.SemiBold) },
                        leadingIcon = {
                            Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                }
            }

            val hasFolders = state.currentSubfolders.isNotEmpty()
            val hasNotebooks = state.notebooks.isNotEmpty()

            if (!hasFolders && !hasNotebooks) {
                // Empty Library State
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (state.currentFolderId != null) Icons.Default.FolderOpen else Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (state.currentFolderId != null) "This folder is empty" else "No notebooks found",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Create folders and notebooks to organize your notes",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = { pdfPickerLauncher.launch("application/pdf") }) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Import PDF")
                            }
                            OutlinedButton(onClick = { showCreateFolderDialog = true }) {
                                Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Create Folder")
                            }
                            Button(onClick = { showCreateNotebookDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Create Notebook")
                            }
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 180.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxSize().padding(top = 8.dp)
                ) {
                    // Folders Section
                    if (hasFolders) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Folders", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("(${state.currentSubfolders.size})", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                            }
                        }

                        items(state.currentSubfolders, key = { "folder_${it.id}" }) { folder ->
                            FolderCard(
                                folder = folder,
                                notebookCount = state.allFolders.let { _ ->
                                    // Count notebooks inside this folder
                                    state.notebooks.count { it.folderId == folder.id }
                                },
                                onClick = { viewModel.openFolder(folder) },
                                onDelete = { viewModel.deleteFolder(folder) }
                            )
                        }
                    }

                    // Notebooks Section
                    if (hasNotebooks) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = if (hasFolders) 16.dp else 8.dp, bottom = 4.dp)
                            ) {
                                Icon(Icons.Default.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Notebooks", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("(${state.notebooks.size})", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                            }
                        }

                        items(state.notebooks, key = { "nb_${it.id}" }) { notebook ->
                            NotebookCard(
                                notebook = notebook,
                                onClick = { onOpenNotebook(notebook) },
                                onToggleFavorite = { viewModel.toggleFavorite(notebook) },
                                onMoveToFolder = { viewModel.openMoveNotebookDialog(notebook) },
                                onDelete = { viewModel.deleteNotebook(notebook) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialog: Create Folder
    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = {
                Text(
                    text = if (state.currentFolderId != null) "Create Subfolder" else "Create New Folder",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (state.folderBreadcrumbs.isNotEmpty()) {
                        Text(
                            text = "Inside: ${state.folderBreadcrumbs.last().name}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    OutlinedTextField(
                        value = folderNameInput,
                        onValueChange = { folderNameInput = it },
                        label = { Text("Folder Name") },
                        placeholder = { Text("e.g. Mathematics, Personal, Projects") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Folder Color", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        folderColors.forEach { colorVal ->
                            val isSelected = selectedFolderColor == colorVal
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(colorVal))
                                    .clickable { selectedFolderColor = colorVal }
                                    .then(
                                        if (isSelected) Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                        else Modifier
                                    )
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (folderNameInput.isNotBlank()) {
                            viewModel.createFolder(folderNameInput, selectedFolderColor)
                            showCreateFolderDialog = false
                        }
                    },
                    enabled = folderNameInput.isNotBlank()
                ) {
                    Text("Create Folder")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Create Notebook
    if (showCreateNotebookDialog) {
        AlertDialog(
            onDismissRequest = { showCreateNotebookDialog = false },
            title = { Text("Create New Notebook", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (state.folderBreadcrumbs.isNotEmpty()) {
                        Text(
                            text = "In Folder: ${state.folderBreadcrumbs.last().name}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    OutlinedTextField(
                        value = notebookTitleInput,
                        onValueChange = { notebookTitleInput = it },
                        label = { Text("Notebook Title") },
                        placeholder = { Text("e.g. Physics Lecture Notes") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Cover Color", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        coverColors.forEach { colorVal ->
                            val isSelected = selectedCoverColor == colorVal
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(colorVal))
                                    .clickable { selectedCoverColor = colorVal }
                                    .then(
                                        if (isSelected) Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                        else Modifier
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Page Template", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        PageTemplate.entries.take(4).forEach { template ->
                            FilterChip(
                                selected = selectedTemplate == template,
                                onClick = { selectedTemplate = template },
                                label = { Text(template.displayName, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val title = notebookTitleInput.ifBlank { "Untitled Notebook" }
                        viewModel.createNotebook(
                            title = title,
                            coverColor = selectedCoverColor,
                            template = selectedTemplate,
                            onCreated = { onOpenNotebook(it) }
                        )
                        showCreateNotebookDialog = false
                    }
                ) {
                    Text("Create & Open")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateNotebookDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Move Notebook to Folder
    state.notebookToMove?.let { notebook ->
        AlertDialog(
            onDismissRequest = { viewModel.closeMoveNotebookDialog() },
            title = { Text("Move '${notebook.title}'", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Select destination folder:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Root option
                    ListItem(
                        headlineContent = { Text("Root Library (No Folder)") },
                        leadingContent = { Icon(Icons.Default.Home, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { viewModel.moveNotebook(notebook.id, null) }
                    )

                    state.allFolders.forEach { folder ->
                        ListItem(
                            headlineContent = { Text(folder.name) },
                            leadingContent = { Icon(Icons.Default.Folder, contentDescription = null, tint = Color(folder.color)) },
                            trailingContent = {
                                if (notebook.folderId == folder.id) {
                                    Icon(Icons.Default.Check, contentDescription = "Current", tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.moveNotebook(notebook.id, folder.id) }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { viewModel.closeMoveNotebookDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Modal Progress Dialog: Importing PDF Document
    if (state.isImportingPdf) {
        AlertDialog(
            onDismissRequest = { /* Modal: keep showing during import */ },
            confirmButton = {},
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = Color(0xFFDC2626),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Importing PDF", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = state.importProgressMessage.ifBlank { "Processing and rendering PDF pages..." },
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }
}

/**
 * Elegant Folder Card component
 */
@Composable
fun FolderCard(
    folder: Folder,
    notebookCount: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(folder.color).copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = Color(folder.color),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options", modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Open Folder") },
                            leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete Folder", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            Column {
                Text(
                    text = folder.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$notebookCount notebooks",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

/**
 * Notebook Card component with Menu for moving & deleting
 */
@Composable
fun NotebookCard(
    notebook: Notebook,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMoveToFolder: () -> Unit = {},
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp)
            .shadow(4.dp, RoundedCornerShape(12.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Notebook Cover Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(Color(notebook.coverColor))
                    .padding(12.dp)
            ) {
                // Notewise Spine Accent
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(10.dp)
                        .background(Color.Black.copy(alpha = 0.2f))
                        .align(Alignment.CenterStart)
                )

                // Favorite Star
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.align(Alignment.TopEnd).size(32.dp)
                ) {
                    Icon(
                        imageVector = if (notebook.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Favorite",
                        tint = if (notebook.isFavorite) Color(0xFFFACC15) else Color.White.copy(alpha = 0.8f)
                    )
                }

                // Page count pill
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.4f),
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    Text(
                        text = "${notebook.pageCount} ${if (notebook.pageCount == 1) "page" else "pages"}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // Notebook Metadata
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = notebook.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = dateFormat.format(Date(notebook.updatedAt)),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    Box {
                        IconButton(onClick = { showMenu = true }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu", modifier = Modifier.size(16.dp))
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Open") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Move to Folder") },
                                leadingIcon = { Icon(Icons.Default.DriveFileMove, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onMoveToFolder()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (notebook.isFavorite) "Unstar" else "Star") },
                                leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onToggleFavorite()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
