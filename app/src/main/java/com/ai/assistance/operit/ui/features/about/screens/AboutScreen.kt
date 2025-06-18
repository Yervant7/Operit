package com.ai.assistance.operit.ui.features.about.screens

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.text.Html
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.updates.UpdateManager
import com.ai.assistance.operit.data.updates.UpdateStatus
import kotlinx.coroutines.launch

@Composable
fun HtmlText(
        html: String,
        modifier: Modifier = Modifier,
        style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium
) {
    val context = LocalContext.current
    val textColor = style.color.toArgb()

    AndroidView(
            modifier = modifier,
            factory = { context ->
                TextView(context).apply {
                    this.textSize = style.fontSize.value
                    this.setTextColor(textColor)
                    this.movementMethod = LinkMovementMethod.getInstance()
                }
            },
            update = { textView ->
                textView.text =
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                            Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)
                        } else {
                            @Suppress("DEPRECATION") Html.fromHtml(html)
                        }
            }
    )
}

@Composable
fun InfoItem(
        icon: ImageVector,
        title: String,
        content: @Composable () -> Unit,
        modifier: Modifier = Modifier
) {
    Row(
            modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.Top
    ) {
        Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 16.dp, top = 2.dp)
        )
        Column {
            Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
            )
            Box(modifier = Modifier.padding(top = 4.dp)) { content() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 获取UpdateManager实例
    val updateManager = remember { UpdateManager.getInstance(context) }

    // 监听更新状态
    var updateStatus by remember { mutableStateOf<UpdateStatus>(UpdateStatus.Initial) }

    // 观察UpdateManager的LiveData
    DisposableEffect(updateManager) {
        val observer =
                androidx.lifecycle.Observer<UpdateStatus> { newStatus -> updateStatus = newStatus }
        updateManager.updateStatus.observeForever(observer)

        onDispose { updateManager.updateStatus.removeObserver(observer) }
    }

    // 显示更新对话框
    var showUpdateDialog by remember { mutableStateOf(false) }
    // 添加下载源选择对话框状态
    var showDownloadSourceMenu by remember { mutableStateOf(false) }

    // 检查更新按钮动画
    val buttonAlpha =
            animateFloatAsState(
                    targetValue =
                            if (updateStatus is UpdateStatus.Checking)
                                    0.6f
                            else 1f,
                    label = "ButtonAlpha"
            )

    // 获取应用版本信息
    val appVersion = remember {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            context.getString(R.string.about_unknown_version)
        }
    }

    // 观察更新状态变化
    LaunchedEffect(updateStatus) {
        when (updateStatus) {
            is UpdateStatus.Available, is UpdateStatus.UpToDate, is UpdateStatus.Error -> {
                showUpdateDialog = true
            }
            else -> {}
        }
    }

    // 检查更新
    fun checkForUpdates() {
        scope.launch { updateManager.checkForUpdates(appVersion) }
    }

    // 处理下载更新 - 显示下载源选择对话框
    fun handleDownload() {
        val status = updateStatus as? UpdateStatus.Available ?: return
        if (status.downloadUrl.isNotEmpty()) {
            showDownloadSourceMenu = true // 显示下载源选择对话框
        } else {
            // 如果没有下载链接，则直接打开更新页面
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(status.updateUrl)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            showUpdateDialog = false
        }
    }

    // 从指定源下载
    fun downloadFromSource(sourceType: String) {
        val status = updateStatus as? UpdateStatus.Available ?: return
        val downloadUrl = when (sourceType) {
            "github" -> status.downloadUrl // 原始GitHub链接
            "ghfast" -> {
                // 使用 ghfast.top 镜像加速 GitHub 下载
                if (status.downloadUrl.contains("github.com") && status.downloadUrl.endsWith(".apk")) {
                    UpdateManager.getAcceleratedDownloadUrl(status.newVersion, status.downloadUrl)
                } else {
                    status.downloadUrl
                }
            }
            else -> status.downloadUrl
        }
        
        // 打开浏览器下载
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(downloadUrl)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        showDownloadSourceMenu = false
        showUpdateDialog = false
    }

    // 更新对话框
    if (showUpdateDialog) {
        AlertDialog(
                onDismissRequest = { showUpdateDialog = false },
                title = {
                    Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val icon =
                                when (updateStatus) {
                                    is UpdateStatus.Available -> Icons.Default.Update
                                    is UpdateStatus.Checking -> Icons.Default.Download
                                    is UpdateStatus.UpToDate -> Icons.Default.CheckCircle
                                    is UpdateStatus.Error -> Icons.Default.Error
                                    else -> Icons.Default.Update
                                }

                        val iconTint =
                                when (updateStatus) {
                                    is UpdateStatus.Available -> MaterialTheme.colorScheme.primary
                                    is UpdateStatus.Checking -> MaterialTheme.colorScheme.primary
                                    is UpdateStatus.UpToDate -> Color(0xFF4CAF50) // Green
                                    is UpdateStatus.Error -> Color(0xFFF44336) // Red
                                    else -> MaterialTheme.colorScheme.primary
                                }

                        Icon(imageVector = icon, contentDescription = null, tint = iconTint)

                        Text(
                                text =
                                        when (updateStatus) {
                                            is UpdateStatus.Available -> stringResource(R.string.about_update_dialog_title_available)
                                            is UpdateStatus.Checking -> stringResource(R.string.about_update_dialog_title_checking)
                                            is UpdateStatus.UpToDate -> stringResource(R.string.about_update_dialog_title_complete)
                                            is UpdateStatus.Error -> stringResource(R.string.about_update_dialog_title_failed)
                                            else -> stringResource(R.string.about_update_dialog_title_generic)
                                        }
                        )
                    }
                },
                text = {
                    when (val status = updateStatus) {
                        is UpdateStatus.Available -> {
                            Column {
                                Text(
                                        stringResource(R.string.about_update_dialog_versions_info, appVersion, status.newVersion),
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                )

                                if (status.releaseNotes.isNotEmpty()) {
                                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                                    Text(
                                            stringResource(R.string.about_update_dialog_release_notes_title),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(bottom = 4.dp)
                                    )

                                    Text(
                                            status.releaseNotes,
                                            style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                        is UpdateStatus.UpToDate -> {
                            Text(stringResource(R.string.about_update_dialog_up_to_date, appVersion))
                        }
                        is UpdateStatus.Error -> {
                            Text(status.message)
                        }
                        else -> {
                            Text(stringResource(R.string.about_update_dialog_checking_message))
                        }
                    }
                },
                confirmButton = {
                    Button(
                            onClick = {
                                if (updateStatus is UpdateStatus.Available) {
                                    handleDownload()
                                } else if (updateStatus !is UpdateStatus.Checking) {
                                    showUpdateDialog = false
                                }
                            },
                            enabled =
                                    updateStatus is UpdateStatus.Available ||
                                            (updateStatus !is UpdateStatus.Checking)
                    ) {
                        Text(
                                when (updateStatus) {
                                    is UpdateStatus.Available -> stringResource(R.string.about_update_dialog_button_download)
                                    else -> stringResource(R.string.about_update_dialog_button_ok)
                                }
                        )
                    }
                },
                dismissButton = {
                    if (updateStatus !is UpdateStatus.Checking) {
                        TextButton(onClick = { showUpdateDialog = false }) { Text(stringResource(R.string.about_update_dialog_button_close)) }
                    }
                }
        )
    }

    // 下载源选择对话框
    if (showDownloadSourceMenu) {
        AlertDialog(
            onDismissRequest = { showDownloadSourceMenu = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        stringResource(R.string.about_download_source_dialog_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        stringResource(R.string.about_download_source_dialog_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    // 国内加速镜像选项 - 使用Card而不是Button
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { downloadFromSource("ghfast") },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Storage,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    stringResource(R.string.about_download_source_accelerated_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    stringResource(R.string.about_download_source_accelerated_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // GitHub原始链接选项
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { downloadFromSource("github") },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    stringResource(R.string.about_download_source_github_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    stringResource(R.string.about_download_source_github_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = { showDownloadSourceMenu = false },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        stringResource(R.string.about_download_source_dialog_button_cancel),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(
                modifier =
                        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // App Logo with circular background
            Box(
                    modifier =
                            Modifier.size(140.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(20.dp),
                    contentAlignment = Alignment.Center
            ) {
                Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = stringResource(R.string.about_app_logo_content_desc),
                        modifier = Modifier.size(100.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // App Name
            Text(
                    text = stringResource(id = R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
            )

            // App Version
            Text(
                    text = stringResource(id = R.string.about_version, appVersion),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            // 添加检查更新按钮 - 美化版
            Button(
                    onClick = { checkForUpdates() },
                    modifier =
                            Modifier.fillMaxWidth(0.8f)
                                    .height(48.dp)
                                    .alpha(buttonAlpha.value)
                                    .padding(horizontal = 8.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors =
                            ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                    enabled = updateStatus !is UpdateStatus.Checking
            ) {
                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                ) {
                    if (updateStatus is UpdateStatus.Checking) {
                        // 使用简单的点代替复杂的加载指示器
                        Box(
                                modifier =
                                        Modifier.size(20.dp)
                                                .background(
                                                        color =
                                                                MaterialTheme.colorScheme.onPrimary
                                                                        .copy(alpha = 0.7f),
                                                        shape = CircleShape
                                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        Icon(
                                imageVector = Icons.Default.Update,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                            text = if (updateStatus is UpdateStatus.Checking) stringResource(R.string.about_button_checking_for_updates) else stringResource(R.string.check_for_updates_button_text),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Card with app information - 美化版
            Card(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .shadow(elevation = 4.dp, shape = RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.Start
                ) {
                    Text(
                            text = stringResource(id = R.string.about_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Text(
                            text = stringResource(id = R.string.about_description),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 24.dp)
                    )

                    // 使用InfoItem组件展示信息
                    InfoItem(
                            icon = Icons.Rounded.Info,
                            title = stringResource(R.string.about_info_developer),
                            content = {
                                HtmlText(
                                        html = stringResource(id = R.string.about_developer),
                                        style = MaterialTheme.typography.bodyMedium
                                )
                            }
                    )

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    InfoItem(
                            icon = Icons.Rounded.Info,
                            title = stringResource(R.string.about_info_contact),
                            content = {
                                Text(
                                        text = stringResource(id = R.string.about_contact),
                                        style = MaterialTheme.typography.bodyMedium
                                )
                            }
                    )

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    InfoItem(
                            icon = Icons.Rounded.Info,
                            title = stringResource(R.string.about_info_project_address),
                            content = {
                                HtmlText(
                                        html = stringResource(id = R.string.about_website),
                                        style = MaterialTheme.typography.bodyMedium
                                )
                            }
                    )

                    Divider(modifier = Modifier.padding(vertical = 16.dp))

                    Text(
                            text = stringResource(id = R.string.about_copyright),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
