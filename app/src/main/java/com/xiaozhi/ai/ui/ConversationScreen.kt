package com.xiaozhi.ai.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.xiaozhi.ai.R
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import com.xiaozhi.ai.ui.theme.TechLightBlue80
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.xiaozhi.ai.data.ConfigManager
import com.xiaozhi.ai.data.Message
import com.xiaozhi.ai.data.MessageRole
import com.xiaozhi.ai.ui.theme.ConnectedGreen
import com.xiaozhi.ai.ui.theme.ConnectionRed
import com.xiaozhi.ai.ui.theme.DarkColorScheme
import com.xiaozhi.ai.utils.ConfigValidator
import com.xiaozhi.ai.viewmodel.ConversationState
import com.xiaozhi.ai.viewmodel.ConversationViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * 对话界面主屏幕
 *
 * 这是应用的主要界面，负责展示对话内容、处理用户输入和管理应用状态。
 * 使用Jetpack Compose构建现代化的响应式UI。
 *
 * @param viewModel 对话状态管理器，默认使用ViewModel工厂创建
 */
@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
@Preview
fun ConversationScreen(
    viewModel: ConversationViewModel = viewModel()
) {
    // 获取上下文和协程作用域
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configManager = remember { ConfigManager(context) }
    val configValidator = remember { ConfigValidator(context) }

    // 权限管理 - 请求录音和音频设置权限
    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            android.Manifest.permission.RECORD_AUDIO, // 录音权限
            android.Manifest.permission.MODIFY_AUDIO_SETTINGS // 音频设置权限
        )
    )

    // 状态收集 - 从ViewModel收集各种状态
    val state by viewModel.state.collectAsState() // 对话状态
    val isConnected by viewModel.isConnected.collectAsState() // 连接状态
    val messages by viewModel.messages.collectAsState() // 消息列表
    val errorMessage by viewModel.errorMessage.collectAsState() // 错误消息
    val showActivationDialog by viewModel.showActivationDialog.collectAsState() // 激活弹窗显示状态
    val activationCode by viewModel.activationCode.collectAsState() // 激活码
    val isMuted by viewModel.isMuted.collectAsState() // 静音状态

    // 文本输入状态和导航状态
    var textInput by remember { mutableStateOf("") } // 文本输入内容
    var showSettings by remember { mutableStateOf(false) } // 是否显示设置页面
    var currentConfig by remember {
        mutableStateOf(configManager.loadConfig()) // 当前配置
    }
    val listState = rememberLazyListState() // 消息列表状态

    // 自动滚动到底部 - 当消息数量变化时自动滚动到最新消息
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1) // 滚动到最后一项
        }
    }

    // 请求权限 - 在组件初始化时请求必要的权限
    LaunchedEffect(Unit) {
        if (!permissionsState.allPermissionsGranted) {
            permissionsState.launchMultiplePermissionRequest() // 请求录音权限
        }
    }

    // 显示设置页面或主界面 - 根据状态决定显示哪个界面
    if (showSettings) {
        // 显示设置页面
        SettingsScreen(
            config = currentConfig, // 当前配置
            onConfigChange = { newConfig ->
                currentConfig = newConfig // 更新当前配置
                configManager.saveConfig(newConfig) // 保存配置到本地
                // 更新ViewModel中的配置
                viewModel.updateConfig(newConfig)
                showSettings = false // 返回主界面
            },
            onBack = {
                showSettings = false // 返回主界面
            }
        )
    } else {
        // 显示主对话界面
        MainConversationContent(
            state = state, // 对话状态
            isConnected = isConnected, // 连接状态
            messages = messages, // 消息列表
            errorMessage = errorMessage, // 错误消息
            textInput = textInput, // 文本输入内容
            onTextInputChange = { textInput = it }, // 文本输入变化回调
            listState = listState, // 列表状态
            hasPermissions = permissionsState.allPermissionsGranted, // 权限状态
            onShowSettings = { showSettings = true }, // 显示设置页面回调
            showActivationDialog = showActivationDialog, // 激活弹窗显示状态
            activationCode = activationCode, // 激活码
            isMuted = isMuted, // 静音状态
            onToggleMute = { viewModel.toggleMute() }, // 切换静音回调
            viewModel = viewModel // ViewModel实例
        )
    }
}

