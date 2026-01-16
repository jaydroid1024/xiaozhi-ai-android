package com.xiaozhi.ai.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 应用排版样式定义
 *
 * 定义应用中使用的字体样式和排版规范：
 * 1. 正文大号字体样式
 * 2. 可扩展的其他字体样式
 */
val Typography = Typography(
    // 正文大号字体样式
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,    // 默认字体族
        fontWeight = FontWeight.Normal,     // 字体粗细
        fontSize = 16.sp,                   // 字体大小
        lineHeight = 24.sp,                 // 行高
        letterSpacing = 0.5.sp              // 字符间距
    )
    /* 其他可覆盖的默认文本样式
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
    */
)