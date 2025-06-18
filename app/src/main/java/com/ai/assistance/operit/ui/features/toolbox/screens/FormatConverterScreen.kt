package com.ai.assistance.operit.ui.features.toolbox.screens

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.FileFormatConversionsResultData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import java.io.File
import kotlinx.coroutines.launch

// 根据文件扩展名获取分类
fun getCategoryFromExtension(extension: String): String? {
    return when (extension) {
        in listOf("txt", "pdf", "doc", "docx", "html") -> "document"
        in listOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "ico") -> "image"
        in listOf("mp3", "wav", "ogg", "m4a", "flac", "aac") -> "audio"
        in listOf("mp4", "avi", "mov", "webm", "mkv") -> "video"
        in listOf("zip", "tar", "7z", "rar") -> "archive"
        else -> null
    }
}

@Composable
private fun getCategoryDisplayNameComposable(category: String): String {
    return when (category.lowercase()) {
        "document" -> stringResource(R.string.file_category_document)
        "image" -> stringResource(R.string.file_category_image)
        "audio" -> stringResource(R.string.file_category_audio)
        "video" -> stringResource(R.string.file_category_video)
        "archive" -> stringResource(R.string.file_category_archive)
        else -> category
    }
}

// 获取文件路径
fun getFilePathFromUri(context: android.content.Context, uri: Uri): Pair<String?, String?> {
    return try {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val columnIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                val fileName = it.getString(columnIndex)
                val file = File(context.cacheDir, fileName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                // 返回原始URI的显示路径和缓存文件路径
                Pair(fileName, file.absolutePath)
            } else {
                Pair(null, null)
            }
        }
                ?: Pair(null, null)
    } catch (e: Exception) {
        Log.e("FormatConverterScreen", "Error getting file path", e)
        Pair(null, null)
    }
}

// 获取目标文件路径
fun getTargetFilePath(
        context: android.content.Context,
        originalFileName: String,
        originalFilePath: String,
        targetFormat: String
): String {
    // 获取文件名（不含扩展名）
    val fileNameWithoutExt = originalFileName.substringBeforeLast('.', originalFileName)
    val newFileName = "$fileNameWithoutExt.$targetFormat"

    // 直接使用/sdcard/Document目录
    val documentPath = "/sdcard/Document/Operit"

    // 返回目标文件路径
    return "$documentPath/$newFileName"
}

// 根据文件扩展名获取MIME类型
fun getMimeType(extension: String): String {
    return when (extension.lowercase()) {
        // 文档
        "pdf" -> "application/pdf"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "txt" -> "text/plain"
        "html" -> "text/html"

        // 图片
        "jpg",
        "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"

        // 音频
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "ogg" -> "audio/ogg"
        "m4a" -> "audio/m4a"
        "flac" -> "audio/flac"
        "aac" -> "audio/aac"

        // 视频
        "mp4" -> "video/mp4"
        "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"

        // 压缩文件
        "zip" -> "application/zip"
        "tar" -> "application/x-tar"
        "7z" -> "application/x-7z-compressed"
        "rar" -> "application/x-rar-compressed"

        // 默认
        else -> "application/octet-stream"
    }
}

// 替换文件扩展名
fun replaceFileExtension(filePath: String, newExtension: String): String {
    val lastDotIndex = filePath.lastIndexOf('.')
    return if (lastDotIndex != -1) {
        filePath.substring(0, lastDotIndex + 1) + newExtension
    } else {
        "$filePath.$newExtension"
    }
}

