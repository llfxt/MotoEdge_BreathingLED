package com.moto.breathingled

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NotificationMonitorService : NotificationListenerService() {

    companion object {
        private const val TAG = "MotoNotifMonitor"
        var isServiceConnected = false
        val unreadNotificationKeys = HashSet<String>()
    }

    private var screenReceiver: BroadcastReceiver? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onListenerConnected() {
        super.onListenerConnected()
        isServiceConnected = true
        Log.d(TAG, "NotificationListenerService connected successfully")
        registerScreenReceiver()
        queryExistingNotifications()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isServiceConnected = false
        unregisterScreenReceiver()
    }

    private fun registerScreenReceiver() {
        if (screenReceiver != null) return
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        // 手机屏幕刚刚熄灭或锁屏超时
                        if (unreadNotificationKeys.isNotEmpty()) {
                            mainHandler.postDelayed({
                                triggerBreathingLed()
                            }, 350)
                        }
                    }
                    Intent.ACTION_USER_PRESENT -> {
                        // 用户解锁进入桌面
                        stopBreathingLed()
                    }
                }
            }
        }
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

    private fun unregisterScreenReceiver() {
        try {
            if (screenReceiver != null) {
                unregisterReceiver(screenReceiver)
                screenReceiver = null
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val key = sbn.key ?: return

        // 过滤系统自身持续性常驻通知（如输入法、音乐控制器、正在充电等）以及本应用通知
        if (!sbn.isOngoing && sbn.packageName != packageName) {
            unreadNotificationKeys.add(key)
            Log.d(TAG, "New notification from: ${sbn.packageName}, total unread: ${unreadNotificationKeys.size}")

            // 检查当前是否处于息屏状态
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val isScreenOff = !pm.isInteractive

            if (isScreenOff) {
                mainHandler.postDelayed({
                    triggerBreathingLed()
                }, 300)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn == null) return

        val key = sbn.key ?: return
        unreadNotificationKeys.remove(key)
        Log.d(TAG, "Notification removed: ${sbn.packageName}, remaining: ${unreadNotificationKeys.size}")

        if (unreadNotificationKeys.isEmpty()) {
            stopBreathingLed()
        }
    }

    private fun queryExistingNotifications() {
        try {
            val sbns = activeNotifications
            if (sbns != null) {
                for (sbn in sbns) {
                    if (!sbn.isOngoing && sbn.packageName != packageName) {
                        sbn.key?.let { unreadNotificationKeys.add(it) }
                    }
                }
            }
            Log.d(TAG, "Queried existing unread notifications: ${unreadNotificationKeys.size}")
        } catch (e: Throwable) {
            Log.e(TAG, "Error querying active notifications", e)
        }
    }

    private fun triggerBreathingLed() {
        Log.d(TAG, "triggerBreathingLed: starting AOD and Overlay service")

        // 1. 启动全屏置顶于锁屏之上的 AOD 呼吸灯 Activity
        AodLedActivity.start(this)

        // 2. 同时启动常驻前台服务保活
        val intent = Intent(this, LedOverlayService::class.java).apply {
            action = LedOverlayService.ACTION_SHOW_LED
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Throwable) {
            try {
                startService(intent)
            } catch (e2: Throwable) {
                Log.e(TAG, "Failed to start LedOverlayService", e2)
            }
        }
    }

    private fun stopBreathingLed() {
        Log.d(TAG, "stopBreathingLed: closing AOD")
        AodLedActivity.stop(this)

        val intent = Intent(this, LedOverlayService::class.java).apply {
            action = LedOverlayService.ACTION_HIDE_LED
        }
        try {
            startService(intent)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to stop LedOverlayService", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterScreenReceiver()
    }
}