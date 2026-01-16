package com.xiaozhi.ai.network

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit

/**
 * WebSocket事件类型
 *
 * 定义了WebSocket连接中可能发生的各种事件：
 * 1. Connected: 连接成功事件
 * 2. Disconnected: 连接断开事件
 * 3. TextMessage: 接收到文本消息事件
 * 4. BinaryMessage: 接收到二进制消息事件
 * 5. Error: 发生错误事件
 * 6. HelloReceived: 握手完成事件
 * 7. MCPMessage: MCP协议消息事件
 */
sealed class WebSocketEvent {
    object Connected : WebSocketEvent() // 连接成功
    object Disconnected : WebSocketEvent() // 连接断开
    data class TextMessage(val message: String) : WebSocketEvent() // 文本消息
    data class BinaryMessage(val data: ByteArray) : WebSocketEvent() // 二进制消息
    data class Error(val error: String) : WebSocketEvent() // 错误事件
    object HelloReceived : WebSocketEvent() // 握手完成
    data class MCPMessage(val message: String) : WebSocketEvent() // MCP消息
}

/**
 * WebSocket管理器
 *
 * 负责管理WebSocket连接和通信：
 * 1. 建立和维护WebSocket连接
 * 2. 处理握手协议
 * 3. 发送和接收文本/二进制消息
 * 4. 管理会话ID和连接状态
 * 5. 实现自动重连机制
 */
class WebSocketManager(private val context: Context) {

    companion object {
        private const val TAG = "WebSocketManager"
        private const val RECONNECT_DELAY = 2000L // 重连延迟2秒
        private const val HELLO_TIMEOUT = 15000L // 握手超时15秒
        private const val CONNECT_TIMEOUT = 15L // 连接超时15秒
        private const val WRITE_TIMEOUT = 15L // 写入超时15秒
    }

