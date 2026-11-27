package com.notewise.tabnotes.data.repository

import com.notewise.tabnotes.data.local.Converters
import com.notewise.tabnotes.data.local.NoteDao
import com.notewise.tabnotes.data.model.*
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class NoteRepository(private val noteDao: NoteDao) {

    val allNotebooks: Flow<List<Notebook>> = noteDao.getAllNotebooks()
    val allFolders: Flow<List<Folder>> = noteDao.getAllFolders()
    val favoriteNotebooks: Flow<List<Notebook>> = noteDao.getFavoriteNotebooks()

    fun getNotebooksInFolder(folderId: String): Flow<List<Notebook>> =
        noteDao.getNotebooksInFolder(folderId)

    fun getNotebookById(id: String): Flow<Notebook?> =
        noteDao.getNotebookById(id)

    fun getPagesForNotebook(notebookId: String): Flow<List<NotePage>> =
        noteDao.getPagesForNotebook(notebookId)

    fun getPage(notebookId: String, pageIndex: Int): Flow<NotePage?> =
        noteDao.getPage(notebookId, pageIndex)

    suspend fun getNotebookByIdOnce(id: String): Notebook? =
        noteDao.getNotebookByIdOnce(id)

    suspend fun getPageOnce(notebookId: String, pageIndex: Int): NotePage? =
        noteDao.getPageOnce(notebookId, pageIndex)

    suspend fun getPagesForNotebookOnce(notebookId: String): List<NotePage> =
        noteDao.getPagesForNotebookOnce(notebookId)

    suspend fun createNotebookFromPdf(
        title: String,
        folderId: String? = null,
        pageImagePaths: List<String>,
        pageAspectRatio: Float = 1.4142f
    ): Notebook {
        val notebookId = UUID.randomUUID().toString()
        val totalPages = maxOf(1, pageImagePaths.size)
        val notebook = Notebook(
            id = notebookId,
            title = title.ifBlank { "Imported PDF" },
            folderId = folderId,
            coverColor = 0xFFDC2626, // Crimson PDF badge cover
            pageCount = totalPages,
            defaultTemplate = PageTemplate.BLANK,
            defaultPaperColor = PaperColor.WHITE.hex,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        noteDao.insertNotebook(notebook)

        for ((index, imagePath) in pageImagePaths.withIndex()) {
            val bgImage = ImageAttachment(
                id = "pdf_bg_${UUID.randomUUID()}",
                filePath = imagePath,
                x = 40f,
                y = 30f,
                width = 1000f,
                height = 1000f * pageAspectRatio,
                rotation = 0f
            )
            val notePage = NotePage(
                id = UUID.randomUUID().toString(),
                notebookId = notebookId,
                pageIndex = index,
                template = PageTemplate.BLANK,
                paperColor = PaperColor.WHITE.hex,
                strokes = emptyList(),
                textAnnotations = emptyList(),
                imageAttachments = listOf(bgImage),
                createdAt = System.currentTimeMillis() + index,
                updatedAt = System.currentTimeMillis() + index
            )
            noteDao.insertPage(notePage)
        }

        return notebook
    }

    suspend fun appendPdfPagesToNotebook(
        notebookId: String,
        pageImagePaths: List<String>,
        pageAspectRatio: Float = 1.4142f
    ): List<NotePage> {
        if (pageImagePaths.isEmpty()) return emptyList()
        val notebook = noteDao.getNotebookByIdOnce(notebookId) ?: return emptyList()
        val existingPages = noteDao.getPagesForNotebookOnce(notebookId).sortedBy { it.pageIndex }

        val createdPages = mutableListOf<NotePage>()

        // Check if notebook has only 1 blank page with no ink or attachments
        val canReplaceFirstEmptyPage = existingPages.size == 1 &&
                existingPages[0].strokes.isEmpty() &&
                existingPages[0].textAnnotations.isEmpty() &&
                existingPages[0].imageAttachments.isEmpty()

        val startIndex = if (canReplaceFirstEmptyPage) 0 else ((noteDao.getMaxPageIndex(notebookId) ?: -1) + 1)

        for ((i, imagePath) in pageImagePaths.withIndex()) {
            val pageIdx = startIndex + i
            val bgImage = ImageAttachment(
                id = "pdf_bg_${UUID.randomUUID()}",
                filePath = imagePath,
                x = 40f,
                y = 30f,
                width = 1000f,
                height = 1000f * pageAspectRatio,
                rotation = 0f
            )
            if (canReplaceFirstEmptyPage && i == 0) {
                val updatedFirstPage = existingPages[0].copy(
                    template = PageTemplate.BLANK,
                    paperColor = PaperColor.WHITE.hex,
                    imageAttachments = listOf(bgImage),
                    updatedAt = System.currentTimeMillis()
                )
                noteDao.updatePage(updatedFirstPage)
                createdPages.add(updatedFirstPage)
            } else {
                val newPage = NotePage(
                    id = UUID.randomUUID().toString(),
                    notebookId = notebookId,
                    pageIndex = pageIdx,
                    template = PageTemplate.BLANK,
                    paperColor = PaperColor.WHITE.hex,
                    strokes = emptyList(),
                    textAnnotations = emptyList(),
                    imageAttachments = listOf(bgImage),
                    createdAt = System.currentTimeMillis() + i,
                    updatedAt = System.currentTimeMillis() + i
                )
                noteDao.insertPage(newPage)
                createdPages.add(newPage)
            }
        }

        val totalCount = noteDao.getPageCount(notebookId)
        val newPageCount = maxOf(notebook.pageCount, totalCount)
        noteDao.updateNotebook(notebook.copy(pageCount = newPageCount, updatedAt = System.currentTimeMillis()))

        return createdPages
    }

    suspend fun createNotebook(
        title: String = "Untitled Notebook",
        folderId: String? = null,
        coverColor: Long = 0xFF3B82F6,
        template: PageTemplate = PageTemplate.LINED,
        paperColor: Long = PaperColor.WHITE.hex
    ): Notebook {
        val notebook = Notebook(
            id = UUID.randomUUID().toString(),
            title = title,
            folderId = folderId,
            coverColor = coverColor,
            pageCount = 1,
            defaultTemplate = template,
            defaultPaperColor = paperColor,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        noteDao.insertNotebook(notebook)

        // Initialize first page
        val firstPage = NotePage(
            id = UUID.randomUUID().toString(),
            notebookId = notebook.id,
            pageIndex = 0,
            template = template,
            paperColor = paperColor
        )
        noteDao.insertPage(firstPage)

        return notebook
    }

    suspend fun updateNotebook(notebook: Notebook) {
        noteDao.updateNotebook(notebook.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun renameNotebook(notebookId: String, newTitle: String) {
        val nb = noteDao.getNotebookByIdOnce(notebookId) ?: return
        noteDao.updateNotebook(nb.copy(title = newTitle, updatedAt = System.currentTimeMillis()))
    }

    suspend fun duplicatePage(notebookId: String, pageIndex: Int): NotePage? {
        val sourcePage = noteDao.getPageOnce(notebookId, pageIndex) ?: return null
        val maxIndex = noteDao.getMaxPageIndex(notebookId) ?: pageIndex
        val nextIndex = maxIndex + 1
        val duplicatedPage = sourcePage.copy(
            id = UUID.randomUUID().toString(),
            pageIndex = nextIndex,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        noteDao.insertPage(duplicatedPage)
        val notebook = noteDao.getNotebookByIdOnce(notebookId)
        if (notebook != null) {
            val updatedCount = maxOf(notebook.pageCount, nextIndex + 1)
            noteDao.updateNotebook(notebook.copy(pageCount = updatedCount, updatedAt = System.currentTimeMillis()))
        }
        return duplicatedPage
    }

    suspend fun deleteNotebook(notebook: Notebook) {
        noteDao.deletePagesForNotebook(notebook.id)
        noteDao.deleteNotebook(notebook)
    }

    suspend fun deletePage(notebookId: String, pageIndex: Int) {
        val page = noteDao.getPageOnce(notebookId, pageIndex)
        if (page != null) {
            noteDao.deletePage(page)
        }
        val remaining = noteDao.getPagesForNotebookOnce(notebookId)
        remaining.sortedBy { it.pageIndex }.forEachIndexed { idx, p ->
            if (p.pageIndex != idx) {
                noteDao.updatePage(p.copy(pageIndex = idx))
            }
        }
        val notebook = noteDao.getNotebookByIdOnce(notebookId)
        if (notebook != null) {
            val updatedCount = maxOf(1, remaining.size)
            noteDao.updateNotebook(notebook.copy(pageCount = updatedCount, updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun savePageStrokesAndAnnotations(
        notebookId: String,
        pageIndex: Int,
        strokes: List<Stroke>,
        annotations: List<TextAnnotation>,
        images: List<ImageAttachment> = emptyList(),
        template: PageTemplate? = null,
        paperColor: Long? = null
    ) {
        val existing = noteDao.getPageOnce(notebookId, pageIndex)
        if (existing != null) {
            val updated = existing.copy(
                template = template ?: existing.template,
                paperColor = paperColor ?: existing.paperColor,
                strokes = strokes,
                textAnnotations = annotations,
                imageAttachments = images,
                updatedAt = System.currentTimeMillis()
            )
            noteDao.updatePage(updated)
        } else {
            val notebook = noteDao.getNotebookByIdOnce(notebookId)
            val newPage = NotePage(
                notebookId = notebookId,
                pageIndex = pageIndex,
                template = template ?: notebook?.defaultTemplate ?: PageTemplate.LINED,
                paperColor = paperColor ?: notebook?.defaultPaperColor ?: PaperColor.WHITE.hex,
                strokes = strokes,
                textAnnotations = annotations,
                imageAttachments = images
            )
            noteDao.insertPage(newPage)
        }

        // Touch notebook updated timestamp
        val nb = noteDao.getNotebookByIdOnce(notebookId)
        if (nb != null) {
            val currentCount = noteDao.getPageCount(notebookId)
            val newCount = maxOf(nb.pageCount, currentCount, pageIndex + 1)
            noteDao.updateNotebook(nb.copy(pageCount = newCount, updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun addNewPage(notebookId: String, template: PageTemplate? = null, paperColor: Long? = null): NotePage {
        val maxIndex = noteDao.getMaxPageIndex(notebookId) ?: -1
        val nextIndex = maxIndex + 1
        val notebook = noteDao.getNotebookByIdOnce(notebookId)
        val newPage = NotePage(
            id = UUID.randomUUID().toString(),
            notebookId = notebookId,
            pageIndex = nextIndex,
            template = template ?: notebook?.defaultTemplate ?: PageTemplate.LINED,
            paperColor = paperColor ?: notebook?.defaultPaperColor ?: PaperColor.WHITE.hex,
            strokes = emptyList(),
            textAnnotations = emptyList(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        noteDao.insertPage(newPage)

        val updatedCount = maxOf(notebook?.pageCount ?: 0, nextIndex + 1)
        if (notebook != null) {
            noteDao.updateNotebook(notebook.copy(pageCount = updatedCount, updatedAt = System.currentTimeMillis()))
        }
        return newPage
    }

    suspend fun createFolder(name: String, parentFolderId: String? = null, color: Long = 0xFF64748B): Folder {
        val folder = Folder(name = name, parentFolderId = parentFolderId, color = color)
        noteDao.insertFolder(folder)
        return folder
    }

    suspend fun updateFolder(folder: Folder) {
        noteDao.updateFolder(folder)
    }

    suspend fun deleteFolder(folder: Folder) {
        noteDao.unassignNotebooksFromFolder(folder.id)
        noteDao.deleteFolder(folder)
    }

    suspend fun moveNotebookToFolder(notebookId: String, folderId: String?) {
        noteDao.moveNotebookToFolder(notebookId, folderId)
    }

    suspend fun exportNotebookAsJson(notebookId: String): String {
        val notebook = noteDao.getNotebookByIdOnce(notebookId) ?: return "{}"
        val pageCount = noteDao.getPageCount(notebookId)
        val pagesArray = JSONArray()
        val converters = Converters()

        for (i in 0 until pageCount) {
            val p = noteDao.getPageOnce(notebookId, i)
            if (p != null) {
                val pObj = JSONObject()
                pObj.put("pageIndex", p.pageIndex)
                pObj.put("template", p.template.name)
                pObj.put("paperColor", p.paperColor)
                pObj.put("strokesCount", p.strokes.size)
                pObj.put("strokesJson", converters.fromStrokeList(p.strokes))
                pObj.put("textAnnotationsJson", converters.fromTextAnnotationList(p.textAnnotations))
                pagesArray.put(pObj)
            }
        }

        val root = JSONObject()
        root.put("id", notebook.id)
        root.put("title", notebook.title)
        root.put("coverColor", notebook.coverColor)
        root.put("pageCount", pageCount)
        root.put("defaultTemplate", notebook.defaultTemplate.name)
        root.put("defaultPaperColor", notebook.defaultPaperColor)
        root.put("createdAt", notebook.createdAt)
        root.put("updatedAt", notebook.updatedAt)
        root.put("pages", pagesArray)

        return root.toString(2)
    }

    suspend fun exportPageAsText(notebookId: String, pageIndex: Int): String {
        val notebook = noteDao.getNotebookByIdOnce(notebookId)
        val page = noteDao.getPageOnce(notebookId, pageIndex)
        val sb = StringBuilder()
        sb.append("# ${notebook?.title ?: "Note"}\n")
        sb.append("Page: ${pageIndex + 1} / ${notebook?.pageCount ?: 1}\n")
        sb.append("Template: ${page?.template?.displayName ?: "Ruled"}\n")
        sb.append("--------------------------------------------------\n\n")

        if (page?.textAnnotations?.isNotEmpty() == true) {
            sb.append("## Text Annotations:\n")
            for (ann in page.textAnnotations) {
                sb.append("- ${ann.text}\n")
            }
            sb.append("\n")
        }

        sb.append("## Handwriting Details:\n")
        sb.append("Total Vector Strokes: ${page?.strokes?.size ?: 0}\n")
        val toolsUsed = page?.strokes?.map { it.toolType.name }?.distinct()?.joinToString(", ") ?: "None"
        sb.append("Tools Used: $toolsUsed\n")

        return sb.toString()
    }
}
