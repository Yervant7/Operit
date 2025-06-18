package com.ai.assistance.operit.ui.floating

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.data.model.AttachmentInfo
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.ui.features.chat.attachments.AttachmentManager
import com.ai.assistance.operit.ui.features.chat.components.AttachmentChip
import com.ai.assistance.operit.ui.features.chat.components.CursorStyleChatMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// 定义边缘类型
enum class ResizeEdge {
    NONE,
    LEFT,
    RIGHT,
    TOP,
    BOTTOM,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT
}

// 自定义线性插值函数
fun lerp(start: Float, end: Float, fraction: Float): Float {
    return start + (end - start) * fraction.coerceIn(0f, 1f)
}

/**
 * 悬浮聊天窗口的主要UI组件
 *
 * @param messages 要显示的聊天消息列表
 * @param width 窗口宽度
 * @param height 窗口高度
 * @param onClose 关闭窗口的回调
 * @param onResize 调整窗口大小的回调
 * @param isBallMode 是否为球模式
 * @param ballSize 球的大小
 * @param initialWindowScale 初始窗口缩放比例
 * @param onToggleBallMode 切换球模式的回调
 * @param onMove 悬浮窗移动的回调，传递相对移动距离和当前缩放比例
 * @param snapToEdge 靠边收起的回调
 * @param isAtEdge 是否处于屏幕边缘
 * @param screenWidth 屏幕宽度参数，用于边界检测
 * @param screenHeight 屏幕高度参数，用于边界检测
 * @param currentX 当前窗口X坐标
 * @param currentY 当前窗口Y坐标
 * @param saveWindowState 保存窗口状态的回调
 * @param onSendMessage 发送消息的回调
 * @param onAttachmentRequest 附件请求回调
 * @param attachments 当前附件列表
 * @param onRemoveAttachment 删除附件回调
 * @param onInputFocusRequest 请求输入焦点的回调，参数为true时请求获取焦点，false时释放焦点
 */
