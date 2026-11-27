package com.notewise.tabnotes.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notewise.tabnotes.data.model.Folder
import com.notewise.tabnotes.data.model.Notebook
import com.notewise.tabnotes.data.model.PageTemplate
import com.notewise.tabnotes.data.model.PaperColor
import com.notewise.tabnotes.data.repository.NoteRepository
import com.notewise.tabnotes.util.PdfImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LibraryViewMode {
    ALL_FILES,
    FOLDERS
}

data class LibraryUiState(
    val notebooks: List<Notebook> = emptyList(),
    val allFolders: List<Folder> = emptyList(),
    val currentSubfolders: List<Folder> = emptyList(),
    val currentFolderId: String? = null,
    val folderBreadcrumbs: List<Folder> = emptyList(),
    val showOnlyFavorites: Boolean = false,
    val viewMode: LibraryViewMode = LibraryViewMode.ALL_FILES,
    val totalNotebookCount: Int = 0,
    val searchQuery: String = "",
    val isCreateNotebookDialogOpen: Boolean = false,
    val isCreateFolderDialogOpen: Boolean = false,
    val notebookToMove: Notebook? = null,
    val isImportingPdf: Boolean = false,
    val importProgressMessage: String = ""
)

private data class FilterParams(
    val folderId: String?,
    val favoritesOnly: Boolean,
    val viewMode: LibraryViewMode,
    val query: String
)

