package com.zcshou.gogogo

import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.baidu.mapapi.map.MapView
import com.elvishew.xlog.XLog
import com.zcshou.database.DataBaseHistoryLocation
import com.zcshou.joystick.FloatingJoystickManager
import com.zcshou.service.MockLocationEngine
import com.zcshou.service.ServiceGo
import com.zcshou.shizuku.ShizukuManager
import com.zcshou.ui.MainComposeScreen
import com.zcshou.ui.theme.LibreGoTheme
import com.zcshou.utils.GoUtils
import com.zcshou.utils.MapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Modern Jetpack Compose + Material 3 (Shizuku-inspired) MainActivity for LibreGo.
 */
class MainActivity : ComponentActivity() {

    companion object {
        const val LNG_MSG_ID = "lng"
        const val LAT_MSG_ID = "lat"
        const val ALT_MSG_ID = "alt"

        @JvmStatic
        var pendingLocationName: String? = null
        @JvmStatic
        var pendingLocationLng: Double? = null
        @JvmStatic
        var pendingLocationLat: Double? = null

        @JvmStatic
        fun showLocation(name: String, bd09Longitude: String, bd09Latitude: String): Boolean {
            return try {
                val lng = bd09Longitude.toDoubleOrNull() ?: return false
                val lat = bd09Latitude.toDoubleOrNull() ?: return false
                pendingLocationName = name
                pendingLocationLng = lng
                pendingLocationLat = lat
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    private lateinit var mapView: MapView
    private lateinit var sharedPreferences: SharedPreferences

    private var isMockServiceRunning = false
    private var isJoystickVisible by mutableStateOf(false)

    private var serviceBinder: ServiceGo.ServiceGoBinder? = null
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            serviceBinder = service as? ServiceGo.ServiceGoBinder
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBinder = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        mapView = MapView(this)

        setContent {
            LibreGoTheme {
                MainComposeScreen(
                    mapView = mapView,
                    isJoystickVisible = isJoystickVisible,
                    onToggleMock = { targetLng, targetLat ->
                        handleToggleMock(targetLng, targetLat)
                    },
                    onToggleJoystick = {
                        handleToggleJoystick()
                    },
                    onOpenSettings = {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    },
                    onOpenHistory = {
                        startActivity(Intent(this, HistoryActivity::class.java))
                    }
                )
            }
        }

        XLog.i("MainActivity Compose initialized")
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        ShizukuManager.updateState(this)
    }

    override fun onPause() {
        mapView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        if (isMockServiceRunning) {
            try {
                unbindService(serviceConnection)
            } catch (_: Exception) {}
        }
        mapView.onDestroy()
        super.onDestroy()
    }

    private fun handleToggleMock(targetLng: Double?, targetLat: Double?) {
        if (MockLocationEngine.locationState.value.isMocking) {
            // Stop mocking
            stopMocking()
            GoUtils.DisplayToast(this, "模擬定位已終止")
        } else {
            // Start mocking
            if (targetLng == null || targetLat == null) {
                GoUtils.DisplayToast(this, "請先點擊地圖選擇目標位置")
                return
            }

            if (!GoUtils.isGpsOpened(this)) {
                GoUtils.showEnableGpsDialog(this)
                return
            }

            if (!GoUtils.isAllowMockLocation(this)) {
                GoUtils.showEnableMockLocationDialog(this) {
                    handleToggleMock(targetLng, targetLat)
                }
                return
            }

            startMocking(targetLng, targetLat)
            GoUtils.DisplayToast(this, "模擬定位已啟動 (可開啟懸浮搖桿自由移動)")
        }
    }

    private fun startMocking(bdLng: Double, bdLat: Double) {
        val wgs = MapUtils.bd2wgs(bdLng, bdLat)
        val alt = sharedPreferences.getString("setting_altitude", "55.0")?.toDoubleOrNull() ?: 55.0

        val intent = Intent(this, ServiceGo::class.java).apply {
            putExtra(LNG_MSG_ID, wgs[0])
            putExtra(LAT_MSG_ID, wgs[1])
            putExtra(ALT_MSG_ID, alt)
        }

        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        startForegroundService(intent)
        isMockServiceRunning = true

        // Record history
        recordHistory(bdLng, bdLat)
    }

    private fun stopMocking() {
        if (isMockServiceRunning) {
            try {
                unbindService(serviceConnection)
            } catch (_: Exception) {}
            isMockServiceRunning = false
        }
        stopService(Intent(this, ServiceGo::class.java))
        MockLocationEngine.stop()
    }

    private fun handleToggleJoystick() {
        if (!Settings.canDrawOverlays(this)) {
            GoUtils.showEnableFloatWindowDialog(this) {
                FloatingJoystickManager.toggle(this)
                isJoystickVisible = !isJoystickVisible
            }
        } else {
            FloatingJoystickManager.toggle(this)
            isJoystickVisible = !isJoystickVisible
        }
    }

    private fun recordHistory(lng: Double, lat: Double) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = DataBaseHistoryLocation(applicationContext).writableDatabase
                val values = ContentValues().apply {
                    put("name", "模擬位置 (${String.format("%.4f", lng)}, ${String.format("%.4f", lat)})")
                    put("longitude", lng.toString())
                    put("latitude", lat.toString())
                }
                db.insert(DataBaseHistoryLocation.TABLE_NAME, null, values)
            } catch (e: Exception) {
                XLog.e("Failed to record history", e)
            }
        }
    }
}
