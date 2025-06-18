package com.ai.assistance.operit.ui.features.demo.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import com.ai.assistance.operit.core.tools.system.ShizukuAuthorizer
import com.ai.assistance.operit.core.tools.system.ShizukuInstaller
import com.ai.assistance.operit.core.tools.system.termux.TermuxInstaller
import com.ai.assistance.operit.ui.features.demo.components.*
import com.ai.assistance.operit.ui.features.demo.viewmodel.ShizukuDemoViewModel
import com.ai.assistance.operit.ui.features.demo.wizards.RootWizardCard
import com.ai.assistance.operit.ui.features.demo.wizards.ShizukuWizardCard
import com.ai.assistance.operit.ui.features.demo.wizards.TermuxWizardCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShizukuDemoScreen(
        viewModel: ShizukuDemoViewModel =
                viewModel(
                        factory =
                                ShizukuDemoViewModel.Factory(
                                        LocalContext.current.applicationContext as
                                                android.app.Application
                                )
                )
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Collect UI state from ViewModel
    val uiState by viewModel.uiState.collectAsState()

    // 跟踪当前显示的权限级别
    var currentDisplayedPermissionLevel by remember {
        mutableStateOf(AndroidPermissionLevel.STANDARD)
    }

    // Location permission launcher
    val locationPermissionLauncher =
            rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                val fineLocationGranted =
                        permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false
                val coarseLocationGranted =
                        permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
                if (fineLocationGranted || coarseLocationGranted) {
                    scope.launch(Dispatchers.IO) { viewModel.refreshStatus(context) }
                }
            }

    // Register state change listeners
    DisposableEffect(Unit) {
        val shizukuListener: () -> Unit = {
            scope.launch(Dispatchers.IO) { viewModel.refreshStatus(context) }
        }

        // 添加单独的Termux状态监听
        val termuxListener: () -> Unit = {
            scope.launch(Dispatchers.IO) { viewModel.checkTermuxAuthState(context) }
        }

        ShizukuAuthorizer.addStateChangeListener(shizukuListener)

        onDispose { ShizukuAuthorizer.removeStateChangeListener(shizukuListener) }
    }

    // 预先加载一个空的UI状态，避免初始化时的卡顿
    var isInitialized by remember { mutableStateOf(false) }

    // Initialize ViewModel
    LaunchedEffect(Unit) {
        // 显示加载指示器
        viewModel.setLoading(true)

        // 在IO线程上执行所有初始化
        withContext(Dispatchers.IO) {
            // 将初始化任务拆分成多个小任务，避免长时间阻塞
            viewModel.initializeAsync(context)
        }

        // 标记初始化完成
        isInitialized = true
    }

    Column(
            modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 加载指示器
        if (uiState.isLoading.value) {
            Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.shizuku_demo_loading), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // 权限管理卡片
        PermissionLevelCard(
                hasStoragePermission = uiState.hasStoragePermission.value,
                hasOverlayPermission = uiState.hasOverlayPermission.value,
                hasBatteryOptimizationExemption = uiState.hasBatteryOptimizationExemption.value,
                hasAccessibilityServiceEnabled = uiState.hasAccessibilityServiceEnabled.value,
                hasLocationPermission = uiState.hasLocationPermission.value,
                isShizukuInstalled = uiState.isShizukuInstalled.value,
                isShizukuRunning = uiState.isShizukuRunning.value,
                hasShizukuPermission = uiState.hasShizukuPermission.value,
                isTermuxInstalled = uiState.isTermuxInstalled.value,
                isTermuxAuthorized = uiState.isTermuxAuthorized.value,
                isTermuxFullyConfigured = viewModel.isTermuxFullyConfigured.value,
                isDeviceRooted = uiState.isDeviceRooted.value,
                hasRootAccess = uiState.hasRootAccess.value,
                isRefreshing = uiState.isRefreshing.value,
                onRefresh = { scope.launch(Dispatchers.IO) { viewModel.refreshStatus(context) } },
                onStoragePermissionClick = {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            // Android 11+: Go to manage all files permission page
                            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            context.startActivity(intent)
                        } else {
                            // Android 10-: Go to app settings page
                            val intent =
                                    Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.parse("package:" + context.packageName)
                                    )
                            context.startActivity(intent)
                        }
                    } catch (e: Exception) {
                        // Fall back to app settings
                        try {
                            val intent =
                                    Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.parse("package:" + context.packageName)
                                    )
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, stringResource(R.string.shizuku_demo_permission_failed), Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onOverlayPermissionClick = {
                    try {
                        val intent =
                                Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:" + context.packageName)
                                )
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, stringResource(R.string.shizuku_demo_overlay_failed), Toast.LENGTH_SHORT).show()
                    }
                },
                onBatteryOptimizationClick = {
                    try {
                        val intent =
                                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:" + context.packageName)
                                }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, stringResource(R.string.shizuku_demo_battery_failed), Toast.LENGTH_SHORT).show()
                    }
                },
                onAccessibilityClick = {
                    try {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, stringResource(R.string.shizuku_demo_accessibility_failed), Toast.LENGTH_SHORT).show()
                    }
                },
                onLocationPermissionClick = {
                    // 请求位置权限
                    locationPermissionLauncher.launch(
                            arrayOf(
                                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                    )
                },
                onShizukuClick = {
                    // 如果Shizuku未完全设置，则显示向导
                    if (!uiState.isShizukuInstalled.value ||
                                    !uiState.isShizukuRunning.value ||
                                    !uiState.hasShizukuPermission.value
                    ) {
                        viewModel.toggleShizukuWizard()
                    }
                },
                onTermuxClick = {
                    // 处理Termux - 不再依赖当前权限级别和Shizuku
                    if (!uiState.isTermuxInstalled.value) {
                        // 如果未安装，显示向导
                        viewModel.toggleTermuxWizard()
                    } else if (!uiState.isTermuxAuthorized.value) {
                        // 如果未授权，显示向导
                        viewModel.toggleTermuxWizard()
                    } else if (!viewModel.isTermuxRunning.value) {
                        // 如果已授权但未运行，直接尝试启动
                        scope.launch(Dispatchers.IO) { viewModel.startTermux(context) }
                    } else if (!viewModel.isTermuxFullyConfigured.value) {
                        // 如果已授权且运行但未完全配置，显示向导
                        viewModel.toggleTermuxWizard()
                    } else {
                        // 如果已完全配置，尝试打开Termux
                        scope.launch(Dispatchers.IO) { viewModel.startTermux(context) }
                    }
                },
                onRootClick = {
                    // 处理Root权限
                    if (currentDisplayedPermissionLevel == AndroidPermissionLevel.ROOT) {
                        // 如果当前正在浏览ROOT权限级别，则显示或隐藏Root向导
                        viewModel.toggleRootWizard()
                    }
                },
                onPermissionLevelChange = { level -> currentDisplayedPermissionLevel = level },
                onPermissionLevelSet = { level ->
                    // 当设置了新的权限级别时，刷新工具
                    scope.launch { viewModel.refreshTools(context) }
                }
        )

        // 组合向导卡片到一个专门的设置区域
        val needTermuxSetupGuide =
                (currentDisplayedPermissionLevel == AndroidPermissionLevel.DEBUGGER ||
                        currentDisplayedPermissionLevel == AndroidPermissionLevel.ROOT ||
                        currentDisplayedPermissionLevel == AndroidPermissionLevel.ADMIN) &&
                        (!uiState.isTermuxInstalled.value ||
                                !uiState.isTermuxAuthorized.value ||
                                !viewModel.isTermuxRunning.value ||
                                !viewModel.isTermuxFullyConfigured.value)

        // 检查Shizuku版本状态 - 使用remember缓存结果，避免每次重组时重复调用
        val (installedVersion, bundledVersion, isUpdateNeeded) =
                remember {
                    val installed = ShizukuInstaller.getInstalledShizukuVersion(context)
                    val bundled = ShizukuInstaller.getBundledShizukuVersion(context)
                    val needsUpdate = ShizukuInstaller.isShizukuUpdateNeeded(context)
                    Log.d(
                            "ShizukuDemo",
                            "缓存Shizuku版本状态 - 已安装: $installed, 内置: $bundled, 需要更新: $needsUpdate"
                    )
                    Triple(installed, bundled, needsUpdate)
                }

        val needShizukuSetupGuide =
                currentDisplayedPermissionLevel == AndroidPermissionLevel.DEBUGGER &&
                        ((!uiState.isShizukuInstalled.value ||
                                !uiState.isShizukuRunning.value ||
                                !uiState.hasShizukuPermission.value) ||
                                // 如果Shizuku已完全设置但有更新可用，也显示向导
                                (uiState.isShizukuInstalled.value &&
                                        uiState.isShizukuRunning.value &&
                                        uiState.hasShizukuPermission.value &&
                                        isUpdateNeeded))

        val needRootSetupGuide =
                currentDisplayedPermissionLevel == AndroidPermissionLevel.ROOT &&
                        (!uiState.hasRootAccess.value)

        val needSetupGuide = needTermuxSetupGuide || needShizukuSetupGuide || needRootSetupGuide

        if (needSetupGuide) {
            Spacer(modifier = Modifier.height(16.dp))

            // 修改为左对齐带图标的标题样式
            Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = stringResource(R.string.shizuku_demo_wizard_icon_cd),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                        text = stringResource(R.string.shizuku_demo_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                )
            }

            // 添加分割线
            Divider(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )

            // Root向导卡片 - 如果当前浏览的是ROOT权限级别且Root未获取
            if (needRootSetupGuide) {
                RootWizardCard(
                        isDeviceRooted = uiState.isDeviceRooted.value,
                        hasRootAccess = uiState.hasRootAccess.value,
                        showWizard = uiState.showRootWizard.value,
                        onToggleWizard = { viewModel.toggleRootWizard() },
                        onRequestRoot = {
                            scope.launch(Dispatchers.IO) {
                                viewModel.requestRootPermission(context)
                            }
                        },
                        onWatchTutorial = {
                            try {
                                val videoUrl = "https://magiskmanager.com/"
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, stringResource(R.string.shizuku_demo_root_tutorial_failed), Toast.LENGTH_SHORT).show()
                            }
                        }
                )

                Spacer(modifier = Modifier.height(12.dp))
            }

            // Shizuku向导卡片 - 如果正在浏览DEBUGGER权限级别且Shizuku未完全设置则显示
            if (needShizukuSetupGuide) {
                ShizukuWizardCard(
                        isShizukuInstalled = uiState.isShizukuInstalled.value,
                        isShizukuRunning = uiState.isShizukuRunning.value,
                        hasShizukuPermission = uiState.hasShizukuPermission.value,
                        showWizard = uiState.showShizukuWizard.value,
                        onToggleWizard = { viewModel.toggleShizukuWizard() },
                        onInstallFromStore = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW)
                                intent.data =
                                        Uri.parse("https://shizuku.rikka.app/zh-hans/download/")
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(
                                                context,
                                                context.getString(
                                                        R.string.toast_download_link_failed
                                                ),
                                                Toast.LENGTH_SHORT
                                        )
                                        .show()
                            }
                        },
                        onInstallBundled = {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    Log.d("ShizukuDemo", "开始安装内置Shizuku")
                                    // 提取APK并安装，无论是否已安装
                                    val apkFile = ShizukuInstaller.extractApkFromAssets(context)
                                    if (apkFile == null) {
                                        Log.e("ShizukuDemo", "提取APK失败")
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(
                                                            context,
                                                            context.getString(
                                                                    R.string
                                                                            .toast_apk_extract_failed
                                                            ),
                                                            Toast.LENGTH_SHORT
                                                    )
                                                    .show()
                                        }
                                        return@launch
                                    }

                                    Log.d(
                                            "ShizukuDemo",
                                            "APK提取成功: ${apkFile.absolutePath}, 大小: ${apkFile.length()} 字节"
                                    )

                                    // 生成APK的URI
                                    val apkUri =
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                                FileProvider.getUriForFile(
                                                        context,
                                                        "${context.packageName}.fileprovider",
                                                        apkFile
                                                )
                                            } else {
                                                Uri.fromFile(apkFile)
                                            }

                                    Log.d("ShizukuDemo", "生成APK URI: $apkUri")

                                    // 创建安装意图
                                    val installIntent =
                                            Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(
                                                        apkUri,
                                                        "application/vnd.android.package-archive"
                                                )
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                                                ) {
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                            }

                                    Log.d("ShizukuDemo", "启动安装界面")

                                    // 启动安装界面
                                    withContext(Dispatchers.Main) {
                                        context.startActivity(installIntent)
                                        Toast.makeText(
                                                        context,
                                                        context.getString(
                                                                R.string.shizuku_demo_install_notify
                                                        ),
                                                        Toast.LENGTH_LONG
                                                )
                                                .show()
                                    }
                                } catch (e: Exception) {
                                    Log.e("ShizukuDemo", "安装内置Shizuku时出错", e)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                                        context,
                                                        context.getString(
                                                                R.string.toast_operation_failed,
                                                                e.message ?: ""
                                                        ),
                                                        Toast.LENGTH_SHORT
                                                )
                                                .show()
                                    }
                                }
                            }
                        },
                        onOpenShizuku = {
                            try {
                                val intent =
                                        context.packageManager.getLaunchIntentForPackage(
                                                "moe.shizuku.privileged.api"
                                        )
                                if (intent != null) {
                                    Log.d("ShizukuDemo", "打开Shizuku应用")
                                    context.startActivity(intent)
                                } else {
                                    Log.e("ShizukuDemo", "无法找到Shizuku应用")
                                    Toast.makeText(context, stringResource(R.string.shizuku_demo_shizuku_not_found), Toast.LENGTH_SHORT)
                                            .show()
                                }
                            } catch (e: Exception) {
                                Log.e("ShizukuDemo", "无法启动Shizuku应用", e)
                                Toast.makeText(context, stringResource(R.string.shizuku_demo_shizuku_launch_failed), Toast.LENGTH_SHORT).show()
                            }
                        },
                        onWatchTutorial = {
                            try {
                                val videoUrl = "https://shizuku.rikka.app/zh-hans/guide/setup/"
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, stringResource(R.string.shizuku_demo_docs_link_failed), Toast.LENGTH_SHORT).show()
                            }
                        },
                        onRequestPermission = {
                            scope.launch {
                                Log.d("ShizukuDemo", "请求Shizuku权限")
                                ShizukuAuthorizer.requestShizukuPermission { granted ->
                                    Log.d("ShizukuDemo", "Shizuku权限请求结果: $granted")
                                    scope.launch(Dispatchers.Main) {
                                        if (granted) {
                                            Toast.makeText(
                                                            context,
                                                            context.getString(
                                                                    R.string
                                                                            .shizuku_demo_shizuku_permission_granted
                                                            ),
                                                            Toast.LENGTH_SHORT
                                                    )
                                                    .show()
                                        } else {
                                            Toast.makeText(
                                                            context,
                                                            context.getString(
                                                                    R.string
                                                                            .shizuku_demo_shizuku_permission_denied
                                                            ),
                                                            Toast.LENGTH_SHORT
                                                    )
                                                    .show()
                                        }
                                    }

                                    scope.launch(Dispatchers.IO) {
                                        viewModel.refreshStatus(context)
                                    }
                                }
                            }
                        },
                        updateNeeded = isUpdateNeeded,
                        onUpdateShizuku = {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    Log.d("ShizukuDemo", "开始更新Shizuku")
                                    // 提取APK并安装，无论是否已安装
                                    val apkFile = ShizukuInstaller.extractApkFromAssets(context)
                                    if (apkFile == null) {
                                        Log.e("ShizukuDemo", "提取APK失败")
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(
                                                            context,
                                                            context.getString(
                                                                    R.string
                                                                            .toast_apk_extract_failed
                                                            ),
                                                            Toast.LENGTH_SHORT
                                                    )
                                                    .show()
                                        }
                                        return@launch
                                    }

                                    Log.d(
                                            "ShizukuDemo",
                                            "APK提取成功: ${apkFile.absolutePath}, 大小: ${apkFile.length()} 字节"
                                    )

                                    // 生成APK的URI
                                    val apkUri =
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                                FileProvider.getUriForFile(
                                                        context,
                                                        "${context.packageName}.fileprovider",
                                                        apkFile
                                                )
                                            } else {
                                                Uri.fromFile(apkFile)
                                            }

                                    Log.d("ShizukuDemo", "生成APK URI: $apkUri")

                                    // 创建安装意图
                                    val installIntent =
                                            Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(
                                                        apkUri,
                                                        "application/vnd.android.package-archive"
                                                )
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                                                ) {
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                            }

                                    Log.d("ShizukuDemo", "启动更新界面")

                                    // 启动安装界面
                                    withContext(Dispatchers.Main) {
                                        context.startActivity(installIntent)
                                        Toast.makeText(
                                                        context,
                                                        context.getString(
                                                                R.string.shizuku_demo_update_notify
                                                        ),
                                                        Toast.LENGTH_LONG
                                                )
                                                .show()
                                    }
                                } catch (e: Exception) {
                                    Log.e("ShizukuDemo", "更新Shizuku时出错", e)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                                        context,
                                                        context.getString(
                                                                R.string.toast_operation_failed,
                                                                e.message ?: ""
                                                        ),
                                                        Toast.LENGTH_SHORT
                                                )
                                                .show()
                                    }
                                }
                            }
                        },
                        // 传递已缓存的版本信息，避免重复调用API
                        installedVersion = installedVersion,
                        bundledVersion = bundledVersion
                )

                Spacer(modifier = Modifier.height(12.dp))
            }

            // Termux向导卡片 - 如果Termux未完全设置则显示（不再依赖Shizuku）
            if (needTermuxSetupGuide) {
                TermuxWizardCard(
                        isTermuxInstalled = uiState.isTermuxInstalled.value,
                        isTermuxAuthorized = uiState.isTermuxAuthorized.value,
                        showWizard = uiState.showTermuxWizard.value,
                        onToggleWizard = { viewModel.toggleTermuxWizard() },
                        onInstallBundled = {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val success =
                                            withContext(Dispatchers.IO) {
                                                TermuxInstaller.installBundledTermux(context)
                                            }
                                    withContext(Dispatchers.Main) {
                                        if (success) {
                                            Toast.makeText(
                                                            context,
                                                            context.getString(
                                                                    R.string
                                                                            .shizuku_demo_termux_install_success
                                                            ),
                                                            Toast.LENGTH_LONG
                                                    )
                                                    .show()
                                        } else {
                                            // 如果无法通过内置安装，尝试跳转到下载页面
                                            Toast.makeText(
                                                            context,
                                                            context.getString(
                                                                    R.string
                                                                            .shizuku_demo_termux_install_failed_store
                                                            ),
                                                            Toast.LENGTH_SHORT
                                                    )
                                                    .show()
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW)
                                                intent.data =
                                                        Uri.parse(
                                                                "https://f-droid.org/packages/com.termux/"
                                                        )
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                Toast.makeText(
                                                                context,
                                                                context.getString(
                                                                        R.string
                                                                                .shizuku_demo_termux_download_failed
                                                                ),
                                                                Toast.LENGTH_SHORT
                                                        )
                                                        .show()
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("ShizukuDemo", "安装内置Termux时出错", e)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                                        context,
                                                        context.getString(
                                                                R.string.toast_operation_failed,
                                                                e.message ?: ""
                                                        ),
                                                        Toast.LENGTH_SHORT
                                                )
                                                .show()
                                    }
                                }
                            }
                        },
                        onOpenTermux = {
                            scope.launch(Dispatchers.IO) { viewModel.startTermux(context) }
                        },
                        onAuthorizeTermux = {
                            scope.launch(Dispatchers.IO) {
                                viewModel.ensureTermuxRunningAndAuthorize(context)
                            }
                        },
                        isTunaSourceEnabled = viewModel.isTunaSourceEnabled.value,
                        isPythonInstalled = viewModel.isPythonInstalled.value,
                        isUvInstalled = viewModel.isUvInstalled.value,
                        isNodeInstalled = viewModel.isNodeInstalled.value,
                        isTermuxRunning = viewModel.isTermuxRunning.value,
                        isTermuxBatteryOptimizationExempted =
                                viewModel.isTermuxBatteryOptimizationExempted.value,
                        onRequestTermuxBatteryOptimization = {
                            scope.launch(Dispatchers.IO) {
                                viewModel.requestTermuxBatteryOptimization(context)
                            }
                        },
                        onConfigureTunaSource = {
                            scope.launch(Dispatchers.IO) { viewModel.configureTunaSource(context) }
                        },
                        onSkipTunaSource = {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    // 显示提示对话框并标记清华源为已跳过
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                                        context,
                                                        context.getString(
                                                                R.string.toast_skip_tuna_source
                                                        ),
                                                        Toast.LENGTH_LONG
                                                )
                                                .show()
                                    }
                                    viewModel.skipTunaSource(context)
                                } catch (e: Exception) {
                                    Log.e("ShizukuDemo", "跳过清华源时出错: ${e.message}", e)

                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                                        context,
                                                        context.getString(
                                                                R.string.toast_skip_tuna_source
                                                        ),
                                                        Toast.LENGTH_SHORT
                                                )
                                                .show()
                                    }
                                }
                            }
                        },
                        onInstallPythonEnv = {
                            scope.launch(Dispatchers.IO) { viewModel.installPython(context) }
                        },
                        onInstallUvEnv = {
                            scope.launch(Dispatchers.IO) { viewModel.installUv(context) }
                        },
                        onInstallNodeEnv = {
                            scope.launch(Dispatchers.IO) { viewModel.installNode(context) }
                        },
                        onDeleteConfig = {
                            scope.launch(Dispatchers.IO) {
                                viewModel.deleteTermuxConfigAndStart(context)
                            }
                        }
                )
            }
        }
    }

    // 显示命令结果对话框
    CommandResultDialog(
            showDialog = uiState.showResultDialogState.value,
            onDismiss = {
                // 只有当未在配置过程中时才允许手动关闭
                if (!viewModel.isTermuxConfiguring.value) {
                    viewModel.hideResultDialog()
                    // 重置输出文本，准备下一次操作
                    viewModel.updateOutputText(stringResource(R.string.shizuku_demo_termux_welcome))
                }
            },
            title = uiState.resultDialogTitle.value,
            content =
                    if (viewModel.isTermuxConfiguring.value) {
                        // 当正在配置时，显示实时输出
                        "${viewModel.outputText.value}\n\n${if (viewModel.currentTask.value.isNotEmpty()) stringResource(R.string.shizuku_demo_executing_prefix) + viewModel.currentTask.value + stringResource(R.string.shizuku_demo_ellipsis) else ""}"
                    } else {
                        // 当配置完成时，显示最终结果
                        viewModel.outputText.value
                    },
            context = context,
            // 当正在配置时不显示按钮，不可复制，配置完成后恢复按钮
            showButtons = !viewModel.isTermuxConfiguring.value,
            allowCopy = !viewModel.isTermuxConfiguring.value,
            isExecuting = viewModel.isTermuxConfiguring.value
    )
}
