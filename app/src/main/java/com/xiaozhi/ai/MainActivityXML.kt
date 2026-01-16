package com.xiaozhi.ai

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.xiaozhi.ai.adapter.MessageAdapter
import com.xiaozhi.ai.data.Message
import com.xiaozhi.ai.data.MessageRole
import com.xiaozhi.ai.viewmodel.ConversationViewModel
import com.xiaozhi.ai.viewmodel.ConversationState
import kotlinx.coroutines.launch

class MainActivityXML : AppCompatActivity() {
    private val viewModel: ConversationViewModel by viewModels()
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var connectionStatus: ImageView
    private lateinit var muteButton: ImageButton
    private lateinit var settingsButton: ImageButton
    private  var statusCard: MaterialCardView?=null
    private lateinit var statusMessage: TextView
    private lateinit var closeButton: ImageButton
    private lateinit var voiceInputButton: FrameLayout
    private lateinit var voiceInputText: TextView
    private lateinit var permissionHint: TextView
    private lateinit var callButton: FloatingActionButton
    private lateinit var textInputContainer: LinearLayout
    private lateinit var textInput: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var voiceInputContainer: LinearLayout

    // Activity结果启动器
    private lateinit var settingsActivityResultLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 强制使用深色主题
        setTheme(R.style.Theme_Yuansheng_Dark)
        setContentView(R.layout.activity_main_xml)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 初始化ActivityResultLauncher
        initActivityResultLaunchers()

        // 初始化UI组件
        initUI()

        // 初始化消息列表
        initMessageList()

        // 观察ViewModel状态
        observeViewModel()

