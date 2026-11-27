package com.notewise.tabnotes.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Result data holder for imported PDF documents.
 */
data class PdfImportResult(
    val title: String,
    val pageImagePaths: List<String>,
    val aspectRatio: Float
)

/**
 * Native, offline PDF importer using Android SDK's [PdfRenderer].
 * Converts each PDF page into a high-DPI raster image cached in local app storage
 * for zero-latency, smooth multi-page continuous handwriting and annotation.
 */
object PdfImporter {

    suspend fun importPdf(
        context: Context,
        uri: Uri,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): PdfImportResult = withContext(Dispatchers.IO) {
        val title = extractTitle(context, uri)
        val tempFile = File(context.cacheDir, "pdf_import_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.pdf")

        // Copy input stream from URI to local temporary file
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Unable to read PDF from selected file source.")

        val pageImagePaths = mutableListOf<String>()
        var detectedAspectRatio = 1.4142f // Standard A4 aspect ratio default

        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null

        try {
            pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            val totalPages = renderer.pageCount

            val targetDir = File(context.filesDir, "pdf_pages").apply { mkdirs() }
            val batchId = UUID.randomUUID().toString().take(8)

            for (pageIndex in 0 until totalPages) {
                onProgress?.invoke(pageIndex + 1, totalPages)
                var page: PdfRenderer.Page? = null
                try {
                    page = renderer.openPage(pageIndex)

                    // Capture aspect ratio from the first page
                    if (pageIndex == 0 && page.width > 0) {
                        detectedAspectRatio = page.height.toFloat() / page.width.toFloat()
                    }

                    // Render at high resolution (1600px width) for ultra-sharp note taking
                    val targetWidth = 1600
                    val targetHeight = if (page.width > 0) {
                        (targetWidth.toFloat() * (page.height.toFloat() / page.width.toFloat())).toInt().coerceAtLeast(200)
                    } else 2262

                    val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE) // Ensure opaque white background for transparent PDFs

                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    val imageFile = File(targetDir, "pdf_${batchId}_p${pageIndex}.jpg")
                    FileOutputStream(imageFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                    }
                    bitmap.recycle()

                    pageImagePaths.add(imageFile.absolutePath)
                } finally {
                    try {
                        page?.close()
                    } catch (_: Exception) {}
                }
            }
        } finally {
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
            try { tempFile.delete() } catch (_: Exception) {}
        }

        PdfImportResult(
            title = title,
            pageImagePaths = pageImagePaths,
            aspectRatio = detectedAspectRatio
        )
    }

    private fun extractTitle(context: Context, uri: Uri): String {
        var name: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            name = cursor.getString(nameIndex)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        if (name.isNullOrBlank()) {
            name = uri.lastPathSegment
        }
        if (name.isNullOrBlank()) {
            name = "Imported PDF"
        }
        if (name!!.endsWith(".pdf", ignoreCase = true)) {
            name = name!!.substring(0, name!!.length - 4)
        }
        return name!!.trim().ifBlank { "Imported PDF" }
    }
}
