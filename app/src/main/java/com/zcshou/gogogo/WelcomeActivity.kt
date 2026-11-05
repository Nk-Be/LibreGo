package com.zcshou.gogogo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.zcshou.utils.GoUtils

/**
 * Welcome & Permission Onboarding Activity in Kotlin for LibreGo.
 * Zero agreement friction, fast direct entry.
 */
class WelcomeActivity : AppCompatActivity() {

    companion object {
        private const val SDK_PERMISSION_REQUEST = 127
    }

    private val reqPermissions = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize default preferences
        PreferenceManager.setDefaultValues(this, R.xml.preferences_main, false)

        setContentView(R.layout.activity_welcome)

        findViewById<Button>(R.id.startButton).setOnClickListener {
            handleStart()
        }

        // Fast path: if permissions already granted, directly enter main screen
        if (hasRequiredPermissions()) {
            startMainActivity()
        } else {
            checkDefaultPermissions()
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        val hasFine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return hasFine || hasCoarse
    }

    private fun handleStart() {
        if (!GoUtils.isGpsOpened(this)) {
            GoUtils.DisplayToast(this, getString(R.string.app_error_gps))
            return
        }

        if (hasRequiredPermissions()) {
            startMainActivity()
        } else {
            checkDefaultPermissions()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == SDK_PERMISSION_REQUEST) {
            if (hasRequiredPermissions()) {
                startMainActivity()
            } else {
                GoUtils.DisplayToast(this, getString(R.string.app_error_permission))
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun checkDefaultPermissions() {
        reqPermissions.clear()

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            reqPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            reqPermissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                reqPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (reqPermissions.isNotEmpty()) {
            requestPermissions(reqPermissions.toTypedArray(), SDK_PERMISSION_REQUEST)
        }
    }

    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