/** 万能格式转换屏幕，用于各种文件格式之间的转换 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FormatConverterScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val toolHandler = AIToolHandler.getInstance(context)

    // 状态变量
    var originalFileName by remember { mutableStateOf<String?>(null) }
    var selectedFile by remember { mutableStateOf<String?>(null) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var selectedTargetFormat by remember { mutableStateOf<String?>(null) }
    var supportedFormats by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var supportedCategories by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var convertedFile by remember { mutableStateOf<String?>(null) }

    // 清理缓存文件
    fun cleanupCacheFile(filePath: String?) {
        if (filePath != null) {
            try {
                val file = File(filePath)
                if (file.exists() && file.absolutePath.startsWith(context.cacheDir.absolutePath)) {
                    if (file.delete()) {
                        Log.d("FormatConverterScreen", "已删除缓存文件: $filePath")
                    } else {
                        Log.w("FormatConverterScreen", "无法删除缓存文件: $filePath")
                    }
                }
            } catch (e: Exception) {
                Log.e("FormatConverterScreen", "清理缓存文件时出错", e)
            }
        }
    }

    // 组件销毁时清理缓存
    DisposableEffect(Unit) {
        onDispose {
            selectedFile?.let { cleanupCacheFile(it) }
            // 清理整个缓存目录中可能的遗留文件
            try {
                context.cacheDir.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        file.delete()
                    }
                }
            } catch (e: Exception) {
                Log.e("FormatConverterScreen", "清理缓存目录时出错", e)
            }
        }
    }

    // 文件选择器
    val filePickerLauncher =
            rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                result.data?.data?.let { uri ->
                    try {
                        // 获取文件路径
                        val (origFileName, cachePath) = getFilePathFromUri(context, uri)
                        if (origFileName != null && cachePath != null) {
                            originalFileName = origFileName
                            selectedFile = cachePath
                            // 根据文件扩展名自动选择分类
                            val extension = origFileName.substringAfterLast('.', "").lowercase()
                            selectedCategory = getCategoryFromExtension(extension)
                        } else {
                            error = context.getString(R.string.format_converter_error_get_path)
                        }
                    } catch (e: Exception) {
                        error = context.getString(R.string.format_converter_error_select_file, e.message ?: "")
                    }
                }
            }

    // 文件类型分类
    val categories =
            listOf(
                    "document" to stringResource(R.string.file_category_document),
                    "image" to stringResource(R.string.file_category_image),
                    "audio" to stringResource(R.string.file_category_audio),
                    "video" to stringResource(R.string.file_category_video),
                    "archive" to stringResource(R.string.file_category_archive)
            )

    // 获取支持的格式转换
    LaunchedEffect(Unit) {
        try {
            val tool = AITool(name = "get_supported_conversions", parameters = emptyList())
            val result = toolHandler.executeTool(tool)
            if (result.success) {
                val formatData = result.result as? FileFormatConversionsResultData
                supportedFormats = formatData?.conversions ?: emptyMap()
                supportedCategories = formatData?.fileTypes ?: emptyMap()
            } else {
                error = result.error ?: context.getString(R.string.unknown_error)
            }
        } catch (e: Exception) {
            error = e.message ?: context.getString(R.string.unknown_error)
        }
    }

    // 执行文件转换
    fun convertFile() {
        if (selectedFile == null || selectedTargetFormat == null || originalFileName == null) {
            error = context.getString(R.string.format_converter_error_selection_incomplete)
            return
        }

        isLoading = true
        error = null
        convertedFile = null
        val currentCacheFile = selectedFile // 保存当前缓存文件的引用以便后续清理

        coroutineScope.launch {
            try {
                // 创建一个合适的输出文件路径
                val targetPath =
                        getTargetFilePath(
                                context,
                                originalFileName!!,
                                selectedFile!!,
                                selectedTargetFormat!!
                        )

                // 确保目标目录存在
                val makeDirectoryTool =
                        AITool(
                                name = "make_directory",
                                parameters =
                                        listOf(ToolParameter("path", "/sdcard/Document/Operit"))
                        )
                toolHandler.executeTool(makeDirectoryTool)

                // 执行文件转换
                val convertFileTool =
                        AITool(
                                name = "convert_file",
                                parameters =
                                        listOf(
                                                ToolParameter("source_path", selectedFile!!),
                                                ToolParameter("target_path", targetPath),
                                                ToolParameter(
                                                        "conversion_type",
                                                        "${selectedCategory}_to_${selectedTargetFormat}"
                                                )
                                        )
                        )

                val result = toolHandler.executeTool(convertFileTool)

                if (result.success) {
                    convertedFile = targetPath
                } else {
                    error = result.error ?: context.getString(R.string.format_converter_error_conversion_failed)
                }
            } catch (e: Exception) {
                Log.e("FormatConverterScreen", "Error converting file", e)
                error = context.getString(R.string.error_generic_format, e.message ?: "")
            } finally {
                isLoading = false
                // 无论成功还是失败，都清理缓存文件
                cleanupCacheFile(currentCacheFile)
            }
        }
    }

    // 打开文件
    fun openFile(filePath: String) {
        coroutineScope.launch {
            try {
                val openFileTool =
                        AITool(
                                name = "open_file",
                                parameters = listOf(ToolParameter("path", filePath))
                        )
                val result = toolHandler.executeTool(openFileTool)

                if (!result.success) {
                    error = context.getString(R.string.format_converter_error_open_file, result.error ?: "")
                }
            } catch (e: Exception) {
                Log.e("FormatConverterScreen", "Error opening file", e)
                error = context.getString(R.string.error_generic_format, e.message ?: "")
            }
        }
    }

    // 选择文件
    fun selectFile() {
        val intent =
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*" // 允许选择所有类型的文件
                }
        filePickerLauncher.launch(intent)
    }

    // 格式转换界面
    Column(
            modifier =
                    Modifier.fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
    ) {
        // 文件选择卡片
        Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors =
                        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                        text = stringResource(R.string.format_converter_title_select_file),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                        onClick = { selectFile() },
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                                ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                        shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.format_converter_button_select_file), // Reused for content desc
                            modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.format_converter_button_select_file))
                }

                selectedFile?.let { file ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                            modifier =
                                    Modifier.fillMaxWidth()
                                            .background(
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(
                                                            alpha = 0.5f
                                                    ),
                                                    RoundedCornerShape(4.dp)
                                            )
                                            .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                                imageVector = Icons.Default.InsertDriveFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // 显示原始文件名
                        Text(
                                text = originalFileName ?: file.substringAfterLast('/', stringResource(R.string.format_converter_unknown_file)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 格式选择卡片
        Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                        text = stringResource(R.string.format_converter_title_select_format),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 类别选择
                FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        maxItemsInEachRow = 3,
                        modifier = Modifier.fillMaxWidth()
                ) {
                    categories.forEach { (id, name) ->
                        CategoryItem(
                                category = name,
                                isSelected = selectedCategory == id,
                                onClick = {
                                    selectedCategory = id
                                    selectedTargetFormat = null
                                }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 目标格式选择
                if (selectedCategory != null) {
                    Text(
                            text = stringResource(R.string.format_converter_label_target_format),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 8.dp)
                    )

                    Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier =
                                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    ) {
                        val targetFormats = supportedCategories[selectedCategory] ?: emptyList()
                        targetFormats.forEach { format ->
                            FormatItem(
                                    format = format,
                                    isSelected = selectedTargetFormat == format,
                                    onClick = { selectedTargetFormat = format }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 转换按钮
        Button(
                onClick = { convertFile() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = selectedFile != null && selectedTargetFormat != null && !isLoading,
                colors =
                        ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                shape = RoundedCornerShape(8.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(
                        imageVector = Icons.Default.Transform,
                        contentDescription = stringResource(R.string.format_converter_icon_convert),
                        modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.format_converter_button_convert), style = MaterialTheme.typography.titleMedium)
            }
        }

        error?.let { errorMessage ->
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(4.dp)
            ) {
                Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        convertedFile?.let { file ->
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                            CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                            ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.format_converter_success_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                            modifier =
                                    Modifier.fillMaxWidth()
                                            .background(
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(
                                                            alpha = 0.5f
                                                    ),
                                                    RoundedCornerShape(4.dp)
                                            )
                                            .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                                imageVector = Icons.Default.InsertDriveFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                                text = file,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                        )
                        IconButton(
                                onClick = { openFile(file) },
                                modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Icon(
                                    imageVector = Icons.Default.OpenInNew,
                                    contentDescription = stringResource(R.string.format_converter_icon_open_file),
                                    tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 文件类别列表项 */
