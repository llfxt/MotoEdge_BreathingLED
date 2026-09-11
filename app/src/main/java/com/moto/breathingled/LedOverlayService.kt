package com.moto.breathingled

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator

class LedOverlayService : Service() {

    companion object {
        const val TAG = "LedOverlayService"
        const val ACTION_SHOW_LED = "com.moto.breathingled.ACTION_SHOW"
        const val ACTION_HIDE_LED = "com.moto.breathingled.ACTION_HIDE"
        const val ACTION_START_MONITORING = "com.moto.breathingled.ACTION_START_MONITORING"
        private const val CHANNEL_ID = "moto_breathing_led_channel"
        private const val NOTIF_ID = 1001
    }

    private var windowManager: WindowManager? = null
    private var overlayView: BreathingDotView? = null
    private var isOverlayShowing = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_USER_PRESENT -> {
                    // 用户已解锁进入系统桌面，退散呼吸灯
                    hideOverlay()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    // 屏幕熄灭且存在未读通知时，延迟激活呼吸灯全黑覆盖层
                    if (NotificationMonitorService.unreadNotificationKeys.isNotEmpty()) {
                        mainHandler.postDelayed({
                            showOverlay()
                        }, 300)
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        try {
            registerReceiver(screenReceiver, filter)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        safeStartForeground()
        when (intent?.action) {
            ACTION_SHOW_LED -> {
                showOverlay()
            }
            ACTION_HIDE_LED -> {
                hideOverlay()
            }
            ACTION_START_MONITORING -> {
                // 仅维持服务常驻以监听息屏广播
            }
        }
        return START_STICKY
    }

    private fun safeStartForeground() {
        try {
            val notification = createForegroundNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIF_ID, notification)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "safeStartForeground warning: ${e.message}")
        }
    }

    private fun showOverlay() {
        if (isOverlayShowing) return

        try {
            // 短暂点亮 OLED 屏幕以显示全黑背景及呼吸圆点
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (wakeLock == null) {
                wakeLock = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "moto:BreathingLedWake"
                )
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(2500)
            }

            val prefs = LedPreferences(this)
            val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }

            overlayView = BreathingDotView(this, prefs)
            // 用户点击屏幕任意位置，退出纯黑呼吸模式，恢复正常锁屏
            overlayView?.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    hideOverlay()
                }
                false
            }

            windowManager?.addView(overlayView, layoutParams)
            isOverlayShowing = true
            overlayView?.startBreathing()
            Log.d(TAG, "Breathing overlay successfully displayed")
        } catch (e: Throwable) {
            Log.e(TAG, "showOverlay error", e)
        }
    }

    private fun hideOverlay() {
        if (!isOverlayShowing) return
        try {
            overlayView?.stopBreathing()
            if (overlayView != null) {
                windowManager?.removeView(overlayView)
                overlayView = null
            }
            isOverlayShowing = false
            Log.d(TAG, "Breathing overlay hidden")
        } catch (e: Throwable) {
            Log.e(TAG, "hideOverlay error", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hideOverlay()
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Moto 息屏呼吸灯",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持息屏呼吸灯待机常驻"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createForegroundNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Moto 息屏呼吸灯运行中")
            .setContentText("息屏时如检测到未读通知将自动闪烁左上角小圆点")
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setOngoing(true)
            .build()
    }
}