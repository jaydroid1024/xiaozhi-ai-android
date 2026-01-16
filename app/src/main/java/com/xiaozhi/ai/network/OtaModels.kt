package com.xiaozhi.ai.network

import android.annotation.SuppressLint
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * OTA设备上报请求数据类
 *
 * 用于向OTA服务器上报设备信息的请求数据结构：
 * 1. 应用信息（版本、标识等）
 * 2. 设备信息（类型、网络状态等）
 *
 * 使用Kotlinx Serialization进行序列化
 */
@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class DeviceReportRequest(
    @SerialName("application")
    val application: Application,  // 应用信息

    @SerialName("board")
    val board: BoardInfo           // 设备信息
) {

    /**
     * 应用信息数据类
     *
     * 包含应用的版本信息和标识信息
     *
     * @property version 应用版本号
     * @property elfSha256 应用ELF文件的SHA256哈希值
     */
    @Serializable
    data class Application(
        @SerialName("version")
        val version: String,          // 应用版本号

        @SerialName("elf_sha256")
        val elfSha256: String         // 应用ELF文件的SHA256哈希值
    )

    /**
     * 设备信息数据类
     *
     * 包含设备的类型、网络状态等信息
     *
     * @property type 设备类型
     * @property name 设备名称
     * @property ssid Wi-Fi网络名称
     * @property rssi Wi-Fi信号强度
     * @property channel Wi-Fi频道
     * @property ip IP地址
     * @property mac MAC地址
     */
    @Serializable
    data class BoardInfo(
        @SerialName("type")
        val type: String,             // 设备类型

        @SerialName("name")
        val name: String? = null,     // 设备名称

        @SerialName("ssid")
        val ssid: String,             // Wi-Fi网络名称

        @SerialName("rssi")
        val rssi: Int,                // Wi-Fi信号强度

        @SerialName("channel")
        val channel: Int,             // Wi-Fi频道

        @SerialName("ip")
        val ip: String,               // IP地址

        @SerialName("mac")
        val mac: String               // MAC地址
    )
}

/**
 * OTA响应数据类
 *
 * OTA服务器返回的响应数据结构：
 * 1. 服务器时间信息
 * 2. 设备激活信息（首次激活时返回）
 * 3. 固件更新信息
 * 4. WebSocket连接信息
 *
 * 使用Kotlinx Serialization进行反序列化
 */
@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class OtaResponse(
    @SerialName("server_time")
    val serverTime: ServerTime,           // 服务器时间信息

    @SerialName("activation")
    val activation: Activation? = null,   // 设备激活信息，只有在第一次激活时才会返回

    @SerialName("firmware")
    val firmware: Firmware,               // 固件信息

    @SerialName("websocket")
    val websocket: WebSocket              // WebSocket连接信息
) {
    /**
     * 服务器时间信息数据类
     *
     * 包含服务器的时间信息和时区信息
     *
     * @property timestamp 服务器时间戳
     * @property timeZone 时区名称
     * @property timezoneOffset 时区偏移量
     */
    @Serializable
    data class ServerTime(
        @SerialName("timestamp")
        val timestamp: Long,              // 服务器时间戳

        @SerialName("timeZone")
        val timeZone: String? = null,     // 时区名称

        @SerialName("timezone_offset")
        val timezoneOffset: Int           // 时区偏移量
    )

    /**
     * 设备激活信息数据类
     *
     * 包含设备首次激活时所需的信息：
     * 1. 激活码
     * 2. 激活消息
     * 3. 挑战码
     *
     * @property code 激活码
     * @property message 激活消息
     * @property challenge 挑战码
     */
    @Serializable
    data class Activation(
        @SerialName("code")
        val code: String,                 // 激活码

        @SerialName("message")
        val message: String,              // 激活消息

        @SerialName("challenge")
        val challenge: String             // 挑战码
    )

    /**
     * 固件信息数据类
     *
     * 包含固件版本和下载地址信息
     *
     * @property version 固件版本号
     * @property url 固件下载地址
     */
    @Serializable
    data class Firmware(
        @SerialName("version")
        val version: String,              // 固件版本号

        @SerialName("url")
        val url: String                   // 固件下载地址
    )

    /**
     * WebSocket连接信息数据类
     *
     * 包含WebSocket服务器连接地址
     *
     * @property url WebSocket服务器地址
     */
    @Serializable
    data class WebSocket(
        @SerialName("url")
        val url: String                   // WebSocket服务器地址
    )
}