package com.zcshou.gogogo

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SimpleAdapter
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.baidu.location.BDAbstractLocationListener
import com.baidu.location.BDLocation
import com.baidu.location.LocationClient
import com.baidu.location.LocationClientOption
import com.baidu.mapapi.map.BaiduMap
import com.baidu.mapapi.map.BitmapDescriptor
import com.baidu.mapapi.map.BitmapDescriptorFactory
import com.baidu.mapapi.map.InfoWindow
import com.baidu.mapapi.map.MapPoi
import com.baidu.mapapi.map.MapStatus
import com.baidu.mapapi.map.MapStatusUpdate
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.MapView
import com.baidu.mapapi.map.Marker
import com.baidu.mapapi.map.MarkerOptions
import com.baidu.mapapi.map.MyLocationConfiguration
import com.baidu.mapapi.map.MyLocationData
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.search.core.SearchResult
import com.baidu.mapapi.search.geocode.GeoCodeResult
import com.baidu.mapapi.search.geocode.GeoCoder
import com.baidu.mapapi.search.geocode.OnGetGeoCoderResultListener
import com.baidu.mapapi.search.geocode.ReverseGeoCodeOption
import com.baidu.mapapi.search.geocode.ReverseGeoCodeResult
import com.baidu.mapapi.search.sug.SuggestionResult
import com.baidu.mapapi.search.sug.SuggestionSearch
import com.baidu.mapapi.search.sug.SuggestionSearchOption
import com.elvishew.xlog.XLog
import com.google.android.material.button.MaterialButton
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import com.zcshou.database.DataBaseHistoryLocation
import com.zcshou.database.DataBaseHistorySearch
import com.zcshou.joystick.FloatingJoystickManager
import com.zcshou.service.MockLocationEngine
import com.zcshou.service.ServiceGo
import com.zcshou.shizuku.ShizukuManager
import com.zcshou.utils.GoUtils
import com.zcshou.utils.MapUtils
import com.zcshou.utils.ShareUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.Locale

/**
 * Modern MainActivity in Kotlin for LibreGo (自由影梭).
 * Features:
 * - Material 3 floating Status Card (Shizuku-inspired design)
 * - StateFlow reactive observation of MockLocationEngine
 * - Direct one-click GitHub open source repository links
 * - Decoupled single-point positioning vs floating joystick
 */
class MainActivity : BaseActivity(), SensorEventListener {

    companion object {
        const val LNG_MSG_ID = "lng"
        const val LAT_MSG_ID = "lat"
        const val ALT_MSG_ID = "alt"
        const val GITHUB_REPO_URL = "https://github.com/Nk-Be/LibreGo"

        private const val POI_NAME = "poi_name"
        private const val POI_ADDRESS = "poi_address"
        private const val POI_LONGITUDE = "poi_longitude"
        private const val POI_LATITUDE = "poi_latitude"

        @JvmStatic
        var mBaiduMap: BaiduMap? = null

        @JvmStatic
        var mMarkLatLngMap: LatLng? = LatLng(36.547743718042415, 117.07018449827267)

        @JvmStatic
        var mMarkName: String? = null

        @JvmStatic
        var mMapIndicator: BitmapDescriptor? = null

        @JvmStatic
        fun showLocation(name: String, bd09Longitude: String, bd09Latitude: String): Boolean {
            return try {
                if (bd09Longitude.isNotEmpty() && bd09Latitude.isNotEmpty()) {
                    mMarkName = name
                    mMarkLatLngMap = LatLng(bd09Latitude.toDouble(), bd09Longitude.toDouble())
                    val marker = MarkerOptions().position(mMarkLatLngMap!!).icon(mMapIndicator)
                    mBaiduMap?.clear()
                    mBaiduMap?.addOverlay(marker)
                    val mapStatusUpdate = MapStatusUpdateFactory.newLatLng(mMarkLatLngMap)
                    mBaiduMap?.setMapStatus(mapStatusUpdate)
                    true
                } else false
            } catch (e: Exception) {
                XLog.e("ERROR: showLocation", e)
                false
            }
        }
    }

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var okHttpClient: OkHttpClient

