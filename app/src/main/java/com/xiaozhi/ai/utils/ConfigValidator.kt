package com.xiaozhi.ai.utils

import android.content.Context
import com.xiaozhi.ai.data.ConfigManager

/**
 * 配置验证工具类
 *
 * 提供配置验证相关的功能：
 * 1. 检查是否需要跳转到设置页面
 * 2. 验证配置是否完整
 * 3. 获取当前配置
 *
 * @param context Android上下文，用于访问配置管理器
 */
class ConfigValidator(private val context: Context) {
    // 配置管理器实例
    private val configManager = ConfigManager(context)

    /**
     * 检查是否需要跳转到设置页面
     *
     * 检查必要的配置项是否已填写，如果OTA地址和WebSocket地址都为空，
     * 则需要跳转到设置页面进行配置
     *
     * @return true 如果需要跳转到设置页面，false 如果配置完整可以继续
     */
    fun shouldNavigateToSettings(): Boolean {
        val config = configManager.loadConfig()
        return config.otaUrl.isBlank() || config.websocketUrl.isBlank()
    }

    /**
     * 检查配置是否完整
     *
     * 使用配置管理器验证当前配置是否包含所有必需的字段
     *
     * @return true表示配置完整，false表示配置不完整
     */
    fun isConfigComplete(): Boolean {
        val config = configManager.loadConfig()
        return configManager.isConfigComplete(config)
    }

    /**
     * 获取当前配置
     *
     * 从配置管理器加载当前配置
     *
     * @return 当前配置对象
     */
    fun getCurrentConfig() = configManager.loadConfig()
}