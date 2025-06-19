package com.ai.assistance.operit.ui.features.chat.attachments

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.AttachmentInfo
import com.ai.assistance.operit.data.model.ToolParameter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * Manages attachment operations for the chat feature Handles adding, removing, and referencing
 * attachments
 */
class AttachmentManager(private val context: Context, private val toolHandler: AIToolHandler) {
    companion object {
        private const val TAG = "AttachmentManager"
    }

    // State for attachments
    private val _attachments = MutableStateFlow<List<AttachmentInfo>>(emptyList())
    val attachments: StateFlow<List<AttachmentInfo>> = _attachments

    // Events
    private val _toastEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastEvent: SharedFlow<String> = _toastEvent

    /**
     * Inserts a reference to an attachment at the current cursor position in the user's message
     * @return the formatted reference string
     */
    fun createAttachmentReference(attachment: AttachmentInfo): String {
        // Generate XML reference for the attachment
        val attachmentRef = StringBuilder("<attachment ")
        attachmentRef.append("id=\"${attachment.filePath}\" ")
        attachmentRef.append("filename=\"${attachment.fileName}\" ")
        attachmentRef.append("type=\"${attachment.mimeType}\" ")

        // Add size property
        if (attachment.fileSize > 0) {
            attachmentRef.append("size=\"${attachment.fileSize}\" ")
        }

        // Add content property (if exists)
        if (attachment.content.isNotEmpty()) {
            attachmentRef.append("content=\"${attachment.content}\" ")
        }

        attachmentRef.append("/>")

        return attachmentRef.toString()
    }