    // 依赖组件
    private val gson = Gson() // JSON序列化/反序列化工具
    private var webSocket: WebSocket? = null // WebSocket连接实例
    private var isConnected = false // 连接状态
    private var isHandshakeComplete = false // 握手完成状态
    private var shouldReconnect = true // 是否需要重连
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()) // 协程作用域

    // 会话管理
    private var sessionId: String? = null // 会话ID
    private var helloTimeoutJob: Job? = null // 握手超时任务

    // 连接参数，用于自动重连
    private var lastUrl: String? = null // 最后连接的URL
    private var lastDeviceId: String? = null // 最后连接的设备ID
    private var lastToken: String? = null // 最后连接的令牌

    // 事件流
    private val _events = MutableSharedFlow<WebSocketEvent>(replay = 1) // WebSocket事件流
    val events: SharedFlow<WebSocketEvent> = _events // 公开的事件流

    // HTTP客户端
    private val client = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS) // 连接超时
        .readTimeout(0, TimeUnit.SECONDS) // 保持无限读取超时
        .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS) // 写入超时
        .build()

    /**
     * 连接WebSocket
     *
     * 建立WebSocket连接到指定服务器：
     * 1. 保存连接参数用于自动重连
     * 2. 重置连接状态
     * 3. 构建连接请求并添加必要头部
     * 4. 建立WebSocket连接并设置监听器
     *
     * @param url WebSocket服务器URL
     * @param deviceId 设备ID（MAC地址）
     * @param token 访问令牌
     */
    fun connect(url: String, deviceId: String, token: String) {
        Log.d(TAG, "正在连接WebSocket: $url")

        // 保存连接参数用于自动重连
        lastUrl = url
        lastDeviceId = deviceId
        lastToken = token

        // 重置状态
        isHandshakeComplete = false // 重置握手状态
        sessionId = null // 清空会话ID

        // 构建连接请求
        val request = Request.Builder()
            .url(url) // WebSocket服务器URL
            .addHeader("Device-Id", deviceId) // 设备ID
            .addHeader("Client-Id", deviceId) // 客户端ID
            .addHeader("Protocol-Version", "1") // 协议版本
            .addHeader("Authorization", "Bearer $token") // 访问令牌
            .build()

        // 建立WebSocket连接
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket连接成功，开始握手")
                isConnected = true // 设置连接状态为已连接
                scope.launch {
                    // 发送Hello消息
                    sendHelloMessage() // 发送握手消息
                    // 启动超时检查
                    startHelloTimeout() // 启动握手超时检查
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "收到文本消息: $text")
                scope.launch {
                    handleTextMessage(text) // 处理文本消息
                    _events.emit(WebSocketEvent.TextMessage(text)) // 发出文本消息事件
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                Log.d(TAG, "收到二进制消息，长度: ${bytes.size}")
                scope.launch {
                    _events.emit(WebSocketEvent.BinaryMessage(bytes.toByteArray())) // 发出二进制消息事件
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket正在关闭: $code - $reason")
                reconnectAfterDisconnect() // 连接关闭后尝试重连
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket已关闭: $code - $reason")
                reconnectAfterDisconnect() // 连接关闭后尝试重连
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket连接失败", t)
                scope.launch {
                    _events.emit(WebSocketEvent.Error("连接失败")) // 发出错误事件
                }
                reconnectAfterDisconnect() // 连接失败后尝试重连
            }
        })
    }

    /**
     * 连接断开后重新连接
     *
     * 在连接断开后尝试重新连接：
     * 1. 重置连接状态
     * 2. 取消握手超时任务
     * 3. 延迟后使用保存的参数重新连接
     */
    private fun reconnectAfterDisconnect() {
        Log.d(TAG, "正在尝试自动重连...")
        isConnected = false // 设置连接状态为未连接
        isHandshakeComplete = false // 重置握手状态
        sessionId = null // 清空会话ID
        helloTimeoutJob?.cancel() // 取消握手超时任务

        scope.launch {
            // 检查是否需要重连且参数完整
            if (shouldReconnect && lastUrl != null && lastDeviceId != null && lastToken != null) {
                Log.d(TAG, "连接失败，准备自动重连...")
                delay(RECONNECT_DELAY) // 延迟重连
                connect(lastUrl!!, lastDeviceId!!, lastToken!!) // 重新连接
            }
        }
    }

    /**
     * 处理文本消息
     *
     * 解析并处理接收到的文本消息：
     * 1. 解析JSON格式的消息
     * 2. 根据消息类型执行相应处理
     * 3. 目前只处理hello类型消息
     *
     * @param text 接收到的文本消息
     */
    private fun handleTextMessage(text: String) {
        try {
            val json = gson.fromJson(text, JsonObject::class.java)
            val type = json.get("type")?.asString // 消息类型

            when (type) {
                "hello" -> {
                    handleHelloResponse(json) // 处理握手响应
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析文本消息失败", e)
        }
    }

    /**
     * 处理Hello响应
     *
     * 处理服务器返回的握手响应：
     * 1. 验证transport类型是否为websocket
     * 2. 提取会话ID
     * 3. 设置握手完成状态
     * 4. 取消握手超时任务
     * 5. 发出握手完成和连接成功事件
     *
     * @param json 服务器返回的Hello响应JSON对象
     */
    private fun handleHelloResponse(json: JsonObject) {
        val transport = json.get("transport")?.asString // 传输类型
        if (transport == "websocket") {
            // 提取session_id
            sessionId = json.get("session_id")?.asString // 提取会话ID
            isHandshakeComplete = true // 设置握手完成状态
            helloTimeoutJob?.cancel() // 取消握手超时任务

            Log.d(TAG, "握手完成1，session_id: $sessionId")
            scope.launch {
                Log.d(TAG, "握手完成2，session_id: $sessionId")
                Log.d(TAG, "发送HelloReceived事件")
                _events.emit(WebSocketEvent.HelloReceived) // 发出握手完成事件
                Log.d(TAG, "发送Connected事件")
                _events.emit(WebSocketEvent.Connected) // 发出连接成功事件
                Log.d(TAG, "事件发送完成")
            }
        } else {
            Log.e(TAG, "服务器返回的transport不匹配: $transport")
            scope.launch {
                _events.emit(WebSocketEvent.Error("握手失败：transport不匹配")) // 发出错误事件
            }
        }
    }

    /**
     * 启动Hello超时检查
     *
     * 启动握手超时检查任务：
     * 1. 延迟指定时间后检查握手是否完成
     * 2. 如果未完成，发出超时错误并断开连接
     */
    private fun startHelloTimeout() {
        helloTimeoutJob = scope.launch {
            delay(HELLO_TIMEOUT) // 等待握手超时时间
            if (!isHandshakeComplete) { // 如果握手未完成
                Log.e(TAG, "Hello握手超时")
                _events.emit(WebSocketEvent.Error("握手超时")) // 发出超时错误事件
                disconnect() // 断开连接
            }
        }
    }

    /**
     * 发送Hello消息
     *
     * 发送握手消息到服务器：
     * 1. 构建Hello消息JSON对象
     2. 包含协议版本、传输类型和音频参数
     3. 发送文本消息到服务器
     */
    private fun sendHelloMessage() {
        // 构建Hello消息
        val hello = JsonObject().apply {
            addProperty("type", "hello") // 消息类型
            addProperty("version", 1) // 协议版本
            addProperty("transport", "websocket") // 传输类型
            // 音频参数
            add("audio_params", JsonObject().apply {
                addProperty("format", "opus") // 音频格式
                addProperty("sample_rate", 16000) // 采样率
                addProperty("channels", 1) // 声道数
                addProperty("frame_duration", 60) // 帧时长
            })
        }
        sendTextMessage(gson.toJson(hello)) // 发送Hello消息
    }

    /**
     * 发送开始监听消息
     *
     * 通知服务器开始监听音频输入：
     * 1. 构建监听开始消息
     * 2. 包含会话ID、消息类型、状态和模式
     * 3. 发送文本消息到服务器
     *
     * @param mode 监听模式，"auto"表示自动模式，"manual"表示手动模式
     */
    fun sendStartListening(mode: String = "auto") {
        // 构建开始监听消息
        val message = JsonObject().apply {
            sessionId?.let { addProperty("session_id", it) } // 添加会话ID
            addProperty("type", "listen") // 消息类型
            addProperty("state", "start") // 状态
            addProperty("mode", mode) // 监听模式
        }
        sendTextMessage(gson.toJson(message)) // 发送开始监听消息
    }

    /**
     * 发送停止监听消息
     *
     * 通知服务器停止监听音频输入：
     * 1. 构建监听停止消息
     * 2. 包含会话ID、消息类型和状态
     * 3. 发送文本消息到服务器
     */
    fun sendStopListening() {
        // 构建停止监听消息
        val message = JsonObject().apply {
            sessionId?.let { addProperty("session_id", it) } // 添加会话ID
            addProperty("type", "listen") // 消息类型
            addProperty("state", "stop") // 状态
        }
        sendTextMessage(gson.toJson(message)) // 发送停止监听消息
    }

    /**
     * 发送唤醒词检测消息
     *
     * 发送文本消息作为唤醒词检测：
     * 1. 构建唤醒词检测消息
     * 2. 包含会话ID、消息类型、状态、文本内容和来源
     * 3. 发送文本消息到服务器
     *
     * @param text 要发送的文本内容
     */
    fun sendWakeWordDetected(text: String) {
        // 构建唤醒词检测消息
        val message = JsonObject().apply {
            sessionId?.let { addProperty("session_id", it) } // 添加会话ID
            addProperty("type", "listen") // 消息类型
            addProperty("state", "detect") // 状态
            addProperty("text", text) // 文本内容
            addProperty("source", "text") // 来源
        }
        sendTextMessage(gson.toJson(message)) // 发送唤醒词检测消息
    }

    /**
     * 发送中断消息
     *
     * 发送中断消息到服务器：
     * 1. 构建中断消息
     * 2. 包含会话ID、消息类型和中断原因
     * 3. 发送文本消息到服务器
     *
     * @param reason 中断原因，默认为"user_interrupt"
     */
    fun sendAbort(reason: String = "user_interrupt") {
        // 构建中断消息
        val message = JsonObject().apply {
            sessionId?.let { addProperty("session_id", it) } // 添加会话ID
            addProperty("type", "abort") // 消息类型
            addProperty("reason", reason) // 中断原因
        }
        sendTextMessage(gson.toJson(message)) // 发送中断消息
    }

    /**
     * 发送文本消息
     *
     * 发送文本消息到服务器：
     * 1. 检查连接状态
     * 2. 尝试发送消息
     * 3. 记录发送结果
     *
     * @param message 要发送的文本消息
     */
    fun sendTextMessage(message: String) {
        // 检查连接状态
        if (isConnected && webSocket != null) {
            try {
                val success = webSocket!!.send(message) // 发送消息
                if (success) {
                    Log.d(TAG, "发送文本消息: $message")
                } else {
                    Log.w(TAG, "发送文本消息失败，WebSocket可能已关闭")
                }
            } catch (e: Exception) {
                Log.e(TAG, "发送文本消息异常", e)
            }
        } else {
            Log.w(TAG, "WebSocket未连接，无法发送消息")
        }
    }

    /**
     * 发送二进制消息
     *
     * 发送二进制消息到服务器：
     * 1. 检查连接和握手状态
     * 2. 将字节数组转换为ByteString
     * 3. 发送二进制消息
     * 4. 记录发送结果
     *
     * @param data 要发送的二进制数据
     */
    fun sendBinaryMessage(data: ByteArray) {
        // 检查连接和握手状态
        if (isConnected && isHandshakeComplete && webSocket != null) {
            try {
                val success = webSocket!!.send(ByteString.of(*data)) // 发送二进制消息
                if (success) {
                    Log.d(TAG, "发送二进制消息，长度: ${data.size}")
                } else {
                    Log.w(TAG, "发送二进制消息失败，WebSocket可能已关闭")
                }
            } catch (e: Exception) {
                Log.e(TAG, "发送二进制消息异常", e)
            }
        } else {
            Log.w(TAG, "WebSocket未就绪，无法发送二进制消息")
        }
    }

    /**
     * 发送文本请求（兼容旧接口）
     *
     * 兼容旧接口的文本请求方法，实际调用发送唤醒词检测消息
     *
     * @param text 要发送的文本内容
     */
    fun sendTextRequest(text: String) {
        sendWakeWordDetected(text) // 调用发送唤醒词检测消息方法
    }

    /**
     * 断开连接
     *
     * 主动断开WebSocket连接：
     * 1. 禁用自动重连
     * 2. 取消握手超时任务
     * 3. 关闭WebSocket连接
     * 4. 重置所有连接状态
     * 5. 清理连接参数
     */
    fun disconnect() {
        shouldReconnect = false // 禁用自动重连
        helloTimeoutJob?.cancel() // 取消握手超时任务
        webSocket?.close(1000, "正常关闭") // 关闭WebSocket连接
        webSocket = null // 清空WebSocket实例
        isConnected = false // 设置连接状态为未连接
        isHandshakeComplete = false // 重置握手状态
        sessionId = null // 清空会话ID

        // 清理连接参数
        lastUrl = null // 清空最后连接的URL
        lastDeviceId = null // 清空最后连接的设备ID
        lastToken = null // 清空最后连接的令牌
    }

    /**
     * 检查连接状态
     *
     * 检查WebSocket是否已连接且握手完成
     *
     * @return true表示已连接且握手完成，false表示未连接或握手未完成
     */
    fun isConnected(): Boolean = isConnected && isHandshakeComplete

    /**
     * 获取会话ID
     *
     * 获取当前连接的会话ID
     *
     * @return 会话ID，如果未连接则返回null
     */
    fun getSessionId(): String? = sessionId

    /**
     * 重新启用自动重连
     *
     * 重新启用WebSocket自动重连功能
     */
    fun enableReconnect() {
        shouldReconnect = true // 启用自动重连
    }

    /**
     * 禁用自动重连
     *
     * 禁用WebSocket自动重连功能
     */
    fun disableReconnect() {
        shouldReconnect = false // 禁用自动重连
    }

    /**
     * 清理资源
     *
     * 清理WebSocket管理器的所有资源：
     * 1. 断开WebSocket连接
     * 2. 取消协程作用域
     */
    fun cleanup() {
        disconnect() // 断开WebSocket连接
        scope.cancel() // 取消协程作用域
    }
}