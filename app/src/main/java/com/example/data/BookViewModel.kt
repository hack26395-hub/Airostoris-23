package com.example.data

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.utils.PdfExporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.OutputStream

class BookViewModel(application: Application, private val repository: BookRepository) : AndroidViewModel(application) {
    private val TAG = "BookViewModel"

    // Fetch list of all books in the database
    val allBooks: StateFlow<List<BookEntity>> = repository.allBooks
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Current active book details
    private val _activeBookId = MutableStateFlow<Long?>(null)
    val activeBookId: StateFlow<Long?> = _activeBookId.asStateFlow()

    private val _activeBookWithPages = MutableStateFlow<BookWithPages?>(null)
    val activeBookWithPages: StateFlow<BookWithPages?> = _activeBookWithPages.asStateFlow()

    // Form states for creating a new book
    var inputTitle = MutableStateFlow("")
    var inputIdea = MutableStateFlow("")
    var inputLanguage = MutableStateFlow("العربية")
    var inputGenre = MutableStateFlow("خيال علمي")
    var inputSeriesName = MutableStateFlow("")
    var inputSeriesPart = MutableStateFlow("الجزء الاول")
    var inputHasLiteraryTouches = MutableStateFlow(true)
    var inputTotalPagesGoal = MutableStateFlow(10)
    var inputLinesPerPage = MutableStateFlow(12)
    var selectedCoverUri = MutableStateFlow<String?>(null)

    // Editing states
    var isGenerating = MutableStateFlow(false)
    var generationError = MutableStateFlow<String?>(null)
    var toastMessage = MutableStateFlow<String?>(null)

    // Temporary image holder during page writing
    var selectedPageFileUri = MutableStateFlow<String?>(null)

    init {
        // Observe the active book changes
        viewModelScope.launch {
            _activeBookId.collect { id ->
                if (id != null) {
                    repository.getBookWithPagesFlow(id).collect { bookWithPages ->
                        _activeBookWithPages.value = bookWithPages
                    }
                } else {
                    _activeBookWithPages.value = null
                }
            }
        }
    }

    fun selectBook(bookId: Long) {
        _activeBookId.value = bookId
        selectedPageFileUri.value = null
    }

    fun clearActiveBook() {
        _activeBookId.value = null
        selectedPageFileUri.value = null
    }

    fun deleteBook(bookId: Long) {
        viewModelScope.launch {
            repository.deleteBook(bookId)
            if (_activeBookId.value == bookId) {
                clearActiveBook()
            }
            showToast("تم حذف الكتاب بنجاح")
        }
    }

    fun resetForm() {
        inputTitle.value = ""
        inputIdea.value = ""
        inputLanguage.value = "العربية"
        inputGenre.value = "خيال علمي"
        inputSeriesName.value = ""
        inputSeriesPart.value = "الجزء الاول"
        inputHasLiteraryTouches.value = true
        inputTotalPagesGoal.value = 10
        inputLinesPerPage.value = 12
        selectedCoverUri.value = null
        generationError.value = null
    }

    fun updateCoverUri(uri: String?) {
        selectedCoverUri.value = uri
        val currentBookId = _activeBookId.value
        if (currentBookId != null) {
            viewModelScope.launch {
                repository.updateBookCover(currentBookId, uri)
            }
        }
    }

    fun updatePageImageUri(pageId: Long, uri: String?) {
        viewModelScope.launch {
            repository.updatePageImage(pageId, uri)
        }
    }

