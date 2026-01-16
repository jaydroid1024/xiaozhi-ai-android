package com.xiaozhi.ai.data

import java.util.*

/**
 * 消息角色枚举
 *
 * 定义对话消息的不同角色类型：
 * 1. USER: 用户发送的消息
 * 2. ASSISTANT: AI助手回复的消息
 * 3. SYSTEM: 系统消息（如MCP消息等）
 */
enum class MessageRole {
    USER,      // 用户消息
    ASSISTANT, // 助手消息
    SYSTEM     // 系统消息
}

/**
 * 消息数据类
 *
 * 表示对话中的一条消息，包含消息的基本信息和内容：
 * 1. 唯一标识符
 * 2. 消息角色（用户、助手、系统）
 * 3. 消息内容
 * 4. 时间戳
 * 5. 是否为音频消息标识
 *
 * @property id 消息唯一标识符，默认使用UUID生成
 * @property role 消息角色，决定消息的发送方
 * @property content 消息内容文本
 * @property timestamp 消息时间戳，默认为当前系统时间
 * @property isAudio 是否为音频消息，默认为false
 */
data class Message(
    val id: String = UUID.randomUUID().toString(), // 消息唯一标识符
    val role: MessageRole,                         // 消息角色
    val content: String,                           // 消息内容
    val timestamp: Long = System.currentTimeMillis(), // 消息时间戳
    val isAudio: Boolean = false                   // 是否为音频消息
)