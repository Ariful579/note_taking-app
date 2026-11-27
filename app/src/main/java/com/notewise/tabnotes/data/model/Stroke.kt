package com.notewise.tabnotes.data.model

import java.util.UUID
import kotlin.math.*

data class Point(
    val x: Float,
    val y: Float,
    val pressure: Float = 1.0f,
    val timestamp: Long = System.currentTimeMillis()
)

data class Stroke(
    val id: String = UUID.randomUUID().toString(),
    val points: List<Point> = emptyList(),
    val color: Long = 0xFF1E293B, // Default slate dark
    val width: Float = 4.0f,
    val alpha: Float = 1.0f,
    val toolType: ToolType = ToolType.PEN,
    val isClosedShape: Boolean = false,
    val isAiSmoothed: Boolean = false
) {
    val bounds: RectF by lazy {
        if (points.isEmpty()) RectF(0f, 0f, 0f, 0f)
        else {
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var maxY = Float.MIN_VALUE
            for (p in points) {
                if (p.x < minX) minX = p.x
                if (p.x > maxX) maxX = p.x
                if (p.y < minY) minY = p.y
                if (p.y > maxY) maxY = p.y
            }
            RectF(minX, minY, maxX, maxY)
        }
    }

    val boundingBox: RectF get() = bounds

    fun intersects(point: Point, tolerance: Float = 16f): Boolean {
        if (points.isEmpty()) return false
        if (points.size == 1) {
            val p = points[0]
            val d2 = (p.x - point.x) * (p.x - point.x) + (p.y - point.y) * (p.y - point.y)
            return d2 <= tolerance * tolerance
        }
        val tolSq = tolerance * tolerance
        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            val distSq = distanceSqToSegment(point.x, point.y, p1.x, p1.y, p2.x, p2.y)
            if (distSq <= tolSq) return true
        }
        return false
    }

    private fun distanceSqToSegment(x: Float, y: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val l2 = (x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1)
        if (l2 == 0f) return (x - x1) * (x - x1) + (y - y1) * (y - y1)
        var t = ((x - x1) * (x2 - x1) + (y - y1) * (y2 - y1)) / l2
        t = t.coerceIn(0f, 1f)
        val projX = x1 + t * (x2 - x1)
        val projY = y1 + t * (y2 - y1)
        return (x - projX) * (x - projX) + (y - projY) * (y - projY)
    }

    /**
     * Precision Point-Eraser: Removes points falling inside the eraser radius and splits
     * the remaining points into contiguous sub-strokes rather than deleting the whole stroke.
     */
    fun erasePointsWithin(center: Point, radius: Float): List<Stroke> {
        if (points.isEmpty()) return emptyList()
        if (!intersects(center, radius)) return listOf(this)

        val radiusSq = radius * radius
        val step = (radius * 0.35f).coerceIn(2f, 8f)

        // Subdivide long segments so high-speed strokes don't jump across the eraser boundary
        val densePoints = mutableListOf<Point>()
        densePoints.add(points[0])
        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            val segDist = hypot(p2.x - p1.x, p2.y - p1.y)
            if (segDist > step) {
                val numSteps = (segDist / step).toInt()
                for (s in 1..numSteps) {
                    val t = s.toFloat() / (numSteps + 1)
                    val interX = p1.x + t * (p2.x - p1.x)
                    val interY = p1.y + t * (p2.y - p1.y)
                    val interPressure = p1.pressure + t * (p2.pressure - p1.pressure)
                    densePoints.add(Point(interX, interY, interPressure))
                }
            }
            densePoints.add(p2)
        }

        // Partition points into surviving runs outside the eraser circle
        val resultingSegments = mutableListOf<MutableList<Point>>()
        var currentSegment = mutableListOf<Point>()

        for (p in densePoints) {
            val dx = p.x - center.x
            val dy = p.y - center.y
            val distSq = dx * dx + dy * dy

            if (distSq > radiusSq) {
                currentSegment.add(p)
            } else {
                if (currentSegment.isNotEmpty()) {
                    resultingSegments.add(currentSegment)
                    currentSegment = mutableListOf()
                }
            }
        }
        if (currentSegment.isNotEmpty()) {
            resultingSegments.add(currentSegment)
        }

        return resultingSegments.mapNotNull { segPoints ->
            // Discard 1-point leftover noise unless the original stroke was a single tap dot
            if (segPoints.isEmpty() || (segPoints.size < 2 && this.points.size >= 2)) null
            else {
                Stroke(
                    id = UUID.randomUUID().toString(),
                    points = segPoints,
                    color = this.color,
                    width = this.width,
                    alpha = this.alpha,
                    toolType = this.toolType,
                    isClosedShape = false,
                    isAiSmoothed = this.isAiSmoothed
                )
            }
        }
    }

    /**
     * Translates all points by offset (dx, dy), preserving the stroke identity.
     */
    fun translate(dx: Float, dy: Float): Stroke {
        val newPoints = points.map { Point(it.x + dx, it.y + dy, it.pressure, it.timestamp) }
        return this.copy(points = newPoints)
    }

    /**
     * Scales all points around center (cx, cy) by factor, preserving the stroke identity.
     */
    fun scaleAround(cx: Float, cy: Float, factor: Float): Stroke {
        val newPoints = points.map {
            Point(cx + (it.x - cx) * factor, cy + (it.y - cy) * factor, it.pressure, it.timestamp)
        }
        return this.copy(
            points = newPoints,
            width = (this.width * factor).coerceIn(1f, 64f)
        )
    }

    companion object {
        /**
         * AI smoothing filter: Applies weighted moving-average filter to remove hand tremors and jitter.
         */
        fun smoothPoints(rawPoints: List<Point>): List<Point> {
            if (rawPoints.size < 4) return rawPoints
            val result = mutableListOf<Point>()
            result.add(rawPoints.first())

            for (i in 1 until rawPoints.size - 1) {
                val prev = rawPoints[i - 1]
                val curr = rawPoints[i]
                val next = rawPoints[i + 1]

                val smoothedX = (prev.x + curr.x * 2f + next.x) / 4f
                val smoothedY = (prev.y + curr.y * 2f + next.y) / 4f
                val smoothedPressure = (prev.pressure + curr.pressure * 2f + next.pressure) / 4f

                result.add(Point(smoothedX, smoothedY, smoothedPressure, curr.timestamp))
            }

            result.add(rawPoints.last())
            return result
        }

        /**
         * Generates pristine points for geometric shapes from start to end coordinates.
         */
        fun createGeometricShape(
            toolType: ToolType,
            start: Point,
            end: Point,
            color: Long,
            width: Float,
            alpha: Float
        ): Stroke {
            val points = mutableListOf<Point>()
            val isClosed: Boolean

            when (toolType) {
                ToolType.SHAPE_LINE -> {
                    isClosed = false
                    points.add(start)
                    points.add(end)
                }
                ToolType.SHAPE_RECTANGLE -> {
                    isClosed = true
                    val left = min(start.x, end.x)
                    val right = max(start.x, end.x)
                    val top = min(start.y, end.y)
                    val bottom = max(start.y, end.y)
                    points.add(Point(left, top))
                    points.add(Point(right, top))
                    points.add(Point(right, bottom))
                    points.add(Point(left, bottom))
                    points.add(Point(left, top))
                }
                ToolType.SHAPE_CIRCLE -> {
                    isClosed = true
                    val centerX = (start.x + end.x) / 2f
                    val centerY = (start.y + end.y) / 2f
                    val rx = abs(end.x - start.x) / 2f
                    val ry = abs(end.y - start.y) / 2f
                    val segments = 48
                    for (i in 0 until segments) {
                        val angle = (2.0 * PI * i / segments).toFloat()
                        val px = centerX + rx * cos(angle)
                        val py = centerY + ry * sin(angle)
                        points.add(Point(px, py))
                    }
                }
                ToolType.SHAPE_TRIANGLE -> {
                    isClosed = true
                    val left = min(start.x, end.x)
                    val right = max(start.x, end.x)
                    val top = min(start.y, end.y)
                    val bottom = max(start.y, end.y)
                    val topX = (left + right) / 2f
                    points.add(Point(topX, top))
                    points.add(Point(right, bottom))
                    points.add(Point(left, bottom))
                }
                ToolType.SHAPE_ARROW -> {
                    isClosed = false
                    points.add(start)
                    points.add(end)

                    // Calculate arrow head wings
                    val dx = end.x - start.x
                    val dy = end.y - start.y
                    val angle = atan2(dy, dx)
                    val arrowLength = (width * 4f).coerceIn(20f, 45f)
                    val wingAngle = (PI / 6).toFloat() // 30 degrees

                    val wing1X = end.x - arrowLength * cos(angle - wingAngle)
                    val wing1Y = end.y - arrowLength * sin(angle - wingAngle)
                    val wing2X = end.x - arrowLength * cos(angle + wingAngle)
                    val wing2Y = end.y - arrowLength * sin(angle + wingAngle)

                    points.add(Point(wing1X, wing1Y))
                    points.add(end)
                    points.add(Point(wing2X, wing2Y))
                }
                else -> {
                    isClosed = false
                    points.add(start)
                    points.add(end)
                }
            }

            return Stroke(
                id = UUID.randomUUID().toString(),
                points = points,
                color = color,
                width = width,
                alpha = alpha,
                toolType = toolType,
                isClosedShape = isClosed,
                isAiSmoothed = true
            )
        }

        /**
         * AI Shape Recognizer: Checks if a raw freehand stroke is close to a line, rectangle, or circle.
         */
        fun tryRecognizeShape(rawPoints: List<Point>, color: Long, width: Float, alpha: Float): Stroke? {
            if (rawPoints.size < 10) return null
            val start = rawPoints.first()
            val end = rawPoints.last()
            val totalDistance = hypot(end.x - start.x, end.y - start.y)

            var pathLength = 0f
            for (i in 0 until rawPoints.size - 1) {
                pathLength += hypot(rawPoints[i + 1].x - rawPoints[i].x, rawPoints[i + 1].y - rawPoints[i].y)
            }

            // 1. Check for straight line: pathLength is almost equal to straight distance
            if (pathLength > 40f && totalDistance / pathLength > 0.94f) {
                return createGeometricShape(ToolType.SHAPE_LINE, start, end, color, width, alpha)
            }

            // 2. Check for closed shape: start and end are close together
            val isClosed = hypot(end.x - start.x, end.y - start.y) < 50f
            if (isClosed && pathLength > 80f) {
                var minX = Float.MAX_VALUE; var maxX = Float.MIN_VALUE
                var minY = Float.MAX_VALUE; var maxY = Float.MIN_VALUE
                for (p in rawPoints) {
                    if (p.x < minX) minX = p.x
                    if (p.x > maxX) maxX = p.x
                    if (p.y < minY) minY = p.y
                    if (p.y > maxY) maxY = p.y
                }
                val boxWidth = maxX - minX
                val boxHeight = maxY - minY
                val aspectRatio = if (boxHeight > 0) boxWidth / boxHeight else 1f

                // If aspect ratio is close to 1:1, check if circular
                if (aspectRatio in 0.75f..1.35f) {
                    val centerX = (minX + maxX) / 2f
                    val centerY = (minY + maxY) / 2f
                    val targetRadius = (boxWidth + boxHeight) / 4f
                    var radiusVariance = 0f
                    for (p in rawPoints) {
                        val d = hypot(p.x - centerX, p.y - centerY)
                        radiusVariance += abs(d - targetRadius)
                    }
                    val avgVariance = radiusVariance / rawPoints.size
                    if (avgVariance / targetRadius < 0.22f) {
                        return createGeometricShape(
                            ToolType.SHAPE_CIRCLE,
                            Point(minX, minY),
                            Point(maxX, maxY),
                            color, width, alpha
                        )
                    }
                }

                // If not circle, snap to rectangle
                return createGeometricShape(
                    ToolType.SHAPE_RECTANGLE,
                    Point(minX, minY),
                    Point(maxX, maxY),
                    color, width, alpha
                )
            }

            return null
        }
    }
}

data class RectF(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)
