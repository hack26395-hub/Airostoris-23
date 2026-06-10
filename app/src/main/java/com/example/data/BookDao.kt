package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY createdAt DESC")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Transaction
    @Query("SELECT * FROM books WHERE id = :bookId")
    fun getBookWithPagesFlow(bookId: Long): Flow<BookWithPages?>

    @Transaction
    @Query("SELECT * FROM books WHERE id = :bookId")
    suspend fun getBookWithPages(bookId: Long): BookWithPages?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPage(page: BookPageEntity): Long

    @Update
    suspend fun updateBook(book: BookEntity)

    @Update
    suspend fun updatePage(page: BookPageEntity)

    @Query("UPDATE books SET coverImageUri = :uri WHERE id = :bookId")
    suspend fun updateBookCover(bookId: Long, uri: String?)

    @Query("UPDATE book_pages SET imageUri = :uri WHERE id = :pageId")
    suspend fun updatePageImage(pageId: Long, uri: String?)

    @Query("DELETE FROM books WHERE id = :bookId")
    suspend fun deleteBookById(bookId: Long)

    @Transaction
    suspend fun deleteBook(bookId: Long) {
        deleteBookById(bookId)
    }
}
