package com.example.subtitleblocker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private var blockCount = 1
    private lateinit var tvCount: TextView

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        startOverlay()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvCount = findViewById(R.id.tvCount)
        val btnMinus = findViewById<Button>(R.id.btnMinus)
        val btnPlus = findViewById<Button>(R.id.btnPlus)

        // 读取保存的数量
        blockCount = getSharedPreferences("settings", MODE_PRIVATE).getInt("block_count", 1)
        updateCountText()

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            ensureNotificationPermissionThenStart()
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopService(Intent(this, OverlayService::class.java))
        }

        // 减少按钮
        btnMinus.setOnClickListener {
            if (blockCount > 1) {
                blockCount--
                updateCountText()
                saveAndNotifyUpdate()
            }
        }

        // 增加按钮
        btnPlus.setOnClickListener {
            if (blockCount < 6) {
                blockCount++
                updateCountText()
                saveAndNotifyUpdate()
            }
        }
    }

    private fun updateCountText() {
        tvCount.text = "$blockCount 个色块"
    }

    private fun saveAndNotifyUpdate() {
        // 1. 保存到本地
        getSharedPreferences("settings", MODE_PRIVATE).edit()
            .putInt("block_count", blockCount)
            .apply()

        // 2. 如果服务已启动，直接给 Service 发送带指令的 Intent
        if (OverlayService.isRunning) {
            val intent = Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_UPDATE_COUNT
            }
            startService(intent)
        }
    }

    private fun ensureNotificationPermissionThenStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startOverlay()
        }
    }

    private fun startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
            return
        }
        OverlayService.start(this)
    }
}