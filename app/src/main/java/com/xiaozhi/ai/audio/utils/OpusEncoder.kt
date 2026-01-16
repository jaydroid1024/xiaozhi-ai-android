package com.xiaozhi.ai.audio.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Opus编码器
 *
 * 负责将PCM音频数据编码为Opus格式：
 * 1. 加载原生库
 * 2. 初始化Opus编码器
 * 3. 编码PCM数据为Opus格式
 * 4. 释放编码器资源
 */
class OpusEncoder(
    private val sampleRate: Int, // 采样率
    private val channels: Int, // 声道数
    frameSizeMs: Int // 帧时长（毫秒）
) {
    companion object {
        private const val TAG = "OpusEncoder"

        init {
            System.loadLibrary("app") // 加载原生库
        }
    }

    private var nativeEncoderHandle: Long = 0 // 原生编码器句柄
    private val frameSize: Int = (sampleRate * frameSizeMs) / 1000 // 帧大小（采样点数）

    init {
        // 初始化原生编码器
        nativeEncoderHandle = nativeInitEncoder(sampleRate, channels, 2048) // OPUS_APPLICATION_VOIP
        if (nativeEncoderHandle == 0L) {
            throw IllegalStateException("Failed to initialize Opus encoder")
        }
    }

    /**
     * 编码PCM数据为Opus格式
     *
     * @param pcmData PCM音频数据
     * @return 编码后的Opus数据，失败时返回null
     */
    suspend fun encode(pcmData: ByteArray): ByteArray? = withContext(Dispatchers.IO) {
        val frameBytes = frameSize * channels * 2 // 16-bit PCM，计算帧字节数
        if (pcmData.size != frameBytes) {
            Log.e(TAG, "Input buffer size must be $frameBytes bytes (got ${pcmData.size})")
            return@withContext null
        }

        val outputBuffer = ByteArray(frameBytes) // 分配足够大的缓冲区
        val encodedBytes = nativeEncodeBytes(
            nativeEncoderHandle, // 编码器句柄
            pcmData, // 输入PCM数据
            pcmData.size, // 输入数据大小
            outputBuffer, // 输出缓冲区
            outputBuffer.size // 最大输出大小
        )

        if (encodedBytes > 0) {
            outputBuffer.copyOf(encodedBytes) // 复制有效数据
        } else {
            Log.e(TAG, "Failed to encode frame")
            null
        }
    }

    /**
     * 释放编码器资源
     */
    fun release() {
        if (nativeEncoderHandle != 0L) {
            nativeReleaseEncoder(nativeEncoderHandle) // 释放原生编码器
            nativeEncoderHandle = 0 // 清空句柄
        }
    }

    protected fun finalize() {
        release() // 释放资源
    }

    // 原生方法声明
    private external fun nativeInitEncoder(sampleRate: Int, channels: Int, application: Int): Long
    private external fun nativeEncodeBytes(
        encoderHandle: Long,
        inputBuffer: ByteArray,
        inputSize: Int,
        outputBuffer: ByteArray,
        maxOutputSize: Int
    ): Int

    private external fun nativeReleaseEncoder(encoderHandle: Long)
}