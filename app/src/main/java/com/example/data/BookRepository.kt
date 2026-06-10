package com.example.data

import kotlinx.coroutines.flow.Flow

class BookRepository(private val bookDao: BookDao) {
    val allBooks: Flow<List<BookEntity>> = bookDao.getAllBooks()

    fun getBookWithPagesFlow(bookId: Long): Flow<BookWithPages?> {
        return bookDao.getBookWithPagesFlow(bookId)
    }

    suspend fun getBookWithPages(bookId: Long): BookWithPages? {
        return bookDao.getBookWithPages(bookId)
    }

    suspend fun insertBook(book: BookEntity): Long {
        return bookDao.insertBook(book)
    }

    suspend fun insertPage(page: BookPageEntity): Long {
        return bookDao.insertPage(page)
    }

    suspend fun updateBook(book: BookEntity) {
        bookDao.updateBook(book)
    }

    suspend fun updatePage(page: BookPageEntity) {
        bookDao.updatePage(page)
    }

    suspend fun updateBookCover(bookId: Long, uri: String?) {
        bookDao.updateBookCover(bookId, uri)
    }

    suspend fun updatePageImage(pageId: Long, uri: String?) {
        bookDao.updatePageImage(pageId, uri)
    }

    suspend fun deleteBook(bookId: Long) {
        bookDao.deleteBook(bookId)
    }
}
