package com.notewise.tabnotes.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke as CanvasStroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import com.notewise.tabnotes.data.model.NotePage
import com.notewise.tabnotes.data.model.Point
import com.notewise.tabnotes.data.model.Stroke
import com.notewise.tabnotes.data.model.ToolType
import java.util.UUID
import kotlin.math.hypot

@Composable
fun InkCanvas(
    strokes: List<Stroke>,
    selectedTool: ToolType,
    strokeColor: Long,
    strokeWidth: Float,
    highlighterAlpha: Float,
    isStylusOnlyMode: Boolean,
    isMagicAiEnabled: Boolean,
    zoom: Float = 1.0f,
    panOffset: Offset = Offset.Zero,
    selectedStrokeIds: Set<String> = emptySet(),
    pageCount: Int = 1,
    activePageIndex: Int = 0,
    allPages: List<NotePage> = emptyList(),
    paperHeight: Float = 1414.2f,
    pageGap: Float = 40f,
    paperTop: Float = 30f,
    onTransform: (Float, Offset) -> Unit = { _, _ -> },
    onSwitchPage: (Int) -> Unit = {},
    onAddStroke: (Stroke) -> Unit,
    onEraseAt: (Point, Float) -> Unit,
    onStartErase: () -> Unit = {},
    onEndErase: () -> Unit = {},
    onSelectLasso: (List<Point>) -> Unit = {},
    onAddTextAt: ((docX: Float, docY: Float, pageIndex: Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Current in-progress stroke points
    var livePoints by remember { mutableStateOf<List<Point>>(emptyList()) }
    var eraserPosition by remember { mutableStateOf<Offset?>(null) }
    val eraserRadius = remember(strokeWidth) { (strokeWidth * 3.5f).coerceIn(16f, 48f) }

    // Magic Pen disappearing strokes state & ticker
    var fadingStrokes by remember { mutableStateOf<List<FadingStroke>>(emptyList()) }

    LaunchedEffect(fadingStrokes.isNotEmpty()) {
        while (fadingStrokes.isNotEmpty()) {
            kotlinx.coroutines.delay(25)
            val now = System.currentTimeMillis()
            fadingStrokes = fadingStrokes.filter { now - it.creationTime < it.durationMs }
        }
    }

    // Use rememberUpdatedState so continuous values don't restart the gesture coroutine!
    val currentZoom by rememberUpdatedState(zoom)
    val currentPanOffset by rememberUpdatedState(panOffset)
    val currentOnTransform by rememberUpdatedState(onTransform)
    val currentOnSwitchPage by rememberUpdatedState(onSwitchPage)
    val currentOnEraseAt by rememberUpdatedState(onEraseAt)
    val currentOnStartErase by rememberUpdatedState(onStartErase)
    val currentOnEndErase by rememberUpdatedState(onEndErase)
    val currentOnAddStroke by rememberUpdatedState(onAddStroke)
    val currentOnSelectLasso by rememberUpdatedState(onSelectLasso)
    val currentOnAddTextAt by rememberUpdatedState(onAddTextAt)
    val currentStrokeColor by rememberUpdatedState(strokeColor)
    val currentStrokeWidth by rememberUpdatedState(strokeWidth)
    val currentSelectedTool by rememberUpdatedState(selectedTool)
    val currentIsStylusOnlyMode by rememberUpdatedState(isStylusOnlyMode)
    val currentActivePageIndex by rememberUpdatedState(activePageIndex)
    val currentPageCount by rememberUpdatedState(pageCount)
    val currentPaperHeight by rememberUpdatedState(paperHeight)
    val currentPageGap by rememberUpdatedState(pageGap)
    val currentPaperTop by rememberUpdatedState(paperTop)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    var isTransforming = false
                    var prevDistance = 0f
                    var prevCentroid = Offset.Zero

                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                    val isStylus = firstDown.type == PointerType.Stylus
                    // If touch starts in the right edge thumb zone (last 48px), treat as one-handed edge scroll
                    val isRightEdgeZone = firstDown.position.x >= (size.width - 48f)
                    // If stylus mode is active (default) or touch starts in edge zone, finger scrolls/moves page
                    val isFingerScroll = if (currentSelectedTool == ToolType.TEXT) {
                        isRightEdgeZone && !isStylus
                    } else {
                        (currentIsStylusOnlyMode && !isStylus) || (isRightEdgeZone && !isStylus)
                    }
                    val canDraw = !isFingerScroll

                    val docX = (firstDown.position.x - currentPanOffset.x) / currentZoom
                    val docY = (firstDown.position.y - currentPanOffset.y) / currentZoom
                    val initialPoint = Point(docX, docY, firstDown.pressure.coerceIn(0.2f, 2.0f))

                    val pageStride = currentPaperHeight + currentPageGap
                    val touchPageIndex = ((docY - currentPaperTop) / pageStride).toInt().coerceIn(0, maxOf(0, currentPageCount - 1))
                    val touchPageOffsetY = touchPageIndex * pageStride
                    var totalDragPx = 0f

                    try {
                        if (canDraw) {
                            if (touchPageIndex != currentActivePageIndex) {
                                currentOnSwitchPage(touchPageIndex)
                            }

                            when (currentSelectedTool) {
                                ToolType.ERASER_STROKE, ToolType.ERASER_POINT -> {
                                    eraserPosition = firstDown.position
                                    currentOnStartErase()
                                    val localPoint = Point(docX, docY - touchPageOffsetY, initialPoint.pressure)
                                    currentOnEraseAt(localPoint, eraserRadius / currentZoom)
                                }
                                ToolType.TEXT -> {
                                    eraserPosition = null
                                    livePoints = emptyList()
                                }
                                else -> {
                                    eraserPosition = null
                                    livePoints = listOf(initialPoint)
                                }
                            }
                        }

                        do {
                            val event = awaitPointerEvent()
                            val activePointers = event.changes.filter { it.pressed }

                            if (activePointers.size >= 2) {
                                // Multi-touch hand gesture: Pinch to Zoom and Pan
                                isTransforming = true
                                livePoints = emptyList()
                                eraserPosition = null

                                val p1 = activePointers[0].position
                                val p2 = activePointers[1].position
                                val currentDist = hypot(p1.x - p2.x, p1.y - p2.y)
                                val currentCentroid = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)

                                if (prevDistance > 0f) {
                                    val zoomRatio = currentDist / prevDistance
                                    val newZoom = (currentZoom * zoomRatio).coerceIn(0.25f, 5.0f)
                                    val scaleFactor = newZoom / currentZoom

                                    // Center zoom around currentCentroid
                                    val newPan = currentCentroid - (currentCentroid - currentPanOffset) * scaleFactor + (currentCentroid - prevCentroid)
                                    currentOnTransform(newZoom, newPan)
                                }

                                prevDistance = currentDist
                                prevCentroid = currentCentroid
                                event.changes.forEach { it.consume() }
                            } else if (activePointers.size == 1 && !isTransforming) {
                                // Single pointer: Drawing, Erasing, or One-Finger Page Scrolling
                                prevDistance = 0f
                                val change = activePointers[0]
                                val delta = change.positionChange()
                                change.consume()

                                val currentDocX = (change.position.x - currentPanOffset.x) / currentZoom
                                val currentDocY = (change.position.y - currentPanOffset.y) / currentZoom
                                val point = Point(currentDocX, currentDocY, change.pressure.coerceIn(0.2f, 2.0f))
                                totalDragPx += hypot(delta.x, delta.y)

                                if (canDraw) {
                                    when (currentSelectedTool) {
                                        ToolType.ERASER_STROKE, ToolType.ERASER_POINT -> {
                                            eraserPosition = change.position
                                            val currentErasePageIndex = ((currentDocY - currentPaperTop) / pageStride).toInt().coerceIn(0, maxOf(0, currentPageCount - 1))
                                            val currentErasePageOffsetY = currentErasePageIndex * pageStride
                                            if (currentErasePageIndex != currentActivePageIndex) {
                                                currentOnSwitchPage(currentErasePageIndex)
                                            }
                                            val localErasePoint = Point(currentDocX, currentDocY - currentErasePageOffsetY, point.pressure)
                                            currentOnEraseAt(localErasePoint, eraserRadius / currentZoom)
                                        }
                                        ToolType.TEXT -> {
                                            eraserPosition = null
                                            livePoints = emptyList()
                                            if (totalDragPx > 20f) {
                                                val deltaX = if (kotlin.math.abs(delta.y) > kotlin.math.abs(delta.x) * 1.4f) 0f else delta.x
                                                currentOnTransform(currentZoom, currentPanOffset + Offset(deltaX, delta.y))
                                            }
                                        }
                                        else -> {
                                            eraserPosition = null
                                            livePoints = livePoints + point
                                        }
                                    }
                                } else if (isFingerScroll) {
                                    // One finger smoothly moves and scrolls page up/down with continuous vertical tracking
                                    val deltaX = if (kotlin.math.abs(delta.y) > kotlin.math.abs(delta.x) * 1.4f) 0f else delta.x
                                    currentOnTransform(currentZoom, currentPanOffset + Offset(deltaX, delta.y))
                                }
                            } else if (activePointers.size == 1 && isTransforming) {
                                // Finger pan transition after multi-touch pinch
                                prevDistance = 0f
                                val change = activePointers[0]
                                val delta = change.positionChange()
                                currentOnTransform(currentZoom, currentPanOffset + delta)
                                change.consume()
                            }
                        } while (event.changes.any { it.pressed })

                        if (currentSelectedTool == ToolType.ERASER_STROKE || currentSelectedTool == ToolType.ERASER_POINT) {
                            currentOnEndErase()
                        }

                        eraserPosition = null
                        if (!isTransforming) {
                            if (currentSelectedTool == ToolType.TEXT && canDraw) {
                                if (totalDragPx <= 20f) {
                                    val tapDocX = initialPoint.x
                                    val tapDocY = initialPoint.y - touchPageOffsetY
                                    currentOnAddTextAt?.invoke(tapDocX, tapDocY, touchPageIndex)
                                }
                            } else {
                                val startPt = livePoints.firstOrNull() ?: initialPoint
                            val strokePageIndex = ((startPt.y - currentPaperTop) / pageStride).toInt().coerceIn(0, maxOf(0, currentPageCount - 1))
                            val strokePageOffsetY = strokePageIndex * pageStride

                            if (strokePageIndex != currentActivePageIndex) {
                                currentOnSwitchPage(strokePageIndex)
                            }

                            val localPoints = livePoints.map { Point(it.x, it.y - strokePageOffsetY, it.pressure) }

                            if (currentSelectedTool == ToolType.LASSO) {
                                if (localPoints.size >= 3) {
                                    currentOnSelectLasso(localPoints)
                                }
                            } else if (currentSelectedTool == ToolType.MAGIC_PEN) {
                                if (livePoints.size >= 2) {
                                    val magicStroke = Stroke(
                                        id = UUID.randomUUID().toString(),
                                        points = livePoints,
                                        color = currentStrokeColor,
                                        width = currentStrokeWidth * 1.5f,
                                        alpha = 1.0f,
                                        toolType = ToolType.MAGIC_PEN,
                                        isClosedShape = false
                                    )
                                    fadingStrokes = fadingStrokes + FadingStroke(magicStroke, System.currentTimeMillis(), 2500L)
                                }
                            } else if (localPoints.size >= 2) {
                                val start = localPoints.first()
                                val end = localPoints.last()

                                val isShapeTool = currentSelectedTool in listOf(
                                    ToolType.SHAPE_LINE,
                                    ToolType.SHAPE_RECTANGLE,
                                    ToolType.SHAPE_CIRCLE,
                                    ToolType.SHAPE_TRIANGLE,
                                    ToolType.SHAPE_ARROW
                                )

                                if (isShapeTool) {
                                    val shapeStroke = Stroke.createGeometricShape(
                                        toolType = currentSelectedTool,
                                        start = start,
                                        end = end,
                                        color = currentStrokeColor,
                                        width = currentStrokeWidth,
                                        alpha = 1.0f
                                    )
                                    currentOnAddStroke(shapeStroke)
                                } else {
                                    val isHighlighter = currentSelectedTool == ToolType.HIGHLIGHTER
                                    val finalStroke = Stroke(
                                        id = UUID.randomUUID().toString(),
                                        points = localPoints,
                                        color = currentStrokeColor,
                                        width = if (isHighlighter) currentStrokeWidth * 2.8f else currentStrokeWidth,
                                        alpha = if (isHighlighter) highlighterAlpha else 1.0f,
                                        toolType = currentSelectedTool,
                                        isClosedShape = false
                                    )
                                    currentOnAddStroke(finalStroke)
                                }
                            }
                        }
                        }
                    } finally {
                        eraserPosition = null
                        livePoints = emptyList()
                    }
                }
            }
    ) {
        withTransform({
            translate(panOffset.x, panOffset.y)
            scale(zoom, zoom, Offset.Zero)
        }) {
            val totalPages = maxOf(1, pageCount)
            val pageStride = paperHeight + pageGap

            // Viewport bounds in document coordinates with 200px safety margin for silky-smooth 60/120fps scrolling
            val visibleTop = (-panOffset.y / zoom) - 200f
            val visibleBottom = ((-panOffset.y + size.height) / zoom) + 200f

            // 1. Draw all saved completed strokes across all continuous vertical pages with Viewport Culling
            for (p in 0 until totalPages) {
                val pageOffsetY = p * pageStride
                val pageTop = paperTop + pageOffsetY
                val pageBottom = pageTop + paperHeight

                // Skip pages completely outside the visible viewport
                if (pageBottom < visibleTop || pageTop > visibleBottom) continue

                val pageStrokes = if (p == activePageIndex) strokes else (allPages.find { it.pageIndex == p }?.strokes ?: emptyList())
                withTransform({
                    translate(0f, pageOffsetY)
                }) {
                    pageStrokes.forEach { stroke ->
                        val b = stroke.bounds
                        // Stroke-level culling
                        if ((b.bottom + pageOffsetY) >= visibleTop && (b.top + pageOffsetY) <= visibleBottom) {
                            drawSmoothStroke(stroke)
                        }
                    }

                    // 1b. Draw Selection Bounding Box & Highlight for Selected Strokes on active page
                    if (p == activePageIndex) {
                        val selectedStrokes = strokes.filter { it.id in selectedStrokeIds }
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
                            val pad = 12f
                            drawRect(
                                color = Color(0x203B82F6),
                                topLeft = Offset(minX - pad, minY - pad),
                                size = androidx.compose.ui.geometry.Size(maxX - minX + pad * 2, maxY - minY + pad * 2)
                            )
                            drawRect(
                                color = Color(0xFF2563EB),
                                topLeft = Offset(minX - pad, minY - pad),
                                size = androidx.compose.ui.geometry.Size(maxX - minX + pad * 2, maxY - minY + pad * 2),
                                style = CanvasStroke(
                                    width = 1.5f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
                                )
                            )
                        }
                    }
                }
            }

            // 1a. Draw temporary Magic Pen disappearing strokes with glowing neon effect
            val now = System.currentTimeMillis()
            fadingStrokes.forEach { fading ->
                val elapsed = now - fading.creationTime
                val remainingRatio = (1f - (elapsed.toFloat() / fading.durationMs)).coerceIn(0f, 1f)
                if (remainingRatio > 0.01f) {
                    val glow = fading.stroke.copy(
                        width = fading.stroke.width * 2.2f,
                        alpha = remainingRatio * 0.45f
                    )
                    drawSmoothStroke(glow)
                    val core = fading.stroke.copy(
                        alpha = remainingRatio
                    )
                    drawSmoothStroke(core)
                }
            }

            // 2. Draw live in-progress stroke, live geometric shape preview, or lasso preview
            if (livePoints.size >= 2) {
                if (selectedTool == ToolType.LASSO) {
                    val lassoPath = Path()
                    lassoPath.moveTo(livePoints[0].x, livePoints[0].y)
                    for (i in 1 until livePoints.size) {
                        lassoPath.lineTo(livePoints[i].x, livePoints[i].y)
                    }
                    drawPath(
                        path = lassoPath,
                        color = Color(0x283B82F6)
                    )
                    drawPath(
                        path = lassoPath,
                        color = Color(0xFF2563EB),
                        style = CanvasStroke(
                            width = 2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
                        )
                    )
                } else {
                    val start = livePoints.first()
                    val end = livePoints.last()

                    val isShapeTool = when (selectedTool) {
                        ToolType.SHAPE_LINE,
                        ToolType.SHAPE_RECTANGLE,
                        ToolType.SHAPE_CIRCLE,
                        ToolType.SHAPE_TRIANGLE,
                        ToolType.SHAPE_ARROW -> true
                        else -> false
                    }

                    if (isShapeTool) {
                        // Live interactive shape preview
                        val previewStroke = Stroke.createGeometricShape(
                            toolType = selectedTool,
                            start = start,
                            end = end,
                            color = strokeColor,
                            width = strokeWidth,
                            alpha = 0.85f
                        )
                        drawSmoothStroke(previewStroke, isDashedPreview = true)
                    } else if (selectedTool == ToolType.MAGIC_PEN) {
                        val liveMagicStroke = Stroke(
                            points = livePoints,
                            color = strokeColor,
                            width = strokeWidth * 1.5f,
                            alpha = 1.0f,
                            toolType = ToolType.MAGIC_PEN
                        )
                        drawSmoothStroke(liveMagicStroke.copy(width = strokeWidth * 2.2f, alpha = 0.45f))
                        drawSmoothStroke(liveMagicStroke)
                    } else {
                        val isHighlighter = selectedTool == ToolType.HIGHLIGHTER
                        val liveStroke = Stroke(
                            points = livePoints,
                            color = strokeColor,
                            width = if (isHighlighter) strokeWidth * 2.8f else strokeWidth,
                            alpha = if (isHighlighter) highlighterAlpha else 1.0f,
                            toolType = selectedTool
                        )
                        drawSmoothStroke(liveStroke)
                    }
                }
            }
        }

        // 3. Draw live Eraser cursor ring
        eraserPosition?.let { pos ->
            drawCircle(
                color = Color(0xFFDC2626).copy(alpha = 0.45f),
                radius = eraserRadius,
                center = pos,
                style = CanvasStroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
            )
            drawCircle(
                color = Color(0x30FFFFFF),
                radius = eraserRadius,
                center = pos
            )
        }
    }
}

