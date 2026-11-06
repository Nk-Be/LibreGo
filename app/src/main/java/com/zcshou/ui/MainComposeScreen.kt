package com.zcshou.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zcshou.gogogo.MainActivity
import com.zcshou.gogogo.R
import com.zcshou.service.MockLocationEngine
import com.zcshou.shizuku.ShizukuManager
import com.zcshou.ui.components.JoystickCard
import com.zcshou.ui.components.ShizukuPrivilegeCard
import com.zcshou.ui.components.StatusCard
import com.zcshou.ui.components.TopSearchBar
import com.zcshou.ui.map.OsmSearchHelper
import com.zcshou.ui.map.OsmTileSources
import com.zcshou.utils.GoUtils
import com.zcshou.utils.MapUtils
import kotlinx.coroutines.launch
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

@Composable
fun MainComposeScreen(
    mapView: MapView,
    onToggleMock: (targetLng: Double?, targetLat: Double?) -> Unit,
    onToggleJoystick: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    isJoystickVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val locationState by MockLocationEngine.locationState.collectAsStateWithLifecycle()
    val shizukuState by ShizukuManager.state.collectAsStateWithLifecycle()

    var targetPoint by remember { mutableStateOf<GeoPoint?>(GeoPoint(39.908722, 116.397496)) }
    var targetName by remember { mutableStateOf<String?>("北京市 (點擊地圖選擇位置)") }
    var currentSpeedPreset by remember { mutableIntStateOf(1) } // 0: walk, 1: run, 2: bike

    var showCoordDialog by remember { mutableStateOf(false) }
    var showPrivilegeDialog by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showTileDialog by remember { mutableStateOf(false) }

    // Tile source index: 0: AMap Vector (default), 1: AMap Satellite, 2: OSM Standard
    var currentTileSourceIndex by remember { mutableIntStateOf(0) }

    val pinDrawable = remember { ContextCompat.getDrawable(context, R.drawable.icon_gcoding) }
    var targetMarker by remember { mutableStateOf<Marker?>(null) }
    var mockMarker by remember { mutableStateOf<Marker?>(null) }

    // Check for pending location from HistoryActivity
    LaunchedEffect(MainActivity.pendingLocationLng, MainActivity.pendingLocationLat) {
        val pLng = MainActivity.pendingLocationLng
        val pLat = MainActivity.pendingLocationLat
        if (pLng != null && pLat != null) {
            val pt = GeoPoint(pLat, pLng)
            targetPoint = pt
            targetName = MainActivity.pendingLocationName ?: "歷史記錄位置"
            mapView.controller.animateTo(pt)

            targetMarker?.let { mapView.overlays.remove(it) }
            val m = Marker(mapView).apply {
                position = pt
                icon = pinDrawable
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = targetName
            }
            mapView.overlays.add(m)
            targetMarker = m
            mapView.invalidate()

            MainActivity.pendingLocationLng = null
            MainActivity.pendingLocationLat = null
            MainActivity.pendingLocationName = null
        }
    }

    // Configure osmdroid MapView overlays and events
    DisposableEffect(mapView) {
        mapView.setMultiTouchControls(true)
        mapView.isTilesScaledToDpi = true
        mapView.setTileSource(OsmTileSources.AMAP_VECTOR)
        mapView.controller.setZoom(16.0)
        targetPoint?.let { mapView.controller.setCenter(it) }

        // Setup default pin
        targetPoint?.let { pt ->
            val m = Marker(mapView).apply {
                position = pt
                icon = pinDrawable
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = targetName
            }
            mapView.overlays.add(m)
            targetMarker = m
        }

        // Tap listener overlay
        val eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                targetPoint = p
                targetName = "載入地址中..."

                targetMarker?.let { mapView.overlays.remove(it) }
                val m = Marker(mapView).apply {
                    position = p
                    icon = pinDrawable
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
                mapView.overlays.add(m)
                targetMarker = m
                mapView.invalidate()

                scope.launch {
                    targetName = OsmSearchHelper.reverseGeocode(p.latitude, p.longitude)
                    m.title = targetName
                }
                return true
            }

            override fun longPressHelper(p: GeoPoint): Boolean {
                return singleTapConfirmedHelper(p)
            }
        })
        mapView.overlays.add(0, eventsOverlay)
        mapView.invalidate()

        onDispose {
            mapView.overlays.clear()
        }
    }

    // Update real-time mock location marker
    LaunchedEffect(locationState) {
        if (locationState.isMocking) {
            val pt = GeoPoint(locationState.latitude, locationState.longitude)
            if (mockMarker == null) {
                val m = Marker(mapView).apply {
                    position = pt
                    icon = pinDrawable
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "即時模擬點"
                }
                mapView.overlays.add(m)
                mockMarker = m
            } else {
                mockMarker?.position = pt
            }
            mapView.invalidate()
        } else {
            mockMarker?.let {
                mapView.overlays.remove(it)
                mockMarker = null
                mapView.invalidate()
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Edge-to-Edge OpenStreetMap View
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Floating Top Search Capsule
        val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        TopSearchBar(
            queryText = targetName ?: if (targetPoint != null) "${targetPoint?.latitude}, ${targetPoint?.longitude}" else "",
            onSearchClick = { showSearchDialog = true },
            onHistoryClick = onOpenHistory,
            onApiKeyClick = { showTileDialog = true },
            onSettingsClick = onOpenSettings,
            modifier = Modifier
                .padding(top = statusBarPadding + 8.dp, start = 16.dp, end = 16.dp)
                .align(Alignment.TopCenter)
        )

        // 3. Floating Right Map Controls
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilledTonalIconButton(
                onClick = {
                    targetPoint?.let {
                        mapView.controller.animateTo(it)
                    } ?: run {
                        mapView.controller.zoomIn()
                    }
                },
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(16.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                )
            ) {
                Text(text = "🎯", fontSize = 18.sp)
            }

            FilledTonalIconButton(
                onClick = { showTileDialog = true },
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(16.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                )
            ) {
                Text(text = "🗺️", fontSize = 18.sp)
            }

            FilledTonalIconButton(
                onClick = { showCoordDialog = true },
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(16.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                )
            ) {
                Text(text = "⌨️", fontSize = 18.sp)
            }
        }

        // 4. Bottom Shizuku Card Stack
        val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, bottom = navBarPadding + 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatusCard(
                locationState = locationState,
                targetName = targetName,
                targetLat = targetPoint?.latitude,
                targetLng = targetPoint?.longitude,
                onToggleMock = {
                    onToggleMock(targetPoint?.longitude, targetPoint?.latitude)
                }
            )

            ShizukuPrivilegeCard(
                shizukuState = shizukuState,
                onOpenPrivilegeDialog = {
                    showPrivilegeDialog = true
                }
            )

            JoystickCard(
                isJoystickVisible = isJoystickVisible,
                currentSpeedPreset = currentSpeedPreset,
                onToggleJoystick = onToggleJoystick,
                onSelectSpeedPreset = { preset ->
                    currentSpeedPreset = preset
                    val speedMs = when (preset) {
                        0 -> 1.2
                        1 -> 3.5
                        else -> 6.0
                    }
                    MockLocationEngine.setSpeed(speedMs)
                }
            )
        }
    }

    // Dialog: Custom Coordinates
    if (showCoordDialog) {
        CustomCoordDialog(
            onDismiss = { showCoordDialog = false },
            onConfirm = { lng, lat, isBD09 ->
                val finalPt = if (isBD09) {
                    val wgs = MapUtils.bd2wgs(lng, lat)
                    GeoPoint(wgs[1], wgs[0])
                } else {
                    GeoPoint(lat, lng)
                }
                targetPoint = finalPt
                targetName = "自定義坐標 (${String.format("%.4f", finalPt.longitude)}, ${String.format("%.4f", finalPt.latitude)})"

                targetMarker?.let { mapView.overlays.remove(it) }
                val m = Marker(mapView).apply {
                    position = finalPt
                    icon = pinDrawable
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = targetName
                }
                mapView.overlays.add(m)
                targetMarker = m
                mapView.controller.animateTo(finalPt)
                mapView.invalidate()
                showCoordDialog = false
            }
        )
    }

    // Dialog: Tile Source Switcher (OpenStreetMap & Public AutoNavi)
    if (showTileDialog) {
        TileSourceDialog(
            selectedIndex = currentTileSourceIndex,
            onDismiss = { showTileDialog = false },
            onSelect = { idx ->
                currentTileSourceIndex = idx
                when (idx) {
                    0 -> mapView.setTileSource(OsmTileSources.AMAP_VECTOR)
                    1 -> mapView.setTileSource(OsmTileSources.AMAP_SATELLITE)
                    2 -> mapView.setTileSource(OsmTileSources.OSM_MAPNIK)
                }
                mapView.invalidate()
                showTileDialog = false
            }
        )
    }

    // Dialog: Shizuku Privilege Center
    if (showPrivilegeDialog) {
        ShizukuPrivilegeDialog(
            state = shizukuState,
            onDismiss = { showPrivilegeDialog = false },
            onGrantAll = {
                scope.launch {
                    val res = ShizukuManager.grantAllPrivileges(context)
                    if (res.first) {
                        GoUtils.DisplayToast(context, "Shizuku 特權已全部配置成功！")
                    } else {
                        GoUtils.DisplayToast(context, "配置異常: ${res.second}")
                    }
                    showPrivilegeDialog = false
                }
            },
            onRequestPerm = {
                if (context is androidx.activity.ComponentActivity) {
                    ShizukuManager.requestPermission(context)
                }
                showPrivilegeDialog = false
            }
        )
    }

    // Dialog: Search Location (OpenStreetMap / Photon)
    if (showSearchDialog) {
        SearchLocationDialog(
            onDismiss = { showSearchDialog = false },
            onSelectLocation = { name, lat, lng ->
                val pt = GeoPoint(lat, lng)
                targetPoint = pt
                targetName = name

                targetMarker?.let { mapView.overlays.remove(it) }
                val m = Marker(mapView).apply {
                    position = pt
                    icon = pinDrawable
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = name
                }
                mapView.overlays.add(m)
                targetMarker = m
                mapView.controller.animateTo(pt)
                mapView.invalidate()
                showSearchDialog = false
            }
        )
    }
}

