package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.ui.common.markdown.StreamMarkdownRenderer
import com.ai.assistance.operit.ui.features.chat.components.part.CustomXmlRenderer
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.markdown.toCharStream

private const val TAG = "AiMessageComposable"

/**
 * A composable function for rendering AI response messages in a Cursor IDE style. Supports text
 * selection and copy on long press for different segments. Always uses collapsed execution mode for
 * tool output display.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AiMessageComposable(
        message: ChatMessage,
        backgroundColor: Color,
        textColor: Color,
        onLinkClick: ((String) -> Unit)? = null,
        overrideStream: Stream<String>? = null
) {
    // 创建自定义XML渲染器
    val xmlRenderer = remember { CustomXmlRenderer() }

    // 移除记录开始渲染时间和LaunchedEffect

    // 移除Card背景，使用直接的Column布局
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
                text = stringResource(com.ai.assistance.operit.R.string.response),
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
        )

        // 使用 message.timestamp 作为 key，确保在重组期间，
        // 只要是同一条消息，StreamMarkdownRenderer就不会被销毁和重建。
        // 这可以防止流被不必要地取消，保证了渲染的连续性。
        key(message.timestamp) {
            val streamToRender = overrideStream ?: message.contentStream
            if (streamToRender != null) {
                // 对于正在流式传输的消息，使用流式渲染器
                // 将contentStream保存到本地变量以避免智能转换问题
                val charStream = remember(streamToRender) { streamToRender.toCharStream() }

                StreamMarkdownRenderer(
                        markdownStream = charStream,
                        textColor = textColor,
                        backgroundColor = backgroundColor,
                        onLinkClick = onLinkClick,
                        xmlRenderer = xmlRenderer,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
            } else {
                // 对于已完成的静态消息，使用新的字符串渲染器以提高性能
                StreamMarkdownRenderer(
                        content = message.content,
                        textColor = textColor,
                        backgroundColor = backgroundColor,
                        onLinkClick = onLinkClick,
                        xmlRenderer = xmlRenderer,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
            }
        }
    }

    // 移除记录结束渲染时间的LaunchedEffect
}
