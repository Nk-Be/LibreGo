package com.zcshou.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Criteria
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.CountDownTimer
import android.provider.Settings
import android.view.Gravity
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.elvishew.xlog.XLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * System and Environment Utility functions for LibreGo in Kotlin.
 */
object GoUtils {

    @JvmStatic
    fun isDeveloperOptionsEnabled(context: Context): Boolean {
        return Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
            0
        ) == 1
    }

    @JvmStatic
    fun isWifiConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val nw = cm.activeNetwork ?: return false
        val actNw = cm.getNetworkCapabilities(nw) ?: return false
        return actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    @JvmStatic
    fun isWifiEnabled(context: Context): Boolean {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return false
        return wm.isWifiEnabled
    }

    @JvmStatic
    fun isMobileConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val nw = cm.activeNetwork ?: return false
        val actNw = cm.getNetworkCapabilities(nw) ?: return false
        return actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    @JvmStatic
    fun isNetworkConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val nw = cm.activeNetwork ?: return false
        val actNw = cm.getNetworkCapabilities(nw) ?: return false
        return actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)
    }

    @JvmStatic
    fun isNetworkAvailable(context: Context): Boolean {
        return (isWifiConnected(context) || isMobileConnected(context)) && isNetworkConnected(context)
    }

    @JvmStatic
    fun isGpsOpened(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    @SuppressLint("WrongConstant")
    @JvmStatic
    fun isAllowMockLocation(context: Context): Boolean {
        if (com.zcshou.service.MockLocationEngine.locationState.value.isMocking) {
            return true
        }
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return try {
            val providers = lm.allProviders
            if (providers.contains(LocationManager.GPS_PROVIDER)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    lm.addTestProvider(
                        LocationManager.GPS_PROVIDER, false, true, false,
                        false, true, true, true,
                        ProviderProperties.POWER_USAGE_HIGH, ProviderProperties.ACCURACY_FINE
                    )
                } else {
                    @Suppress("DEPRECATION")
                    lm.addTestProvider(
                        LocationManager.GPS_PROVIDER, false, true, false,
                        false, true, true, true,
                        Criteria.POWER_HIGH, Criteria.ACCURACY_FINE
                    )
                }
                lm.setTestProviderEnabled(LocationManager.GPS_PROVIDER, false)
                lm.removeTestProvider(LocationManager.GPS_PROVIDER)
                true
            } else {
                false
            }
        } catch (_: SecurityException) {
            false
        } catch (e: Exception) {
            XLog.e("isAllowMockLocation check error", e)
            false
        }
    }

    @JvmStatic
    fun getVersionName(context: Context): String {
        return try {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0)).versionName ?: "2.0.0"
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0).versionName ?: "2.0.0"
            }
        } catch (e: Exception) {
            "2.0.0"
        }
    }

    @JvmStatic
    fun getAppName(context: Context): String {
        return try {
            val pm = context.packageManager
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(context.packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(context.packageName, 0)
            }
            context.resources.getString(appInfo.labelRes)
        } catch (_: Exception) {
            "LibreGo"
        }
    }

    @JvmStatic
    fun timeStamp2Date(seconds: String?): String {
        if (seconds.isNullOrEmpty() || seconds == "null") return ""
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            sdf.format(Date(seconds.toLong() * 1000))
        } catch (_: Exception) {
            ""
        }
    }

    @JvmStatic
    fun showEnableMockLocationDialog(context: Context, onShizukuGranted: (() -> Unit)? = null) {
        val isShizukuAlive = com.zcshou.shizuku.ShizukuManager.isBinderAlive()
        val hasShizukuPerm = com.zcshou.shizuku.ShizukuManager.hasPermission()

        if (isShizukuAlive) {
            val builder = AlertDialog.Builder(context)
                .setTitle("⚡ Shizuku 免 Root 特權授權")
            if (hasShizukuPerm) {
                builder.setMessage("檢測到系統運行 Shizuku 且特權已連接！\n\n點擊「一鍵特權授權」，LibreGo 將自動獲得模擬位置與懸浮窗權限，無需手動進入系統設定。")
                    .setPositiveButton("一鍵特權授權") { _, _ ->
                        CoroutineScope(Dispatchers.Main).launch {
                            val result = com.zcshou.shizuku.ShizukuManager.grantAllPrivileges(context)
                            if (result.first) {
                                DisplayToast(context, "Shizuku 特權授權成功！已啟用模擬與懸浮窗")
                                onShizukuGranted?.invoke()
                            } else {
                                DisplayToast(context, "授權失敗: ${result.second}")
                            }
                        }
                    }
                    .setNeutralButton("手動設定") { _, _ -> openDevSettings(context) }
                    .setNegativeButton("取消", null)
                    .show()
                return
            } else {
                builder.setMessage("檢測到系統運行 Shizuku，但 LibreGo 尚未獲得 Shizuku 授權。\n\n點擊「請求授權」，即可一鍵免 Root 配置模擬定位與懸浮窗。")
                    .setPositiveButton("請求 Shizuku 授權") { _, _ ->
                        if (context is Activity) {
                            com.zcshou.shizuku.ShizukuManager.requestPermission(context)
                        } else {
                            DisplayToast(context, "請在主介面點擊授權")
                        }
                    }
                    .setNeutralButton("手動設定") { _, _ -> openDevSettings(context) }
                    .setNegativeButton("取消", null)
                    .show()
                return
            }
        }

        AlertDialog.Builder(context)
            .setTitle("未設定模擬位置資訊應用程式")
            .setMessage("系統檢測到 LibreGo 尚未被設為模擬位置應用程式。\n\n請按照以下步驟開啟：\n1. 進入系統「開發者選項」\n2. 找到「選取模擬位置資訊應用程式」\n3. 點選並選擇「LibreGo」\n\n（提示：安裝並啟動 Shizuku 可免進入開發者選項一鍵授權）")
            .setPositiveButton("前往開發者選項") { _, _ -> openDevSettings(context) }
            .setNegativeButton("取消", null)
            .show()
    }

    @JvmStatic
    fun openDevSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            DisplayToast(context, "無法自動跳轉，請手動打開設定中的「開發者選項」")
        }
    }

    @JvmStatic
    fun showEnableFloatWindowDialog(context: Context, onShizukuGranted: (() -> Unit)? = null) {
        val isShizukuAlive = com.zcshou.shizuku.ShizukuManager.isBinderAlive()
        val hasShizukuPerm = com.zcshou.shizuku.ShizukuManager.hasPermission()

        if (isShizukuAlive && hasShizukuPerm) {
            AlertDialog.Builder(context)
                .setTitle("⚡ 啟用懸浮窗特權")
                .setMessage("檢測到 Shizuku 特權已連接，可直接一鍵為 LibreGo 啟用螢幕懸浮窗權限！")
                .setPositiveButton("一鍵啟用") { _, _ ->
                    CoroutineScope(Dispatchers.Main).launch {
                        val result = com.zcshou.shizuku.ShizukuManager.execPrivilegedCommand("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
                        if (result.first) {
                            DisplayToast(context, "懸浮窗特權授權成功！")
                            onShizukuGranted?.invoke()
                        } else {
                            DisplayToast(context, "授權失敗: ${result.second}")
                        }
                    }
                }
                .setNeutralButton("手動授權") { _, _ -> openOverlaySettings(context) }
                .setNegativeButton("暫不使用", null)
                .show()
            return
        }

        AlertDialog.Builder(context)
            .setTitle("啟用懸浮窗權限")
            .setMessage("若需要使用螢幕懸浮搖桿即時移動位置，請開啟「在其他應用程式上層顯示」權限")
            .setPositiveButton("前往授權") { _, _ -> openOverlaySettings(context) }
            .setNegativeButton("暫不使用", null)
            .show()
    }

    private fun openOverlaySettings(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    @JvmStatic
    fun showEnableGpsDialog(context: Context) {
        AlertDialog.Builder(context)
            .setTitle("啟用定位服務")
            .setMessage("是否開啟系統 GPS 定位服務以供坐標標定？")
            .setPositiveButton("確定") { _, _ ->
                try {
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                } catch (_: Exception) {}
            }
            .setNegativeButton("取消", null)
            .show()
    }

    @JvmStatic
    fun showDisableWifiDialog(context: Context) {
        AlertDialog.Builder(context)
            .setTitle("Wi-Fi 掃描干擾提醒")
            .setMessage("開啟 Wi-Fi 掃描可能導致 Android 系統抓取附近路由器 BSSID 並覆蓋模擬定位。如遇跳回真實位置，建議關閉 Wi-Fi 使用行動網路！")
            .setPositiveButton("前往關閉") { _, _ ->
                try {
                    context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                } catch (_: Exception) {}
            }
            .setNegativeButton("忽略", null)
            .show()
    }

    @JvmStatic
    fun DisplayToast(context: Context?, str: String?) {
        if (context == null || str.isNullOrEmpty()) return
        val toast = Toast.makeText(context, str, Toast.LENGTH_LONG)
        toast.setGravity(Gravity.TOP, 0, 100)
        toast.show()
    }

    class TimeCount(millisInFuture: Long, countDownInterval: Long) :
        CountDownTimer(millisInFuture, countDownInterval) {

        private var listener: TimeCountListener? = null

        override fun onFinish() {
            listener?.onFinish()
        }

        override fun onTick(millisUntilFinished: Long) {
            listener?.onTick(millisUntilFinished)
        }

        fun setListener(listener: TimeCountListener) {
            this.listener = listener
        }

        interface TimeCountListener {
            fun onTick(millisUntilFinished: Long)
            fun onFinish()
        }
    }
}
