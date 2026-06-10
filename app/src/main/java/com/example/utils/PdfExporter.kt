package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.Log
import com.example.data.BookWithPages
import java.io.InputStream
import java.io.OutputStream

object PdfExporter {
    private const val TAG = "PdfExporter"
    
    // Standard A4 paper size in postscript points (1/72 inch). 
    // 595 x 842 points.
    private const val A4_WIDTH = 595
    private const val A4_HEIGHT = 842

    fun exportBookToPdf(
        context: Context,
        bookWithPages: BookWithPages,
        outputStream: OutputStream
    ): Boolean {
        val book = bookWithPages.book
        val pages = bookWithPages.pages.sortedBy { it.pageNumber }

        val pdfDocument = PdfDocument()

        val contentResolver = context.contentResolver

        try {
            // ==========================================
            // PAGE 1: COVER PAGE
            // ==========================================
            val coverPageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, 1).create()
            val coverPage = pdfDocument.startPage(coverPageInfo)
            val coverCanvas = coverPage.canvas

            // Fill background with a very light luxury cream/slate
            coverCanvas.drawColor(Color.parseColor("#FAF8F5"))

            // Draw clean neon pink & neon cyan structural design lines
            val neonCyanPaint = Paint().apply {
                color = Color.parseColor("#00E5FF")
                strokeWidth = 4f
                style = Paint.Style.STROKE
                isAntiAlias = true
            }
            val neonPinkPaint = Paint().apply {
                color = Color.parseColor("#FF007F")
                strokeWidth = 2f
                style = Paint.Style.STROKE
                isAntiAlias = true
            }

            // Outer elegant border
            coverCanvas.drawRect(20f, 20f, A4_WIDTH - 20f, A4_HEIGHT - 20f, neonCyanPaint)
            coverCanvas.drawRect(24f, 24f, A4_WIDTH - 24f, A4_HEIGHT - 24f, neonPinkPaint)

            // Title Typography
            val titlePaint = Paint().apply {
                color = Color.parseColor("#121212")
                textSize = 32f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }

            // Genre Tag Typography
            val tagPaint = Paint().apply {
                color = Color.parseColor("#FF007F")
                textSize = 14f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }

            // Series Details Cover
            val seriesPaint = Paint().apply {
                color = Color.parseColor("#555555")
                textSize = 16f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }

            // App Brand watermark
            val brandPaint = Paint().apply {
                color = Color.parseColor("#888888")
                textSize = 12f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }

            // Write Title
            coverCanvas.drawText(book.title, (A4_WIDTH / 2).toFloat(), 180f, titlePaint)

            // Write Series (if exists)
            val seriesText = if (book.seriesName.isNotEmpty()) {
                "${book.seriesName} — ${book.seriesPart}"
            } else {
                "رواية أدبيّة خاصّة"
            }
            coverCanvas.drawText(seriesText, (A4_WIDTH / 2).toFloat(), 220f, seriesPaint)

            // Write Genre
            coverCanvas.drawText("التصنيف: ${book.genre}", (A4_WIDTH / 2).toFloat(), 250f, tagPaint)

            // Load and draw Cover Image if present (scale centered)
            var coverImageBitmap: Bitmap? = null
            if (!book.coverImageUri.isNullOrEmpty()) {
                try {
                    val uri = Uri.parse(book.coverImageUri)
                    val inputStream: InputStream? = contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        coverImageBitmap = BitmapFactory.decodeStream(inputStream)
                        inputStream.close()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load cover image Bitmap", e)
                }
            }

            if (coverImageBitmap != null) {
                // Resize and center cover image
                val maxImgWidth = 280
                val maxImgHeight = 350
                val scaledBitmap = scaleBitmapToFit(coverImageBitmap, maxImgWidth, maxImgHeight)
                
                val startX = (A4_WIDTH - scaledBitmap.width) / 2f
                val startY = 300f + (maxImgHeight - scaledBitmap.height) / 2f

                // Draw a neon shadow box around the photo
                val imgBorderPaint = Paint().apply {
                    color = Color.parseColor("#00E5FF")
                    strokeWidth = 3f
                    style = Paint.Style.STROKE
                }
                coverCanvas.drawRect(
                    startX - 3,
                    startY - 3,
                    startX + scaledBitmap.width + 3,
                    startY + scaledBitmap.height + 3,
                    imgBorderPaint
                )
                coverCanvas.drawBitmap(scaledBitmap, startX, startY, null)
            } else {
                // Fallback decorative neon illustration if no book cover attached
                val decorPaint = Paint().apply {
                    color = Color.parseColor("#E0E0E0")
                    strokeWidth = 1f
                    style = Paint.Style.STROKE
                    isAntiAlias = true
                }
                coverCanvas.drawCircle((A4_WIDTH / 2).toFloat(), 460f, 100f, decorPaint)
                coverCanvas.drawCircle((A4_WIDTH / 2).toFloat(), 460f, 80f, decorPaint)
                
                val centerIconPaint = Paint().apply {
                    color = Color.parseColor("#FF007F")
                    textSize = 40f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                coverCanvas.drawText("📖", (A4_WIDTH / 2).toFloat(), 475f, centerIconPaint)
            }

            // Story Synopsis
            val synoTitlePaint = Paint().apply {
                color = Color.parseColor("#333333")
                textSize = 13f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
                textAlign = Paint.Align.RIGHT
            }
            val synoTextPaint = Paint().apply {
                color = Color.parseColor("#555555")
                textSize = 11f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
            }

            val synoYStart = 700f
            coverCanvas.drawText("فكرة وعقدة الرواية:", (A4_WIDTH - 50).toFloat(), synoYStart, synoTitlePaint)
            val wrappedIdea = wrapText(book.idea, synoTextPaint, (A4_WIDTH - 100).toFloat())
            var currentY = synoYStart + 20f
            for (line in wrappedIdea.take(3)) {
                // If arabic or other text, right align it
                val xPos = if (book.language.contains("عرب", true) || book.language.contains("arab", true)) {
                    (A4_WIDTH - 50) - synoTextPaint.measureText(line)
                } else {
                    50f
                }
                coverCanvas.drawText(line, xPos, currentY, synoTextPaint)
                currentY += 16f
            }

            // Write Brand logo at the absolute bottom
            coverCanvas.drawText("AIROSTORIS  •  تأليف ذكي متكامل", (A4_WIDTH / 2).toFloat(), (A4_HEIGHT - 45).toFloat(), brandPaint)

            pdfDocument.finishPage(coverPage)

            // ==========================================
            // SUBSEQUENT PAGES: NOVEL PAGES
            // ==========================================
            val textPaint = Paint().apply {
                color = Color.parseColor("#1E1E1E")
                textSize = 13f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
            }

            val pageHeaderPaint = Paint().apply {
                color = Color.parseColor("#888888")
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
            }

            val pageFooterPaint = Paint().apply {
                color = Color.parseColor("#444444")
                textSize = 11f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }

            for ((index, pageEntity) in pages.withIndex()) {
                val pNum = pageEntity.pageNumber
                val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, index + 2).create()
                val pdfPage = pdfDocument.startPage(pageInfo)
                val canvas = pdfPage.canvas

                // Fill clean page background
                canvas.drawColor(Color.parseColor("#FFFFFF"))

                // Top decorative thin line & header text
                val textDirRight = book.language.contains("عرب", true) || book.language.contains("arab", true)
                val headerText = "${book.title} - الصفحة $pNum"
                
                val headerX = if (textDirRight) {
                    (A4_WIDTH - 45) - pageHeaderPaint.measureText(headerText)
                } else {
                    45f
                }
                canvas.drawText(headerText, headerX, 40f, pageHeaderPaint)

                val linePaint = Paint().apply {
                    color = Color.parseColor("#E5E5E5")
                    strokeWidth = 1f
                }
                canvas.drawLine(40f, 48f, (A4_WIDTH - 40).toFloat(), 48f, linePaint)

                // Layout control
                var nextLineY = 80f
                val wrapWidth = (A4_WIDTH - 90).toFloat() // margins of 45 on each side

                // If user attached an image to this page, draw it!
                var pageImgBitmap: Bitmap? = null
                if (!pageEntity.imageUri.isNullOrEmpty()) {
                    try {
                        val uri = Uri.parse(pageEntity.imageUri)
                        val inputStream: InputStream? = contentResolver.openInputStream(uri)
                        if (inputStream != null) {
                            pageImgBitmap = BitmapFactory.decodeStream(inputStream)
                            inputStream.close()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load page image Bitmap", e)
                    }
                }

                if (pageImgBitmap != null) {
                    // Resize to fit beautifully on top half of page
                    val scaledPageBitmap = scaleBitmapToFit(pageImgBitmap, 320, 220)
                    val startX = (A4_WIDTH - scaledPageBitmap.width) / 2f
                    
                    canvas.drawBitmap(scaledPageBitmap, startX, nextLineY, null)
                    
                    val borderP = Paint().apply {
                        color = Color.parseColor("#FF007F")
                        strokeWidth = 1.5f
                        style = Paint.Style.STROKE
                    }
                    canvas.drawRect(
                        startX - 1,
                        nextLineY - 1,
                        startX + scaledPageBitmap.width + 1,
                        nextLineY + scaledPageBitmap.height + 1,
                        borderP
                    )
                    
                    nextLineY += scaledPageBitmap.height + 30f // Spacer
                }

                // Write text content (Wrap beautifully)
                val lines = wrapText(pageEntity.content, textPaint, wrapWidth)
                
                // Line spacing
                val lineSpacing = 20f
                for (line in lines) {
                    if (nextLineY > A4_HEIGHT - 80) {
                        // Prevent writing beyond footer
                        break
                    }
                    val xPos = if (textDirRight) {
                        (A4_WIDTH - 45) - textPaint.measureText(line)
                    } else {
                        45f
                    }
                    canvas.drawText(line, xPos, nextLineY, textPaint)
                    nextLineY += lineSpacing
                }

                // Footer page numbering
                canvas.drawText("- $pNum -", (A4_WIDTH / 2).toFloat(), (A4_HEIGHT - 40).toFloat(), pageFooterPaint)

                pdfDocument.finishPage(pdfPage)
            }

            // Write PDF file
            pdfDocument.writeTo(outputStream)
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Unrecoverable PDF creation error", e)
            return false
        } finally {
            pdfDocument.close()
        }
    }