    private var mMapView: MapView? = null
    private var mLocClient: LocationClient? = null
    private var mGeoCoder: GeoCoder? = null

    private var mSensorManager: SensorManager? = null
    private var mSensorAccelerometer: Sensor? = null
    private var mSensorMagnetic: Sensor? = null
    private var lastAccelerometer: FloatArray? = null
    private var lastMagnetic: FloatArray? = null
    private var mCurrentDirection = 0

    private var mCurrentLat = 0.0
    private var mCurrentLon = 0.0
    private var mCurrentCity = ""
    private var isFirstLoc = true
    private var isMockServStart = false
    private var mMockMarker: Marker? = null

    private var mServiceBinder: ServiceGo.ServiceGoBinder? = null
    private val mConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            mServiceBinder = service as? ServiceGo.ServiceGoBinder
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            mServiceBinder = null
        }
    }

    // Databases
    private var mLocationHistoryDB: SQLiteDatabase? = null
    private var mSearchHistoryDB: SQLiteDatabase? = null

    // Search
    private var mSuggestionSearch: SuggestionSearch? = null
    private lateinit var mSearchLayout: LinearLayout
    private lateinit var mSearchList: ListView
    private lateinit var mHistoryLayout: LinearLayout
    private lateinit var mSearchHistoryList: ListView

    // Bottom Card Views (Shizuku style)
    private lateinit var tvStatusBadge: TextView
    private lateinit var tvShizukuBadge: TextView
    private lateinit var tvCoordinates: TextView
    private lateinit var tvOpenGithub: TextView
    private lateinit var btnStartMock: MaterialButton
    private lateinit var btnToggleJoystick: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val drawer = findViewById<DrawerLayout>(R.id.drawer_layout)
        val toggle = ActionBarDrawerToggle(
            this, drawer, toolbar, R.string.nav_drawer_open, R.string.nav_drawer_close
        )
        drawer.addDrawerListener(toggle)
        toggle.syncState()

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        okHttpClient = OkHttpClient()
        mMapIndicator = BitmapDescriptorFactory.fromResource(R.drawable.icon_gcoding)

        initCardViews()
        initNavigationView()
        initMap()
        initMapLocation()
        initMapButton()
        initStoreHistory()
        initSearchView()
        initShizukuView()

        observeMockEngine()
        XLog.i("MainActivity onCreate (Kotlin)")
    }

    override fun onResume() {
        super.onResume()
        mMapView?.onResume()
        mMarkLatLngMap?.let { updatePinText(it) }
        ShizukuManager.updateState(this)
        mSensorManager?.let { sm ->
            mSensorAccelerometer?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
            mSensorMagnetic?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        }
    }

    override fun onPause() {
        mMapView?.onPause()
        mSensorManager?.unregisterListener(this)
        super.onPause()
    }

    override fun onDestroy() {
        if (isMockServStart) {
            try {
                unbindService(mConnection)
            } catch (_: Exception) {}
        }
        mMapView?.onDestroy()
        mLocClient?.stop()
        mSuggestionSearch?.destroy()
        mGeoCoder?.destroy()
        super.onDestroy()
    }

    // ==========================================
    // UI Card & Control Initialization (Shizuku Style)
    // ==========================================
    private fun initCardViews() {
        tvStatusBadge = findViewById(R.id.tvStatusBadge)
        tvShizukuBadge = findViewById(R.id.tvShizukuBadge)
        tvCoordinates = findViewById(R.id.tvCoordinates)
        tvOpenGithub = findViewById(R.id.tvOpenGithub)
        btnStartMock = findViewById(R.id.btnStartMock)
        btnToggleJoystick = findViewById(R.id.btnToggleJoystick)

        tvOpenGithub.setOnClickListener {
            openGithubPage()
        }

        btnStartMock.setOnClickListener {
            handleMockButtonClick(it)
        }

        btnToggleJoystick.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                GoUtils.showEnableFloatWindowDialog(this) {
                    FloatingJoystickManager.toggle(this)
                }
            } else {
                FloatingJoystickManager.toggle(this)
            }
        }
    }

    private fun initShizukuView() {
        lifecycleScope.launch {
            ShizukuManager.state.collectLatest { s ->
                when {
                    !s.isInstalled -> {
                        tvShizukuBadge.text = "⚡ Shizuku 未安裝"
                        tvShizukuBadge.setTextColor(Color.parseColor("#78909C"))
                    }
                    !s.isBinderAlive -> {
                        tvShizukuBadge.text = "⚡ Shizuku 未運行"
                        tvShizukuBadge.setTextColor(Color.parseColor("#D32F2F"))
                    }
                    !s.hasPermission -> {
                        tvShizukuBadge.text = "⚡ Shizuku 待授權"
                        tvShizukuBadge.setTextColor(Color.parseColor("#F57C00"))
                    }
                    else -> {
                        tvShizukuBadge.text = "⚡ Shizuku 特權已連接"
                        tvShizukuBadge.setTextColor(Color.parseColor("#00796B"))
                    }
                }
            }
        }

        tvShizukuBadge.setOnClickListener {
            val s = ShizukuManager.state.value
            when {
                !s.isInstalled -> {
                    AlertDialog.Builder(this)
                        .setTitle("Shizuku 未安裝")
                        .setMessage("Shizuku 可讓 LibreGo 免 Root 一鍵配置模擬位置、螢幕懸浮窗與後台防殺白名單。\n\n是否前往 Shizuku 官方網站了解或下載？")
                        .setPositiveButton("前往下載") { _, _ ->
                            try {
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")))
                            } catch (_: Exception) {}
                        }
                        .setNegativeButton("取消", null)
                        .show()
                }
                !s.isBinderAlive -> {
                    AlertDialog.Builder(this)
                        .setTitle("Shizuku 服務未運行")
                        .setMessage("檢測到已安裝 Shizuku，但 Shizuku 服務目前尚未啟動。\n\n請打開 Shizuku 應用程式，使用「無線除錯」或「連接電腦 (adb)」啟動服務。")
                        .setPositiveButton("打開 Shizuku") { _, _ ->
                            try {
                                val intent = packageManager.getLaunchIntentForPackage(ShizukuManager.SHIZUKU_PACKAGE)
                                if (intent != null) startActivity(intent)
                            } catch (_: Exception) {}
                        }
                        .setNegativeButton("關閉", null)
                        .show()
                }
                !s.hasPermission -> {
                    ShizukuManager.requestPermission(this)
                }
                else -> {
                    AlertDialog.Builder(this)
                        .setTitle("⚡ Shizuku 特權中心")
                        .setMessage("Shizuku 特權服務正常運行中 (v${s.version})！\n\n可一鍵自動配置所有模擬權限：\n• 模擬位置 AppOps\n• 懸浮窗顯示權限\n• 通知與後台白名單")
                        .setPositiveButton("一鍵配置特權") { _, _ ->
                            lifecycleScope.launch {
                                val result = ShizukuManager.grantAllPrivileges(this@MainActivity)
                                if (result.first) {
                                    GoUtils.DisplayToast(this@MainActivity, "Shizuku 特權已全部配置成功！")
                                } else {
                                    GoUtils.DisplayToast(this@MainActivity, "配置異常: ${result.second}")
                                }
                            }
                        }
                        .setNegativeButton("關閉", null)
                        .show()
                }
            }
        }
    }

    private fun observeMockEngine() {
        lifecycleScope.launch {
            MockLocationEngine.locationState.collectLatest { state ->
                isMockServStart = state.isMocking
                if (state.isMocking) {
                    tvStatusBadge.text = getString(R.string.status_mocking)
                    tvStatusBadge.setTextColor(Color.parseColor("#2E7D32")) // Green
                    tvCoordinates.text = String.format(
                        "實時模擬：經度 %.6f, 緯度 %.6f (速度 %.1fm/s)",
                        state.longitude, state.latitude, state.speed
                    )
                    btnStartMock.text = getString(R.string.action_stop)
                    btnStartMock.setIconResource(R.drawable.ic_close)

                    // Real-time map marker movement
                    val bd = MapUtils.wgs2bd09(state.longitude, state.latitude)
                    val mockPos = LatLng(bd[1], bd[0])
                    val map = mBaiduMap
                    if (map != null) {
                        if (mMockMarker == null) {
                            val markerOptions = MarkerOptions().position(mockPos).icon(mMapIndicator).zIndex(9)
                            mMockMarker = map.addOverlay(markerOptions) as? Marker
                        } else {
                            mMockMarker?.position = mockPos
                        }
                    }
                } else {
                    mMockMarker?.remove()
                    mMockMarker = null

                    tvStatusBadge.text = getString(R.string.status_idle)
                    tvStatusBadge.setTextColor(Color.parseColor("#455A64")) // Slate Gray
                    val mark = mMarkLatLngMap
                    if (mark != null) {
                        val nameStr = if (!mMarkName.isNullOrEmpty()) "已選：$mMarkName" else String.format("已選目標：%.6f, %.6f", mark.longitude, mark.latitude)
                        tvCoordinates.text = "$nameStr (點擊開始模擬)"
                    } else {
                        tvCoordinates.text = "點擊地圖選擇目標位置"
                    }
                    btnStartMock.text = getString(R.string.action_start)
                    btnStartMock.setIconResource(R.drawable.ic_position)
                }
            }
        }
    }

    private fun handleMockButtonClick(v: View) {
        if (!GoUtils.isNetworkAvailable(this)) {
            GoUtils.DisplayToast(this, getString(R.string.app_error_network))
            return
        }

        if (!GoUtils.isGpsOpened(this)) {
            GoUtils.showEnableGpsDialog(this)
            return
        }

        if (!GoUtils.isAllowMockLocation(this)) {
            GoUtils.showEnableMockLocationDialog(this) {
                handleMockButtonClick(v)
            }
            return
        }

        if (isMockServStart) {
            // Already mocking
            if (mMarkLatLngMap == null) {
                stopGoLocation()
                Snackbar.make(v, "模擬定位已終止", Snackbar.LENGTH_LONG).show()
            } else {
                // Teleport to new point
                val mark = mMarkLatLngMap!!
                val wgs = MapUtils.bd2wgs(mark.longitude, mark.latitude)
                val alt = sharedPreferences.getString("setting_altitude", "55.0")?.toDoubleOrNull() ?: 55.0
                mServiceBinder?.setPosition(wgs[0], wgs[1], alt)
                MockLocationEngine.updatePosition(wgs[1], wgs[0], alt)
                Snackbar.make(v, "已傳送到新位置", Snackbar.LENGTH_LONG).show()
                recordCurrentLocation(mark.longitude, mark.latitude)
                mBaiduMap?.clear()
                mMarkLatLngMap = null
            }
        } else {
            // Start mocking
            val mark = mMarkLatLngMap
            if (mark == null) {
                Snackbar.make(v, "請先在上方地圖點選一個目標位置", Snackbar.LENGTH_LONG).show()
            } else {
                startGoLocation(mark)
                Snackbar.make(v, "模擬定位已啟動！(可點擊「懸浮搖桿」移動)", Snackbar.LENGTH_LONG).show()
                recordCurrentLocation(mark.longitude, mark.latitude)
                mBaiduMap?.clear()
                mMarkLatLngMap = null
            }
        }
    }

    private fun startGoLocation(mark: LatLng) {
        val intent = Intent(this, ServiceGo::class.java)
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE)

        val wgs = MapUtils.bd2wgs(mark.longitude, mark.latitude)
        val alt = sharedPreferences.getString("setting_altitude", "55.0")?.toDoubleOrNull() ?: 55.0

        intent.putExtra(LNG_MSG_ID, wgs[0])
        intent.putExtra(LAT_MSG_ID, wgs[1])
        intent.putExtra(ALT_MSG_ID, alt)

        startForegroundService(intent)
        isMockServStart = true
    }

    private fun stopGoLocation() {
        try {
            unbindService(mConnection)
        } catch (_: Exception) {}
        val intent = Intent(this, ServiceGo::class.java)
        stopService(intent)
        isMockServStart = false
        MockLocationEngine.stop()
    }

    // ==========================================
    // Navigation Drawer & GitHub Highlighting
    // ==========================================
    private fun initNavigationView() {
        val navView = findViewById<NavigationView>(R.id.nav_view)

        // Setup header clicks
        val header = navView.getHeaderView(0)
        header?.findViewById<View>(R.id.app_github_badge)?.setOnClickListener {
            openGithubPage()
        }

        navView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_github -> {
                    openGithubPage()
                }
                R.id.nav_history -> {
                    startActivity(Intent(this, HistoryActivity::class.java))
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                }
                R.id.nav_dev -> {
                    if (!GoUtils.isDeveloperOptionsEnabled(this)) {
                        GoUtils.DisplayToast(this, getString(R.string.app_error_dev))
                    } else {
                        try {
                            startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                        } catch (_: Exception) {
                            GoUtils.DisplayToast(this, getString(R.string.app_error_dev))
                        }
                    }
                }
                R.id.nav_about -> {
                    showAboutDialog()
                }
            }
            val drawer = findViewById<DrawerLayout>(R.id.drawer_layout)
            drawer.closeDrawer(GravityCompat.START)
            true
        }
    }

    private fun openGithubPage() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_REPO_URL))
        startActivity(intent)
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.about_dialog_title)
            .setMessage(R.string.about_dialog_message)
            .setPositiveButton("前往 GitHub 支持 ⭐") { _, _ ->
                openGithubPage()
            }
            .setNegativeButton("關閉", null)
            .show()
    }

    // ==========================================
    // Map & Sensors
    // ==========================================
    private fun initMap() {
        mMapView = findViewById(R.id.bdMapView)
        mBaiduMap = mMapView?.map
        mBaiduMap?.let { map ->
            map.isMyLocationEnabled = true
            map.setOnMapClickListener(object : BaiduMap.OnMapClickListener {
                override fun onMapClick(point: LatLng?) {
                    point?.let {
                        mMarkLatLngMap = it
                        mMarkName = null
                        markMap()
                        updatePinText(it)
                        mGeoCoder?.reverseGeoCode(ReverseGeoCodeOption().location(it))
                    }
                }
                override fun onMapPoiClick(poi: MapPoi?) {
                    poi?.position?.let {
                        mMarkLatLngMap = it
                        mMarkName = poi.name
                        markMap()
                        updatePinText(it)
                    }
                }
            })
            map.setOnMapLongClickListener { point ->
                point?.let {
                    mMarkLatLngMap = it
                    markMap()
                    updatePinText(it)
                    mGeoCoder?.reverseGeoCode(ReverseGeoCodeOption().location(it))
                }
            }
            map.setOnMapDoubleClickListener {
                map.animateMapStatus(MapStatusUpdateFactory.zoomIn())
            }
        }

        mGeoCoder = GeoCoder.newInstance().apply {
            setOnGetGeoCodeResultListener(object : OnGetGeoCoderResultListener {
                override fun onGetGeoCodeResult(result: GeoCodeResult?) {}
                override fun onGetReverseGeoCodeResult(result: ReverseGeoCodeResult?) {
                    if (result != null && result.error == SearchResult.ERRORNO.NO_ERROR) {
                        mMarkName = result.address
                        mMarkLatLngMap?.let { updatePinText(it) }
                    }
                }
            })
        }

        mSensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        mSensorManager?.let { sm ->
            mSensorAccelerometer = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            mSensorMagnetic = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        }
    }

    private fun updatePinText(latLng: LatLng) {
        if (!isMockServStart) {
            val nameStr = if (!mMarkName.isNullOrEmpty()) "已選：$mMarkName" else String.format("已選目標：%.6f, %.6f", latLng.longitude, latLng.latitude)
            tvCoordinates.text = "$nameStr (點擊開始模擬)"
        }
    }

    private fun markMap() {
        val latLng = mMarkLatLngMap ?: return
        val map = mBaiduMap ?: return
        val marker = MarkerOptions().position(latLng).icon(mMapIndicator)
        map.clear()
        map.addOverlay(marker)
    }

    private fun resetMap() {
        val map = mBaiduMap ?: return
        map.clear()
        mMarkLatLngMap = null
        val locData = MyLocationData.Builder()
            .latitude(mCurrentLat)
            .longitude(mCurrentLon)
            .direction(mCurrentDirection.toFloat())
            .build()
        map.setMyLocationData(locData)

        val builder = MapStatus.Builder().target(LatLng(mCurrentLat, mCurrentLon)).zoom(18.0f)
        map.animateMapStatus(MapStatusUpdateFactory.newMapStatus(builder.build()))
    }

    private fun initMapLocation() {
        try {
            mLocClient = LocationClient(this).apply {
                registerLocationListener(object : BDAbstractLocationListener() {
                    override fun onReceiveLocation(bdLocation: BDLocation?) {
                        if (bdLocation == null || mMapView == null) return
                        mCurrentCity = bdLocation.city ?: ""
                        mCurrentLat = bdLocation.latitude
                        mCurrentLon = bdLocation.longitude

                        val locData = MyLocationData.Builder()
                            .accuracy(bdLocation.radius)
                            .direction(mCurrentDirection.toFloat())
                            .latitude(bdLocation.latitude)
                            .longitude(bdLocation.longitude)
                            .build()
                        mBaiduMap?.setMyLocationData(locData)
                        mBaiduMap?.setMyLocationConfiguration(
                            MyLocationConfiguration(MyLocationConfiguration.LocationMode.NORMAL, true, null)
                        )

                        if (isFirstLoc) {
                            isFirstLoc = false
                            mMarkLatLngMap = LatLng(bdLocation.latitude, bdLocation.longitude)
                            val builder = MapStatus.Builder().target(mMarkLatLngMap).zoom(18.0f)
                            mBaiduMap?.animateMapStatus(MapStatusUpdateFactory.newMapStatus(builder.build()))
                        }
                    }
                })
                locOption = getLocationClientOption()
                start()
            }
        } catch (e: Exception) {
            XLog.e("ERROR: initMapLocation", e)
        }
    }

    private fun getLocationClientOption(): LocationClientOption {
        return LocationClientOption().apply {
            locationMode = LocationClientOption.LocationMode.Hight_Accuracy
            coorType = "bd09ll"
            scanSpan = 1000
            setIsNeedAddress(true)
            setNeedDeviceDirect(false)
            isLocationNotify = true
            isIgnoreKillProcess = true
            isOpenGnss = true
            SetIgnoreCacheException(true)
        }
    }

    private fun initMapButton() {
        findViewById<RadioGroup>(R.id.RadioGroupMapType)?.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.mapNormal) {
                mBaiduMap?.mapType = BaiduMap.MAP_TYPE_NORMAL
            } else if (checkedId == R.id.mapSatellite) {
                mBaiduMap?.mapType = BaiduMap.MAP_TYPE_SATELLITE
            }
        }

        findViewById<ImageButton>(R.id.cur_position)?.setOnClickListener { resetMap() }
        findViewById<ImageButton>(R.id.zoom_in)?.setOnClickListener {
            mBaiduMap?.animateMapStatus(MapStatusUpdateFactory.zoomIn())
        }
        findViewById<ImageButton>(R.id.zoom_out)?.setOnClickListener {
            mBaiduMap?.animateMapStatus(MapStatusUpdateFactory.zoomOut())
        }

        findViewById<ImageButton>(R.id.input_pos)?.setOnClickListener {
            showInputCoordinateDialog()
        }
    }

    private fun showInputCoordinateDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.location_input, null)
        val dialog = AlertDialog.Builder(this)
            .setTitle("輸入自定義座標")
            .setView(view)
            .show()

        val etLng = view.findViewById<EditText>(R.id.joystick_longitude)
        val etLat = view.findViewById<EditText>(R.id.joystick_latitude)
        val rbBD = view.findViewById<RadioButton>(R.id.pos_type_bd)

        view.findViewById<Button>(R.id.input_position_ok).setOnClickListener {
            val lngStr = etLng.text.toString().trim()
            val latStr = etLat.text.toString().trim()
            if (lngStr.isEmpty() || latStr.isEmpty()) {
                GoUtils.DisplayToast(this, getString(R.string.app_error_input))
                return@setOnClickListener
            }
            val lng = lngStr.toDoubleOrNull() ?: return@setOnClickListener
            val lat = latStr.toDoubleOrNull() ?: return@setOnClickListener

            if (lng < -180.0 || lng > 180.0 || lat < -90.0 || lat > 90.0) {
                GoUtils.DisplayToast(this, "座標超出有效範圍")
                return@setOnClickListener
            }

            mMarkLatLngMap = if (rbBD.isChecked) {
                LatLng(lat, lng)
            } else {
                val bd = MapUtils.wgs2bd09(lng, lat)
                LatLng(bd[1], bd[0])
            }
            mMarkName = String.format(Locale.getDefault(), "自定義坐標 (%.4f, %.4f)", lng, lat)
            markMap()
            mMarkLatLngMap?.let {
                mBaiduMap?.setMapStatus(MapStatusUpdateFactory.newLatLng(it))
                updatePinText(it)
            }
            dialog.dismiss()
        }

        view.findViewById<Button>(R.id.input_position_cancel).setOnClickListener {
            dialog.dismiss()
        }
    }

    // ==========================================
    // Search & History
    // ==========================================
    private fun initStoreHistory() {
        try {
            mLocationHistoryDB = DataBaseHistoryLocation(applicationContext).writableDatabase
            mSearchHistoryDB = DataBaseHistorySearch(applicationContext).writableDatabase
        } catch (e: Exception) {
            XLog.e("ERROR: sqlite init", e)
        }
    }

    private fun initSearchView() {
        mSearchLayout = findViewById(R.id.search_linear)
        mSearchList = findViewById(R.id.search_list_view)
        mHistoryLayout = findViewById(R.id.search_history_linear)
        mSearchHistoryList = findViewById(R.id.search_history_list_view)

        mSuggestionSearch = SuggestionSearch.newInstance().apply {
            setOnGetSuggestionResultListener { result ->
                val suggestions = result?.allSuggestions
                if (suggestions.isNullOrEmpty()) {
                    GoUtils.DisplayToast(this@MainActivity, getString(R.string.app_search_null))
                } else {
                    val data = suggestions.mapNotNull { sug ->
                        sug.pt?.let { pt ->
                            mapOf(
                                POI_NAME to (sug.key ?: ""),
                                POI_ADDRESS to "${sug.city ?: ""} ${sug.district ?: ""}",
                                POI_LONGITUDE to pt.longitude.toString(),
                                POI_LATITUDE to pt.latitude.toString()
                            )
                        }
                    }
                    val adapter = SimpleAdapter(
                        this@MainActivity,
                        data,
                        R.layout.search_poi_item,
                        arrayOf(POI_NAME, POI_ADDRESS, POI_LONGITUDE, POI_LATITUDE),
                        intArrayOf(R.id.poi_name, R.id.poi_address, R.id.poi_longitude, R.id.poi_latitude)
                    )
                    mSearchList.adapter = adapter
                    mSearchLayout.visibility = View.VISIBLE
                }
            }
        }

        mSearchList.setOnItemClickListener { _, view, _, _ ->
            val lat = view.findViewById<TextView>(R.id.poi_latitude).text.toString().toDoubleOrNull() ?: return@setOnItemClickListener
            val lng = view.findViewById<TextView>(R.id.poi_longitude).text.toString().toDoubleOrNull() ?: return@setOnItemClickListener
            mMarkName = view.findViewById<TextView>(R.id.poi_name).text.toString()
            mMarkLatLngMap = LatLng(lat, lng)
            mBaiduMap?.setMapStatus(MapStatusUpdateFactory.newLatLng(mMarkLatLngMap))
            markMap()
            mMarkLatLngMap?.let { updatePinText(it) }
            mSearchLayout.visibility = View.GONE
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        val searchItem = menu?.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as? SearchView

        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (!query.isNullOrEmpty()) {
                    mSuggestionSearch?.requestSuggestion(
                        SuggestionSearchOption().city(mCurrentCity.ifEmpty { "全国" }).keyword(query)
                    )
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText.isNullOrEmpty()) {
                    mSearchLayout.visibility = View.GONE
                } else {
                    mSuggestionSearch?.requestSuggestion(
                        SuggestionSearchOption().city(mCurrentCity.ifEmpty { "全国" }).keyword(newText)
                    )
                }
                return true
            }
        })

        return super.onCreateOptionsMenu(menu)
    }

    private fun recordCurrentLocation(lng: Double, lat: Double, customName: String? = null) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val latLngWgs = MapUtils.bd2wgs(lng, lat)
                val locationName = when {
                    !customName.isNullOrBlank() -> customName
                    !mMarkName.isNullOrBlank() -> mMarkName!!
                    else -> String.format(Locale.getDefault(), "目標坐標 (%.4f, %.4f)", lng, lat)
                }
                val values = ContentValues().apply {
                    put(DataBaseHistoryLocation.DB_COLUMN_LOCATION, locationName)
                    put(DataBaseHistoryLocation.DB_COLUMN_LONGITUDE_WGS84, latLngWgs[0].toString())
                    put(DataBaseHistoryLocation.DB_COLUMN_LATITUDE_WGS84, latLngWgs[1].toString())
                    put(DataBaseHistoryLocation.DB_COLUMN_TIMESTAMP, System.currentTimeMillis() / 1000)
                    put(DataBaseHistoryLocation.DB_COLUMN_LONGITUDE_CUSTOM, lng.toString())
                    put(DataBaseHistoryLocation.DB_COLUMN_LATITUDE_CUSTOM, lat.toString())
                }
                mLocationHistoryDB?.let { db ->
                    DataBaseHistoryLocation.saveHistoryLocation(db, values)
                }
                XLog.i("Saved location history: $locationName at ($lng, $lat)")
            } catch (e: Exception) {
                XLog.e("ERROR: recordCurrentLocation", e)
            }
        }
    }

    // ==========================================
    // Sensor Listener
    // ==========================================
    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> lastAccelerometer = event.values.clone()
            Sensor.TYPE_MAGNETIC_FIELD -> lastMagnetic = event.values.clone()
        }
        val acc = lastAccelerometer ?: return
        val mag = lastMagnetic ?: return

        val r = FloatArray(9)
        val values = FloatArray(3)
        if (SensorManager.getRotationMatrix(r, null, acc, mag)) {
            SensorManager.getOrientation(r, values)
            var degree = Math.toDegrees(values[0].toDouble()).toFloat()
            if (degree < 0) degree += 360f
            mCurrentDirection = degree.toInt()
            val locData = MyLocationData.Builder()
                .direction(degree)
                .latitude(mCurrentLat)
                .longitude(mCurrentLon)
                .build()
            mBaiduMap?.setMyLocationData(locData)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
