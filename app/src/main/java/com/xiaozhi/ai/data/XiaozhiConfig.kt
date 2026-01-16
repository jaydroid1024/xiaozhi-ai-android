package com.xiaozhi.ai.data

/**
 * 小智配置数据类
 *
 * 存储小智AI应用的所有配置信息：
 * 1. 设备标识信息（ID、名称、MAC地址、UUID）
 * 2. 服务器连接信息（OTA地址、WebSocket地址）
 * 3. 认证信息（Token）
 * 4. MCP服务器配置
 *
 * @property id 配置ID
 * @property name 设备名称
 * @property otaUrl OTA服务器地址
 * @property websocketUrl WebSocket服务器地址
 * @property macAddress 设备MAC地址
 * @property uuid 设备UUID
 * @property token 访问令牌
 * @property mcpEnabled MCP功能是否启用
 * @property mcpServers MCP服务器列表
 */
data class XiaozhiConfig(
    val id: String,
    val name: String,
    val otaUrl: String,
    val websocketUrl: String = "",
    val macAddress: String,
    val uuid: String,
    val token: String,
    val mcpEnabled: Boolean = false,
    val mcpServers: List<McpServer> = emptyList()
) {
    companion object {
        /**
         * 创建默认配置
         *
         * 创建一个包含默认值的配置对象，用于首次启动应用或配置丢失时使用
         *
         * @return 默认配置对象
         */
        fun createDefault(): XiaozhiConfig {
            return XiaozhiConfig(
                id = "default",
                name = "测试",
                otaUrl = "",
                websocketUrl = "",
                macAddress = "",
                uuid = generateRandomUuid(),
                token = "test-token",
                mcpEnabled = false,
                mcpServers = listOf(
                    McpServer("示例MCP服务器", "ws://example.com/mcp", false)
                )
            )
        }

        /**
         * 生成随机MAC地址
         *
         * 生成一个格式为XX:XX:XX:XX:XX:XX的随机MAC地址
         *
         * @return 随机生成的MAC地址
         */
        private fun generateRandomMacAddress(): String {
            val random = java.util.Random()
            val mac = ByteArray(6)
            random.nextBytes(mac)
            return mac.joinToString(":") { "%02X".format(it) }
        }

        /**
         * 生成随机UUID
         *
         * 生成一个随机的UUID字符串
         *
         * @return 随机生成的UUID
         */
        fun generateRandomUuid(): String {
            return java.util.UUID.randomUUID().toString()
        }
    }
}

/**
 * MCP服务器配置数据类
 *
 * 存储MCP（Multi-Client Protocol）服务器的配置信息
 *
 * @property name 服务器名称
 * @property url 服务器地址
 * @property enabled 服务器是否启用
 */
data class McpServer(
    val name: String,
    val url: String,
    val enabled: Boolean = true
)