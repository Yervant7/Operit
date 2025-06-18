package com.ai.assistance.operit.ui.features.toolbox.screens.logcat

import android.content.Context
import android.os.Environment
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.system.AndroidShellExecutor

/** 日志查看器ViewModel，用于管理日志状态，确保在界面间导航时保持日志捕获状态 */
class LogcatViewModel(private val context: Context) : ViewModel() {
    // 日志管理器
    private val logcatManager = LogcatManager(context)

    // 日志记录
    private val _logRecords = mutableStateListOf<LogRecord>()
    val logRecords: List<LogRecord>
        get() = _logRecords

    // 是否正在捕获
    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    // 当前过滤条件
    private val _currentFilter = MutableStateFlow("")
    val currentFilter: StateFlow<String> = _currentFilter.asStateFlow()

    // 保存日志状态
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    // 保存结果消息
    private val _saveResult = MutableStateFlow<String?>(null)
    val saveResult: StateFlow<String?> = _saveResult.asStateFlow()

    /** 开始捕获日志 */
    fun startCapturing(filter: String = "") {
        if (_isCapturing.value) {
            stopCapturing()
            // 等待停止操作完成
            viewModelScope.launch {
                delay(300) // 短暂等待以确保先前的捕获已停止
                startCapturingInternal(filter)
            }
        } else {
            startCapturingInternal(filter)
        }
    }

    /** 内部实际开始捕获日志的方法 */
    private fun startCapturingInternal(filter: String) {
        _logRecords.clear()
        _currentFilter.value = filter

        viewModelScope.launch {
            val job = logcatManager.startLogcat(
                    onNewLog = { logRecord ->
                        _logRecords.add(logRecord)

                        // 避免内存占用过大，保留最新的1000条
                        // 批量移除以提高性能
                        if (_logRecords.size > 1200) { // 设置一个缓冲区，避免频繁移除
                            val toRemove = _logRecords.size - 1000
                            viewModelScope.launch(Dispatchers.Default) {
                                synchronized(_logRecords) {
                                    if (_logRecords.size > toRemove) {
                                        repeat(toRemove) { _logRecords.removeAt(0) }
                                    }
                                }
                            }
                        }
                    },
                    filter = filter
            )
            
            // 只有在成功启动捕获时才设置为true
            if (job != null) {
                _isCapturing.value = true
            } else {
                // 如果启动失败，显示一个空的错误日志
                _logRecords.add(
                    LogRecord(
                        message = context.getString(R.string.logcat_error_cannot_start),
                        level = LogLevel.ERROR,
                        timestamp = System.currentTimeMillis(),
                        tag = "LogcatError"
                    )
                )
            }
        }
    }

    /** 停止捕获日志 */
    fun stopCapturing() {
        if (!_isCapturing.value) return

        viewModelScope.launch {
            withContext(Dispatchers.IO) { logcatManager.stopLogcat() }
            _isCapturing.value = false
        }
    }

    /** 清除日志 */
    fun clearLogs() {
        logcatManager.clearLogcat()
        _logRecords.clear()
    }

    /** 获取预设过滤器 */
    fun getPresetFilters(): List<PresetFilter> {
        return logcatManager.getPresetFilters()
    }

    /**
     * 将当前日志保存到文件
     * @param onlyFiltered 是否只保存过滤后的日志
     * @param levelFilter 级别过滤器
     * @param searchQuery 搜索查询
     */
    fun saveLogsToFile(
            onlyFiltered: Boolean = false,
            levelFilter: LogLevel? = null,
            searchQuery: String = ""
    ) {
        if (_isSaving.value) return

        _isSaving.value = true
        _saveResult.value = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 创建文件名
                val timestamp =
                        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "operit_log_$timestamp.txt"

                // 过滤日志（如果需要）
                val logsToSave =
                        if (onlyFiltered) {
                            _logRecords.filter { record ->
                                (levelFilter == null || record.level == levelFilter) &&
                                        (searchQuery.isEmpty() ||
                                                record.message.contains(
                                                        searchQuery,
                                                        ignoreCase = true
                                                ) ||
                                                (record.tag?.contains(
                                                        searchQuery,
                                                        ignoreCase = true
                                                ) == true))
                            }
                        } else {
                            _logRecords
                        }
                    
                if (logsToSave.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        _saveResult.value = context.getString(R.string.logcat_save_no_logs)
                        delay(3000) 
                        _saveResult.value = null
                    }
                    _isSaving.value = false
                    return@launch
                }