    /** Handles a file or image attachment selected by the user 确保在IO线程执行所有文件操作 */
    suspend fun handleAttachment(filePath: String) =
            withContext(Dispatchers.IO) {
                try {
                    // 检查是否是媒体选择器特殊路径
                    if (filePath.contains("/sdcard/.transforms/synthetic/picker/") ||
                                    filePath.contains("/com.android.providers.media.photopicker/")
                    ) {
                        Log.d(TAG, "检测到媒体选择器特殊路径: $filePath")

                        try {
                            // 尝试从特殊路径提取实际URI
                            val actualUri = extractMediaStoreUri(filePath)
                            if (actualUri != null) {
                                // 使用提取出的URI创建临时文件
                                val fileName = filePath.substringAfterLast('/')
                                val tempFile = createTempFileFromUri(actualUri, fileName)

                                if (tempFile != null) {
                                    Log.d(TAG, "成功从媒体选择器路径创建临时文件: ${tempFile.absolutePath}")

                                    // 创建附件对象
                                    val mimeType =
                                            getMimeTypeFromPath(tempFile.name) ?: "image/jpeg"
                                    val attachmentInfo =
                                            AttachmentInfo(
                                                    filePath = tempFile.absolutePath,
                                                    fileName = fileName,
                                                    mimeType = mimeType,
                                                    fileSize = tempFile.length()
                                            )

                                    // 添加到附件列表
                                    val currentList = _attachments.value
                                    if (!currentList.any { it.filePath == tempFile.absolutePath }) {
                                        _attachments.value = currentList + attachmentInfo
                                    }

                                    _toastEvent.emit("已添加附件: $fileName")
                                    return@withContext
                                } else {
                                    Log.e(TAG, "无法从媒体路径创建临时文件")
                                    _toastEvent.emit("无法处理媒体文件，请尝试其他方式")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "处理媒体选择器路径失败", e)
                            // 继续尝试常规处理方法
                        }
                    }

                    // Check if it's a content URI path
                    if (filePath.startsWith("content://")) {
                        // Handle as URI
                        val uri = Uri.parse(filePath)
                        val contentResolver = context.contentResolver

                        // Get file name from ContentResolver
                        val fileName = getFileNameFromUri(uri)

                        // Get file size from ContentResolver
                        val fileSize = getFileSizeFromUri(uri)

                        // Infer MIME type
                        val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"

                        // 检查是否是图片类型
                        val isImage = mimeType.startsWith("image/")

                        // 对于图片类型，转换为实际文件路径
                        val actualFilePath =
                                if (isImage) {
                                    // 创建一个临时文件来保存图片
                                    val tempFile = createTempFileFromUri(uri, fileName)
                                    tempFile?.absolutePath ?: filePath
                                } else {
                                    filePath // 非图片类型保留原始路径
                                }

                        val attachmentInfo =
                                AttachmentInfo(
                                        filePath = actualFilePath, // 对图片使用绝对路径
                                        fileName = fileName,
                                        mimeType = mimeType,
                                        fileSize = fileSize
                                )

                        // Add to attachment list
                        val currentList = _attachments.value
                        if (!currentList.any { it.filePath == actualFilePath }) {
                            _attachments.value = currentList + attachmentInfo
                        }

                        _toastEvent.emit("已添加附件: $fileName")
                    } else {
                        // Handle as regular file path
                        val file = java.io.File(filePath)
                        if (!file.exists()) {
                            _toastEvent.emit("文件不存在")
                            return@withContext
                        }

                        val fileName = file.name
                        val fileSize = file.length()
                        val mimeType = getMimeTypeFromPath(filePath) ?: "application/octet-stream"

                        // 图片文件使用绝对路径
                        val actualFilePath =
                                if (mimeType.startsWith("image/")) {
                                    file.absolutePath
                                } else {
                                    filePath
                                }

                        val attachmentInfo =
                                AttachmentInfo(
                                        filePath = actualFilePath,
                                        fileName = fileName,
                                        mimeType = mimeType,
                                        fileSize = fileSize
                                )

                        // Add to attachment list
                        val currentList = _attachments.value
                        if (!currentList.any { it.filePath == actualFilePath }) {
                            _attachments.value = currentList + attachmentInfo
                        }

                        _toastEvent.emit("已添加附件: $fileName")
                    }
                } catch (e: Exception) {
                    _toastEvent.emit("添加附件失败: ${e.message}")
                    Log.e(TAG, "添加附件错误", e)
                }
            }

    /** 从媒体选择器路径提取真实的MediaStore URI */
    private fun extractMediaStoreUri(filePath: String): Uri? {
        try {
            // 从文件名中提取媒体ID
            val mediaId = filePath.substringAfterLast('/').substringBefore('.')
            if (mediaId.toLongOrNull() != null) {
                // 构造MediaStore URI
                return Uri.parse("content://media/external/images/media/$mediaId")
            }

            // 尝试通过直接构造content URI
            if (filePath.contains("com.android.providers.media.photopicker")) {
                val path = "content://com.android.providers.media.photopicker/media/$mediaId"
                return Uri.parse(path)
            }

            // 最后尝试直接将路径转为URI
            return Uri.parse("file://$filePath")
        } catch (e: Exception) {
            Log.e(TAG, "提取媒体URI失败: $filePath", e)
            return null
        }
    }

    /** 从URI创建临时文件 */
    private suspend fun createTempFileFromUri(uri: Uri, fileName: String): java.io.File? =
            withContext(Dispatchers.IO) {
                try {
                    val fileExtension = fileName.substringAfterLast('.', "jpg")

                    // 使用外部存储Download/Operit/cleanOnExit目录，而不是缓存目录
                    val externalDir = java.io.File("/sdcard/Download/Operit/cleanOnExit")

                    // 确保目录存在
                    if (!externalDir.exists()) {
                        externalDir.mkdirs()
                    }

                    val tempFile =
                            java.io.File(
                                    externalDir,
                                    "img_${System.currentTimeMillis()}.$fileExtension"
                            )

                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    }

                    if (tempFile.exists() && tempFile.length() > 0) {
                        Log.d(TAG, "成功创建临时图片文件: ${tempFile.absolutePath}")
                        return@withContext tempFile
                    }

                    return@withContext null
                } catch (e: Exception) {
                    Log.e(TAG, "创建临时文件失败", e)
                    return@withContext null
                }
            }

    /** Removes an attachment by its file path */
    fun removeAttachment(filePath: String) {
        val currentList = _attachments.value
        _attachments.value = currentList.filter { it.filePath != filePath }
    }