/**
 * 主对话界面内容
 *
 * 负责显示对话界面的主要内容，包括顶部标题栏、消息列表、底部输入区域和各种状态指示器。
 *
 * @param state 当前对话状态
 * @param isConnected 是否已连接到服务器
 * @param messages 消息列表
 * @param errorMessage 错误消息
 * @param textInput 文本输入内容
 * @param onTextInputChange 文本输入变化回调
 * @param listState 消息列表状态
 * @param hasPermissions 是否具有必要权限
 * @param onShowSettings 显示设置页面回调
 * @param showActivationDialog 是否显示激活弹窗
 * @param activationCode 激活码
 * @param isMuted 是否静音
 * @param onToggleMute 切换静音回调
 * @param viewModel 对话状态管理器
 */
@Composable
fun MainConversationContent(
    state: ConversationState,
    isConnected: Boolean,
    messages: List<Message>,
    errorMessage: String?,
    textInput: String,
    onTextInputChange: (String) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    hasPermissions: Boolean,
    onShowSettings: () -> Unit,
    showActivationDialog: Boolean,
    activationCode: String?,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    viewModel: ConversationViewModel
) {
    // 主界面布局 - 使用Scaffold提供标准的Material Design布局结构
    Scaffold(
        modifier = Modifier
            .fillMaxSize() // 填充整个屏幕
            .navigationBarsPadding(), // 添加导航栏内边距
        containerColor = DarkColorScheme.background, // 背景颜色
        topBar = {
            // 沉浸式顶部标题栏
            Surface(
                modifier = Modifier.fillMaxWidth(), // 填充整个宽度
                color = DarkColorScheme.primary, // 主色调背景
                shadowElevation = 0.dp // 无阴影
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding() // 添加状态栏高度的padding
                        .padding(horizontal = 16.dp, vertical = 4.dp), // 减小垂直padding
                    horizontalArrangement = Arrangement.SpaceBetween, // 水平两端对齐
                    verticalAlignment = Alignment.CenterVertically // 垂直居中对齐
                ) {
                    // 左侧：标题和状态
                    Row(
                        verticalAlignment = Alignment.CenterVertically // 垂直居中对齐
                    ) {
                        // 应用标题
                        Text(
                            text = "小智AI",
                            fontSize = 24.sp, // 字体大小
                            fontWeight = FontWeight.Bold, // 粗体
                            color = DarkColorScheme.onPrimary // 文字颜色
                        )
                        Spacer(modifier = Modifier.width(4.dp)) // 间距
                        // 连接状态指示器 - 根据连接状态显示不同图标和颜色
                        Icon(
                            painter = painterResource(
                                id = if (isConnected) R.drawable.cloud_on else R.drawable.cloud_disabled
                            ),
                            contentDescription = if (isConnected) "已连接" else "未连接",
                            modifier = Modifier.size(16.dp), // 图标大小
                            tint = if (isConnected) ConnectedGreen else ConnectionRed // 图标颜色
                        )
                    }

                    // 右侧：功能按钮
                    Row {
                        // 静音按钮 - 根据静音状态显示不同图标
                        IconButton(onClick = onToggleMute) {
                            Icon(
                                painter = painterResource(
                                    id = if (isMuted) R.drawable.volume_off else R.drawable.volume_up
                                ),
                                modifier = Modifier.size(24.dp), // 图标大小
                                contentDescription = if (isMuted) "取消静音" else "静音",
                                tint = DarkColorScheme.onPrimary // 图标颜色
                            )
                        }

                        // 设置按钮
                        IconButton(onClick = onShowSettings) {
                            Icon(
                                painter = painterResource(id = R.drawable.settings), // 设置图标
                                contentDescription = "设置", // 内容描述
                                modifier = Modifier.size(24.dp), // 图标大小
                                tint = DarkColorScheme.onPrimary // 图标颜色
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            // 底部栏 - 处理键盘和导航栏内边距
            Column(
                modifier = Modifier
                    .imePadding() // 关键：处理键盘插入
                    .navigationBarsPadding() // 处理导航栏
            ) {
                // 简约的底部输入区域
                ModernBottomInputArea(
                    textInput = textInput, // 文本输入内容
                    onTextChange = onTextInputChange, // 文本变化回调
                    onSendText = {
                        // 发送文本消息
                        if (textInput.isNotBlank()) {
                            viewModel.sendTextMessage(textInput) // 发送消息到ViewModel
                            onTextInputChange("") // 清空输入框
                        }
                    },
                    state = state, // 当前对话状态
                    isConnected = isConnected, // 连接状态
                    hasPermissions = hasPermissions, // 权限状态
                    viewModel = viewModel // ViewModel实例
                )
            }
        }
    ) { paddingValues ->
        // 主内容区域 - 使用Column垂直排列
        Column(
            modifier = Modifier
                .fillMaxSize() // 填充剩余空间
                .padding(paddingValues) // 应用Scaffold提供的内边距
        ) {
            // 简洁的错误消息显示 - 当有错误消息时显示错误提示
            errorMessage?.let { error ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth() // 填充整个宽度
                        .padding(horizontal = 16.dp, vertical = 8.dp), // 内边距
                    color = TechLightBlue80, // 背景颜色
                    shape = RoundedCornerShape(8.dp) // 圆角形状
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp), // 内边距
                        verticalAlignment = Alignment.CenterVertically // 垂直居中对齐
                    ) {
                        // 警告图标
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = DarkColorScheme.primary, // 图标颜色
                            modifier = Modifier.size(16.dp) // 图标大小
                        )
                        Spacer(modifier = Modifier.width(8.dp)) // 间距
                        // 错误消息文本
                        Text(
                            text = error,
                            color = DarkColorScheme.primary, // 文字颜色
                            modifier = Modifier.weight(1f), // 占据剩余空间
                            fontSize = 14.sp // 字体大小
                        )
                        // 关闭按钮
                        IconButton(
                            onClick = { viewModel.clearError() }, // 清除错误消息
                            modifier = Modifier.size(24.dp) // 按钮大小
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "关闭", // 内容描述
                                tint = DarkColorScheme.error, // 图标颜色
                                modifier = Modifier.size(16.dp) // 图标大小
                            )
                        }
                    }
                }
            }

            // 消息列表 - 使用LazyColumn实现高效的消息列表
            LazyColumn(
                state = listState, // 列表状态
                modifier = Modifier
                    .weight(1f) // 占据剩余空间
                    .fillMaxWidth(), // 填充整个宽度
                contentPadding = PaddingValues(8.dp), // 内容内边距
                verticalArrangement = Arrangement.spacedBy(8.dp) // 垂直间距
            ) {
                // 为每条消息创建列表项
                items(messages) { message ->
                    MessageItem(message = message) // 显示消息项
                }
            }
        }
    }

    // 激活弹窗 - 当需要设备激活时显示激活码弹窗
    if (showActivationDialog && activationCode != null) {
        AlertDialog(
            onDismissRequest = { /* 不允许点击外部关闭 */ }, // 禁止点击外部关闭
            title = {
                // 弹窗标题
                Text(
                    text = "设备激活",
                    fontWeight = FontWeight.Bold, // 粗体
                    color = DarkColorScheme.onSurface // 文字颜色
                )
            },
            text = {
                // 弹窗内容
                Column {
                    Text(
                        text = "激活码：",
                        color = DarkColorScheme.onSurface, // 文字颜色
                        fontSize = 16.sp // 字体大小
                    )
                    Spacer(modifier = Modifier.height(8.dp)) // 间距
                    // 激活码显示区域
                    Surface(
                        modifier = Modifier.fillMaxWidth(), // 填充整个宽度
                        color = DarkColorScheme.surfaceVariant, // 背景颜色
                        shape = RoundedCornerShape(8.dp) // 圆角形状
                    ) {
                        Text(
                            text = activationCode, // 激活码
                            modifier = Modifier.padding(16.dp), // 内边距
                            color = DarkColorScheme.primary, // 文字颜色
                            fontSize = 18.sp, // 字体大小
                            fontWeight = FontWeight.Bold, // 粗体
                            textAlign = TextAlign.Center // 居中对齐
                        )
                    }
                }
            },
            confirmButton = {
                // 确认按钮
                Button(
                    onClick = { viewModel.onActivationConfirmed() }, // 确认激活回调
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DarkColorScheme.primary // 按钮背景色
                    )
                ) {
                    Text(
                        text = "我已激活", // 按钮文字
                        color = DarkColorScheme.onPrimary // 文字颜色
                    )
                }
            },
            containerColor = DarkColorScheme.surface, // 弹窗背景色
            titleContentColor = DarkColorScheme.onSurface, // 标题文字颜色
            textContentColor = DarkColorScheme.onSurface // 内容文字颜色
        )
    }
}

