package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.DirectoryListingData
import com.ai.assistance.operit.core.tools.FileApplyResultData
import com.ai.assistance.operit.core.tools.FileContentData
import com.ai.assistance.operit.core.tools.FileExistsData
import com.ai.assistance.operit.core.tools.FileInfoData
import com.ai.assistance.operit.core.tools.FileOperationData
import com.ai.assistance.operit.core.tools.FilePartContentData
import com.ai.assistance.operit.core.tools.FindFilesResultData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.model.ToolResult
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Collection of file system operation tools for the AI assistant These tools use Java File APIs for
 * file operations
 */
open class StandardFileSystemTools(protected val context: Context) {
    companion object {
        private const val TAG = "FileSystemTools"

        // Maximum allowed file size for operations
        protected const val MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024 // 10MB

        // 每个部分的行数
        protected const val PART_SIZE = 200
    }

    /** List files in a directory */
    open suspend fun listFiles(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""

        if (path.isBlank()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Path parameter is required"
            )
        }

        return try {
            val directory = File(path)

            if (!directory.exists()) {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Directory does not exist: $path"
                )
            }

            if (!directory.isDirectory) {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Path is not a directory: $path"
                )
            }

            val entries = mutableListOf<DirectoryListingData.FileEntry>()
            val files = directory.listFiles() ?: emptyArray()

            val dateFormat = SimpleDateFormat("MMM dd HH:mm", Locale.US)

            for (file in files) {
                if (file.name != "." && file.name != "..") {
                    entries.add(
                            DirectoryListingData.FileEntry(
                                    name = file.name,
                                    isDirectory = file.isDirectory,
                                    size = file.length(),
                                    permissions = getFilePermissions(file),
                                    lastModified = dateFormat.format(Date(file.lastModified()))
                            )
                    )
                }
            }

            Log.d(TAG, "Listed ${entries.size} entries in directory $path")

            return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = DirectoryListingData(path, entries),
                    error = ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error listing directory", e)
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error listing directory: ${e.message}"
            )
        }
    }

    /** Get file permissions as a string like "rwxr-xr-x" */
    protected fun getFilePermissions(file: File): String {
        // Java has limited capabilities for getting Unix-style file permissions
        // This is a simplified version that checks basic permissions
        val canRead = if (file.canRead()) 'r' else '-'
        val canWrite = if (file.canWrite()) 'w' else '-'
        val canExecute = if (file.canExecute()) 'x' else '-'

        // For simplicity, we'll use the same permissions for user, group, and others
        return "$canRead$canWrite$canExecute$canRead-$canExecute$canRead-$canExecute"
    }

    /** Read file content */
    open suspend fun readFile(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""

        if (path.isBlank()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Path parameter is required"
            )
        }

        return try {
            val file = File(path)

            if (!file.exists()) {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "File does not exist: $path"
                )
            }

            if (!file.isFile) {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Path is not a file: $path"
                )
            }

            if (file.length() > MAX_FILE_SIZE_BYTES) {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error =
                                "File is too large (${file.length() / 1024} KB). Maximum allowed size is ${MAX_FILE_SIZE_BYTES / 1024} KB."
                )
            }

            // Check if it's a Word document
            val fileExt = file.extension.lowercase()
            if (fileExt == "doc" || fileExt == "docx") {
                Log.d(TAG, "Detected Word document, attempting to convert to text before reading")

                // Create temporary file for converted text
                val tempFilePath = "${path}_converted_${System.currentTimeMillis()}.txt"

                try {
                    // Try to use document conversion tool if available
                    val fileConverterTool =
                            AITool(
                                    name = "convert_file",
                                    parameters =
                                            listOf(
                                                    ToolParameter("source_path", path),
                                                    ToolParameter("target_path", tempFilePath)
                                            )
                            )

                    val toolHandler = AIToolHandler.getInstance(context)
                    val conversionResult = toolHandler.executeTool(fileConverterTool)

                    if (conversionResult.success) {
                        Log.d(TAG, "Successfully converted Word document to text")

                        // Read the converted text file
                        val tempFile = File(tempFilePath)
                        if (tempFile.exists()) {
                            val content = tempFile.readText()
                            tempFile.delete() // Clean up

                            return ToolResult(
                                    toolName = tool.name,
                                    success = true,
                                    result =
                                            FileContentData(
                                                    path = path,
                                                    content = content,
                                                    size = content.length.toLong()
                                            ),
                                    error = ""
                            )
                        }
                    } else {
                        Log.w(
                                TAG,
                                "Word conversion failed: ${conversionResult.error}, falling back to raw content"
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error during Word document conversion", e)
                    // Conversion failed, fall back to reading raw file
                }
            } else if (fileExt == "jpg" ||
                            fileExt == "jpeg" ||
                            fileExt == "png" ||
                            fileExt == "gif" ||
                            fileExt == "bmp"
            ) {
                Log.d(TAG, "Detected image file, attempting to extract text using OCR")

                try {
                    // 使用BitmapFactory读取图片
                    val bitmap = android.graphics.BitmapFactory.decodeFile(path)
                    if (bitmap != null) {
                        // 使用OCRUtils提取文本
                        val ocrText =
                                kotlinx.coroutines.runBlocking {
                                    com.ai.assistance.operit.util.OCRUtils.recognizeText(
                                            context,
                                            bitmap
                                    )
                                }

                        if (ocrText.isNotBlank()) {
                            Log.d(TAG, "Successfully extracted text from image using OCR")

                            // 返回提取的文本
                            return ToolResult(
                                    toolName = tool.name,
                                    success = true,
                                    result =
                                            FileContentData(
                                                    path = path,
                                                    content = ocrText,
                                                    size = ocrText.length.toLong()
                                            ),
                                    error = ""
                            )
                        } else {
                            Log.w(
                                    TAG,
                                    "OCR extraction returned empty text, falling back to raw content"
                            )
                        }
                    } else {
                        Log.w(TAG, "Failed to decode image file, falling back to raw content")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error during OCR text extraction", e)
                    // OCR提取失败，回退到读取原始文件
                }
            }

            // Read file content as string
            val content = file.readText()

            return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = FileContentData(path = path, content = content, size = file.length()),
                    error = ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error reading file", e)
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error reading file: ${e.message}"
            )
        }
    }

    /** 分段读取文件内容，每次读取指定部分（默认每部分200行） */
    open suspend fun readFilePart(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val partIndex = tool.parameters.find { it.name == "partIndex" }?.value?.toIntOrNull() ?: 0

        if (path.isBlank()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Path parameter is required"
            )
        }

        // 先调用readFile获取完整内容
        val fileResult =
                readFile(
                        AITool(name = "read_file", parameters = listOf(ToolParameter("path", path)))
                )

        if (!fileResult.success) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = fileResult.error
            )
        }

        // 获取文件内容并按行分割
        val fileContent = (fileResult.result as? FileContentData)?.content ?: ""
        val lines = fileContent.lines()

        // 计算总部分数
        val totalParts = (lines.size + PART_SIZE - 1) / PART_SIZE

        // 确保partIndex在有效范围内
        val validPartIndex = partIndex.coerceIn(0, totalParts - 1)

        // 计算开始和结束行
        val startLine = validPartIndex * PART_SIZE
        val endLine = minOf(startLine + PART_SIZE, lines.size)

        // 提取当前部分的行
        val partContent =
                if (lines.isNotEmpty()) {
                    lines.subList(startLine, endLine).joinToString("\n")
                } else {
                    ""
                }

        return ToolResult(
                toolName = tool.name,
                success = true,
                result =
                        FilePartContentData(
                                path = path,
                                content = partContent,
                                partIndex = validPartIndex,
                                totalParts = totalParts,
                                startLine = startLine,
                                endLine = endLine,
                                totalLines = lines.size
                        ),
                error = ""
        )
    }

    /** Write content to a file */
    open suspend fun writeFile(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val content = tool.parameters.find { it.name == "content" }?.value ?: ""
        val append = tool.parameters.find { it.name == "append" }?.value?.toBoolean() ?: false

        if (path.isBlank()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileOperationData(
                                    operation = "write",
                                    path = "",
                                    successful = false,
                                    details = "Path parameter is required"
                            ),
                    error = "Path parameter is required"
            )
        }

        return try {
            val file = File(path)

            // Create parent directories if needed
            val parentDir = file.parentFile
            if (parentDir != null && !parentDir.exists()) {
                if (!parentDir.mkdirs()) {
                    Log.w(TAG, "Failed to create parent directory: ${parentDir.absolutePath}")
                }
            }

            // Write content to file
            if (append && file.exists()) {
                file.appendText(content)
            } else {
                file.writeText(content)
            }

            // Verify write was successful
            if (!file.exists()) {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                                FileOperationData(
                                        operation = if (append) "append" else "write",
                                        path = path,
                                        successful = false,
                                        details =
                                                "Write completed but file does not exist. Possible permission issue."
                                ),
                        error =
                                "Write completed but file does not exist. Possible permission issue."
                )
            }

            if (file.length() == 0L && content.isNotEmpty()) {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                                FileOperationData(
                                        operation = if (append) "append" else "write",
                                        path = path,
                                        successful = false,
                                        details =
                                                "File was created but appears to be empty. Possible write failure."
                                ),
                        error = "File was created but appears to be empty. Possible write failure."
                )
            }

            val operation = if (append) "append" else "write"
            val details = if (append) "Content appended to $path" else "Content written to $path"

            return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result =
                            FileOperationData(
                                    operation = operation,
                                    path = path,
                                    successful = true,
                                    details = details
                            ),
                    error = ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error writing to file", e)

            val errorMessage =
                    when {
                        e is IOException ->
                                "File I/O error: ${e.message}. Please check if the path has write permissions."
                        e.message?.contains("permission", ignoreCase = true) == true ->
                                "Permission denied, cannot write to file: ${e.message}. Please check if the app has proper permissions."
                        else -> "Error writing to file: ${e.message}"
                    }

            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileOperationData(
                                    operation = if (append) "append" else "write",
                                    path = path,
                                    successful = false,
                                    details = errorMessage
                            ),
                    error = errorMessage
            )
        }
    }

    /** Delete a file or directory */
    open suspend fun deleteFile(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val recursive = tool.parameters.find { it.name == "recursive" }?.value?.toBoolean() ?: false

        if (path.isBlank()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileOperationData(
                                    operation = "delete",
                                    path = "",
                                    successful = false,
                                    details = "Path parameter is required"
                            ),
                    error = "Path parameter is required"
            )
        }

        // Don't allow deleting system directories
        val restrictedPaths = listOf("/system", "/data", "/proc", "/dev")
        if (restrictedPaths.any { path.startsWith(it) }) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileOperationData(
                                    operation = "delete",
                                    path = path,
                                    successful = false,
                                    details = "Deleting system directories is not allowed"
                            ),
                    error = "Deleting system directories is not allowed"
            )
        }

        return try {
            val file = File(path)

            if (!file.exists()) {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                                FileOperationData(
                                        operation = "delete",
                                        path = path,
                                        successful = false,
                                        details = "File or directory does not exist: $path"
                                ),
                        error = "File or directory does not exist: $path"
                )
            }

            var success = false

            if (file.isDirectory) {
                if (recursive) {
                    success = file.deleteRecursively()
                } else {
                    // Only delete if directory is empty
                    val files = file.listFiles() ?: emptyArray()
                    if (files.isEmpty()) {
                        success = file.delete()
                    } else {
                        return ToolResult(
                                toolName = tool.name,
                                success = false,
                                result =
                                        FileOperationData(
                                                operation = "delete",
                                                path = path,
                                                successful = false,
                                                details =
                                                        "Directory is not empty and recursive flag is not set"
                                        ),
                                error = "Directory is not empty and recursive flag is not set"
                        )
                    }
                }
            } else {
                success = file.delete()
            }

            if (success) {
                return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                FileOperationData(
                                        operation = "delete",
                                        path = path,
                                        successful = true,
                                        details = "Successfully deleted $path"
                                ),
                        error = ""
                )
            } else {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                                FileOperationData(
                                        operation = "delete",
                                        path = path,
                                        successful = false,
                                        details =
                                                "Failed to delete: permission denied or file in use"
                                ),
                        error = "Failed to delete: permission denied or file in use"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting file/directory", e)
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileOperationData(
                                    operation = "delete",
                                    path = path,
                                    successful = false,
                                    details = "Error deleting file/directory: ${e.message}"
                            ),
                    error = "Error deleting file/directory: ${e.message}"
            )
        }
    }

    /** Check if a file or directory exists */
    open suspend fun fileExists(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""

        if (path.isBlank()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Path parameter is required"
            )
        }

        return try {
            val file = File(path)
            val exists = file.exists()

            if (!exists) {
                return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result = FileExistsData(path = path, exists = false),
                        error = ""
                )
            }

            val isDirectory = file.isDirectory
            val size = file.length()

            return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result =
                            FileExistsData(
                                    path = path,
                                    exists = true,
                                    isDirectory = isDirectory,
                                    size = size
                            ),
                    error = ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error checking file existence", e)
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileExistsData(
                                    path = path,
                                    exists = false,
                                    isDirectory = false,
                                    size = 0
                            ),
                    error = "Error checking file existence: ${e.message}"
            )
        }
    }

    /** Move or rename a file or directory */
    open suspend fun moveFile(tool: AITool): ToolResult {
        val sourcePath = tool.parameters.find { it.name == "source" }?.value ?: ""
        val destPath = tool.parameters.find { it.name == "destination" }?.value ?: ""

        if (sourcePath.isBlank() || destPath.isBlank()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileOperationData(
                                    operation = "move",
                                    path = sourcePath,
                                    successful = false,
                                    details = "Source and destination parameters are required"
                            ),
                    error = "Source and destination parameters are required"
            )
        }

        // Don't allow moving system directories
        val restrictedPaths = listOf("/system", "/data", "/proc", "/dev")
        if (restrictedPaths.any { sourcePath.startsWith(it) }) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileOperationData(
                                    operation = "move",
                                    path = sourcePath,
                                    successful = false,
                                    details = "Moving system directories is not allowed"
                            ),
                    error = "Moving system directories is not allowed"
            )
        }

        return try {
            val sourceFile = File(sourcePath)
            val destFile = File(destPath)

            if (!sourceFile.exists()) {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                                FileOperationData(
                                        operation = "move",
                                        path = sourcePath,
                                        successful = false,
                                        details = "Source file does not exist: $sourcePath"
                                ),
                        error = "Source file does not exist: $sourcePath"
                )
            }

            // Create parent directory if needed
            val destParent = destFile.parentFile
            if (destParent != null && !destParent.exists()) {
                destParent.mkdirs()
            }

            // Perform move operation
            if (sourceFile.renameTo(destFile)) {
                return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                FileOperationData(
                                        operation = "move",
                                        path = sourcePath,
                                        successful = true,
                                        details = "Successfully moved $sourcePath to $destPath"
                                ),
                        error = ""
                )
            } else {
                // If simple rename fails, try copy and delete (could be across
                // filesystems)
                if (sourceFile.isDirectory) {
                    // For directories, use directory copy utility
                    val copySuccess = copyDirectory(sourceFile, destFile)
                    if (copySuccess && sourceFile.deleteRecursively()) {
                        return ToolResult(
                                toolName = tool.name,
                                success = true,
                                result =
                                        FileOperationData(
                                                operation = "move",
                                                path = sourcePath,
                                                successful = true,
                                                details =
                                                        "Successfully moved $sourcePath to $destPath (via copy and delete)"
                                        ),
                                error = ""
                        )
                    }
                } else {
                    // For files, copy the content then delete original
                    sourceFile.inputStream().use { input ->
                        destFile.outputStream().use { output -> input.copyTo(output) }
                    }

                    if (destFile.exists() &&
                                    destFile.length() == sourceFile.length() &&
                                    sourceFile.delete()
                    ) {
                        return ToolResult(
                                toolName = tool.name,
                                success = true,
                                result =
                                        FileOperationData(
                                                operation = "move",
                                                path = sourcePath,
                                                successful = true,
                                                details =
                                                        "Successfully moved $sourcePath to $destPath (via copy and delete)"
                                        ),
                                error = ""
                        )
                    }
                }

                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                                FileOperationData(
                                        operation = "move",
                                        path = sourcePath,
                                        successful = false,
                                        details =
                                                "Failed to move file: possibly a permissions issue or destination already exists"
                                ),
                        error =
                                "Failed to move file: possibly a permissions issue or destination already exists"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error moving file", e)
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileOperationData(
                                    operation = "move",
                                    path = sourcePath,
                                    successful = false,
                                    details = "Error moving file: ${e.message}"
                            ),
                    error = "Error moving file: ${e.message}"
            )
        }
    }

    /** Helper method to recursively copy a directory */
    private fun copyDirectory(sourceDir: File, destDir: File): Boolean {
        try {
            if (!destDir.exists()) {
                destDir.mkdirs()
            }

            sourceDir.listFiles()?.forEach { file ->
                val destFile = File(destDir, file.name)
                if (file.isDirectory) {
                    copyDirectory(file, destFile)
                } else {
                    file.inputStream().use { input ->
                        destFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error copying directory", e)
            return false
        }
    }

    /** Copy a file or directory */
    open suspend fun copyFile(tool: AITool): ToolResult {
        val sourcePath = tool.parameters.find { it.name == "source" }?.value ?: ""
        val destPath = tool.parameters.find { it.name == "destination" }?.value ?: ""
        val recursive = tool.parameters.find { it.name == "recursive" }?.value?.toBoolean() ?: true

        if (sourcePath.isBlank() || destPath.isBlank()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileOperationData(
                                    operation = "copy",
                                    path = sourcePath,
                                    successful = false,
                                    details = "Source and destination parameters are required"
                            ),
                    error = "Source and destination parameters are required"
            )
        }

        return try {
            val sourceFile = File(sourcePath)
            val destFile = File(destPath)

            if (!sourceFile.exists()) {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                                FileOperationData(
                                        operation = "copy",
                                        path = sourcePath,
                                        successful = false,
                                        details = "Source path does not exist: $sourcePath"
                                ),
                        error = "Source path does not exist: $sourcePath"
                )
            }

            // Create parent directory if needed
            val destParent = destFile.parentFile
            if (destParent != null && !destParent.exists()) {
                destParent.mkdirs()
            }

            if (sourceFile.isDirectory) {
                if (recursive) {
                    val success = copyDirectory(sourceFile, destFile)
                    if (success) {
                        return ToolResult(
                                toolName = tool.name,
                                success = true,
                                result =
                                        FileOperationData(
                                                operation = "copy",
                                                path = sourcePath,
                                                successful = true,
                                                details =
                                                        "Successfully copied directory $sourcePath to $destPath"
                                        ),
                                error = ""
                        )
                    } else {
                        return ToolResult(
                                toolName = tool.name,
                                success = false,
                                result =
                                        FileOperationData(
                                                operation = "copy",
                                                path = sourcePath,
                                                successful = false,
                                                details =
                                                        "Failed to copy directory: possible permission issue"
                                        ),
                                error = "Failed to copy directory: possible permission issue"
                        )
                    }
                } else {
                    return ToolResult(
                            toolName = tool.name,
                            success = false,
                            result =
                                    FileOperationData(
                                            operation = "copy",
                                            path = sourcePath,
                                            successful = false,
                                            details = "Cannot copy directory without recursive flag"
                                    ),
                            error = "Cannot copy directory without recursive flag"
                    )
                }
            } else {
                // Copy file
                sourceFile.inputStream().use { input ->
                    destFile.outputStream().use { output -> input.copyTo(output) }
                }

                // Verify copy was successful
                if (destFile.exists() && destFile.length() == sourceFile.length()) {
                    return ToolResult(
                            toolName = tool.name,
                            success = true,
                            result =
                                    FileOperationData(
                                            operation = "copy",
                                            path = sourcePath,
                                            successful = true,
                                            details =
                                                    "Successfully copied file $sourcePath to $destPath"
                                    ),
                            error = ""
                    )
                } else {
                    return ToolResult(
                            toolName = tool.name,
                            success = false,
                            result =
                                    FileOperationData(
                                            operation = "copy",
                                            path = sourcePath,
                                            successful = false,
                                            details =
                                                    "Copy operation completed but verification failed"
                                    ),
                            error = "Copy operation completed but verification failed"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error copying file/directory", e)
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileOperationData(
                                    operation = "copy",
                                    path = sourcePath,
                                    successful = false,
                                    details = "Error copying file/directory: ${e.message}"
                            ),
                    error = "Error copying file/directory: ${e.message}"
            )
        }
    }

    /** Create a directory */
    open suspend fun makeDirectory(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val createParents =
                tool.parameters.find { it.name == "create_parents" }?.value?.toBoolean() ?: false

        if (path.isBlank()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileOperationData(
                                    operation = "mkdir",
                                    path = "",
                                    successful = false,
                                    details = "Path parameter is required"
                            ),
                    error = "Path parameter is required"
            )
        }

        return try {
            val directory = File(path)

            // Check if directory already exists
            if (directory.exists()) {
                if (directory.isDirectory) {
                    return ToolResult(
                            toolName = tool.name,
                            success = true,
                            result =
                                    FileOperationData(
                                            operation = "mkdir",
                                            path = path,
                                            successful = true,
                                            details = "Directory already exists: $path"
                                    ),
                            error = ""
                    )
                } else {
                    return ToolResult(
                            toolName = tool.name,
                            success = false,
                            result =
                                    FileOperationData(
                                            operation = "mkdir",
                                            path = path,
                                            successful = false,
                                            details = "Path exists but is not a directory: $path"
                                    ),
                            error = "Path exists but is not a directory: $path"
                    )
                }
            }

            // Create directory
            val success =
                    if (createParents) {
                        directory.mkdirs()
                    } else {
                        directory.mkdir()
                    }

            if (success) {
                return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                FileOperationData(
                                        operation = "mkdir",
                                        path = path,
                                        successful = true,
                                        details = "Successfully created directory $path"
                                ),
                        error = ""
                )
            } else {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                                FileOperationData(
                                        operation = "mkdir",
                                        path = path,
                                        successful = false,
                                        details =
                                                "Failed to create directory: parent directory may not exist or permission denied"
                                ),
                        error =
                                "Failed to create directory: parent directory may not exist or permission denied"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating directory", e)
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileOperationData(
                                    operation = "mkdir",
                                    path = path,
                                    successful = false,
                                    details = "Error creating directory: ${e.message}"
                            ),
                    error = "Error creating directory: ${e.message}"
            )
        }
    }

    /** Search for files matching a pattern */
    open suspend fun findFiles(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val pattern = tool.parameters.find { it.name == "pattern" }?.value ?: ""

        if (path.isBlank() || pattern.isBlank()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FindFilesResultData(
                                    path = path,
                                    pattern = pattern,
                                    files = emptyList()
                            ),
                    error = "Path and pattern parameters are required"
            )
        }

        return try {
            val rootDir = File(path)

            if (!rootDir.exists() || !rootDir.isDirectory) {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                                FindFilesResultData(
                                        path = path,
                                        pattern = pattern,
                                        files = emptyList()
                                ),
                        error = "Path does not exist or is not a directory: $path"
                )
            }

            // Get search options
            val usePathPattern =
                    tool.parameters.find { it.name == "use_path_pattern" }?.value?.toBoolean()
                            ?: false
            val caseInsensitive =
                    tool.parameters.find { it.name == "case_insensitive" }?.value?.toBoolean()
                            ?: false
            val maxDepth =
                    tool.parameters.find { it.name == "max_depth" }?.value?.toIntOrNull() ?: -1

            // Convert glob pattern to regex
            val regex = globToRegex(pattern, caseInsensitive)

            // Recursively find matching files
            val matchingFiles = mutableListOf<String>()
            findMatchingFiles(
                    rootDir,
                    regex,
                    matchingFiles,
                    usePathPattern,
                    maxDepth,
                    0,
                    rootDir.absolutePath
            )

            return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result =
                            FindFilesResultData(
                                    path = path,
                                    pattern = pattern,
                                    files = matchingFiles
                            ),
                    error = ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error searching for files", e)
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FindFilesResultData(
                                    path = path,
                                    pattern = pattern,
                                    files = emptyList()
                            ),
                    error = "Error searching for files: ${e.message}"
            )
        }
    }

    /** Helper method to convert glob pattern to regex */
    private fun globToRegex(glob: String, caseInsensitive: Boolean): Regex {
        val regex = StringBuilder("^")

        for (i in glob.indices) {
            val c = glob[i]
            when (c) {
                '*' -> regex.append(".*")
                '?' -> regex.append(".")
                '.' -> regex.append("\\.")
                '\\' -> regex.append("\\\\")
                '[' -> regex.append("[")
                ']' -> regex.append("]")
                '(' -> regex.append("\\(")
                ')' -> regex.append("\\)")
                '{' -> regex.append("(")
                '}' -> regex.append(")")
                ',' -> regex.append("|")
                else -> regex.append(c)
            }
        }

        regex.append("$")

        return if (caseInsensitive) {
            Regex(regex.toString(), RegexOption.IGNORE_CASE)
        } else {
            Regex(regex.toString())
        }
    }

    /** Helper method to recursively find files matching a pattern */
    private fun findMatchingFiles(
            dir: File,
            regex: Regex,
            results: MutableList<String>,
            usePathPattern: Boolean,
            maxDepth: Int,
            currentDepth: Int,
            rootPath: String
    ) {
        if (maxDepth >= 0 && currentDepth > maxDepth) {
            return
        }

        val files = dir.listFiles() ?: return

        for (file in files) {
            val relativePath = file.absolutePath.substring(rootPath.length + 1)

            val testString = if (usePathPattern) relativePath else file.name

            if (regex.matches(testString)) {
                results.add(file.absolutePath)
            }

            if (file.isDirectory) {
                findMatchingFiles(
                        file,
                        regex,
                        results,
                        usePathPattern,
                        maxDepth,
                        currentDepth + 1,
                        rootPath
                )
            }
        }
    }

    /** Get file information */
    open suspend fun fileInfo(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""

        if (path.isBlank()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileInfoData(
                                    path = "",
                                    exists = false,
                                    fileType = "",
                                    size = 0,
                                    permissions = "",
                                    owner = "",
                                    group = "",
                                    lastModified = "",
                                    rawStatOutput = ""
                            ),
                    error = "Path parameter is required"
            )
        }

        return try {
            val file = File(path)

            if (!file.exists()) {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                                FileInfoData(
                                        path = path,
                                        exists = false,
                                        fileType = "",
                                        size = 0,
                                        permissions = "",
                                        owner = "",
                                        group = "",
                                        lastModified = "",
                                        rawStatOutput = ""
                                ),
                        error = "File or directory does not exist: $path"
                )
            }

            // Get file type
            val fileType =
                    when {
                        file.isDirectory -> "directory"
                        file.isFile -> "file"
                        else -> "other"
                    }

            // Get permissions
            val permissions = getFilePermissions(file)

            // Owner and group info are not easily available in Java
            val owner = System.getProperty("user.name") ?: ""
            val group = ""

            // Last modified time
            val lastModified =
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                            .format(Date(file.lastModified()))

            // Size
            val size = if (file.isFile) file.length() else 0

            // Collect all file info into a raw string
            val rawInfo = StringBuilder()
            rawInfo.append("File: $path\n")
            rawInfo.append("Size: $size bytes\n")
            rawInfo.append("Type: $fileType\n")
            rawInfo.append("Permissions: $permissions\n")
            rawInfo.append("Last Modified: $lastModified\n")
            rawInfo.append("Owner: $owner\n")
            if (file.canRead()) rawInfo.append("Access: Readable\n")
            if (file.canWrite()) rawInfo.append("Access: Writable\n")
            if (file.canExecute()) rawInfo.append("Access: Executable\n")
            if (file.isHidden()) rawInfo.append("Hidden: Yes\n")

            return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result =
                            FileInfoData(
                                    path = path,
                                    exists = true,
                                    fileType = fileType,
                                    size = size,
                                    permissions = permissions,
                                    owner = owner,
                                    group = group,
                                    lastModified = lastModified,
                                    rawStatOutput = rawInfo.toString()
                            ),
                    error = ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting file information", e)
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileInfoData(
                                    path = path,
                                    exists = false,
                                    fileType = "",
                                    size = 0,
                                    permissions = "",
                                    owner = "",
                                    group = "",
                                    lastModified = "",
                                    rawStatOutput = ""
                            ),
                    error = "Error getting file information: ${e.message}"
            )
        }
    }

    /** Zip files or directories */
    open suspend fun zipFiles(tool: AITool): ToolResult {
        val sourcePath = tool.parameters.find { it.name == "source" }?.value ?: ""
        val zipPath = tool.parameters.find { it.name == "destination" }?.value ?: ""

        if (sourcePath.isBlank() || zipPath.isBlank()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Source and destination parameters are required"
            )
        }

        return try {
            val sourceFile = File(sourcePath)
            val destZipFile = File(zipPath)

            if (!sourceFile.exists()) {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Source file or directory does not exist: $sourcePath"
                )
            }

            // Create parent directory for zip file if needed
            val zipDir = destZipFile.parentFile
            if (zipDir != null && !zipDir.exists()) {
                zipDir.mkdirs()
            }

            // Initialize buffer for file operations
            val buffer = ByteArray(1024)

            ZipOutputStream(BufferedOutputStream(FileOutputStream(destZipFile))).use { zos ->
                if (sourceFile.isDirectory) {
                    // For directories, add all files recursively
                    addDirectoryToZip(sourceFile, sourceFile.name, zos)
                } else {
                    // For a single file, add it directly
                    val entryName = sourceFile.name
                    zos.putNextEntry(ZipEntry(entryName))

                    FileInputStream(sourceFile).use { fis ->
                        BufferedInputStream(fis).use { bis ->
                            var len: Int
                            while (bis.read(buffer).also { len = it } > 0) {
                                zos.write(buffer, 0, len)
                            }
                        }
                    }

                    zos.closeEntry()
                }
            }

            if (destZipFile.exists()) {
                return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                FileOperationData(
                                        operation = "zip",
                                        path = sourcePath,
                                        successful = true,
                                        details = "Successfully compressed $sourcePath to $zipPath"
                                ),
                        error = ""
                )
            } else {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Failed to create zip file"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error compressing files", e)
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error compressing files: ${e.message}"
            )
        }
    }

    /** Helper method to add directory contents to zip */
    private fun addDirectoryToZip(dir: File, baseName: String, zos: ZipOutputStream) {
        val buffer = ByteArray(1024)
        val files = dir.listFiles() ?: return

        for (file in files) {
            if (file.isDirectory) {
                addDirectoryToZip(file, "$baseName/${file.name}", zos)
                continue
            }

            val entryName = "$baseName/${file.name}"
            zos.putNextEntry(ZipEntry(entryName))

            FileInputStream(file).use { fis ->
                BufferedInputStream(fis).use { bis ->
                    var len: Int
                    while (bis.read(buffer).also { len = it } > 0) {
                        zos.write(buffer, 0, len)
                    }
                }
            }

            zos.closeEntry()
        }
    }

    /** Unzip a zip file */
    open suspend fun unzipFiles(tool: AITool): ToolResult {
        val zipPath = tool.parameters.find { it.name == "source" }?.value ?: ""
        val destPath = tool.parameters.find { it.name == "destination" }?.value ?: ""

        if (zipPath.isBlank() || destPath.isBlank()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Source and destination parameters are required"
            )
        }

        return try {
            val zipFile = File(zipPath)
            val destDir = File(destPath)

            if (!zipFile.exists()) {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Zip file does not exist: $zipPath"
                )
            }

            if (!zipFile.isFile) {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Source path is not a file: $zipPath"
                )
            }

            // Create destination directory if needed
            if (!destDir.exists()) {
                destDir.mkdirs()
            }

            val buffer = ByteArray(1024)

            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                var zipEntry: ZipEntry? = zis.nextEntry

                while (zipEntry != null) {
                    val fileName = zipEntry.name
                    val newFile = File(destDir, fileName)

                    // Create parent directories if needed
                    val parentDir = newFile.parentFile
                    if (parentDir != null && !parentDir.exists()) {
                        parentDir.mkdirs()
                    }

                    if (zipEntry.isDirectory) {
                        // Create directory if it doesn't exist
                        if (!newFile.exists()) {
                            newFile.mkdirs()
                        }
                    } else {
                        // Extract file
                        FileOutputStream(newFile).use { fos ->
                            BufferedOutputStream(fos).use { bos ->
                                var len: Int
                                while (zis.read(buffer).also { len = it } > 0) {
                                    bos.write(buffer, 0, len)
                                }
                            }
                        }
                    }

                    zis.closeEntry()
                    zipEntry = zis.nextEntry
                }
            }

            return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result =
                            FileOperationData(
                                    operation = "unzip",
                                    path = zipPath,
                                    successful = true,
                                    details = "Successfully extracted $zipPath to $destPath"
                            ),
                    error = ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting zip file", e)
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error extracting zip file: ${e.message}"
            )
        }
    }

    /** 智能应用文件绑定，将AI生成的代码与原始文件内容智能合并 该工具会读取原始文件内容，应用AI生成的代码（通常包含//existing code标记）， 然后将合并后的内容写回文件 */
    open suspend fun applyFile(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val aiGeneratedCode = tool.parameters.find { it.name == "content" }?.value ?: ""

        if (path.isBlank()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileOperationData(
                                    operation = "apply",
                                    path = "",
                                    successful = false,
                                    details = "Path parameter is required"
                            ),
                    error = "Path parameter is required"
            )
        }

        if (aiGeneratedCode.isBlank()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileOperationData(
                                    operation = "apply",
                                    path = path,
                                    successful = false,
                                    details = "Content parameter is required"
                            ),
                    error = "Content parameter is required"
            )
        }

        return try {
            // 1. 检查文件是否存在
            val fileExistsResult =
                    fileExists(
                            AITool(
                                    name = "file_exists",
                                    parameters = listOf(ToolParameter("path", path))
                            )
                    )

            // 如果文件不存在，直接写入内容而不是合并
            if (!fileExistsResult.success || !(fileExistsResult.result as FileExistsData).exists) {
                Log.d(TAG, "文件不存在，直接创建新文件: $path")
                // 直接调用writeFile写入内容
                val writeResult =
                        writeFile(
                                AITool(
                                        name = "write_file",
                                        parameters =
                                                listOf(
                                                        ToolParameter("path", path),
                                                        ToolParameter("content", aiGeneratedCode),
                                                        ToolParameter("append", "false")
                                                )
                                )
                        )

                if (writeResult.success) {
                    // 成功写入新文件
                    val operationData =
                            FileOperationData(
                                    operation = "apply",
                                    path = path,
                                    successful = true,
                                    details = "Successfully created new file with AI code: $path"
                            )

                    return ToolResult(
                            toolName = tool.name,
                            success = true,
                            result =
                                    FileApplyResultData(
                                            operation = operationData,
                                            aiDiffInstructions = aiGeneratedCode
                                    ),
                            error = ""
                    )
                } else {
                    // 写入失败，返回写入工具的错误
                    return writeResult
                }
            }

            // 2. 读取原始文件内容
            val readResult =
                    readFile(
                            AITool(
                                    name = "read_file",
                                    parameters = listOf(ToolParameter("path", path))
                            )
                    )

            if (!readResult.success) {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                                FileOperationData(
                                        operation = "apply",
                                        path = path,
                                        successful = false,
                                        details =
                                                "Failed to read original file: ${readResult.error}"
                                ),
                        error = "Failed to read original file: ${readResult.error}"
                )
            }

            // 提取原始文件内容
            val originalContent = (readResult.result as? FileContentData)?.content ?: ""

            // 2. 使用EnhancedAIService处理文件绑定
            val enhancedAIService =
                    com.ai.assistance.operit.api.EnhancedAIService.getInstance(context)
            val bindingResult = enhancedAIService.applyFileBinding(originalContent, aiGeneratedCode)
            val mergedContent = bindingResult.first
            val aiInstructions = bindingResult.second

            // 检查文件绑定是否返回错误
            if (aiInstructions.startsWith("Error", ignoreCase = true)) {
                Log.e(TAG, "File binding failed: $aiInstructions")
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                                FileOperationData(
                                        operation = "apply",
                                        path = path,
                                        successful = false,
                                        details = "File binding failed: $aiInstructions"
                                ),
                        error = aiInstructions
                )
            }

            // 3. 将合并后的内容写回文件
            val writeResult =
                    writeFile(
                            AITool(
                                    name = "write_file",
                                    parameters =
                                            listOf(
                                                    ToolParameter("path", path),
                                                    ToolParameter("content", mergedContent),
                                                    ToolParameter("append", "false")
                                            )
                            )
                    )

            if (!writeResult.success) {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                                FileOperationData(
                                        operation = "apply",
                                        path = path,
                                        successful = false,
                                        details =
                                                "Failed to write merged content: ${writeResult.error}"
                                ),
                        error = "Failed to write merged content: ${writeResult.error}"
                )
            }

            // 成功完成
            val operationData =
                    FileOperationData(
                            operation = "apply",
                            path = path,
                            successful = true,
                            details = "Successfully applied AI code to file: $path"
                    )

            return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result =
                            FileApplyResultData(
                                    operation = operationData,
                                    aiDiffInstructions = aiInstructions
                            ),
                    error = ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error applying file binding", e)
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileOperationData(
                                    operation = "apply",
                                    path = path,
                                    successful = false,
                                    details = "Error applying file binding: ${e.message}"
                            ),
                    error = "Error applying file binding: ${e.message}"
            )
        }
    }

    /** Download file from URL */
    open suspend fun downloadFile(tool: AITool): ToolResult {
        val url = tool.parameters.find { it.name == "url" }?.value ?: ""
        val destPath = tool.parameters.find { it.name == "destination" }?.value ?: ""

        if (url.isBlank() || destPath.isBlank()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileOperationData(
                                    operation = "download",
                                    path = destPath,
                                    successful = false,
                                    details = "URL and destination parameters are required"
                            ),
                    error = "URL and destination parameters are required"
            )
        }

        // Validate URL format
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileOperationData(
                                    operation = "download",
                                    path = destPath,
                                    successful = false,
                                    details = "URL must start with http:// or https://"
                            ),
                    error = "URL must start with http:// or https://"
            )
        }

        return try {
            val destFile = File(destPath)

            // Create parent directory if needed
            val destParent = destFile.parentFile
            if (destParent != null && !destParent.exists()) {
                destParent.mkdirs()
            }

            // Open connection to URL
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.instanceFollowRedirects = true

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                                FileOperationData(
                                        operation = "download",
                                        path = destPath,
                                        successful = false,
                                        details =
                                                "Failed to download: HTTP error code $responseCode"
                                ),
                        error = "Failed to download: HTTP error code $responseCode"
                )
            }

            // Get file size for progress tracking
            val fileSize = connection.contentLength

            // Download the file
            connection.inputStream.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    var totalBytesRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                    }
                }
            }

            // Verify download was successful
            if (destFile.exists()) {
                val fileSize = destFile.length()
                val formattedSize =
                        when {
                            fileSize > 1024 * 1024 ->
                                    String.format("%.2f MB", fileSize / (1024.0 * 1024.0))
                            fileSize > 1024 -> String.format("%.2f KB", fileSize / 1024.0)
                            else -> "$fileSize bytes"
                        }

                return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                FileOperationData(
                                        operation = "download",
                                        path = destPath,
                                        successful = true,
                                        details =
                                                "File downloaded successfully: $url -> $destPath (file size: $formattedSize)"
                                ),
                        error = ""
                )
            } else {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                                FileOperationData(
                                        operation = "download",
                                        path = destPath,
                                        successful = false,
                                        details = "Download completed but file was not created"
                                ),
                        error = "Download completed but file was not created"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading file", e)
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileOperationData(
                                    operation = "download",
                                    path = destPath,
                                    successful = false,
                                    details = "Error downloading file: ${e.message}"
                            ),
                    error = "Error downloading file: ${e.message}"
            )
        }
    }

    /** Open file with system default app */
    open suspend fun openFile(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""

        if (path.isBlank()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileOperationData(
                                    operation = "open",
                                    path = "",
                                    successful = false,
                                    details = "Path parameter is required"
                            ),
                    error = "Path parameter is required"
            )
        }

        return ToolResult(
                toolName = tool.name,
                success = false,
                result =
                        FileOperationData(
                                operation = "open",
                                path = path,
                                successful = false,
                                details =
                                        "Opening files with system apps requires Android Intent functionality and is not supported in standard file operations"
                        ),
                error =
                        "Opening files with system apps requires Android Intent functionality and is not supported in standard file operations"
        )
    }

    /** Share file via system share dialog */
    open suspend fun shareFile(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val title = tool.parameters.find { it.name == "title" }?.value ?: "Share File"

        if (path.isBlank()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileOperationData(
                                    operation = "share",
                                    path = "",
                                    successful = false,
                                    details = "Path parameter is required"
                            ),
                    error = "Path parameter is required"
            )
        }

        return ToolResult(
                toolName = tool.name,
                success = false,
                result =
                        FileOperationData(
                                operation = "share",
                                path = path,
                                successful = false,
                                details =
                                        "Sharing files requires Android Intent functionality and is not supported in standard file operations"
                        ),
                error =
                        "Sharing files requires Android Intent functionality and is not supported in standard file operations"
        )
    }
}
