package com.example.subtitleblocker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

class OverlayService : Service() {

    companion object {
        const val ACTION_STOP = "com.example.subtitleblocker.action.STOP"
        const val ACTION_UPDATE = "com.example.subtitleblocker.action.UPDATE"
        const val ACTION_UPDATE_COUNT = "com.example.subtitleblocker.action.UPDATE_COUNT"
        const val ACTION_TOGGLE_PASS = "com.example.subtitleblocker.action.TOGGLE_PASS"

        private const val CHANNEL_ID = "subtitle_blocker_channel"
        private const val NOTIF_ID = 1001

        @Volatile
        var isRunning = false
            private set

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, OverlayService::class.java)
            )
        }
    }

    private lateinit var windowManager: WindowManager
    private val blockList = mutableListOf<BlockHolder>()

    private var passThrough = false
    private var started = false

    inner class BlockHolder(val index: Int) {
        lateinit var rootView: FrameLayout
        lateinit var params: WindowManager.LayoutParams
        var blockView: View? = null
        var isLocked = false
    }

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_UPDATE -> {
                    blockList.forEach { updateBlockSize(it) }
                }
                ACTION_TOGGLE_PASS -> togglePassThrough()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val filter = IntentFilter().apply {
            addAction(ACTION_UPDATE)
            addAction(ACTION_TOGGLE_PASS)
        }
        ContextCompat.registerReceiver(
            this,
            updateReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        // 拦截数量更新指令
        if (intent?.action == ACTION_UPDATE_COUNT) {
            val targetCount = getSharedPreferences("settings", MODE_PRIVATE).getInt("block_count", 1)
            syncBlockCount(targetCount)
            return START_STICKY
        }

        if (!started) {
            started = true
            isRunning = true
            createNotificationChannel()
            startForegroundCompat()

            val initialCount = getSharedPreferences("settings", MODE_PRIVATE).getInt("block_count", 1)
            syncBlockCount(initialCount)
        }
        return START_STICKY
    }

    private fun syncBlockCount(target: Int) {
        if (target > blockList.size) {
            for (i in blockList.size until target) {
                createNewBlock(i)
            }
        } else if (target < blockList.size) {
            for (i in blockList.size - 1 downTo target) {
                val holder = blockList[i]
                try { windowManager.removeView(holder.rootView) } catch (_: Exception) {}
                blockList.removeAt(i)
            }
        }
    }

    private fun createNewBlock(index: Int) {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val initialWidth = prefs.getInt("width_px", (screenWidth * 0.8).toInt())
        val initialHeight = dp(80)

        val holder = BlockHolder(index)
        val root = FrameLayout(this)
        holder.rootView = root

        val block = View(this)
        holder.blockView = block
        root.addView(block, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        applyBlockColor(holder)

        val resizeHandle = View(this)
        val handleSize = dp(32)
        resizeHandle.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = handleSize / 2f
            setColor(Color.argb(150, 255, 255, 255))
        }
        val handleLp = FrameLayout.LayoutParams(handleSize, handleSize)
        handleLp.gravity = Gravity.END or Gravity.BOTTOM
        handleLp.rightMargin = dp(8)
        handleLp.bottomMargin = dp(8)
        root.addView(resizeHandle, handleLp)

        val lockBtn = ImageView(this)
        val lockBtnSize = dp(40)
        lockBtn.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = lockBtnSize / 2f
            setColor(Color.argb(100, 0, 0, 0))
        }
        lockBtn.setPadding(dp(8), dp(8), dp(8), dp(8))
        lockBtn.setImageResource(R.drawable.ic_lock_open)
        val lockLp = FrameLayout.LayoutParams(lockBtnSize, lockBtnSize)
        lockLp.gravity = Gravity.START or Gravity.TOP
        lockLp.leftMargin = dp(8)
        lockLp.topMargin = dp(8)
        root.addView(lockBtn, lockLp)

        // 限制起始 Y 坐标，防止多个色块超出屏幕
        val startX = (screenWidth - initialWidth) / 2
        val startY = (300 + (index * 120)).coerceAtMost(screenHeight - initialHeight)

        holder.params = WindowManager.LayoutParams(
            initialWidth, initialHeight,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = startX
            y = startY
        }

        lockBtn.setOnClickListener {
            holder.isLocked = !holder.isLocked
            if (holder.isLocked) {
                lockBtn.setImageResource(R.drawable.ic_lock)
                resizeHandle.visibility = View.GONE
            } else {
                lockBtn.setImageResource(R.drawable.ic_lock_open)
                resizeHandle.visibility = View.VISIBLE
            }
            applyBlockColor(holder)
        }

        var startXPos = 0
        var startYPos = 0
        var startTouchX = 0f
        var startTouchY = 0f
        root.setOnTouchListener { _, event ->
            if (holder.isLocked) return@setOnTouchListener true
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startXPos = holder.params.x
                    startYPos = holder.params.y
                    startTouchX = event.rawX
                    startTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - startTouchX).toInt()
                    val deltaY = (event.rawY - startTouchY).toInt()
                    holder.params.x = (startXPos + deltaX).coerceIn(0, screenWidth - holder.params.width)
                    holder.params.y = (startYPos + deltaY).coerceIn(0, screenHeight - holder.params.height)
                    safeUpdate(holder)
                    true
                }
                else -> false
            }
        }

        var startW = 0
        var startH = 0
        var startScaleTouchX = 0f
        var startScaleTouchY = 0f
        resizeHandle.setOnTouchListener { _, event ->
            if (holder.isLocked) return@setOnTouchListener true
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startW = holder.params.width
                    startH = holder.params.height
                    startScaleTouchX = event.rawX
                    startScaleTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - startScaleTouchX).roundToInt()
                    val deltaY = (event.rawY - startScaleTouchY).roundToInt()
                    holder.params.width = (startW + deltaX).coerceIn(dp(30), screenWidth - holder.params.x)
                    holder.params.height = (startH + deltaY).coerceIn(dp(24), screenHeight - holder.params.y)
                    safeUpdate(holder)
                    true
                }
                else -> false
            }
        }

        try {
            windowManager.addView(root, holder.params)
            blockList.add(holder)
        } catch (_: Exception) {
            // 忽略由于极少数情况下产生的系统窗口添加失败
        }
    }

    private fun updateBlockSize(holder: BlockHolder) {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        holder.params.height = dp(prefs.getInt("height_dp", 80))
        applyBlockColor(holder)
        safeUpdate(holder)
    }

    private fun applyBlockColor(holder: BlockHolder) {
        val currentAlpha = if (holder.isLocked) 255 else getSharedPreferences("settings", MODE_PRIVATE).getInt("alpha", 255)
        holder.blockView?.setBackgroundColor(Color.argb(currentAlpha, 0, 0, 0))
    }

    private fun safeUpdate(holder: BlockHolder) {
        if (!this::windowManager.isInitialized) return
        if (!holder.rootView.isAttachedToWindow) return
        try {
            windowManager.updateViewLayout(holder.rootView, holder.params)
        } catch (_: Exception) {}
    }

    private fun togglePassThrough() {
        passThrough = !passThrough
        blockList.forEach { holder ->
            if (passThrough) {
                holder.params.flags = holder.params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            } else {
                holder.params.flags = holder.params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            }
            safeUpdate(holder)
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_ID, buildNotification())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "屏幕遮挡", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "保持遮挡色块在屏幕上" }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun startForegroundCompat() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val stopPi = PendingIntent.getService(this, 0, Intent(this, OverlayService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val passPi = PendingIntent.getBroadcast(this, 1, Intent(ACTION_TOGGLE_PASS).setPackage(packageName), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val openPi = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("遮挡已开启")
            .setContentText(if (passThrough) "点击穿透：开（无法拖动）" else "点击穿透：关")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openPi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, if (passThrough) "关闭穿透" else "开启穿透", passPi)
            .addAction(0, "停止", stopPi)
            .build()
    }

    override fun onDestroy() {
        isRunning = false
        try { unregisterReceiver(updateReceiver) } catch (_: Exception) {}

        blockList.forEach {
            try { windowManager.removeView(it.rootView) } catch (_: Exception) {}
        }
        blockList.clear()

        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}