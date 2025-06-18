package com.ai.assistance.operit.ui.features.toolbox.screens.ffmpegtoolbox

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.FFmpegResultData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.model.ToolResult
import kotlinx.coroutines.launch

/**
 * FFmpeg工具箱主屏幕 - 直接提供自定义命令功能
 * 简化后的界面，移除了所有导航功能，以避免导航问题
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FFmpegToolboxScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val aiToolHandler = AIToolHandler.getInstance(context)

    // 状态
    var ffmpegCommand by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var commandResult by remember { mutableStateOf<ToolResult?>(null) }
    var showCommandTemplates by remember { mutableStateOf(false) }

    // 命令模板
    val commandTemplates = listOf(
        CommandTemplate(
            name = stringResource(R.string.ffmpeg_template_video_to_mp4_name),
            description = stringResource(R.string.ffmpeg_template_video_to_mp4_desc),
            command = "-i input.mp4 -c:v libx264 -c:a aac output.mp4"
        ),
        CommandTemplate(
            name = stringResource(R.string.ffmpeg_template_video_compress_name),
            description = stringResource(R.string.ffmpeg_template_video_compress_desc),
            command = "-i input.mp4 -vf scale=1280:-1 -c:v libx264 -crf 23 -preset medium -c:a aac -b:a 128k output.mp4"
        ),
        CommandTemplate(
            name = stringResource(R.string.ffmpeg_template_video_trim_name),
            description = stringResource(R.string.ffmpeg_template_video_trim_desc),
            command = "-i input.mp4 -ss 00:00:30 -t 00:00:10 -c:v copy -c:a copy output.mp4"
        ),
        CommandTemplate(
            name = stringResource(R.string.ffmpeg_template_extract_audio_name),
            description = stringResource(R.string.ffmpeg_template_extract_audio_desc),
            command = "-i input.mp4 -vn -acodec copy output.aac"
        ),
        CommandTemplate(
            name = stringResource(R.string.ffmpeg_template_gif_creation_name),
            description = stringResource(R.string.ffmpeg_template_gif_creation_desc),
            command = "-i input.mp4 -vf \"fps=10,scale=320:-1:flags=lanczos\" -c:v gif output.gif"
        )
    )

    Column(
        modifier = Modifier.fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 命令输入区域
        Column {
            Text(
                text = stringResource(R.string.ffmpeg_title_command),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            OutlinedTextField(
                value = ffmpegCommand,
                onValueChange = { ffmpegCommand = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                placeholder = { Text(stringResource(R.string.ffmpeg_placeholder_command)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                maxLines = 5
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { showCommandTemplates = !showCommandTemplates },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) { Text(stringResource(R.string.ffmpeg_button_templates)) }

                Button(
                    onClick = {
                        if (ffmpegCommand.isEmpty()) {
                            Toast.makeText(context, context.getString(R.string.ffmpeg_toast_enter_command), Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        isProcessing = true
                        commandResult = null

                        scope.launch {
                            val tool = AITool(
                                name = "ffmpeg_execute",
                                parameters = listOf(
                                    ToolParameter("command", ffmpegCommand)
                                )
                            )

                            try {
                                val result = aiToolHandler.executeTool(tool)
                                commandResult = result
                            } catch (e: Exception) {
                                commandResult = ToolResult(
                                    toolName = "ffmpeg_execute",
                                    success = false,
                                    result = com.ai.assistance.operit.core.tools.StringResultData(""),
                                    error = stringResource(R.string.ffmpeg_error_execution_failed, e.message ?: "")
                                )
                            } finally {
                                isProcessing = false
                            }
                        }
                    },
                    enabled = !isProcessing && ffmpegCommand.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) { Text(if (isProcessing) stringResource(R.string.ffmpeg_button_executing) else stringResource(R.string.ffmpeg_button_execute)) }
            }
        }

        // 命令模板区域
        if (showCommandTemplates) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.ffmpeg_title_common_templates),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    commandTemplates.forEach { template ->
                        TemplateItem(
                            template = template,
                            onSelect = {
                                ffmpegCommand = template.command
                                showCommandTemplates = false
                            }
                        )
                    }

                    Text(
                        text = stringResource(R.string.ffmpeg_warning_template_paths),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // FFmpeg信息区域
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.ffmpeg_title_help),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = stringResource(R.string.ffmpeg_help_intro),
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = stringResource(R.string.ffmpeg_help_common_params),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )

                Text(
                    text = stringResource(R.string.ffmpeg_help_params_list),
                    style = MaterialTheme.typography.bodySmall
                )

                Button(
                    onClick = {
                        scope.launch {
                            val tool = AITool(name = "ffmpeg_info", parameters = listOf())
                            try {
                                val result = aiToolHandler.executeTool(tool)
                                commandResult = result
                            } catch (e: Exception) {
                                commandResult = ToolResult(
                                    toolName = "ffmpeg_info",
                                    success = false,
                                    result = com.ai.assistance.operit.core.tools.StringResultData(""),
                                    error = stringResource(R.string.ffmpeg_error_info_failed, e.message ?: "")
                                )
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) { Text(stringResource(R.string.ffmpeg_button_more_info)) }
            }
        }

        // 结果显示
        if (isProcessing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            Text(
                text = stringResource(R.string.ffmpeg_progress_executing),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        commandResult?.let { result ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (result.success)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    else
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = if (result.success) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (result.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(28.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = if (result.success) stringResource(R.string.ffmpeg_result_success) else stringResource(R.string.ffmpeg_result_failed),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (result.success)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onErrorContainer
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 显示错误信息或执行结果
                    if (!result.success && result.error != null) {
                        Text(
                            text = result.error!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    } else if (result.result is FFmpegResultData) {
                        val ffmpegResult = result.result as FFmpegResultData

                        Text(
                            text = stringResource(R.string.ffmpeg_result_command, ffmpegResult.command),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )

                        Text(
                            text = stringResource(R.string.ffmpeg_result_return_code, ffmpegResult.returnCode.toString()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )

                        Text(
                            text = stringResource(R.string.ffmpeg_result_duration, ffmpegResult.duration),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )

                        if (ffmpegResult.output.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = stringResource(R.string.ffmpeg_result_output_label),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )

                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = ffmpegResult.output,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TemplateItem(template: CommandTemplate, onSelect: () -> Unit) {
    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = template.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = template.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = template.command,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    maxLines = 1
                )
            }

            IconButton(onClick = onSelect) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = stringResource(R.string.ffmpeg_icon_use_template),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

data class CommandTemplate(val name: String, val description: String, val command: String)