@Composable
fun MessageItem(message: Message) {
    val isUser = message.role == MessageRole.USER
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            // AI头像 - 更小更简洁
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(DarkColorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "AI",
                    color = DarkColorScheme.onPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 260.dp)
        ) {
            Surface(
                color = if (isUser)
                    DarkColorScheme.primary
                else
                    DarkColorScheme.surfaceVariant,
                shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = if (isUser) 18.dp else 6.dp,
                    bottomEnd = if (isUser) 6.dp else 18.dp
                ),
                shadowElevation = 2.dp
            ) {
                Text(
                    text = message.content,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    color = if (isUser)
                        DarkColorScheme.onPrimary
                    else
                        DarkColorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }

            Text(
                text = timeFormat.format(Date(message.timestamp)),
                fontSize = 10.sp,
                color = DarkColorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(
                    start = if (isUser) 0.dp else 8.dp,
                    end = if (isUser) 8.dp else 0.dp,
                    top = 4.dp
                )
            )
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            // 用户头像 - 更小更简洁
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(DarkColorScheme.secondary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = DarkColorScheme.onSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun ModernBottomInputArea(
    textInput: String,
    onTextChange: (String) -> Unit,
    onSendText: () -> Unit,
    state: ConversationState,
    isConnected: Boolean,
    hasPermissions: Boolean,
    viewModel: ConversationViewModel
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = DarkColorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 状态指示器 - 更简洁
            AnimatedVisibility(
                visible = state != ConversationState.IDLE,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (state) {
                        ConversationState.LISTENING -> {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        DarkColorScheme.error,
                                        CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "正在聆听",
                                color = DarkColorScheme.error,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        ConversationState.PROCESSING -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = DarkColorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "处理中",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        ConversationState.SPEAKING -> {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        DarkColorScheme.primary,
                                        CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "小智回复中",
                                color = DarkColorScheme.primary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        else -> {}
                    }
                }
            }

            // 输入区域 - 新的语音输入组件和圆形按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 语音输入组件
                VoiceInputField(
                    textInput = textInput,
                    onTextChange = onTextChange,
                    onSendText = onSendText,
                    isConnected = isConnected,
                    hasPermissions = hasPermissions,
                    viewModel = viewModel,
                    modifier = Modifier.weight(1f)
                )
                
                // 右侧圆形按钮
                IconButton(
                    onClick = {
                        // 这里可以添加按钮点击事件
                        // 例如：打开更多选项、发送表情等
                    },
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            color = TechLightBlue80,
                            shape = CircleShape
                        )
                        .clip(CircleShape)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.call),
                        contentDescription = "打电话",
                        modifier = Modifier.size(32.dp),
                        tint = Color.White
                    )
                }
            }

            // 权限提示
            if (!hasPermissions) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "需要录音权限才能使用语音功能",
                    color = DarkColorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun VoiceInputField(
    textInput: String,
    onTextChange: (String) -> Unit,
    onSendText: () -> Unit,
    isConnected: Boolean,
    hasPermissions: Boolean,
    viewModel: ConversationViewModel,
    modifier: Modifier = Modifier
) {
    var isInputMode by remember { mutableStateOf(false) }
    var isPressed by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var longPressJob by remember { mutableStateOf<Job?>(null) }
    val focusRequester = remember { FocusRequester() }

    // 新增：长按录音提示与取消提示状态
    var showRecordingHint by remember { mutableStateOf(false) }
    var showCancelHint by remember { mutableStateOf(false) }

    // 监听键盘状态
    val keyboardController = LocalSoftwareKeyboardController.current
    val isKeyboardOpen by rememberUpdatedState(WindowInsets.ime.getBottom(LocalDensity.current) > 0)

    // 当键盘隐藏时，自动切换回按钮模式
    LaunchedEffect(isKeyboardOpen) {
        if (!isKeyboardOpen && isInputMode) {
            isInputMode = false
        }
    }

    if (isInputMode) {
        // 输入框模式
        OutlinedTextField(
            value = textInput,
            onValueChange = onTextChange,
            modifier = modifier
                .fillMaxWidth()
                .onKeyEvent { keyEvent ->
                    if (keyEvent.key == Key.Enter && textInput.isNotBlank() && isConnected) {
                        onSendText()
                        true
                    } else {
                        false
                    }
                }
                .focusRequester(focusRequester),
            placeholder = {
                Text(
                    text = if (!isConnected) "未连接到服务器" else "输入消息",
                    color = if (!isConnected) DarkColorScheme.error else DarkColorScheme.onSurfaceVariant
                )
            },
            maxLines = 4,
            enabled = isConnected,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TechLightBlue80,
                unfocusedBorderColor = DarkColorScheme.outline
            ),
            shape = RoundedCornerShape(24.dp),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Send
            ),
            keyboardActions = KeyboardActions(
                onSend = {
                    if (textInput.isNotBlank() && isConnected) {
                        onSendText()
                    }
                }
            )
        )

        // 自动聚焦并显示键盘
        LaunchedEffect(isInputMode) {
            if (isInputMode) {
                focusRequester.requestFocus()
                keyboardController?.show()
            }
        }
    } else {
        // 新增：外层使用 Column，在按钮上方显示提示
        val density = LocalDensity.current
        val cancelThresholdPx = with(density) { 80.dp.toPx() } // 上滑阈值

        Column(modifier = modifier.fillMaxWidth()) {
            // 录音提示气泡
            AnimatedVisibility(visible = showRecordingHint) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            color = if (showCancelHint) ConnectionRed.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.7f)
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (showCancelHint) "松开取消" else "松开发送，上滑取消",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 按钮模式
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(
                        color = when {
                            showCancelHint -> ConnectionRed
                            isPressed && hasPermissions -> ConnectedGreen
                            isConnected -> TechLightBlue80
                            else -> DarkColorScheme.surfaceVariant
                        },
                        shape = RoundedCornerShape(24.dp)
                    )
                    .pointerInput(hasPermissions, isConnected) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            if (!hasPermissions || !isConnected) {
                                // 无权限/未连接：短按进入输入模式
                                isPressed = true
                                val released = down.pressed
                                // 等待抬起
                                do {
                                  val e = awaitPointerEvent()
                                  val ch = e.changes.firstOrNull { it.id == down.id }
                                } while (ch != null && ch.pressed)
                                isPressed = false
                                isInputMode = true
                                return@awaitEachGesture
                            }

                            // 有权限且已连接：支持长按录音与上滑取消
                            isPressed = true
                            showCancelHint = false
                            var longPressed = false
                            var canceledBySwipeUp = false
                            val startY = down.position.y

                            longPressJob?.cancel()
                            longPressJob = coroutineScope.launch {
                                delay(500)
                                if (isPressed) {
                                  longPressed = true
                                  showRecordingHint = true
                                  // 开始录音
                                  viewModel.startListening()
                                }
                            }

                            // 监听移动/抬起
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: continue

                                // 长按后检查上滑取消
                                if (longPressed) {
                                  val dy = change.position.y - startY
                                  val shouldCancel = dy < -cancelThresholdPx
                                  if (shouldCancel && !canceledBySwipeUp) {
                                    canceledBySwipeUp = true
                                    showCancelHint = true
                                    // 立即取消录音与传输
                                    try {
                                      viewModel.cancelListeningWithAbort("wake_word_detected")
                                    } catch (t: Throwable) {
                                      // 忽略UI层异常，确保不崩溃
                                    }
                                  } else if (!shouldCancel && canceledBySwipeUp) {
                                    // 回到阈值内，取消提示
                                    showCancelHint = false
                                  }
                                }

                                if (change.changedToUp()) {
                                  // 结束本次手势
                                  isPressed = false
                                  longPressJob?.cancel()

                                  if (longPressed) {
                                    // 已经进入录音
                                    if (!canceledBySwipeUp) {
                                      // 正常松开 -> 结束录音并发送
                                      if (viewModel.state.value == ConversationState.LISTENING) {
                                        viewModel.stopListening()
                                      }
                                    }
                                  } else {
                                    // 短按 -> 切换输入模式
                                    isInputMode = true
                                  }

                                  // 收尾UI
                                  showRecordingHint = false
                                  showCancelHint = false
                                  break
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when {
                            !isConnected -> "未连接到服务器"
                            isPressed && hasPermissions -> "录音中..."
                            else -> "输入消息或长按说话"
                        },
                        color = Color.White
                    )
                }
            }
        }
    }
}
