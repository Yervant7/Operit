package com.ai.assistance.operit.ui.features.packages.screens

import android.util.Log
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.consumePositionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.mcp.InstallResult
import com.ai.assistance.operit.data.mcp.MCPRepository
import com.ai.assistance.operit.data.mcp.MCPRepositoryConstants.SortDirection
import com.ai.assistance.operit.data.mcp.MCPRepositoryConstants.SortOptions
import com.ai.assistance.operit.ui.features.packages.components.dialogs.MCPServerDetailsDialog
import com.ai.assistance.operit.ui.features.packages.screens.mcp.components.MCPInstallProgressDialog
import com.ai.assistance.operit.ui.features.packages.screens.mcp.components.MCPServerItem
import com.ai.assistance.operit.ui.features.packages.screens.mcp.model.MCPServer
import com.ai.assistance.operit.ui.features.packages.screens.mcp.viewmodel.MCPViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * The main screen for displaying and managing MCP servers.
 *
 * @param mcpRepository The repository for fetching and managing MCP servers
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MCPScreen(mcpRepository: MCPRepository) {
        // 创建 ViewModel
        val viewModel = remember {
                MCPViewModel.Factory(mcpRepository).create(MCPViewModel::class.java)
        }

        // 安装状态
        val installProgress by viewModel.installProgress.collectAsState()
        val installResult by viewModel.installResult.collectAsState()
        val currentInstallingServer by viewModel.currentServer.collectAsState()

        // Convert data.mcp.MCPServer to model.MCPServer
        val dataServers by mcpRepository.mcpServers.collectAsState()

        // Convert data MCPServer to model MCPServer
        val mcpServers =
                remember(dataServers) {
                        dataServers.map { dataServer ->
                                MCPServer(
                                        id = dataServer.id,
                                        name = dataServer.name,
                                        description = dataServer.description,
                                        logoUrl = dataServer.logoUrl,
                                        stars = dataServer.stars,
                                        category = dataServer.category,
                                        requiresApiKey = dataServer.requiresApiKey,
                                        author = dataServer.author,
                                        isVerified = dataServer.isVerified,
                                        isInstalled = dataServer.isInstalled,
                                        version = dataServer.version,
                                        updatedAt = dataServer.updatedAt,
                                        longDescription = dataServer.longDescription,
                                        repoUrl = dataServer.repoUrl
                                )
                        }
                }

        val isLoading by mcpRepository.isLoading.collectAsState()
        val hasMore by mcpRepository.hasMore.collectAsState()
        val error by mcpRepository.errorMessage.collectAsState()
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()

        // 搜索和筛选状态
        val searchText = remember { mutableStateOf("") }

        // Add debounced search text state
        var debouncedSearchText by remember { mutableStateOf("") }

        // Add search state
        var isSearchActive by remember { mutableStateOf(false) }

        // 排序状态
        var showSortMenu by remember { mutableStateOf(false) }
        var sortOption by remember { mutableStateOf(SortOptions.RECOMMENDED) }
        var sortDirection by remember { mutableStateOf(SortDirection.DESC) }

        // Selected server for details dialog
        var selectedServer by remember { mutableStateOf<MCPServer?>(null) }

        // 仅在本地过滤已安装的插件
        val displayedServers =
                remember(mcpServers, debouncedSearchText) {
                        // 删除 if (showInstalledOnly) 的条件过滤

                        // Apply local search if text is present
                        if (debouncedSearchText.isNotBlank()) {
                                val searchTerms = debouncedSearchText.lowercase().split(" ")
                                mcpServers.filter { server ->
                                        searchTerms.all { term ->
                                                server.name.lowercase().contains(term) ||
                                                        server.description
                                                                .lowercase()
                                                                .contains(term) ||
                                                        server.author.lowercase().contains(term) ||
                                                        server.category
                                                                .lowercase()
                                                                .contains(term) ||
                                                        server.id.lowercase().contains(term)
                                        }
                                }
                        } else {
                                mcpServers
                        }
                }

        // Debounce search text changes
        LaunchedEffect(searchText.value) {
                delay(300) // Wait for 300ms of inactivity before updating
                debouncedSearchText = searchText.value

                // Only trigger network search if actively searching and text is not empty
                if (isSearchActive && debouncedSearchText.isNotBlank()) {
                        // Call the search function
                        coroutineScope.launch {
                                mcpRepository.fetchMCPServers(
                                        forceRefresh = true,
                                        query = debouncedSearchText,
                                        sortBy = sortOption,
                                        sortDirection = sortDirection
                                )
                        }
                }
        }

        // 执行搜索 - local filtering happens automatically
        fun performSearch() {
                isSearchActive = true
                // Perform network search
                coroutineScope.launch {
                        mcpRepository.fetchMCPServers(
                                forceRefresh = true,
                                query = debouncedSearchText,
                                sortBy = sortOption,
                                sortDirection = sortDirection
                        )
                }
        }

        // Initialize fetch on first load
        LaunchedEffect(Unit) {
                // 检查是否已有加载的服务器数据
                if (mcpServers.isEmpty()) {
                        // 首先扫描已安装的插件状态
                        mcpRepository.syncInstalledStatus()
                        // 获取服务器列表（默认会先加载官方插件，再加载第三方插件）
                        mcpRepository.fetchMCPServers(forceRefresh = true)
                } else {
                        // 如果已有数据，只确保安装状态是最新的
                        mcpRepository.syncInstalledStatus()
                }
        }

        // 初始化下拉刷新状态
        var refreshing by remember { mutableStateOf(false) }
        var pullOffset by remember { mutableStateOf(0f) }
        val pullThreshold = 120f
        val listState = rememberLazyListState()

        // 监控列表滚动状态 - 如果到达底部则自动加载更多
        LaunchedEffect(listState, hasMore, isLoading) {
                snapshotFlow {
                        val layoutInfo = listState.layoutInfo
                        val totalItemsCount = layoutInfo.totalItemsCount
                        val lastVisibleItemIndex =
                                (layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0)

                        // 删除 showInstalledOnly 相关的条件
                        val shouldConsiderLoadingMore = hasMore && displayedServers.isNotEmpty()

                        // 提前检测 - 当滚动到距离底部更远的位置时触发加载
                        // 增加阈值为10，确保有足够的提前量
                        val isNearBottom = lastVisibleItemIndex >= totalItemsCount - 10

                        shouldConsiderLoadingMore && isNearBottom && !isLoading
                }
                        .distinctUntilChanged()
                        .collect { shouldLoadMore ->
                                if (shouldLoadMore) {
                                        Log.d("MCPScreen", "触发自动加载更多内容")
                                        coroutineScope.launch {
                                                try {
                                                        mcpRepository.fetchMCPServers(
                                                                forceRefresh = false,
                                                                query = debouncedSearchText,
                                                                sortBy = sortOption,
                                                                sortDirection = sortDirection
                                                        )
                                                        Log.d("MCPScreen", "加载更多内容成功")
                                                } catch (e: Exception) {
                                                        Log.e("MCPScreen", "加载更多内容失败: ${e.message}")
                                                }
                                        }
                                }
                        }
        }

        // 根据刷新状态重置pullOffset
        LaunchedEffect(refreshing) {
                if (refreshing) {
                        delay(1000)
                        mcpRepository.fetchMCPServers(
                                forceRefresh = true,
                                query = searchText.value,
                                sortBy = sortOption,
                                sortDirection = sortDirection
                        )
                        delay(500)
                        refreshing = false
                        pullOffset = 0f
                }
        }

        // 处理安装状态变化时进行UI更新
        LaunchedEffect(installResult) {
                if (installResult != null) {
                        // 安装或卸载操作完成后，刷新本地插件状态
                        viewModel.refreshLocalPlugins()
                }
        }

        Column(modifier = Modifier.fillMaxSize()) {
                // 顶部搜索栏、排序和过滤选项 - 更紧凑的设计
                Column(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                        // 搜索栏和排序按钮 - 更具流线型设计
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                                // 搜索输入框 - 更紧凑
                                OutlinedTextField(
                                        value = searchText.value,
                                        onValueChange = { searchText.value = it },
                                        placeholder = {
                                                Text(
                                                        stringResource(R.string.mcp_search_placeholder),
                                                        style = MaterialTheme.typography.bodyMedium
                                                )
                                        },
                                        singleLine = true,
                                        leadingIcon = {
                                                Icon(
                                                        imageVector = Icons.Default.Search,
                                                        contentDescription = stringResource(R.string.search_content_description),
                                                        tint =
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant
                                                )
                                        },
                                        trailingIcon = {
                                                if (searchText.value.isNotEmpty()) {
                                                        IconButton(
                                                                onClick = {
                                                                        searchText.value = ""
                                                                        isSearchActive = false
                                                                },
                                                                modifier = Modifier.size(40.dp)
                                                        ) {
                                                                Icon(
                                                                        imageVector =
                                                                                Icons.Default
                                                                                        .CheckCircle,
                                                                        contentDescription = stringResource(R.string.clear_search_content_description),
                                                                        tint =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onSurfaceVariant
                                                                )
                                                        }
                                                }
                                        },
                                        keyboardActions =
                                                KeyboardActions(onSearch = { performSearch() }),
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        colors =
                                                TextFieldDefaults.outlinedTextFieldColors(
                                                        containerColor =
                                                                MaterialTheme.colorScheme
                                                                        .surfaceVariant.copy(
                                                                        alpha = 0.3f
                                                                ),
                                                        unfocusedBorderColor =
                                                                MaterialTheme.colorScheme
                                                                        .surfaceVariant
                                                ),
                                        textStyle = MaterialTheme.typography.bodyMedium
                                )

                                // 排序按钮 - 更美观的圆形按钮
                                Box {
                                        IconButton(
                                                onClick = { showSortMenu = true },
                                                modifier =
                                                        Modifier.size(40.dp)
                                                                .clip(CircleShape)
                                                                .background(
                                                                        MaterialTheme.colorScheme
                                                                                .primaryContainer
                                                                                .copy(alpha = 0.7f)
                                                                )
                                        ) {
                                                Icon(
                                                        imageVector = Icons.Default.Sort,
                                                        contentDescription = stringResource(R.string.sort_options_content_description),
                                                        tint =
                                                                MaterialTheme.colorScheme
                                                                        .onPrimaryContainer,
                                                        modifier = Modifier.size(18.dp)
                                                )
                                        }

                                        // 排序下拉菜单
                                        DropdownMenu(
                                                expanded = showSortMenu,
                                                onDismissRequest = { showSortMenu = false }
                                        ) {
                                                Text(
                                                        text = stringResource(R.string.sort_by_header),
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier =
                                                                Modifier.padding(
                                                                        horizontal = 16.dp,
                                                                        vertical = 8.dp
                                                                )
                                                )

                                                // 排序选项
                                                SortMenuItem(
                                                        stringResource(R.string.sort_by_recommended),
                                                        SortOptions.RECOMMENDED,
                                                        sortOption,
                                                        sortDirection
                                                ) { newOption, newDirection ->
                                                        sortOption = newOption
                                                        sortDirection = newDirection
                                                        showSortMenu = false
                                                        performSearch()
                                                }

                                                SortMenuItem(
                                                        stringResource(R.string.sort_by_created_time),
                                                        SortOptions.CREATED,
                                                        sortOption,
                                                        sortDirection
                                                ) { newOption, newDirection ->
                                                        sortOption = newOption
                                                        sortDirection = newDirection
                                                        showSortMenu = false
                                                        performSearch()
                                                }

                                                SortMenuItem(
                                                        stringResource(R.string.sort_by_updated_time),
                                                        SortOptions.UPDATED,
                                                        sortOption,
                                                        sortDirection
                                                ) { newOption, newDirection ->
                                                        sortOption = newOption
                                                        sortDirection = newDirection
                                                        showSortMenu = false
                                                        performSearch()
                                                }

                                                SortMenuItem(
                                                        stringResource(R.string.sort_by_comments),
                                                        SortOptions.COMMENTS,
                                                        sortOption,
                                                        sortDirection
                                                ) { newOption, newDirection ->
                                                        sortOption = newOption
                                                        sortDirection = newDirection
                                                        showSortMenu = false
                                                        performSearch()
                                                }

                                                SortMenuItem(
                                                        stringResource(R.string.sort_by_reactions),
                                                        SortOptions.REACTIONS,
                                                        sortOption,
                                                        sortDirection
                                                ) { newOption, newDirection ->
                                                        sortOption = newOption
                                                        sortDirection = newDirection
                                                        showSortMenu = false
                                                        performSearch()
                                                }
                                        }
                                }

                                // 刷新按钮
                                IconButton(
                                        onClick = {
                                                refreshing = true
                                                coroutineScope.launch {
                                                        mcpRepository.fetchMCPServers(
                                                                forceRefresh = true,
                                                                query = debouncedSearchText,
                                                                sortBy = sortOption,
                                                                sortDirection = sortDirection
                                                        )
                                                }
                                        },
                                        modifier =
                                                Modifier.size(40.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                                MaterialTheme.colorScheme
                                                                        .primaryContainer.copy(
                                                                        alpha = 0.7f
                                                                )
                                                        )
                                ) {
                                        Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = stringResource(R.string.refresh_content_description),
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(18.dp)
                                        )
                                }
                        }
                }

                // 实验性功能警告横幅
                Card(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 4.dp),
                        colors =
                                CardDefaults.cardColors(
                                        containerColor =
                                                MaterialTheme.colorScheme.errorContainer.copy(
                                                        alpha = 0.7f
                                                )
                                ),
                        shape = RoundedCornerShape(8.dp)
                ) {
                        Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                                Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = stringResource(R.string.warning_content_description),
                                        tint = MaterialTheme.colorScheme.error
                                )
                                Text(
                                        text = stringResource(R.string.mcp_experimental_warning),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                )
                        }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                        if (isLoading && mcpServers.isEmpty()) {
                                // 加载中状态 - 更现代化的加载指示器
                                Column(
                                        modifier = Modifier.align(Alignment.Center),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                ) {
                                        Surface(
                                                modifier = Modifier.size(40.dp),
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                        ) {
                                                Box(
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentAlignment = Alignment.Center
                                                ) {
                                                        // 添加旋转动画
                                                        val infiniteTransition =
                                                                rememberInfiniteTransition()
                                                        val rotation by
                                                                infiniteTransition.animateFloat(
                                                                        initialValue = 0f,
                                                                        targetValue = 360f,
                                                                        animationSpec =
                                                                                infiniteRepeatable(
                                                                                        animation =
                                                                                                tween(
                                                                                                        1000,
                                                                                                        easing =
                                                                                                                LinearEasing
                                                                                                ),
                                                                                        repeatMode =
                                                                                                RepeatMode
                                                                                                        .Restart
                                                                                )
                                                                )

                                                        Icon(
                                                                imageVector = Icons.Default.Refresh,
                                                                contentDescription = stringResource(R.string.loading_content_description),
                                                                modifier =
                                                                        Modifier.size(20.dp)
                                                                                .graphicsLayer {
                                                                                        rotationZ =
                                                                                                rotation
                                                                                }
                                                        )
                                                }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                                text = stringResource(R.string.loading_text_alt),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                        )
                                }
                        } else if (displayedServers.isEmpty() && !isLoading) {
                                // 数据为空状态 - 更精简
                                Column(
                                        modifier = Modifier.align(Alignment.Center),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                ) {
                                        Text(
                                                text = stringResource(R.string.warning_emoji),
                                                style = MaterialTheme.typography.headlineLarge,
                                                textAlign = TextAlign.Center,
                                                color = MaterialTheme.colorScheme.error
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                                text =
                                                        if (debouncedSearchText.isNotBlank())
                                                                stringResource(R.string.mcp_no_plugins_found_search)
                                                        else stringResource(R.string.mcp_no_plugins_available),
                                                style = MaterialTheme.typography.bodyLarge,
                                                textAlign = TextAlign.Center,
                                                color = MaterialTheme.colorScheme.onSurface
                                        )

                                        if (error != null) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                        text = error!!,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        textAlign = TextAlign.Center,
                                                        color = MaterialTheme.colorScheme.error
                                                )
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))
                                        Button(
                                                onClick = {
                                                        refreshing = true
                                                        coroutineScope.launch {
                                                                mcpRepository.fetchMCPServers(
                                                                        forceRefresh = true
                                                                )
                                                        }
                                                }
                                        ) {
                                                Icon(
                                                        imageVector = Icons.Default.Refresh,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(stringResource(R.string.refresh_button))
                                        }
                                }
                        } else {
                                Box(
                                        modifier =
                                                Modifier.fillMaxSize()
                                                        .padding(vertical = 4.dp, horizontal = 4.dp)
                                ) {
                                        // 下拉刷新指示器
                                        if (pullOffset > 0 || refreshing) {
                                                RefreshingIndicator(
                                                        modifier =
                                                                Modifier.align(Alignment.TopCenter)
                                                                        .padding(top = 8.dp),
                                                        isRefreshing = refreshing,
                                                        progress =
                                                                (pullOffset / pullThreshold)
                                                                        .coerceIn(0f, 1f)
                                                )
                                        }

                                        // 插件列表
                                        LazyColumn(
                                                state = listState,
                                                modifier =
                                                        Modifier.fillMaxSize()
                                                                .offset(
                                                                        y =
                                                                                pullOffset
                                                                                        .roundToInt()
                                                                                        .dp
                                                                )
                                                                .pointerInput(Unit) {
                                                                        detectVerticalDragGestures(
                                                                                onVerticalDrag = {
                                                                                        change,
                                                                                        dragAmount
                                                                                        ->
                                                                                        change.consumePositionChange()
                                                                                        if (listState
                                                                                                        .firstVisibleItemIndex ==
                                                                                                        0 &&
                                                                                                        (listState
                                                                                                                .firstVisibleItemScrollOffset ==
                                                                                                                0 ||
                                                                                                                pullOffset >
                                                                                                                        0)
                                                                                        ) {
                                                                                                if (!refreshing
                                                                                                ) {
                                                                                                        pullOffset =
                                                                                                                (pullOffset +
                                                                                                                                dragAmount)
                                                                                                                        .coerceAtLeast(
                                                                                                                                0f
                                                                                                                        )
                                                                                                }
                                                                                        }
                                                                                },
                                                                                onDragEnd = {
                                                                                        if (pullOffset >
                                                                                                        pullThreshold &&
                                                                                                        !refreshing
                                                                                        ) {
                                                                                                refreshing =
                                                                                                        true
                                                                                                coroutineScope
                                                                                                        .launch {
                                                                                                                mcpRepository
                                                                                                                        .fetchMCPServers(
                                                                                                                                forceRefresh =
                                                                                                                                        true,
                                                                                                                                query =
                                                                                                                                        searchText
                                                                                                                                                .value,
                                                                                                                                sortBy =
                                                                                                                                        sortOption,
                                                                                                                                sortDirection =
                                                                                                                                        sortDirection
                                                                                                                        )
                                                                                                        }
                                                                                        } else if (!refreshing
                                                                                        ) {
                                                                                                pullOffset =
                                                                                                        0f
                                                                                        }
                                                                                }
                                                                        )
                                                                },
                                                contentPadding = PaddingValues(vertical = 4.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                                // 确保每个项目有唯一的键，以避免 "key was already used" 错误
                                                itemsIndexed(
                                                        items = displayedServers,
                                                        key = { index, server ->
                                                                "${server.id}_$index"
                                                        }
                                                ) { _, server ->
                                                        MCPServerItem(
                                                                server = server,
                                                                onClick = {
                                                                        selectedServer = server
                                                                }
                                                        )
                                                }

                                                // 显示加载中指示器 - 更新条件，让它在滚动到底部且hasMore时就显示
                                                item {
                                                        if (!hasMore) {
                                                                Box(
                                                                        modifier =
                                                                                Modifier.fillMaxWidth()
                                                                                        .padding(
                                                                                                vertical =
                                                                                                        12.dp
                                                                                        )
                                                                                        .height(
                                                                                                60.dp
                                                                                        ),
                                                                        contentAlignment =
                                                                                Alignment.Center
                                                                ) {
                                                                        Row(
                                                                                verticalAlignment =
                                                                                        Alignment
                                                                                                .CenterVertically,
                                                                                horizontalArrangement =
                                                                                        Arrangement
                                                                                                .Center,
                                                                                modifier =
                                                                                        Modifier.clickable(
                                                                                                onClick = {
                                                                                                        if (!isLoading &&
                                                                                                                        hasMore
                                                                                                        ) {
                                                                                                                Log.d(
                                                                                                                        "MCPScreen",
                                                                                                                        "手动点击加载更多"
                                                                                                                )
                                                                                                                coroutineScope
                                                                                                                        .launch {
                                                                                                                                mcpRepository
                                                                                                                                        .fetchMCPServers(
                                                                                                                                                forceRefresh =
                                                                                                                                                        false,
                                                                                                                                                query =
                                                                                                                                                        debouncedSearchText,
                                                                                                                                                sortBy =
                                                                                                                                                        sortOption,
                                                                                                                                                sortDirection =
                                                                                                                                                        sortDirection
                                                                                                                                        )
                                                                                                                        }
                                                                                                        }
                                                                                                }
                                                                                        )
                                                                        ) {
                                                                                // 加载中指示器
                                                                                val rotation by
                                                                                        rememberInfiniteTransition(
                                                                                                        label =
                                                                                                                "loadingRotation"
                                                                                                )
                                                                                                .animateFloat(
                                                                                                        initialValue =
                                                                                                                0f,
                                                                                                        targetValue =
                                                                                                                360f,
                                                                                                        animationSpec =
                                                                                                                infiniteRepeatable(
                                                                                                                        animation =
                                                                                                                                tween(
                                                                                                                                        1000,
                                                                                                                                        easing =
                                                                                                                                                LinearEasing
                                                                                                                                ),
                                                                                                                        repeatMode =
                                                                                                                                RepeatMode
                                                                                                                                        .Restart
                                                                                                                ),
                                                                                                        label =
                                                                                                                "loadingRotation"
                                                                                                )

                                                                                Box(
                                                                                        modifier =
                                                                                                Modifier.size(
                                                                                                                24.dp
                                                                                                        )
                                                                                                        .clip(
                                                                                                                CircleShape
                                                                                                        )
                                                                                                        .background(
                                                                                                                MaterialTheme
                                                                                                                        .colorScheme
                                                                                                                        .primaryContainer
                                                                                                        ),
                                                                                        contentAlignment =
                                                                                                Alignment
                                                                                                        .Center
                                                                                ) {
                                                                                        Icon(
                                                                                                imageVector =
                                                                                                        Icons.Default
                                                                                                                .Refresh,
                                                                                                contentDescription =
                                                                                                        stringResource(R.string.loading_content_description),
                                                                                                tint =
                                                                                                        MaterialTheme
                                                                                                                .colorScheme
                                                                                                                .primary,
                                                                                                modifier =
                                                                                                        Modifier.size(
                                                                                                                        16.dp
                                                                                                                )
                                                                                                                .graphicsLayer {
                                                                                                                        rotationZ =
                                                                                                                                if (isLoading
                                                                                                                                )
                                                                                                                                        rotation
                                                                                                                                else
                                                                                                                                        0f
                                                                                                                }
                                                                                        )
                                                                                }

                                                                                Spacer(
                                                                                        modifier =
                                                                                                Modifier.width(
                                                                                                        8.dp
                                                                                                )
                                                                                )
                                                                                Text(
                                                                                        text =
                                                                                                if (isLoading
                                                                                                )
                                                                                                        stringResource(R.string.loading_more_items_progress)
                                                                                                else
                                                                                                        stringResource(R.string.loading_more_items_auto),
                                                                                        style =
                                                                                                MaterialTheme
                                                                                                        .typography
                                                                                                        .bodyMedium,
                                                                                        color =
                                                                                                MaterialTheme
                                                                                                        .colorScheme
                                                                                                        .primary
                                                                                )
                                                                        }
                                                                }
                                                        }
                                                }
                                        }

                                        // 显示错误提示
                                        if (error != null && displayedServers.isNotEmpty()) {
                                                Card(
                                                        modifier =
                                                                Modifier.align(
                                                                                Alignment
                                                                                        .BottomCenter
                                                                        )
                                                                        .padding(16.dp)
                                                                        .fillMaxWidth(),
                                                        colors =
                                                                CardDefaults.cardColors(
                                                                        containerColor =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .errorContainer
                                                                )
                                                ) {
                                                        Row(
                                                                modifier = Modifier.padding(12.dp),
                                                                verticalAlignment =
                                                                        Alignment.CenterVertically
                                                        ) {
                                                                Text(
                                                                        text = error!!,
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .bodySmall,
                                                                        color =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onErrorContainer,
                                                                        modifier =
                                                                                Modifier.weight(1f)
                                                                )
                                                                TextButton(
                                                                        onClick = {
                                                                                refreshing = true
                                                                                coroutineScope
                                                                                        .launch {
                                                                                                mcpRepository
                                                                                                        .fetchMCPServers(
                                                                                                                forceRefresh =
                                                                                                                        true
                                                                                                        )
                                                                                        }
                                                                        }
                                                                ) { Text(stringResource(R.string.retry_button)) }
                                                        }
                                                }
                                        }
                                }
                        }
                }
        }

        // 显示服务器详情
        if (selectedServer != null) {
                val installedPath =
                        remember(selectedServer!!.id) {
                                viewModel.getInstalledPath(selectedServer!!.id)
                        }
                MCPServerDetailsDialog(
                        server = selectedServer!!,
                        onDismiss = { selectedServer = null },
                        onInstall = { server -> viewModel.installServer(server) },
                        onUninstall = { server -> viewModel.uninstallServer(server) },
                        installedPath = installedPath
                )
        }

        // 显示安装进度
        if (installProgress != null && currentInstallingServer != null) {
                // 将值存储在本地变量中以避免智能转换问题
                val currentInstallResult = installResult
                // 判断当前是否是卸载操作
                val isUninstallOperation =
                        if (currentInstallResult is InstallResult.Success) {
                                currentInstallResult.pluginPath.isEmpty()
                        } else {
                                false
                        }

                MCPInstallProgressDialog(
                        installProgress = installProgress,
                        onDismissRequest = { viewModel.resetInstallState() },
                        result = installResult,
                        serverName = currentInstallingServer?.name ?: stringResource(R.string.mcp_server_default_name),
                        // 添加操作类型参数：卸载/安装
                        operationType = if (isUninstallOperation) stringResource(R.string.uninstall_operation) else stringResource(R.string.install_operation)
                )
        }
}

