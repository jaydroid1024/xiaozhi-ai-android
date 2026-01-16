package com.xiaozhi.ai.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 配置管理器
 *
 * 负责小智AI应用配置的本地存储和读取：
 * 1. 使用SharedPreferences存储配置数据
 * 2. 使用Gson进行配置对象的序列化和反序列化
 * 3. 提供配置完整性验证功能
 * 4. 提供缺失配置项检查功能
 *
 * @param context Android上下文，用于访问SharedPreferences
 */
class ConfigManager(private val context: Context) {
    // SharedPreferences实例，用于存储配置数据
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    // Gson实例，用于JSON序列化和反序列化
    private val gson = Gson()

    companion object {
        // SharedPreferences文件名
        private const val PREFS_NAME = "xiaozhi_config"
        // 配置数据在SharedPreferences中的键名
        private const val KEY_CONFIG = "config"
    }

    /**
     * 保存配置到本地存储
     *
     * 将XiaozhiConfig对象序列化为JSON字符串并保存到SharedPreferences中
     *
     * @param config 要保存的配置对象
     */
    fun saveConfig(config: XiaozhiConfig) {
        val configJson = gson.toJson(config)
        sharedPreferences.edit()
            .putString(KEY_CONFIG, configJson)
            .apply()
    }

    /**
     * 从本地存储读取配置
     *
     * 从SharedPreferences中读取配置JSON字符串并反序列化为XiaozhiConfig对象
     * 如果没有保存的配置或解析失败，则返回默认配置
     *
     * @return 读取到的配置对象或默认配置
     */
    fun loadConfig(): XiaozhiConfig {
        val configJson = sharedPreferences.getString(KEY_CONFIG, null)
        return if (configJson != null) {
            try {
                gson.fromJson(configJson, XiaozhiConfig::class.java)
            } catch (e: Exception) {
                XiaozhiConfig.createDefault()
            }
        } else {
            XiaozhiConfig.createDefault()
        }
    }

    /**
     * 检查配置是否完整
     *
     * 验证配置对象是否包含所有必需的字段：
     * 1. 设备名称不为空
     * 2. OTA地址或WebSocket地址至少有一个不为空
     * 3. MAC地址不为空
     * 4. Token不为空
     *
     * @param config 要检查的配置对象
     * @return true表示配置完整，false表示配置不完整
     */
    fun isConfigComplete(config: XiaozhiConfig): Boolean {
        return config.name.isNotBlank() &&
               (config.otaUrl.isNotBlank() || config.websocketUrl.isNotBlank()) &&
               config.macAddress.isNotBlank() &&
               config.token.isNotBlank()
    }

    /**
     * 获取缺失的配置项列表
     *
     * 检查配置对象中缺失的必需字段，并返回缺失字段的描述列表
     *
     * @param config 要检查的配置对象
     * @return 缺失字段的描述列表
     */
    fun getMissingFields(config: XiaozhiConfig): List<String> {
        val missingFields = mutableListOf<String>()

        if (config.name.isBlank()) missingFields.add("设备名称")
        if (config.otaUrl.isBlank() && config.websocketUrl.isBlank()) missingFields.add("OTA地址或WSS地址(至少填一个)")
        if (config.macAddress.isBlank()) missingFields.add("MAC地址")
        if (config.token.isBlank()) missingFields.add("Token")

        return missingFields
    }
}