    fun createAndStartBook(onSuccess: (Long) -> Unit) {
        if (inputTitle.value.isBlank()) {
            generationError.value = "يرجى إدخال اسم الكتاب أولاً"
            return
        }
        if (inputIdea.value.isBlank()) {
            generationError.value = "يرجى تحديد فكرة كافية للكتاب لكي يستطيع الذكاء الاصطناعي الكتابة"
            return
        }

        generationError.value = null
        viewModelScope.launch {
            isGenerating.value = true
            try {
                // Insert empty Book entry
                val newBook = BookEntity(
                    title = inputTitle.value.trim(),
                    idea = inputIdea.value.trim(),
                    language = inputLanguage.value.trim(),
                    genre = inputGenre.value,
                    seriesName = inputSeriesName.value.trim(),
                    seriesPart = inputSeriesPart.value,
                    hasLiteraryTouches = inputHasLiteraryTouches.value,
                    totalPagesGoal = inputTotalPagesGoal.value,
                    linesPerPage = inputLinesPerPage.value,
                    coverImageUri = selectedCoverUri.value
                )
                val bookId = repository.insertBook(newBook)
                _activeBookId.value = bookId

                // Generate the first page automatically!
                val firstPageContent = GeminiService.generateNextPage(
                    book = newBook.copy(id = bookId),
                    existingPages = emptyList(),
                    nextPageNum = 1
                )

                // Save first page
                val pageId = repository.insertPage(
                    BookPageEntity(
                        bookId = bookId,
                        pageNumber = 1,
                        content = firstPageContent,
                        imageUri = selectedPageFileUri.value
                    )
                )

                if (firstPageContent.startsWith("خطأ:")) {
                    generationError.value = firstPageContent
                } else {
                    selectedPageFileUri.value = null
                    onSuccess(bookId)
                    showToast("تم إنشاء الكتاب وتوليد الصفحة الأولى!")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to create book", e)
                generationError.value = "حدث خطأ غير متوقع أثناء توليد فصل الكتاب: ${e.localizedMessage}"
            } finally {
                isGenerating.value = false
            }
        }
    }

    fun generateNextPageWithAI() {
        val currentBookWithPages = _activeBookWithPages.value ?: return
        val book = currentBookWithPages.book
        val pages = currentBookWithPages.pages.sortedBy { it.pageNumber }
        val nextPageNumber = (pages.lastOrNull()?.pageNumber ?: 0) + 1

        if (nextPageNumber > book.totalPagesGoal) {
            showToast("لقد وصلت إلى حد الصفحات الأقصى المطلوب لهذا الكتاب وهو ${book.totalPagesGoal} صفحة")
            return
        }

        viewModelScope.launch {
            isGenerating.value = true
            generationError.value = null
            try {
                val nextContent = GeminiService.generateNextPage(
                    book = book,
                    existingPages = pages,
                    nextPageNum = nextPageNumber
                )

                if (nextContent.startsWith("خطأ:")) {
                    generationError.value = nextContent
                } else {
                    repository.insertPage(
                        BookPageEntity(
                            bookId = book.id,
                            pageNumber = nextPageNumber,
                            content = nextContent,
                            imageUri = selectedPageFileUri.value
                        )
                    )
                    selectedPageFileUri.value = null // reset image uri holder
                    showToast("تم كتابة الصفحة رقم $nextPageNumber بنجاح!")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate next page", e)
                generationError.value = "تعذر كتابة الصفحة التالية: ${e.localizedMessage}"
            } finally {
                isGenerating.value = false
            }
        }
    }

    fun editPageContent(pageEntity: BookPageEntity, newContent: String) {
        viewModelScope.launch {
            repository.updatePage(pageEntity.copy(content = newContent))
            showToast("تم تحديث الصفحة بنجاح")
        }
    }

    fun exportActiveBookToPdf(context: Context, outputStream: OutputStream) {
        val currentBookWithPages = _activeBookWithPages.value
        if (currentBookWithPages == null) {
            showToast("يرجى اختيار كتاب أولاً لتصديره")
            return
        }

        viewModelScope.launch {
            val success = PdfExporter.exportBookToPdf(context, currentBookWithPages, outputStream)
            if (success) {
                showToast("تم تصدير الكتاب وحفظ الملف PDF بنجاح!")
            } else {
                showToast("حدث خطأ أثناء تصدير الكتاب إلى ملف PDF")
            }
        }
    }

    fun showToast(message: String) {
        toastMessage.value = message
    }

    fun clearToast() {
        toastMessage.value = null
    }

    class Factory(private val application: Application, private val repository: BookRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(BookViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return BookViewModel(application, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
