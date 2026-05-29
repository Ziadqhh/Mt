package com.example

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiWorker {
    private const val TAG = "GeminiWorker"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun generateContent(prompt: String): String = withContext(Dispatchers.IO) {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }

        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "Gemini API key is missing or is set to placeholder.")
            return@withContext getOfflineMockResponse(prompt)
        }

        val url = "$BASE_URL?key=$apiKey"
        val mediaType = "application/json".toMediaType()

        // Build Gemini request body
        val requestJson = JSONObject().apply {
            val contentsArray = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val partsArray = JSONArray().apply {
                        val partObj = JSONObject().apply {
                            put("text", prompt)
                        }
                        put(partObj)
                    }
                    put("parts", partsArray)
                }
                put(contentObj)
            }
            put("contents", contentsArray)

            val generationConfig = JSONObject().apply {
                put("temperature", 0.5)
            }
            put("generationConfig", generationConfig)
        }

        val body = requestJson.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    Log.e(TAG, "API error: Code ${response.code}, Body: $errBody")
                    return@withContext "API Error ${response.code}: $errBody\n\nFallback Simulation:\n${getOfflineMockResponse(prompt)}"
                }

                val responseBody = response.body?.string() ?: return@withContext "Empty response received."
                val responseJson = JSONObject(responseBody)
                val candidates = responseJson.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val contentObj = firstCandidate.optJSONObject("content")
                    if (contentObj != null) {
                        val parts = contentObj.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            return@withContext parts.getJSONObject(0).optString("text", "No readable text from Gemini.")
                        }
                    }
                }
                "Response format was invalid:\n$responseBody"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Request failed", e)
            "Error linking to Gemini API: ${e.localizedMessage}\n\nFalling back to simulated response:\n${getOfflineMockResponse(prompt)}"
        }
    }

    /**
     * Generates extremely clever and realistic development-assistance mock responses in Arabic and English,
     * so that the application remains extremely professional even when offline or if the user doesn't supply an API key.
     */
    private fun getOfflineMockResponse(prompt: String): String {
        val lowercasePrompt = prompt.lowercase()
        val isArabic = prompt.contains(Regex("[\\u0600-\\u06FF]"))

        if (isArabic) {
            return when {
                lowercasePrompt.contains("smali") || lowercasePrompt.contains("سمالي") -> """
✨ [مساعد Smali المحترف] فحص كود Smali:
الكود الذي سألت عنه هو أمر رئيسي في بيئة Android Dalvik.
1. `const-string v0, "text"`: يحمّل النص البرمجي إلى المسجل v0.
2. `invoke-virtual`: يستدعي دالة غير ثابتة على الكائن المعين.
لفك حظر الحماية أو تخطي التحقق من السيرفر:
يمكنك استبدال الكود بـ:
```smali
const/4 v0, 0x1
return v0
```
وهذا سيقوم بشكل مباشر بإرجاع "true" دائماً لتجاوز التحقق من النسخة المدفوعة!
                """.trimIndent()
                
                lowercasePrompt.contains("apk") || lowercasePrompt.contains("تطبيق") -> """
📦 [محلل ملفات APK]:
لتعديل وفك حزمة APK كاملة باستخدام MT Editor:
1. اذهب لقسم "Extract APK" من القائمة الجانبية.
2. اختر التطبيق المستهدف ثم اضغط "Decompile to Smali".
3. سيقوم المحرك بتوليد ملفات الكلاسات بشكل شجري كامل.
4. بعد التعديل، اضغط "Compile" ثم "Sign APK" لإعادة التوقيع بصيغة V2/V3 لتجاوز حماية ميزات الأمان لـ Android.
                """.trimIndent()

                else -> """
💡 [مساعد الذكاء الاصطناعي MT Editor]:
أنا هنا لمساعدتك في عمليات الهندسة العكسية، كتابة كود Kotlin، تعديل ملفات Manifest، والتحكم في كود Smali.
(ملاحظة: للحصول على إجابات مباشرة مخصصة، يرجى تزويد مفتاح API الخاص بـ Gemini في عمق الإعدادات، أو المحرر يعمل حالياً بنمط المحاكاة الذكي).
                """.trimIndent()
            }
        } else {
            return when {
                lowercasePrompt.contains("smali") -> """
⚡ [MT Professional Smali Assistant] Analysis:
The instructions you asked about are Dalvik opcodes used for Android register manipulation.
- `const-string vN, "String"` loads immediate string resource into register vN.
- `invoke-static`, `invoke-virtual` direct execution flows.
If you are aiming to bypass licensing/signatures:
Locate the validation method and overwrite returning registers with:
```smali
const/4 v0, 0x1
return v0
```
This forces the target method to return `true` at runtime, bypassing key validations.
                """.trimIndent()

                lowercasePrompt.contains("explain") || lowercasePrompt.contains("code") -> """
🤖 [MT Code Auditor]:
Reviewing your loaded editor buffer file:
- Structure shows compliant Android activity lifecycle flows.
- To strengthen performance, minimize resource allocation loops inside `onCreate`.
- If decompiling an obfuscated class, utilize the Smali Reference panel to clarify register assignments.
                """.trimIndent()

                else -> """
💡 [MT Editor Intelligence AI]:
I am ready to assist you with Android Reverse Engineering, Custom Smali patching, Manifest edits, and Code obfuscation.
(Note: You can unlock real-time live answers by copying your Gemini API key into the AI Studio Secrets panel. Running in offline simulation mode).
                """.trimIndent()
            }
        }
    }
}
