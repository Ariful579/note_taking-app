package com.notewise.tabnotes.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.notewise.tabnotes.data.model.TextAnnotation
import com.notewise.tabnotes.data.model.ToolType
import com.notewise.tabnotes.ui.components.*
import com.notewise.tabnotes.util.PdfExporter
import com.notewise.tabnotes.viewmodel.EditorUiState
import com.notewise.tabnotes.viewmodel.LibraryViewModel
import com.notewise.tabnotes.viewmodel.NoteEditorViewModel
import java.io.File
import java.io.FileOutputStream

@Composable
fun MainTabletScreen(
    editorViewModel: NoteEditorViewModel,
    libraryViewModel: LibraryViewModel,
    modifier: Modifier = Modifier
) {
    // Default startup route: launches directly to the Home Page / Dashboard
    var currentNavDestination by remember { mutableStateOf(TabletNavDestination.LIBRARY) }
    val editorState by editorViewModel.uiState.collectAsState()
    val strokes by editorViewModel.currentStrokes.collectAsState()
    val images by editorViewModel.imageAttachments.collectAsState()
    val textAnnotations by editorViewModel.textAnnotations.collectAsState()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    // Zoom and pan state for canvas multi-touch gestures
    var zoom by remember { mutableFloatStateOf(1.0f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    // Dialog & UI interaction states
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameTitleInput by remember { mutableStateOf("") }
    var selectedImageId by remember { mutableStateOf<String?>(null) }
    var activeEditingTextId by remember { mutableStateOf<String?>(null) }
    val textFocusRequester = remember { FocusRequester() }
    var isToolbarExpandedWhenUnpinned by remember { mutableStateOf(false) }

    LaunchedEffect(activeEditingTextId) {
        if (activeEditingTextId != null) {
            delay(80)
            textFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    // Image Picker Launcher for device storage
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null && bytes.isNotEmpty()) {
                    val imagesDir = File(context.filesDir, "note_images").apply { if (!exists()) mkdirs() }
                    val destFile = File(imagesDir, "img_${System.currentTimeMillis()}.jpg")
                    destFile.writeBytes(bytes)

                    val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)

                    val origWidth = boundsOptions.outWidth.toFloat()
                    val origHeight = boundsOptions.outHeight.toFloat()
                    val aspectRatio = if (origHeight > 0f) origWidth / origHeight else 1.33f
                    val displayWidth = 360f
                    val displayHeight = (displayWidth / aspectRatio).coerceIn(80f, 1600f)

                    val initialDocX = ((-panOffset.x + 120f) / zoom).coerceAtLeast(40f)
                    val initialDocY = ((-panOffset.y + 120f) / zoom).coerceAtLeast(40f)

                    val addedImg = editorViewModel.addImageAttachment(
                        filePath = destFile.absolutePath,
                        width = displayWidth,
                        height = displayHeight,
                        x = initialDocX,
                        y = initialDocY
                    )
                    selectedImageId = addedImg.id
                    Toast.makeText(context, "Image added! Drag corners to resize.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load image: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // PDF Picker Launcher for importing PDF pages directly into the active notebook
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            editorViewModel.importPdfIntoNotebook(
                context = context,
                uri = uri,
                onSuccess = { pageCount ->
                    Toast.makeText(context, "Successfully imported $pageCount PDF pages!", Toast.LENGTH_SHORT).show()
                },
                onError = { errMsg ->
                    Toast.makeText(context, "Error importing PDF: $errMsg", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    // Reset zoom and pan whenever active note changes (start smoothly at top of sheet)
    LaunchedEffect(editorState.activeTabId) {
        zoom = 1.0f
        panOffset = Offset(0f, 30f)
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        color = MaterialTheme.colorScheme.background
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // 1. Tablet Left Navigation Rail (Only shown on Library / Home / Favorites screen, NOT inside notebook editor)
            if (currentNavDestination != TabletNavDestination.EDITOR) {
                TabletNavigationRail(
                    currentDestination = currentNavDestination,
                    onNavigate = { destination ->
                        if (destination == TabletNavDestination.FAVORITES) {
                            libraryViewModel.setFavoritesFilter(true)
                            currentNavDestination = TabletNavDestination.LIBRARY
                        } else if (destination == TabletNavDestination.LIBRARY) {
                            libraryViewModel.setFavoritesFilter(false)
                            currentNavDestination = TabletNavDestination.LIBRARY
                        } else {
                            currentNavDestination = TabletNavDestination.EDITOR
                        }
                    },
                    onNewNotebook = {
                        editorViewModel.createNewNotebookAndOpenTab()
                        currentNavDestination = TabletNavDestination.EDITOR
                    }
                )

                VerticalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            }

        // 2. Main Content Canvas or Library Gallery
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            when (currentNavDestination) {
                TabletNavDestination.LIBRARY, TabletNavDestination.FAVORITES -> {
                    LibraryScreen(
                        viewModel = libraryViewModel,
                        onOpenNotebook = { notebook ->
                            editorViewModel.openNotebookInTab(notebook)
                            currentNavDestination = TabletNavDestination.EDITOR
                        }
                    )
                }

                TabletNavDestination.EDITOR -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Modern Notebook Header Bar (replaces browser tab bar)
                        NotebookHeader(
                            notebook = editorState.activeNotebook,
                            currentPageIndex = editorState.pageIndex,
                            pageCount = editorState.pageCount,
                            isPagePanelOpen = editorState.isPagePanelOpen,
                            paperSize = editorState.paperSize,
                            isFullscreen = editorState.isFullscreen,
                            isToolbarPinned = editorState.isToolbarPinned,
                            onNavigateBack = { currentNavDestination = TabletNavDestination.LIBRARY },
                            onRenameNotebook = {
                                renameTitleInput = editorState.activeNotebook?.title ?: ""
                                showRenameDialog = true
                            },
                            onTogglePagePanel = { editorViewModel.togglePagePanel() },
                            onSelectPaperSize = { size ->
                                editorViewModel.changePaperSize(size)
                                Toast.makeText(context, "Paper size: ${size.displayName}", Toast.LENGTH_SHORT).show()
                            },
                            onToggleFullscreen = { editorViewModel.toggleFullscreen() },
                            onTogglePinToolbar = { editorViewModel.toggleToolbarPin() },
                            onSharePdf = {
                                editorState.activeNotebook?.let { nb ->
                                    val completePages = editorViewModel.getCompletePagesForExport()
                                    PdfExporter.exportAndSharePdf(
                                        context = context,
                                        notebook = nb,
                                        pages = completePages
                                    )
                                } ?: Toast.makeText(context, "No active notebook to export", Toast.LENGTH_SHORT).show()
                            },
                            onDuplicatePage = {
                                editorViewModel.duplicateCurrentPage()
                                Toast.makeText(context, "Page duplicated", Toast.LENGTH_SHORT).show()
                            },
                            onClearPage = {
                                editorViewModel.clearCurrentPage()
                                Toast.makeText(context, "Page ink cleared", Toast.LENGTH_SHORT).show()
                            },
                            onImportPdf = {
                                pdfPickerLauncher.launch("application/pdf")
                            }
                        )

                        if (editorState.activeTabId != null) {
                            // Stylus & Drawing Toolbar (Pinned or Expanded when Unpinned)
                            val isToolbarVisible = editorState.isToolbarPinned || isToolbarExpandedWhenUnpinned
                            if (isToolbarVisible) {
                                StylusToolbar(
                                    selectedTool = editorState.selectedTool,
                                    strokeColor = editorState.strokeColor,
                                    strokeWidth = editorState.strokeWidth,
                                    selectedTemplate = editorState.pageTemplate,
                                    selectedPaperColor = editorState.paperColor,
                                    canUndo = editorState.canUndo,
                                    canRedo = editorState.canRedo,
                                    isStylusOnlyMode = editorState.isStylusOnlyMode,
                                    isMagicAiEnabled = editorState.isMagicAiEnabled,
                                    selectedShapeType = editorState.selectedShapeType,
                                    saveStatusMessage = editorState.saveStatusMessage,
                                    lastEraserType = editorState.lastEraserType,
                                    isToolbarPinned = editorState.isToolbarPinned,
                                    onSelectTool = { newTool ->
                                        if (newTool != ToolType.TEXT) {
                                            val cur = textAnnotations.find { it.id == activeEditingTextId }
                                            if (cur != null && cur.text.isBlank()) {
                                                editorViewModel.removeTextAnnotation(cur.id)
                                            }
                                            activeEditingTextId = null
                                            keyboardController?.hide()
                                        }
                                        editorViewModel.selectTool(newTool)
                                    },
                                    onSelectColor = { editorViewModel.setStrokeColor(it) },
                                    onSelectWidth = { editorViewModel.setStrokeWidth(it) },
                                    onSelectTemplate = { editorViewModel.changePageTemplate(it) },
                                    onSelectPaperColor = { editorViewModel.changePaperColor(it) },
                                    onToggleMagicAi = { editorViewModel.toggleMagicAi() },
                                    onSelectShape = { editorViewModel.selectShapeTool(it) },
                                    onExportClick = {
                                        editorState.activeNotebook?.let { nb ->
                                            val completePages = editorViewModel.getCompletePagesForExport()
                                            PdfExporter.exportAndSharePdf(
                                                context = context,
                                                notebook = nb,
                                                pages = completePages
                                            )
                                        } ?: Toast.makeText(context, "No active notebook to export", Toast.LENGTH_SHORT).show()
                                    },
                                    onUndo = { editorViewModel.undo() },
                                    onRedo = { editorViewModel.redo() },
                                    onClearPage = { editorViewModel.clearCurrentPage() },
                                    onToggleStylusMode = { editorViewModel.toggleStylusOnlyMode() },
                                    onTogglePin = { editorViewModel.toggleToolbarPin() },
                                    onAddImage = { imagePickerLauncher.launch("image/*") },
                                    onImportPdf = { pdfPickerLauncher.launch("application/pdf") }
                                )
                            }

                            // Note Canvas Workspace with Contrasting Desk Background
                            val paperColor = Color(editorState.paperColor)
                            val isDarkPaper = (paperColor.red * 0.299f + paperColor.green * 0.587f + paperColor.blue * 0.114f) < 0.48f
                            val deskBackgroundColor = if (isDarkPaper) Color(0xFFE2E8F0) else Color(0xFF121316)

                            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                BoxWithConstraints(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .background(deskBackgroundColor)
                                        .clipToBounds()
                                ) {
                                    val density = LocalDensity.current
                                    val viewportWidthPx = constraints.maxWidth.toFloat()
                                    val viewportHeightPx = constraints.maxHeight.toFloat()

                                    // Continuous vertical multi-page geometry (A4, A3, Legal, etc.)
                                    val paperLeft = 40f
                                    val paperTop = 30f
                                    val paperWidth = 1000f
                                    val paperHeight = paperWidth * editorState.paperSize.aspectRatio
                                    val pageGap = 40f
                                    val totalPages = maxOf(1, editorState.pageCount)
                                    val pageStride = paperHeight + pageGap
                                    val totalDocHeight = paperTop + totalPages * paperHeight + (totalPages - 1) * pageGap

                                    val maxPanY = 40f
                                    val bottomButtonMargin = 160f
                                    val minPanY = (viewportHeightPx - (totalDocHeight + bottomButtonMargin) * zoom).coerceAtMost(maxPanY)

                                    val clampPan: (Offset, Float) -> Offset = { targetOffset, targetZoom ->
                                        val scaledHeight = (totalDocHeight + bottomButtonMargin) * targetZoom
                                        val scaledWidth = (paperWidth + paperLeft * 2) * targetZoom
                                        val minAllowedPanY = (viewportHeightPx - scaledHeight).coerceAtMost(maxPanY)
                                        val clampedY = targetOffset.y.coerceIn(minAllowedPanY, maxPanY)

                                        val clampedX = if (scaledWidth <= viewportWidthPx) {
                                            (viewportWidthPx - scaledWidth) / 2f
                                        } else {
                                            val minPanX = viewportWidthPx - scaledWidth - 60f
                                            val maxPanX = 60f
                                            targetOffset.x.coerceIn(minPanX, maxPanX)
                                        }
                                        Offset(clampedX, clampedY)
                                    }

                                    // Debounced visible page index sync while continuously scrolling
                                    LaunchedEffect(panOffset.y) {
                                        delay(150)
                                        val visiblePage = (((-panOffset.y + viewportHeightPx * 0.35f) / zoom - paperTop) / pageStride).toInt()
                                            .coerceIn(0, maxOf(0, editorState.pageCount - 1))
                                        if (visiblePage != editorState.pageIndex) {
                                            editorViewModel.goToPage(visiblePage)
                                        }
                                    }

                                    // 1. Continuous Multi-Page Background Template (Lined, Grid, Cornell, etc.)
                                    PageTemplateCanvas(
                                        template = editorState.pageTemplate,
                                        paperColor = paperColor,
                                        zoom = zoom,
                                        panOffset = panOffset,
                                        paperSize = editorState.paperSize,
                                        pageCount = editorState.pageCount,
                                        allPages = editorState.allNotebookPages
                                    )

                                // 2. Image Attachments Layer across all continuous pages (Rendered below ink)
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                             translationX = panOffset.x
                                             translationY = panOffset.y
                                             scaleX = zoom
                                             scaleY = zoom
                                             transformOrigin = TransformOrigin(0f, 0f)
                                        }
                                ) {
                                    val density = LocalDensity.current
                                    val visibleTop = (-panOffset.y / zoom) - 200f
                                    val visibleBottom = ((-panOffset.y + viewportHeightPx) / zoom) + 200f
                                    for (p in 0 until totalPages) {
                                        val pageTopOffset = p * pageStride
                                        val pageTop = paperTop + pageTopOffset
                                        val pageBottom = pageTop + paperHeight
                                        if (pageBottom < visibleTop || pageTop > visibleBottom) continue

                                        val pageImages = if (p == editorState.pageIndex) images else (editorState.allNotebookPages.find { it.pageIndex == p }?.imageAttachments ?: emptyList())

                                        pageImages.forEach { img ->
                                            val bitmap = ImageBitmapCache.get(img.filePath, context)
                                            if (bitmap != null) {
                                                val isPdfBg = img.id.startsWith("pdf_bg_")
                                                val isSelected = !isPdfBg && selectedImageId == img.id && p == editorState.pageIndex
                                                val widthDp = with(density) { img.width.toDp() }
                                                val heightDp = with(density) { img.height.toDp() }

                                                Surface(
                                                    modifier = Modifier
                                                        .offset { IntOffset(img.x.toInt(), (img.y + pageTopOffset).toInt()) }
                                                        .size(widthDp, heightDp)
                                                        .then(
                                                            if (isSelected) Modifier.border(2.5.dp, Color(0xFF0284C7), RoundedCornerShape(8.dp))
                                                            else Modifier
                                                        ),
                                                    shape = if (isPdfBg) androidx.compose.ui.graphics.RectangleShape else RoundedCornerShape(8.dp),
                                                    shadowElevation = if (isPdfBg) 0.dp else (if (isSelected) 6.dp else 2.dp),
                                                    color = Color.White
                                                ) {
                                                    Image(
                                                        bitmap = bitmap,
                                                        contentDescription = if (isPdfBg) "PDF Page Background" else "Note Image Attachment",
                                                        contentScale = if (isPdfBg) ContentScale.FillBounds else ContentScale.Fit,
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .graphicsLayer { rotationZ = img.rotation }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // 3. Foreground Low-Latency Ink Canvas with Continuous Multi-Page Drawing, Erasing, and Lasso
                                InkCanvas(
                                    strokes = strokes,
                                    selectedTool = editorState.selectedTool,
                                    strokeColor = editorState.strokeColor,
                                    strokeWidth = editorState.strokeWidth,
                                    highlighterAlpha = editorState.highlighterAlpha,
                                    isStylusOnlyMode = editorState.isStylusOnlyMode,
                                    isMagicAiEnabled = editorState.isMagicAiEnabled,
                                    zoom = zoom,
                                    panOffset = panOffset,
                                    selectedStrokeIds = editorState.selectedStrokeIds,
                                    pageCount = editorState.pageCount,
                                    activePageIndex = editorState.pageIndex,
                                    allPages = editorState.allNotebookPages,
                                    paperHeight = paperHeight,
                                    pageGap = pageGap,
                                    paperTop = paperTop,
                                    onTransform = { newZoom, newPan ->
                                        zoom = newZoom
                                        panOffset = clampPan(newPan, newZoom)
                                    },
                                    onSwitchPage = { newIdx ->
                                        editorViewModel.goToPage(newIdx)
                                    },
                                    onAddStroke = { editorViewModel.addStroke(it) },
                                    onEraseAt = { pt, radius -> editorViewModel.eraseStrokesAt(pt, radius) },
                                    onStartErase = { editorViewModel.startEraseSession() },
                                    onEndErase = { editorViewModel.endEraseSession() },
                                    onSelectLasso = { lassoPoints -> editorViewModel.selectStrokesByLasso(lassoPoints) },
                                    onAddTextAt = { docX, docY, pageIdx ->
                                        if (pageIdx != editorState.pageIndex) {
                                            editorViewModel.goToPage(pageIdx)
                                        }
                                        val prevAnn = textAnnotations.find { it.id == activeEditingTextId }
                                        if (prevAnn != null && prevAnn.text.isBlank()) {
                                            editorViewModel.removeTextAnnotation(prevAnn.id)
                                        }

                                        val tappedExisting = textAnnotations.find { ann ->
                                            docX >= (ann.x - 10f) && docX <= (ann.x + 350f) &&
                                            docY >= (ann.y - 10f) && docY <= (ann.y + 36f)
                                        }
                                        if (tappedExisting != null) {
                                            activeEditingTextId = tappedExisting.id
                                        } else {
                                            val newAnn = TextAnnotation(
                                                id = java.util.UUID.randomUUID().toString(),
                                                text = "",
                                                x = docX.coerceIn(40f, paperWidth - 40f),
                                                y = docY.coerceIn(30f, paperHeight - 30f),
                                                fontSizeSp = 18f,
                                                color = editorState.strokeColor,
                                                isBold = false,
                                                isItalic = false,
                                                width = (paperWidth - docX - 40f).coerceAtLeast(150f)
                                            )
                                            editorViewModel.addTextAnnotation(newAnn)
                                            activeEditingTextId = newAnn.id
                                        }
                                    }
                                )

                                // 4. Floating Image Control Badges & Corner Drag Handles (Rendered above InkCanvas for interactive edit & resize)
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            translationX = panOffset.x
                                            translationY = panOffset.y
                                            scaleX = zoom
                                            scaleY = zoom
                                            transformOrigin = TransformOrigin(0f, 0f)
                                        }
                                ) {
                                    val density = LocalDensity.current
                                    val activePageTopOffset = editorState.pageIndex * pageStride
                                    images.forEach { img ->
                                        if (img.id.startsWith("pdf_bg_")) return@forEach
                                        val isSelected = selectedImageId == img.id
                                        val widthDp = with(density) { img.width.toDp() }
                                        val heightDp = with(density) { img.height.toDp() }

                                        if (isSelected) {
                                            // Selected Image Bounding Box: drag anywhere inside to move the image!
                                            Box(
                                                modifier = Modifier
                                                    .offset { IntOffset(img.x.toInt(), (img.y + activePageTopOffset).toInt()) }
                                                    .size(widthDp, heightDp)
                                                    .border(2.5.dp, Color(0xFF0284C7), RoundedCornerShape(8.dp))
                                                    .pointerInput(img.id) {
                                                        detectDragGestures { change, dragAmount ->
                                                            change.consume()
                                                            editorViewModel.updateImagePosition(
                                                                img.id,
                                                                img.x + dragAmount.x / zoom,
                                                                img.y + dragAmount.y / zoom
                                                            )
                                                        }
                                                    }
                                            )

                                            // Floating Action Bar above image
                                            Surface(
                                                modifier = Modifier
                                                    .offset {
                                                        IntOffset(img.x.toInt(), (img.y + activePageTopOffset - 48f).toInt().coerceAtLeast(4))
                                                    },
                                                shape = RoundedCornerShape(16.dp),
                                                color = Color(0xFF0F172A),
                                                shadowElevation = 6.dp
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    // Move indicator
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(horizontal = 4.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.TouchApp,
                                                            contentDescription = "Move Image",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(3.dp))
                                                        Text("Drag to Move", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                                    }

                                                    VerticalDivider(modifier = Modifier.height(18.dp).padding(horizontal = 2.dp), color = Color(0x55FFFFFF))

                                                    // Rotate 90° Clockwise
                                                    IconButton(
                                                        onClick = { editorViewModel.updateImageRotation(img.id, img.rotation + 90f) },
                                                        modifier = Modifier.size(28.dp)
                                                    ) {
                                                        Icon(Icons.Default.RotateRight, contentDescription = "Rotate 90°", tint = Color.White, modifier = Modifier.size(18.dp))
                                                    }

                                                    // Duplicate Image
                                                    IconButton(
                                                        onClick = {
                                                            editorViewModel.duplicateImage(img.id)
                                                            Toast.makeText(context, "Image duplicated", Toast.LENGTH_SHORT).show()
                                                        },
                                                        modifier = Modifier.size(28.dp)
                                                    ) {
                                                        Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate Image", tint = Color.White, modifier = Modifier.size(16.dp))
                                                    }

                                                    // Delete Image
                                                    IconButton(
                                                        onClick = {
                                                            editorViewModel.removeImageAttachment(img.id)
                                                            selectedImageId = null
                                                        },
                                                        modifier = Modifier.size(28.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Delete,
                                                            contentDescription = "Delete Image",
                                                            tint = Color(0xFFEF4444),
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }

                                                    VerticalDivider(modifier = Modifier.height(18.dp).padding(horizontal = 2.dp), color = Color(0x55FFFFFF))

                                                    // Done / Deselect Button
                                                    Surface(
                                                        shape = RoundedCornerShape(12.dp),
                                                        color = Color(0xFF10B981),
                                                        modifier = Modifier.clickable { selectedImageId = null }
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Icon(Icons.Default.Check, contentDescription = "Done", tint = Color.White, modifier = Modifier.size(14.dp))
                                                            Spacer(modifier = Modifier.width(3.dp))
                                                            Text("Done", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                }
                                            }

                                            // Bottom-Right Corner Drag Handle for Resizing Width and Height
                                            Box(
                                                modifier = Modifier
                                                    .offset {
                                                        IntOffset((img.x + img.width - 18f).toInt(), (img.y + activePageTopOffset + img.height - 18f).toInt())
                                                    }
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF0284C7))
                                                    .border(2.dp, Color.White, CircleShape)
                                                    .pointerInput(img.id) {
                                                        detectDragGestures { change, dragAmount ->
                                                            change.consume()
                                                            editorViewModel.updateImageSize(
                                                                img.id,
                                                                img.width + dragAmount.x / zoom,
                                                                img.height + dragAmount.y / zoom
                                                            )
                                                        }
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.OpenInFull,
                                                    contentDescription = "Resize Bottom-Right",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }

                                            // Top-Right Corner Drag Handle
                                            Box(
                                                modifier = Modifier
                                                    .offset {
                                                        IntOffset((img.x + img.width - 16f).toInt(), (img.y + activePageTopOffset - 16f).toInt())
                                                    }
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF0284C7))
                                                    .border(2.dp, Color.White, CircleShape)
                                                    .pointerInput(img.id) {
                                                        detectDragGestures { change, dragAmount ->
                                                            change.consume()
                                                            editorViewModel.updateImageSize(
                                                                img.id,
                                                                img.width + dragAmount.x / zoom,
                                                                img.height - dragAmount.y / zoom
                                                            )
                                                            editorViewModel.updateImagePosition(
                                                                img.id,
                                                                img.x,
                                                                img.y + dragAmount.y / zoom
                                                            )
                                                        }
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.OpenInFull,
                                                    contentDescription = "Resize Top-Right",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }

                                            // Bottom-Left Corner Drag Handle
                                            Box(
                                                modifier = Modifier
                                                    .offset {
                                                        IntOffset((img.x - 16f).toInt(), (img.y + activePageTopOffset + img.height - 16f).toInt())
                                                    }
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF0284C7))
                                                    .border(2.dp, Color.White, CircleShape)
                                                    .pointerInput(img.id) {
                                                        detectDragGestures { change, dragAmount ->
                                                            change.consume()
                                                            editorViewModel.updateImageSize(
                                                                img.id,
                                                                img.width - dragAmount.x / zoom,
                                                                img.height + dragAmount.y / zoom
                                                            )
                                                            editorViewModel.updateImagePosition(
                                                                img.id,
                                                                img.x + dragAmount.x / zoom,
                                                                img.y
                                                            )
                                                        }
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.OpenInFull,
                                                    contentDescription = "Resize Bottom-Left",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        } else {
                                            // Unselected Image: Quick Tap Chip to Select & Edit Image
                                            Surface(
                                                modifier = Modifier
                                                    .offset { IntOffset(img.x.toInt(), (img.y + activePageTopOffset).toInt()) }
                                                    .clip(RoundedCornerShape(bottomEnd = 12.dp, topStart = 8.dp))
                                                    .clickable { selectedImageId = img.id },
                                                shape = RoundedCornerShape(bottomEnd = 12.dp, topStart = 8.dp),
                                                color = Color(0xDD0F172A),
                                                shadowElevation = 4.dp
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.CropFree,
                                                        contentDescription = "Select & Resize Image",
                                                        modifier = Modifier.size(14.dp),
                                                        tint = Color.White
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "Resize / Edit",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Selected Strokes Interactive Bounding Box on Canvas with Direct Move Drag Handle
                                    val selectedStrokes = strokes.filter { it.id in editorState.selectedStrokeIds }
                                    if (selectedStrokes.isNotEmpty()) {
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
                                        val activePageTopOffset = editorState.pageIndex * pageStride
                                        val boxLeft = (minX - 12f).coerceAtLeast(0f)
                                        val boxTop = (minY + activePageTopOffset - 12f).coerceAtLeast(0f)
                                        val boxWidth = (maxX - minX + 24f).coerceAtLeast(40f)
                                        val boxHeight = (maxY - minY + 24f).coerceAtLeast(40f)

                                        val widthDp = with(density) { boxWidth.toDp() }
                                        val heightDp = with(density) { boxHeight.toDp() }

                                        Box(
                                            modifier = Modifier
                                                .offset { IntOffset(boxLeft.toInt(), boxTop.toInt()) }
                                                .size(widthDp, heightDp)
                                                .border(2.dp, Color(0xFF2563EB), RoundedCornerShape(8.dp))
                                                .background(Color(0x183B82F6), RoundedCornerShape(8.dp))
                                                .pointerInput(editorState.selectedStrokeIds, zoom) {
                                                    detectDragGestures(
                                                        onDragStart = { editorViewModel.recordUndoForSelection() },
                                                        onDrag = { change, dragAmount ->
                                                            change.consume()
                                                            editorViewModel.moveSelectedStrokes(dragAmount.x / zoom, dragAmount.y / zoom)
                                                        }
                                                    )
                                                }
                                        )
                                    }

                                    // 4b. Interactive Text Annotations Layer (Multi-Page Display, Typing, Editing, and Soft Keyboard)
                                    val visibleTextTop = (-panOffset.y / zoom) - 200f
                                    val visibleTextBottom = ((-panOffset.y + viewportHeightPx) / zoom) + 200f
                                    for (p in 0 until totalPages) {
                                        val pageTopOffset = p * pageStride
                                        val pageTop = paperTop + pageTopOffset
                                        val pageBottom = pageTop + paperHeight
                                        if (pageBottom < visibleTextTop || pageTop > visibleTextBottom) continue

                                        val pageTextAnnotations = if (p == editorState.pageIndex) textAnnotations else (editorState.allNotebookPages.find { it.pageIndex == p }?.textAnnotations ?: emptyList())
                                        val isCurrentPage = (p == editorState.pageIndex)

                                        pageTextAnnotations.forEach { ann ->
                                            val isEditing = (activeEditingTextId == ann.id && isCurrentPage)

                                            if (isEditing) {
                                                // In-place direct text cursor on paper with zero word box/border/toolbar
                                                BasicTextField(
                                                    value = ann.text,
                                                    onValueChange = { newText ->
                                                        editorViewModel.updateTextAnnotation(ann.copy(text = newText))
                                                    },
                                                    modifier = Modifier
                                                        .offset { IntOffset(ann.x.toInt(), (ann.y + pageTopOffset).toInt()) }
                                                        .widthIn(min = 16.dp, max = with(density) { (paperWidth - ann.x - 20f).coerceAtLeast(100f).toDp() })
                                                        .focusRequester(textFocusRequester),
                                                    textStyle = TextStyle(
                                                        fontSize = 18.sp,
                                                        color = Color(ann.color or 0xFF000000L),
                                                        fontFamily = FontFamily.Default,
                                                        lineHeight = 24.sp
                                                    ),
                                                    cursorBrush = SolidColor(Color(ann.color or 0xFF000000L))
                                                )
                                            } else {
                                                // Pure typed text on page surface (clickable to reposition cursor & edit)
                                                Box(
                                                    modifier = Modifier
                                                        .offset { IntOffset(ann.x.toInt(), (ann.y + pageTopOffset).toInt()) }
                                                        .widthIn(min = 16.dp, max = with(density) { (paperWidth - ann.x - 20f).coerceAtLeast(100f).toDp() })
                                                        .clickable {
                                                            if (isCurrentPage) {
                                                                activeEditingTextId = ann.id
                                                            } else {
                                                                editorViewModel.goToPage(p)
                                                                activeEditingTextId = ann.id
                                                            }
                                                        }
                                                ) {
                                                    Text(
                                                        text = ann.text,
                                                        fontSize = 18.sp,
                                                        color = Color(ann.color or 0xFF000000L),
                                                        fontFamily = FontFamily.Default,
                                                        lineHeight = 24.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // 5. Floating Lasso Selection Action Bar (Move, Resize, Copy, Paste, Delete)
                                if (editorState.selectedStrokeIds.isNotEmpty()) {
                                    Surface(
                                        shape = RoundedCornerShape(24.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shadowElevation = 6.dp,
                                        modifier = Modifier
                                            .align(Alignment.TopCenter)
                                            .padding(top = 12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            // Move Drag Handle
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.pointerInput(editorState.selectedStrokeIds, zoom) {
                                                    detectDragGestures(
                                                        onDragStart = { editorViewModel.recordUndoForSelection() },
                                                        onDrag = { change, dragAmount ->
                                                            change.consume()
                                                            editorViewModel.moveSelectedStrokes(dragAmount.x / zoom, dragAmount.y / zoom)
                                                        }
                                                    )
                                                }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.TouchApp, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text("Move", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                                }
                                            }

                                            // Scale Down
                                            IconButton(
                                                onClick = { editorViewModel.scaleSelectedStrokes(0.85f) },
                                                modifier = Modifier.size(30.dp)
                                            ) {
                                                Icon(Icons.Default.Remove, contentDescription = "Shrink Selection", modifier = Modifier.size(16.dp))
                                            }

                                            // Scale Up
                                            IconButton(
                                                onClick = { editorViewModel.scaleSelectedStrokes(1.18f) },
                                                modifier = Modifier.size(30.dp)
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = "Enlarge Selection", modifier = Modifier.size(16.dp))
                                            }

                                            // Copy
                                            IconButton(
                                                onClick = {
                                                    editorViewModel.copySelectedStrokes()
                                                    Toast.makeText(context, "Selection copied!", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.size(30.dp)
                                            ) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Selection", modifier = Modifier.size(16.dp))
                                            }

                                            // Paste
                                            IconButton(
                                                onClick = {
                                                    editorViewModel.pasteStrokes()
                                                    Toast.makeText(context, "Selection pasted!", Toast.LENGTH_SHORT).show()
                                                },
                                                enabled = editorState.clipboardStrokes.isNotEmpty(),
                                                modifier = Modifier.size(30.dp)
                                            ) {
                                                Icon(Icons.Default.ContentPaste, contentDescription = "Paste Selection", modifier = Modifier.size(16.dp))
                                            }

                                            // Delete
                                            IconButton(
                                                onClick = { editorViewModel.deleteSelectedStrokes() },
                                                modifier = Modifier.size(30.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete Selection", tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                                            }

                                            // Deselect (Close)
                                            IconButton(
                                                onClick = { editorViewModel.clearSelection() },
                                                modifier = Modifier.size(30.dp)
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Deselect", modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }

                                // 6. Interactive Floating Zoom Controls Bar (Top-Right) with [-], percentage, [+], and 100% reset
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                                    shadowElevation = 4.dp,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 12.dp, end = 16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Zoom Out [-]
                                        IconButton(
                                            onClick = {
                                                val newZoom = (zoom * 0.82f).coerceIn(0.25f, 5.0f)
                                                zoom = newZoom
                                            },
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Icon(Icons.Default.Remove, contentDescription = "Zoom Out", modifier = Modifier.size(16.dp))
                                        }

                                        // Zoom Percentage (click resets to 100%)
                                        Text(
                                            text = "${(zoom * 100).toInt()}%",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .clickable {
                                                    zoom = 1.0f
                                                    panOffset = Offset.Zero
                                                }
                                                .padding(horizontal = 4.dp)
                                        )

                                        // Zoom In [+]
                                        IconButton(
                                            onClick = {
                                                val newZoom = (zoom * 1.20f).coerceIn(0.25f, 5.0f)
                                                zoom = newZoom
                                            },
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "Zoom In", modifier = Modifier.size(16.dp))
                                        }

                                        if (zoom != 1.0f || panOffset != Offset.Zero) {
                                            VerticalDivider(modifier = Modifier.height(16.dp).padding(horizontal = 2.dp))
                                            IconButton(
                                                onClick = {
                                                    zoom = 1.0f
                                                    panOffset = Offset.Zero
                                                },
                                                modifier = Modifier.size(26.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Refresh,
                                                    contentDescription = "Reset Zoom (100%)",
                                                    modifier = Modifier.size(14.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }

                                // 7. Unpinned Toolbar Show Trigger (when unpinned & collapsed)
                                if (!editorState.isToolbarPinned && !isToolbarExpandedWhenUnpinned) {
                                    Surface(
                                        shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                                        shadowElevation = 4.dp,
                                        modifier = Modifier
                                            .align(Alignment.TopCenter)
                                            .clickable { isToolbarExpandedWhenUnpinned = true }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Tools ▼", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }

                                // 8. Fullscreen Mode Exit Pill (Top-Left)
                                if (editorState.isFullscreen) {
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                                        shadowElevation = 4.dp,
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(top = 12.dp, start = 16.dp)
                                            .clickable { editorViewModel.toggleFullscreen() }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.FullscreenExit,
                                                contentDescription = "Exit Fullscreen",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Exit Fullscreen",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }

                                // Manual "+ Add Page" Button after the page finishes
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .graphicsLayer {
                                            translationX = panOffset.x + (paperLeft + paperWidth / 2f) * zoom
                                            translationY = panOffset.y + (totalDocHeight + 28f) * zoom
                                            scaleX = zoom.coerceIn(0.85f, 1.25f)
                                            scaleY = zoom.coerceIn(0.85f, 1.25f)
                                            transformOrigin = TransformOrigin(0f, 0f)
                                        }
                                ) {
                                    Surface(
                                        onClick = {
                                            val prevCount = editorState.pageCount
                                            editorViewModel.addNewPage()
                                            val newTotalPages = prevCount + 1
                                            val newDocHeight = paperTop + newTotalPages * paperHeight + (newTotalPages - 1) * pageGap
                                            val targetPanY = (viewportHeightPx - (newDocHeight + bottomButtonMargin) * zoom).coerceAtMost(maxPanY)
                                            coroutineScope.launch {
                                                androidx.compose.animation.core.Animatable(panOffset.y).animateTo(
                                                    targetValue = targetPanY,
                                                    animationSpec = androidx.compose.animation.core.tween(
                                                        durationMillis = 350,
                                                        easing = androidx.compose.animation.core.FastOutSlowInEasing
                                                    )
                                                ) {
                                                    panOffset = Offset(panOffset.x, this.value)
                                                }
                                            }
                                            Toast.makeText(context, "Page $newTotalPages added", Toast.LENGTH_SHORT).show()
                                        },
                                        shape = RoundedCornerShape(24.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shadowElevation = 5.dp,
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.5.dp,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                        ),
                                        modifier = Modifier.layout { measurable, constraints ->
                                            val placeable = measurable.measure(constraints)
                                            layout(placeable.width, placeable.height) {
                                                placeable.placeRelative(-placeable.width / 2, 0)
                                            }
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Add New Page",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text(
                                                text = "Add Page (${editorState.pageCount + 1})",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }

                                // 9. Floating Tablet Page Navigator (Bottom Center)
                                PageNavigator(
                                    currentPage = editorState.pageIndex,
                                    totalPages = editorState.pageCount,
                                    onPreviousPage = {
                                        val prevIdx = (editorState.pageIndex - 1).coerceAtLeast(0)
                                        editorViewModel.goToPage(prevIdx)
                                        val targetPanY = (maxPanY - prevIdx * pageStride * zoom).coerceIn(minPanY, maxPanY)
                                        coroutineScope.launch {
                                            androidx.compose.animation.core.Animatable(panOffset.y).animateTo(targetPanY, androidx.compose.animation.core.tween(350)) {
                                                panOffset = Offset(panOffset.x, this.value)
                                            }
                                        }
                                    },
                                    onNextPage = {
                                        val nextIdx = (editorState.pageIndex + 1).coerceAtMost(editorState.pageCount - 1)
                                        editorViewModel.goToPage(nextIdx)
                                        val targetPanY = (maxPanY - nextIdx * pageStride * zoom).coerceIn(minPanY, maxPanY)
                                        coroutineScope.launch {
                                            androidx.compose.animation.core.Animatable(panOffset.y).animateTo(targetPanY, androidx.compose.animation.core.tween(350)) {
                                                panOffset = Offset(panOffset.x, this.value)
                                            }
                                        }
                                    },
                                    onAddPage = { editorViewModel.addNewPage() },
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 24.dp)
                                )
                            }

                            // 2. Right-Hand Slide-Out Vertical Page Thumbnail Strip (matching uploaded photo!)
                            VerticalPageStrip(
                                isOpen = editorState.isPagePanelOpen,
                                currentPageIndex = editorState.pageIndex,
                                pageCount = editorState.pageCount,
                                pages = editorState.allNotebookPages,
                                paperSize = editorState.paperSize,
                                onSelectPage = { pageIdx ->
                                    editorViewModel.goToPage(pageIdx)
                                    val pH = 1000f * editorState.paperSize.aspectRatio
                                    val pStride = pH + 40f
                                    val targetPanY = 40f - pageIdx * pStride * zoom
                                    coroutineScope.launch {
                                        androidx.compose.animation.core.Animatable(panOffset.y).animateTo(
                                            targetValue = targetPanY,
                                            animationSpec = androidx.compose.animation.core.tween(350, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                                        ) {
                                            panOffset = Offset(panOffset.x, this.value)
                                        }
                                    }
                                },
                                onToggleOpen = { editorViewModel.togglePagePanel() },
                                onAddNewPage = { editorViewModel.addNewPage() },
                                onDuplicatePage = {
                                    editorViewModel.duplicateCurrentPage()
                                    Toast.makeText(context, "Page duplicated", Toast.LENGTH_SHORT).show()
                                },
                                onDeletePage = { idx ->
                                    editorViewModel.deletePage(idx)
                                    Toast.makeText(context, "Page deleted", Toast.LENGTH_SHORT).show()
                                },
                                onClearPage = {
                                    editorViewModel.clearCurrentPage()
                                    Toast.makeText(context, "Page ink cleared", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    } else {
                            // Empty State when all tabs are closed
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.EditNote,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "No open notebook tabs",
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = { editorViewModel.createNewNotebookAndOpenTab() }
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Open New Tab")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Export Note File Dialog (Exports & Shares as PDF Document)
    if (editorState.isExportDialogOpen) {
        AlertDialog(
            onDismissRequest = { editorViewModel.closeExportDialog() },
            title = {
                Text(
                    text = "Download / Share: ${editorState.activeNotebook?.title ?: "Notebook"}",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Generate a high-quality multi-page PDF with all vector handwriting, paper templates, colors, and embedded images:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            editorState.activeNotebook?.let { nb ->
                                val completePages = editorViewModel.getCompletePagesForExport()
                                PdfExporter.exportAndSharePdf(
                                    context = context,
                                    notebook = nb,
                                    pages = completePages
                                )
                            } ?: Toast.makeText(context, "No active notebook to export", Toast.LENGTH_SHORT).show()
                            editorViewModel.closeExportDialog()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Download & Share as PDF")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { editorViewModel.closeExportDialog() }) {
                    Text("Close")
                }
            },
            dismissButton = null
        )
    }

    // Rename Notebook Dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Notebook", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameTitleInput,
                    onValueChange = { renameTitleInput = it },
                    label = { Text("Notebook Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameTitleInput.isNotBlank()) {
                            editorViewModel.renameActiveNotebook(renameTitleInput)
                            Toast.makeText(context, "Notebook renamed", Toast.LENGTH_SHORT).show()
                        }
                        showRenameDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Modal Progress Dialog: Importing PDF Pages into Notebook
    if (editorState.isImportingPdf) {
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
                        text = editorState.importProgressMessage.ifBlank { "Adding PDF pages to notebook..." },
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }
    }
}

/**
 * Memory LRU cache for image attachment bitmaps to eliminate disk decode overhead during scrolling.
 */
object ImageBitmapCache {
    private val lruCache = object : android.util.LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(40) {}

    private fun resolveFile(filePath: String, context: android.content.Context? = null): java.io.File? {
        val direct = java.io.File(filePath)
        if (direct.exists()) return direct
        if (context != null) {
            val fileName = direct.name
            val inImages = java.io.File(context.filesDir, "note_images/$fileName")
            if (inImages.exists()) return inImages
            val inFiles = java.io.File(context.filesDir, fileName)
            if (inFiles.exists()) return inFiles
        }
        return null
    }

    fun get(filePath: String, context: android.content.Context? = null): androidx.compose.ui.graphics.ImageBitmap? {
        val cached = lruCache.get(filePath)
        if (cached != null) return cached

        return try {
            val file = resolveFile(filePath, context) ?: return null
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, boundsOptions)
            val maxDim = maxOf(boundsOptions.outWidth, boundsOptions.outHeight)
            val sampleSize = if (maxDim > 2048) maxDim / 1536 else 1
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bmp = BitmapFactory.decodeFile(file.absolutePath, decodeOptions)?.asImageBitmap()
            if (bmp != null) {
                lruCache.put(filePath, bmp)
            }
            bmp
        } catch (_: Exception) {
            null
        }
    }
}
