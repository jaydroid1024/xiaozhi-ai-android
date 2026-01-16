package com.xiaozhi.ai.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.xiaozhi.ai.audio.AudioEvent
import com.xiaozhi.ai.audio.EnhancedAudioManager
import com.xiaozhi.ai.data.ConfigManager
import com.xiaozhi.ai.data.Message
import com.xiaozhi.ai.data.MessageRole
import com.xiaozhi.ai.data.XiaozhiConfig
import com.xiaozhi.ai.network.WebSocketEvent
import com.xiaozhi.ai.network.WebSocketManager
import com.xiaozhi.ai.network.OtaService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 对话状态枚举
 *
 * 定义了应用在对话过程中的各种状态：
 * 1. IDLE: 空闲状态，等待用户操作
 * 2. CONNECTING: 正在连接WebSocket服务器
 * 3. LISTENING: 正在聆听用户语音输入
 * 4. PROCESSING: 正在处理用户输入（STT识别中）
 * 5. SPEAKING: 正在播放TTS语音回复
 */
enum class ConversationState {
    IDLE,           // 空闲状态，等待用户操作
    CONNECTING,     // 连接中，正在建立WebSocket连接
    LISTENING,      // 聆听中，正在录制用户语音
    PROCESSING,     // 处理中，正在等待服务器响应
    SPEAKING        // 说话中，正在播放TTS语音
}

/**
 * 对话ViewModel
 *
 * 负责管理整个对话流程的状态和业务逻辑：
 * 1. 管理WebSocket连接和通信
 * 2. 控制音频录制和播放
 * 3. 处理对话状态流转
 * 4. 管理消息列表和错误状态
 * 5. 处理OTA检查和设备激活
 */
class ConversationViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "ConversationViewModel"
    }

    // 依赖的服务组件
    private val gson = Gson() // JSON序列化/反序列化工具
    private val webSocketManager = WebSocketManager(application) // WebSocket通信管理器
    private val audioManager = EnhancedAudioManager(application) // 音频管理器
    private val otaService = OtaService() // OTA服务

    // 状态管理
    // 对话状态：空闲、连接中、聆听中、处理中、说话中
    private val _state = MutableStateFlow(ConversationState.IDLE)
    val state: StateFlow<ConversationState> = _state.asStateFlow()

    // WebSocket连接状态
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    // 对话消息列表
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    // 错误消息
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // 激活弹窗状态
    private val _showActivationDialog = MutableStateFlow(false)
    val showActivationDialog: StateFlow<Boolean> = _showActivationDialog.asStateFlow()

    // 激活码
    private val _activationCode = MutableStateFlow<String?>(null)
    val activationCode: StateFlow<String?> = _activationCode.asStateFlow()

    // 静音状态管理
    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    // 配置管理
    private val configManager = ConfigManager(application) // 配置管理器
    private var config = configManager.loadConfig() // 当前配置

    // 多轮对话支持
    private var isAutoMode = false // 是否为自动对话模式
    private var currentUserMessage: String? = null // 当前用户消息

    init {
        initializeServices()
    }

    /**
     * 初始化服务
     *
     * 在ViewModel创建时初始化所有依赖的服务：
     * 1. 启动事件监听器
     * 2. 初始化音频管理器
     * 3. 执行OTA检查
     */
    @SuppressLint("MissingPermission")
    private fun initializeServices() {
        // 首先启动事件监听，确保不会错过任何事件
        startEventListening()

        // 初始化音频管理器
        if (!audioManager.initialize()) {
            _errorMessage.value = "音频系统初始化失败"
            return
        }

        // 执行OTA检查
        performOtaCheck()
    }
    
    /**
     * 启动事件监听
     *
     * 启动对WebSocket和音频事件的监听：
     * 1. 监听WebSocket事件，处理服务器消息
     * 2. 监听音频事件，处理录音和播放
     */
    private fun startEventListening() {
        // 监听WebSocket事件 - 确保在WebSocket连接之前就开始监听
        viewModelScope.launch {
            Log.d(TAG, "开始监听WebSocket事件")
            webSocketManager.events.collect { event ->
                handleWebSocketEvent(event)
            }
        }

        // 监听音频事件
        viewModelScope.launch {
            audioManager.audioEvents.collect { event ->
                handleAudioEvent(event)
            }
        }
    }

    /**
     * 执行OTA检查
     *
     * 向OTA服务器上报设备信息并获取配置：
     * 1. 检查OTA URL配置
     * 2. 上报设备信息
     * 3. 处理服务器响应（WebSocket URL、激活信息等）
     * 4. 根据响应结果决定是否连接WebSocket
     */
    private fun performOtaCheck() {
        viewModelScope.launch {
            try {
                // 检查OTA URL是否配置
                if (config.otaUrl.isBlank()) {
                    Log.w(TAG, "OTA URL未配置，跳过OTA检查")
                    return@launch
                }

                Log.d(TAG, "开始执行OTA检查...")
                val result = otaService.reportDeviceAndGetOta(
                    clientId = config.uuid, // 客户端ID
                    deviceId = config.macAddress, // 设备MAC地址
                    otaUrl = config.otaUrl // OTA服务器URL
                )

                result.onSuccess { otaResponse ->
                    Log.d(TAG, "OTA检查成功")
                    Log.d(TAG, "服务器时间: ${otaResponse.serverTime.timestamp}")
                    Log.d(TAG, "固件版本: ${otaResponse.firmware.version}")
                    Log.d(TAG, "WebSocket URL: ${otaResponse.websocket.url}")

                    // 更新WebSocket URL（如果服务器返回了新的URL）
                    updateWebSocketUrl(otaResponse.websocket.url)

                    // 处理激活信息（如果是首次激活）
                    otaResponse.activation?.let { activation ->
                        Log.d(TAG, "设备激活信息: ${activation.message}")
                        Log.d(TAG, "激活码: ${activation.code}")
                        // 设备未激活，显示激活弹窗
                        _activationCode.value = activation.code
                        _showActivationDialog.value = true
                        return@onSuccess // 不连接WebSocket，等待用户确认激活
                    }

                    // OTA检查完成后连接WebSocket（仅在没有activation时）
                    connectToServer()
                }.onFailure { exception ->
                    Log.e(TAG, "OTA检查失败", exception)
                    _errorMessage.value = "OTA检查失败: ${exception.message}"
                }
            } catch (e: Exception) {
                Log.e(TAG, "OTA检查异常", e)
                _errorMessage.value = "OTA检查异常: ${e.message}"
            }
        }
    }
    
    /**
     * 用户确认激活后连接WebSocket
     */
    fun onActivationConfirmed() {
        Log.d(TAG, "用户确认激活，开始连接WebSocket")
        _showActivationDialog.value = false
        _activationCode.value = null
        connectToServer()
    }
    
    /**
     * 关闭激活弹窗
     */
    fun dismissActivationDialog() {
        _showActivationDialog.value = false
        _activationCode.value = null
    }
    
    /**
     * 更新WebSocket URL
     */
    private fun updateWebSocketUrl(newUrl: String) {
        if (newUrl.isNotEmpty() && newUrl != config.websocketUrl) {
            Log.d(TAG, "更新WebSocket URL: $newUrl")
            // 更新配置中的WebSocket URL
            val updatedConfig = config.copy(websocketUrl = newUrl)
            updateConfig(updatedConfig)
        }
    }

    /**
     * 连接到服务器
     *
     * 建立WebSocket连接到服务器：
     * 1. 设置连接状态为CONNECTING
     * 2. 检查WebSocket URL配置
     * 3. 如果URL为空，先执行OTA检查获取URL
     * 4. 如果URL已配置，直接连接
     */
    private fun connectToServer() {
        _state.value = ConversationState.CONNECTING

        // 如果websocketUrl为空，先请求OTA接口获取websocketUrl
        if (config.websocketUrl.isBlank()) {
            Log.d(TAG, "WebSocket URL为空，先执行OTA检查获取URL")
            performOtaCheckForWebSocketUrl()
        } else {
            // 直接使用配置的websocketUrl连接
            Log.d(TAG, "使用配置的WebSocket URL连接: ${config.websocketUrl}")
            webSocketManager.connect(
                url = config.websocketUrl, // WebSocket服务器URL
                deviceId = config.macAddress, // 设备MAC地址
                token = config.token // 访问令牌
            )
        }
    }
    
    /**
     * 专门用于获取WebSocket URL的OTA检查
     */
    private fun performOtaCheckForWebSocketUrl() {
        viewModelScope.launch {
            try {
                // 检查OTA URL是否配置
                if (config.otaUrl.isBlank()) {
                    Log.w(TAG, "OTA URL未配置，无法获取WebSocket URL")
                    _errorMessage.value = "OTA URL未配置，无法连接服务器"
                    _state.value = ConversationState.IDLE
                    return@launch
                }
                
                Log.d(TAG, "执行OTA检查以获取WebSocket URL...")
                val result = otaService.reportDeviceAndGetOta(
                    clientId = config.uuid,
                    deviceId = config.macAddress,
                    otaUrl = config.otaUrl
                )
                
                result.onSuccess { otaResponse ->
                    Log.d(TAG, "获取WebSocket URL成功: ${otaResponse.websocket.url}")
                    
                    // 更新WebSocket URL
                    updateWebSocketUrl(otaResponse.websocket.url)
                    
                    // 处理激活信息（如果是首次激活）
                    otaResponse.activation?.let { activation ->
                        Log.d(TAG, "设备激活信息: ${activation.message}")
                        Log.d(TAG, "激活码: ${activation.code}")
                        // 设备未激活，显示激活弹窗
                        _activationCode.value = activation.code
                        _showActivationDialog.value = true
                        return@onSuccess // 不连接WebSocket，等待用户确认激活
                    }
                    
                    // 使用获取到的WebSocket URL连接（仅在没有activation时）
                    webSocketManager.connect(
                        url = otaResponse.websocket.url,
                        deviceId = config.macAddress,
                        token = config.token
                    )
                }.onFailure { exception ->
                    Log.e(TAG, "获取WebSocket URL失败", exception)
                    _errorMessage.value = "获取WebSocket URL失败: ${exception.message}"
                    _state.value = ConversationState.IDLE
                }
            } catch (e: Exception) {
                Log.e(TAG, "获取WebSocket URL异常", e)
                _errorMessage.value = "获取WebSocket URL异常: ${e.message}"
                _state.value = ConversationState.IDLE
            }
        }
    }
    
    /**
     * 更新配置并重连
     */
    fun updateConfig(newConfig: XiaozhiConfig) {
        val oldConfig = config
        config = newConfig
        configManager.saveConfig(newConfig)
        Log.d(TAG, "配置已更新")
        
        // 如果WebSocket相关配置发生变化，需要重连
        if (oldConfig.websocketUrl != newConfig.websocketUrl || 
            oldConfig.macAddress != newConfig.macAddress ||
            oldConfig.token != newConfig.token) {
            Log.d(TAG, "WebSocket配置发生变化，执行重连")
            reconnect()
        }
    }

    /**
     * 处理WebSocket事件
     *
     * 处理来自WebSocket的各种事件：
     * 1. HelloReceived: 握手完成事件
     * 2. Connected: 连接成功事件
     * 3. Disconnected: 连接断开事件
     * 4. TextMessage: 文本消息事件
     * 5. BinaryMessage: 二进制消息事件
     * 6. MCPMessage: MCP协议消息事件
     * 7. Error: 错误事件
     */
    private fun handleWebSocketEvent(event: WebSocketEvent) {
        Log.d(TAG, "收到WebSocket事件: ${event::class.simpleName}")
        when (event) {
            is WebSocketEvent.HelloReceived -> {
                Log.d(TAG, "握手完成")
            }

            is WebSocketEvent.Connected -> {
                Log.d(TAG, "WebSocket连接成功")
                _isConnected.value = true
                _state.value = ConversationState.IDLE
                _errorMessage.value = null
            }

            is WebSocketEvent.Disconnected -> {
                Log.d(TAG, "WebSocket连接断开")
                _isConnected.value = false
                _state.value = ConversationState.IDLE
                audioManager.stopRecording() // 停止录音
                audioManager.stopPlaying() // 停止播放
            }

            is WebSocketEvent.TextMessage -> {
                handleTextMessage(event.message) // 处理文本消息
            }

            is WebSocketEvent.BinaryMessage -> {
                handleBinaryMessage(event.data) // 处理二进制消息
            }

            is WebSocketEvent.MCPMessage -> {
                handleMCPMessage(event.message) // 处理MCP消息
            }

            is WebSocketEvent.Error -> {
                Log.e(TAG, "WebSocket错误: ${event.error}")
                _errorMessage.value = event.error
                _state.value = ConversationState.IDLE
                audioManager.stopRecording() // 停止录音
                audioManager.stopPlaying() // 停止播放
            }
        }
    }

    /**
     * 处理文本消息
     *
     * 处理来自服务器的文本消息，根据消息类型执行相应操作：
     * 1. stt: 语音转文本结果，显示用户语音内容
     * 2. llm: 大语言模型表情结果，处理表情显示
     * 3. tts: 文本转语音控制消息，控制TTS播放状态
     */
    private fun handleTextMessage(message: String) {
        try {
            val json = gson.fromJson(message, JsonObject::class.java)
            val type = json.get("type")?.asString // 消息类型
            val sessionId = json.get("session_id")?.asString // 会话ID

            Log.d(TAG, "处理消息类型: $type, session_id: $sessionId")

            when (type) {
                "stt" -> {
                    // 语音转文本结果
                    val text = json.get("text")?.asString
                    if (!text.isNullOrEmpty() && !text.contains("请登录控制面板")) {
                        currentUserMessage = text
                        addMessage(Message(
                            role = MessageRole.USER, // 用户消息
                            content = text // 语音识别结果
                        ))
                        // STT结果表示用户说话结束，停止录音
                        audioManager.stopRecording()
                        _state.value = ConversationState.PROCESSING // 进入处理状态
                    }
                }

                "llm" -> {
                    // 大语言模型表情结果
                    val emotion = json.get("emotion")?.asString // 情感状态
                    val text = json.get("text")?.asString // 表情文本
                    Log.d(TAG, "收到表情: $emotion, 文本: $text")
                    // 可以在这里处理表情显示
                }

                "tts" -> {
                    val state = json.get("state")?.asString // TTS状态
                    when (state) {
                        "sentence_start" -> {
                            // TTS句子开始，显示要播放的文本
                            val text = json.get("text")?.asString
                            if (!text.isNullOrEmpty()) {
                                addMessage(Message(
                                    role = MessageRole.ASSISTANT, // 助手消息
                                    content = text // 要播放的文本
                                ))
                            }
                        }
                        "start" -> {
                            // TTS开始播放
                            _state.value = ConversationState.SPEAKING // 进入说话状态
                            Log.d(TAG, "开始TTS播放")
                        }
                        "stop" -> {
                            // TTS播放结束
                            audioManager.stopPlaying() // 停止音频播放
                            Log.d(TAG, "TTS播放结束")

                            // 根据模式决定下一步
                            if (isAutoMode) {
                                // 自动模式：继续下一轮对话
                                startNextRound()
                            } else {
                                // 手动模式：回到空闲状态
                                _state.value = ConversationState.IDLE
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析文本消息失败", e)
        }
    }

    /**
     * 处理二进制消息（音频数据）
     *
     * 处理来自服务器的二进制音频数据：
     * 1. 记录接收到的音频数据长度
     * 2. 检查是否处于静音状态
     * 3. 如果非静音状态，播放音频数据
     */
    private fun handleBinaryMessage(data: ByteArray) {

        Log.d(TAG, "收到二进制消息，长度: ${data.size}")
        // 只有在非静音状态下才播放音频数据
        if (!_isMuted.value) {
            audioManager.playAudio(data) // 播放音频数据
        } else {
            Log.d(TAG, "静音模式，跳过音频播放")
        }
    }

    /**
     * 处理音频事件
     *
     * 处理来自音频管理器的事件：
     * 1. AudioData: 音频数据事件，发送到服务器
     * 2. Error: 音频错误事件，显示错误信息并停止聆听
     */
    private fun handleAudioEvent(event: AudioEvent) {
        when (event) {
            is AudioEvent.AudioData -> {
                // 只有在聆听状态才发送音频数据
                if (_state.value == ConversationState.LISTENING) {
                    webSocketManager.sendBinaryMessage(event.data) // 发送音频数据到服务器
                }
            }
            is AudioEvent.Error -> {
                Log.e(TAG, "音频错误: ${event.message}")
                _errorMessage.value = event.message // 显示错误信息
                stopListening() // 停止聆听
            }
        }
    }

    /**
     * 开始聆听（手动模式）
     *
     * 启动手动聆听模式：
     * 1. 检查当前状态是否允许开始聆听
     * 2. 设置为手动模式
     * 3. 启动音频录制
     * 4. 通知服务器开始聆听
     */
    fun startListening() {
        // 检查当前状态是否允许开始聆听（必须是空闲状态且已连接）
        if (_state.value != ConversationState.IDLE || !_isConnected.value) {
            return
        }

        isAutoMode = false // 设置为手动模式
        _state.value = ConversationState.LISTENING // 进入聆听状态
        audioManager.startRecording() // 启动音频录制

        // 发送开始聆听消息
        webSocketManager.sendStartListening("manual") // 通知服务器开始手动聆听
        Log.d(TAG, "开始手动聆听")
    }

    /**
     * 开始自动对话模式
     *
     * 启动自动对话模式：
     * 1. 检查当前状态是否允许开始自动对话
     * 2. 设置为自动模式
     * 3. 启动音频录制
     * 4. 通知服务器开始自动聆听
     */
    fun startAutoConversation() {
        // 检查当前状态是否允许开始自动对话（必须是空闲状态且已连接）
        if (_state.value != ConversationState.IDLE || !_isConnected.value) {
            return
        }

        isAutoMode = true // 设置为自动模式
        _state.value = ConversationState.LISTENING // 进入聆听状态
        audioManager.startRecording() // 启动音频录制

        // 发送开始聆听消息
        webSocketManager.sendStartListening("auto") // 通知服务器开始自动聆听
        Log.d(TAG, "开始自动对话模式")
    }

    /**
     * 停止聆听
     *
     * 停止当前的聆听状态：
     * 1. 检查当前是否处于聆听状态
     * 2. 停止音频录制
     * 3. 进入处理状态
     * 4. 通知服务器停止聆听
     */
    fun stopListening() {
        // 检查当前是否处于聆听状态
        if (_state.value != ConversationState.LISTENING) {
            return
        }

        audioManager.stopRecording() // 停止音频录制
        _state.value = ConversationState.PROCESSING // 进入处理状态

        // 发送停止聆听消息
        webSocketManager.sendStopListening() // 通知服务器停止聆听
        Log.d(TAG, "停止聆听")
    }

    /**
     * 取消当前录音并发送中止信号（可选原因）
     * 用于上滑取消等场景：立即停止录音、停止Opus数据传输，并发送 type=abort 给服务器。
     *
     * @param reason 中止原因，默认为"user_interrupt"
     */
    fun cancelListeningWithAbort(reason: String = "user_interrupt") {
        // 将状态置为IDLE，确保 handleAudioEvent 不再发送后续音频帧
        if (_state.value == ConversationState.LISTENING) {
            _state.value = ConversationState.IDLE
        }
        // 停止录音，确保底层不再采集与编码音频
        audioManager.stopRecording()

        // 发送中止信号到服务器，包含 session_id 与原因
        webSocketManager.sendAbort(reason)

        Log.d(TAG, "取消录音并发送中止: $reason")
    }

    /**
     * 开始下一轮对话（自动模式）
     *
     * 在自动对话模式下开始下一轮对话：
     * 1. 检查是否为自动模式且已连接
     * 2. 进入聆听状态
     * 3. 启动音频录制
     * 4. 通知服务器开始自动聆听
     */
    private fun startNextRound() {
        // 检查是否为自动模式且已连接
        if (!isAutoMode || !_isConnected.value) {
            _state.value = ConversationState.IDLE
            return
        }

        _state.value = ConversationState.LISTENING // 进入聆听状态
        audioManager.startRecording() // 启动音频录制

        // 发送开始聆听消息
        webSocketManager.sendStartListening("auto") // 通知服务器开始自动聆听
        Log.d(TAG, "开始下一轮自动对话")
    }

    /**
     * 发送文本消息
     *
     * 发送文本消息到服务器：
     * 1. 检查连接状态和文本内容
     * 2. 发送文本请求（唤醒词检测消息）
     * 3. 进入处理状态
     *
     * @param text 要发送的文本内容
     */
    fun sendTextMessage(text: String) {
        // 检查连接状态和文本内容
        if (!_isConnected.value || text.isBlank()) {
            return
        }
        // 发送唤醒词检测消息
        webSocketManager.sendTextRequest(text) // 发送文本请求
        _state.value = ConversationState.PROCESSING // 进入处理状态
        Log.d(TAG, "发送文本消息: $text")
    }

    /**
     * 发送初始化消息（设备激活时使用，不添加到对话列表）
     */
    private fun sendInitializationMessage() {
        // 发送"初始化"文本消息，但不添加到对话列表
        webSocketManager.sendTextRequest("初始化")
        Log.d(TAG, "发送设备初始化消息")
    }

    /**
     * 打断当前对话
     *
     * 用户主动打断当前对话：
     * 1. 停止音频播放和录制
     * 2. 发送中断消息到服务器
     * 3. 退出自动模式
     * 4. 回到空闲状态
     */
    fun interrupt() {
        audioManager.stopPlaying() // 停止音频播放
        audioManager.stopRecording() // 停止音频录制

        // 发送中断消息
        webSocketManager.sendAbort("user_interrupt") // 发送中断消息到服务器

        // 退出自动模式
        isAutoMode = false // 退出自动模式
        _state.value = ConversationState.IDLE // 回到空闲状态
        Log.d(TAG, "用户打断对话")
    }

    /**
     * 停止自动对话模式
     *
     * 停止自动对话模式：
     * 1. 退出自动模式
     * 2. 停止音频录制和播放
     * 3. 发送停止自动模式消息到服务器
     * 4. 回到空闲状态
     */
    fun stopAutoConversation() {
        isAutoMode = false // 退出自动模式
        audioManager.stopRecording() // 停止音频录制
        audioManager.stopPlaying() // 停止音频播放

        // 发送中断消息
        webSocketManager.sendAbort("stop_auto_mode") // 发送停止自动模式消息到服务器

        _state.value = ConversationState.IDLE // 回到空闲状态
        Log.d(TAG, "停止自动对话模式")
    }

    /**
     * 添加消息到列表
     *
     * 将新的消息添加到消息列表中，用于在UI上显示对话历史
     *
     * @param message 要添加的消息对象
     */
    private fun addMessage(message: Message) {
        _messages.value = _messages.value + message // 将消息添加到消息列表
    }

    /**
     * 清除错误消息
     *
     * 清除当前显示的错误消息，通常在用户确认错误后调用
     */
    fun clearError() {
        _errorMessage.value = null // 清除错误消息
    }

    /**
     * 清除对话历史
     *
     * 清空所有对话消息，用于重置对话界面
     */
    fun clearMessages() {
        _messages.value = emptyList() // 清空对话历史
    }

    /**
     * 重新连接
     *
     * 断开当前WebSocket连接并重新连接到服务器
     * 通常在配置更改或连接异常时调用
     */
    fun reconnect() {
        webSocketManager.disconnect() // 断开当前连接
        connectToServer() // 重新连接到服务器
    }

    /**
     * 测试音频播放
     *
     * 播放一段测试音频，用于验证音频系统是否正常工作
     */
    fun testAudioPlayback() {
        audioManager.testAudioPlayback() // 执行音频播放测试
    }

    /**
     * 切换静音状态
     *
     * 切换音频播放的静音状态：
     * - 开启静音时停止当前播放
     * - 关闭静音时允许正常播放
     */
    fun toggleMute() {
        _isMuted.value = !_isMuted.value // 切换静音状态
        Log.d(TAG, "静音状态切换为: ${_isMuted.value}")

        // 如果切换到静音状态，停止当前播放
        if (_isMuted.value) {
            audioManager.stopPlaying() // 停止音频播放
        }
    }

    /**
     * 处理MCP消息
     *
     * 处理来自MCP协议的消息，MCP是小智AI的控制协议
     *
     * @param message MCP消息内容
     */
    private fun handleMCPMessage(message: String) {
        Log.d(TAG, "收到MCP消息: $message")

        // 添加MCP消息到对话列表（可选）
        addMessage(Message(
            role = MessageRole.SYSTEM, // 系统消息
            content = "MCP: $message" // MCP消息内容
        ))
    }

    /**
     * ViewModel销毁时的回调
     *
     * 在ViewModel被销毁时执行清理工作：
     * 1. 清理音频管理器资源
     * 2. 清理WebSocket连接资源
     */
    override fun onCleared() {
        super.onCleared()
        audioManager.cleanup() // 清理音频管理器资源
        webSocketManager.cleanup() // 清理WebSocket连接资源
    }
}