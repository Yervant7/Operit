package com.ai.assistance.operit.ui.features.settings.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import kotlinx.coroutines.launch

// 保存滑动状态变量，使其跨重组保持
private val SettingsScreenScrollPosition = mutableStateOf(0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
        onNavigateToUserPreferences: () -> Unit,
        navigateToToolPermissions: () -> Unit,
        navigateToModelConfig: () -> Unit,
        navigateToThemeSettings: () -> Unit,
        navigateToModelPrompts: () -> Unit,
        navigateToFunctionalConfig: () -> Unit,
        navigateToChatHistorySettings: () -> Unit,
        navigateToLanguageSettings: () -> Unit
) {
        val context = LocalContext.current
        val apiPreferences = remember { ApiPreferences(context) }
        val scope = rememberCoroutineScope()

        // 创建和记住滚动状态，设置为上次保存的位置
        val scrollState = rememberScrollState(SettingsScreenScrollPosition.value)

        // 当滚动状态改变时更新保存的位置
        LaunchedEffect(scrollState) {
                snapshotFlow { scrollState.value }.collect { position ->
                        SettingsScreenScrollPosition.value = position
                }
        }

        // Collect API settings as state
        val apiKey = apiPreferences.apiKeyFlow.collectAsState(initial = "").value
        val apiEndpoint =
                apiPreferences.apiEndpointFlow.collectAsState(
                                initial = ApiPreferences.DEFAULT_API_ENDPOINT
                        )
                        .value
        val modelName =
                apiPreferences.modelNameFlow.collectAsState(
                                initial = ApiPreferences.DEFAULT_MODEL_NAME
                        )
                        .value
        val memoryOptimization =
                apiPreferences.memoryOptimizationFlow.collectAsState(
                                initial = ApiPreferences.DEFAULT_MEMORY_OPTIMIZATION
                        )
                        .value
        val showFpsCounter =
                apiPreferences.showFpsCounterFlow.collectAsState(
                                initial = ApiPreferences.DEFAULT_SHOW_FPS_COUNTER
                        )
                        .value
        val autoGrantAccessibility =
                apiPreferences.autoGrantAccessibilityFlow.collectAsState(
                                initial = ApiPreferences.DEFAULT_AUTO_GRANT_ACCESSIBILITY
                        )
                        .value

        // Mutable state for editing
        var apiKeyInput by remember { mutableStateOf(apiKey) }
        var apiEndpointInput by remember { mutableStateOf(apiEndpoint) }
        var modelNameInput by remember { mutableStateOf(modelName) }
        var memoryOptimizationInput by remember { mutableStateOf(memoryOptimization) }
        var showFpsCounterInput by remember { mutableStateOf(showFpsCounter) }
        var autoGrantAccessibilityInput by remember { mutableStateOf(autoGrantAccessibility) }
        var showSaveSuccessMessage by remember { mutableStateOf(false) }

        // Add state for endpoint warning
        var endpointWarningMessage by remember { mutableStateOf<String?>(null) }
        var showEndpointWarning by remember { mutableStateOf(false) }

        // Add state to check if using default API key
        val isUsingDefaultApiKey = apiKeyInput == ApiPreferences.DEFAULT_API_KEY
        var showModelRestrictionInfo by remember { mutableStateOf(isUsingDefaultApiKey) }

        // Force deepseek-chat model when using default API key
        LaunchedEffect(apiKeyInput) {
                if (apiKeyInput == ApiPreferences.DEFAULT_API_KEY) {
                        modelNameInput = ApiPreferences.DEFAULT_MODEL_NAME
                        showModelRestrictionInfo = true
                } else {
                        showModelRestrictionInfo = false
                }
        }

        // Update local state when preferences change
        LaunchedEffect(
                apiKey,
                apiEndpoint,
                modelName,
                memoryOptimization,
                showFpsCounter,
                autoGrantAccessibility
        ) {
                apiKeyInput = apiKey
                apiEndpointInput = apiEndpoint
                modelNameInput = modelName
                memoryOptimizationInput = memoryOptimization
                showFpsCounterInput = showFpsCounter
                autoGrantAccessibilityInput = autoGrantAccessibility
        }

        Column(
                modifier =
                        Modifier.fillMaxSize()
                                .padding(16.dp)
                                .verticalScroll(scrollState) // 使用保存的滚动状态
        ) {
                // ======= SECTION 1: PERSONALIZATION =======
                SettingsSectionTitle(
                        title = stringResource(id = R.string.settings_section_personalization),
                        icon = Icons.Default.Person
                )

                // 用户偏好设置
                SettingsCard(
                        title = stringResource(id = R.string.settings_user_preferences),
                        description = stringResource(id = R.string.settings_user_preferences_desc),
                        onClick = onNavigateToUserPreferences,
                        buttonText = stringResource(id = R.string.settings_configure_preferences),
                        icon = Icons.Default.Face
                )

                // 模型提示词设置
                SettingsCard(
                        title = stringResource(id = R.string.settings_model_prompts),
                        description = stringResource(id = R.string.settings_model_prompts_desc),
                        onClick = navigateToModelPrompts,
                        buttonText = stringResource(id = R.string.settings_configure_prompts),
                        icon = Icons.Default.Message
                )

                // 主题设置
                SettingsCard(
                        title = stringResource(id = R.string.settings_theme_appearance),
                        description = stringResource(id = R.string.settings_theme_appearance_desc),
                        onClick = navigateToThemeSettings,
                        buttonText = stringResource(id = R.string.settings_customize_theme),
                        icon = Icons.Default.Palette
                )

                // 语言设置
                SettingsCard(
                        title = stringResource(R.string.language_settings),
                        description = stringResource(R.string.language_settings_desc),
                        onClick = navigateToLanguageSettings,
                        buttonText = stringResource(R.string.change_language),
                        icon = Icons.Default.Language
                )

                // 聊天记录管理
                SettingsCard(
                        title = stringResource(id = R.string.settings_data_backup),
                        description = stringResource(id = R.string.settings_data_backup_desc),
                        onClick = navigateToChatHistorySettings,
                        buttonText = stringResource(id = R.string.settings_manage_backup),
                        icon = Icons.Default.History
                )

                // ======= SECTION 2: AI MODEL CONFIGURATION =======
                SettingsSectionTitle(
                        title = stringResource(id = R.string.settings_section_ai_model),
                        icon = Icons.Default.Settings
                )

                // 模型与参数配置卡片
                SettingsCard(
                        title = stringResource(id = R.string.settings_model_parameters),
                        description = stringResource(id = R.string.settings_model_parameters_desc),
                        onClick = navigateToModelConfig,
                        buttonText = stringResource(id = R.string.settings_configure_model),
                        icon = Icons.Default.Api
                )

                // 功能模型配置卡片
                SettingsCard(
                        title = stringResource(id = R.string.settings_functional_model),
                        description = stringResource(id = R.string.settings_functional_model_desc),
                        onClick = navigateToFunctionalConfig,
                        buttonText = stringResource(id = R.string.settings_configure_function),
                        icon = Icons.Default.Tune
                )

                // ======= SECTION 3: DISPLAY AND BEHAVIOR =======
                SettingsSectionTitle(
                        title = stringResource(id = R.string.settings_section_display),
                        icon = Icons.Default.Visibility
                )

                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(bottom = 16.dp)
                                ) {
                                        Icon(
                                                imageVector = Icons.Default.DisplaySettings,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                                text =
                                                        stringResource(
                                                                id = R.string.display_settings
                                                        ),
                                                style = MaterialTheme.typography.titleMedium
                                        )
                                }

                                // 记忆优化开关
                                SettingsToggle(
                                        title =
                                                stringResource(
                                                        id = R.string.settings_memory_optimization
                                                ),
                                        description =
                                                stringResource(
                                                        id =
                                                                R.string
                                                                        .settings_memory_optimization_desc
                                                ),
                                        checked = memoryOptimizationInput,
                                        onCheckedChange = {
                                                memoryOptimizationInput = it
                                                scope.launch {
                                                        apiPreferences.saveMemoryOptimization(it)
                                                        showSaveSuccessMessage = true
                                                }
                                        }
                                )

                                // 帧率显示开关
                                SettingsToggle(
                                        title = stringResource(id = R.string.show_fps_counter),
                                        description =
                                                stringResource(
                                                        id = R.string.fps_counter_description
                                                ),
                                        checked = showFpsCounterInput,
                                        onCheckedChange = {
                                                showFpsCounterInput = it
                                                scope.launch {
                                                        apiPreferences.saveShowFpsCounter(it)
                                                        showSaveSuccessMessage = true
                                                }
                                        }
                                )

                                // 自动授予无障碍权限开关
                                SettingsToggle(
                                        title =
                                                stringResource(
                                                        id = R.string.settings_auto_accessibility
                                                ),
                                        description =
                                                stringResource(
                                                        id =
                                                                R.string
                                                                        .settings_auto_accessibility_desc
                                                ),
                                        checked = autoGrantAccessibilityInput,
                                        onCheckedChange = {
                                                autoGrantAccessibilityInput = it
                                                scope.launch {
                                                        apiPreferences.saveAutoGrantAccessibility(
                                                                it
                                                        )
                                                        showSaveSuccessMessage = true
                                                }
                                        }
                                )
                        }
                }

                // ======= SECTION 4: PERMISSIONS AND SECURITY =======
                SettingsSectionTitle(
                        title = stringResource(id = R.string.settings_section_permissions),
                        icon = Icons.Default.Security
                )

                // 工具权限设置卡片
                SettingsCard(
                        title = stringResource(id = R.string.settings_tool_permissions),
                        description = stringResource(id = R.string.settings_tool_permissions_desc),
                        onClick = navigateToToolPermissions,
                        buttonText = stringResource(id = R.string.settings_configure_permissions),
                        icon = Icons.Default.AdminPanelSettings
                )

                // ======= SECTION 5: USAGE STATISTICS =======
                SettingsSectionTitle(
                        title = stringResource(id = R.string.settings_section_usage),
                        icon = Icons.Default.Analytics
                )

                // Token统计卡片
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(bottom = 16.dp)
                                ) {
                                        Icon(
                                                imageVector = Icons.Default.DataUsage,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                                text =
                                                        stringResource(
                                                                id =
                                                                        R.string
                                                                                .settings_token_statistics
                                                        ),
                                                style = MaterialTheme.typography.titleMedium
                                        )
                                }

                                // 从数据源读取token统计数据
                                val chatHistories = remember {
                                        ChatHistoryManager.getInstance(context)
                                }
                                var totalInputTokens by remember { mutableStateOf(0) }
                                var totalOutputTokens by remember { mutableStateOf(0) }
                                var preferenceAnalysisInputTokens by remember { mutableStateOf(0) }
                                var preferenceAnalysisOutputTokens by remember { mutableStateOf(0) }

                                // 读取和统计token数据
                                LaunchedEffect(Unit) {
                                        // 聊天历史token计数
                                        chatHistories.chatHistoriesFlow.collect { histories ->
                                                totalInputTokens =
                                                        histories.sumOf { it.inputTokens }
                                                totalOutputTokens =
                                                        histories.sumOf { it.outputTokens }
                                        }
                                }

                                LaunchedEffect(Unit) {
                                        apiPreferences.preferenceAnalysisInputTokensFlow.collect {
                                                tokens ->
                                                preferenceAnalysisInputTokens = tokens
                                        }
                                }

                                LaunchedEffect(Unit) {
                                        apiPreferences.preferenceAnalysisOutputTokensFlow.collect {
                                                tokens ->
                                                preferenceAnalysisOutputTokens = tokens
                                        }
                                }

                                // 计算费用
                                val usdToRmbRate = 7.2
                                val chatInputCost =
                                        totalInputTokens * 0.27 / 1_000_000 * usdToRmbRate
                                val chatOutputCost =
                                        totalOutputTokens * 1.10 / 1_000_000 * usdToRmbRate
                                val preferenceAnalysisInputCost =
                                        preferenceAnalysisInputTokens * 0.27 / 1_000_000 *
                                                usdToRmbRate
                                val preferenceAnalysisOutputCost =
                                        preferenceAnalysisOutputTokens * 1.10 / 1_000_000 *
                                                usdToRmbRate
                                val totalCost =
                                        chatInputCost +
                                                chatOutputCost +
                                                preferenceAnalysisInputCost +
                                                preferenceAnalysisOutputCost

                                // 统计信息表格
                                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                        TokenStatRow(
                                                label =
                                                        stringResource(
                                                                id =
                                                                        R.string
                                                                                .settings_chat_input_token
                                                        ),
                                                value = totalInputTokens.toString(),
                                                cost = stringResource(R.string.currency_format_cny, chatInputCost)
                                        )

                                        TokenStatRow(
                                                label =
                                                        stringResource(
                                                                id =
                                                                        R.string
                                                                                .settings_chat_output_token
                                                        ),
                                                value = totalOutputTokens.toString(),
                                                cost = stringResource(R.string.currency_format_cny, chatOutputCost)
                                        )

                                        TokenStatRow(
                                                label =
                                                        stringResource(
                                                                id =
                                                                        R.string
                                                                                .settings_preference_input_token
                                                        ),
                                                value = preferenceAnalysisInputTokens.toString(),
                                                cost =
                                                        stringResource(R.string.currency_format_cny, preferenceAnalysisInputCost)
                                        )

                                        TokenStatRow(
                                                label =
                                                        stringResource(
                                                                id =
                                                                        R.string
                                                                                .settings_preference_output_token
                                                        ),
                                                value = preferenceAnalysisOutputTokens.toString(),
                                                cost =
                                                        stringResource(R.string.currency_format_cny, preferenceAnalysisOutputCost)
                                        )

                                        Divider(modifier = Modifier.padding(vertical = 8.dp))

                                        TokenStatRow(
                                                label =
                                                        stringResource(
                                                                id = R.string.settings_total
                                                        ),
                                                value =
                                                        (totalInputTokens +
                                                                        totalOutputTokens +
                                                                        preferenceAnalysisInputTokens +
                                                                        preferenceAnalysisOutputTokens)
                                                                .toString(),
                                                cost = stringResource(R.string.currency_format_cny, totalCost),
                                                isHighlighted = true
                                        )
                                }

                                // 重置按钮
                                Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                        horizontalArrangement = Arrangement.End
                                ) {
                                        OutlinedButton(
                                                onClick = {
                                                        scope.launch {
                                                                apiPreferences
                                                                        .resetPreferenceAnalysisTokens()
                                                                showSaveSuccessMessage = true
                                                        }
                                                }
                                        ) {
                                                Text(
                                                        stringResource(
                                                                id =
                                                                        R.string
                                                                                .settings_reset_analysis_count
                                                        )
                                                )
                                        }
                                }

                                // 幽默解释
                                Card(
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                        colors =
                                                CardDefaults.cardColors(
                                                        containerColor =
                                                                MaterialTheme.colorScheme
                                                                        .surfaceVariant.copy(
                                                                        alpha = 0.5f
                                                                )
                                                )
                                ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                                Text(
                                                        text =
                                                                stringResource(
                                                                        id =
                                                                                R.string
                                                                                        .settings_why_token_stats
                                                                ),
                                                        style =
                                                                MaterialTheme.typography.bodyMedium
                                                                        .copy(
                                                                                fontWeight =
                                                                                        FontWeight
                                                                                                .Bold
                                                                        ),
                                                        modifier = Modifier.padding(bottom = 4.dp)
                                                )

                                                Text(
                                                        text =
                                                                stringResource(
                                                                        id =
                                                                                R.string
                                                                                        .settings_token_stats_explanation
                                                                ),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant
                                                )
                                        }
                                }
                        }
                }
        }
}

