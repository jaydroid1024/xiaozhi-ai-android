/**
 * 小智AI Android应用的主Activity
 *
 * 这是整个应用的入口点，负责初始化应用的主要组件和UI界面。
 * 使用Jetpack Compose构建现代化的用户界面，并启用边缘到边缘显示以提供更好的视觉体验。
 */
package com.xiaozhi.ai

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.xiaozhi.ai.ui.ConversationScreen
import com.xiaozhi.ai.ui.theme.DarkColorScheme
import com.xiaozhi.ai.ui.theme.YTheme

/**
 * 主Activity类，继承自ComponentActivity
 *
 * 负责应用的初始化和主界面的渲染：
 * 1. 启用边缘到边缘显示（enableEdgeToEdge）
 * 2. 设置深色主题
 * 3. 渲染对话界面（ConversationScreen）
 */
class MainActivity : ComponentActivity() {
    companion object {
        // 日志标签，用于调试信息输出
        private const val TAG = "MainActivity"
    }

    /**
     * Activity创建时的回调方法
     *
     * 在此方法中完成应用的初始化工作：
     * 1. 调用父类的onCreate方法
     * 2. 启用边缘到边缘显示模式
     * 3. 输出启动日志
     * 4. 设置Jetpack Compose内容
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 启用边缘到边缘显示，使内容可以延伸到状态栏和导航栏区域
        enableEdgeToEdge()

        // 输出应用启动日志，便于调试跟踪
        Log.d(TAG, "应用启动，开始初始化...")

        // 使用Jetpack Compose设置界面内容
        setContent {
            // 应用主题设置，这里强制使用深色主题
            YTheme(
                darkTheme = true // 强制使用深色主题，提供更好的夜间使用体验
            ) {
                // Surface组件作为界面容器，填充整个屏幕
                Surface(
                    modifier = Modifier.fillMaxSize(), // 填充最大可用空间
                    color = DarkColorScheme.background // 设置背景颜色为深色主题的背景色
                ) {
                    // 渲染主对话界面
                    ConversationScreen()
                }
            }
        }
    }
}