package com.notewise.tabnotes.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.notewise.tabnotes.data.model.NotePage
import com.notewise.tabnotes.data.model.PageTemplate
import com.notewise.tabnotes.data.model.PaperSize

/**
 * Continuous vertical multi-page template renderer.
 * Efficiently draws paper sheets with shadows, backgrounds, borders, and guidelines
 * for all notebook pages in a seamless vertical layout.
 */
@Composable
fun PageTemplateCanvas(
    template: PageTemplate,
    paperColor: Color,
    zoom: Float = 1.0f,
    panOffset: Offset = Offset.Zero,
    paperSize: PaperSize = PaperSize.A4,
    pageCount: Int = 1,
    allPages: List<NotePage> = emptyList(),
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier.fillMaxSize()
    ) {
        withTransform({
            translate(panOffset.x, panOffset.y)
            scale(zoom, zoom, Offset.Zero)
        }) {
            val paperLeft = 40f
            val paperTop = 30f
            val paperWidth = 1000f
            val paperHeight = paperWidth * paperSize.aspectRatio
            val pageGap = 40f
            val totalPages = maxOf(1, pageCount)

            // Viewport culling in document coordinates with 200px safety buffer
            val visibleTop = (-panOffset.y / zoom) - 200f
            val visibleBottom = ((-panOffset.y + size.height) / zoom) + 200f

            for (pageIdx in 0 until totalPages) {
                val currentTop = paperTop + pageIdx * (paperHeight + pageGap)
                val pageBottom = currentTop + paperHeight

                // Skip offscreen pages completely for maximum rendering smoothness
                if (pageBottom < visibleTop || currentTop > visibleBottom) continue

                val pageData = allPages.find { it.pageIndex == pageIdx }
                val curTemplate = pageData?.template ?: template
                val curPaperColor = pageData?.let { Color(it.paperColor) } ?: paperColor

                drawSinglePageSheet(
                    template = curTemplate,
                    paperColor = curPaperColor,
                    paperLeft = paperLeft,
                    paperTop = currentTop,
                    paperWidth = paperWidth,
                    paperHeight = paperHeight
                )
            }
        }
    }
}

