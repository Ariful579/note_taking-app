package com.notewise.tabnotes.util

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import com.notewise.tabnotes.data.model.Notebook
import com.notewise.tabnotes.data.model.NotePage
import com.notewise.tabnotes.data.model.PageTemplate
import com.notewise.tabnotes.data.model.ToolType
import java.io.File
import java.io.FileOutputStream

object PdfExporter {

    /**
     * Generates a high-fidelity vector PDF from the notebook pages and triggers
     * the native Android share / download intent.
     */
    fun exportAndSharePdf(
        context: Context,
        notebook: Notebook,
        pages: List<NotePage>
    ): File? {
        val pdfDocument = PdfDocument()
        try {
            // Standard A4 dimensions in PDF points (72 DPI): 595 x 842 pt
            val pageWidth = 595
            val pageHeight = 842
            val docWidth = 1000f // Base canvas width
            val docHeight = 1414.2f // Base canvas height
            val scaleX = pageWidth.toFloat() / docWidth
            val scaleY = pageHeight.toFloat() / docHeight

            val pagesToExport = if (pages.isNotEmpty()) {
                pages.sortedBy { it.pageIndex }
            } else {
                listOf(NotePage(notebookId = notebook.id, pageIndex = 0))
            }

            for ((index, pageData) in pagesToExport.withIndex()) {
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                val pdfPage = pdfDocument.startPage(pageInfo)
                val canvas = pdfPage.canvas

                // 1. Draw Paper Background Tone
                val bgPaint = Paint().apply {
                    color = (pageData.paperColor or 0xFF000000L).toInt()
                    style = Paint.Style.FILL
                }
                canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), bgPaint)

                // 2. Draw High-Contrast Template Guidelines
                drawTemplateGuidelines(
                    canvas = canvas,
                    template = pageData.template,
                    paperColor = pageData.paperColor,
                    width = pageWidth.toFloat(),
                    height = pageHeight.toFloat()
                )

                // 3. Draw Embedded Image Attachments
                for (img in pageData.imageAttachments) {
                    try {
                        val directFile = File(img.filePath)
                        val file = if (directFile.exists()) directFile else {
                            val inImages = File(context.filesDir, "note_images/${directFile.name}")
                            if (inImages.exists()) inImages else File(context.filesDir, directFile.name)
                        }
                        if (file.exists()) {
                            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                            if (bitmap != null) {
                                val left = ((img.x - 40f).coerceAtLeast(0f)) * scaleX
                                val top = ((img.y - 30f).coerceAtLeast(0f)) * scaleY
                                val right = left + (img.width * scaleX)
                                val bottom = top + (img.height * scaleY)
                                val destRect = RectF(left, top, right, bottom)
                                canvas.save()
                                canvas.rotate(img.rotation, destRect.centerX(), destRect.centerY())
                                canvas.drawBitmap(bitmap, null, destRect, null)
                                canvas.restore()
                            }
                        }
                    } catch (_: Exception) {}
                }

                // 4. Draw Text Annotations
                for (ann in pageData.textAnnotations) {
                    if (ann.text.isBlank()) continue
                    val textPaint = Paint().apply {
                        isAntiAlias = true
                        color = (ann.color or 0xFF000000L).toInt()
                        textSize = (ann.fontSizeSp * scaleX * 1.35f).coerceAtLeast(8f)
                        typeface = when {
                            ann.isBold && ann.isItalic -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
                            ann.isBold -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                            ann.isItalic -> Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                            else -> Typeface.DEFAULT
                        }
                    }
                    val annX = ((ann.x - 40f).coerceAtLeast(0f)) * scaleX
                    val annY = ((ann.y - 30f).coerceAtLeast(0f)) * scaleY + textPaint.textSize

                    val lines = ann.text.split("\n")
                    var lineY = annY
                    for (line in lines) {
                        canvas.drawText(line, annX, lineY, textPaint)
                        lineY += textPaint.textSize * 1.25f
                    }
                }

                // 5. Draw Handwriting Vector Strokes (Smooth Quadratic Curves & Geometric Shapes)
                for (stroke in pageData.strokes) {
                    if (stroke.toolType == ToolType.MAGIC_PEN) continue // Fading ink is temporary
                    val pts = stroke.points
                    if (pts.isEmpty()) continue

                    val isHighlighter = stroke.toolType == ToolType.HIGHLIGHTER
                    val baseColor = (stroke.color or 0xFF000000L).toInt()
                    val strokePaint = Paint().apply {
                        isAntiAlias = true
                        color = baseColor
                        alpha = if (isHighlighter) (stroke.alpha * 150).toInt().coerceIn(0, 255) else (stroke.alpha * 255).toInt().coerceIn(0, 255)
                        strokeWidth = if (isHighlighter) (stroke.width * 2.5f * scaleX).coerceAtLeast(2.5f) else (stroke.width * scaleX).coerceAtLeast(0.8f)
                        style = Paint.Style.STROKE
                        strokeCap = if (isHighlighter) Paint.Cap.SQUARE else Paint.Cap.ROUND
                        strokeJoin = if (isHighlighter) Paint.Join.BEVEL else Paint.Join.ROUND
                    }

                    if (pts.size == 1) {
                        val pt = pts[0]
                        val px = ((pt.x - 40f).coerceAtLeast(0f)) * scaleX
                        val py = ((pt.y - 30f).coerceAtLeast(0f)) * scaleY
                        val dotPaint = Paint(strokePaint).apply { style = Paint.Style.FILL }
                        canvas.drawCircle(px, py, strokePaint.strokeWidth / 2f, dotPaint)
                        continue
                    }

                    if (stroke.toolType == ToolType.SHAPE_RECTANGLE && pts.size >= 4) {
                        val left = minOf(pts[0].x, pts[1].x, pts[2].x, pts[3].x)
                        val right = maxOf(pts[0].x, pts[1].x, pts[2].x, pts[3].x)
                        val top = minOf(pts[0].y, pts[1].y, pts[2].y, pts[3].y)
                        val bottom = maxOf(pts[0].y, pts[1].y, pts[2].y, pts[3].y)
                        val rLeft = ((left - 40f).coerceAtLeast(0f)) * scaleX
                        val rTop = ((top - 30f).coerceAtLeast(0f)) * scaleY
                        val rRight = ((right - 40f).coerceAtLeast(0f)) * scaleX
                        val rBottom = ((bottom - 30f).coerceAtLeast(0f)) * scaleY
                        val rectPaint = Paint(strokePaint).apply { strokeJoin = Paint.Join.MITER }
                        canvas.drawRect(rLeft, rTop, rRight, rBottom, rectPaint)
                        continue
                    }

                    val path = Path()
                    val startX = ((pts[0].x - 40f).coerceAtLeast(0f)) * scaleX
                    val startY = ((pts[0].y - 30f).coerceAtLeast(0f)) * scaleY
                    path.moveTo(startX, startY)

                    val isStraightSegment = stroke.toolType in listOf(
                        ToolType.SHAPE_LINE,
                        ToolType.SHAPE_RECTANGLE,
                        ToolType.SHAPE_TRIANGLE,
                        ToolType.SHAPE_ARROW
                    )

                    if (isStraightSegment || pts.size == 2) {
                        for (i in 1 until pts.size) {
                            val px = ((pts[i].x - 40f).coerceAtLeast(0f)) * scaleX
                            val py = ((pts[i].y - 30f).coerceAtLeast(0f)) * scaleY
                            path.lineTo(px, py)
                        }
                    } else {
                        for (i in 1 until pts.size - 1) {
                            val cur = pts[i]
                            val next = pts[i + 1]
                            val curX = ((cur.x - 40f).coerceAtLeast(0f)) * scaleX
                            val curY = ((cur.y - 30f).coerceAtLeast(0f)) * scaleY
                            val nextX = ((next.x - 40f).coerceAtLeast(0f)) * scaleX
                            val nextY = ((next.y - 30f).coerceAtLeast(0f)) * scaleY
                            val midX = (curX + nextX) / 2f
                            val midY = (curY + nextY) / 2f
                            path.quadTo(curX, curY, midX, midY)
                        }
                        val last = pts.last()
                        val lastX = ((last.x - 40f).coerceAtLeast(0f)) * scaleX
                        val lastY = ((last.y - 30f).coerceAtLeast(0f)) * scaleY
                        path.lineTo(lastX, lastY)
                    }

                    if (stroke.isClosedShape) {
                        path.close()
                    }
                    canvas.drawPath(path, strokePaint)
                }

                // 6. Draw Page Number Footer
                val footerPaint = Paint().apply {
                    isAntiAlias = true
                    color = Color.GRAY
                    textSize = 9f
                    textAlign = Paint.Align.RIGHT
                }
                canvas.drawText("${index + 1} / ${pagesToExport.size}", pageWidth - 20f, pageHeight - 16f, footerPaint)

                pdfDocument.finishPage(pdfPage)
            }