                // 准备日志内容
                val logContent = StringBuilder()
                // 写入头部信息
                logContent.append(context.getString(R.string.logcat_save_header_title) + "\n")
                logContent.append(
                        context.getString(R.string.logcat_save_header_date, SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())) + "\n"
                )
                logContent.append(context.getString(R.string.logcat_save_header_filter, _currentFilter.value) + "\n")
                logContent.append(context.getString(R.string.logcat_save_header_count, logsToSave.size) + "\n")
                logContent.append(context.getString(R.string.logcat_save_header_separator) + "\n\n")

                // 写入日志
                logsToSave.forEach { record ->
                    val timestamp =
                            SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
                                    .format(Date(record.timestamp))
                    val tag = record.tag?.let { "[$it]" } ?: ""
                    val level = record.level.symbol
                    logContent.append("$timestamp $level $tag ${record.message}\n")
                }

                // 尝试两种方式保存文件
                val filePath = try {
                    // 检查Android版本并决定使用哪种保存方法
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        saveUsingMediaStore(fileName, logContent.toString())
                    } else {
                        saveUsingFileSystem(fileName, logContent.toString())
                    }
                } catch (e: Exception) {
                    // 捕获保存过程中的特定异常，返回明确的错误信息
                    context.getString(R.string.logcat_save_failed_generic, e.message ?: context.getString(R.string.unknown_error))
                }

                withContext(Dispatchers.Main) {
                    // 检查保存路径是否包含错误信息
                    if (filePath.startsWith(context.getString(R.string.logcat_save_failed_generic, "").substringBefore(":"))) { // Check for "保存失败" prefix
                        _saveResult.value = filePath
                    } else {
                        _saveResult.value = context.getString(R.string.logcat_save_success, filePath)
                    }
                    delay(3000) // 3秒后清除结果信息
                    _saveResult.value = null
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _saveResult.value = context.getString(R.string.logcat_save_failed_generic, e.message ?: context.getString(R.string.unknown_error))
                    delay(3000) // 3秒后清除结果信息
                    _saveResult.value = null
                }
            } finally {
                _isSaving.value = false
            }
        }
    }

    /** 使用MediaStore API保存文件（适用于Android 10+） */
    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.Q)
    private fun saveUsingMediaStore(fileName: String, content: String): String {
        try {
            val contentValues =
                    android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                        put(
                                android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                                "${android.os.Environment.DIRECTORY_DOWNLOADS}/operit"
                        )
                    }

            val uri =
                    context.contentResolver.insert(
                            android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            contentValues
                    )

            if (uri == null) {
                return context.getString(R.string.logcat_save_error_create_file_permission)
            }

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(content.toByteArray())
            } ?: throw Exception(context.getString(R.string.logcat_save_error_output_stream))

            // 成功写入后，查询实际路径用于显示
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            return "${downloadsDir.absolutePath}/operit/$fileName"
        } catch (e: Exception) {
            throw Exception(context.getString(R.string.logcat_save_error_mediastore, e.message ?: ""))
        }
    }

    /** 使用传统文件系统API保存文件（适用于Android 9及以下） */
    private fun saveUsingFileSystem(fileName: String, content: String): String {
        try {
            // 获取文件夹
            val downloadsDir =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            
            if (downloadsDir == null || !downloadsDir.exists() && !downloadsDir.mkdirs()) {
                throw Exception(context.getString(R.string.logcat_save_error_create_download_dir))
            }
            
            val operitDir = File(downloadsDir, "operit")

            // 确保目录存在
            if (!operitDir.exists() && !operitDir.mkdirs()) {
                throw Exception(context.getString(R.string.logcat_save_error_create_operit_dir))
            }

            // 创建文件
            val file = File(operitDir, fileName)

            // 写入文件
            FileWriter(file).use { writer -> writer.write(content) }

            if (!file.exists() || file.length() == 0L) {
                throw Exception(context.getString(R.string.logcat_save_error_file_empty))
            }

            return file.absolutePath
        } catch (e: Exception) {
            throw Exception(context.getString(R.string.logcat_save_error_filesystem, e.message ?: ""))
        }
    }

    /** ViewModel工厂，用于创建ViewModel实例 */
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LogcatViewModel::class.java)) {
                return LogcatViewModel(context) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    /** 清理资源 */
    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch(Dispatchers.IO) { logcatManager.stopLogcat() }
    }
}