private fun DrawScope.drawSinglePageSheet(
    template: PageTemplate,
    paperColor: Color,
    paperLeft: Float,
    paperTop: Float,
    paperWidth: Float,
    paperHeight: Float
) {
    val isDark = (paperColor.red * 0.299f + paperColor.green * 0.587f + paperColor.blue * 0.114f) < 0.48f

    // 1. Realistic drop shadow behind paper sheet
    drawRoundRect(
        color = Color(0x35000000),
        topLeft = Offset(paperLeft + 4f, paperTop + 6f),
        size = Size(paperWidth, paperHeight),
        cornerRadius = CornerRadius(12f, 12f)
    )

    // 2. Paper sheet base fill
    drawRoundRect(
        color = paperColor,
        topLeft = Offset(paperLeft, paperTop),
        size = Size(paperWidth, paperHeight),
        cornerRadius = CornerRadius(8f, 8f)
    )

    // 3. Subtle sheet outline border
    drawRoundRect(
        color = if (isDark) Color(0x33FFFFFF) else Color(0x22000000),
        topLeft = Offset(paperLeft, paperTop),
        size = Size(paperWidth, paperHeight),
        cornerRadius = CornerRadius(8f, 8f),
        style = Stroke(width = 1.2f)
    )

    // 4. High-contrast template line and marker colors
    val mainLineColor = if (isDark) Color(0x60FFFFFF) else Color(0x451E293B)
    val accentLineColor = if (isDark) Color(0x99F43F5E) else Color(0x88E11D48)
    val dotColor = if (isDark) Color(0x80FFFFFF) else Color(0x55334155)

    when (template) {
        PageTemplate.BLANK -> {
            // Clean, pristine paper
        }

        PageTemplate.LINED -> {
            val lineSpacing = 38f
            val leftMargin = paperLeft + 80f

            // Vertical left margin accent line
            drawLine(
                color = accentLineColor,
                start = Offset(leftMargin, paperTop),
                end = Offset(leftMargin, paperTop + paperHeight),
                strokeWidth = 1.6f
            )

            // Horizontal ruled lines
            var y = paperTop + 76f
            while (y < paperTop + paperHeight - 30f) {
                drawLine(
                    color = mainLineColor,
                    start = Offset(paperLeft, y),
                    end = Offset(paperLeft + paperWidth, y),
                    strokeWidth = 1.2f
                )
                y += lineSpacing
            }
        }

        PageTemplate.GRID -> {
            val gridSpacing = 32f

            // Vertical grid lines
            var x = paperLeft + gridSpacing
            while (x < paperLeft + paperWidth) {
                drawLine(
                    color = mainLineColor,
                    start = Offset(x, paperTop),
                    end = Offset(x, paperTop + paperHeight),
                    strokeWidth = 1.0f
                )
                x += gridSpacing
            }

            // Horizontal grid lines
            var y = paperTop + gridSpacing
            while (y < paperTop + paperHeight) {
                drawLine(
                    color = mainLineColor,
                    start = Offset(paperLeft, y),
                    end = Offset(paperLeft + paperWidth, y),
                    strokeWidth = 1.0f
                )
                y += gridSpacing
            }
        }

        PageTemplate.DOTTED -> {
            val dotSpacing = 32f
            val radius = 1.8f

            var y = paperTop + dotSpacing
            while (y < paperTop + paperHeight - 16f) {
                var x = paperLeft + dotSpacing
                while (x < paperLeft + paperWidth - 16f) {
                    drawCircle(
                        color = dotColor,
                        radius = radius,
                        center = Offset(x, y)
                    )
                    x += dotSpacing
                }
                y += dotSpacing
            }
        }

        PageTemplate.CORNELL -> {
            val cueColumnX = paperLeft + (paperWidth * 0.28f)
            val headerY = paperTop + 76f
            val summaryY = paperTop + paperHeight - 180f
            val lineSpacing = 36f

            // Header line
            drawLine(
                color = accentLineColor,
                start = Offset(paperLeft, headerY),
                end = Offset(paperLeft + paperWidth, headerY),
                strokeWidth = 2.0f
            )

            // Cue vertical divider
            drawLine(
                color = accentLineColor,
                start = Offset(cueColumnX, headerY),
                end = Offset(cueColumnX, summaryY),
                strokeWidth = 2.0f
            )

            // Summary horizontal divider
            drawLine(
                color = accentLineColor,
                start = Offset(paperLeft, summaryY),
                end = Offset(paperLeft + paperWidth, summaryY),
                strokeWidth = 2.0f
            )

            // Notes body ruled lines
            var y = headerY + lineSpacing
            while (y < summaryY) {
                drawLine(
                    color = mainLineColor,
                    start = Offset(cueColumnX, y),
                    end = Offset(paperLeft + paperWidth, y),
                    strokeWidth = 1.2f
                )
                y += lineSpacing
            }
        }

        PageTemplate.TASK_LIST -> {
            val rowHeight = 44f
            val checkX = paperLeft + 40f
            val boxSize = 18f

            var y = paperTop + 60f
            while (y < paperTop + paperHeight - 60f) {
                // Task Checkbox
                drawRoundRect(
                    color = mainLineColor,
                    topLeft = Offset(checkX, y - boxSize / 2f),
                    size = Size(boxSize, boxSize),
                    cornerRadius = CornerRadius(3f, 3f),
                    style = Stroke(width = 1.6f)
                )

                // Ruled line for task description
                drawLine(
                    color = mainLineColor,
                    start = Offset(checkX + boxSize + 18f, y + boxSize / 2f),
                    end = Offset(paperLeft + paperWidth - 40f, y + boxSize / 2f),
                    strokeWidth = 1.2f
                )
                y += rowHeight
            }
        }
    }
}
