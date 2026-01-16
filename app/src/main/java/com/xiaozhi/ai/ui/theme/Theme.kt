package com.xiaozhi.ai.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * 深色主题颜色方案
 *
 * 定义应用在深色模式下的颜色方案，使用科技蓝色调
 */
val DarkColorScheme = darkColorScheme(
    primary = TechBlue80,        // 主色调
    secondary = TechBlueGrey80,  // 次色调
    tertiary = TechLightBlue80,  // 第三色调
    background = Color(0xFFFFFBFE),     // 背景色
    surface = Color(0xFFFFFBFE),        // 表面色
    surfaceVariant = Color(0xFFF5F5F5), // 表面色变体
    onPrimary = Color.White,            // 主色调上的文字颜色
    onSecondary = Color.White,          // 次色调上的文字颜色
    onTertiary = Color.White,           // 第三色调上的文字颜色
    onBackground = Color(0xFF1C1B1F),   // 背景色上的文字颜色
    onSurface = Color(0xFF1C1B1F),      // 表面色上的文字颜色
    onSurfaceVariant = Color(0xFF49454F), // 表面色变体上的文字颜色
    outline = Color(0xFF79747E),        // 轮廓线颜色
    error = Color(0xFFBA1A1A)           // 错误颜色
)

/**
 * 浅色主题颜色方案
 *
 * 定义应用在浅色模式下的颜色方案，使用科技蓝色调
 */
val LightColorScheme = lightColorScheme(
    primary = TechBlue40,        // 主色调
    secondary = TechBlueGrey40,  // 次色调
    tertiary = TechLightBlue40,  // 第三色调
    background = Color(0xFFFFFBFE),     // 背景色
    surface = Color(0xFFFFFBFE),        // 表面色
    surfaceVariant = Color(0xFFF5F5F5), // 表面色变体
    onPrimary = Color.White,            // 主色调上的文字颜色
    onSecondary = Color.White,          // 次色调上的文字颜色
    onTertiary = Color.White,           // 第三色调上的文字颜色
    onBackground = Color(0xFF1C1B1F),   // 背景色上的文字颜色
    onSurface = Color(0xFF1C1B1F),      // 表面色上的文字颜色
    onSurfaceVariant = Color(0xFF49454F), // 表面色变体上的文字颜色
    outline = Color(0xFF79747E),        // 轮廓线颜色
    error = Color(0xFFBA1A1A)           // 错误颜色
)

/**
 * 应用主题组合函数
 *
 * 提供应用的主题设置，支持深色/浅色模式切换和动态颜色：
 * 1. 根据系统设置或参数决定使用深色还是浅色主题
 * 2. 在Android 12+设备上支持动态颜色
 * 3. 设置状态栏颜色和图标亮度
 *
 * @param darkTheme 是否使用深色主题，默认根据系统设置决定
 * @param dynamicColor 是否使用动态颜色，Android 12+可用，默认为true
 * @param content 内容组合函数
 */
@Composable
fun YTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    // 根据条件选择颜色方案
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme  // 使用自定义深色主题
        else -> LightColorScheme      // 使用自定义浅色主题
    }

    // 获取当前视图
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // 设置状态栏颜色
            window.statusBarColor = TechBlue80.toArgb()
            // 设置状态栏图标亮度（深色主题时为浅色图标）
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkTheme
        }
    }

    // 应用Material主题
    MaterialTheme(
        colorScheme = colorScheme,   // 颜色方案
        typography = Typography,     // 排版样式
        content = content            // 内容
    )
}