/**
 * High-performance LRU cache for pre-computed vector Paths.
 * Eliminates repeated path allocations and Bézier curve recalculations during 60/120fps canvas rendering.
 */
object StrokePathCache {
    private val lruCache = object : android.util.LruCache<String, Path>(1500) {}

    fun getPath(stroke: Stroke): Path {
        val firstPt = stroke.points.firstOrNull()
        val key = "${stroke.id}_${stroke.points.size}_${firstPt?.x}_${firstPt?.y}"
        val cached = lruCache.get(key)
        if (cached != null) return cached

        val path = Path()
        val points = stroke.points
        if (points.isNotEmpty()) {
            if (stroke.toolType == ToolType.SHAPE_RECTANGLE && points.size >= 4) {
                val left = minOf(points[0].x, points[1].x, points[2].x, points[3].x)
                val right = maxOf(points[0].x, points[1].x, points[2].x, points[3].x)
                val top = minOf(points[0].y, points[1].y, points[2].y, points[3].y)
                val bottom = maxOf(points[0].y, points[1].y, points[2].y, points[3].y)
                path.addRect(androidx.compose.ui.geometry.Rect(left, top, right, bottom))
                lruCache.put(key, path)
                return path
            }

            path.moveTo(points[0].x, points[0].y)
            val isStraightSegment = stroke.toolType in listOf(
                ToolType.SHAPE_LINE,
                ToolType.SHAPE_RECTANGLE,
                ToolType.SHAPE_TRIANGLE,
                ToolType.SHAPE_ARROW
            )
            if (isStraightSegment || points.size == 2) {
                for (i in 1 until points.size) {
                    path.lineTo(points[i].x, points[i].y)
                }
            } else {
                for (i in 1 until points.size - 1) {
                    val current = points[i]
                    val next = points[i + 1]
                    val midX = (current.x + next.x) / 2f
                    val midY = (current.y + next.y) / 2f
                    path.quadraticTo(current.x, current.y, midX, midY)
                }
                path.lineTo(points.last().x, points.last().y)
            }
            if (stroke.isClosedShape) {
                path.close()
            }
        }
        lruCache.put(key, path)
        return path
    }

