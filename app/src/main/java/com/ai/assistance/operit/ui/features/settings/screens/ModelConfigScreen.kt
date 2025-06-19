package com.ai.assistance.operit.ui.features.settings.screens

import androidx.compose.animation.*
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.ui.features.settings.sections.ModelApiSettingsSection
import com.ai.assistance.operit.ui.features.settings.sections.ModelParametersSection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelConfigScreen() {
    val context = LocalContext.current
    val configManager = remember { ModelConfigManager(context) }
    val apiPreferences = remember { ApiPreferences(context) }
    val scope = rememberCoroutineScope()

    // 配置状态
    val configList = configManager.configListFlow.collectAsState(initial = listOf("default")).value
    // 不再使用activeConfigIdFlow，默认选择第一个配置
    var selectedConfigId by remember { mutableStateOf(configList.firstOrNull() ?: "default") }
    val selectedConfig = remember { mutableStateOf<ModelConfigData?>(null) }

    // 配置名称映射
    val configNameMap = remember { mutableStateMapOf<String, String>() }

    // UI状态
    var showAddConfigDialog by remember { mutableStateOf(false) }
    var showSaveSuccessMessage by remember { mutableStateOf(false) }
    var isDropdownExpanded by remember { mutableStateOf(false) }
    var newConfigName by remember { mutableStateOf("") }
    var confirmMessage by remember { mutableStateOf("") }

    // 初始化配置管理器
    LaunchedEffect(Unit) { configManager.initializeIfNeeded() }

    // 加载所有配置名称
    LaunchedEffect(configList) {
        configList.forEach { id ->
            val config = configManager.getModelConfigFlow(id).first()
            configNameMap[id] = config.name
        }
    }

    // 加载选中的配置
    LaunchedEffect(selectedConfigId) {
        configManager.getModelConfigFlow(selectedConfigId).collect { config ->
            selectedConfig.value = config
        }
    }

    // 显示通知消息
    fun showNotification(message: String) {
        confirmMessage = message
        showSaveSuccessMessage = true
        scope.launch {
            kotlinx.coroutines.delay(3000)
            showSaveSuccessMessage = false
        }
    }

    // 主界面内容
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {

        // 配置选择区域
        Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors =
                        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border =
                        BorderStroke(
                                0.7.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                // 配置选择标题行
                Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    // 选择配置标题
                    Text(
                            "选择模型配置",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                    )

                    // 新建按钮
                    OutlinedButton(
                            onClick = { showAddConfigDialog = true },
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(28.dp),
                            colors =
                                    ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.primary
                                    )
                    ) {
                        Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("新建", fontSize = 12.sp, style = MaterialTheme.typography.labelSmall)
                    }
                }

                val selectedConfigName = configNameMap[selectedConfigId] ?: "默认配置"

                // 当前选中配置显示框
                Surface(
                        modifier = Modifier.fillMaxWidth().clickable { isDropdownExpanded = true },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        tonalElevation = 0.5.dp,
                ) {
                    Row(
                            modifier =
                                    Modifier.fillMaxWidth()
                                            .padding(vertical = 12.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                    text = selectedConfigName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // 下拉箭头动画
                        @OptIn(ExperimentalAnimationApi::class)
                        AnimatedContent(
                                targetState = isDropdownExpanded,
                                transitionSpec = {
                                    fadeIn() + scaleIn() with fadeOut() + scaleOut()
                                }
                        ) { expanded ->
                            Icon(
                                    if (expanded) Icons.Default.KeyboardArrowUp
                                    else Icons.Default.KeyboardArrowDown,
                                    contentDescription = "选择配置",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 配置操作按钮
                Row(
                        modifier = Modifier.padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // 删除按钮 - 不能删除默认配置
                    if (selectedConfigId != "default") {
                        TextButton(
                                onClick = {
                                    scope.launch {
                                        configManager.deleteConfig(selectedConfigId)
                                        selectedConfigId = configList.firstOrNull() ?: "default"
                                        showNotification("配置已删除")
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                colors =
                                        ButtonDefaults.textButtonColors(
                                                contentColor = MaterialTheme.colorScheme.error
                                        ),
                                modifier = Modifier.height(36.dp)
                        ) {
                            Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("删除", fontSize = 14.sp)
                        }
                    }
                }
            }

            // 默认配置警告提示
            AnimatedVisibility(visible = selectedConfigId == "default") {
                Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        colors =
                                CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                ),
                        shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                                text = "请修改默认配置的API Key，否则将被判断为未使用自己的配置",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // 配置下拉菜单
            DropdownMenu(
                    expanded = isDropdownExpanded,
                    onDismissRequest = { isDropdownExpanded = false },
                    modifier = Modifier.width(280.dp),
                    properties = PopupProperties(focusable = true)
            ) {
                configList.forEach { configId ->
                    val configName = configNameMap[configId] ?: "未命名配置"
                    val isSelected = configId == selectedConfigId

                    DropdownMenuItem(
                            text = {
                                Text(
                                        text = configName,
                                        fontWeight =
                                                if (isSelected) FontWeight.SemiBold
                                                else FontWeight.Normal,
                                        color =
                                                if (isSelected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            leadingIcon =
                                    if (isSelected) {
                                        {
                                            Box(
                                                    modifier =
                                                            Modifier.size(8.dp)
                                                                    .background(
                                                                            MaterialTheme
                                                                                    .colorScheme
                                                                                    .primary,
                                                                            CircleShape
                                                                    )
                                            )
                                        }
                                    } else null,
                            trailingIcon =
                                    if (isSelected) {
                                        {
                                            Box(
                                                    modifier =
                                                            Modifier.size(8.dp)
                                                                    .background(
                                                                            MaterialTheme
                                                                                    .colorScheme
                                                                                    .primary,
                                                                            CircleShape
                                                                    )
                                            )
                                        }
                                    } else null,
                            onClick = {
                                selectedConfigId = configId
                                isDropdownExpanded = false
                            },
                            colors =
                                    MenuDefaults.itemColors(
                                            textColor =
                                                    if (isSelected)
                                                            MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.onSurface
                                    ),
                            modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    if (configId != configList.last()) {
                        Divider(modifier = Modifier.padding(horizontal = 8.dp), thickness = 0.5.dp)
                    }
                }
            }
        }

        // API设置区域
        selectedConfig.value?.let { config ->
            ModelApiSettingsSection(
                    config = config,
                    configManager = configManager,
                    showNotification = { message -> showNotification(message) }
            )
        }

        // 模型参数区域
        selectedConfig.value?.let { config ->
            ModelParametersSection(
                    config = config,
                    configManager = configManager,
                    showNotification = { message -> showNotification(message) }
            )
        }

        // 操作成功消息显示
        AnimatedVisibility(
                visible = showSaveSuccessMessage,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
        ) {
            Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors =
                            CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
            ) {
                Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                            text = confirmMessage,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }

    // 新建配置对话框
    if (showAddConfigDialog) {
        AlertDialog(
                onDismissRequest = {
                    showAddConfigDialog = false
                    newConfigName = ""
                },
                title = {
                    Text(
                            "新建模型配置",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                                "创建新的模型配置，自定义API和参数设置",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                                value = newConfigName,
                                onValueChange = { newConfigName = it },
                                label = { Text("配置名称", fontSize = 12.sp) },
                                placeholder = { Text("例如: GPT-4配置、Claude配置...", fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(
                            onClick = {
                                if (newConfigName.isNotBlank()) {
                                    scope.launch {
                                        val configId = configManager.createConfig(newConfigName)
                                        selectedConfigId = configId
                                        showAddConfigDialog = false
                                        newConfigName = ""
                                        showNotification("新配置已创建")
                                    }
                                }
                            },
                            shape = RoundedCornerShape(8.dp)
                    ) { Text("创建", fontSize = 13.sp) }
                },
                dismissButton = {
                    TextButton(
                            onClick = {
                                showAddConfigDialog = false
                                newConfigName = ""
                            }
                    ) { Text("取消", fontSize = 13.sp) }
                },
                shape = RoundedCornerShape(12.dp)
        )
    }
}