    /** Clear all attachments */
    fun clearAttachments() {
        _attachments.value = emptyList()
    }

    /** Update attachments with a new list */
    fun updateAttachments(newAttachments: List<AttachmentInfo>) {
        _attachments.value = newAttachments
    }

    /**
     * Captures the current screen content and attaches it to the message Uses the get_page_info
     * AITool to retrieve UI structure 确保在IO线程中执行
     */
    suspend fun captureScreenContent() =
            withContext(Dispatchers.IO) {
                try {
                    // Create a tool to get page info
                    val pageInfoTool = AITool(name = "get_page_info", parameters = emptyList())

                    // Execute the tool
                    val result = toolHandler.executeTool(pageInfoTool)

                    if (result.success) {
                        // Generate a unique ID for this screen capture
                        val captureId = "screen_${System.currentTimeMillis()}"
                        val screenContent = result.result.toString()

                        // Create attachment info with content as filePath
                        val attachmentInfo =
                                AttachmentInfo(
                                        filePath = captureId, // Use ID as virtual path
                                        fileName = "screen_content.json",
                                        mimeType = "text/json",
                                        fileSize = screenContent.length.toLong(),
                                        content = screenContent // Add content field to store actual
                                        // data
                                        )

                        // Add to attachments list
                        val currentList = _attachments.value
                        _attachments.value = currentList + attachmentInfo

                        _toastEvent.emit("已添加屏幕内容")
                    } else {
                        _toastEvent.emit("获取屏幕内容失败: ${result.error ?: "未知错误"}")
                    }
                } catch (e: Exception) {
                    _toastEvent.emit("获取屏幕内容失败: ${e.message}")
                    Log.e(TAG, "Error capturing screen content", e)
                }
            }

    /** 获取设备当前通知并作为附件添加到消息 使用get_notifications AITool获取通知数据 确保在IO线程中执行 */
    suspend fun captureNotifications(limit: Int = 10) =
            withContext(Dispatchers.IO) {
                try {
                    // 创建工具参数
                    val toolParams =
                            listOf(
                                    ToolParameter("limit", limit.toString()),
                                    ToolParameter("include_ongoing", "true")
                            )

                    // 创建工具
                    val notificationsToolTask =
                            AITool(name = "get_notifications", parameters = toolParams)

                    // 执行工具
                    val result = toolHandler.executeTool(notificationsToolTask)

                    if (result.success) {
                        // 生成唯一ID
                        val captureId = "notifications_${System.currentTimeMillis()}"
                        val notificationsContent = result.result.toString()

                        // 创建附件信息
                        val attachmentInfo =
                                AttachmentInfo(
                                        filePath = captureId,
                                        fileName = "notifications.json",
                                        mimeType = "application/json",
                                        fileSize = notificationsContent.length.toLong(),
                                        content = notificationsContent
                                )

                        // 添加到附件列表
                        val currentList = _attachments.value
                        _attachments.value = currentList + attachmentInfo

                        _toastEvent.emit("已添加当前通知")
                    } else {
                        _toastEvent.emit("获取通知失败: ${result.error ?: "未知错误"}")
                    }
                } catch (e: Exception) {
                    _toastEvent.emit("获取通知失败: ${e.message}")
                    Log.e(TAG, "Error capturing notifications", e)
                }
            }

    /** 获取设备当前位置并作为附件添加到消息 使用get_device_location AITool获取位置数据 确保在IO线程中执行 */
    suspend fun captureLocation(highAccuracy: Boolean = true) =
            withContext(Dispatchers.IO) {
                try {
                    // 创建工具参数
                    val toolParams =
                            listOf(
                                    ToolParameter("high_accuracy", highAccuracy.toString()),
                                    ToolParameter("timeout", "10") // 10秒超时
                            )

                    // 创建工具
                    val locationToolTask =
                            AITool(name = "get_device_location", parameters = toolParams)

                    // 执行工具
                    val result = toolHandler.executeTool(locationToolTask)

                    if (result.success) {
                        // 生成唯一ID
                        val captureId = "location_${System.currentTimeMillis()}"
                        val locationContent = result.result.toString()

                        // 创建附件信息
                        val attachmentInfo =
                                AttachmentInfo(
                                        filePath = captureId,
                                        fileName = "location.json",
                                        mimeType = "application/json",
                                        fileSize = locationContent.length.toLong(),
                                        content = locationContent
                                )

                        // 添加到附件列表
                        val currentList = _attachments.value
                        _attachments.value = currentList + attachmentInfo

                        _toastEvent.emit("已添加当前位置")
                    } else {
                        _toastEvent.emit("获取位置失败: ${result.error ?: "未知错误"}")
                    }
                } catch (e: Exception) {
                    _toastEvent.emit("获取位置失败: ${e.message}")
                    Log.e(TAG, "Error capturing location", e)
                }
            }

