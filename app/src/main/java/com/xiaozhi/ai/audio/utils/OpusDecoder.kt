package com.xiaozhi.ai.audio.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Opus解码器
 *
 * 负责将Opus音频数据解码为PCM格式：
 * 1. 加载原生库
 * 2. 初始化Opus解码器
 * 3. 解码Opus数据为PCM格式
 * 4. 释放解码器资源
 */
class OpusDecoder(
    private val sampleRate: Int, // 采样率
    private val channels: Int, // 声道数
    frameSizeMs: Int // 帧时长（毫秒）
) {
    companion object {
        private const val TAG = "OpusDecoder"

        init {
            System.loadLibrary("app") // 加载原生库
        }
    }

    private var nativeDecoderHandle: Long = 0 // 原生解码器句柄
    private val frameSize: Int = (sampleRate * frameSizeMs) / 1000 // 帧大小（采样点数）

    init {
        // 初始化原生解码器
        nativeDecoderHandle = nativeInitDecoder(sampleRate, channels)
        if (nativeDecoderHandle == 0L) {
            throw IllegalStateException("Failed to initialize Opus decoder")
        }
    }

    /**
     * 解码Opus数据为PCM格式
     *
     * 使用协程进行解码，运行在 IO 线程
     *
     * @param opusData Opus音频数据
     * @return 解码后的PCM数据，失败时返回null
     */
    suspend fun decode(opusData: ByteArray): ByteArray? = withContext(Dispatchers.IO) {
        val maxPcmSize = frameSize * channels * 2 // 16-bit PCM，计算最大PCM大小
        val pcmBuffer = ByteArray(maxPcmSize) // 创建PCM缓冲区

        val decodedBytes = nativeDecodeBytes(
            nativeDecoderHandle, // 解码器句柄
            opusData, // 输入Opus数据
            opusData.size, // 输入数据大小
            pcmBuffer, // 输出PCM缓冲区
            maxPcmSize // 最大输出大小
        )

        if (decodedBytes > 0) {
            if (decodedBytes < pcmBuffer.size) {
                pcmBuffer.copyOf(decodedBytes) // 复制有效数据
            } else {
                pcmBuffer
            }
        } else {
            Log.e(TAG, "Failed to decode frame")
            null
        }
    }

    /**
     * 释放解码器资源
     */
    fun release() {
        if (nativeDecoderHandle != 0L) {
            nativeReleaseDecoder(nativeDecoderHandle) // 释放原生解码器
            nativeDecoderHandle = 0 // 清空句柄
        }
    }

    protected fun finalize() {
        release() // 释放资源
    }

    // 原生方法声明
    private external fun nativeInitDecoder(sampleRate: Int, channels: Int): Long
    private external fun nativeDecodeBytes(
        decoderHandle: Long,
        inputBuffer: ByteArray,
        inputSize: Int,
        outputBuffer: ByteArray,
        maxOutputSize: Int
    ): Int

    private external fun nativeReleaseDecoder(decoderHandle: Long)
}