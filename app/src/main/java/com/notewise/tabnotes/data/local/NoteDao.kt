package com.notewise.tabnotes.data.local

import androidx.room.*
import com.notewise.tabnotes.data.model.Folder
import com.notewise.tabnotes.data.model.Notebook
import com.notewise.tabnotes.data.model.NotePage
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    // Notebook queries
    @Query("SELECT * FROM notebooks ORDER BY updatedAt DESC")
    fun getAllNotebooks(): Flow<List<Notebook>>

    @Query("SELECT * FROM notebooks WHERE folderId = :folderId ORDER BY updatedAt DESC")
    fun getNotebooksInFolder(folderId: String): Flow<List<Notebook>>

    @Query("SELECT * FROM notebooks WHERE isFavorite = 1 ORDER BY updatedAt DESC")
    fun getFavoriteNotebooks(): Flow<List<Notebook>>

    @Query("SELECT * FROM notebooks WHERE id = :notebookId LIMIT 1")
    fun getNotebookById(notebookId: String): Flow<Notebook?>

    @Query("SELECT * FROM notebooks WHERE id = :notebookId LIMIT 1")
    suspend fun getNotebookByIdOnce(notebookId: String): Notebook?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotebook(notebook: Notebook)

    @Update
    suspend fun updateNotebook(notebook: Notebook)

    @Delete
    suspend fun deleteNotebook(notebook: Notebook)

    // Page queries
    @Query("SELECT * FROM note_pages WHERE notebookId = :notebookId ORDER BY pageIndex ASC")
    fun getPagesForNotebook(notebookId: String): Flow<List<NotePage>>

    @Query("SELECT * FROM note_pages WHERE notebookId = :notebookId ORDER BY pageIndex ASC")
    suspend fun getPagesForNotebookOnce(notebookId: String): List<NotePage>

    @Query("SELECT * FROM note_pages WHERE notebookId = :notebookId AND pageIndex = :pageIndex LIMIT 1")
    fun getPage(notebookId: String, pageIndex: Int): Flow<NotePage?>

    @Query("SELECT * FROM note_pages WHERE notebookId = :notebookId AND pageIndex = :pageIndex LIMIT 1")
    suspend fun getPageOnce(notebookId: String, pageIndex: Int): NotePage?

    @Query("SELECT COUNT(*) FROM note_pages WHERE notebookId = :notebookId")
    suspend fun getPageCount(notebookId: String): Int

    @Query("SELECT MAX(pageIndex) FROM note_pages WHERE notebookId = :notebookId")
    suspend fun getMaxPageIndex(notebookId: String): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPage(page: NotePage)

    @Update
    suspend fun updatePage(page: NotePage)

    @Delete
    suspend fun deletePage(page: NotePage)

    @Query("DELETE FROM note_pages WHERE notebookId = :notebookId")
    suspend fun deletePagesForNotebook(notebookId: String)

    // Folder queries
    @Query("SELECT * FROM folders ORDER BY name ASC")
    fun getAllFolders(): Flow<List<Folder>>

    @Query("SELECT * FROM folders WHERE parentFolderId IS NULL ORDER BY name ASC")
    fun getRootFolders(): Flow<List<Folder>>

    @Query("SELECT * FROM folders WHERE parentFolderId = :parentId ORDER BY name ASC")
    fun getSubfolders(parentId: String): Flow<List<Folder>>

    @Query("SELECT * FROM folders WHERE id = :folderId LIMIT 1")
    suspend fun getFolderById(folderId: String): Folder?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: Folder)

    @Update
    suspend fun updateFolder(folder: Folder)

    @Delete
    suspend fun deleteFolder(folder: Folder)

    @Query("UPDATE notebooks SET folderId = :folderId WHERE id = :notebookId")
    suspend fun moveNotebookToFolder(notebookId: String, folderId: String?)

    @Query("UPDATE notebooks SET folderId = NULL WHERE folderId = :folderId")
    suspend fun unassignNotebooksFromFolder(folderId: String)
}