@Composable
private fun CustomCoordDialog(
    onDismiss: () -> Unit,
    onConfirm: (lng: Double, lat: Double, isBD09: Boolean) -> Unit
) {
    var lngText by remember { mutableStateOf("") }
    var latText by remember { mutableStateOf("") }
    var isWGS84 by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("輸入自定義座標", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = lngText,
                    onValueChange = { lngText = it },
                    label = { Text("經度 (Longitude, 如 116.3974)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = latText,
                    onValueChange = { latText = it },
                    label = { Text("緯度 (Latitude, 如 39.9087)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = isWGS84, onClick = { isWGS84 = true })
                    Text("真實標準 / OSM (WGS84)", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.width(12.dp))
                    RadioButton(selected = !isWGS84, onClick = { isWGS84 = false })
                    Text("百度坐標 (BD09)", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val lng = lngText.toDoubleOrNull()
                    val lat = latText.toDoubleOrNull()
                    if (lng != null && lat != null) {
                        onConfirm(lng, lat, !isWGS84)
                    }
                }
            ) {
                Text("定位至該點")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun TileSourceDialog(
    selectedIndex: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val options = listOf(
        Pair("高德公開路網 (推薦)", "免 Key、中文路網精確、國內 CDN 加速"),
        Pair("高德公開衛星空拍", "高畫質衛星航拍照、無偏移覆蓋"),
        Pair("OpenStreetMap 標準圖源", "全球開源社群維護標準地圖")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🗺️ 公開地圖圖層切換", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "當前採用 100% 公開地圖架構，無須任何 API Key，永遠無套件名限制！",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                options.forEachIndexed { index, (title, desc) ->
                    Card(
                        onClick = { onSelect(index) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedIndex == index) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedIndex == index, onClick = { onSelect(index) })
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("關閉")
            }
        }
    )
}

@Composable
private fun ShizukuPrivilegeDialog(
    state: ShizukuManager.ShizukuState,
    onDismiss: () -> Unit,
    onGrantAll: () -> Unit,
    onRequestPerm: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("⚡ Shizuku 特權中心", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    !state.isInstalled -> {
                        Text("未檢測到 Shizuku 安裝。\n\nShizuku 支援免 Root 一秒自動配置模擬位置權限與懸浮窗。")
                    }
                    !state.isBinderAlive -> {
                        Text("檢測到已安裝 Shizuku，但服務尚未啟動。\n\n請打開 Shizuku 使用「無線除錯」啟動服務。")
                    }
                    !state.hasPermission -> {
                        Text("Shizuku 服務運行中 (v${state.version})！\n\n需要授予 LibreGo 存取 Shizuku 的特權。")
                    }
                    else -> {
                        Text("Shizuku 特權正常運行 (v${state.version})！\n\n點擊下方按鈕即可一鍵全自動配置：\n• AppOps 模擬位置授權\n• 系統懸浮窗覆蓋權限\n• 後台電池防殺白名單")
                    }
                }
            }
        },
        confirmButton = {
            when {
                !state.isInstalled -> {
                    Button(onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")))
                        } catch (_: Exception) {}
                        onDismiss()
                    }) {
                        Text("前往官網下載")
                    }
                }
                !state.isBinderAlive -> {
                    Button(onClick = {
                        try {
                            val intent = context.packageManager.getLaunchIntentForPackage(ShizukuManager.SHIZUKU_PACKAGE)
                            if (intent != null) context.startActivity(intent)
                        } catch (_: Exception) {}
                        onDismiss()
                    }) {
                        Text("打開 Shizuku")
                    }
                }
                !state.hasPermission -> {
                    Button(onClick = onRequestPerm) {
                        Text("請求特權授權")
                    }
                }
                else -> {
                    Button(onClick = onGrantAll) {
                        Text("一鍵全自動配置")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("關閉")
            }
        }
    )
}

@Composable
private fun SearchLocationDialog(
    onDismiss: () -> Unit,
    onSelectLocation: (name: String, lat: Double, lng: Double) -> Unit
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val results = remember { mutableStateListOf<OsmSearchHelper.PlaceResult>() }
    var isSearching by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("搜尋目標地點 (公開檢索)", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        if (it.length >= 2) {
                            isSearching = true
                            scope.launch {
                                val list = OsmSearchHelper.searchPlaces(it)
                                results.clear()
                                results.addAll(list)
                                isSearching = false
                            }
                        } else {
                            results.clear()
                        }
                    },
                    label = { Text("輸入地名 / 商圈 / 地址") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (isSearching) {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                        items(results) { item ->
                            Card(
                                onClick = { onSelectLocation(item.title, item.latitude, item.longitude) },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = item.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("關閉")
            }
        }
    )
}
