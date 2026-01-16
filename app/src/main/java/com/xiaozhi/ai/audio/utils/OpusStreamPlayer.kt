package com.xiaozhi.ai.audio.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Opus流式播放器
 *
 * 负责播放PCM音频数据流：
 * 1. 初始化AudioTrack
 * 2. 管理音频焦点
 * 3. 播放PCM数据流
 * 4. 释放播放器资源
 */
class OpusStreamPlayer(
    private val sampleRate: Int, // 采样率
    private val channels: Int, // 声道数
    frameSizeMs: Int, // 帧时长（毫秒）
    private val context: Context? = null // 上下文（用于音频焦点管理）
) {
    companion object {
        private const val TAG = "OpusStreamPlayer"
    }

    private var audioTrack: AudioTrack // 音频轨道
    private val playerScope = CoroutineScope(Dispatchers.IO + Job()) // 播放协程作用域
    private var isPlaying = false // 播放状态
    private var playbackJob: Job? = null // 播放任务

    // 音频焦点管理
    private var audioManager: AudioManager? = null // 音频管理器
    private var audioFocusRequest: AudioFocusRequest? = null // 音频焦点请求
    private var hasAudioFocus = false // 音频焦点状态

    init {
        // 配置音频轨道参数
        val channelConfig = if (channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            channelConfig,
            AudioFormat.ENCODING_PCM_16BIT
        ) * 2 // Increase buffer size

        // 创建音频轨道
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA) // 媒体用途
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH) // 语音内容
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate) // 采样率
                    .setChannelMask(channelConfig) // 声道配置
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT) // 编码格式
                    .build()
            )
            .setBufferSizeInBytes(bufferSize) // 缓冲区大小
            .setTransferMode(AudioTrack.MODE_STREAM) // 流式传输模式
            .build()

        // 初始化音频焦点管理
        context?.let {
            audioManager = it.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            setupAudioFocus() // 设置音频焦点
        }
    }

    /**
     * 设置音频焦点
     */
    private fun setupAudioFocus() {
        audioManager?.let { am ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setOnAudioFocusChangeListener { focusChange ->
                        handleAudioFocusChange(focusChange) // 处理音频焦点变化
                    }
                    .build()
            }
        }
    }

    /**
     * 处理音频焦点变化
     *
     * @param focusChange 音频焦点变化类型
     */
    private fun handleAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                Log.d(TAG, "获得音频焦点")
            }
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                hasAudioFocus = false
                Log.d(TAG, "失去音频焦点")
                stop() // 停止播放
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.d(TAG, "音频焦点被降低")
            }
        }
    }

    /**
     * 请求音频焦点
     *
     * @return true表示获得音频焦点，false表示未获得音频焦点
     */
    private fun requestAudioFocus(): Boolean {
        audioManager?.let { am ->
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am.requestAudioFocus(it) } ?: AudioManager.AUDIOFOCUS_REQUEST_FAILED
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    { focusChange -> handleAudioFocusChange(focusChange) },
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
            }

            hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            Log.d(TAG, "请求音频焦点结果: $hasAudioFocus")
            return hasAudioFocus
        }
        return true // 如果没有context，假设有焦点
    }

    /**
     * 放弃音频焦点
     */
    private fun abandonAudioFocus() {
        if (hasAudioFocus) {
            audioManager?.let { am ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
                } else {
                    @Suppress("DEPRECATION")
                    am.abandonAudioFocus { focusChange -> handleAudioFocusChange(focusChange) }
                }
            }
            hasAudioFocus = false
            Log.d(TAG, "释放音频焦点")
        }
    }

    /**
     * 开始播放PCM数据流
     *
     * @param pcmFlow PCM数据流
     */
    fun start(pcmFlow: Flow<ByteArray?>) {
        // 如果已经在播放，不要重新启动
        if (isPlaying) {
            Log.d(TAG, "播放器已经在运行，跳过重新启动")
            return
        }

        // 取消之前的播放任务
        playbackJob?.cancel()

        // 请求音频焦点
        if (!requestAudioFocus()) {
            Log.e(TAG, "无法获得音频焦点，播放可能会失败")
        }

        isPlaying = true // 设置播放状态
        if (audioTrack.state == AudioTrack.STATE_INITIALIZED) {
            audioTrack.play() // 开始播放
            Log.d(TAG, "AudioTrack开始播放，状态: ${audioTrack.playState}")
        } else {
            Log.e(TAG, "AudioTrack未初始化，状态: ${audioTrack.state}")
            isPlaying = false
            return
        }

        // 启动播放任务
        playbackJob = playerScope.launch {
            try {
                Log.d(TAG, "开始收集PCM数据流")
                pcmFlow.collect { pcmData -> // 收集PCM数据
                    if (isPlaying && pcmData != null) {
                        val bytesWritten = audioTrack.write(pcmData, 0, pcmData.size) // 写入音频数据
                        if (bytesWritten < 0) {
                            Log.e(TAG, "AudioTrack写入失败: $bytesWritten")
                        } else {
                            Log.d(TAG, "AudioTrack写入成功: $bytesWritten bytes")
                        }
                    }
                }
                Log.d(TAG, "PCM数据流收集完成")
            } catch (e: Exception) {
                Log.e(TAG, "播放音频流时出错", e)
            } finally {
                Log.d(TAG, "播放任务结束")
            }
        }
    }

    /**
     * 停止播放
     */
    fun stop() {
        if (isPlaying) {
            isPlaying = false // 设置播放状态为false
            playbackJob?.cancel() // 取消播放任务
            playbackJob = null

            if (audioTrack.state == AudioTrack.STATE_INITIALIZED) {
                audioTrack.stop() // 停止音频轨道
                Log.d(TAG, "AudioTrack停止播放")
            }
            abandonAudioFocus() // 放弃音频焦点
        }
    }

    /**
     * 释放播放器资源
     */
    fun release() {
        stop() // 停止播放
        playbackJob?.cancel() // 取消播放任务
        playbackJob = null
        audioTrack.release() // 释放音频轨道
        playerScope.cancel() // 取消协程作用域
    }

    /**
     * 等待播放完成
     *
     * 挂起函数，等待音频播放完成
     */
    suspend fun waitForPlaybackCompletion() {
        var position = 0
        while (audioTrack.playState == AudioTrack.PLAYSTATE_PLAYING && audioTrack.playbackHeadPosition != position) {
            Log.i(TAG, "audioTrack.playState: ${audioTrack.playState}, playbackHeadPosition: ${audioTrack.playbackHeadPosition}")
            position = audioTrack.playbackHeadPosition
            delay(100) // 检查间隔
        }
    }

    /**
     * 检查当前是否正在播放
     *
     * @return true表示正在播放，false表示未在播放
     */
    fun isCurrentlyPlaying(): Boolean {
        return isPlaying && audioTrack.playState == AudioTrack.PLAYSTATE_PLAYING
    }

    protected fun finalize() {
        release() // 释放资源
    }
}