package com.moto.breathingled

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager

/**
 * 息屏全屏纯黑 AOD 呼吸灯 Activity
 * 针对 Android 14 / 15 / 16 (API 36) 核心设计：
 * 1. 突破 Keyguard 锁屏：通过 setShowWhenLocked(true) 和 FLAG_SHOW_WHEN_LOCKED，将窗口直接置顶于锁屏之上
 * 2. 突破后台启动限制 (BAL)：在 Android 14/15/16 下，通过高优先级通道的 FullScreenIntent Notification 唤醒
 *    系统 Keyguard 接收到 setFullScreenIntent 后，直接将该 Activity 提至锁屏最顶层全屏呈现
 * 3. 唤醒点亮 OLED 屏幕：setTurnScreenOn(true)、FLAG_KEEP_SCREEN_ON 配合短暂 WakeLock 硬件亮屏
 * 4. 极致省电：整屏背景为 100% 纯黑 (#000000)，Motorola edge 的 pOLED 屏幕纯黑像素完全断电不发光
 * 5. 任意轻触退出：用户轻触屏幕任意位置或按下电源键即可立即退出，无缝回到系统锁屏/指纹解锁
 */
class AodLedActivity : Activity() {

    companion object {
        private const val TAG = "AodLedActivity"
        const val ACTION_CLOSE_AOD = "com.moto.breathingled.ACTION_CLOSE_AOD"
        private const val AOD_NOTIF_ID = 2026
        private const val AOD_CHANNEL_ID = "moto_aod_led_channel"
        var isAodActive = false

        fun start(context: Context) {
            val intent = Intent(context, AodLedActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
            }

            // 1. 针对 Android 14/15/16 (API 36)：使用 FullScreenIntent 突破系统后台启动活动限制(BAL)
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        AOD_CHANNEL_ID,
                        "息屏呼吸灯唤醒通道",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "用于在熄屏锁屏时唤醒呼吸灯界面 (Android 16 兼容)"
                        setSound(null, null)
                        enableVibration(false)
                        lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                    }
                    nm.createNotificationChannel(channel)
                }

                val pendingIntent = android.app.PendingIntent.getActivity(
                    context,
                    AOD_NOTIF_ID,
                    intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )

                val notifBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    android.app.Notification.Builder(context, AOD_CHANNEL_ID)
                } else {
                    @Suppress("DEPRECATION")
                    android.app.Notification.Builder(context)
                }

                val notification = notifBuilder
                    .setSmallIcon(android.R.drawable.stat_notify_chat)
                    .setContentTitle("息屏呼吸灯运行中")
                    .setPriority(android.app.Notification.PRIORITY_MAX)
                    .setCategory(android.app.Notification.CATEGORY_CALL)
                    .setFullScreenIntent(pendingIntent, true)
                    .setAutoCancel(true)
                    .build()

                nm.notify(AOD_NOTIF_ID, notification)
                Log.d(TAG, "Sent FullScreenIntent Notification for AOD wake-up")
            } catch (e: Throwable) {
                Log.e(TAG, "FullScreenIntent notification failed", e)
            }

            // 2. 硬件点亮 OLED 屏幕 WakeLock
            try {
                val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                val wakeLock = pm.newWakeLock(
                    android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "moto:AodScreenWakeLock"
                )
                wakeLock.acquire(2000)
            } catch (e: Throwable) {
                Log.w(TAG, "WakeLock acquire note: ${e.message}")
            }

            // 3. 辅助直启（拥有悬浮窗权限时受系统白名单保护）
            try {
                context.startActivity(intent)
                Log.d(TAG, "AodLedActivity direct startActivity invoked")
            } catch (e: Throwable) {
                Log.w(TAG, "Direct startActivity note: ${e.message}")
            }
        }

        fun stop(context: Context) {
            try {
                context.sendBroadcast(Intent(ACTION_CLOSE_AOD))
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    private lateinit var breathingView: BreathingDotView
    private lateinit var prefs: LedPreferences

    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_CLOSE_AOD, Intent.ACTION_USER_PRESENT -> {
                    finishAndRemoveTask()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isAodActive = true

        // 立即清除用于唤醒的通知，保持通知栏干净
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(AOD_NOTIF_ID)
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        // 突破锁屏并在息屏时点亮纯黑 OLED 屏幕（API 27+ 官方标准方案）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )

        // 适配刘海屏与前摄打孔区（全屏延伸到边框与孔洞边缘）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        hideSystemUI()

        prefs = LedPreferences(this)
        breathingView = BreathingDotView(this, prefs)
        breathingView.setBackgroundColor(Color.BLACK)
        setContentView(breathingView)

        // 轻触屏幕任意位置：立即退出息屏呼吸模式，回到原生系统锁屏（可进行指纹识别或看时间）
        breathingView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                finishAndRemoveTask()
            }
            false
        }

        val filter = IntentFilter().apply {
            addAction(ACTION_CLOSE_AOD)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        try {
            // Android 14/15/16 动态注册必须声明 RECEIVER_NOT_EXPORTED，避免 SecurityException
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(closeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(closeReceiver, filter)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        breathingView.startBreathing()
    }

    override fun onPause() {
        super.onPause()
        breathingView.stopBreathing()
    }

    override fun onDestroy() {
        super.onDestroy()
        isAodActive = false
        try {
            unregisterReceiver(closeReceiver)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
}