@Composable
fun CategoryItem(category: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
            modifier =
                    Modifier.fillMaxWidth()
                            .background(
                                    when {
                                        isSelected ->
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                        else -> Color.Transparent
                                    }
                            )
                            .clickable(onClick = onClick)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        // 类别图标
        Icon(
                imageVector = getCategoryIcon(category),
                contentDescription = null,
                tint =
                        when {
                            isSelected -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        },
                modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        // 类别名称
        Text(
                text = getCategoryDisplayName(category),
                style = MaterialTheme.typography.bodyMedium,
                color =
                        when {
                            isSelected -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        }
        )
    }
}

/** 格式选择项 */
@Composable
fun FormatItem(format: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
            modifier =
                    Modifier.aspectRatio(1f)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                    when {
                                        isSelected -> MaterialTheme.colorScheme.primaryContainer
                                        else ->
                                                MaterialTheme.colorScheme.surfaceVariant.copy(
                                                        alpha = 0.5f
                                                )
                                    }
                            )
                            .border(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color =
                                            if (isSelected) MaterialTheme.colorScheme.primary
                                            else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                            )
                            .clickable(onClick = onClick)
                            .padding(4.dp),
            contentAlignment = Alignment.Center
    ) {
        Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
        ) {
            Icon(
                    imageVector = getFormatIcon(format),
                    contentDescription = null,
                    tint =
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                    text = format.uppercase(),
                    style =
                            MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight =
                                            if (isSelected) FontWeight.Bold else FontWeight.Normal
                            ),
                    color =
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
            )
        }
    }
}

/** 获取类别图标 */
fun getCategoryIcon(category: String): ImageVector {
    return when (category.lowercase()) {
        "document" -> Icons.Default.Description
        "image" -> Icons.Default.Image
        "audio" -> Icons.Default.AudioFile
        "video" -> Icons.Default.VideoFile
        "archive" -> Icons.Default.FolderZip
        else -> Icons.Default.InsertDriveFile
    }
}

/** 获取类别显示名称 */
@Composable
fun getCategoryDisplayName(category: String): String { // This is called by CategoryItem, which is Composable
    return getCategoryDisplayNameComposable(category = category)
}

/** 获取格式图标 */
fun getFormatIcon(format: String): ImageVector {
    return when (format.lowercase()) {
        "pdf" -> Icons.Default.PictureAsPdf
        "docx" -> Icons.Default.Description
        "txt" -> Icons.Default.TextSnippet
        else -> Icons.Default.InsertDriveFile
    }
}
