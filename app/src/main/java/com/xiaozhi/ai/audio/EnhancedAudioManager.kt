package com.xiaozhi.ai.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.xiaozhi.ai.audio.utils.OpusDecoder
import com.xiaozhi.ai.audio.utils.OpusEncoder
import com.xiaozhi.ai.audio.utils.OpusStreamPlayer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.flow

/**
 * 音频事件
 *
 * 定义了音频处理过程中可能发生的事件：
 * 1. AudioData: 音频数据事件，包含录制的音频数据
 * 2. Error: 音频错误事件，包含错误信息
 */
sealed class AudioEvent {
    data class AudioData(val data: ByteArray) : AudioEvent() // 音频数据事件
    data class Error(val message: String) : AudioEvent() // 音频错误事件
}

/**
 * 增强版音频管理器
 * 使用真正的Opus编解码器和流式播放
 *
 * 负责管理音频录制和播放功能：
 * 1. 音频录制（支持回声消除和降噪）
 * 2. Opus编解码（使用原生库）
 * 3. 音频播放（流式播放）
 * 4. 音频事件分发
 */
class EnhancedAudioManager(private val context: Context) {
    companion object {
        private const val TAG = "EnhancedAudioManager"
        private const val RECORD_SAMPLE_RATE = 16000 // 录音采样率
        private const val PLAY_SAMPLE_RATE = 24000 // 播放采样率
        private const val CHANNELS = AudioFormat.CHANNEL_IN_MONO // 单声道
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT // 16位PCM格式
        private const val FRAME_DURATION_MS = 60 // 帧时长60毫秒
        private const val FRAME_SIZE = RECORD_SAMPLE_RATE * FRAME_DURATION_MS / 1000 * 2 // 16bit = 2 bytes，帧大小
    }