@Composable
private fun SettingsCard(
        title: String,
        description: String,
        onClick: () -> Unit,
        buttonText: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector
) {
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(
                        modifier =
                                Modifier.fillMaxWidth() // 确保Column填满Card的宽度
                                        .padding(16.dp)
                ) {
                        Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                                Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = title, style = MaterialTheme.typography.titleMedium)
                        }

                        Text(
                                text = description,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 16.dp)
                        )

                        // 使用Row替代Button的align(End)
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                        ) { Button(onClick = onClick) { Text(buttonText) } }
                }
        }
}

@Composable
private fun SettingsToggle(
        title: String,
        description: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit
) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
        ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(text = title, style = MaterialTheme.typography.bodyMedium)
                        Text(
                                text = description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                }

                Switch(
                        checked = checked,
                        onCheckedChange = onCheckedChange,
                        modifier = Modifier.padding(top = 4.dp)
                )
        }
}

@Composable
private fun SettingsSectionTitle(
        title: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector
) {
        Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 8.dp)
        ) {
                Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                )
        }
        Divider(modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun TokenStatRow(
        label: String,
        value: String,
        cost: String,
        isHighlighted: Boolean = false
) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
                Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color =
                                if (isHighlighted) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(0.6f), // 限制文本宽度为60%
                        maxLines = 2, // 允许最多两行
                        softWrap = true // 允许换行
                )

                Row(modifier = Modifier.weight(0.4f), horizontalArrangement = Arrangement.End) {
                        Text(
                                text = value,
                                style =
                                        MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight =
                                                        if (isHighlighted) FontWeight.Bold
                                                        else FontWeight.Normal
                                        ),
                                color =
                                        if (isHighlighted) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.width(IntrinsicSize.Min)
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Text(
                                text = cost,
                                style =
                                        MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight =
                                                        if (isHighlighted) FontWeight.Bold
                                                        else FontWeight.Normal
                                        ),
                                color =
                                        if (isHighlighted) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface
                        )
                }
        }
}
