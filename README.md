# Moto Edge 息屏呼吸灯通知助手 (Motorola Edge 2026 Edition)

专为美版 Motorola Edge 2026 (pOLED 屏幕) 打造的息屏通知呼吸指示灯工具。当有系统或软件通知时，在息屏纯黑背景下于屏幕左上角呈现柔和呼吸动态绿光。

---

## 🚀 方式一：Android Studio 本地 1 分钟一键生成 APK（最推荐）

1. 在电脑上下载并打开免费的 [Android Studio](https://developer.android.com/studio)
2. 点击 **Open**，选中解压后的本项目根目录
3. 等待几秒钟依赖同步（Gradle Sync 完成）
4. 点击顶部菜单栏：
   👉 **`Build` -> `Build Bundle(s) / APK(s)` -> `Build APK(s)`**
5. 编译完成后，右下角弹出提示，点击 **locate**，即可得到 `app-debug.apk`
6. 通过微信传输、数据线或 Google Drive 发送到 Motorola edge 手机上安装即可！

---

## ☁️ 方式二：免装环境云端自动打包（GitHub Actions）

1. 将本代码推送到您的任意 GitHub 仓库（或 Fork）
2. 打开仓库顶部的 **Actions** 标签页
3. GitHub 的云端服务器会自动运行并编译 Android APK（通常需要 2 分钟）
4. 编译完成后，在 Actions 任务详情页底部点击下载 **`moto-breathing-led-debug-apk.zip`**，解压后手机直接安装！

---

## 📱 Motorola Edge 美版手机关键设置（防杀后台 & 权限开启）

Motorola 手机系统（基于近原生 MyUX / Hello UI）安全性较高，请确保开启以下两项：
1. **通知使用权 (Notification Access)**：
   打开手机 **Settings -> Apps -> Special app access -> Notification access** -> 勾选允许 **MotoBreathingLED**。
2. **在其他应用上层显示 (Display over other apps)**：
   打开手机 **Settings -> Apps -> Special app access -> Display over other apps** -> 勾选允许。
3. **电池无限制后台运行 (Unrestricted Battery)**：
   长按桌面 App 图标 -> 点击 (i) App Info -> Battery -> 选择 **Unrestricted**（避免息屏后被系统休眠清理）。