class LibraryViewModel(private val repository: NoteRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.allNotebooks,
                repository.allFolders,
                _uiState.map {
                    FilterParams(it.currentFolderId, it.showOnlyFavorites, it.viewMode, it.searchQuery)
                }.distinctUntilChanged()
            ) { allNotebooks, allFolders, filter ->
                val folderId = filter.folderId
                val favoritesOnly = filter.favoritesOnly
                val mode = filter.viewMode
                val query = filter.query

                // Build breadcrumb trail
                val breadcrumbs = mutableListOf<Folder>()
                var currId = folderId
                while (currId != null) {
                    val f = allFolders.find { it.id == currId }
                    if (f != null) {
                        breadcrumbs.add(0, f)
                        currId = f.parentFolderId
                    } else {
                        break
                    }
                }

                // Subfolders in the current folder view
                val subfolders = if (favoritesOnly || mode == LibraryViewMode.ALL_FILES) {
                    emptyList()
                } else if (query.isNotBlank()) {
                    allFolders.filter { it.name.contains(query, ignoreCase = true) }
                } else {
                    allFolders.filter { it.parentFolderId == folderId }
                }

                // Notebooks in the current folder view
                var displayedNotebooks = allNotebooks
                if (favoritesOnly) {
                    displayedNotebooks = displayedNotebooks.filter { it.isFavorite }
                } else if (query.isNotBlank()) {
                    displayedNotebooks = displayedNotebooks.filter { it.title.contains(query, ignoreCase = true) }
                } else if (mode == LibraryViewMode.ALL_FILES) {
                    displayedNotebooks = allNotebooks
                } else {
                    displayedNotebooks = displayedNotebooks.filter { it.folderId == folderId }
                }

                _uiState.value.copy(
                    notebooks = displayedNotebooks,
                    allFolders = allFolders,
                    currentSubfolders = subfolders,
                    folderBreadcrumbs = breadcrumbs,
                    totalNotebookCount = allNotebooks.size
                )
            }.collect { updatedState ->
                _uiState.value = updatedState
            }
        }
    }

    fun setViewMode(mode: LibraryViewMode) {
        _uiState.update { it.copy(viewMode = mode, currentFolderId = null, showOnlyFavorites = false, searchQuery = "") }
    }

    fun openFolder(folder: Folder) {
        _uiState.update { it.copy(viewMode = LibraryViewMode.FOLDERS, currentFolderId = folder.id, showOnlyFavorites = false, searchQuery = "") }
    }

    fun navigateToFolder(folderId: String?) {
        _uiState.update { it.copy(viewMode = LibraryViewMode.FOLDERS, currentFolderId = folderId, showOnlyFavorites = false, searchQuery = "") }
    }

    fun navigateUp() {
        val currentBreadcrumbs = _uiState.value.folderBreadcrumbs
        if (currentBreadcrumbs.size > 1) {
            val parentFolder = currentBreadcrumbs[currentBreadcrumbs.size - 2]
            _uiState.update { it.copy(currentFolderId = parentFolder.id) }
        } else {
            _uiState.update { it.copy(currentFolderId = null) }
        }
    }

    fun setFavoritesFilter(onlyFavorites: Boolean) {
        _uiState.update { it.copy(showOnlyFavorites = onlyFavorites, currentFolderId = null) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun openCreateNotebookDialog() {
        _uiState.update { it.copy(isCreateNotebookDialogOpen = true) }
    }

    fun closeCreateNotebookDialog() {
        _uiState.update { it.copy(isCreateNotebookDialogOpen = false) }
    }

    fun openCreateFolderDialog() {
        _uiState.update { it.copy(isCreateFolderDialogOpen = true) }
    }

    fun closeCreateFolderDialog() {
        _uiState.update { it.copy(isCreateFolderDialogOpen = false) }
    }

    fun openMoveNotebookDialog(notebook: Notebook) {
        _uiState.update { it.copy(notebookToMove = notebook) }
    }

    fun closeMoveNotebookDialog() {
        _uiState.update { it.copy(notebookToMove = null) }
    }

    fun createNotebook(
        title: String,
        coverColor: Long,
        template: PageTemplate,
        paperColor: Long = PaperColor.WHITE.hex,
        onCreated: (Notebook) -> Unit = {}
    ) {
        viewModelScope.launch {
            val nb = repository.createNotebook(
                title = title.ifBlank { "Untitled Notebook" },
                folderId = _uiState.value.currentFolderId,
                coverColor = coverColor,
                template = template,
                paperColor = paperColor
            )
            closeCreateNotebookDialog()
            onCreated(nb)
        }
    }

    fun createFolder(name: String, color: Long = 0xFF64748B) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.createFolder(
                name = name.trim(),
                parentFolderId = _uiState.value.currentFolderId,
                color = color
            )
            closeCreateFolderDialog()
        }
    }

    fun deleteFolder(folder: Folder) {
        viewModelScope.launch {
            repository.deleteFolder(folder)
            if (_uiState.value.currentFolderId == folder.id) {
                navigateUp()
            }
        }
    }

    fun moveNotebook(notebookId: String, targetFolderId: String?) {
        viewModelScope.launch {
            repository.moveNotebookToFolder(notebookId, targetFolderId)
            closeMoveNotebookDialog()
        }
    }

    fun toggleFavorite(notebook: Notebook) {
        viewModelScope.launch {
            repository.updateNotebook(notebook.copy(isFavorite = !notebook.isFavorite))
        }
    }

    fun deleteNotebook(notebook: Notebook) {
        viewModelScope.launch {
            repository.deleteNotebook(notebook)
        }
    }

    fun importPdfAsNotebook(
        context: Context,
        uri: Uri,
        onSuccess: (Notebook) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isImportingPdf = true, importProgressMessage = "Reading and converting PDF...") }
            try {
                val importResult = PdfImporter.importPdf(context, uri) { current, total ->
                    _uiState.update { it.copy(importProgressMessage = "Rendering page $current of $total...") }
                }
                if (importResult.pageImagePaths.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(isImportingPdf = false, importProgressMessage = "") }
                        onError("Failed to read pages from the PDF document.")
                    }
                    return@launch
                }
                val createdNotebook = repository.createNotebookFromPdf(
                    title = importResult.title,
                    folderId = _uiState.value.currentFolderId,
                    pageImagePaths = importResult.pageImagePaths,
                    pageAspectRatio = importResult.aspectRatio
                )
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isImportingPdf = false, importProgressMessage = "") }
                    onSuccess(createdNotebook)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isImportingPdf = false, importProgressMessage = "") }
                    onError(e.message ?: "Failed to import PDF")
                }
            }
        }
    }
}
