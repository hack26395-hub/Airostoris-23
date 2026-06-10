package com.example.data

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object GeminiService {
    private const val TAG = "GeminiService"
    
    // Configured with 60-second timeouts as mandated by the Gemini skill guidelines
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
    private const val DEFAULT_MODEL = "gemini-3.5-flash"

    suspend fun generateNextPage(
        book: BookEntity,
        existingPages: List<BookPageEntity>,
        nextPageNum: Int
    ): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "Gemini API Key is missing or placeholders used.")
            return@withContext "خطأ: لم يتم ضبط مفتاح الذكاء الاصطناعي في إعدادات التطبيق. يرجى تهيئة GEMINI_API_KEY في لوحة الأسرار (Secrets)."
        }

        // Build the system instructions
        val styleInstruction = if (book.hasLiteraryTouches) {
            "أضف لمسات أدبية احترافية عميقة ومجازات بلاغية راقية وأسلوب روائي ممتاز ومثير للاهتمام."
        } else {
            "اكتب بأسلوب بسيط ومباشر وسهل القراءة."
        }

        val punctuationInstruction = """
            هام جداً وحرج للغاية: 
            يجب أن يكون النص خالياً تماماً من أي علامات تنقيط أو رموز ترقيم (مثل علامات الاستفهام '؟'، علامات التعجب '!'، النقطتين الكأسيتين ':'، الفاصلة المنقوطة '؛'، الأقواس، علامات التنصيص، الشرطة، إلخ) 
            باستثناء الفاصلة (',') والنقطة ('.') فقط لا غير. لا تستخدم أي رمز تنقيط وتعبير آخر على الإطلاق.
        """.trimIndent()

        val seriesInstruction = if (book.seriesName.isNotEmpty()) {
            "هذا الكتاب جزء من سلسلة بعنوان '${book.seriesName}' وهو '${book.seriesPart}'."
        } else ""

        val systemPrompt = """
            أنت كاتب ومؤلف روائي محترف وخبير باللغة ${book.language}. 
            مهمتك هي كتابة الصفحة رقم $nextPageNum من رواية بعنوان '${book.title}'.
            تصنيف الرواية: ${book.genre}.
            فكرة الرواية الأساسية هي: ${book.idea}.
            $styleInstruction
            $seriesInstruction
            $punctuationInstruction
            
            يُرجى صياغة الصفحة بحيث تتكون من حوالي ${book.linesPerPage} أسطر فقط. 
            تجنب كتابة عناوين الصفحات، أو كلمة 'الصفحة $nextPageNum'، أو أي نصوص فرعية غريبة. ابدأ بكتابة المحتوى الروائي مباشرة.
        """.trimIndent()

        // Build user prompt detailing history
        val userPromptBuilder = StringBuilder()
        if (nextPageNum > 1) {
            userPromptBuilder.append("هذه هي الصفحة رقم $nextPageNum. لمتابعة قصة الرواية بتناسق تام وبدون انقطاع، إليك الصفحات السابقة المكتوبة حتى الآن:\n\n")
            // Take the last 3-4 pages to avoid token explosion but keep strong local context
            val contextPages = existingPages.sortedBy { it.pageNumber }.takeLast(4)
            for (p in contextPages) {
                userPromptBuilder.append("[الصفحة ${p.pageNumber}]:\n${p.content}\n\n")
            }
            userPromptBuilder.append("تنبيه حاسم: واصل كتابة أحداث القصة للصفحة رقم $nextPageNum مباشرة من حيث انتهت الصفحة السابقة بشكل مستمر وبأسلوب روائي محكم، ملتزماً تماماً بشرط علامات التنقيط (الفاصلة والنقطة فقط).")
        } else {
            userPromptBuilder.append("ابدأ بكتابة الصفحة الأولى (الصفحة 1) لهذه الرواية الآن بناءً على الإرشادات المذكورة.")
        }

        val url = "$BASE_URL/$DEFAULT_MODEL:generateContent?key=$apiKey"

        try {
            // Build direct JSON payload
            val contentsArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", userPromptBuilder.toString())
                        })
                    })
                })
            }

            val requestJson = JSONObject().apply {
                put("contents", contentsArray)
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", systemPrompt)
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.75)
                })
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = requestJson.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    Log.e(TAG, "API call failed code: ${response.code}, body: $errBody")
                    return@withContext "خطأ في الاتصال بالذكاء الاصطناعي (كود ${response.code}). يرجى التأكد من صحة مفتاح API وصلاحية الاتصال بالإنترنت."
                }

                val responseBodyStr = response.body?.string() ?: ""
                val responseJson = JSONObject(responseBodyStr)
                
                val candidates = responseJson.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val contentObj = firstCandidate.optJSONObject("content")
                    if (contentObj != null) {
                        val parts = contentObj.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            var text = parts.getJSONObject(0).optString("text") ?: ""
                            
                            // Post-process response to strictly guarantee no unrequested punctuation slips in
                            text = sanitizePunctuation(text)
                            return@withContext text
                        }
                    }
                }
                
                return@withContext "عذرًا، لم يتمكن الذكاء الاصطناعي من توليد نص لهذه الصفحة. الرجاء إعادة المحاولة."
            }

        } catch (e: IOException) {
            Log.e(TAG, "Network connection error", e)
            return@withContext "خطأ في الاتصال بالإنترنت: يرجى التحقق من اتصال الشبكة وإعادة المحاولة."
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during generation", e)
            return@withContext "حدث خطأ غير متوقع: ${e.localizedMessage ?: "يرجى المحاولة مجدداً."}"
        }
    }

    /**
     * Sanitizes the text by replacing forbidden punctuation with spaces or periods,
     * maintaining only full stops (Arabic and Western) and commas (Arabic and Western).
     */
    private fun sanitizePunctuation(text: String): String {
        // Arabic quote marks, western quotes, semicolons, colons, exclamation marks, question marks, dashes.
        // We only allow:
        // Period: '.' or '۔'
        // Comma: ',' or '،'
        // Let's replace:
        // '!' -> '.'
        // '?' or '؟' -> '.' or ',' depending on context, let's change to '.' for sentence end, or ','
        // ':' or ';' or '؛' -> ','
        // '"' or '\'' or '`' or '/' -> '' (empty string)
        
        return text
            .replace("!", ".")
            .replace("؟", ".")
            .replace("?", ".")
            .replace(":", ",")
            .replace(";", ",")
            .replace("؛", "،")
            .replace("\"", "")
            .replace("'", "")
            .replace("`", "")
            .replace("«", "")
            .replace("»", "")
            .replace("—", " ")
            .replace("-", " ")
            .replace("[", " ")
            .replace("]", " ")
            .replace("*", "") // Remove double stars from markdown formatting of text
    }
}
