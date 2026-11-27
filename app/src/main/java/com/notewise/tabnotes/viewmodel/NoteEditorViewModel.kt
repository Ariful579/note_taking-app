package com.notewise.tabnotes.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notewise.tabnotes.data.model.*
import com.notewise.tabnotes.data.repository.NoteRepository
import com.notewise.tabnotes.util.PdfImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

data class NotebookTab(
    val notebookId: String,
    val title: String,
    val coverColor: Long,
    val currentPageIndex: Int = 0
)

data class EditorUiState(
    val openTabs: List<NotebookTab> = emptyList(),
    val activeTabId: String? = null,
    val activeNotebook: Notebook? = null,
    val activePage: NotePage? = null,
    val pageIndex: Int = 0,
    val pageCount: Int = 1,
    val selectedTool: ToolType = ToolType.PEN,
    val strokeColor: Long = 0xFF0F172A, // InkBlack
    val strokeWidth: Float = 4.0f,
    val highlighterAlpha: Float = 0.38f,
    val paperColor: Long = PaperColor.WHITE.hex,
    val pageTemplate: PageTemplate = PageTemplate.LINED,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isStylusOnlyMode: Boolean = true,
    val isMagicAiEnabled: Boolean = false,
    val selectedShapeType: ToolType = ToolType.SHAPE_RECTANGLE,
    val isExportDialogOpen: Boolean = false,
    val exportData: String = "",
    val exportFormat: String = "JSON",
    val saveStatusMessage: String = "Saved",
    val scale: Float = 1.0f,
    val panOffset: Pair<Float, Float> = Pair(0f, 0f),
    val lastEraserType: ToolType = ToolType.ERASER_STROKE,
    val isToolbarPinned: Boolean = true,
    val isFullscreen: Boolean = false,
    val selectedStrokeIds: Set<String> = emptySet(),
    val clipboardStrokes: List<Stroke> = emptyList(),
    val paperSize: PaperSize = PaperSize.A4,
    val isPagePanelOpen: Boolean = false,
    val allNotebookPages: List<NotePage> = emptyList(),
    val isImportingPdf: Boolean = false,
    val importProgressMessage: String = ""
)

