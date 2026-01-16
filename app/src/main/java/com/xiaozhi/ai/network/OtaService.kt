package com.xiaozhi.ai.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * OTA服务类
 *
 * 负责处理设备信息上报和获取OTA（Over-The-Air）更新信息：
 * 1. 向OTA服务器发送设备信息
 * 2. 获取服务器响应（包括WebSocket连接信息、固件更新信息等）
 * 3. 处理设备激活流程
 *
 * 使用OkHttpClient进行网络请求，Kotlinx Serialization进行JSON序列化
 */
class OtaService {
    companion object {
        // 日志标签
        private const val TAG = "OtaService"
        // 网络请求超时时间（秒）
        private const val TIMEOUT_SECONDS = 30L
    }

    // JSON序列化器配置
    private val json = Json {
        ignoreUnknownKeys = true  // 忽略未知字段
        encodeDefaults = true     // 编码默认值
    }

    // HTTP客户端配置
    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)  // 连接超时
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)     // 读取超时
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)    // 写入超时
        .build()

    /**
     * 向服务器上报设备信息并获取OTA响应
     *
     * 发送设备信息到OTA服务器，并获取服务器响应：
     * 1. 构建设备信息请求体
     * 2. 发送POST请求到OTA服务器
     * 3. 解析服务器响应
     * 4. 返回Result对象表示成功或失败
     *
     * @param clientId 客户端ID（UUID）
     * @param deviceId 设备ID（MAC地址）
     * @param otaUrl OTA服务器地址，如果为null或空则使用默认值
     * @return Result对象，成功时包含OtaResponse，失败时包含异常
     */
    suspend fun reportDeviceAndGetOta(clientId: String, deviceId: String, otaUrl: String? = null): Result<OtaResponse> {
        return withContext(Dispatchers.IO) {
            try {
                // 确定OTA服务器URL
                val url = otaUrl?.takeIf { it.isNotBlank() } ?: ""
                // 创建设备上报请求数据
                val deviceRequest = createDeviceReportRequest(clientId, deviceId)
                // 将请求数据序列化为JSON字符串
                val requestBodyString = json.encodeToString(DeviceReportRequest.serializer(), deviceRequest)
                // 创建请求体
                val requestBody = requestBodyString.toRequestBody("application/json".toMediaType())

                // 构建HTTP请求
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .addHeader("Client-Id", clientId)      // 客户端ID头部
                    .addHeader("Device-Id", deviceId)      // 设备ID头部
                    .addHeader("Content-Type", "application/json") // 内容类型头部
                    .build()

                // 记录请求日志
                Log.d(TAG, "发送OTA请求到: $url")
                Log.d(TAG, "请求头 - Client-Id: $clientId, Device-Id: $deviceId")
                Log.d(TAG, "请求体数据: $requestBodyString")

                // 执行网络请求
                val response = client.newCall(request).execute()

                // 记录响应日志
                Log.d(TAG, "收到响应 - 状态码: ${response.code}, 消息: ${response.message}")
                Log.d(TAG, "响应头: ${response.headers}")

                // 处理响应结果
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        Log.d(TAG, "OTA响应成功 - 状态码: ${response.code}")
                        Log.d(TAG, "响应体数据: $responseBody")
                        // 反序列化响应体为OtaResponse对象
                        val otaResponse = json.decodeFromString(OtaResponse.serializer(), responseBody)
                        Log.d(TAG, "解析后的OTA响应对象: $otaResponse")
                        Result.success(otaResponse)
                    } else {
                        Log.e(TAG, "OTA响应体为空 - 状态码: ${response.code}")
                        Result.failure(Exception("响应体为空"))
                    }
                } else {
                    // 处理错误响应
                    val errorBody = response.body?.string() ?: "未知错误"
                    Log.e(TAG, "OTA请求失败 - 状态码: ${response.code}, 消息: ${response.message}")
                    Log.e(TAG, "错误响应体: $errorBody")
                    Result.failure(Exception("HTTP ${response.code}: $errorBody"))
                }
            } catch (e: Exception) {
                // 处理异常情况
                Log.e(TAG, "OTA请求异常", e)
                Result.failure(e)
            }
        }
    }

    /**
     * 创建设备上报请求数据 - 针对非ESP32设备的最小请求
     *
     * 创建设备信息上报请求数据，包含应用信息和设备信息：
     * 1. 应用版本和标识信息
     * 2. 设备类型和网络信息
     *
     * @param clientId 客户端ID
     * @param deviceId 设备ID
     * @return 设备上报请求数据对象
     */
    private fun createDeviceReportRequest(clientId: String, deviceId: String): DeviceReportRequest {
        return DeviceReportRequest(
            application = DeviceReportRequest.Application(
                version = "2.0.0",  // 应用版本
                elfSha256 = "c8a8ecb6d6fbcda682494d9675cd1ead240ecf38bdde75282a42365a0e396033" // 应用SHA256哈希
            ),
            board = DeviceReportRequest.BoardInfo(
                type = "wifi",           // 标识为Wi-Fi设备类型
                name = "xiaozhi-android", // 设备名称
                ssid = "卧室",            // Wi-Fi SSID，如需要可从系统获取
                rssi = -55,              // Wi-Fi信号强度，如需要可从系统获取
                channel = 1,             // Wi-Fi频道，如需要可从系统获取
                ip = "192.168.1.11",     // IP地址，如需要可从系统获取
                mac = deviceId           // 使用设备ID作为MAC地址
            )
        )
    }
}