        // 初始化连接状态
        initializeConnection()
    }

    private fun initUI() {
        // 获取顶部标题栏中的组件
        connectionStatus = findViewById(R.id.connection_status)
        muteButton = findViewById(R.id.mute_button)
        settingsButton = findViewById(R.id.settings_button)

        // 获取状态指示器组件
        statusCard = findViewById(R.id.status_card)
        statusMessage = findViewById(R.id.status_message)
        closeButton = findViewById(R.id.close_button)

        // 获取底部输入区域组件
        textInputContainer = findViewById(R.id.text_input_container)
        textInput = findViewById(R.id.text_input)
        sendButton = findViewById(R.id.send_button)
        voiceInputContainer = findViewById(R.id.voice_input_container)
        voiceInputButton = findViewById(R.id.voice_input_button)
        voiceInputText = findViewById(R.id.voice_input_text)
        permissionHint = findViewById(R.id.permission_hint)
        callButton = findViewById(R.id.call_button)

        // 设置按钮点击事件
        muteButton.setOnClickListener {
            // 切换静音状态
            toggleMute()
        }

        settingsButton.setOnClickListener {
            // 打开设置页面
            openSettings()
        }

        closeButton.setOnClickListener {
            // 隐藏状态指示器
            statusCard?.visibility = View.GONE
        }

        voiceInputButton.setOnClickListener {
            // 处理语音输入
            handleVoiceInput()
        }

        callButton.setOnClickListener {
            // 处理打电话
            handleCall()
        }

        sendButton.setOnClickListener {
            // 发送文本消息
            sendTextMessage()
        }

        // 设置文本输入框的回车键监听器
        textInput.setOnEditorActionListener { _, _, _ ->
            sendTextMessage()
            true
        }
    }

    private fun initActivityResultLaunchers() {
        settingsActivityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                // 配置已更改，重新连接
                connectToServer()
            }
        }
    }

    private fun initMessageList() {
        val recyclerView = findViewById<RecyclerView>(R.id.messages_recycler_view)
        messageAdapter = MessageAdapter()
        recyclerView.adapter = messageAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun observeViewModel() {
        // 观察连接状态
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isConnected.collect { isConnected ->
                    runOnUiThread {
                        updateConnectionStatus(isConnected)
                        // 根据连接状态更新输入区域
                        updateInputAreaVisibility(isConnected)
                    }
                }
            }
        }

        // 观察消息列表
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.messages.collect { messages ->
                    runOnUiThread {
                        messageAdapter.updateMessages(messages)
                    }
                }
            }
        }

        // 观察错误消息
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.errorMessage.collect { errorMessage ->
                    runOnUiThread {
                        if (errorMessage != null) {
                            showErrorMessage(errorMessage)
                        } else {
                            hideErrorMessage()
                        }
                    }
                }
            }
        }

        // 观察对话状态
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    runOnUiThread {
                        updateDialogState(state)
                    }
                }
            }
        }
    }

    private fun initializeConnection() {
        // 连接WebSocket服务器
        // 注意：实际的连接参数应该从配置中获取
        // 这里使用示例参数，实际应用中应该从配置管理器获取
        // 由于这是一个示例，我们暂时不自动连接，让用户手动触发连接
    }

    private fun updateConnectionStatus(isConnected: Boolean) {
        if (isConnected) {
            connectionStatus.setImageResource(R.drawable.cloud_on)
            connectionStatus.setColorFilter(getColor(R.color.connectedGreen))
        } else {
            connectionStatus.setImageResource(R.drawable.cloud_disabled)
            connectionStatus.setColorFilter(getColor(R.color.connectionRed))
        }
    }

    private fun showErrorMessage(message: String) {
        statusCard?.visibility = View.VISIBLE
        statusMessage.text = message
    }

    private fun hideErrorMessage() {
        statusCard?.visibility = View.GONE
    }

    private fun updateDialogState(state: ConversationState) {
        when (state) {
            ConversationState.IDLE -> {
                // 空闲状态
                voiceInputText.text = getString(R.string.input_hint)
            }
            ConversationState.CONNECTING -> {
                // 连接中
                voiceInputText.text = getString(R.string.connecting)
            }
            ConversationState.LISTENING -> {
                // 聆听中
                voiceInputText.text = getString(R.string.listening)
            }
            ConversationState.PROCESSING -> {
                // 处理中
                voiceInputText.text = getString(R.string.processing)
            }
            ConversationState.SPEAKING -> {
                // 说话中
                voiceInputText.text = getString(R.string.speaking)
            }
        }
    }

    private fun updateInputAreaVisibility(isConnected: Boolean) {
        if (isConnected) {
            // 连接成功，显示输入区域
            textInputContainer.visibility = View.VISIBLE
            voiceInputContainer.visibility = View.VISIBLE
        } else {
            // 未连接，隐藏输入区域
            textInputContainer.visibility = View.GONE
            voiceInputContainer.visibility = View.GONE
        }
    }

    private fun toggleMute() {
        viewModel.toggleMute()
    }

    private fun openSettings() {
        // 启动SettingsActivity
        val intent = Intent(this, SettingsActivity::class.java)
        settingsActivityResultLauncher.launch(intent)
    }

    private fun connectToServer() {
        // 使用ViewModel的重新连接方法
        viewModel.reconnect()
        Toast.makeText(this, "正在连接到服务器...", Toast.LENGTH_SHORT).show()
    }

    private fun handleVoiceInput() {
        // 处理语音输入的逻辑
        lifecycleScope.launch {
            viewModel.state.collect { state ->
                when (state) {
                    ConversationState.IDLE -> {
                        // 空闲状态，开始聆听
                        viewModel.startListening()
                    }
                    ConversationState.LISTENING -> {
                        // 聆听中，停止聆听
                        viewModel.stopListening()
                    }
                    else -> {
                        // 其他状态，不处理
                    }
                }
            }
        }
    }

    private fun handleCall() {
        // 处理打电话的逻辑
        Toast.makeText(this, "打电话功能", Toast.LENGTH_SHORT).show()
    }

    private fun sendTextMessage() {
        val message = textInput.text.toString().trim()
        if (message.isNotEmpty()) {
            viewModel.sendTextMessage(message)
            textInput.setText("") // 清空输入框
        }
    }

    private fun toggleInputMode() {
        // 切换输入模式（语音/文本）
        val isTextInputVisible = textInputContainer.visibility == View.VISIBLE
        val isVoiceInputVisible = voiceInputContainer.visibility == View.VISIBLE

        if (isTextInputVisible && isVoiceInputVisible) {
            // 当前显示两个输入区域，切换到只显示语音输入
            textInputContainer.visibility = View.GONE
            voiceInputContainer.visibility = View.VISIBLE
        } else if (!isTextInputVisible && isVoiceInputVisible) {
            // 当前只显示语音输入，切换到只显示文本输入
            textInputContainer.visibility = View.VISIBLE
            voiceInputContainer.visibility = View.GONE
        } else {
            // 其他情况，显示两个输入区域
            textInputContainer.visibility = View.VISIBLE
            voiceInputContainer.visibility = View.VISIBLE
        }
    }
}