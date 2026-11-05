package com.zcshou.utils

import android.annotation.SuppressLint
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
    fun showEnableMockLocationDialog(context: Context) {
        AlertDialog.Builder(context)
            .setTitle("啟用位置模擬")
            .setMessage("請在「開發者選項 → 選取模擬位置資訊應用程式」中選擇 LibreGo")
            .setPositiveButton("前往設定") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {}
            }
            .setNegativeButton("取消", null)
            .show()
    }

    @JvmStatic
    fun showEnableFloatWindowDialog(context: Context) {
        AlertDialog.Builder(context)
            .setTitle("啟用懸浮窗權限")
            .setMessage("若需要使用螢幕懸浮搖桿即時移動位置，請開啟「在其他應用程式上層顯示」權限")
            .setPositiveButton("前往授權") { _, _ ->
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
            .setNegativeButton("暫不使用", null)
            .show()
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