    fun clear() {
        lruCache.evictAll()
    }
}

/**
 * Renders a stroke using cached quadratic Bézier curves with zero object allocations on redraw frames.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSmoothStroke(
    stroke: Stroke,
    isDashedPreview: Boolean = false
) {
    val points = stroke.points
    if (points.isEmpty()) return

    if (points.size == 1) {
        val p = points.first()
        drawCircle(
            color = Color(stroke.color).copy(alpha = stroke.alpha),
            radius = stroke.width / 2f,
            center = Offset(p.x, p.y)
        )
        return
    }

    val isHighlighter = stroke.toolType == ToolType.HIGHLIGHTER
    val isShape = stroke.toolType in listOf(
        ToolType.SHAPE_LINE,
        ToolType.SHAPE_RECTANGLE,
        ToolType.SHAPE_TRIANGLE,
        ToolType.SHAPE_ARROW,
        ToolType.SHAPE_CIRCLE
    )
    val path = StrokePathCache.getPath(stroke)
    val pathEffect = if (isDashedPreview) PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f) else null

    val strokeCap = when {
        isShape -> StrokeCap.Square
        isHighlighter -> StrokeCap.Square
        else -> StrokeCap.Round
    }
    val strokeJoin = when {
        stroke.toolType == ToolType.SHAPE_RECTANGLE || stroke.toolType == ToolType.SHAPE_TRIANGLE -> StrokeJoin.Miter
        isHighlighter -> StrokeJoin.Bevel
        else -> StrokeJoin.Round
    }

    drawPath(
        path = path,
        color = Color(stroke.color).copy(alpha = stroke.alpha),
        blendMode = BlendMode.SrcOver,
        style = CanvasStroke(
            width = stroke.width,
            cap = strokeCap,
            join = strokeJoin,
            pathEffect = pathEffect
        )
    )
}

data class FadingStroke(
    val stroke: Stroke,
    val creationTime: Long = System.currentTimeMillis(),
    val durationMs: Long = 2500L
)
