package com.ai.assistance.operit.ui.features.packages.screens

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.PackageTool
import com.ai.assistance.operit.core.tools.ToolPackage
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.data.mcp.MCPRepository
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.ui.features.packages.components.EmptyState
import com.ai.assistance.operit.ui.features.packages.components.PackageTab
import com.ai.assistance.operit.ui.features.packages.dialogs.PackageDetailsDialog
import com.ai.assistance.operit.ui.features.packages.dialogs.ScriptExecutionDialog
import com.ai.assistance.operit.ui.features.packages.lists.PackagesList
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackageManagerScreen() {
    val context = LocalContext.current
    val packageManager = remember {
        PackageManager.getInstance(context, AIToolHandler.getInstance(context))
    }
    val scope = rememberCoroutineScope()
    val mcpRepository = remember { MCPRepository(context) }

    // State for available and imported packages
    val availablePackages = remember { mutableStateOf<Map<String, ToolPackage>>(emptyMap()) }
    val importedPackages = remember { mutableStateOf<List<String>>(emptyList()) }
    // UI展示用的导入状态列表，与后端状态分离
    val visibleImportedPackages = remember { mutableStateOf<List<String>>(emptyList()) }

    // State for selected package and showing details
    var selectedPackage by remember { mutableStateOf<String?>(null) }
    var showDetails by remember { mutableStateOf(false) }

    // State for script execution
    var showScriptExecution by remember { mutableStateOf(false) }
    var selectedTool by remember { mutableStateOf<PackageTool?>(null) }
    var scriptExecutionResult by remember { mutableStateOf<ToolResult?>(null) }

    // State for snackbar
    val snackbarHostState = remember { SnackbarHostState() }

    // Tab selection state
    var selectedTab by remember { mutableStateOf(PackageTab.PACKAGES) }

    // File picker launcher for importing external packages
    val packageFilePicker =
            rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri
                ->
                uri?.let {
                    scope.launch {
                        try {
                            // Convert URI to file path - this is a simplified approach
                            val cursor = context.contentResolver.query(uri, null, null, null, null)
                            cursor?.use {
                                val nameIndex = it.getColumnIndex("_display_name")
                                if (it.moveToFirst() && nameIndex >= 0) {
                                    val fileName = it.getString(nameIndex)
                                    if (!fileName.endsWith(".hjson")) {
                                        snackbarHostState.showSnackbar(message = context.getString(R.string.only_hjson_supported_snackbar))
                                        return@launch
                                    }
                                }
                            }

                            // Copy the file to a temporary location
                            val inputStream = context.contentResolver.openInputStream(uri)
                            val tempFile = File(context.cacheDir, "temp_package.hjson")

                            inputStream?.use { input ->
                                tempFile.outputStream().use { output -> input.copyTo(output) }
                            }

                            // Import the package from the temporary file
                            val result =
                                    packageManager.importPackageFromExternalStorage(
                                            tempFile.absolutePath
                                    )

                            // Refresh the lists
                            availablePackages.value = packageManager.getAvailablePackages()
                            importedPackages.value = packageManager.getImportedPackages()

                            snackbarHostState.showSnackbar(message = context.getString(R.string.external_package_import_success_snackbar))

                            // Clean up the temporary file
                            tempFile.delete()
                        } catch (e: Exception) {
                            Log.e("PackageManagerScreen", "Failed to import external package", e)
                            snackbarHostState.showSnackbar(message = context.getString(R.string.external_package_import_error_snackbar, e.message))
                        }
                    }
                }
            }

    // Load packages
    LaunchedEffect(Unit) {
        try {
            availablePackages.value = packageManager.getAvailablePackages()
            importedPackages.value = packageManager.getImportedPackages()
            // 初始化UI显示状态
            visibleImportedPackages.value = importedPackages.value.toList()
        } catch (e: Exception) {
            Log.e("PackageManagerScreen", "Failed to load packages", e)
        }
    }

    Scaffold(
            snackbarHost = {
                SnackbarHost(snackbarHostState) { data ->
                    Snackbar(
                            modifier = Modifier.padding(16.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            snackbarData = data
                    )
                }
            },
            floatingActionButton = {
                if (selectedTab != PackageTab.MCP_MARKETPLACE &&
                                selectedTab != PackageTab.MCP_CONFIG
                ) {
                    FloatingActionButton(
                            onClick = { packageFilePicker.launch("*/*") },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier =
                                    Modifier.shadow(
                                            elevation = 6.dp,
                                            shape = FloatingActionButtonDefaults.shape
                                    )
                    ) { Icon(imageVector = Icons.Rounded.Add, contentDescription = stringResource(R.string.import_external_package_fab)) }
                }
            }
    ) { paddingValues ->
        Column(
                modifier =
                        Modifier.fillMaxSize()
                                .padding(paddingValues)
                ) {
            // 优化标签栏布局 - 直接使用TabRow，不再使用Card包裹，移除边距完全贴满
            TabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    modifier = Modifier.fillMaxWidth(),
                    divider = {
                        Divider(
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant
                        )
                    },
                    indicator = { tabPositions ->
                        if (selectedTab.ordinal < tabPositions.size) {
                            TabRowDefaults.PrimaryIndicator(
                                    modifier =
                                            Modifier.tabIndicatorOffset(
                                                    tabPositions[selectedTab.ordinal]
                                            ),
                                    height = 3.dp,
                                    color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
            ) {
                // 包管理标签
                Tab(
                        selected = selectedTab == PackageTab.PACKAGES,
                        onClick = { selectedTab = PackageTab.PACKAGES },
                        modifier = Modifier.height(48.dp),
                        text = {
                            Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                        imageVector = Icons.Default.Extension,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                        stringResource(R.string.tab_package_management),
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // MCP插件市场标签
                Tab(
                        selected = selectedTab == PackageTab.MCP_MARKETPLACE,
                        onClick = { selectedTab = PackageTab.MCP_MARKETPLACE },
                        modifier = Modifier.height(48.dp),
                        text = {
                            Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                        imageVector = Icons.Default.Cloud,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                        stringResource(R.string.tab_mcp_marketplace),
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // MCP配置标签
                Tab(
                        selected = selectedTab == PackageTab.MCP_CONFIG,
                        onClick = { selectedTab = PackageTab.MCP_CONFIG },
                        modifier = Modifier.height(48.dp),
                        text = {
                            Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                        stringResource(R.string.tab_mcp_config),
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 内容区域添加水平padding
            Box(modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)) {
                when (selectedTab) {
                    PackageTab.PACKAGES -> {
                        // 显示包列表
                        if (availablePackages.value.isEmpty()) {
                            EmptyState(message = stringResource(R.string.no_packages_available_text))
                        } else {
                            Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.background,
                                    shape = MaterialTheme.shapes.medium
                            ) {
                                PackagesList(
                                        packages = availablePackages.value,
                                        importedPackages = visibleImportedPackages.value,
                                        onPackageClick = { packageName ->
                                            selectedPackage = packageName
                                            showDetails = true
                                        },
                                        onToggleImport = { packageName, isChecked ->
                                            // 立即更新UI显示的导入状态列表，使开关立即响应
                                            val currentImported =
                                                    visibleImportedPackages.value.toMutableList()
                                            if (isChecked) {
                                                if (!currentImported.contains(packageName)) {
                                                    currentImported.add(packageName)
                                                }
                                            } else {
                                                currentImported.remove(packageName)
                                            }
                                            visibleImportedPackages.value = currentImported

                                            // 后台执行实际的导入/移除操作
                                            scope.launch {
                                                try {
                                                    if (isChecked) {
                                                        packageManager.importPackage(packageName)
                                                    } else {
                                                        packageManager.removePackage(packageName)
                                                    }
                                                    // 操作成功后，更新真实的导入状态
                                                    importedPackages.value =
                                                            packageManager.getImportedPackages()
                                                } catch (e: Exception) {
                                                    Log.e(
                                                            "PackageManagerScreen",
                                                            if (isChecked) "Failed to import package"
                                                            else "Failed to remove package",
                                                            e
                                                    )
                                                    // 操作失败时恢复UI显示状态为实际状态
                                                    visibleImportedPackages.value =
                                                            importedPackages.value

                                                    // 只在失败时显示提示
                                                    snackbarHostState.showSnackbar(
                                                            message =
                                                                    if (isChecked) context.getString(R.string.package_import_failed_snackbar) else context.getString(R.string.package_remove_failed_snackbar)
                                                    )
                                                }
                                            }
                                        }
                                )
                            }
                        }
                    }
                    PackageTab.MCP_MARKETPLACE -> {
                        // MCP插件市场界面
                        MCPScreen(mcpRepository = mcpRepository)
                    }
                    PackageTab.MCP_CONFIG -> {
                        // MCP配置界面
                        MCPConfigScreen()
                    }
                }
            }

            // Package Details Dialog
            if (showDetails && selectedPackage != null) {
                PackageDetailsDialog(
                        packageName = selectedPackage!!,
                        packageDescription = availablePackages.value[selectedPackage]?.description
                                        ?: "",
                        packageManager = packageManager,
                        onRunScript = { tool ->
                            selectedTool = tool
                            showScriptExecution = true
                        },
                        onDismiss = { showDetails = false }
                )
            }

            // Script Execution Dialog
            if (showScriptExecution && selectedTool != null && selectedPackage != null) {
                ScriptExecutionDialog(
                        packageName = selectedPackage!!,
                        tool = selectedTool!!,
                        packageManager = packageManager,
                        initialResult = scriptExecutionResult,
                        onExecuted = { result -> scriptExecutionResult = result },
                        onDismiss = {
                            showScriptExecution = false
                            scriptExecutionResult = null
                        }
                )
            }
        }
    }
}
