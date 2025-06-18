package com.ai.assistance.operit.ui.features.toolbox.screens.logcat

// 导入数据模型
// 导入管理器和ViewModel
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.ai.assistance.operit.R
import java.util.*
import kotlinx.coroutines.*

/** 日志查看器屏幕 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogcatScreen(navController: NavController? = null) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // 使用ViewModel
    val viewModel: LogcatViewModel = viewModel(factory = LogcatViewModel.Factory(context))

    // 从ViewModel获取状态
    val isCapturing by viewModel.isCapturing.collectAsState()
    val currentFilter by viewModel.currentFilter.collectAsState()
    val logRecords = viewModel.logRecords

    // 本地UI状态
    var filterInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showError by remember { mutableStateOf(false) }
    var levelFilter by remember { mutableStateOf<LogLevel?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var autoScroll by remember { mutableStateOf(true) }
    var showFilterOptions by remember { mutableStateOf(false) }

    // 界面折叠状态
    var showLevelFilters by remember { mutableStateOf(true) }
    var showPresetFilters by remember { mutableStateOf(false) }

    // 获取预设过滤器
    val presetFilters = remember { viewModel.getPresetFilters() }
    val presetsByCategory = remember(presetFilters) { presetFilters.groupBy { it.category } }

    // LazyColumn状态，用于自动滚动
    val listState = rememberLazyListState()
    val horizontalScrollState = rememberScrollState()

    // 根据级别和搜索词过滤日志
    val filteredLogs =
            remember(logRecords, levelFilter, searchQuery) {
                logRecords.filter { record ->
                    (levelFilter == null || record.level == levelFilter) &&
                            (searchQuery.isEmpty() ||
                                    record.message.contains(searchQuery, ignoreCase = true) ||
                                    (record.tag?.contains(searchQuery, ignoreCase = true) == true))
                }
            }

    // 自动滚动到底部
    LaunchedEffect(filteredLogs.size, autoScroll) {
        if (filteredLogs.isNotEmpty() && autoScroll) {
            listState.animateScrollToItem(filteredLogs.size - 1)
        }
    }

    Scaffold(
            topBar = {
                TopAppBar(
                        title = { Text(stringResource(R.string.logcat_title)) },
                        actions = {
                            // 保存日志按钮
                            val isSaving by viewModel.isSaving.collectAsState()
                            IconButton(
                                    onClick = {
                                        viewModel.saveLogsToFile(true, levelFilter, searchQuery)
                                    },
                                    enabled = !isSaving && logRecords.isNotEmpty()
                            ) {
                                Icon(
                                        imageVector =
                                                if (isSaving) Icons.Default.Pending
                                                else Icons.Default.Save,
                                        contentDescription = stringResource(R.string.logcat_save_logs_desc),
                                        tint =
                                                if (!isSaving && logRecords.isNotEmpty())
                                                        MaterialTheme.colorScheme.primary
                                                else
                                                        MaterialTheme.colorScheme.onSurface.copy(
                                                                alpha = 0.38f
                                                        )
                                )
                            }

                            // 搜索图标
                            IconButton(onClick = { showFilterOptions = !showFilterOptions }) {
                                Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = stringResource(R.string.logcat_filter_options_desc)
                                )
                            }

                            // 清除日志
                            IconButton(onClick = { viewModel.clearLogs() }) {
                                Icon(
                                        imageVector = Icons.Default.ClearAll,
                                        contentDescription = stringResource(R.string.logcat_clear_logs_desc)
                                )
                            }

                            // 开始/停止按钮
                            IconButton(
                                    onClick = {
                                        if (isCapturing) {
                                            viewModel.stopCapturing()
                                        } else {
                                            viewModel.startCapturing(filterInput)
                                        }
                                    }
                            ) {
                                Icon(
                                        imageVector =
                                                if (isCapturing) Icons.Default.Stop
                                                else Icons.Default.PlayArrow,
                                        contentDescription = if (isCapturing) stringResource(R.string.logcat_stop_capture_desc) else stringResource(R.string.logcat_start_capture_desc),
                                        tint =
                                                if (isCapturing) MaterialTheme.colorScheme.error
                                                else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                )
            },
            snackbarHost = {
                // 保存结果提示
                val saveResult by viewModel.saveResult.collectAsState()
                saveResult?.let { message ->
                    Snackbar(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        Text(message, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // 可展开的过滤选项面板 - 更紧凑的设计
            AnimatedVisibility(
                    visible = showFilterOptions,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
            ) {
                Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 1.dp) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        // 第一行：搜索和过滤输入
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // 搜索框 - 紧凑设计
                            CompactSearchField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = stringResource(R.string.logcat_search_placeholder),
                                    modifier = Modifier.weight(1f),
                                    leadingIcon = {
                                        Icon(
                                                imageVector = Icons.Default.Search,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    trailingIcon =
                                            if (searchQuery.isNotEmpty()) {
                                                {
                                                    IconButton(
                                                            onClick = { searchQuery = "" },
                                                            modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(
                                                                Icons.Default.Clear,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                }
                                            } else null
                            )

                            // 过滤输入框 - 紧凑设计
                            CompactSearchField(
                                    value = filterInput,
                                    onValueChange = { filterInput = it },
                                    placeholder = stringResource(R.string.logcat_filter_placeholder),
                                    modifier = Modifier.weight(1f),
                                    trailingIcon = {
                                        IconButton(
                                                onClick = {
                                                    if (isCapturing) {
                                                        viewModel.stopCapturing()
                                                        viewModel.startCapturing(filterInput)
                                                    } else {
                                                        viewModel.startCapturing(filterInput)
                                                    }
                                                    focusManager.clearFocus()
                                                },
                                                modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                            )
                        }

                        // 日志级别过滤区域
                        CollapsibleSectionHeader(
                                title = stringResource(R.string.logcat_filter_level_header),
                                expanded = showLevelFilters,
                                onToggle = { showLevelFilters = !showLevelFilters }
                        )

                        AnimatedVisibility(visible = showLevelFilters) {
                            // 级别过滤器行 - 紧凑设计
                            Row(
                                    modifier =
                                            Modifier.fillMaxWidth()
                                                    .padding(vertical = 2.dp)
                                                    .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 全部级别
                                FilterChip(
                                        selected = levelFilter == null,
                                        onClick = { levelFilter = null },
                                        label = { Text(stringResource(R.string.logcat_level_all), fontSize = 11.sp) },
                                        modifier = Modifier.height(24.dp)
                                )

                                // 各个级别的过滤器
                                listOf(
                                                LogLevel.VERBOSE,
                                                LogLevel.DEBUG,
                                                LogLevel.INFO,
                                                LogLevel.WARNING,
                                                LogLevel.ERROR,
                                                LogLevel.FATAL
                                        )
                                        .forEach { level ->
                                            FilterChip(
                                                    selected = levelFilter == level,
                                                    onClick = {
                                                        levelFilter =
                                                                if (levelFilter == level) null
                                                                else level
                                                    },
                                                    label = {
                                        Text(stringResource(getIdForLogLevel(level)), fontSize = 11.sp)
                                                    },
                                                    modifier = Modifier.height(24.dp),
                                                    colors =
                                                            FilterChipDefaults.filterChipColors(
                                                                    selectedContainerColor =
                                                                            level.color.copy(
                                                                                    alpha = 0.2f
                                                                            ),
                                                                    selectedLabelColor = level.color
                                                            )
                                            )
                                        }

                                // 自动滚动
                                Spacer(modifier = Modifier.width(8.dp))

                                Switch(
                                        checked = autoScroll,
                                        onCheckedChange = { autoScroll = it },
                                        modifier = Modifier.scale(0.7f)
                                )

                                Text(
                                        stringResource(R.string.logcat_auto_scroll),
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(end = 8.dp)
                                )
                            }
                        }

                        // 预设过滤器区域
                        if (presetFilters.isNotEmpty()) {
                            CollapsibleSectionHeader(
                                    title = stringResource(R.string.logcat_preset_filters_header),
                                    expanded = showPresetFilters,
                                    onToggle = { showPresetFilters = !showPresetFilters }
                            )

                            AnimatedVisibility(visible = showPresetFilters) {
                                Row(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .horizontalScroll(rememberScrollState())
                                                        .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    presetFilters.forEach { filter ->
                                        SuggestionChip(
                                                onClick = {
                                                    filterInput = filter.filter
                                                    if (isCapturing) {
                                                        viewModel.stopCapturing()
                                                        viewModel.startCapturing(filter.filter)
                                                    } else {
                                                        viewModel.startCapturing(filter.filter)
                                                    }
                                                },
                                                label = { Text(filter.name, fontSize = 11.sp) },
                                                icon = {
                                                    Icon(
                                                            imageVector = filter.icon,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 状态指示条 - 极度紧凑设计
            Row(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .background(
                                            if (isCapturing)
                                                    MaterialTheme.colorScheme.primaryContainer.copy(
                                                            alpha = 0.5f
                                                    )
                                            else
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(
                                                            alpha = 0.5f
                                                    )
                                    )
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                if (isCapturing) {
                    CircularProgressIndicator(
                            modifier = Modifier.size(10.dp),
                            strokeWidth = 1.5.dp,
                            color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(10.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Text(
                        if (isCapturing) stringResource(R.string.logcat_status_capturing) else stringResource(R.string.logcat_status_stopped),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.width(4.dp))

                Text(
                        "(${filteredLogs.size})",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.weight(1f))

                // 如有过滤器，则显示
                if (searchQuery.isNotEmpty()) {
                    Text(
                            stringResource(R.string.logcat_status_search_query, searchQuery),
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary
                    )
                }

                if (levelFilter != null) {
                    Spacer(modifier = Modifier.width(4.dp))

                    Box(
                            modifier =
                                    Modifier.background(
                                                    levelFilter!!.color.copy(alpha = 0.2f),
                                                    RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                                stringResource(getIdForLogLevel(levelFilter!!)),
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 9.sp,
                                color = levelFilter!!.color,
                                fontWeight = FontWeight.Bold
                        )
                    }
                }

                // 显示当前过滤条件
                if (currentFilter.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                            stringResource(R.string.logcat_status_filter_active, currentFilter),
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 1
                    )
                }
            }

            // 日志列表区域
            Box(modifier = Modifier.weight(1f)) {
                if (filteredLogs.isEmpty()) {
                    // 空状态显示 - 更简洁
                    Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                                imageVector = Icons.Default.DataObject,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                modifier = Modifier.size(40.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                                text = if (isCapturing) stringResource(R.string.logcat_empty_waiting) else stringResource(R.string.logcat_empty_start_prompt),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    // 日志列表
                    LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.fillMaxSize()
                    ) {
                        items(items = filteredLogs) { record -> LogRecordItem(record = record) }

                        item { Spacer(modifier = Modifier.height(60.dp)) }
                    }

                    // 滚动到底部按钮
                    Box(modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                        androidx.compose.animation.AnimatedVisibility(
                                visible = !autoScroll && filteredLogs.size > 10
                        ) {
                            FloatingActionButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            listState.animateScrollToItem(filteredLogs.size - 1)
                                        }
                                    },
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                        imageVector = Icons.Default.ArrowDownward,
                                        contentDescription = stringResource(R.string.logcat_scroll_to_bottom_desc),
                                        modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 错误提示
    if (showError && errorMessage != null) {
        AlertDialog(
                onDismissRequest = { showError = false },
                title = { Text(stringResource(R.string.logcat_error_dialog_title)) },
                text = { Text(errorMessage!!) },
                confirmButton = { TextButton(onClick = { showError = false }) { Text(stringResource(R.string.logcat_dialog_ok_button)) } }
        )
    }
}

@Composable
private fun getIdForLogLevel(level: LogLevel): Int {
    return when (level) {
        LogLevel.VERBOSE -> R.string.log_level_verbose
        LogLevel.DEBUG -> R.string.log_level_debug
        LogLevel.INFO -> R.string.log_level_info
        LogLevel.WARNING -> R.string.log_level_warning
        LogLevel.ERROR -> R.string.log_level_error
        LogLevel.FATAL -> R.string.log_level_fatal
        LogLevel.SILENT -> R.string.log_level_silent
        else -> R.string.log_level_unknown
    }
}

// Helper function to get string resource ID for FilterCategory (if needed in UI)
@Composable
private fun getIdForFilterCategory(category: FilterCategory): Int {
    return when (category) {
        FilterCategory.LEVEL -> R.string.filter_category_level
        FilterCategory.SYSTEM -> R.string.filter_category_system
        FilterCategory.APP -> R.string.filter_category_app
        FilterCategory.CUSTOM -> R.string.filter_category_custom
        // Add cases for other categories if they exist and need display names
    }
}
