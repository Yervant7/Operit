package com.ai.assistance.operit.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.annotation.WorkerThread
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** OCR工具类，封装ML Kit的文本识别功能 */
object OCRUtils {
    private const val TAG = "OCRUtils"

    // 识别器缓存
    private var latinRecognizer: TextRecognizer? = null
    private var chineseRecognizer: TextRecognizer? = null
    private var japaneseRecognizer: TextRecognizer? = null
    private var koreanRecognizer: TextRecognizer? = null

    /** 文本识别语言选项 */
    enum class Language {
        LATIN, // 拉丁语系（英文、法文、德文等）
        CHINESE, // 中文
        JAPANESE, // 日文
        KOREAN // 韩文
    }

    /** 初始化识别器 */
    private fun getRecognizer(language: Language): TextRecognizer {
        return when (language) {
            Language.LATIN -> {
                if (latinRecognizer == null) {
                    latinRecognizer =
                            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                }
                latinRecognizer!!
            }
            Language.CHINESE -> {
                if (chineseRecognizer == null) {
                    chineseRecognizer =
                            TextRecognition.getClient(
                                    ChineseTextRecognizerOptions.Builder().build()
                            )
                }
                chineseRecognizer!!
            }
            Language.JAPANESE -> {
                if (japaneseRecognizer == null) {
                    japaneseRecognizer =
                            TextRecognition.getClient(
                                    JapaneseTextRecognizerOptions.Builder().build()
                            )
                }
                japaneseRecognizer!!
            }
            Language.KOREAN -> {
                if (koreanRecognizer == null) {
                    koreanRecognizer =
                            TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
                }
                koreanRecognizer!!
            }
        }
    }

    // --------- 低级API：直接返回OCRResult结果 ---------//

    /**
     * 从Bitmap识别文本（同步方法）
     *
     * @param bitmap 要识别的图像
     * @param language 识别语言
     * @return 识别结果
     */
    @WorkerThread
    suspend fun recognizeTextFromBitmap(
            bitmap: Bitmap,
            language: Language = Language.LATIN
    ): OCRResult {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = processImage(image, language)
            OCRResult.Success(result)
        } catch (e: Exception) {
            Log.e(TAG, "Error recognizing text from bitmap: ${e.message}", e)
            OCRResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * 从Uri识别文本（同步方法）
     *
     * @param context 上下文
     * @param uri 图像的Uri
     * @param language 识别语言
     * @return 识别结果
     */
    @WorkerThread
    suspend fun recognizeTextFromUri(
            context: Context,
            uri: Uri,
            language: Language = Language.LATIN
    ): OCRResult {
        return try {
            val image = InputImage.fromFilePath(context, uri)
            val result = processImage(image, language)
            OCRResult.Success(result)
        } catch (e: IOException) {
            Log.e(TAG, "Error reading image: ${e.message}", e)
            OCRResult.Error("无法读取图像文件: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error recognizing text from uri: ${e.message}", e)
            OCRResult.Error(e.message ?: "Unknown error")
        }
    }

    /** 处理图像并识别文本 */
    private suspend fun processImage(image: InputImage, language: Language): Text =
            suspendCancellableCoroutine { continuation ->
                val recognizer = getRecognizer(language)
                recognizer
                        .process(image)
                        .addOnSuccessListener { text -> continuation.resume(text) }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Text recognition failed: ${e.message}", e)
                            continuation.resumeWithException(e)
                        }
            }

    // --------- 高级API：简化的接口，直接返回文本字符串 ---------//

    /**
     * 识别图像中的文本（同时识别拉丁文和中文）
     *
     * @param context 上下文
     * @param bitmap 图像
     * @return 识别到的文本，如果失败则返回空字符串
     */
    @WorkerThread
    suspend fun recognizeText(context: Context, bitmap: Bitmap): String {
        // 同时进行拉丁文和中文识别
        val latinResult = recognizeTextFromBitmap(bitmap, Language.LATIN)
        val chineseResult = recognizeTextFromBitmap(bitmap, Language.CHINESE)
        
        val latinText = if (latinResult is OCRResult.Success) latinResult.getFullText() else ""
        val chineseText = if (chineseResult is OCRResult.Success) chineseResult.getFullText() else ""
        
        // 合并结果，如果两种结果相同或其中一个为空，则直接返回非空结果
        return when {
            latinText.isEmpty() -> chineseText
            chineseText.isEmpty() -> latinText
            latinText == chineseText -> latinText
            else -> "$latinText\n$chineseText" // 不同结果合并返回
        }
    }

