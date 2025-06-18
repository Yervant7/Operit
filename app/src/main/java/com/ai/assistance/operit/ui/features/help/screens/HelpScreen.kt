package com.ai.assistance.operit.ui.features.help.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.ai.assistance.operit.R
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HelpScreen(onBackPressed: () -> Unit = {}) {
    val uriHandler = LocalUriHandler.current

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        // 标题部分
        Text(
                text = stringResource(R.string.help_screen_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
        )

        // 简介
        Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                        text = stringResource(R.string.help_screen_subtitle),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                        text = stringResource(R.string.help_screen_description),
                        style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // GitHub和官网链接
        Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // GitHub链接
            Surface(
                    modifier =
                            Modifier.weight(1f).clickable {
                                uriHandler.openUri("https://github.com/AAswordman/Operit")
                            },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = stringResource(R.string.help_screen_github_cd),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                            text = stringResource(R.string.help_screen_github_docs),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            // 官网链接
            Surface(
                    modifier =
                            Modifier.weight(1f).clickable {
                                uriHandler.openUri("https://operit.dev.tc/")
                            },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = stringResource(R.string.help_screen_website_cd),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                            text = stringResource(R.string.help_screen_official_website),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 内置工具部分
        var expandedCoreTools by remember { mutableStateOf(true) }
        ExpandableCard(
                title = stringResource(R.string.help_screen_core_tools_title),
                icon = Icons.Default.Build,
                expanded = expandedCoreTools,
                onExpandChange = { expandedCoreTools = it }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ToolCategoryItem(
                        title = stringResource(R.string.help_screen_basic_tools_category_title),
                        items =
                                listOf(
                                        Pair(R.string.help_tool_sleep_name, R.string.help_tool_sleep_desc),
                                        Pair(R.string.help_tool_device_info_name, R.string.help_tool_device_info_desc),
                                        Pair(R.string.help_tool_use_package_name, R.string.help_tool_use_package_desc),
                                        Pair(R.string.help_tool_query_problem_library_name, R.string.help_tool_query_problem_library_desc)
                                )
                )

                ToolCategoryItem(
                        title = stringResource(R.string.help_screen_filesystem_tools_category_title),
                        items =
                                listOf(
                                        Pair(R.string.help_tool_list_files_name, R.string.help_tool_list_files_desc),
                                        Pair(R.string.help_tool_read_file_part_name, R.string.help_tool_read_file_part_desc),
                                        Pair(R.string.help_tool_write_file_name, R.string.help_tool_write_file_desc),
                                        Pair(R.string.help_tool_apply_file_name, R.string.help_tool_apply_file_desc),
                                        Pair(R.string.help_tool_delete_file_name, R.string.help_tool_delete_file_desc),
                                        Pair(R.string.help_tool_file_exists_name, R.string.help_tool_file_exists_desc),
                                        Pair(R.string.help_tool_move_file_name, R.string.help_tool_move_file_desc),
                                        Pair(R.string.help_tool_copy_file_name, R.string.help_tool_copy_file_desc),
                                        Pair(R.string.help_tool_make_directory_name, R.string.help_tool_make_directory_desc),
                                        Pair(R.string.help_tool_find_files_name, R.string.help_tool_find_files_desc),
                                        Pair(R.string.help_tool_file_info_name, R.string.help_tool_file_info_desc),
                                        Pair(R.string.help_tool_zip_files_name, R.string.help_tool_zip_files_desc),
                                        Pair(R.string.help_tool_open_file_name, R.string.help_tool_open_file_desc),
                                        Pair(R.string.help_tool_share_file_name, R.string.help_tool_share_file_desc),
                                        Pair(R.string.help_tool_download_file_name, R.string.help_tool_download_file_desc),
                                        Pair(R.string.help_tool_convert_file_name, R.string.help_tool_convert_file_desc),
                                        Pair(R.string.help_tool_get_supported_conversions_name, R.string.help_tool_get_supported_conversions_desc)
                                )
                )

                ToolCategoryItem(
                        title = stringResource(R.string.help_screen_http_tools_category_title),
                        items =
                                listOf(
                                        Pair(R.string.help_tool_http_request_name, R.string.help_tool_http_request_desc),
                                        Pair(R.string.help_tool_multipart_request_name, R.string.help_tool_multipart_request_desc),
                                        Pair(R.string.help_tool_manage_cookies_name, R.string.help_tool_manage_cookies_desc),
                                        Pair(R.string.help_tool_visit_web_name, R.string.help_tool_visit_web_desc)
                                )
                )

                ToolCategoryItem(
                        title = stringResource(R.string.help_screen_system_tools_category_title),
                        items =
                                listOf(
                                        Pair(R.string.help_tool_get_system_setting_name, R.string.help_tool_get_system_setting_desc),
                                        Pair(R.string.help_tool_modify_system_setting_name, R.string.help_tool_modify_system_setting_desc),
                                        Pair(R.string.help_tool_install_app_name, R.string.help_tool_install_app_desc),
                                        Pair(R.string.help_tool_start_app_name, R.string.help_tool_start_app_desc),
                                        Pair(R.string.help_tool_get_notifications_name, R.string.help_tool_get_notifications_desc),
                                        Pair(R.string.help_tool_get_device_location_name, R.string.help_tool_get_device_location_desc)
                                )
                )

                ToolCategoryItem(
                        title = stringResource(R.string.help_screen_ui_auto_tools_category_title),
                        items =
                                listOf(
                                        Pair(R.string.help_tool_get_page_info_name, R.string.help_tool_get_page_info_desc),
                                        Pair(R.string.help_tool_tap_name, R.string.help_tool_tap_desc),
                                        Pair(R.string.help_tool_click_element_name, R.string.help_tool_click_element_desc),
                                        Pair(R.string.help_tool_set_input_text_name, R.string.help_tool_set_input_text_desc),
                                        Pair(R.string.help_tool_press_key_name, R.string.help_tool_press_key_desc),
                                        Pair(R.string.help_tool_swipe_name, R.string.help_tool_swipe_desc),
                                        Pair(R.string.help_tool_find_element_name, R.string.help_tool_find_element_desc)
                                )
                )

                ToolCategoryItem(
                        title = stringResource(R.string.help_screen_ffmpeg_tools_category_title),
                        items =
                                listOf(
                                        Pair(R.string.help_tool_ffmpeg_execute_name, R.string.help_tool_ffmpeg_execute_desc),
                                        Pair(R.string.help_tool_ffmpeg_info_name, R.string.help_tool_ffmpeg_info_desc),
                                        Pair(R.string.help_tool_ffmpeg_convert_name, R.string.help_tool_ffmpeg_convert_desc)
                                )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 扩展包部分
        var expandedPackages by remember { mutableStateOf(true) }
        ExpandableCard(
                title = stringResource(R.string.help_screen_extensions_title),
                icon = Icons.Default.Extension,
                expanded = expandedPackages,
                onExpandChange = { expandedPackages = it }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PackageItem(
                        name = stringResource(R.string.help_package_writer_name),
                        description = stringResource(R.string.help_package_writer_desc),
                        icon = Icons.Default.Edit
                )

                PackageItem(
                        name = stringResource(R.string.help_package_various_search_name),
                        description = stringResource(R.string.help_package_various_search_desc),
                        icon = Icons.Default.Search
                )

                PackageItem(
                        name = stringResource(R.string.help_package_daily_life_name),
                        description = stringResource(R.string.help_package_daily_life_desc),
                        icon = Icons.Default.DateRange
                )

                PackageItem(
                        name = stringResource(R.string.help_package_super_admin_name),
                        description = stringResource(R.string.help_package_super_admin_desc),
                        icon = Icons.Default.AdminPanelSettings
                )

                PackageItem(
                        name = stringResource(R.string.help_package_code_runner_name),
                        description = stringResource(R.string.help_package_code_runner_desc),
                        icon = Icons.Default.Code
                )

                PackageItem(name = stringResource(R.string.help_package_baidu_map_name), description = stringResource(R.string.help_package_baidu_map_desc), icon = Icons.Default.Map)

                PackageItem(
                        name = stringResource(R.string.help_package_qq_intelligent_name),
                        description = stringResource(R.string.help_package_qq_intelligent_desc),
                        icon = Icons.Default.Message
                )

                PackageItem(
                        name = stringResource(R.string.help_package_time_name),
                        description = stringResource(R.string.help_package_time_desc),
                        icon = Icons.Default.AccessTime
                )

                PackageItem(
                        name = stringResource(R.string.help_package_various_output_name),
                        description = stringResource(R.string.help_package_various_output_desc),
                        icon = Icons.Default.Image
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 使用指南
        var expandedGuide by remember { mutableStateOf(true) }
        ExpandableCard(
                title = stringResource(R.string.help_screen_usage_guide_title),
                icon = Icons.Default.Info,
                expanded = expandedGuide,
                onExpandChange = { expandedGuide = it }
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                GuideItem(
                        title = stringResource(R.string.help_guide_tool_invocation_title),
                        content = stringResource(R.string.help_guide_tool_invocation_content)
                )

                GuideItem(
                        title = stringResource(R.string.help_guide_extension_activation_title),
                        content = stringResource(R.string.help_guide_extension_activation_content)
                )

                GuideItem(title = stringResource(R.string.help_guide_permission_management_title), content = stringResource(R.string.help_guide_permission_management_content))

                GuideItem(title = stringResource(R.string.help_guide_planning_mode_title), content = stringResource(R.string.help_guide_planning_mode_content))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 联系信息
        Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
        ) {
            Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                        text = stringResource(R.string.help_screen_contact_us_prompt),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                )
                Text(
                        text = "aaswordsman@foxmail.com",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun ExpandableCard(
        title: String,
        icon: ImageVector,
        expanded: Boolean,
        onExpandChange: (Boolean) -> Unit,
        content: @Composable () -> Unit
) {
    Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                    modifier =
                            Modifier.fillMaxWidth().padding(bottom = if (expanded) 8.dp else 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = { onExpandChange(!expanded) }) {
                    Icon(
                            imageVector =
                                    if (expanded) Icons.Default.ExpandLess
                                    else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) stringResource(R.string.help_screen_collapse_cd) else stringResource(R.string.help_screen_expand_cd)
                    )
                }
            }
            if (expanded) {
                content()
            }
        }
    }
}

@Composable
fun ToolCategoryItem(title: String, items: List<Pair<Int, Int>>) { // Changed items type
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
                text = title, // This title is already a stringResource at call site
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp)
        )

        Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items.forEach { itemPair ->
                    // Display tool name and description using stringResource
                    Text(
                            text = "• ${stringResource(itemPair.first)}: ${stringResource(itemPair.second)}",
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
fun PackageItem(name: String, description: String, icon: ImageVector) { // name and description are already stringResource at call site
    Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(8.dp).size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                        text = name, // Already a stringResource
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                )

                Text(
                        text = description, // Already a stringResource
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun GuideItem(title: String, content: String) { // title and content are already stringResource at call site
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                    text = title, // Already a stringResource
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
            )
        }

        Text(
                text = content, // Already a stringResource
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 28.dp, top = 4.dp)
        )
    }
}
