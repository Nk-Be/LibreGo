package com.zcshou.service

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
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.elvishew.xlog.XLog
import com.zcshou.gogogo.MainActivity
import com.zcshou.gogogo.R
import com.zcshou.joystick.FloatingJoystickManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Modern Mock Location Foreground Service in Kotlin.
 * Fully decoupled from FloatingJoystickManager.
 */
class ServiceGo : Service() {

    companion object {
        const val DEFAULT_LAT = 36.667662
        const val DEFAULT_LNG = 117.027707
        const val DEFAULT_ALT = 55.0

        private const val SERVICE_GO_NOTE_ID = 1
        const val ACTION_JOYSTICK_TOGGLE = "com.zcshou.service.ACTION_JOYSTICK_TOGGLE"
        const val ACTION_JOYSTICK_SHOW = "ShowJoyStick"
        const val ACTION_JOYSTICK_HIDE = "HideJoyStick"
        const val ACTION_STOP_SERVICE = "com.zcshou.service.ACTION_STOP_SERVICE"

        private const val CHANNEL_ID = "SERVICE_GO_NOTE"
        private const val CHANNEL_NAME = "LibreGo Location Service"
    }

    private val binder = ServiceGoBinder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var actionReceiver: NoteActionReceiver? = null

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        initNotification()
        observeEngineState()
        XLog.i("ServiceGo: onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val lat = intent.getDoubleExtra(MainActivity.LAT_MSG_ID, DEFAULT_LAT)
            val lng = intent.getDoubleExtra(MainActivity.LNG_MSG_ID, DEFAULT_LNG)
            val alt = intent.getDoubleExtra(MainActivity.ALT_MSG_ID, DEFAULT_ALT)
            MockLocationEngine.start(this, lat, lng, alt)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        MockLocationEngine.stop()
        FloatingJoystickManager.hide()

        actionReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {}
        }
        actionReceiver = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        XLog.i("ServiceGo: onDestroy")
        super.onDestroy()
    }

    private fun initNotification() {
        actionReceiver = NoteActionReceiver()
        val filter = IntentFilter().apply {
            addAction(ACTION_JOYSTICK_TOGGLE)
            addAction(ACTION_JOYSTICK_SHOW)
            addAction(ACTION_JOYSTICK_HIDE)
            addAction(ACTION_STOP_SERVICE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(actionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(actionReceiver, filter)
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)

        val notification = buildNotification(DEFAULT_LAT, DEFAULT_LNG)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(SERVICE_GO_NOTE_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(SERVICE_GO_NOTE_ID, notification)
        }
    }

    private fun buildNotification(lat: Double, lng: Double): Notification {
        val clickIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val clickPI = PendingIntent.getActivity(this, 1, clickIntent, PendingIntent.FLAG_IMMUTABLE)

        val toggleIntent = Intent(ACTION_JOYSTICK_TOGGLE).setPackage(packageName)
        val togglePI = PendingIntent.getBroadcast(this, 2, toggleIntent, PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = Intent(ACTION_STOP_SERVICE).setPackage(packageName)
        val stopPI = PendingIntent.getBroadcast(this, 3, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val toggleText = if (FloatingJoystickManager.isShowing) {
            getString(R.string.note_hide)
        } else {
            getString(R.string.note_show)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.app_name) + " - 正在模擬定位")
            .setContentText(String.format("經度: %.5f, 緯度: %.5f", lng, lat))
            .setContentIntent(clickPI)
            .addAction(0, toggleText, togglePI)
            .addAction(0, "停止模擬", stopPI)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(lat: Double, lng: Double) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(SERVICE_GO_NOTE_ID, buildNotification(lat, lng))
    }

    private fun observeEngineState() {
        scope.launch {
            MockLocationEngine.locationState.collectLatest { state ->
                if (state.isMocking) {
                    updateNotification(state.latitude, state.longitude)
                }
            }
        }
    }

    inner class NoteActionReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_JOYSTICK_TOGGLE -> {
                    FloatingJoystickManager.toggle(context)
                    val state = MockLocationEngine.locationState.value
                    updateNotification(state.latitude, state.longitude)
                }
                ACTION_JOYSTICK_SHOW -> {
                    FloatingJoystickManager.show(context)
                    val state = MockLocationEngine.locationState.value
                    updateNotification(state.latitude, state.longitude)
                }
                ACTION_JOYSTICK_HIDE -> {
                    FloatingJoystickManager.hide()
                    val state = MockLocationEngine.locationState.value
                    updateNotification(state.latitude, state.longitude)
                }
                ACTION_STOP_SERVICE -> {
                    stopSelf()
                }
            }
        }
    }

    inner class ServiceGoBinder : Binder() {
        fun setPosition(lng: Double, lat: Double, alt: Double) {
            MockLocationEngine.updatePosition(lat = lat, lng = lng, alt = alt)
        }
    }
}
