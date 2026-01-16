package com.xiaozhi.ai

import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.xiaozhi.ai.data.ConfigManager
import com.xiaozhi.ai.data.XiaozhiConfig
import java.util.*

class SettingsActivity : AppCompatActivity() {
    private lateinit var configManager: ConfigManager
    private lateinit var currentConfig: XiaozhiConfig

    // UI组件
    private lateinit var nameEditText: EditText
    private lateinit var otaUrlEditText: EditText
    private lateinit var websocketUrlEditText: EditText
    private lateinit var macAddressEditText: EditText
    private lateinit var tokenEditText: EditText
    private lateinit var saveButton: Button
    private lateinit var generateMacButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settings_main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 初始化配置管理器
        configManager = ConfigManager(this)
        currentConfig = configManager.loadConfig()

        // 初始化UI组件
        initUI()

        // 加载配置数据到UI
        loadConfigToUI()
    }

    private fun initUI() {
        // 获取UI组件
        nameEditText = findViewById(R.id.name_edit_text)
        otaUrlEditText = findViewById(R.id.ota_url_edit_text)
        websocketUrlEditText = findViewById(R.id.websocket_url_edit_text)
        macAddressEditText = findViewById(R.id.mac_address_edit_text)
        tokenEditText = findViewById(R.id.token_edit_text)
        saveButton = findViewById(R.id.save_button)
        generateMacButton = findViewById(R.id.generate_mac_button)

        // 设置ActionBar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "设置"

        // 设置按钮点击事件
        saveButton.setOnClickListener {
            saveConfig()
        }

        generateMacButton.setOnClickListener {
            generateNewMacAddress()
        }
    }

    private fun loadConfigToUI() {
        nameEditText.setText(currentConfig.name)
        otaUrlEditText.setText(currentConfig.otaUrl)
        websocketUrlEditText.setText(currentConfig.websocketUrl)
        macAddressEditText.setText(currentConfig.macAddress)
        tokenEditText.setText(currentConfig.token)
    }

    private fun saveConfig() {
        // 获取UI中的配置数据
        val name = nameEditText.text.toString().trim()
        val otaUrl = otaUrlEditText.text.toString().trim()
        val websocketUrl = websocketUrlEditText.text.toString().trim()
        val macAddress = macAddressEditText.text.toString().trim()
        val token = tokenEditText.text.toString().trim()

        // 创建新的配置对象
        val newConfig = XiaozhiConfig(
            id = currentConfig.id,
            name = name,
            otaUrl = otaUrl,
            websocketUrl = websocketUrl,
            macAddress = macAddress,
            uuid = currentConfig.uuid,
            token = token,
            mcpEnabled = currentConfig.mcpEnabled,
            mcpServers = currentConfig.mcpServers
        )

        // 验证配置
        if (configManager.isConfigComplete(newConfig)) {
            // 保存配置
            configManager.saveConfig(newConfig)
            Toast.makeText(this, "配置保存成功", Toast.LENGTH_SHORT).show()
            // 设置结果码，通知调用者配置已更改
            setResult(RESULT_OK)
            finish()
        } else {
            // 显示缺失字段
            val missingFields = configManager.getMissingFields(newConfig)
            val message = "请填写以下必填项：\n${missingFields.joinToString("、")}"
            showValidationDialog(message)
        }
    }

    private fun generateNewMacAddress() {
        // 生成新的随机MAC地址
        val newMacAddress = (1..6).joinToString(":") {
            "%02x".format((0..255).random())
        }
        macAddressEditText.setText(newMacAddress)
    }

    private fun showValidationDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("配置验证")
            .setMessage(message)
            .setPositiveButton("确定") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}