package com.zcshou.shizuku

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.elvishew.xlog.XLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Shizuku Manager for LibreGo.
 * Handles Shizuku privileged ADB binder connection, permission requests,
 * and privileged system operations (AppOps mock location, overlay, battery whitelist).
 */
object ShizukuManager {

    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    const val REQUEST_CODE_PERMISSION = 7001

    data class ShizukuState(
        val isInstalled: Boolean = false,
        val isBinderAlive: Boolean = false,
        val hasPermission: Boolean = false,
        val version: Int = 0,
        val uid: Int = -1
    )

    private val _state = MutableStateFlow(ShizukuState())
    val state: StateFlow<ShizukuState> = _state.asStateFlow()

    private var stateChangeListener: (() -> Unit)? = null

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        XLog.i("ShizukuManager: Binder received")
        updateState()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        XLog.w("ShizukuManager: Binder dead")
        updateState()
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == REQUEST_CODE_PERMISSION) {
            val granted = grantResult == PackageManager.PERMISSION_GRANTED
            XLog.i("ShizukuManager: Permission result received: granted=$granted")
            updateState()
        }
    }

    fun init(context: Context) {
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
            updateState(context)
            XLog.i("ShizukuManager: initialized successfully")
        } catch (e: Exception) {
            XLog.e("ShizukuManager: init error", e)
        }
    }

    fun destroy() {
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        } catch (_: Exception) {}
    }

    fun isInstalled(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun isBinderAlive(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (_: Exception) {
            false
        }
    }

    fun hasPermission(): Boolean {
        if (!isBinderAlive()) return false
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
    }

    fun requestPermission(activity: Activity) {
        if (!isBinderAlive()) return
        try {
            if (Shizuku.isPreV11()) {
                XLog.w("ShizukuManager: Shizuku pre-v11 is not supported")
                return
            }
            if (!hasPermission()) {
                Shizuku.requestPermission(REQUEST_CODE_PERMISSION)
            }
        } catch (e: Exception) {
            XLog.e("ShizukuManager: requestPermission error", e)
        }
    }

    fun updateState(context: Context? = null) {
        val alive = isBinderAlive()
        val perm = if (alive) hasPermission() else false
        val ver = if (alive) {
            try { Shizuku.getVersion() } catch (_: Exception) { 0 }
        } else 0
        val uid = if (alive) {
            try { Shizuku.getUid() } catch (_: Exception) { -1 }
        } else -1

        val installed = if (context != null) isInstalled(context) else _state.value.isInstalled

        _state.value = ShizukuState(
            isInstalled = installed,
            isBinderAlive = alive,
            hasPermission = perm,
            version = ver,
            uid = uid
        )
        stateChangeListener?.invoke()
    }

    /**
     * Executes privileged shell command via Shizuku (runs with UID 2000 shell or UID 0 root).
     */
    suspend fun execPrivilegedCommand(cmd: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (!hasPermission()) {
            return@withContext Pair(false, "Shizuku 未授權")
        }

        try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }

            val process = method.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errReader = BufferedReader(InputStreamReader(process.errorStream))
            val output = StringBuilder()
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            while (errReader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }

            val exitCode = process.waitFor()
            Pair(exitCode == 0, output.toString().trim())
        } catch (e: Exception) {
            XLog.e("ShizukuManager: exec error on command: $cmd", e)
            Pair(false, e.localizedMessage ?: "執行失敗")
        }
    }

    /**
     * One-click grant all mock and floating privileges via Shizuku.
     * Sets OP_MOCK_LOCATION, SYSTEM_ALERT_WINDOW, notification permission, and battery whitelist.
     */
    suspend fun grantAllPrivileges(context: Context): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val pkg = context.packageName
        val cmds = listOf(
            "appops set $pkg android:mock_location allow",
            "appops set $pkg SYSTEM_ALERT_WINDOW allow",
            "pm grant $pkg android.permission.POST_NOTIFICATIONS",
            "pm grant $pkg android.permission.ACCESS_FINE_LOCATION",
            "pm grant $pkg android.permission.ACCESS_COARSE_LOCATION",
            "dumpsys deviceidle whitelist +$pkg"
        )

        val fullCmd = cmds.joinToString(" && ")
        val result = execPrivilegedCommand(fullCmd)
        if (result.first) {
            XLog.i("ShizukuManager: Successfully granted all privileges for $pkg via Shizuku")
        } else {
            XLog.w("ShizukuManager: Grant privileges partial or failed: ${result.second}")
        }
        result
    }
}
