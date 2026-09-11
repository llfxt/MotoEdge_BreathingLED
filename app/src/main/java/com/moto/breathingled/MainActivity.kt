package com.moto.breathingled

import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: LedPreferences
    private lateinit var tvOffsetXValue: TextView
    private lateinit var sbOffsetX: SeekBar
    private lateinit var tvOffsetYValue: TextView
    private lateinit var sbOffsetY: SeekBar
    private lateinit var tvRadiusValue: TextView
    private lateinit var sbRadius: SeekBar
    private lateinit var tvDurationValue: TextView
    private lateinit var sbDuration: SeekBar
    private lateinit var vPreviewDot: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = LedPreferences(this)
        initViews()
        checkPermissions()
        startBackgroundMonitoring()
    }

    private fun startBackgroundMonitoring() {
        try {
            val intent = Intent(this, LedOverlayService::class.java).apply {
                action = LedOverlayService.ACTION_START_MONITORING
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun initViews() {
        tvOffsetXValue = findViewById(R.id.tvOffsetXValue)
        sbOffsetX = findViewById(R.id.sbOffsetX)
        tvOffsetYValue = findViewById(R.id.tvOffsetYValue)
        sbOffsetY = findViewById(R.id.sbOffsetY)
        tvRadiusValue = findViewById(R.id.tvRadiusValue)
        sbRadius = findViewById(R.id.sbRadius)
        tvDurationValue = findViewById(R.id.tvDurationValue)
        sbDuration = findViewById(R.id.sbDuration)
        vPreviewDot = findViewById(R.id.vPreviewDot)

        // 1. 水平 X 轴位置 (0dp - 100dp)
        sbOffsetX.max = 100
        sbOffsetX.progress = prefs.offsetXDp.toInt().coerceIn(0, 100)
        updateOffsetXLabel(prefs.offsetXDp.toInt())
        sbOffsetX.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                prefs.offsetXDp = progress.toFloat()
                updateOffsetXLabel(progress)
                updatePreview()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // X 轴微调按钮
        findViewById<Button>(R.id.btnXMinus).setOnClickListener {
            val current = prefs.offsetXDp.toInt()
            if (current > 0) {
                val newX = current - 1
                sbOffsetX.progress = newX
                prefs.offsetXDp = newX.toFloat()
                updateOffsetXLabel(newX)
                updatePreview()
            }
        }
        findViewById<Button>(R.id.btnXPlus).setOnClickListener {
            val current = prefs.offsetXDp.toInt()
            if (current < 100) {
                val newX = current + 1
                sbOffsetX.progress = newX
                prefs.offsetXDp = newX.toFloat()
                updateOffsetXLabel(newX)
                updatePreview()
            }
        }

        // 2. 垂直 Y 轴位置 (0dp - 100dp)
        sbOffsetY.max = 100
        sbOffsetY.progress = prefs.offsetYDp.toInt().coerceIn(0, 100)
        updateOffsetYLabel(prefs.offsetYDp.toInt())
        sbOffsetY.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                prefs.offsetYDp = progress.toFloat()
                updateOffsetYLabel(progress)
                updatePreview()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Y 轴微调按钮
        findViewById<Button>(R.id.btnYMinus).setOnClickListener {
            val current = prefs.offsetYDp.toInt()
            if (current > 0) {
                val newY = current - 1
                sbOffsetY.progress = newY
                prefs.offsetYDp = newY.toFloat()
                updateOffsetYLabel(newY)
                updatePreview()
            }
        }
        findViewById<Button>(R.id.btnYPlus).setOnClickListener {
            val current = prefs.offsetYDp.toInt()
            if (current < 100) {
                val newY = current + 1
                sbOffsetY.progress = newY
                prefs.offsetYDp = newY.toFloat()
                updateOffsetYLabel(newY)
                updatePreview()
            }
        }

        // 3. 半径大小滑块 (4dp - 24dp)
        sbRadius.max = 20
        sbRadius.progress = (prefs.dotRadiusDp - 4).toInt().coerceIn(0, 20)
        updateRadiusLabel(prefs.dotRadiusDp.toInt())
        sbRadius.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val newRadius = 4 + progress
                prefs.dotRadiusDp = newRadius.toFloat()
                updateRadiusLabel(newRadius)
                updatePreview()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 4. 呼吸周期滑块 (1.0s - 5.0s)
        sbDuration.max = 40
        val durationProgress = ((prefs.breathingDuration - 1.0f) * 10).toInt().coerceIn(0, 40)
        sbDuration.progress = durationProgress
        updateDurationLabel(prefs.breathingDuration)
        sbDuration.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val newSec = 1.0f + (progress / 10.0f)
                prefs.breathingDuration = newSec
                updateDurationLabel(newSec)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 5. 快捷颜色预设按钮
        findViewById<Button>(R.id.btnColorGreen).setOnClickListener { selectColor("#00FF41", "经典荧光绿") }
        findViewById<Button>(R.id.btnColorEmerald).setOnClickListener { selectColor("#10B981", "翡翠薄荷绿") }
        findViewById<Button>(R.id.btnColorCyan).setOnClickListener { selectColor("#06B6D4", "极光青蓝") }
        findViewById<Button>(R.id.btnColorAmber).setOnClickListener { selectColor("#F59E0B", "琥珀暖黄") }
        findViewById<Button>(R.id.btnColorRed).setOnClickListener { selectColor("#EF4444", "警示鲜红") }

        // 6. 权限检查按钮（采用动态容错解析，避免因布局版本不同导致编译失败）
        findViewByName<Button>("btnGrantNotif")?.setOnClickListener {
            openNotificationListenerSettings()
        }
        findViewByName<Button>("btnGrantFullScreen")?.setOnClickListener {
            openFullScreenIntentSettings()
        }
        findViewByName<Button>("btnGrantOverlay")?.setOnClickListener {
            openOverlaySettings()
        }
        findViewByName<Button>("btnBatteryOpt")?.setOnClickListener {
            openBatteryOptimizationSettings()
        }

        // 7. 测试息屏呼吸灯
        findViewByName<Button>("btnTestLed")?.setOnClickListener {
            NotificationMonitorService.unreadNotificationKeys.add("manual_test_key")
            Toast.makeText(this, "模拟通知已注入！请按下电源键熄屏，稍候即可看到呼吸圆点", Toast.LENGTH_LONG).show()
            startBackgroundMonitoring()
        }

        // 8. 立即全屏沉浸预览息屏呼吸灯（轻触屏幕任意位置即可退出）
        findViewByName<Button>("btnDirectAodPreview")?.setOnClickListener {
            AodLedActivity.start(this)
        }

        // 初始化实时预览小圆点
        updatePreview()
    }

    private inline fun <reified T : View> findViewByName(name: String): T? {
        val id = resources.getIdentifier(name, "id", packageName)
        return if (id != 0) findViewById<T>(id) else null
    }

    private fun updatePreview() {
        try {
            val density = resources.displayMetrics.density
            val radiusPx = (prefs.dotRadiusDp * density).toInt().coerceAtLeast(4)
            val sizePx = radiusPx * 2
            val offsetX = (prefs.offsetXDp * density).toInt()
            val offsetY = (prefs.offsetYDp * density).toInt()

            val params = FrameLayout.LayoutParams(sizePx, sizePx).apply {
                leftMargin = offsetX
                topMargin = offsetY
            }
            vPreviewDot.layoutParams = params
            vPreviewDot.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(prefs.dotColor))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun selectColor(hex: String, name: String) {
        prefs.dotColor = hex
        updatePreview()
        Toast.makeText(this, "已切换为：\$name (\$hex)", Toast.LENGTH_SHORT).show()
    }

    private fun updateOffsetXLabel(value: Int) {
        tvOffsetXValue.text = "\${value} dp"
    }

    private fun updateOffsetYLabel(value: Int) {
        tvOffsetYValue.text = "\${value} dp"
    }

    private fun updateRadiusLabel(value: Int) {
        tvRadiusValue.text = "\${value} dp"
    }

    private fun updateDurationLabel(value: Float) {
        tvDurationValue.text = String.format("%.1f s", value)
    }

    private fun checkPermissions() {
        if (!isNotificationListenerGranted()) {
            showPermissionDialog(
                title = "需要开启通知访问权限",
                message = "呼吸灯需要监听新消息（如微信、短信）以触发闪烁。请在接下来的系统设置列表中找到【Moto 息屏呼吸灯】并允许。",
                onPositive = { openNotificationListenerSettings() }
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            showPermissionDialog(
                title = "需要悬浮窗/顶层显示权限",
                message = "为了在息屏和锁屏时显示发光圆点，请允许在其他应用上层显示。",
                onPositive = { openOverlaySettings() }
            )
        }
    }

    private fun isNotificationListenerGranted(): Boolean {
        val packageName = packageName
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat?.contains(packageName) == true
    }

    private fun openNotificationListenerSettings() {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "无法自动跳转，请在系统设置中搜索：通知使用权", Toast.LENGTH_LONG).show()
        }
    }

    private fun openFullScreenIntentSettings() {
        if (Build.VERSION.SDK_INT >= 34) { // Android 14+
            try {
                val intent = Intent("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT").apply {
                    data = Uri.parse("package:\$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:\$packageName")
                    }
                    startActivity(intent)
                } catch (e2: Exception) {
                    Toast.makeText(this, "请前往：设置 > 应用 > 特殊应用权限 > 全屏意图/通知", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            Toast.makeText(this, "当前 Android 版本默认允许全屏意图通知", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openOverlaySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:\$packageName")
            )
            startActivity(intent)
        }
    }

    private fun openBatteryOptimizationSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:\$packageName")
            }
            startActivity(intent)
            Toast.makeText(this, "请点击【电池】> 选择【无限制 (Unrestricted)】", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "请前往系统设置 > 应用管理 > 电池设置无限制", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPermissionDialog(title: String, message: String, onPositive: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("立即前往设置") { _, _ -> onPositive() }
            .setNegativeButton("稍后") { dialog, _ -> dialog.dismiss() }
            .setCancelable(false)
            .show()
    }
}
