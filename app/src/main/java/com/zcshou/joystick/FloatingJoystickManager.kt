package com.zcshou.joystick

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.elvishew.xlog.XLog
import com.zcshou.gogogo.HistoryActivity
import com.zcshou.gogogo.MainActivity
import com.zcshou.gogogo.R
import com.zcshou.service.MockLocationEngine
import com.zcshou.utils.GoUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * Decoupled Floating Joystick Manager for LibreGo.
 * Responsible ONLY for floating overlay lifecycle and user input.
 * Does NOT hold mock location providers, database queries, or embedded map views.
 */
object FloatingJoystickManager {

    private const val PREF_KEY_X = "floating_joystick_x"
    private const val PREF_KEY_Y = "floating_joystick_y"
    private const val INTERVAL_MS = 1000L

    private var windowManager: WindowManager? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private var overlayView: View? = null

    private var isWalk = true
    private var isRun = false
    private var isBike = false
    private var currentSpeed = 1.2

    private var moveJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var currentAngle = 0.0
    private var currentR = 0.0
    private var isMoving = false

    val isShowing: Boolean
        get() = overlayView != null && overlayView?.parent != null

    @SuppressLint("ClickableViewAccessibility")
    fun show(context: Context) {
        if (isShowing) return

        if (!Settings.canDrawOverlays(context)) {
            XLog.w("FloatingJoystickManager: No overlay permission")
            return
        }

        val appCtx = context.applicationContext
        val sp = PreferenceManager.getDefaultSharedPreferences(appCtx)
        windowManager = appCtx.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val params = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            format = PixelFormat.RGBA_8888
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            gravity = Gravity.START or Gravity.TOP
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            x = sp.getInt(PREF_KEY_X, 300)
            y = sp.getInt(PREF_KEY_Y, 400)
        }
        windowParams = params

        val inflater = LayoutInflater.from(appCtx)
        val root = inflater.inflate(R.layout.joystick, null)
        overlayView = root

        setupViews(appCtx, root, sp, params)