/** 排序选项菜单项 */
@Composable
private fun SortMenuItem(
        label: String,
        option: SortOptions,
        currentOption: SortOptions,
        currentDirection: SortDirection,
        onClick: (SortOptions, SortDirection) -> Unit
) {
        val isSelected = currentOption == option
        var direction = currentDirection

        // 如果点击当前选中的选项，则切换方向
        if (isSelected) {
                direction =
                        if (currentDirection == SortDirection.DESC) SortDirection.ASC
                        else SortDirection.DESC
        } else {
                // 新选项默认为降序
                direction = SortDirection.DESC
        }

        DropdownMenuItem(
                text = {
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                Text(
                                        text = label,
                                        fontWeight =
                                                if (isSelected) FontWeight.Bold
                                                else FontWeight.Normal,
                                        color =
                                                if (isSelected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface
                                )

                                if (isSelected) {
                                        Text(
                                                text =
                                                        if (currentDirection == SortDirection.DESC)
                                                                stringResource(R.string.sort_direction_desc)
                                                        else stringResource(R.string.sort_direction_asc),
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                        )
                                }
                        }
                },
                onClick = { onClick(option, direction) }
        )
}

/** 获取排序选项的显示名称 */
@Composable
fun getSortDisplayName(sortOption: SortOptions): String {
        return when (sortOption) {
                SortOptions.RECOMMENDED -> stringResource(R.string.sort_by_recommended)
                SortOptions.CREATED -> stringResource(R.string.sort_by_created_time)
                SortOptions.UPDATED -> stringResource(R.string.sort_by_updated_time)
                SortOptions.COMMENTS -> stringResource(R.string.sort_by_comments)
                SortOptions.REACTIONS -> stringResource(R.string.sort_by_reactions)
        }
}