    private fun scaleBitmapToFit(original: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = original.width
        val height = original.height

        val ratioBitmap = width.toFloat() / height.toFloat()
        val ratioMax = maxWidth.toFloat() / maxHeight.toFloat()

        var finalWidth = maxWidth
        var finalHeight = maxHeight

        if (ratioMax > ratioBitmap) {
            finalWidth = (maxHeight.toFloat() * ratioBitmap).toInt()
        } else {
            finalHeight = (maxWidth.toFloat() / ratioBitmap).toInt()
        }

        return Bitmap.createScaledBitmap(original, finalWidth, finalHeight, true)
    }

    fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val results = mutableListOf<String>()
        // Split by markdown newlines or normal newlines safely
        val paragraphs = text.split("\n")
        
        for (paragraph in paragraphs) {
            if (paragraph.trim().isEmpty()) {
                results.add("") // Double spacing paragraphs
                continue
            }
            
            val words = paragraph.split("\\s+".toRegex())
            var currentLine = StringBuilder()
            
            for (word in words) {
                if (word.isEmpty()) continue
                
                val testLine = if (currentLine.isEmpty()) word else "${currentLine} $word"
                val width = paint.measureText(testLine)
                
                if (width <= maxWidth) {
                    currentLine.append(if (currentLine.isEmpty()) word else " $word")
                } else {
                    results.add(currentLine.toString())
                    currentLine = StringBuilder(word)
                }
            }
            
            if (currentLine.isNotEmpty()) {
                results.add(currentLine.toString())
            }
        }
        return results
    }
}
