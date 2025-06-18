package com.ai.assistance.operit.ui.features.toolbox.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Transform
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ai.assistance.operit.ui.features.toolbox.screens.apppermissions.AppPermissionsScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.ffmpegtoolbox.FFmpegToolboxScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.FileManagerScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.logcat.LogcatScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.shellexecutor.ShellExecutorScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.terminal.screens.TerminalScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.terminalconfig.TerminalAutoConfigScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.uidebugger.UIDebuggerScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.annotation.StringRes
import com.ai.assistance.operit.R

// 工具类别
enum class ToolCategory(@StringRes val displayName: Int) {
        ALL(R.string.toolbox_category_all),
        FILE_MANAGEMENT(R.string.toolbox_category_file_management),
        DEVELOPMENT(R.string.toolbox_category_development),
        SYSTEM(R.string.toolbox_category_system)
}

data class Tool(
        @StringRes val name: Int,
        val icon: ImageVector,
        @StringRes val description: Int,
        val category: ToolCategory,
        val onClick: () -> Unit
)

/** 工具箱屏幕，展示可用的各种工具 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolboxScreen(
        navController: NavController,
        onFormatConverterSelected: () -> Unit,
        onFileManagerSelected: () -> Unit,
        onTerminalSelected: () -> Unit,
        onTerminalAutoConfigSelected: () -> Unit,
        onAppPermissionsSelected: () -> Unit,
        onUIDebuggerSelected: () -> Unit,
        onFFmpegToolboxSelected: () -> Unit,
        onShellExecutorSelected: () -> Unit,
        onLogcatSelected: () -> Unit,
        onMarkdownDemoSelected: () -> Unit
) {
        // 屏幕配置信息，用于响应式布局
        val configuration = LocalConfiguration.current
        val screenWidth = configuration.screenWidthDp.dp

        // 根据屏幕宽度决定每行显示的卡片数量
        val columnsCount =
                when {
                        screenWidth > 840.dp -> 3 // 大屏幕设备显示3列
                        screenWidth > 600.dp -> 2 // 中等屏幕设备显示2列
                        else -> 2 // 小屏幕设备显示2列
                }

        // 当前选中的分类过滤器
        var selectedCategory by remember { mutableStateOf(ToolCategory.ALL) }

        val tools =
                listOf(
                        Tool(
                                name = R.string.toolbox_tool_format_converter_name,
                                icon = Icons.Rounded.Transform,
                                description = R.string.toolbox_tool_format_converter_desc,
                                category = ToolCategory.FILE_MANAGEMENT,
                                onClick = onFormatConverterSelected
                        ),
                        Tool(
                                name = R.string.toolbox_tool_file_manager_name,
                                icon = Icons.Rounded.Folder,
                                description = R.string.toolbox_tool_file_manager_desc,
                                category = ToolCategory.FILE_MANAGEMENT,
                                onClick = onFileManagerSelected
                        ),
                        Tool(
                                name = R.string.toolbox_tool_terminal_name,
                                icon = Icons.Rounded.Terminal,
                                description = R.string.toolbox_tool_terminal_desc,
                                category = ToolCategory.DEVELOPMENT,
                                onClick = onTerminalSelected
                        ),
                        Tool(
                                name = R.string.toolbox_tool_terminal_config_name,
                                icon = Icons.Rounded.Build,
                                description = R.string.toolbox_tool_terminal_config_desc,
                                category = ToolCategory.DEVELOPMENT,
                                onClick = onTerminalAutoConfigSelected
                        ),
                        Tool(
                                name = R.string.toolbox_tool_app_permissions_name,
                                icon = Icons.Rounded.Security,
                                description = R.string.toolbox_tool_app_permissions_desc,
                                category = ToolCategory.SYSTEM,
                                onClick = onAppPermissionsSelected
                        ),
                        Tool(
                                name = R.string.toolbox_tool_ui_debugger_name,
                                icon = Icons.Default.DeviceHub,
                                description = R.string.toolbox_tool_ui_debugger_desc,
                                category = ToolCategory.DEVELOPMENT,
                                onClick = onUIDebuggerSelected
                        ),
                        Tool(
                                name = R.string.toolbox_tool_ffmpeg_toolbox_name,
                                icon = Icons.Default.VideoSettings,
                                description = R.string.toolbox_tool_ffmpeg_toolbox_desc,
                                category = ToolCategory.DEVELOPMENT,
                                onClick = onFFmpegToolboxSelected
                        ),
                        Tool(
                                name = R.string.toolbox_tool_markdown_demo_name,
                                icon = Icons.Default.FormatAlignLeft,
                                description = R.string.toolbox_tool_markdown_demo_desc,
                                category = ToolCategory.DEVELOPMENT,
                                onClick = onMarkdownDemoSelected
                        ),
                        Tool(
                                name = R.string.toolbox_tool_command_executor_name,
                                icon = Icons.Default.Code,
                                description = R.string.toolbox_tool_command_executor_desc,
                                category = ToolCategory.DEVELOPMENT,
                                onClick = onShellExecutorSelected
                        ),
                        Tool(
                                name = R.string.toolbox_tool_logcat_viewer_name,
                                icon = Icons.Default.DataObject,
                                description = R.string.toolbox_tool_logcat_viewer_desc,
                                category = ToolCategory.DEVELOPMENT,
                                onClick = onLogcatSelected
                        )
                )

        // 根据选中的分类过滤工具
        val filteredTools =
                if (selectedCategory == ToolCategory.ALL) {
                        tools
                } else {
                        tools.filter { it.category == selectedCategory }
                }

        Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                        // 顶部标题区域
                        TopAppSection()

                        // 分类选择器
                        CategorySelector(
                                selectedCategory = selectedCategory,
                                onCategorySelected = { selectedCategory = it }
                        )

                        // 工具网格
                        LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 120.dp),
                                contentPadding = PaddingValues(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                        ) { items(filteredTools) { tool -> ToolCard(tool = tool) } }
                }
        }
}

@Composable
private fun TopAppSection() {
        Column(
                modifier =
                        Modifier.fillMaxWidth()
                                .background(
                                        brush =
                                                Brush.verticalGradient(
                                                        colors =
                                                                listOf(
                                                                        MaterialTheme.colorScheme
                                                                                .primary.copy(
                                                                                alpha = 0.05f
                                                                        ),
                                                                        MaterialTheme.colorScheme
                                                                                .surface
                                                                )
                                                )
                                )
                                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp)
        ) {
                Text(
                        text = stringResource(R.string.toolbox_title),
                        style =
                                MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Bold
                                )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                        text = stringResource(R.string.toolbox_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
        }
}

@Composable
private fun CategorySelector(
        selectedCategory: ToolCategory,
        onCategorySelected: (ToolCategory) -> Unit
) {
        val categories = ToolCategory.values()

        // 水平滚动的分类选择器
        Row(
                modifier =
                        Modifier.fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
                categories.forEach { category ->
                        val isSelected = selectedCategory == category
                        val backgroundColor =
                                if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                } else {
                                        MaterialTheme.colorScheme.surface
                                }

                        val textColor =
                                if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                }

                        Surface(
                                onClick = { onCategorySelected(category) },
                                shape = RoundedCornerShape(20.dp),
                                color = backgroundColor,
                                tonalElevation = if (isSelected) 0.dp else 1.dp,
                                shadowElevation = if (isSelected) 2.dp else 0.dp,
                                modifier = Modifier.height(36.dp)
                        ) {
                                Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                ) {
                                        Text(
                                                text = stringResource(category.displayName),
                                                style =
                                                        MaterialTheme.typography.bodyMedium.copy(
                                                                fontWeight =
                                                                        if (isSelected)
                                                                                FontWeight.Bold
                                                                        else FontWeight.Normal
                                                        ),
                                                color = textColor
                                        )
                                }
                        }
                }
        }
}

/** 工具项卡片 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolCard(tool: Tool) {
        var isPressed by remember { mutableStateOf(false) }

        // 创建协程作用域
        val scope = rememberCoroutineScope()

        // 缩放动画
        val scale by
                animateFloatAsState(
                        targetValue = if (isPressed) 0.95f else 1f,
                        animationSpec = tween(durationMillis = if (isPressed) 100 else 200),
                        label = "scale"
                )

        Card(
                onClick = {
                        isPressed = true
                        // 使用rememberCoroutineScope来启动协程
                        scope.launch {
                                delay(100)
                                tool.onClick()
                                isPressed = false
                        }
                },
                modifier = Modifier.defaultMinSize(minHeight = 140.dp).scale(scale),
                colors =
                        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation =
                        CardDefaults.cardElevation(
                                defaultElevation = 2.dp,
                                pressedElevation = 8.dp
                        ),
                shape = RoundedCornerShape(12.dp)
        ) {
                // 卡片内容
                Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                        // 工具图标带有背景圆圈
                        Box(
                                contentAlignment = Alignment.Center,
                                modifier =
                                        Modifier.size(48.dp)
                                                .clip(CircleShape)
                                                .background(
                                                        color =
                                                                when (tool.category) {
                                                                        ToolCategory
                                                                                .FILE_MANAGEMENT ->
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .primaryContainer
                                                                        ToolCategory.DEVELOPMENT ->
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .tertiaryContainer
                                                                        ToolCategory.SYSTEM ->
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .secondaryContainer
                                                                        else ->
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .primaryContainer
                                                                }
                                                )
                                                .padding(8.dp)
                        ) {
                                Icon(
                                        imageVector = tool.icon,
                                        contentDescription = stringResource(tool.name),
                                        modifier = Modifier.size(24.dp),
                                        tint =
                                                when (tool.category) {
                                                        ToolCategory.FILE_MANAGEMENT ->
                                                                MaterialTheme.colorScheme.primary
                                                        ToolCategory.DEVELOPMENT ->
                                                                MaterialTheme.colorScheme.tertiary
                                                        ToolCategory.SYSTEM ->
                                                                MaterialTheme.colorScheme.secondary
                                                        else -> MaterialTheme.colorScheme.primary
                                                }
                                )
                        }

                        Text(
                                text = stringResource(tool.name),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                        )

                        Text(
                                text = stringResource(tool.description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 2.dp),
                                maxLines = 2
                        )
                }
        }
}

/** 显示格式转换工具屏幕 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormatConverterToolScreen(navController: NavController) {
        Scaffold() { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues)) {
                        FormatConverterScreen(navController = navController)
                }
        }
}

/** 显示文件管理器工具屏幕 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerToolScreen(navController: NavController) {
        Scaffold() { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues)) {
                        FileManagerScreen(navController = navController)
                }
        }
}

/** 显示终端工具屏幕 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalToolScreen(navController: NavController) {
        Scaffold() { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues)) { TerminalScreen() }
        }
}

/** 显示终端自动配置工具屏幕 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalAutoConfigToolScreen(navController: NavController) {
        Scaffold() { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues)) {
                        TerminalAutoConfigScreen(navController = navController)
                }
        }
}

/** 显示应用权限管理工具屏幕 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPermissionsToolScreen(navController: NavController) {
        Scaffold() { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues)) {
                        AppPermissionsScreen(navController = navController)
                }
        }
}

/** 显示UI调试工具屏幕 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UIDebuggerToolScreen(navController: NavController) {
        Scaffold() { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues)) {
                        UIDebuggerScreen(navController = navController)
                }
        }
}

/** 显示FFmpeg工具箱屏幕 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FFmpegToolboxToolScreen(navController: NavController) {
        Scaffold() { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues)) {
                        FFmpegToolboxScreen(navController = navController)
                }
        }
}

/** 显示Shell命令执行器屏幕 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShellExecutorToolScreen(navController: NavController) {
        Scaffold() { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues)) {
                        ShellExecutorScreen(navController = navController)
                }
        }
}

/** 显示日志查看器屏幕 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogcatToolScreen(navController: NavController) {
        Scaffold() { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues)) {
                        LogcatScreen(navController = navController)
                }
        }
}