// Define a custom RefreshingIndicator for this screen
@Composable
fun RefreshingIndicator(modifier: Modifier = Modifier, isRefreshing: Boolean, progress: Float) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
                val rotation by
                        rememberInfiniteTransition(label = "refreshRotation")
                                .animateFloat(
                                        initialValue = 0f,
                                        targetValue = 360f,
                                        animationSpec =
                                                infiniteRepeatable(
                                                        animation =
                                                                tween(1000, easing = LinearEasing),
                                                        repeatMode = RepeatMode.Restart
                                                ),
                                        label = "refreshRotation"
                                )

                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                ) {
                        Box(
                                modifier =
                                        Modifier.size(24.dp)
                                                .clip(CircleShape)
                                                .background(
                                                        MaterialTheme.colorScheme.primaryContainer
                                                ),
                                contentAlignment = Alignment.Center
                        ) {
                                Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = stringResource(R.string.refreshing_content_description),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier =
                                                Modifier.size(16.dp).graphicsLayer {
                                                        rotationZ = rotation
                                                }
                                )
                        }

                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                                text = if (isRefreshing) stringResource(R.string.refreshing_text) else stringResource(R.string.pull_to_refresh),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                        )
                }
        }
}

// Define a custom LoadingItem for this screen
@Composable
fun LoadingItem(modifier: Modifier = Modifier) {
        Box(modifier = modifier.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                val animatedAlpha by
                        rememberInfiniteTransition(label = "loadingAlpha")
                                .animateFloat(
                                        initialValue = 0.4f,
                                        targetValue = 0.8f,
                                        animationSpec =
                                                infiniteRepeatable(
                                                        animation =
                                                                tween(800, easing = LinearEasing),
                                                        repeatMode = RepeatMode.Reverse
                                                ),
                                        label = "loadingAlpha"
                                )

                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                ) {
                        // Custom loading animation with three dots
                        Row(
                                modifier =
                                        Modifier.background(
                                                        MaterialTheme.colorScheme.primaryContainer
                                                                .copy(alpha = 0.3f),
                                                        RoundedCornerShape(12.dp)
                                                )
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                Text(
                                        text = stringResource(R.string.load_more),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                )
                        }
                }
        }
}