class NoteEditorViewModel(private val repository: NoteRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _currentStrokes = MutableStateFlow<List<Stroke>>(emptyList())
    val currentStrokes: StateFlow<List<Stroke>> = _currentStrokes.asStateFlow()

    private val _textAnnotations = MutableStateFlow<List<TextAnnotation>>(emptyList())
    val textAnnotations: StateFlow<List<TextAnnotation>> = _textAnnotations.asStateFlow()

    private val _imageAttachments = MutableStateFlow<List<ImageAttachment>>(emptyList())
    val imageAttachments: StateFlow<List<ImageAttachment>> = _imageAttachments.asStateFlow()

    private val undoStack = mutableListOf<List<Stroke>>()
    private val redoStack = mutableListOf<List<Stroke>>()

    private var isErasing = false
    private var preEraseSnapshot: List<Stroke>? = null
    private var pagesObservationJob: Job? = null

    private var autoSaveJob: Job? = null

    init {
        // Observe all notebooks so if title or page count changes, tabs stay synced
        viewModelScope.launch {
            repository.allNotebooks.collect { allList ->
                val currentTabs = _uiState.value.openTabs
                if (currentTabs.isEmpty() && allList.isNotEmpty()) {
                    // Open the most recently updated notebook in the first tab by default
                    openNotebookInTab(allList.first())
                } else if (allList.isNotEmpty()) {
                    // Sync tab titles/covers
                    val updatedTabs = currentTabs.map { tab ->
                        val matched = allList.find { it.id == tab.notebookId }
                        if (matched != null) {
                            tab.copy(title = matched.title, coverColor = matched.coverColor)
                        } else tab
                    }
                    _uiState.update { it.copy(openTabs = updatedTabs) }
                }
            }
        }
    }

    // ==========================================
    // Multi-Tab Management & Window Controls
    // ==========================================

    fun openNotebookInTab(notebook: Notebook) {
        val currentTabs = _uiState.value.openTabs.toMutableList()
        val existingIndex = currentTabs.indexOfFirst { it.notebookId == notebook.id }

        if (existingIndex == -1) {
            val newTab = NotebookTab(
                notebookId = notebook.id,
                title = notebook.title,
                coverColor = notebook.coverColor,
                currentPageIndex = 0
            )
            currentTabs.add(newTab)
        }

        _uiState.update {
            it.copy(
                openTabs = currentTabs,
                activeTabId = notebook.id,
                activeNotebook = notebook,
                pageIndex = 0,
                pageCount = maxOf(1, notebook.pageCount),
                isPagePanelOpen = true
            )
        }
        observePagesForNotebook(notebook.id)
        loadPage(notebook.id, 0)
    }

    fun selectTab(notebookId: String) {
        if (_uiState.value.activeTabId == notebookId) return
        saveCurrentPageImmediately()

        val tab = _uiState.value.openTabs.find { it.notebookId == notebookId } ?: return
        viewModelScope.launch {
            val notebook = repository.getNotebookByIdOnce(notebookId)
            _uiState.update {
                it.copy(
                    activeTabId = notebookId,
                    activeNotebook = notebook,
                    pageIndex = tab.currentPageIndex,
                    pageCount = maxOf(1, notebook?.pageCount ?: 1)
                )
            }
            observePagesForNotebook(notebookId)
            loadPage(notebookId, tab.currentPageIndex)
        }
    }

    private fun observePagesForNotebook(notebookId: String) {
        pagesObservationJob?.cancel()
        pagesObservationJob = viewModelScope.launch {
            repository.getPagesForNotebook(notebookId).collect { pages ->
                _uiState.update { it.copy(allNotebookPages = pages, pageCount = maxOf(1, pages.size)) }
            }
        }
    }

    fun closeTab(notebookId: String) {
        saveCurrentPageImmediately()
        val currentTabs = _uiState.value.openTabs.toMutableList()
        val index = currentTabs.indexOfFirst { it.notebookId == notebookId }
        if (index == -1) return

        currentTabs.removeAt(index)

        if (currentTabs.isEmpty()) {
            _uiState.update {
                it.copy(
                    openTabs = emptyList(),
                    activeTabId = null,
                    activeNotebook = null,
                    activePage = null,
                    pageIndex = 0,
                    pageCount = 1
                )
            }
            _currentStrokes.value = emptyList()
            _textAnnotations.value = emptyList()
            _imageAttachments.value = emptyList()
            undoStack.clear()
            redoStack.clear()
            updateUndoRedoAvailability()
        } else {
            val newActiveIndex = if (index >= currentTabs.size) currentTabs.size - 1 else index
            val nextTab = currentTabs[newActiveIndex]
            _uiState.update { it.copy(openTabs = currentTabs) }
            selectTab(nextTab.notebookId)
        }
    }

    fun closeActiveTab() {
        val activeId = _uiState.value.activeTabId ?: return
        closeTab(activeId)
    }

    fun createNewNotebookAndOpenTab(title: String = "Untitled Note", template: PageTemplate = PageTemplate.LINED) {
        viewModelScope.launch {
            val notebook = repository.createNotebook(title = title, template = template)
            openNotebookInTab(notebook)
        }
    }

    // ==========================================
    // Page Management (Top & Bottom Navigation)
    // ==========================================

    private fun loadPage(notebookId: String, pageIndex: Int) {
        undoStack.clear()
        redoStack.clear()
        updateUndoRedoAvailability()

        viewModelScope.launch {
            val page = repository.getPageOnce(notebookId, pageIndex)
            if (page != null) {
                _currentStrokes.value = page.strokes
                _textAnnotations.value = page.textAnnotations
                _imageAttachments.value = page.imageAttachments
                _uiState.update {
                    it.copy(
                        activePage = page,
                        pageIndex = pageIndex,
                        pageTemplate = page.template,
                        paperColor = page.paperColor,
                        saveStatusMessage = "Page ${pageIndex + 1} loaded"
                    )
                }
            } else {
                _currentStrokes.value = emptyList()
                _textAnnotations.value = emptyList()
                _imageAttachments.value = emptyList()
                _uiState.update {
                    it.copy(
                        pageIndex = pageIndex,
                        saveStatusMessage = "Blank page"
                    )
                }
            }
        }
    }

    fun goToPage(targetIndex: Int) {
        val nbId = _uiState.value.activeTabId ?: return
        if (targetIndex < 0 || targetIndex >= _uiState.value.pageCount) return
        if (targetIndex == _uiState.value.pageIndex) return

        val curIndex = _uiState.value.pageIndex
        val curStrokes = _currentStrokes.value
        val curAnnotations = _textAnnotations.value
        val curImages = _imageAttachments.value
        val curTemplate = _uiState.value.pageTemplate
        val curPaperColor = _uiState.value.paperColor

        // Immediately persist the outgoing page to disk so work is never lost on navigation
        viewModelScope.launch(Dispatchers.IO) {
            repository.savePageStrokesAndAnnotations(
                notebookId = nbId,
                pageIndex = curIndex,
                strokes = curStrokes,
                annotations = curAnnotations,
                images = curImages,
                template = curTemplate,
                paperColor = curPaperColor
            )
        }

        // Keep allNotebookPages up-to-date in memory without waiting for disk I/O
        val existingList = _uiState.value.allNotebookPages.toMutableList()
        val curIdxInList = existingList.indexOfFirst { it.pageIndex == curIndex }
        val updatedCurrentPage = if (curIdxInList >= 0) {
            existingList[curIdxInList].copy(
                strokes = curStrokes,
                textAnnotations = curAnnotations,
                imageAttachments = curImages,
                template = curTemplate,
                paperColor = curPaperColor
            )
        } else {
            NotePage(
                notebookId = nbId,
                pageIndex = curIndex,
                strokes = curStrokes,
                textAnnotations = curAnnotations,
                imageAttachments = curImages,
                template = curTemplate,
                paperColor = curPaperColor
            )
        }

        if (curIdxInList >= 0) {
            existingList[curIdxInList] = updatedCurrentPage
        } else {
            existingList.add(updatedCurrentPage)
        }

        // Update tab's current page index
        val updatedTabs = _uiState.value.openTabs.map {
            if (it.notebookId == nbId) it.copy(currentPageIndex = targetIndex) else it
        }

        // Fast in-memory page transition
        val targetPage = existingList.find { it.pageIndex == targetIndex }
        if (targetPage != null) {
            undoStack.clear()
            redoStack.clear()
            updateUndoRedoAvailability()
            _currentStrokes.value = targetPage.strokes
            _textAnnotations.value = targetPage.textAnnotations
            _imageAttachments.value = targetPage.imageAttachments
            _uiState.update {
                it.copy(
                    openTabs = updatedTabs,
                    allNotebookPages = existingList,
                    pageIndex = targetIndex,
                    pageTemplate = targetPage.template,
                    paperColor = targetPage.paperColor,
                    activePage = targetPage
                )
            }
        } else {
            _uiState.update { it.copy(openTabs = updatedTabs, allNotebookPages = existingList, pageIndex = targetIndex) }
            loadPage(nbId, targetIndex)
        }

        saveCurrentPageImmediately()
    }

    /**
     * Dynamically creates and immediately switches to a new blank page in the current notebook.
     */
    fun addNewPage() {
        val nbId = _uiState.value.activeTabId ?: return
        val currentPIndex = _uiState.value.pageIndex
        val strokesToSave = _currentStrokes.value
        val annotationsToSave = _textAnnotations.value
        val imagesToSave = _imageAttachments.value

        viewModelScope.launch {
            // First save current page strokes so work isn't lost
            if (strokesToSave.isNotEmpty() || annotationsToSave.isNotEmpty() || imagesToSave.isNotEmpty()) {
                repository.savePageStrokesAndAnnotations(
                    notebookId = nbId,
                    pageIndex = currentPIndex,
                    strokes = strokesToSave,
                    annotations = annotationsToSave,
                    images = imagesToSave
                )
            }

            // Create new page
            val newPage = repository.addNewPage(
                notebookId = nbId,
                template = _uiState.value.pageTemplate,
                paperColor = _uiState.value.paperColor
            )
            val updatedNb = repository.getNotebookByIdOnce(nbId)
            val newCount = maxOf(updatedNb?.pageCount ?: 1, newPage.pageIndex + 1)

            // Clear ink canvas immediately for the new blank page
            _currentStrokes.value = emptyList()
            _textAnnotations.value = emptyList()
            _imageAttachments.value = emptyList()
            undoStack.clear()
            redoStack.clear()
            updateUndoRedoAvailability()

            // Update open tab's current page index
            val updatedTabs = _uiState.value.openTabs.map {
                if (it.notebookId == nbId) it.copy(currentPageIndex = newPage.pageIndex) else it
            }

            _uiState.update {
                it.copy(
                    openTabs = updatedTabs,
                    activeNotebook = updatedNb,
                    activePage = newPage,
                    pageIndex = newPage.pageIndex,
                    pageCount = newCount,
                    saveStatusMessage = "Page ${newPage.pageIndex + 1} created"
                )
            }
        }
    }

    fun setZoomAndPan(scale: Float, panX: Float, panY: Float) {
        _uiState.update {
            it.copy(
                scale = scale.coerceIn(0.5f, 4.0f),
                panOffset = Pair(panX, panY)
            )
        }
    }

    fun resetZoom() {
        _uiState.update {
            it.copy(
                scale = 1.0f,
                panOffset = Pair(0f, 0f)
            )
        }
    }

    fun changePageTemplate(template: PageTemplate) {
        _uiState.update { it.copy(pageTemplate = template) }
        saveCurrentPageImmediately()
    }

    fun changePaperColor(colorHex: Long) {
        _uiState.update { it.copy(paperColor = colorHex) }
        saveCurrentPageImmediately()
    }

    fun changePaperSize(size: PaperSize) {
        _uiState.update { it.copy(paperSize = size) }
        scheduleAutoSave()
    }

    fun togglePagePanel() {
        _uiState.update { it.copy(isPagePanelOpen = !it.isPagePanelOpen) }
    }

    fun deletePage(pageIndex: Int) {
        val nbId = _uiState.value.activeTabId ?: return
        val currentCount = _uiState.value.pageCount
        if (currentCount <= 1) return

        viewModelScope.launch {
            repository.deletePage(nbId, pageIndex)
            val newIndex = (pageIndex - 1).coerceAtLeast(0)
            _uiState.update { it.copy(pageIndex = newIndex) }
            loadPage(nbId, newIndex)
        }
    }

    // ==========================================
    // Tool & Drawing Operations
    // ==========================================

    fun selectTool(tool: ToolType) {
        _uiState.update {
            if (tool == ToolType.ERASER_STROKE || tool == ToolType.ERASER_POINT) {
                it.copy(selectedTool = tool, lastEraserType = tool)
            } else {
                it.copy(selectedTool = tool)
            }
        }
    }

    fun setEraserType(tool: ToolType) {
        _uiState.update { it.copy(selectedTool = tool, lastEraserType = tool) }
    }

    fun toggleToolbarPin() {
        _uiState.update { it.copy(isToolbarPinned = !it.isToolbarPinned) }
    }

    fun toggleFullscreen() {
        _uiState.update { it.copy(isFullscreen = !it.isFullscreen) }
    }

    fun setStrokeColor(color: Long) {
        _uiState.update { it.copy(strokeColor = color) }
    }

    fun setStrokeWidth(width: Float) {
        _uiState.update { it.copy(strokeWidth = width.coerceIn(1.0f, 60.0f)) }
    }

    fun toggleStylusOnlyMode() {
        _uiState.update {
            val next = !it.isStylusOnlyMode
            it.copy(
                isStylusOnlyMode = next,
                saveStatusMessage = if (next) "1-Finger Scroll Page / Stylus Draws" else "Finger Drawing ON"
            )
        }
    }

    fun toggleMagicAi() {
        _uiState.update {
            val newState = !it.isMagicAiEnabled
            it.copy(
                isMagicAiEnabled = newState,
                saveStatusMessage = if (newState) "AI Smoothing & Shape Snap ON" else "AI Smoothing OFF"
            )
        }
    }

    fun selectShapeTool(shape: ToolType) {
        _uiState.update {
            it.copy(
                selectedTool = shape,
                selectedShapeType = shape
            )
        }
    }

    fun addStroke(stroke: Stroke) {
        val isShape = stroke.toolType in listOf(
            ToolType.SHAPE_LINE,
            ToolType.SHAPE_RECTANGLE,
            ToolType.SHAPE_CIRCLE,
            ToolType.SHAPE_TRIANGLE,
            ToolType.SHAPE_ARROW
        )
        // If Magic AI Smoothing is enabled, smooth or recognize shapes on freehand ink (never smooth geometric shape tools)
        val processedStroke: Stroke = if (_uiState.value.isMagicAiEnabled && !isShape) {
            // Check for shape recognition first
            val recognized = Stroke.tryRecognizeShape(
                rawPoints = stroke.points,
                color = stroke.color,
                width = stroke.width,
                alpha = stroke.alpha
            )
            if (recognized != null) {
                recognized
            } else {
                // Apply weighted moving average smoothing
                val smoothedPts = Stroke.smoothPoints(stroke.points)
                stroke.copy(points = smoothedPts, isAiSmoothed = true)
            }
        } else {
            stroke
        }

        // Push state for undo
        undoStack.add(_currentStrokes.value.toList())
        redoStack.clear()

        _currentStrokes.value = _currentStrokes.value + processedStroke
        updateUndoRedoAvailability()
        scheduleAutoSave()
    }

    fun startEraseSession() {
        if (!isErasing) {
            isErasing = true
            preEraseSnapshot = _currentStrokes.value.toList()
        }
    }

    fun endEraseSession() {
        if (isErasing) {
            isErasing = false
            val initial = preEraseSnapshot
            preEraseSnapshot = null
            if (initial != null && initial != _currentStrokes.value) {
                undoStack.add(initial)
                redoStack.clear()
                updateUndoRedoAvailability()
                scheduleAutoSave()
            }
        }
    }

    fun eraseStrokesAt(point: Point, radius: Float = 20f) {
        if (!isErasing) {
            startEraseSession()
        }
        val strokes = _currentStrokes.value
        val isPrecisionEraser = _uiState.value.selectedTool == ToolType.ERASER_POINT

        if (isPrecisionEraser) {
            var changed = false
            val newStrokes = ArrayList<Stroke>(strokes.size)
            for (stroke in strokes) {
                if (stroke.intersects(point, radius)) {
                    val split = stroke.erasePointsWithin(point, radius)
                    newStrokes.addAll(split)
                    changed = true
                } else {
                    newStrokes.add(stroke)
                }
            }
            if (changed) {
                _currentStrokes.value = newStrokes
            }
        } else {
            val surviving = strokes.filterNot { it.intersects(point, radius) }
            if (surviving.size != strokes.size) {
                _currentStrokes.value = surviving
            }
        }
    }

    // ==========================================
    // Lasso Selection & Editing Operations
    // ==========================================

    fun selectStrokesByLasso(lassoPoints: List<Point>) {
        if (lassoPoints.size < 3) {
            clearSelection()
            return
        }
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        for (p in lassoPoints) {
            if (p.x < minX) minX = p.x
            if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y
            if (p.y > maxY) maxY = p.y
        }

        val strokes = _currentStrokes.value
        val selected = strokes.filter { s ->
            // Stroke is considered selected if any of its points or center falls inside lasso bounding box
            val b = s.boundingBox
            val cx = (b.left + b.right) / 2f
            val cy = (b.top + b.bottom) / 2f
            (cx in minX..maxX && cy in minY..maxY) || s.points.any { it.x in minX..maxX && it.y in minY..maxY }
        }.map { it.id }.toSet()

        _uiState.update { it.copy(selectedStrokeIds = selected) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedStrokeIds = emptySet()) }
    }

    fun copySelectedStrokes() {
        val selectedIds = _uiState.value.selectedStrokeIds
        if (selectedIds.isEmpty()) return
        val toCopy = _currentStrokes.value.filter { it.id in selectedIds }
        _uiState.update { it.copy(clipboardStrokes = toCopy) }
    }

    fun pasteStrokes(offsetX: Float = 40f, offsetY: Float = 40f) {
        val clipboard = _uiState.value.clipboardStrokes
        if (clipboard.isEmpty()) return
        val pasted = clipboard.map { it.copy(id = java.util.UUID.randomUUID().toString()).translate(offsetX, offsetY) }
        undoStack.add(_currentStrokes.value.toList())
        redoStack.clear()
        _currentStrokes.value = _currentStrokes.value + pasted
        _uiState.update { it.copy(selectedStrokeIds = pasted.map { p -> p.id }.toSet()) }
        updateUndoRedoAvailability()
        scheduleAutoSave()
    }

    fun recordUndoForSelection() {
        undoStack.add(_currentStrokes.value.toList())
        redoStack.clear()
        updateUndoRedoAvailability()
    }

    fun moveSelectedStrokes(dx: Float, dy: Float) {
        val selectedIds = _uiState.value.selectedStrokeIds
        if (selectedIds.isEmpty()) return
        _currentStrokes.value = _currentStrokes.value.map {
            if (it.id in selectedIds) it.translate(dx, dy) else it
        }
        scheduleAutoSave()
    }

    fun scaleSelectedStrokes(factor: Float) {
        val selectedIds = _uiState.value.selectedStrokeIds
        if (selectedIds.isEmpty()) return
        val selectedStrokes = _currentStrokes.value.filter { it.id in selectedIds }
        if (selectedStrokes.isEmpty()) return
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        for (s in selectedStrokes) {
            val b = s.boundingBox
            if (b.left < minX) minX = b.left
            if (b.top < minY) minY = b.top
            if (b.right > maxX) maxX = b.right
            if (b.bottom > maxY) maxY = b.bottom
        }
        val cx = (minX + maxX) / 2f
        val cy = (minY + maxY) / 2f

        undoStack.add(_currentStrokes.value.toList())
        redoStack.clear()
        _currentStrokes.value = _currentStrokes.value.map {
            if (it.id in selectedIds) it.scaleAround(cx, cy, factor) else it
        }
        updateUndoRedoAvailability()
        scheduleAutoSave()
    }

    fun deleteSelectedStrokes() {
        val selectedIds = _uiState.value.selectedStrokeIds
        if (selectedIds.isEmpty()) return
        undoStack.add(_currentStrokes.value.toList())
        redoStack.clear()
        _currentStrokes.value = _currentStrokes.value.filterNot { it.id in selectedIds }
        _uiState.update { it.copy(selectedStrokeIds = emptySet()) }
        updateUndoRedoAvailability()
        scheduleAutoSave()
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val previousState = undoStack.removeAt(undoStack.size - 1)
            redoStack.add(_currentStrokes.value.toList())
            _currentStrokes.value = previousState
            updateUndoRedoAvailability()
            scheduleAutoSave()
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val nextState = redoStack.removeAt(redoStack.size - 1)
            undoStack.add(_currentStrokes.value.toList())
            _currentStrokes.value = nextState
            updateUndoRedoAvailability()
            scheduleAutoSave()
        }
    }

    fun clearCurrentPage() {
        if (_currentStrokes.value.isNotEmpty() || _textAnnotations.value.isNotEmpty() || _imageAttachments.value.isNotEmpty()) {
            undoStack.add(_currentStrokes.value.toList())
            redoStack.clear()
            _currentStrokes.value = emptyList()
            _textAnnotations.value = emptyList()
            _imageAttachments.value = emptyList()
            updateUndoRedoAvailability()
            scheduleAutoSave()
        }
    }

    fun addTextAnnotation(annotation: TextAnnotation) {
        _textAnnotations.value = _textAnnotations.value + annotation
        scheduleAutoSave()
    }

    fun updateTextAnnotation(updated: TextAnnotation) {
        _textAnnotations.value = _textAnnotations.value.map {
            if (it.id == updated.id) updated else it
        }
        scheduleAutoSave()
    }

    fun removeTextAnnotation(id: String) {
        _textAnnotations.value = _textAnnotations.value.filter { it.id != id }
        scheduleAutoSave()
    }

    fun addImageAttachment(
        filePath: String,
        width: Float = 360f,
        height: Float = 270f,
        x: Float = 80f,
        y: Float = 80f
    ): ImageAttachment {
        val newImg = ImageAttachment(
            filePath = filePath,
            x = x,
            y = y,
            width = width,
            height = height
        )
        _imageAttachments.value = _imageAttachments.value + newImg
        scheduleAutoSave()
        return newImg
    }

    fun updateImagePosition(id: String, x: Float, y: Float) {
        _imageAttachments.value = _imageAttachments.value.map {
            if (it.id == id) it.copy(x = x.coerceAtLeast(0f), y = y.coerceAtLeast(0f)) else it
        }
        scheduleAutoSave()
    }

    fun updateImageSize(id: String, width: Float, height: Float) {
        _imageAttachments.value = _imageAttachments.value.map {
            if (it.id == id) it.copy(
                width = width.coerceIn(80f, 2000f),
                height = height.coerceIn(60f, 2000f)
            ) else it
        }
        scheduleAutoSave()
    }

    fun updateImageRotation(id: String, rotation: Float) {
        _imageAttachments.value = _imageAttachments.value.map {
            if (it.id == id) it.copy(rotation = rotation % 360f) else it
        }
        scheduleAutoSave()
    }

    fun duplicateImage(id: String) {
        val original = _imageAttachments.value.find { it.id == id } ?: return
        val clone = original.copy(
            id = java.util.UUID.randomUUID().toString(),
            x = original.x + 36f,
            y = original.y + 36f
        )
        _imageAttachments.value = _imageAttachments.value + clone
        scheduleAutoSave()
    }

    fun removeImageAttachment(id: String) {
        _imageAttachments.value = _imageAttachments.value.filterNot { it.id == id }
        scheduleAutoSave()
    }

    fun renameActiveNotebook(newTitle: String) {
        val activeId = _uiState.value.activeTabId ?: return
        if (newTitle.isBlank()) return
        viewModelScope.launch {
            repository.renameNotebook(activeId, newTitle.trim())
            _uiState.update { state ->
                state.copy(
                    activeNotebook = state.activeNotebook?.copy(title = newTitle.trim()),
                    openTabs = state.openTabs.map {
                        if (it.notebookId == activeId) it.copy(title = newTitle.trim()) else it
                    }
                )
            }
        }
    }

    fun duplicateCurrentPage() {
        val activeId = _uiState.value.activeTabId ?: return
        val curIndex = _uiState.value.pageIndex
        saveCurrentPageImmediately()
        viewModelScope.launch {
            val newPage = repository.duplicatePage(activeId, curIndex)
            if (newPage != null) {
                val nb = repository.getNotebookByIdOnce(activeId)
                val totalPages = nb?.pageCount ?: (newPage.pageIndex + 1)
                _uiState.update { it.copy(pageCount = totalPages) }
                goToPage(newPage.pageIndex)
            }
        }
    }

    private fun updateUndoRedoAvailability() {
        _uiState.update {
            it.copy(
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
    }

    // ==========================================
    // File Saving & Exporting
    // ==========================================

    private fun scheduleAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            _uiState.update { it.copy(saveStatusMessage = "Saving...") }
            delay(400) // 400ms debounce
            saveCurrentPageImmediately()
        }
    }

    fun saveCurrentPageImmediately() {
        val nbId = _uiState.value.activeTabId ?: return
        val pIndex = _uiState.value.pageIndex
        val strokesToSave = _currentStrokes.value
        val annotationsToSave = _textAnnotations.value
        val imagesToSave = _imageAttachments.value
        val currentTemplate = _uiState.value.pageTemplate
        val currentPaperColor = _uiState.value.paperColor

        viewModelScope.launch {
            repository.savePageStrokesAndAnnotations(
                notebookId = nbId,
                pageIndex = pIndex,
                strokes = strokesToSave,
                annotations = annotationsToSave,
                images = imagesToSave,
                template = currentTemplate,
                paperColor = currentPaperColor
            )
            _uiState.update { it.copy(saveStatusMessage = "All changes saved to database") }
        }
    }

    fun openExportDialog(format: String = "JSON") {
        val nbId = _uiState.value.activeTabId ?: return
        viewModelScope.launch {
            val content = if (format == "JSON") {
                repository.exportNotebookAsJson(nbId)
            } else {
                repository.exportPageAsText(nbId, _uiState.value.pageIndex)
            }
            _uiState.update {
                it.copy(
                    isExportDialogOpen = true,
                    exportFormat = format,
                    exportData = content
                )
            }
        }
    }

    fun closeExportDialog() {
        _uiState.update { it.copy(isExportDialogOpen = false) }
    }

    /**
     * Builds and returns the comprehensive, up-to-the-millisecond list of NotePages
     * for high-fidelity PDF export, ensuring current unsaved strokes, text annotations,
     * and image attachments on the active page are fully incorporated.
     */
    fun getCompletePagesForExport(): List<NotePage> {
        saveCurrentPageImmediately()
        val activeId = _uiState.value.activeTabId ?: return emptyList()
        val curIndex = _uiState.value.pageIndex
        val curStrokes = _currentStrokes.value
        val curAnnotations = _textAnnotations.value
        val curImages = _imageAttachments.value
        val curTemplate = _uiState.value.pageTemplate
        val curPaperColor = _uiState.value.paperColor
        val existingPages = _uiState.value.allNotebookPages.toMutableList()

        val activePageIndexInList = existingPages.indexOfFirst { it.pageIndex == curIndex }
        val activePageUpdated = if (activePageIndexInList >= 0) {
            existingPages[activePageIndexInList].copy(
                strokes = curStrokes,
                textAnnotations = curAnnotations,
                imageAttachments = curImages,
                template = curTemplate,
                paperColor = curPaperColor
            )
        } else {
            NotePage(
                notebookId = activeId,
                pageIndex = curIndex,
                strokes = curStrokes,
                textAnnotations = curAnnotations,
                imageAttachments = curImages,
                template = curTemplate,
                paperColor = curPaperColor
            )
        }

        if (activePageIndexInList >= 0) {
            existingPages[activePageIndexInList] = activePageUpdated
        } else {
            existingPages.add(activePageUpdated)
        }

        return existingPages.sortedBy { it.pageIndex }
    }

    fun importPdfIntoNotebook(
        context: Context,
        uri: Uri,
        onSuccess: (Int) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val nbId = _uiState.value.activeTabId ?: return
        saveCurrentPageImmediately()
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
                val addedPages = repository.appendPdfPagesToNotebook(
                    notebookId = nbId,
                    pageImagePaths = importResult.pageImagePaths,
                    pageAspectRatio = importResult.aspectRatio
                )
                val updatedNb = repository.getNotebookByIdOnce(nbId)
                val allPages = repository.getPagesForNotebookOnce(nbId)
                withContext(Dispatchers.Main) {
                    if (updatedNb != null) {
                        _uiState.update { it.copy(
                            activeNotebook = updatedNb,
                            pageCount = updatedNb.pageCount,
                            allNotebookPages = allPages,
                            isImportingPdf = false,
                            importProgressMessage = ""
                        ) }
                    } else {
                        _uiState.update { it.copy(isImportingPdf = false, importProgressMessage = "") }
                    }
                    loadPage(nbId, _uiState.value.pageIndex)
                    onSuccess(addedPages.size)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isImportingPdf = false, importProgressMessage = "") }
                    onError(e.message ?: "Failed to import PDF")
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        autoSaveJob?.cancel()
        val nbId = _uiState.value.activeTabId ?: return
        val pIndex = _uiState.value.pageIndex
        val strokesToSave = _currentStrokes.value
        val annotationsToSave = _textAnnotations.value
        val imagesToSave = _imageAttachments.value
        val currentTemplate = _uiState.value.pageTemplate
        val currentPaperColor = _uiState.value.paperColor

        runBlocking(Dispatchers.IO) {
            try {
                repository.savePageStrokesAndAnnotations(
                    notebookId = nbId,
                    pageIndex = pIndex,
                    strokes = strokesToSave,
                    annotations = annotationsToSave,
                    images = imagesToSave,
                    template = currentTemplate,
                    paperColor = currentPaperColor
                )
            } catch (_: Exception) {}
        }
    }
}