    /** 添加问题记忆附件 确保在IO线程中执行 */
    suspend fun attachProblemMemory(content: String, filename: String) =
            withContext(Dispatchers.IO) {
                try {
                    // 生成唯一ID
                    val captureId = "problem_memory_${System.currentTimeMillis()}"

                    // 创建附件信息
                    val attachmentInfo =
                            AttachmentInfo(
                                    filePath = captureId,
                                    fileName = filename,
                                    mimeType = "text/plain",
                                    fileSize = content.length.toLong(),
                                    content = content
                            )

                    // 添加到附件列表
                    val currentList = _attachments.value
                    _attachments.value = currentList + attachmentInfo

                    _toastEvent.emit("已添加问题记忆: $filename")
                } catch (e: Exception) {
                    _toastEvent.emit("添加问题记忆失败: ${e.message}")
                    Log.e(TAG, "Error attaching problem memory", e)
                }
            }

    /** 查询问题记忆库并添加结果作为附件 确保在IO线程中执行 */
    suspend fun queryProblemMemory(query: String): Pair<String, String> =
            withContext(Dispatchers.IO) {
                try {
                    // 创建查询问题库的工具
                    val queryTool =
                            AITool(
                                    name = "query_problem_library",
                                    parameters = listOf(ToolParameter("query", query))
                            )

                    // 执行工具查询问题库
                    val result = toolHandler.executeTool(queryTool)

                    if (result.success) {
                        // 查询成功，获取结果
                        val queryResult = result.result.toString()
                        // 创建文件名
                        val fileName = "问题库查询结果.txt"
                        return@withContext Pair(queryResult, fileName)
                    } else {
                        // 查询失败，返回错误信息
                        val errorMsg = "查询问题库失败: ${result.error ?: "未知错误"}"
                        return@withContext Pair(errorMsg, "查询错误.txt")
                    }
                } catch (e: Exception) {
                    // 处理异常
                    val errorMsg = "查询问题库出错: ${e.message}"
                    Log.e(TAG, "查询问题库出错", e)
                    return@withContext Pair(errorMsg, "查询错误.txt")
                }
            }

    /** Get file name from content URI */
    private suspend fun getFileNameFromUri(uri: Uri): String =
            withContext(Dispatchers.IO) {
                val contentResolver = context.contentResolver
                var fileName = "未知文件"

                try {
                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val displayNameIndex =
                                    cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (displayNameIndex != -1) {
                                fileName = cursor.getString(displayNameIndex)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting file name from URI", e)
                }

                return@withContext fileName
            }

    /** Get file size from content URI */
    private suspend fun getFileSizeFromUri(uri: Uri): Long =
            withContext(Dispatchers.IO) {
                val contentResolver = context.contentResolver
                var fileSize = 0L

                try {
                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (sizeIndex != -1) {
                                fileSize = cursor.getLong(sizeIndex)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting file size from URI", e)
                }

                return@withContext fileSize
            }

    /** Get MIME type from file path */
    private fun getMimeTypeFromPath(filePath: String): String? {
        val extension = filePath.substringAfterLast('.', "")
        return when (extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "txt" -> "text/plain"
            "pdf" -> "application/pdf"
            "doc", "docx" -> "application/msword"
            "xls", "xlsx" -> "application/vnd.ms-excel"
            "zip" -> "application/zip"
            else -> null
        }
    }
}