            // Save PDF to cache directory for immediate sharing
            val cleanTitle = notebook.title.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "Notebook" }
            val pdfDir = File(context.cacheDir, "exported_pdfs").apply { if (!exists()) mkdirs() }
            val outputFile = File(pdfDir, "${cleanTitle}.pdf")

            FileOutputStream(outputFile).use { out ->
                pdfDocument.writeTo(out)
            }

            // Also copy to device's public Downloads directory
            try {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (downloadsDir != null && downloadsDir.exists()) {
                    val publicFile = File(downloadsDir, "${cleanTitle}.pdf")
                    outputFile.copyTo(publicFile, overwrite = true)
                }
            } catch (_: Exception) {}

            // Share PDF via Android FileProvider Intent
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outputFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, "${notebook.title}.pdf")
                putExtra(Intent.EXTRA_TEXT, "Exported PDF: ${notebook.title}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Download / Share Note as PDF")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)

            Toast.makeText(context, "PDF saved: ${cleanTitle}.pdf", Toast.LENGTH_SHORT).show()
            return outputFile
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to generate PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            return null
        } finally {
            pdfDocument.close()
        }
    }

    private fun drawTemplateGuidelines(
        canvas: Canvas,
        template: PageTemplate,
        paperColor: Long,
        width: Float,
        height: Float
    ) {
        val isDark = (((paperColor shr 16) and 0xFF) * 0.299 +
                      ((paperColor shr 8) and 0xFF) * 0.587 +
                      (paperColor and 0xFF) * 0.114) < 122

        val linePaint = Paint().apply {
            color = if (isDark) Color.argb(60, 255, 255, 255) else Color.argb(45, 30, 41, 59)
            strokeWidth = 0.8f
            style = Paint.Style.STROKE
        }
        val accentPaint = Paint().apply {
            color = if (isDark) Color.argb(120, 244, 63, 94) else Color.argb(100, 225, 29, 72)
            strokeWidth = 1.2f
            style = Paint.Style.STROKE
        }

        when (template) {
            PageTemplate.BLANK -> {}

            PageTemplate.LINED -> {
                val lineSpacing = 24f
                val leftMargin = 48f
                // Margin vertical line
                canvas.drawLine(leftMargin, 0f, leftMargin, height, accentPaint)
                // Ruled horizontal lines
                var y = 48f
                while (y < height - 20f) {
                    canvas.drawLine(0f, y, width, y, linePaint)
                    y += lineSpacing
                }
            }

            PageTemplate.GRID -> {
                val spacing = 20f
                var x = spacing
                while (x < width) {
                    canvas.drawLine(x, 0f, x, height, linePaint)
                    x += spacing
                }
                var y = spacing
                while (y < height) {
                    canvas.drawLine(0f, y, width, y, linePaint)
                    y += spacing
                }
            }

            PageTemplate.DOTTED -> {
                val dotPaint = Paint().apply {
                    color = if (isDark) Color.argb(80, 255, 255, 255) else Color.argb(60, 51, 65, 85)
                    style = Paint.Style.FILL
                }
                val spacing = 20f
                var y = spacing
                while (y < height) {
                    var x = spacing
                    while (x < width) {
                        canvas.drawCircle(x, y, 1.2f, dotPaint)
                        x += spacing
                    }
                    y += spacing
                }
            }

            PageTemplate.CORNELL -> {
                val cueX = width * 0.28f
                val headerY = 50f
                val summaryY = height - 100f
                // Header line
                canvas.drawLine(0f, headerY, width, headerY, accentPaint)
                // Cue column
                canvas.drawLine(cueX, headerY, cueX, summaryY, accentPaint)
                // Summary line
                canvas.drawLine(0f, summaryY, width, summaryY, accentPaint)
                // Notes lines
                var y = headerY + 22f
                while (y < summaryY) {
                    canvas.drawLine(cueX, y, width, y, linePaint)
                    y += 22f
                }
            }

            PageTemplate.TASK_LIST -> {
                val rowHeight = 28f
                val checkX = 28f
                val boxSize = 12f
                var y = 36f
                while (y < height - 36f) {
                    canvas.drawRect(checkX, y - boxSize / 2f, checkX + boxSize, y + boxSize / 2f, linePaint)
                    canvas.drawLine(checkX + boxSize + 12f, y + boxSize / 2f, width - 24f, y + boxSize / 2f, linePaint)
                    y += rowHeight
                }
            }
        }
    }
}
