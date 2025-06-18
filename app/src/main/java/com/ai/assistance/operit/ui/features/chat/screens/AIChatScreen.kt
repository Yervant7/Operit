package com.ai.assistance.operit.ui.features.chat.screens

import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R
import androidx.compose.ui.platform.LocalFocusManager
import com.ai.assistance.operit.data.model.AttachmentInfo
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.services.FloatingChatService
import com.ai.assistance.operit.ui.components.ErrorDialog
import com.ai.assistance.operit.ui.features.chat.components.*
import com.ai.assistance.operit.ui.features.chat.util.ConfigurationStateHolder
import com.ai.assistance.operit.ui.features.chat.viewmodel.ChatViewModel
import com.ai.assistance.operit.ui.features.chat.viewmodel.ChatViewModelFactory
import com.ai.assistance.operit.ui.main.screens.GestureStateHolder
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.sample

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIChatScreen(
        padding: PaddingValues,
        viewModel: ChatViewModel? = null,
        isFloatingMode: Boolean = false,
        onLoading: (Boolean) -> Unit = {},
        onError: (String) -> Unit = {},
        hasBackgroundImage: Boolean = false,
        onNavigateToTokenConfig: () -> Unit = {},
        onNavigateToSettings: () -> Unit = {},
        onGestureConsumed: (Boolean) -> Unit = {}
) {
        val context = LocalContext.current
        val density = LocalDensity.current

        // Initialize ViewModel without using viewModel() function
        val factory = ChatViewModelFactory(context)
        val actualViewModel = viewModel ?: remember { factory.create(ChatViewModel::class.java) }

        // Get background image state
        val preferencesManager = remember { UserPreferencesManager(context) }
        val useBackgroundImage by
                preferencesManager.useBackgroundImage.collectAsState(initial = false)
        val backgroundImageUri by
                preferencesManager.backgroundImageUri.collectAsState(initial = null)
        val hasBackgroundImage = useBackgroundImage && backgroundImageUri != null

        // 添加编辑按钮和编辑状态
        val isEditMode = remember { mutableStateOf(false) }
        val editingMessageIndex = remember { mutableStateOf<Int?>(null) }
        val editingMessageContent = remember { mutableStateOf("") }

        // Collect state from ViewModel
        val apiKey by actualViewModel.apiKey.collectAsState()
        val isConfigured by actualViewModel.isConfigured.collectAsState()
        val chatHistory by actualViewModel.chatHistory.collectAsState()
        val userMessage by actualViewModel.userMessage.collectAsState()
        val isLoading by actualViewModel.isLoading.collectAsState()
        val errorMessage by actualViewModel.errorMessage.collectAsState()
        val toolProgress by actualViewModel.toolProgress.collectAsState()
        val isProcessingInput by actualViewModel.isProcessingInput.collectAsState()
        val inputProcessingMessage by actualViewModel.inputProcessingMessage.collectAsState()
        val planItems by actualViewModel.planItems.collectAsState()
        val enableAiPlanning by actualViewModel.enableAiPlanning.collectAsState()
        val showChatHistorySelector by actualViewModel.showChatHistorySelector.collectAsState()
        val chatHistories by actualViewModel.chatHistories.collectAsState()
        val currentChatId by actualViewModel.currentChatId.collectAsState()
        val popupMessage by actualViewModel.popupMessage.collectAsState()
        val attachments by actualViewModel.attachments.collectAsState()
        // 收集附件面板状态
        val attachmentPanelState by actualViewModel.attachmentPanelState.collectAsState()
        // 收集滚动事件
        val scrollToBottomEvent = actualViewModel.scrollToBottomEvent

        // 添加WebView刷新相关状态
        val webViewNeedsRefresh by actualViewModel.webViewNeedsRefresh.collectAsState()

        // Floating window mode state
        val isFloatingMode by actualViewModel.isFloatingMode.collectAsState()
        val canDrawOverlays = remember { mutableStateOf(Settings.canDrawOverlays(context)) }

        // UI state
        val scrollState = rememberScrollState()
        val focusManager = LocalFocusManager.current
        val coroutineScope = rememberCoroutineScope()

        // 确保每次应用启动时正确处理配置界面的显示逻辑
        LaunchedEffect(apiKey) {
                // 只有当apiKey有效值时才执行逻辑，防止初始化阶段的不正确判断
                if (apiKey.isNotBlank()) {
                        // 如果使用的是自定义配置，标记为已确认，不显示配置界面
                        if (apiKey != ApiPreferences.DEFAULT_API_KEY) {
                                ConfigurationStateHolder.hasConfirmedDefaultInSession = true
                        }
                }
        }

        // Modern chat UI colors - Cursor风格
        val backgroundColor =
                if (hasBackgroundImage) Color.Transparent else MaterialTheme.colorScheme.background
        val userMessageColor = MaterialTheme.colorScheme.primaryContainer
        val aiMessageColor = MaterialTheme.colorScheme.surface
        val userTextColor = MaterialTheme.colorScheme.onPrimaryContainer
        val aiTextColor = MaterialTheme.colorScheme.onSurface
        val systemMessageColor = MaterialTheme.colorScheme.surfaceVariant
        val systemTextColor = MaterialTheme.colorScheme.onSurfaceVariant
        val thinkingBackgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        val thinkingTextColor = MaterialTheme.colorScheme.onSurfaceVariant

        // 滚动状态
        var autoScrollToBottom by remember { mutableStateOf(true) }
        var showScrollButton by remember { mutableStateOf(false) }

        // 核心滚动逻辑
        // 使用 LaunchedEffect(scrollState) 确保监听器在组件的整个生命周期内持续运行，
        // 避免因 chatHistory.size 变化而频繁重启，从而解决了 lastPosition 被意外重置的问题。
        LaunchedEffect(scrollState) {
                var lastPosition = scrollState.value
                snapshotFlow { scrollState.value }.collect { currentPosition ->
                        // isScrollInProgress 只在用户手动滚动或程序化动画期间为 true。
                        // 这可以有效过滤掉因内容变化导致的滚动位置"跳变"。
                        if (scrollState.isScrollInProgress) {
                                val scrolledUp = currentPosition < lastPosition
                                if (scrolledUp) {
                                        // 用户向上滚动，禁用自动滚动并显示按钮
                                        if (autoScrollToBottom) {
                                                Log.d("AIChatScreen", "用户向上滚动，禁用自动滚动")
                                                autoScrollToBottom = false
                                                showScrollButton = true
                                        }
                                } else {
                                        // 用户向下滚动，检查是否接近底部
                                        val isNearBottom =
                                                scrollState.maxValue - currentPosition < 200
                                        if (isNearBottom && !autoScrollToBottom) {
                                                Log.d("AIChatScreen", "用户滚动到底部，启用自动滚动")
                                                autoScrollToBottom = true
                                                showScrollButton = false
                                        }
                                }
                        }
                        // 持续更新 lastPosition，为下一次滚动事件做准备
                        lastPosition = currentPosition
                }
        }

        // 处理来自ViewModel的滚动事件（流式输出时）
        LaunchedEffect(Unit) {
                scrollToBottomEvent.collect {
                        if (autoScrollToBottom) {
                                try {
                                        scrollState.animateScrollTo(scrollState.maxValue)
                                } catch (e: Exception) {
                                        // Log.e("AIChatScreen", "自动滚动失败", e)
                                }
                        }
                }
        }

        // 自动滚动处理 - 仅在消息数量变化时触发
        LaunchedEffect(chatHistory.size) {
                if (autoScrollToBottom) {
                        try {
                                scrollState.animateScrollTo(scrollState.maxValue)
                        } catch (e: Exception) {
                                // Log.e("AIChatScreen", "自动滚动失败", e)
                        }
                }
        }

        // 启动和停止悬浮窗服务
        LaunchedEffect(isFloatingMode) {
                if (isFloatingMode) {
                        try {
                                val intent = Intent(context, FloatingChatService::class.java)
                                context.startService(intent)
                                Log.d("AIChatScreen", "悬浮窗服务已启动")
                        } catch (e: SecurityException) {
                                Log.e("AIChatScreen", "启动悬浮窗服务失败：权限问题", e)
                                actualViewModel.toggleFloatingMode()
                        }
                } else {
                        context.stopService(Intent(context, FloatingChatService::class.java))
                        Log.d("AIChatScreen", "悬浮窗服务已停止")
                }
        }

        // 当聊天记录变化时，更新悬浮窗内容（使用采样限流）
        LaunchedEffect(Unit) {
                snapshotFlow { chatHistory }
                        .sample(300L) // 每300毫秒采样一次，比debounce更适合流式场景
                        .distinctUntilChanged()
                        .collect { history ->
                                // 在收集器内部直接从StateFlow获取最新状态，避免竞态问题
                                if (actualViewModel.isFloatingMode.value) {
                                        val filteredMessages =
                                                history.filter { it.sender != "think" }
                                        actualViewModel.updateFloatingWindowMessages(
                                                filteredMessages
                                        )
                                }
                        }
        }

        // 移除原有的 snackbar 错误处理
        val snackbarHostState = remember { SnackbarHostState() }

        // 用新的错误弹窗替换原有的错误显示逻辑
        errorMessage?.let { message ->
                ErrorDialog(errorMessage = message, onDismiss = { actualViewModel.clearError() })
        }

        // 处理toast事件 (保留)
        val toastEvent by actualViewModel.toastEvent.collectAsState()

        toastEvent?.let { message ->
                LaunchedEffect(message) {
                        android.widget.Toast.makeText(
                                        context,
                                        message,
                                        android.widget.Toast.LENGTH_SHORT
                                )
                                .show()
                        actualViewModel.clearToastEvent()
                }
        }

        // Save chat on app exit
        DisposableEffect(Unit) {
                onDispose {
                        // This is handled by the ViewModel
                }
        }
        // 判断是否有默认配置可用
        val hasDefaultConfig = apiKey.isNotBlank()

        // 判断是否正在使用默认配置
        val isUsingDefaultConfig = actualViewModel.isUsingDefaultConfig()

        // 只有在使用默认配置且用户尚未在当前会话中确认使用默认配置时才显示配置界面
        val shouldShowConfig =
                isUsingDefaultConfig && !ConfigurationStateHolder.hasConfirmedDefaultInSession

        // 添加手势状态
        var chatScreenGestureConsumed by remember { mutableStateOf(false) }

        // 添加累计滑动距离变量
        var currentDrag by remember { mutableStateOf(0f) }
        var verticalDrag by remember { mutableStateOf(0f) }
        val dragThreshold = 40f // 与PhoneLayout保持一致

        // 收集WebView显示状态
        val showWebView by actualViewModel.showWebView.collectAsState()

        // 当手势状态改变时，通知父组件
        LaunchedEffect(chatScreenGestureConsumed, showWebView) {
                // 当WebView显示时，设置手势已消费状态为true，防止侧边栏滑出
                val finalGestureState = chatScreenGestureConsumed || showWebView
                // 同时更新全局状态持有者，确保PhoneLayout能够访问到状态
                GestureStateHolder.isChatScreenGestureConsumed = finalGestureState
                onGestureConsumed(finalGestureState)
        }

        // 处理文件选择器请求
        val fileChooserRequest by
                actualViewModel.uiStateDelegate.fileChooserRequest.collectAsState()
        val fileChooserLauncher =
                rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                        // 处理文件选择结果
                        actualViewModel.handleFileChooserResult(result.resultCode, result.data)
                        // 清除请求
                        actualViewModel.uiStateDelegate.clearFileChooserRequest()
                }

        // 启动文件选择器
        LaunchedEffect(fileChooserRequest) {
                fileChooserRequest?.let { fileChooserLauncher.launch(it) }
        }

        Scaffold(
                containerColor = Color.Transparent,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                // Add WindowInsets to modify the entire screen when keyboard appears
                contentWindowInsets = WindowInsets.ime,
                bottomBar = {
                        // 只在不显示配置界面时显示底部输入框
                        if (!shouldShowConfig) {
                                Column {
                                        // 添加优化和计划模式开关到输入框上方
                                        ChatSettingsBar(
                                                actualViewModel = actualViewModel,
                                                memoryOptimization =
                                                        actualViewModel.memoryOptimization
                                                                .collectAsState()
                                                                .value,
                                                masterPermissionLevel =
                                                        actualViewModel.masterPermissionLevel
                                                                .collectAsState()
                                                                .value,
                                                enableAiPlanning = enableAiPlanning
                                        )

                                        // 原有输入框区域
                                        ChatInputSection(
                                                userMessage = userMessage,
                                                onUserMessageChange = {
                                                        actualViewModel.updateUserMessage(it)
                                                },
                                                onSendMessage = {
                                                        actualViewModel.sendUserMessage()
                                                        // 在发送消息后重置附件面板状态
                                                        actualViewModel.resetAttachmentPanelState()
                                                },
                                                onCancelMessage = {
                                                        actualViewModel.cancelCurrentMessage()
                                                },
                                                isLoading = isLoading,
                                                isProcessingInput = isProcessingInput,
                                                inputProcessingMessage = inputProcessingMessage,
                                                allowTextInputWhileProcessing = true,
                                                onAttachmentRequest = { filePath ->
                                                        // 处理附件 - 现在使用文件路径而不是Uri
                                                        actualViewModel.handleAttachment(filePath)
                                                },
                                                attachments = attachments,
                                                onRemoveAttachment = { filePath ->
                                                        // 删除附件 - 现在使用文件路径而不是Uri
                                                        actualViewModel.removeAttachment(filePath)
                                                },
                                                onInsertAttachment = { attachment: AttachmentInfo ->
                                                        // 在光标位置插入附件引用
                                                        actualViewModel.insertAttachmentReference(
                                                                attachment
                                                        )
                                                },
                                                onAttachScreenContent = {
                                                        // 添加屏幕内容附件
                                                        actualViewModel.captureScreenContent()
                                                },
                                                onAttachNotifications = {
                                                        // 添加当前通知附件
                                                        actualViewModel.captureNotifications()
                                                },
                                                onAttachLocation = {
                                                        // 添加当前位置附件
                                                        actualViewModel.captureLocation()
                                                },
                                                onAttachProblemMemory = { content, filename ->
                                                        // 添加问题记忆附件
                                                        actualViewModel.attachProblemMemory(
                                                                content,
                                                                filename
                                                        )
                                                },
                                                hasBackgroundImage = hasBackgroundImage,
                                                // 传递附件面板状态
                                                externalAttachmentPanelState = attachmentPanelState,
                                                onAttachmentPanelStateChange = { newState ->
                                                        actualViewModel.updateAttachmentPanelState(
                                                                newState
                                                        )
                                                }
                                        )
                                }
                        }
                },
                floatingActionButton = {
                        // Remove the existing FAB since we now have the button in the header
                }
        ) { paddingValues ->
                // 根据前面的逻辑条件决定是否显示配置界面
                if (shouldShowConfig) {
                        ConfigurationScreen(
                                apiEndpoint = "",
                                apiKey = apiKey,
                                modelName = "",
                                onApiEndpointChange = {},
                                onApiKeyChange = { actualViewModel.updateApiKey(it) },
                                onModelNameChange = {},
                                onSaveConfig = {
                                        actualViewModel.saveApiSettings()
                                        // 保存配置后导航到聊天界面
                                        ConfigurationStateHolder.hasConfirmedDefaultInSession = true
                                },
                                onError = { error -> actualViewModel.showErrorMessage(error) },
                                coroutineScope = coroutineScope,
                                // 新增：使用默认配置的回调
                                onUseDefault = {
                                        actualViewModel.useDefaultConfig()
                                        // 确认使用默认配置后导航到聊天界面
                                        ConfigurationStateHolder.hasConfirmedDefaultInSession = true
                                },
                                // 标识是否在使用默认配置
                                isUsingDefault = isUsingDefaultConfig,
                                // 添加导航到聊天界面的回调
                                onNavigateToChat = {
                                        // 当用户设置了自己的配置后保存
                                        actualViewModel.saveApiSettings()
                                        // 确认后导航到聊天界面
                                        ConfigurationStateHolder.hasConfirmedDefaultInSession = true
                                },
                                // 添加导航到Token配置页面的回调
                                onNavigateToTokenConfig = onNavigateToTokenConfig,
                                // 添加导航到Settings页面的回调
                                onNavigateToSettings = onNavigateToSettings
                        )
                } else {
                        // 使用提取出来的聊天内容组件
                        ChatScreenContent(
                                paddingValues = paddingValues,
                                actualViewModel = actualViewModel,
                                showChatHistorySelector = showChatHistorySelector,
                                chatHistory = chatHistory,
                                planItems = planItems,
                                enableAiPlanning = enableAiPlanning,
                                toolProgress = toolProgress,
                                isLoading = isLoading,
                                userMessageColor = userMessageColor,
                                aiMessageColor = aiMessageColor,
                                userTextColor = userTextColor,
                                aiTextColor = aiTextColor,
                                systemMessageColor = systemMessageColor,
                                systemTextColor = systemTextColor,
                                thinkingBackgroundColor = thinkingBackgroundColor,
                                thinkingTextColor = thinkingTextColor,
                                hasBackgroundImage = hasBackgroundImage,
                                isEditMode = isEditMode,
                                editingMessageIndex = editingMessageIndex,
                                editingMessageContent = editingMessageContent,
                                chatScreenGestureConsumed = chatScreenGestureConsumed,
                                onChatScreenGestureConsumed = { chatScreenGestureConsumed = it },
                                currentDrag = currentDrag,
                                onCurrentDragChange = { currentDrag = it },
                                verticalDrag = verticalDrag,
                                onVerticalDragChange = { verticalDrag = it },
                                dragThreshold = dragThreshold,
                                scrollState = scrollState,
                                showScrollButton = showScrollButton,
                                onShowScrollButtonChange = { showScrollButton = it },
                                autoScrollToBottom = autoScrollToBottom,
                                onAutoScrollToBottomChange = { autoScrollToBottom = it },
                                coroutineScope = coroutineScope,
                                chatHistories = chatHistories,
                                currentChatId = currentChatId ?: "",
                                // 添加WebView刷新相关参数
                                webViewNeedsRefresh = webViewNeedsRefresh,
                                onWebViewRefreshed = { actualViewModel.resetWebViewRefreshState() }
                        )
                }
        }

        // Show popup message dialog when needed
        popupMessage?.let { message ->
                AlertDialog(
                        onDismissRequest = { actualViewModel.clearPopupMessage() },
                        title = { Text(stringResource(R.string.dialog_title_prompt)) },
                        text = { Text(message ?: "") },
                        confirmButton = {
                                TextButton(onClick = { actualViewModel.clearPopupMessage() }) {
                                        Text(stringResource(R.string.dialog_button_ok))
                                }
                        }
                )
        }

        // Check for overlay permission on resume
        LaunchedEffect(Unit) {
                canDrawOverlays.value = Settings.canDrawOverlays(context)

                // If floating mode is on but no permission, turn it off
                if (isFloatingMode && !canDrawOverlays.value) {
                        actualViewModel.toggleFloatingMode()
                        android.widget.Toast.makeText(
                                context,
                                context.getString(R.string.overlay_permission_denied_toast),
                                android.widget.Toast.LENGTH_SHORT
                        ).show()
                }
        }
}
