package com.zcshou.service

import android.annotation.SuppressLint
import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import com.elvishew.xlog.XLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Random
import kotlin.math.abs
import kotlin.math.cos

/**
 * Modern Mock Location Engine for LibreGo.
 * Decoupled from UI and floating windows.
 * Supports:
 * - Simultaneous GPS, Network, and Fused mock providers
 * - Monotonic timestamp & elapsed realtime nanoseconds
 * - Dynamic physical accuracy & satellite extras
 * - Pure Kotlin Coroutines loop
 */
object MockLocationEngine {

    const val FUSED_PROVIDER = "fused"
    const val DEFAULT_LAT = 36.667662
    const val DEFAULT_LNG = 117.027707
    const val DEFAULT_ALT = 55.0
    const val DEFAULT_BEARING = 0.0f
    const val DEFAULT_SPEED = 1.2 // m/s

    data class LocationState(
        val latitude: Double = DEFAULT_LAT,
        val longitude: Double = DEFAULT_LNG,
        val altitude: Double = DEFAULT_ALT,
        val bearing: Float = DEFAULT_BEARING,
        val speed: Double = DEFAULT_SPEED,
        val isMocking: Boolean = false
    )

    private val _locationState = MutableStateFlow(LocationState())
    val locationState: StateFlow<LocationState> = _locationState.asStateFlow()

    private var locationManager: LocationManager? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var mockJob: Job? = null

    private var lastTime = 0L
    private var lastElapsedNanos = 0L
    private val random = Random()

    @Synchronized
    fun start(context: Context, lat: Double, lng: Double, alt: Double) {
        val appCtx = context.applicationContext
        locationManager = appCtx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

        _locationState.value = LocationState(
            latitude = lat,
            longitude = lng,
            altitude = alt,
            bearing = DEFAULT_BEARING,
            speed = DEFAULT_SPEED,
            isMocking = true
        )

        registerTestProviders()

        mockJob?.cancel()
        mockJob = scope.launch {
            while (isActive && _locationState.value.isMocking) {
                updateMonotonicTimes()
                injectAllProviders(_locationState.value)
                delay(200) // Update every 200ms
            }
        }
        XLog.i("MockLocationEngine started at ($lat, $lng, $alt)")
    }

    @Synchronized
    fun stop() {
        mockJob?.cancel()
        mockJob = null
        _locationState.value = _locationState.value.copy(isMocking = false)
        unregisterTestProviders()
        XLog.i("MockLocationEngine stopped")
    }

    @Synchronized
    fun updatePosition(lat: Double, lng: Double, alt: Double = _locationState.value.altitude) {
        _locationState.value = _locationState.value.copy(
            latitude = lat,
            longitude = lng,
            altitude = alt
        )
        // Immediately push one update
        updateMonotonicTimes()
        injectAllProviders(_locationState.value)
    }

    @Synchronized
    fun moveByRocker(speed: Double, disLngKm: Double, disLatKm: Double, bearing: Float) {
        val cur = _locationState.value
        val latRad = Math.toRadians(abs(cur.latitude))
        val cosLat = cos(latRad).coerceAtLeast(0.000001)

        val deltaLng = disLngKm / (111.320 * cosLat)
        val deltaLat = disLatKm / 110.574

        val newLng = cur.longitude + deltaLng
        val newLat = cur.latitude + deltaLat

        _locationState.value = cur.copy(
            latitude = newLat,
            longitude = newLng,
            bearing = bearing,
            speed = speed
        )
    }

    private fun updateMonotonicTimes() {
        val nowTime = System.currentTimeMillis()
        lastTime = if (nowTime > lastTime) nowTime else lastTime + 1

        val nowElapsed = SystemClock.elapsedRealtimeNanos()
        lastElapsedNanos = if (nowElapsed > lastElapsedNanos) nowElapsed else lastElapsedNanos + 1
    }