        try {
            windowManager?.addView(root, params)
            XLog.i("FloatingJoystickManager: overlay displayed")
        } catch (e: Exception) {
            XLog.e("FloatingJoystickManager: Failed to add overlay view", e)
        }
    }

    fun hide() {
        stopMovement()
        val view = overlayView
        val wm = windowManager
        if (view != null && wm != null && view.parent != null) {
            try {
                wm.removeViewImmediate(view)
                XLog.i("FloatingJoystickManager: overlay removed")
            } catch (e: Exception) {
                XLog.e("FloatingJoystickManager: Failed to remove overlay view", e)
            }
        }
        overlayView = null
    }

    fun toggle(context: Context) {
        if (isShowing) {
            hide()
        } else {
            show(context)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupViews(
        context: Context,
        root: View,
        sp: SharedPreferences,
        params: WindowManager.LayoutParams
    ) {
        // Drag handler
        root.setOnTouchListener(object : View.OnTouchListener {
            private var lastX = 0
            private var lastY = 0

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        lastX = event.rawX.toInt()
                        lastY = event.rawY.toInt()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val nowX = event.rawX.toInt()
                        val nowY = event.rawY.toInt()
                        params.x += (nowX - lastX)
                        params.y += (nowY - lastY)
                        lastX = nowX
                        lastY = nowY
                        if (root.parent != null) {
                            windowManager?.updateViewLayout(root, params)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        sp.edit()
                            .putInt(PREF_KEY_X, params.x)
                            .putInt(PREF_KEY_Y, params.y)
                            .apply()
                        return true
                    }
                }
                return false
            }
        })

        // Top actions
        root.findViewById<ImageButton>(R.id.joystick_close)?.setOnClickListener {
            hide()
        }

        root.findViewById<ImageButton>(R.id.joystick_position)?.setOnClickListener {
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            context.startActivity(intent)
        }

        root.findViewById<ImageButton>(R.id.joystick_history)?.setOnClickListener {
            val intent = Intent(context, HistoryActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        // Speed buttons
        val btnWalk = root.findViewById<ImageButton>(R.id.joystick_walk)
        val btnRun = root.findViewById<ImageButton>(R.id.joystick_run)
        val btnBike = root.findViewById<ImageButton>(R.id.joystick_bike)

        val accentColor = ContextCompat.getColor(context, R.color.colorAccent)
        val defaultColor = ContextCompat.getColor(context, R.color.black)

        fun updateSpeedUI() {
            btnWalk?.setColorFilter(if (isWalk) accentColor else defaultColor)
            btnRun?.setColorFilter(if (isRun) accentColor else defaultColor)
            btnBike?.setColorFilter(if (isBike) accentColor else defaultColor)
        }

        fun parseSpeed(prefKey: String, defaultVal: String): Double {
            return try {
                sp.getString(prefKey, defaultVal)?.toDoubleOrNull() ?: defaultVal.toDouble()
            } catch (_: Exception) {
                defaultVal.toDouble()
            }
        }

        currentSpeed = parseSpeed("setting_walk", "1.2")
        isWalk = true; isRun = false; isBike = false
        updateSpeedUI()

        btnWalk?.setOnClickListener {
            isWalk = true; isRun = false; isBike = false
            currentSpeed = parseSpeed("setting_walk", "1.2")
            updateSpeedUI()
        }

        btnRun?.setOnClickListener {
            isWalk = false; isRun = true; isBike = false
            currentSpeed = parseSpeed("setting_run", "3.6")
            updateSpeedUI()
        }

        btnBike?.setOnClickListener {
            isWalk = false; isRun = false; isBike = true
            currentSpeed = parseSpeed("setting_bike", "10.0")
            updateSpeedUI()
        }

        // Joystick mode (Rocker vs ButtonView)
        val rockerView = root.findViewById<RockerView>(R.id.joystick_rocker)
        val buttonView = root.findViewById<ButtonView>(R.id.joystick_button)

        val joystickType = sp.getString("setting_joystick_type", "0")
        if (joystickType == "0") {
            rockerView?.visibility = View.VISIBLE
            buttonView?.visibility = View.GONE
        } else {
            rockerView?.visibility = View.GONE
            buttonView?.visibility = View.VISIBLE
        }

        rockerView?.setListener { auto, angle, r ->
            onDirectionInput(auto, angle, r)
        }

        buttonView?.setListener { auto, angle, r ->
            onDirectionInput(auto, angle, r)
        }
    }

    private fun onDirectionInput(auto: Boolean, angle: Double, r: Double) {
        if (r <= 0) {
            stopMovement()
        } else {
            currentAngle = angle
            currentR = r
            if (auto) {
                if (!isMoving) {
                    startMovement()
                }
            } else {
                stopMovement()
                performMoveStep()
            }
        }
    }

    private fun startMovement() {
        isMoving = true
        moveJob?.cancel()
        moveJob = scope.launch {
            while (isActive && isMoving) {
                performMoveStep()
                delay(INTERVAL_MS)
            }
        }
    }

    private fun stopMovement() {
        isMoving = false
        moveJob?.cancel()
        moveJob = null
    }

    private fun performMoveStep() {
        val angleRad = Math.toRadians(currentAngle)
        val disLng = currentSpeed * (INTERVAL_MS.toDouble() / 1000.0) * currentR * cos(angleRad) / 1000.0 // km
        val disLat = currentSpeed * (INTERVAL_MS.toDouble() / 1000.0) * currentR * sin(angleRad) / 1000.0 // km
        val bearing = (90.0 - currentAngle).toFloat()

        MockLocationEngine.moveByRocker(
            speed = currentSpeed,
            disLngKm = disLng,
            disLatKm = disLat,
            bearing = bearing
        )
    }
}