    /**
     * 识别图像中的文本（指定语言）
     *
     * @param context 上下文
     * @param bitmap 图像
     * @param language 语言
     * @return 识别到的文本，如果失败则返回空字符串
     */
    @WorkerThread
    suspend fun recognizeText(context: Context, bitmap: Bitmap, language: Language): String {
        val result = recognizeTextFromBitmap(bitmap, language)
        return when (result) {
            is OCRResult.Success -> result.getFullText()
            is OCRResult.Error -> {
                Log.e(TAG, "Text recognition failed: ${result.message}")
                ""
            }
        }
    }

    /**
     * 从文件Uri识别文本（同时识别拉丁文和中文）
     *
     * @param context 上下文
     * @param uri 图像Uri
     * @return 识别到的文本，如果失败则返回空字符串
     */
    @WorkerThread
    suspend fun recognizeText(context: Context, uri: Uri): String {
        // 同时进行拉丁文和中文识别
        val latinResult = recognizeTextFromUri(context, uri, Language.LATIN)
        val chineseResult = recognizeTextFromUri(context, uri, Language.CHINESE)
        
        val latinText = if (latinResult is OCRResult.Success) latinResult.getFullText() else ""
        val chineseText = if (chineseResult is OCRResult.Success) chineseResult.getFullText() else ""
        
        // 合并结果，如果两种结果相同或其中一个为空，则直接返回非空结果
        return when {
            latinText.isEmpty() -> chineseText
            chineseText.isEmpty() -> latinText
            latinText == chineseText -> latinText
            else -> "$latinText\n$chineseText" // 不同结果合并返回
        }
    }

    /**
     * 扫描并提取图像中所有的文本块
     *
     * @param bitmap 要识别的图像
     * @param languages 要尝试的语言列表，按优先级排序
     * @return 识别到的所有文本块列表
     */
    @WorkerThread
    suspend fun extractTextBlocks(
            bitmap: Bitmap,
            languages: List<Language> = listOf(Language.LATIN, Language.CHINESE)
    ): List<String> {
        val textBlocks = mutableListOf<String>()

        for (language in languages) {
            val result = recognizeTextFromBitmap(bitmap, language)
            if (result is OCRResult.Success) {
                result.getTextBlocks().forEach { block -> textBlocks.add(block.text) }
                // 如果有结果，就不需要继续尝试其他语言
                if (textBlocks.isNotEmpty()) {
                    break
                }
            }
        }

        return textBlocks
    }

    /**
     * 保存Bitmap到临时文件，用于OCR处理
     *
     * @param context 上下文
     * @param bitmap 位图
     * @return 临时文件
     */
    @WorkerThread
    suspend fun saveBitmapToTempFile(context: Context, bitmap: Bitmap): File? =
            withContext(Dispatchers.IO) {
                val cacheDir = context.cacheDir
                val tempFile = File(cacheDir, "ocr_temp_${System.currentTimeMillis()}.jpg")

                try {
                    FileOutputStream(tempFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                    }
                    return@withContext tempFile
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to save bitmap to temp file", e)
                    return@withContext null
                }
            }

    /** 清理资源 */
    fun closeRecognizers() {
        latinRecognizer?.close()
        latinRecognizer = null

        chineseRecognizer?.close()
        chineseRecognizer = null

        japaneseRecognizer?.close()
        japaneseRecognizer = null

        koreanRecognizer?.close()
        koreanRecognizer = null
    }

    /** OCR识别结果 */
    sealed class OCRResult {
        /** 识别成功 */
        data class Success(val text: Text) : OCRResult() {
            /** 获取识别到的完整文本 */
            fun getFullText(): String = text.text

            /** 获取所有文本块 */
            fun getTextBlocks(): List<Text.TextBlock> = text.textBlocks

            /** 获取结构化文本信息 */
            fun getStructuredText(): String {
                val sb = StringBuilder()
                for (block in text.textBlocks) {
                    for (line in block.lines) {
                        sb.append(line.text).append("\n")
                    }
                    sb.append("\n")
                }
                return sb.toString().trim()
            }
        }

        /** 识别失败 */
        data class Error(val message: String) : OCRResult()
    }
}