    @SuppressLint("WrongConstant")
    private fun registerTestProviders() {
        val mgr = locationManager ?: return

        // Network
        try {
            removeTestProvider(mgr, LocationManager.NETWORK_PROVIDER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mgr.addTestProvider(
                    LocationManager.NETWORK_PROVIDER, true, false,
                    true, true, true, true,
                    true, ProviderProperties.POWER_USAGE_LOW, ProviderProperties.ACCURACY_COARSE
                )
            } else {
                @Suppress("DEPRECATION")
                mgr.addTestProvider(
                    LocationManager.NETWORK_PROVIDER, true, false,
                    true, true, true, true,
                    true, Criteria.POWER_LOW, Criteria.ACCURACY_COARSE
                )
            }
            if (!mgr.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                mgr.setTestProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
            }
        } catch (e: Exception) {
            XLog.e("MockLocationEngine: add NETWORK_PROVIDER failed", e)
        }

        // GPS
        try {
            removeTestProvider(mgr, LocationManager.GPS_PROVIDER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mgr.addTestProvider(
                    LocationManager.GPS_PROVIDER, false, true, false,
                    false, true, true, true,
                    ProviderProperties.POWER_USAGE_HIGH, ProviderProperties.ACCURACY_FINE
                )
            } else {
                @Suppress("DEPRECATION")
                mgr.addTestProvider(
                    LocationManager.GPS_PROVIDER, false, true, false,
                    false, true, true, true,
                    Criteria.POWER_HIGH, Criteria.ACCURACY_FINE
                )
            }
            if (!mgr.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                mgr.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
            }
        } catch (e: Exception) {
            XLog.e("MockLocationEngine: add GPS_PROVIDER failed", e)
        }

        // Fused
        try {
            removeTestProvider(mgr, FUSED_PROVIDER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mgr.addTestProvider(
                    FUSED_PROVIDER, true, true, true,
                    false, true, true, true,
                    ProviderProperties.POWER_USAGE_HIGH, ProviderProperties.ACCURACY_FINE
                )
            } else {
                @Suppress("DEPRECATION")
                mgr.addTestProvider(
                    FUSED_PROVIDER, true, true, true,
                    false, true, true, true,
                    Criteria.POWER_HIGH, Criteria.ACCURACY_FINE
                )
            }
            if (!mgr.isProviderEnabled(FUSED_PROVIDER)) {
                mgr.setTestProviderEnabled(FUSED_PROVIDER, true)
            }
        } catch (e: Exception) {
            XLog.e("MockLocationEngine: add FUSED_PROVIDER failed", e)
        }
    }

    private fun unregisterTestProviders() {
        val mgr = locationManager ?: return
        removeTestProvider(mgr, LocationManager.NETWORK_PROVIDER)
        removeTestProvider(mgr, LocationManager.GPS_PROVIDER)
        removeTestProvider(mgr, FUSED_PROVIDER)
    }

    private fun removeTestProvider(mgr: LocationManager, provider: String) {
        try {
            if (mgr.isProviderEnabled(provider)) {
                mgr.setTestProviderEnabled(provider, false)
                mgr.removeTestProvider(provider)
            }
        } catch (_: Exception) {
        }
    }

    private fun injectAllProviders(state: LocationState) {
        val mgr = locationManager ?: return
        injectGps(mgr, state)
        injectNetwork(mgr, state)
        injectFused(mgr, state)
    }

    private fun injectGps(mgr: LocationManager, state: LocationState) {
        try {
            val loc = Location(LocationManager.GPS_PROVIDER).apply {
                accuracy = 2.0f + random.nextFloat() * 1.5f + (state.speed.toFloat() * 0.05f)
                altitude = state.altitude
                bearing = state.bearing
                latitude = state.latitude
                longitude = state.longitude
                time = lastTime
                speed = state.speed.toFloat()
                elapsedRealtimeNanos = lastElapsedNanos

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    verticalAccuracyMeters = 1.2f + random.nextFloat() * 0.5f
                    bearingAccuracyDegrees = 5.0f
                    speedAccuracyMetersPerSecond = 0.5f
                }

                extras = Bundle().apply {
                    putInt("satellites", 8 + random.nextInt(6))
                }
            }
            mgr.setTestProviderLocation(LocationManager.GPS_PROVIDER, loc)
        } catch (e: Exception) {
            XLog.e("MockLocationEngine: inject GPS failed", e)
        }
    }

    private fun injectNetwork(mgr: LocationManager, state: LocationState) {
        try {
            val loc = Location(LocationManager.NETWORK_PROVIDER).apply {
                @Suppress("DEPRECATION")
                accuracy = Criteria.ACCURACY_COARSE.toFloat()
                altitude = state.altitude
                bearing = state.bearing
                latitude = state.latitude
                longitude = state.longitude
                time = lastTime
                speed = state.speed.toFloat()
                elapsedRealtimeNanos = lastElapsedNanos
            }
            mgr.setTestProviderLocation(LocationManager.NETWORK_PROVIDER, loc)
        } catch (e: Exception) {
            XLog.e("MockLocationEngine: inject Network failed", e)
        }
    }

    private fun injectFused(mgr: LocationManager, state: LocationState) {
        try {
            val loc = Location(FUSED_PROVIDER).apply {
                accuracy = 2.5f + random.nextFloat() * 1.5f + (state.speed.toFloat() * 0.05f)
                altitude = state.altitude
                bearing = state.bearing
                latitude = state.latitude
                longitude = state.longitude
                time = lastTime
                speed = state.speed.toFloat()
                elapsedRealtimeNanos = lastElapsedNanos

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    verticalAccuracyMeters = 1.5f
                    bearingAccuracyDegrees = 5.0f
                    speedAccuracyMetersPerSecond = 0.5f
                }

                extras = Bundle().apply {
                    putInt("satellites", 8 + random.nextInt(6))
                }
            }
            mgr.setTestProviderLocation(FUSED_PROVIDER, loc)
        } catch (e: Exception) {
            XLog.e("MockLocationEngine: inject Fused failed", e)
        }
    }
}