    // 音频录制组件
    private var audioRecord: AudioRecord? = null // 音频录制器
    private var isRecording = false // 录制状态
    private var isPlayingState = false // 播放状态
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()) // 协程作用域

    // 音频事件流
    private val _audioEvents = MutableSharedFlow<AudioEvent>() // 音频事件流
    val audioEvents: SharedFlow<AudioEvent> = _audioEvents // 公开的音频事件流

    // AEC和NS处理器
    private var acousticEchoCanceler: AcousticEchoCanceler? = null // 回声消除器
    private var noiseSuppressor: NoiseSuppressor? = null // 噪声抑制器

    // Opus编解码器
    private var opusEncoder: OpusEncoder? = null // Opus编码器
    private var opusDecoder: OpusDecoder? = null // Opus解码器
    private var streamPlayer: OpusStreamPlayer? = null // 流式播放器

    // 音频播放流
    private val _audioPlaybackFlow = MutableSharedFlow<ByteArray>() // 音频播放流
    private var playbackJob: Job? = null // 播放任务
    private var isPlaybackSetup = false // 播放流设置状态

    /**
     * 初始化音频系统
     *
     * 初始化音频系统的所有组件：
     * 1. 初始化Opus编解码器
     * 2. 初始化流式播放器
     * 3. 设置音频录制器
     * 4. 不在初始化时启动播放流，而是在需要时启动
     *
     * @return true表示初始化成功，false表示初始化失败
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun initialize(): Boolean {
        return try {
            // 初始化Opus编解码器
            opusEncoder = OpusEncoder(RECORD_SAMPLE_RATE, 1, FRAME_DURATION_MS) // 初始化Opus编码器
            opusDecoder = OpusDecoder(PLAY_SAMPLE_RATE, 1, FRAME_DURATION_MS) // 初始化Opus解码器
            streamPlayer = OpusStreamPlayer(PLAY_SAMPLE_RATE, 1, FRAME_DURATION_MS, context) // 初始化流式播放器

            setupAudioRecord() // 设置音频录制器
            // 不在初始化时启动播放流，而是在需要时启动
            Log.d(TAG, "增强版音频系统初始化成功")
            true
        } catch (e: Exception) {
            Log.e(TAG, "增强版音频系统初始化失败", e)
            false
        }
    }

    /**
     * 设置录音器
     *
     * 设置音频录制器：
     * 1. 检查录音权限
     * 2. 计算缓冲区大小
     * 3. 创建AudioRecord实例
     * 4. 设置音频效果（AEC和NS）
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun setupAudioRecord() {
        // 检查录音权限
        if (!checkPermissions()) {
            throw SecurityException("缺少录音权限")
        }

        // 计算缓冲区大小
        val bufferSize = AudioRecord.getMinBufferSize(RECORD_SAMPLE_RATE, CHANNELS, AUDIO_FORMAT)

        // 创建AudioRecord实例
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION, // 语音通信源
            RECORD_SAMPLE_RATE, // 采样率
            CHANNELS, // 声道配置
            AUDIO_FORMAT, // 音频格式
            bufferSize * 2 // 缓冲区大小
        )

        // 设置AEC和NS
        setupAudioEffects() // 设置音频效果
    }

    /**
     * 设置音频效果（AEC+NS）
     *
     * 设置音频录制的回声消除和噪声抑制效果：
     * 1. 检查设备是否支持AEC
     * 2. 创建并启用回声消除器
     * 3. 检查设备是否支持NS
     * 4. 创建并启用噪声抑制器
     */
    private fun setupAudioEffects() {
        audioRecord?.let { record ->
            try {
                // 回音消除
                if (AcousticEchoCanceler.isAvailable()) {
                    acousticEchoCanceler = AcousticEchoCanceler.create(record.audioSessionId) // 创建回声消除器
                    acousticEchoCanceler?.enabled = true // 启用回声消除
                    Log.d(TAG, "AEC已启用")
                } else {
                    Log.w(TAG, "设备不支持AEC")
                }

                // 噪声抑制
                if (NoiseSuppressor.isAvailable()) {
                    noiseSuppressor = NoiseSuppressor.create(record.audioSessionId) // 创建噪声抑制器
                    noiseSuppressor?.enabled = true // 启用噪声抑制
                    Log.d(TAG, "NS已启用")
                } else {
                    Log.w(TAG, "设备不支持NS")
                }
            } catch (e: Exception) {
                Log.e(TAG, "设置音频效果失败", e)
            }
        }
    }

    /**
     * 设置音频播放流
     *
     * 设置音频播放流：
     * 1. 防止重复设置播放流
     2. 创建持续的PCM数据流
     3. 解码Opus数据为PCM数据
     4. 启动流式播放器
     */
    private fun setupAudioPlayback() {
        // 防止重复设置播放流
        if (isPlaybackSetup) {
            Log.d(TAG, "播放流已经设置，跳过重复设置")
            return
        }

        isPlaybackSetup = true // 标记播放流已设置
        Log.d(TAG, "首次设置音频播放流")

        // 创建持续的PCM数据流
        val pcmFlow = flow {
            _audioPlaybackFlow.collect { opusData -> // 收集Opus数据
                try {
                    opusDecoder?.let { decoder ->
                        val pcmData = decoder.decode(opusData) // 解码Opus数据为PCM数据
                        pcmData?.let {
                            emit(it) // 发出PCM数据
                            Log.d(TAG, "解码音频数据，PCM大小: ${it.size}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "解码音频数据失败", e)
                }
            }
        }

        // 启动播放器
        streamPlayer?.start(pcmFlow) // 启动流式播放器
        Log.d(TAG, "音频播放流已设置并启动")
    }

    /**
     * 开始录音
     *
     * 开始音频录制：
     * 1. 检查是否已经在录制
     * 2. 启动音频录制器
     * 3. 启动录制协程
     * 4. 读取音频数据并编码为Opus格式
     * 5. 发出音频数据事件
     */
    fun startRecording() {
        // 检查是否已经在录制
        if (isRecording) return

        audioRecord?.let { record ->
            try {
                record.startRecording() // 启动音频录制器
                isRecording = true // 设置录制状态为true
                Log.d(TAG, "开始录音")

                // 启动录制协程
                scope.launch {
                    val buffer = ByteArray(FRAME_SIZE) // 创建音频数据缓冲区
                    while (isRecording) {
                        val bytesRead = record.read(buffer, 0, buffer.size) // 读取音频数据
                        if (bytesRead > 0) {
                            // 使用真正的Opus编码器
                            opusEncoder?.let { encoder ->
                                val opusData = encoder.encode(buffer.copyOf(bytesRead)) // 编码为Opus格式
                                opusData?.let {
                                    _audioEvents.emit(AudioEvent.AudioData(it)) // 发出音频数据事件
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "录音失败", e)
                scope.launch {
                    _audioEvents.emit(AudioEvent.Error("录音失败: ${e.message}")) // 发出错误事件
                }
            }
        }
    }

    /**
     * 停止录音
     *
     * 停止音频录制：
     * 1. 检查是否正在录制
     * 2. 设置录制状态为false
     * 3. 停止音频录制器
     */
    fun stopRecording() {
        // 检查是否正在录制
        if (!isRecording) return

        isRecording = false // 设置录制状态为false
        audioRecord?.stop() // 停止音频录制器
        Log.d(TAG, "停止录音")
    }

    /**
     * 播放音频数据（单次播放）
     *
     * 播放单次音频数据：
     * 1. 确保播放器已经启动
     * 2. 设置播放状态
     * 3. 设置音频播放流
     * 4. 发送音频数据到播放流
     *
     * @param audioData 要播放的音频数据（Opus格式）
     */
    fun playAudio(audioData: ByteArray) {
        scope.launch {
            try {
                // 确保播放器已经启动
                if (!isPlayingState) {
                    isPlayingState = true // 设置播放状态
                    Log.d(TAG, "首次播放音频，设置播放流")
                    setupAudioPlayback() // 设置音频播放流
                }

                // 直接发送到播放流
                _audioPlaybackFlow.emit(audioData) // 发送音频数据到播放流
                Log.d(TAG, "发送音频数据到播放流，长度: ${audioData.size}")
            } catch (e: Exception) {
                Log.e(TAG, "播放音频失败", e)
            }
        }
    }

    /**
     * 停止播放
     *
     * 停止音频播放：
     * 1. 设置播放状态为false
     * 2. 重置播放流设置状态
     * 3. 停止流式播放器
     */
    fun stopPlaying() {
        isPlayingState = false // 设置播放状态为false
        isPlaybackSetup = false // 重置播放流设置状态
        streamPlayer?.stop() // 停止流式播放器

        Log.d(TAG, "停止播放并重置状态")
    }

    /**
     * 开始流式播放
     *
     * 开始流式音频播放：
     * 1. 取消之前的播放任务
     * 2. 启动新的播放协程
     * 3. 设置播放状态
     * 4. 收集Opus数据流并发送到播放流
     *
     * @param opusDataFlow Opus数据流
     */
    fun startStreamPlayback(opusDataFlow: SharedFlow<ByteArray>) {
        playbackJob?.cancel() // 取消之前的播放任务
        playbackJob = scope.launch {
            isPlayingState = true // 设置播放状态
            opusDataFlow.collect { opusData -> // 收集Opus数据
                _audioPlaybackFlow.emit(opusData) // 发送到播放流
            }
        }
        Log.d(TAG, "开始流式播放")
    }

    /**
     * 停止流式播放
     *
     * 停止流式音频播放：
     * 1. 设置播放状态为false
     * 2. 重置播放流设置状态
     * 3. 取消播放任务
     * 4. 停止流式播放器
     */
    fun stopStreamPlayback() {
        isPlayingState = false // 设置播放状态为false
        isPlaybackSetup = false // 重置播放流设置状态
        playbackJob?.cancel() // 取消播放任务
        streamPlayer?.stop() // 停止流式播放器

        Log.d(TAG, "停止流式播放并重置状态")
    }

    /**
     * 等待播放完成
     *
     * 等待音频播放完成：
     * 挂起函数，等待流式播放器播放完成
     */
    suspend fun waitForPlaybackCompletion() {
        streamPlayer?.waitForPlaybackCompletion() // 等待流式播放器播放完成
    }

    /**
     * 检查权限
     *
     * 检查是否具有录音权限
     *
     * @return true表示具有录音权限，false表示没有录音权限
     */
    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 清理资源
     *
     * 清理音频管理器的所有资源：
     * 1. 停止录音和播放
     * 2. 释放音频效果处理器
     * 3. 释放音频录制器
     * 4. 释放Opus编解码器资源
     * 5. 释放流式播放器资源
     * 6. 取消协程作用域
     */
    fun cleanup() {
        stopRecording() // 停止录音
        stopStreamPlayback() // 停止流式播放

        acousticEchoCanceler?.release() // 释放回声消除器
        noiseSuppressor?.release() // 释放噪声抑制器

        audioRecord?.release() // 释放音频录制器

        // 释放Opus编解码器资源
        opusEncoder?.release() // 释放Opus编码器
        opusDecoder?.release() // 释放Opus解码器
        streamPlayer?.release() // 释放流式播放器

        scope.cancel() // 取消协程作用域
        Log.d(TAG, "增强版音频资源已清理")
    }

    /**
     * 获取录音状态
     *
     * 获取当前是否正在录音
     *
     * @return true表示正在录音，false表示未在录音
     */
    fun isRecording(): Boolean = isRecording

    /**
     * 获取播放状态
     *
     * 获取当前是否正在播放音频
     *
     * @return true表示正在播放，false表示未在播放
     */
    fun isPlaying(): Boolean = isPlayingState
    
    /**
     * 测试音频播放（生成一个简单的测试音调）
     *
     * 测试音频播放功能：
     * 1. 创建独立的测试播放器
     * 2. 生成440Hz的测试音调（A4音符）
     * 3. 创建PCM数据流
     * 4. 播放测试音调
     * 5. 等待播放完成并释放资源
     */
    fun testAudioPlayback() {
        scope.launch {
            try {
                // 创建一个独立的测试播放器
                val testPlayer = OpusStreamPlayer(PLAY_SAMPLE_RATE, 1, FRAME_DURATION_MS, context)

                // 生成一个440Hz的测试音调（A4音符）
                val sampleRate = PLAY_SAMPLE_RATE
                val duration = 1.0 // 1秒
                val frequency = 440.0
                val samples = (sampleRate * duration).toInt()
                val pcmData = ByteArray(samples * 2) // 16-bit = 2 bytes per sample

                for (i in 0 until samples) {
                    val sample = (32767 * kotlin.math.sin(2 * kotlin.math.PI * frequency * i / sampleRate)).toInt().toShort()
                    pcmData[i * 2] = (sample.toInt() and 0xFF).toByte()
                    pcmData[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
                }

                // 直接发送PCM数据到测试播放器
                val testFlow = flow {
                    emit(pcmData) // 发出PCM数据
                }

                testPlayer.start(testFlow) // 启动测试播放器
                Log.d(TAG, "开始播放测试音调")

                // 等待播放完成
                delay(1500)
                testPlayer.stop() // 停止测试播放器
                testPlayer.release() // 释放测试播放器
                Log.d(TAG, "测试音调播放完成")

            } catch (e: Exception) {
                Log.e(TAG, "测试音频播放失败", e)
            }
        }
    }
}