@Composable
fun FloatingChatWindow(
        messages: List<ChatMessage>,
        width: Dp,
        height: Dp,
        onClose: () -> Unit,
        onResize: (Dp, Dp) -> Unit,
        isBallMode: Boolean = false,
        ballSize: Dp = 48.dp,
        initialWindowScale: Float = 1.0f,
        onToggleBallMode: () -> Unit = {},
        onMove: (Float, Float, Float) -> Unit = { _, _, _ -> },
        snapToEdge: (Boolean) -> Unit = { _ -> },
        isAtEdge: Boolean = false,
        screenWidth: Dp = 1080.dp,
        screenHeight: Dp = 2340.dp,
        currentX: Float = 0f,
        currentY: Float = 0f,
        saveWindowState: (() -> Unit)? = null,
        onSendMessage: ((String) -> Unit)? = null,
        onAttachmentRequest: ((String) -> Unit)? = null,
        attachments: List<AttachmentInfo> = emptyList(),
        onRemoveAttachment: ((String) -> Unit)? = null,
        onInputFocusRequest: ((Boolean) -> Unit)? = null
) {
    val updatedMessages = remember(messages) { messages }
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // 主题和颜色
    val backgroundColor = MaterialTheme.colorScheme.background
    val userMessageColor = MaterialTheme.colorScheme.primaryContainer
    val aiMessageColor = MaterialTheme.colorScheme.surface
    val userTextColor = MaterialTheme.colorScheme.onPrimaryContainer
    val aiTextColor = MaterialTheme.colorScheme.onSurface
    val systemMessageColor = MaterialTheme.colorScheme.surfaceVariant
    val systemTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val thinkingBackgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    val thinkingTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    // 淡入淡出动画
    val animatedAlpha = remember { Animatable(1f) }

    // 模式过渡动画状态
    val isInTransition = remember { mutableStateOf(false) }

    // 模式切换动画
    val ballToWindowTransition = remember { Animatable(if (isBallMode) 0f else 1f) }

    // 窗口形状圆角动画 - 添加一个硬切换标志
    val cornerRadius = remember { Animatable(if (isBallMode) 100f else 12f) }
    // 添加一个标志用来决定是否显示当前的圆角过渡动画
    val useDirectCorners = remember { mutableStateOf(false) }

    // 窗口缩放状态 - 初始值来自服务，限制最大为1.0
    var windowScale by remember { mutableFloatStateOf(initialWindowScale.coerceIn(0.5f, 1.0f)) }

    // 当初始缩放值更新时应用它，限制最大缩放为1.0
    LaunchedEffect(initialWindowScale) { windowScale = initialWindowScale.coerceIn(0.5f, 1.0f) }

    // 窗口尺寸状态（基于传入的width和height）- 确保尺寸有效
    var windowWidthState by remember { mutableStateOf(width.coerceAtLeast(200.dp)) }
    var windowHeightState by remember { mutableStateOf(height.coerceAtLeast(250.dp)) }

    // 边缘检测相关的状态
    var activeEdge by remember { mutableStateOf(ResizeEdge.NONE) }
    var isEdgeResizing by remember { mutableStateOf(false) }
    val edgeHighlightColor = MaterialTheme.colorScheme.primary
    val borderThickness = 3.dp // 高亮边框厚度

    // 记录拖动开始时的尺寸，用于计算调整
    var initialWindowWidth by remember { mutableStateOf(0f) }
    var initialWindowHeight by remember { mutableStateOf(0f) }

    // 球模式动画
    val pulseAnimation = rememberInfiniteTransition(label = "BallPulse")
    val pulseScale by
            pulseAnimation.animateFloat(
                    initialValue = 1.0f,
                    targetValue = 1.04f,
                    animationSpec =
                            infiniteRepeatable(
                                    animation = tween(2000, easing = EaseInOutQuad),
                                    repeatMode = RepeatMode.Reverse
                            ),
                    label = "PulseScale"
            )

    // 当模式变化时更新缩放和动画
    LaunchedEffect(isBallMode) {
        // 设置转换状态开始
        isInTransition.value = true

        if (isBallMode) {
            // 向球模式过渡 - 保持现有逻辑，球模式需要圆角
            useDirectCorners.value = false
            coroutineScope.launch {
                // 同时处理圆角和大小变化，无需先淡出再淡入
                animatedAlpha.animateTo(
                        0.9f,
                        animationSpec = tween(150, easing = FastOutSlowInEasing)
                )

                // 并行执行这些动画以加快过渡
                coroutineScope.launch {
                    cornerRadius.animateTo(
                            100f,
                            animationSpec = tween(150, easing = FastOutSlowInEasing)
                    )
                }

                coroutineScope.launch {
                    ballToWindowTransition.animateTo(
                            0f,
                            animationSpec = tween(150, easing = FastOutSlowInEasing)
                    )
                }

                // 快速恢复透明度
                animatedAlpha.animateTo(1f, animationSpec = tween(100))
            }

            // 切换到球模式时，保存当前缩放的90%
            val targetScale = (windowScale * 0.9f).coerceAtLeast(0.5f)
            windowScale = targetScale
        } else {
            // 从球模式切换到窗口模式 - 使用直接切换，避免圆角过渡
            useDirectCorners.value = true

            // 立即设置窗口圆角 - 在动画开始前
            cornerRadius.snapTo(12f)

            coroutineScope.launch {
                // 轻微的淡出效果
                animatedAlpha.animateTo(
                        0.85f,
                        animationSpec = tween(80, easing = FastOutSlowInEasing)
                )

                // 只做大小过渡动画，直接从球扩展到窗口，无需圆角过渡
                ballToWindowTransition.animateTo(
                        1f,
                        animationSpec = tween(160, easing = FastOutSlowInEasing)
                )

                // 快速恢复透明度
                animatedAlpha.animateTo(1f, animationSpec = tween(60))
            }

            // 从球模式切换回窗口模式时确保窗口可见
            if (windowScale < 0.6f) {
                windowScale = 0.6f
            }
            snapToEdge(false)
        }

        // 短暂延迟后完成转换
        delay(200)
        isInTransition.value = false
        // 转换完成后重置直接切换标志
        useDirectCorners.value = false
    }

    // 处理拖动事件的函数
    val handleDrag = { dx: Float, dy: Float -> onMove(dx, dy, windowScale) }

    // 模式切换处理函数 - 添加防抖动
    val lastToggleTime = remember { mutableStateOf(0L) }
    val handleModeToggle = {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastToggleTime.value > 500 && !isInTransition.value) {
            lastToggleTime.value = currentTime
            onToggleBallMode()
        }
    }

    // 计算动画过渡中的尺寸
    val transitionBallSize = with(density) { ballSize.toPx() }
    val transitionWindowWidth = with(density) { windowWidthState.toPx() }
    val transitionWindowHeight = with(density) { windowHeightState.toPx() }

    // 计算过渡中的尺寸
    val currentWidth = lerp(transitionBallSize, transitionWindowWidth, ballToWindowTransition.value)
    val currentHeight =
            lerp(transitionBallSize, transitionWindowHeight, ballToWindowTransition.value)
    // 修改圆角计算，从球模式到窗口模式时直接使用窗口圆角
    val currentCornerRadius =
            if (useDirectCorners.value && !isBallMode) {
                with(density) { 12f.dp }
            } else {
                with(density) { cornerRadius.value.dp }
            }

    // 添加过渡期间的视觉反馈
    val transitionFeedback = remember { Animatable(0f) }

    // 当状态变化时提供视觉反馈
    LaunchedEffect(isInTransition.value) {
        if (isInTransition.value) {
            // 启动一个短暂的波纹动画 - 减少持续时间
            transitionFeedback.snapTo(0f)
            transitionFeedback.animateTo(
                    1f,
                    animationSpec = tween(200, easing = FastOutSlowInEasing)
            )
        } else {
            transitionFeedback.snapTo(0f)
        }
    }

    // 添加淡入淡出内容动画
    val contentVisible = !isInTransition.value

    // Add state for the input dialog
    var showInputDialog by remember { mutableStateOf(false) }
    var userMessage by remember { mutableStateOf("") }

    // 添加附件选择器相关状态
    var showAttachmentPanel by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val attachmentManager = remember {
        AttachmentManager(context, AIToolHandler.getInstance(context))
    }

    // 监听显示状态变化并通知焦点变化
    LaunchedEffect(showInputDialog) {
        // 通知服务需要切换焦点模式
        onInputFocusRequest?.invoke(showInputDialog)

        // 如果隐藏输入框，清空消息
        if (!showInputDialog) {
            userMessage = ""
        }
    }

    if (isBallMode) {
        // 球模式
        val ballHoverState = remember { mutableStateOf(false) }
        val touchAnimationState = remember { mutableStateOf(false) }
        val touchAnimationValue = remember { Animatable(0f) }
        // Add balloon mode button visibility state
        val showBallButtons by remember { mutableStateOf(true) }
        // Add input button pressed state
        val inputButtonPressed = remember { mutableStateOf(false) }
        // Add click state
        val ballTapState = remember { mutableStateOf(false) }

        // Pre-extract theme colors for Canvas usage
        val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
        val primaryColor = MaterialTheme.colorScheme.primary

        // Handle input button press
        LaunchedEffect(inputButtonPressed.value) {
            if (inputButtonPressed.value) {
                // 修改为先切换到窗口模式，再显示输入框
                onToggleBallMode()
                // 等待模式切换完成后再显示输入框
                delay(500) // 等待模式切换动画完成
                showInputDialog = true
                inputButtonPressed.value = false
            }
        }

        // Handle click events
        LaunchedEffect(ballTapState.value) {
            if (ballTapState.value) {
                if (isAtEdge) {
                    snapToEdge(false)
                } else {
                    handleModeToggle()
                }
                // Reset state
                ballTapState.value = false
            }
        }

        // 触摸动画效果
        LaunchedEffect(touchAnimationState.value) {
            if (touchAnimationState.value) {
                touchAnimationValue.snapTo(0f)
                touchAnimationValue.animateTo(1f, animationSpec = tween(500, easing = EaseOutQuart))
                touchAnimationState.value = false
            }
        }

        // 当用户悬停或触摸时的效果
        val ballInteractionModifier =
                Modifier.pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            // 检测是否有触摸点在球上
                            ballHoverState.value = event.changes.any { it.pressed }

                            if (event.type == PointerEventType.Press) {
                                touchAnimationState.value = true
                            }
                        }
                    }
                }

        Box(
                modifier =
                        Modifier.size(ballSize)
                                .shadow(
                                        elevation = if (ballHoverState.value) 6.dp else 4.dp,
                                        shape = CircleShape
                                )
                                .clip(CircleShape)
                                .background(
                                        if (ballHoverState.value) primaryColor.copy(alpha = 0.9f)
                                        else primaryColor
                                )
                                .alpha(if (isAtEdge) 0.8f else 1f)
                                .then(ballInteractionModifier)
                                .graphicsLayer {
                                    // 确保球的缩放不会太小，最小保持0.8倍大小
                                    val effectiveScale = maxOf(windowScale, 0.8f)
                                    val hoverEffect = if (ballHoverState.value) 1.05f else 1f
                                    scaleX =
                                            effectiveScale *
                                                    (if (isAtEdge) 0.7f else 1f) *
                                                    (if (!isAtEdge) pulseScale else 1f) *
                                                    hoverEffect
                                    scaleY =
                                            effectiveScale *
                                                    (if (isAtEdge) 0.7f else 1f) *
                                                    (if (!isAtEdge) pulseScale else 1f) *
                                                    hoverEffect
                                    alpha = animatedAlpha.value
                                }
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                            onDragStart = { offset ->
                                                // 检查点击是否在圆内
                                                val radius = size.width / 2f
                                                val center = Offset(radius, radius)
                                                val distance = (offset - center).getDistance()

                                                // 只有当点击在圆内才处理事件
                                                if (distance <= radius) {
                                                    if (isAtEdge) snapToEdge(false)
                                                }
                                            },
                                            onDragEnd = {
                                                saveWindowState?.invoke()

                                                // 靠边检测
                                                val edgeThresholdPx = with(density) { 20.dp.toPx() }
                                                if (currentX < edgeThresholdPx ||
                                                                currentX >
                                                                        screenWidth.toPx() -
                                                                                edgeThresholdPx
                                                ) {
                                                    snapToEdge(true)
                                                }
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                handleDrag(dragAmount.x, dragAmount.y)
                                            }
                                    )
                                }
        ) {
            // 过渡时的波纹效果
            if (transitionFeedback.value > 0) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                            color =
                                    onPrimaryColor.copy(
                                            alpha = 0.15f * (1f - transitionFeedback.value)
                                    ),
                            radius = size.minDimension * 0.6f * transitionFeedback.value,
                            center = Offset(size.width / 2, size.height / 2)
                    )
                }
            }

            // 触摸反馈波纹
            if (touchAnimationValue.value > 0) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                            color =
                                    onPrimaryColor.copy(
                                            alpha = 0.2f * (1f - touchAnimationValue.value)
                                    ),
                            radius = size.minDimension * 0.7f * touchAnimationValue.value,
                            center = Offset(size.width / 2, size.height / 2)
                    )
                }
            }

            Box(
                    modifier =
                            Modifier.fillMaxSize().padding(4.dp).pointerInput(Unit) {
                                detectTapGestures(
                                        onTap = { offset ->
                                            // 检查点击是否在圆内
                                            val radius = size.width / 2f
                                            val center = Offset(radius, radius)
                                            val distance = (offset - center).getDistance()

                                            // 只有当点击在圆内才处理事件
                                            if (distance <= radius) {
                                                touchAnimationState.value = true
                                                ballTapState.value = true
                                            }
                                        },
                                        onLongPress = { offset ->
                                            // 检查长按是否在圆内
                                            val radius = size.width / 2f
                                            val center = Offset(radius, radius)
                                            val distance = (offset - center).getDistance()

                                            // 只有当长按在圆内才处理事件
                                            if (distance <= radius) {
                                                // 长按重置位置到屏幕中心（安全恢复功能）
                                                val centerX = screenWidth.value / 2
                                                val centerY = screenHeight.value / 2
                                                onMove(
                                                        centerX - currentX,
                                                        centerY - currentY,
                                                        windowScale
                                                )

                                                // 保存新位置
                                                saveWindowState?.invoke()
                                            }
                                        }
                                )
                            }
            ) {
                Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = stringResource(R.string.floating_window_ball_chat_desc),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp).align(Alignment.Center)
                )
            }
        }
    } else {
        // 窗口模式
        // Pre-extract theme colors for Canvas usage
        val primaryColor = MaterialTheme.colorScheme.primary
        val errorColor = MaterialTheme.colorScheme.error
        val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant

        Box(modifier = Modifier.width(windowWidthState).height(windowHeightState)) {
            // 创建一个呈现边缘视觉反馈的覆盖层
            Box(
                    modifier =
                            Modifier.fillMaxSize().graphicsLayer {
                                scaleX = windowScale
                                scaleY = windowScale
                                transformOrigin = TransformOrigin(0f, 0f)
                                alpha = animatedAlpha.value
                            }
            ) {
                // 主要内容区域，含边框
                Box(
                        modifier =
                                Modifier.fillMaxSize()
                                        .shadow(8.dp, RoundedCornerShape(currentCornerRadius))
                                        .border(
                                                width = borderThickness,
                                                color =
                                                        if (isEdgeResizing) edgeHighlightColor
                                                        else Color.Transparent,
                                                shape = RoundedCornerShape(currentCornerRadius)
                                        )
                                        .clip(RoundedCornerShape(currentCornerRadius))
                                        .background(backgroundColor)
                                        .onSizeChanged { size ->
                                            // 更新实际窗口大小（像素值）
                                            initialWindowWidth = size.width.toFloat()
                                            initialWindowHeight = size.height.toFloat()
                                        }
                ) {
                    // 过渡时的波纹效果
                    if (transitionFeedback.value > 0) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val centerX = size.width / 2
                            val centerY = size.height / 2
                            val radius = size.minDimension * 0.6f * transitionFeedback.value

                            drawCircle(
                                    color =
                                            primaryColor.copy(
                                                    alpha = 0.1f * (1f - transitionFeedback.value)
                                            ),
                                    radius = radius,
                                    center = Offset(centerX, centerY)
                            )
                        }
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        // 窗口模式下的顶部工具栏
                        val titleBarHover = remember { mutableStateOf(false) }
                        // 添加按钮事件状态
                        val closeButtonPressed = remember { mutableStateOf(false) }
                        val minimizeButtonPressed = remember { mutableStateOf(false) }

                        // 处理关闭按钮副作用
                        LaunchedEffect(closeButtonPressed.value) {
                            if (closeButtonPressed.value) {
                                animatedAlpha.animateTo(0f, animationSpec = tween(200))
                                onClose()
                                closeButtonPressed.value = false // 重置状态
                            }
                        }

                        // 处理最小化按钮副作用
                        LaunchedEffect(minimizeButtonPressed.value) {
                            if (minimizeButtonPressed.value) {
                                onToggleBallMode()
                                minimizeButtonPressed.value = false // 重置状态
                            }
                        }

                        Box(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .height(48.dp)
                                                .background(
                                                        MaterialTheme.colorScheme.surfaceVariant
                                                                .copy(
                                                                        alpha =
                                                                                if (titleBarHover
                                                                                                .value
                                                                                )
                                                                                        0.3f
                                                                                else 0.2f
                                                                )
                                                )
                                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                                .pointerInput(Unit) {
                                                    awaitPointerEventScope {
                                                        while (true) {
                                                            val event = awaitPointerEvent()
                                                            titleBarHover.value =
                                                                    event.changes.any { it.pressed }
                                                        }
                                                    }
                                                }
                                                .pointerInput(Unit) {
                                                    detectDragGestures(
                                                            onDragEnd = {
                                                                saveWindowState?.invoke()
                                                            },
                                                            onDrag = { change, dragAmount ->
                                                                change.consume()
                                                                handleDrag(
                                                                        dragAmount.x,
                                                                        dragAmount.y
                                                                )
                                                            }
                                                    )
                                                }
                        ) {
                            // 显示内容（使用条件渲染而不是ContentWithFade）
                            if (contentVisible) {
                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                            text = stringResource(R.string.floating_window_title),
                                            style =
                                                    MaterialTheme.typography.titleMedium.copy(
                                                            fontWeight = FontWeight.Medium
                                                    ),
                                            color = MaterialTheme.colorScheme.primary
                                    )

                                    Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // 最小化按钮
                                        val minimizeHover = remember { mutableStateOf(false) }

                                        IconButton(
                                                onClick = { minimizeButtonPressed.value = true },
                                                modifier =
                                                        Modifier.size(30.dp)
                                                                .background(
                                                                        color =
                                                                                if (minimizeHover
                                                                                                .value
                                                                                )
                                                                                        primaryColor
                                                                                                .copy(
                                                                                                        alpha =
                                                                                                                0.1f
                                                                                                )
                                                                                else
                                                                                        Color.Transparent,
                                                                        shape = CircleShape
                                                                )
                                                                .pointerInput(Unit) {
                                                                    awaitPointerEventScope {
                                                                        while (true) {
                                                                            val event =
                                                                                    awaitPointerEvent()
                                                                            minimizeHover.value =
                                                                                    event.changes
                                                                                            .any {
                                                                                                it.pressed
                                                                                            }
                                                                        }
                                                                    }
                                                                }
                                        ) {
                                            Icon(
                                                    imageVector = Icons.Default.KeyboardArrowDown,
                                                    contentDescription = stringResource(R.string.floating_window_minimize_desc),
                                                    tint =
                                                            MaterialTheme.colorScheme
                                                                    .onSurfaceVariant,
                                                    modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        // 关闭按钮
                                        val closeHover = remember { mutableStateOf(false) }

                                        IconButton(
                                                onClick = { closeButtonPressed.value = true },
                                                modifier =
                                                        Modifier.size(30.dp)
                                                                .background(
                                                                        color =
                                                                                if (closeHover.value
                                                                                )
                                                                                        errorColor
                                                                                                .copy(
                                                                                                        alpha =
                                                                                                                0.1f
                                                                                                )
                                                                                else
                                                                                        Color.Transparent,
                                                                        shape = CircleShape
                                                                )
                                                                .pointerInput(Unit) {
                                                                    awaitPointerEventScope {
                                                                        while (true) {
                                                                            val event =
                                                                                    awaitPointerEvent()
                                                                            closeHover.value =
                                                                                    event.changes
                                                                                            .any {
                                                                                                it.pressed
                                                                                            }
                                                                        }
                                                                    }
                                                                }
                                        ) {
                                            Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = stringResource(R.string.close),
                                                    tint =
                                                            if (closeHover.value) errorColor
                                                            else onSurfaceVariantColor,
                                                    modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 聊天内容区域
                        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                            // 条件性显示聊天内容或输入框
                            if (!showInputDialog) {
                                val scrollState = rememberScrollState()

                                // Auto-scroll to bottom
                                LaunchedEffect(messages.size) {
                                    if (messages.isNotEmpty()) {
                                        scrollState.animateScrollTo(scrollState.maxValue)
                                    }
                                }

                                val isLoading = messages.lastOrNull()?.sender == "think"

                                Column(
                                        modifier =
                                                Modifier.fillMaxSize()
                                                        .verticalScroll(scrollState)
                                                        .padding(
                                                                horizontal = 16.dp,
                                                                vertical = 16.dp
                                                        )
                                ) {
                                    messages.forEachIndexed { index, message ->
                                        if (message.sender != "think") {
                                            key(message.timestamp) {
                                                MessageItem(
                                                        index = index,
                                                        message = message,
                                                        allMessages = messages,
                                                        userMessageColor = userMessageColor,
                                                        aiMessageColor = aiMessageColor,
                                                        userTextColor = userTextColor,
                                                        aiTextColor = aiTextColor,
                                                        systemMessageColor = systemMessageColor,
                                                        systemTextColor = systemTextColor,
                                                        thinkingBackgroundColor =
                                                                thinkingBackgroundColor,
                                                        thinkingTextColor = thinkingTextColor,
                                                        isEditMode = false, // 悬浮窗不支持编辑模式
                                                        onSelectMessageToEdit = null,
                                                        onCopyMessage = null
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                    }

                                    val lastMessage = messages.lastOrNull { it.sender != "think" }
                                    if (isLoading &&
                                                    (lastMessage?.sender == "user" ||
                                                            (lastMessage?.sender == "ai" &&
                                                                    lastMessage.content.isBlank()))
                                    ) {
                                        Column(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .padding(vertical = 0.dp)
                                        ) {
                                            Box(modifier = Modifier.padding(start = 16.dp)) {
                                                LoadingDotsIndicator(aiTextColor)
                                            }
                                        }
                                    }
                                }
                            } else {
                                // 集成的输入区域
                                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                                    Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                                text = stringResource(R.string.floating_window_send_message_title),
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                        )

                                        IconButton(
                                                onClick = {
                                                    showInputDialog = false
                                                    showAttachmentPanel = false // 确保关闭附件选择面板
                                                }
                                        ) {
                                            Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = stringResource(R.string.close),
                                                    tint =
                                                            MaterialTheme.colorScheme
                                                                    .onSurfaceVariant
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // 显示已添加的附件
                                    if (attachments.isNotEmpty()) {
                                        LazyRow(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .padding(vertical = 4.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            items(attachments) { attachment ->
                                                AttachmentChip(
                                                        attachmentInfo = attachment,
                                                        onRemove = {
                                                            onRemoveAttachment?.invoke(
                                                                    attachment.filePath
                                                            )
                                                        },
                                                        onInsert = { /* 在悬浮窗中不支持插入操作 */}
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }

                                    val focusRequester = remember { FocusRequester() }
                                    val keyboardController = LocalSoftwareKeyboardController.current
                                    val focusManager = LocalFocusManager.current

                                    // 添加一个当输入区域显示时的DisposableEffect
                                    DisposableEffect(showInputDialog) {
                                        if (showInputDialog) {
                                            coroutineScope.launch {
                                                // 增加延迟，确保视图完全渲染
                                                delay(300)

                                                // 请求焦点 - 但不主动显示键盘，让服务层处理键盘显示
                                                try {
                                                    focusRequester.requestFocus()
                                                    // 移除直接显示键盘的代码，避免重复显示
                                                } catch (e: Exception) {
                                                    Log.e(
                                                            "FloatingChatWindow",
                                                            "Failed to request focus",
                                                            e
                                                    )
                                                }
                                            }
                                        }

                                        // 清理
                                        onDispose {
                                            // 不主动隐藏键盘，让服务层处理
                                        }
                                    }

                                    // 改成Box包装输入框，使其填充剩余空间
                                    Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                                        // 输入框 - 占满可用空间
                                        OutlinedTextField(
                                                value = userMessage,
                                                onValueChange = { userMessage = it },
                                                placeholder = { Text(stringResource(R.string.chat_input_placeholder)) },
                                                modifier =
                                                        Modifier.fillMaxSize()
                                                                .focusRequester(focusRequester),
                                                textStyle = TextStyle.Default,
                                                maxLines = Int.MAX_VALUE, // 允许多行
                                                keyboardOptions =
                                                        KeyboardOptions(
                                                                imeAction = ImeAction.Send,
                                                                autoCorrect = true
                                                        ),
                                                keyboardActions =
                                                        KeyboardActions(
                                                                onSend = {
                                                                    if (userMessage.isNotBlank() ||
                                                                                    attachments
                                                                                            .isNotEmpty()
                                                                    ) {
                                                                        onSendMessage?.invoke(
                                                                                userMessage
                                                                        )
                                                                        userMessage = ""
                                                                        showInputDialog = false
                                                                        showAttachmentPanel = false
                                                                    }
                                                                }
                                                        ),
                                                colors =
                                                        OutlinedTextFieldDefaults.colors(
                                                                focusedBorderColor =
                                                                        MaterialTheme.colorScheme
                                                                                .primary,
                                                                unfocusedBorderColor =
                                                                        MaterialTheme.colorScheme
                                                                                .outline
                                                        ),
                                                shape = RoundedCornerShape(12.dp)
                                        )

                                        // 发送按钮 - 放在右下角
                                        FloatingActionButton(
                                                onClick = {
                                                    if (userMessage.isNotBlank() ||
                                                                    attachments.isNotEmpty()
                                                    ) {
                                                        onSendMessage?.invoke(userMessage)
                                                        userMessage = ""
                                                        showInputDialog = false
                                                        showAttachmentPanel = false
                                                    }
                                                },
                                                modifier =
                                                        Modifier.align(Alignment.BottomEnd)
                                                                .padding(8.dp)
                                                                .size(46.dp),
                                                containerColor = MaterialTheme.colorScheme.primary
                                        ) {
                                            Icon(
                                                    imageVector = Icons.Default.Send,
                                                    contentDescription = stringResource(R.string.send),
                                                    tint = MaterialTheme.colorScheme.onPrimary
                                            )
                                        }
                                    }
                                }
                            }

                            // 缩放控制手柄 - 只在不显示输入框且处于窗口模式时显示
                            if (!showInputDialog && !isBallMode) {
                                // 添加缩放按钮悬停状态
                                val scaleButtonHover = remember { mutableStateOf(false) }

                                Box(
                                        modifier =
                                                Modifier.size(48.dp)
                                                        .padding(6.dp)
                                                        .align(Alignment.BottomEnd)
                                                        .offset(x = (-8).dp, y = (-8).dp)
                                                        // 移除阴影和背景
                                                        // 添加点击涟漪效果的背景
                                                        .background(
                                                                color =
                                                                        if (scaleButtonHover.value)
                                                                                primaryColor.copy(
                                                                                        alpha = 0.1f
                                                                                )
                                                                        else Color.Transparent,
                                                                shape = CircleShape
                                                        )
                                                        // 移除边框
                                                        .pointerInput(Unit) {
                                                            awaitPointerEventScope {
                                                                while (true) {
                                                                    val event = awaitPointerEvent()
                                                                    scaleButtonHover.value =
                                                                            event.changes.any {
                                                                                it.pressed
                                                                            }
                                                                }
                                                            }
                                                        }
                                                        .pointerInput(Unit) {
                                                            detectDragGestures(
                                                                    onDrag = { change, dragAmount ->
                                                                        change.consume()
                                                                        val scaleDelta =
                                                                                dragAmount.y *
                                                                                        0.001f
                                                                        windowScale =
                                                                                (windowScale +
                                                                                                scaleDelta)
                                                                                        .coerceIn(
                                                                                                0.5f,
                                                                                                1.0f
                                                                                        )
                                                                        saveWindowState?.invoke()
                                                                    }
                                                            )
                                                        }
                                                        .pointerInput(Unit) {
                                                            detectTapGestures {
                                                                windowScale =
                                                                        when {
                                                                            windowScale > 0.8f ->
                                                                                    0.7f
                                                                            windowScale > 0.7f ->
                                                                                    0.9f
                                                                            else -> 1.0f
                                                                        }
                                                                saveWindowState?.invoke()
                                                            }
                                                        }
                                ) {
                                    val lineColor =
                                            if (scaleButtonHover.value)
                                                    primaryColor.copy(alpha = 1.0f)
                                            else primaryColor.copy(alpha = 0.7f)

                                    Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                                        // 绘制缩放图标 -
                                        // 增加粗细使其在透明背景下更加明显
                                        drawLine(
                                                color = lineColor,
                                                start =
                                                        Offset(
                                                                size.width * 0.2f,
                                                                size.height * 0.8f
                                                        ),
                                                end = Offset(size.width * 0.8f, size.height * 0.2f),
                                                strokeWidth = 3.5f // 略微增加线条粗细
                                        )
                                        drawLine(
                                                color = lineColor,
                                                start =
                                                        Offset(
                                                                size.width * 0.5f,
                                                                size.height * 0.8f
                                                        ),
                                                end = Offset(size.width * 0.8f, size.height * 0.5f),
                                                strokeWidth = 3.5f // 略微增加线条粗细
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 顶部边框 - 调整高度
                Box(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .height(20.dp)
                                        .align(Alignment.TopCenter)
                                        .pointerInput(Unit) {
                                            detectDragGestures(
                                                    onDragStart = {
                                                        isEdgeResizing = true
                                                        activeEdge = ResizeEdge.TOP
                                                        initialWindowHeight =
                                                                windowHeightState.value *
                                                                        density.density
                                                    },
                                                    onDragEnd = {
                                                        isEdgeResizing = false
                                                        saveWindowState?.invoke()
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        val heightChange = -dragAmount.y
                                                        val newHeight =
                                                                (initialWindowHeight + heightChange)
                                                                        .coerceAtLeast(200f)
                                                        windowHeightState =
                                                                with(density) {
                                                                    (newHeight / density.density).dp
                                                                            .coerceAtLeast(200.dp)
                                                                }
                                                        handleDrag(0f, dragAmount.y)
                                                        onResize(
                                                                windowWidthState,
                                                                windowHeightState
                                                        )
                                                    }
                                            )
                                        }
                )

                // 底部边框 - 调整高度
                Box(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .height(20.dp)
                                        .align(Alignment.BottomCenter)
                                        .pointerInput(Unit) {
                                            detectDragGestures(
                                                    onDragStart = {
                                                        isEdgeResizing = true
                                                        activeEdge = ResizeEdge.BOTTOM
                                                        initialWindowHeight =
                                                                windowHeightState.value *
                                                                        density.density
                                                    },
                                                    onDragEnd = {
                                                        isEdgeResizing = false
                                                        saveWindowState?.invoke()
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        val newHeight =
                                                                (initialWindowHeight + dragAmount.y)
                                                                        .coerceAtLeast(200f)
                                                        windowHeightState =
                                                                with(density) {
                                                                    (newHeight / density.density).dp
                                                                            .coerceAtLeast(200.dp)
                                                                }
                                                        onResize(
                                                                windowWidthState,
                                                                windowHeightState
                                                        )
                                                    }
                                            )
                                        }
                )

                // 左侧边框 - 调整宽度
                Box(
                        modifier =
                                Modifier.fillMaxHeight()
                                        .width(20.dp)
                                        .align(Alignment.CenterStart)
                                        .pointerInput(Unit) {
                                            detectDragGestures(
                                                    onDragStart = {
                                                        isEdgeResizing = true
                                                        activeEdge = ResizeEdge.LEFT
                                                        initialWindowWidth =
                                                                windowWidthState.value *
                                                                        density.density
                                                    },
                                                    onDragEnd = {
                                                        isEdgeResizing = false
                                                        saveWindowState?.invoke()
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        val widthChange = -dragAmount.x
                                                        val newWidth =
                                                                (initialWindowWidth + widthChange)
                                                                        .coerceAtLeast(150f)
                                                        windowWidthState =
                                                                with(density) {
                                                                    (newWidth / density.density).dp
                                                                            .coerceAtLeast(150.dp)
                                                                }
                                                        handleDrag(dragAmount.x, 0f)
                                                        onResize(
                                                                windowWidthState,
                                                                windowHeightState
                                                        )
                                                    }
                                            )
                                        }
                )

                // 右侧边框 - 调整宽度
                Box(
                        modifier =
                                Modifier.fillMaxHeight()
                                        .width(20.dp)
                                        .align(Alignment.CenterEnd)
                                        .pointerInput(Unit) {
                                            detectDragGestures(
                                                    onDragStart = {
                                                        isEdgeResizing = true
                                                        activeEdge = ResizeEdge.RIGHT
                                                        initialWindowWidth =
                                                                windowWidthState.value *
                                                                        density.density
                                                    },
                                                    onDragEnd = {
                                                        isEdgeResizing = false
                                                        saveWindowState?.invoke()
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        val newWidth =
                                                                (initialWindowWidth + dragAmount.x)
                                                                        .coerceAtLeast(150f)
                                                        windowWidthState =
                                                                with(density) {
                                                                    (newWidth / density.density).dp
                                                                            .coerceAtLeast(150.dp)
                                                                }
                                                        onResize(
                                                                windowWidthState,
                                                                windowHeightState
                                                        )
                                                    }
                                            )
                                        }
                )

                // 左上角 - 调整宽度和高度
                Box(
                        modifier =
                                Modifier.size(25.dp).align(Alignment.TopStart).pointerInput(Unit) {
                                    detectDragGestures(
                                            onDragStart = {
                                                isEdgeResizing = true
                                                activeEdge = ResizeEdge.TOP_LEFT
                                                initialWindowWidth =
                                                        windowWidthState.value * density.density
                                                initialWindowHeight =
                                                        windowHeightState.value * density.density
                                            },
                                            onDragEnd = {
                                                isEdgeResizing = false
                                                saveWindowState?.invoke()
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                val widthChange = -dragAmount.x
                                                val heightChange = -dragAmount.y
                                                val newWidth =
                                                        (initialWindowWidth + widthChange)
                                                                .coerceAtLeast(150f)
                                                val newHeight =
                                                        (initialWindowHeight + heightChange)
                                                                .coerceAtLeast(200f)
                                                windowWidthState =
                                                        with(density) {
                                                            (newWidth / density.density).dp
                                                                    .coerceAtLeast(150.dp)
                                                        }
                                                windowHeightState =
                                                        with(density) {
                                                            (newHeight / density.density).dp
                                                                    .coerceAtLeast(200.dp)
                                                        }
                                                handleDrag(dragAmount.x, dragAmount.y)
                                                onResize(windowWidthState, windowHeightState)
                                            }
                                    )
                                }
                )

                // 右上角 - 调整宽度和高度
                Box(
                        modifier =
                                Modifier.size(25.dp).align(Alignment.TopEnd).pointerInput(Unit) {
                                    detectDragGestures(
                                            onDragStart = {
                                                isEdgeResizing = true
                                                activeEdge = ResizeEdge.TOP_RIGHT
                                                initialWindowWidth =
                                                        windowWidthState.value * density.density
                                                initialWindowHeight =
                                                        windowHeightState.value * density.density
                                            },
                                            onDragEnd = {
                                                isEdgeResizing = false
                                                saveWindowState?.invoke()
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                val newWidth =
                                                        (initialWindowWidth + dragAmount.x)
                                                                .coerceAtLeast(150f)
                                                val heightChange = -dragAmount.y
                                                val newHeight =
                                                        (initialWindowHeight + heightChange)
                                                                .coerceAtLeast(200f)
                                                windowWidthState =
                                                        with(density) {
                                                            (newWidth / density.density).dp
                                                                    .coerceAtLeast(150.dp)
                                                        }
                                                windowHeightState =
                                                        with(density) {
                                                            (newHeight / density.density).dp
                                                                    .coerceAtLeast(200.dp)
                                                        }
                                                handleDrag(0f, dragAmount.y)
                                                onResize(windowWidthState, windowHeightState)
                                            }
                                    )
                                }
                )

                // 左下角 - 调整宽度和高度
                Box(
                        modifier =
                                Modifier.size(25.dp).align(Alignment.BottomStart).pointerInput(
                                                Unit
                                        ) {
                                    detectDragGestures(
                                            onDragStart = {
                                                isEdgeResizing = true
                                                activeEdge = ResizeEdge.BOTTOM_LEFT
                                                initialWindowWidth =
                                                        windowWidthState.value * density.density
                                                initialWindowHeight =
                                                        windowHeightState.value * density.density
                                            },
                                            onDragEnd = {
                                                isEdgeResizing = false
                                                saveWindowState?.invoke()
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                val widthChange = -dragAmount.x
                                                val newWidth =
                                                        (initialWindowWidth + widthChange)
                                                                .coerceAtLeast(150f)
                                                val newHeight =
                                                        (initialWindowHeight + dragAmount.y)
                                                                .coerceAtLeast(200f)
                                                windowWidthState =
                                                        with(density) {
                                                            (newWidth / density.density).dp
                                                                    .coerceAtLeast(150.dp)
                                                        }
                                                windowHeightState =
                                                        with(density) {
                                                            (newHeight / density.density).dp
                                                                    .coerceAtLeast(200.dp)
                                                        }
                                                handleDrag(dragAmount.x, 0f)
                                                onResize(windowWidthState, windowHeightState)
                                            }
                                    )
                                }
                )

                // 右下角 - 调整宽度和高度
                Box(
                        modifier =
                                Modifier.size(25.dp).align(Alignment.BottomEnd).pointerInput(Unit) {
                                    detectDragGestures(
                                            onDragStart = {
                                                isEdgeResizing = true
                                                activeEdge = ResizeEdge.BOTTOM_RIGHT
                                                initialWindowWidth =
                                                        windowWidthState.value * density.density
                                                initialWindowHeight =
                                                        windowHeightState.value * density.density
                                            },
                                            onDragEnd = {
                                                isEdgeResizing = false
                                                saveWindowState?.invoke()
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                val newWidth =
                                                        (initialWindowWidth + dragAmount.x)
                                                                .coerceAtLeast(150f)
                                                val newHeight =
                                                        (initialWindowHeight + dragAmount.y)
                                                                .coerceAtLeast(200f)
                                                windowWidthState =
                                                        with(density) {
                                                            (newWidth / density.density).dp
                                                                    .coerceAtLeast(150.dp)
                                                        }
                                                windowHeightState =
                                                        with(density) {
                                                            (newHeight / density.density).dp
                                                                    .coerceAtLeast(200.dp)
                                                        }
                                                onResize(windowWidthState, windowHeightState)
                                            }
                                    )
                                }
                )

                // 显示边缘缩放指示器 - 当鼠标悬停或触摸边缘时
                if (isEdgeResizing) {
                    // 根据活动边缘显示相应的视觉指示器
                    when (activeEdge) {
                        ResizeEdge.LEFT, ResizeEdge.RIGHT -> {
                            // 水平调整指示器
                            Box(
                                    modifier =
                                            Modifier.fillMaxHeight()
                                                    .width(4.dp)
                                                    .background(
                                                            color = edgeHighlightColor,
                                                            shape = RoundedCornerShape(2.dp)
                                                    )
                                                    .align(
                                                            when (activeEdge) {
                                                                ResizeEdge.LEFT ->
                                                                        Alignment.CenterStart
                                                                else -> Alignment.CenterEnd
                                                            }
                                                    )
                            )
                        }
                        ResizeEdge.TOP, ResizeEdge.BOTTOM -> {
                            // 垂直调整指示器
                            Box(
                                    modifier =
                                            Modifier.fillMaxWidth()
                                                    .height(4.dp)
                                                    .background(
                                                            color = edgeHighlightColor,
                                                            shape = RoundedCornerShape(2.dp)
                                                    )
                                                    .align(
                                                            when (activeEdge) {
                                                                ResizeEdge.TOP ->
                                                                        Alignment.TopCenter
                                                                else -> Alignment.BottomCenter
                                                            }
                                                    )
                            )
                        }
                        ResizeEdge.TOP_LEFT,
                        ResizeEdge.TOP_RIGHT,
                        ResizeEdge.BOTTOM_LEFT,
                        ResizeEdge.BOTTOM_RIGHT -> {
                            // 角落调整指示器
                            Box(
                                    modifier =
                                            Modifier.size(8.dp)
                                                    .background(
                                                            color = edgeHighlightColor,
                                                            shape = CircleShape
                                                    )
                                                    .align(
                                                            when (activeEdge) {
                                                                ResizeEdge.TOP_LEFT ->
                                                                        Alignment.TopStart
                                                                ResizeEdge.TOP_RIGHT ->
                                                                        Alignment.TopEnd
                                                                ResizeEdge.BOTTOM_LEFT ->
                                                                        Alignment.BottomStart
                                                                else -> Alignment.BottomEnd
                                                            }
                                                    )
                            )
                        }
                        else -> {
                            /* 不显示任何指示器 */
                        }
                    }
                }
            }
        }

        // 输入按钮 - 只有在不显示输入框时才显示
        if (!showInputDialog && onSendMessage != null) {
            Box(
                    modifier =
                            Modifier.width(windowWidthState) // 使用窗口宽度而不是fillMaxSize，确保按钮在窗口内
                                    .height(windowHeightState) // 使用窗口高度
                                    .graphicsLayer { // 应用与窗口相同的缩放
                                        scaleX = windowScale
                                        scaleY = windowScale
                                        transformOrigin = TransformOrigin(0f, 0f)
                                    }
            ) {
                // 添加附件按钮 - 放在编辑按钮上方
                SmallFloatingActionButton(
                        onClick = {
                            // 修改为切换附件面板显示/隐藏
                            showAttachmentPanel = !showAttachmentPanel
                        },
                        modifier =
                                Modifier.align(Alignment.BottomStart)
                                        .padding(start = 12.dp, bottom = 60.dp) // 减小间距，使按钮更靠近
                                        .size(34.dp), // 减小按钮尺寸
                        containerColor =
                                if (showAttachmentPanel)
                                        MaterialTheme.colorScheme.primary.copy(
                                                alpha = 0.8f
                                        ) // 增加透明度
                                else
                                        MaterialTheme.colorScheme.secondaryContainer.copy(
                                                alpha = 0.75f
                                        ), // 增加透明度
                        contentColor =
                                if (showAttachmentPanel) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = if (showAttachmentPanel) stringResource(R.string.floating_window_toggle_attachments_close) else stringResource(R.string.floating_window_toggle_attachments_open),
                            modifier = Modifier.size(16.dp) // 减小图标尺寸
                    )
                }

                // 编辑消息按钮 - 保持原有功能
                SmallFloatingActionButton(
                        onClick = {
                            if (isBallMode) {
                                // 如果是球模式，先切换到窗口模式再显示输入框
                                onToggleBallMode()
                                // 等待模式切换完成后再显示输入框
                                coroutineScope.launch {
                                    delay(500) // 等待模式切换动画完成
                                    showInputDialog = true
                                }
                            } else {
                                showInputDialog = true
                            }
                        },
                        modifier =
                                Modifier.align(Alignment.BottomStart)
                                        .padding(12.dp) // 减小内边距
                                        .size(34.dp), // 减小按钮尺寸
                        containerColor =
                                MaterialTheme.colorScheme.primary.copy(
                                        alpha = 0.75f // 增加透明度
                                ),
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(R.string.floating_window_send_message_fab),
                            modifier = Modifier.size(16.dp) // 减小图标尺寸
                    )
                }
            }
        }

        // 附件面板 - 在点击附件按钮时显示
        if (showAttachmentPanel && !showInputDialog) {
            Box(
                    modifier =
                            Modifier.width(windowWidthState)
                                    .height(windowHeightState)
                                    .graphicsLayer {
                                        scaleX = windowScale
                                        scaleY = windowScale
                                        transformOrigin = TransformOrigin(0f, 0f)
                                    }
                                    // 修改pointerInput逻辑以检测附件按钮区域
                                    .pointerInput(Unit) {
                                        detectTapGestures { offset ->
                                            // 计算附件面板的大概位置（底部区域）
                                            val panelHeight = 220.dp.toPx() // 附件面板的大致高度
                                            val screenHeight = size.height

                                            // 计算附件按钮的区域（左下角+按钮位置）- 使用与实际按钮一致的值
                                            val buttonSize = 34.dp.toPx() // 使用实际按钮尺寸34dp
                                            val buttonPaddingStart = 12.dp.toPx() // 使用实际的开始内边距12dp
                                            val buttonPaddingBottom = 60.dp.toPx() // 使用实际的底部内边距60dp
                                            val buttonLeft = buttonPaddingStart
                                            val buttonTop =
                                                    screenHeight - buttonPaddingBottom - buttonSize
                                            val buttonRight = buttonLeft + buttonSize
                                            val buttonBottom = buttonTop + buttonSize

                                            // 增加一点点点击区域，使按钮更容易点到
                                            val expandedClickArea = 6.dp.toPx()
                                            val isButtonClicked =
                                                    offset.x >= (buttonLeft - expandedClickArea) &&
                                                            offset.x <=
                                                                    (buttonRight +
                                                                            expandedClickArea) &&
                                                            offset.y >=
                                                                    (buttonTop -
                                                                            expandedClickArea) &&
                                                            offset.y <=
                                                                    (buttonBottom +
                                                                            expandedClickArea)

                                            if (isButtonClicked) {
                                                // 点击了附件按钮，切换面板显示状态
                                                showAttachmentPanel = !showAttachmentPanel
                                            }
                                            // 检查点击是否在面板外部区域
                                            else if (offset.y < screenHeight - panelHeight) {
                                                // 点击面板外部区域，关闭面板
                                                showAttachmentPanel = false
                                            }
                                            // 点击在面板内部区域，不做处理
                                        }
                                    }
            ) {
                // 使用自定义的悬浮窗专用附件面板
                FloatingAttachmentPanel(
                        visible = showAttachmentPanel,
                        onAttachScreenContent = {
                            coroutineScope.launch {
                                // 屏幕内容附件 - 在service层处理
                                onAttachmentRequest?.invoke("screen_capture")
                                // 允许附件面板关闭，但稍后再刷新附件列表
                                delay(500) // 给Service一点时间处理附件
                                // 保持附件面板关闭状态，但内容已更新
                                showAttachmentPanel = false
                            }
                        },
                        onAttachNotifications = {
                            coroutineScope.launch {
                                // 通知附件 - 在service层处理
                                onAttachmentRequest?.invoke("notifications_capture")
                                // 允许附件面板关闭，但稍后再刷新附件列表
                                delay(500) // 给Service一点时间处理附件
                                // 保持附件面板关闭状态，但内容已更新
                                showAttachmentPanel = false
                            }
                        },
                        onAttachLocation = {
                            coroutineScope.launch {
                                // 位置附件 - 在service层处理
                                onAttachmentRequest?.invoke("location_capture")
                                // 允许附件面板关闭，但稍后再刷新附件列表
                                delay(500) // 给Service一点时间处理附件
                                // 保持附件面板关闭状态，但内容已更新
                                showAttachmentPanel = false
                            }
                        },
                        onAttachProblemMemory = {
                            coroutineScope.launch {
                                // 问题记忆附件 - 在service层处理
                                onAttachmentRequest?.invoke("problem_memory")
                                // 允许附件面板关闭，但稍后再刷新附件列表
                                delay(500) // 给Service一点时间处理附件
                                // 保持附件面板关闭状态，但内容已更新
                                showAttachmentPanel = false
                            }
                        },
                        onDismiss = { showAttachmentPanel = false }
                )
            }
        }
    }
}

/** 单个消息项组件 将消息渲染逻辑提取到单独的组件，减少重组范围 */
@Composable
private fun MessageItem(
        index: Int,
        message: ChatMessage,
        allMessages: List<ChatMessage>,
        userMessageColor: Color,
        aiMessageColor: Color,
        userTextColor: Color,
        aiTextColor: Color,
        systemMessageColor: Color,
        systemTextColor: Color,
        thinkingBackgroundColor: Color,
        thinkingTextColor: Color,
        isEditMode: Boolean,
        onSelectMessageToEdit: ((Int, ChatMessage) -> Unit)?,
        onCopyMessage: ((ChatMessage) -> Unit)?
) {
    // 添加Context以访问剪贴板服务
    val context = LocalContext.current

    // 编辑模式下为消息添加点击功能
    val messageModifier =
            if (isEditMode && message.sender != "think") {
                Modifier.clickable { onSelectMessageToEdit?.invoke(index, message) }
            } else {
                Modifier
            }

    Box(modifier = messageModifier) {
        // 检查这是否是最后一条AI消息
        val isLastAiMessage =
                message.sender == "ai" && index == allMessages.indexOfLast { it.sender == "ai" }
        val streamToRender = if (isLastAiMessage) message.contentStream else null

        CursorStyleChatMessage(
                message = message,
                userMessageColor = userMessageColor,
                aiMessageColor = aiMessageColor,
                userTextColor = userTextColor,
                aiTextColor = aiTextColor,
                systemMessageColor = systemMessageColor,
                systemTextColor = systemTextColor,
                thinkingBackgroundColor = thinkingBackgroundColor,
                thinkingTextColor = thinkingTextColor,
                supportToolMarkup = true,
                initialThinkingExpanded = true,
                overrideStream = streamToRender
        )

        // 在编辑模式下显示编辑指示器
        if (isEditMode && (message.sender == "user" || message.sender == "ai")) {
            // 复制按钮
            Box(
                    modifier =
                            Modifier.align(Alignment.TopEnd)
                                    .padding(
                                            end = 46.dp,
                                            top = if (message.sender == "user") 6.dp else 0.dp
                                    )
                                    .size(32.dp)
                                    .clickable(
                                            interactionSource =
                                                    remember { MutableInteractionSource() },
                                            indication =
                                                    rememberRipple(
                                                            bounded = false,
                                                            radius = 20.dp,
                                                            color =
                                                                    MaterialTheme.colorScheme
                                                                            .primary
                                                    )
                                    ) {
                                        // 实现复制功能
                                        val clipboardManager =
                                                context.getSystemService(
                                                        Context.CLIPBOARD_SERVICE
                                                ) as
                                                        ClipboardManager
                                        val clipData =
                                                ClipData.newPlainText("聊天消息", message.content)
                                        clipboardManager.setPrimaryClip(clipData)

                                        // 显示复制成功的提示
                                        Toast.makeText(context, context.getString(R.string.message_item_copied_toast), Toast.LENGTH_SHORT)
                                                .show()

                                        // 如果有外部回调，也调用它
                                        onCopyMessage?.invoke(message)
                                    }
            ) {
                Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.message_item_copy_desc),
                        modifier = Modifier.align(Alignment.Center).size(16.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
            }

            // 编辑按钮
            Box(
                    modifier =
                            Modifier.align(Alignment.TopEnd)
                                    .padding(
                                            end = 20.dp,
                                            top = if (message.sender == "user") 6.dp else 0.dp
                                    )
                                    .size(32.dp)
                                    .clickable(
                                            interactionSource =
                                                    remember { MutableInteractionSource() },
                                            indication =
                                                    rememberRipple(
                                                            bounded = false,
                                                            radius = 20.dp,
                                                            color =
                                                                    MaterialTheme.colorScheme
                                                                            .primary
                                                    )
                                    ) { onSelectMessageToEdit?.invoke(index, message) }
            ) {
                Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.message_item_edit_desc),
                        modifier = Modifier.align(Alignment.Center).size(16.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun LoadingDotsIndicator(textColor: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")

    Row(
            modifier = Modifier.padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        val jumpHeight = -5f
        val animationDelay = 160

        (0..2).forEach { index ->
            val offsetY by
                    infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = jumpHeight,
                            animationSpec =
                                    infiniteRepeatable(
                                            animation =
                                                    keyframes {
                                                        durationMillis = 600
                                                        0f at 0
                                                        jumpHeight * 0.4f at 100
                                                        jumpHeight * 0.8f at 200
                                                        jumpHeight at 300
                                                        jumpHeight * 0.8f at 400
                                                        jumpHeight * 0.4f at 500
                                                        0f at 600
                                                    },
                                            repeatMode = RepeatMode.Restart,
                                            initialStartOffset = StartOffset(index * animationDelay)
                                    ),
                            label = "offsetY_$index"
                    )

            Box(
                    modifier =
                            Modifier.size(6.dp)
                                    .offset(y = offsetY.dp)
                                    .background(
                                            color = textColor.copy(alpha = 0.6f),
                                            shape = CircleShape
                                    )
            )
        }
    }
}
