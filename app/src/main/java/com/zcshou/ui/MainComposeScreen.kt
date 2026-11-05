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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baidu.mapapi.map.BaiduMap
import com.baidu.mapapi.map.BitmapDescriptor
import com.baidu.mapapi.map.BitmapDescriptorFactory
import com.baidu.mapapi.map.MapPoi
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.MapView
import com.baidu.mapapi.map.Marker
import com.baidu.mapapi.map.MarkerOptions
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.search.core.SearchResult
import com.baidu.mapapi.search.geocode.GeoCodeResult
import com.baidu.mapapi.search.geocode.GeoCoder
import com.baidu.mapapi.search.geocode.OnGetGeoCoderResultListener
import com.baidu.mapapi.search.geocode.ReverseGeoCodeOption
import com.baidu.mapapi.search.geocode.ReverseGeoCodeResult
import com.baidu.mapapi.search.sug.OnGetSuggestionResultListener
import com.baidu.mapapi.search.sug.SuggestionResult
import com.baidu.mapapi.search.sug.SuggestionSearch
import com.baidu.mapapi.search.sug.SuggestionSearchOption
import com.zcshou.gogogo.R
import com.zcshou.service.MockLocationEngine
import com.zcshou.shizuku.ShizukuManager
import com.zcshou.ui.components.JoystickCard
import com.zcshou.ui.components.ShizukuPrivilegeCard
import com.zcshou.ui.components.StatusCard
import com.zcshou.ui.components.TopSearchBar
import com.zcshou.utils.GoUtils
import com.zcshou.utils.MapUtils
import kotlinx.coroutines.launch

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

    var targetLatLng by remember { mutableStateOf<LatLng?>(null) }
    var targetName by remember { mutableStateOf<String?>(null) }
    var currentSpeedPreset by remember { mutableIntStateOf(1) } // 0: walk, 1: run, 2: bike

    var showCoordDialog by remember { mutableStateOf(false) }
    var showPrivilegeDialog by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSatelliteMap by remember { mutableStateOf(false) }

    val pinIcon = remember { BitmapDescriptorFactory.fromResource(R.drawable.icon_gcoding) }
    var currentMarker by remember { mutableStateOf<Marker?>(null) }

    // Geocoder
    val geoCoder = remember {
        GeoCoder.newInstance().apply {
            setOnGetGeoCodeResultListener(object : OnGetGeoCoderResultListener {
                override fun onGetGeoCodeResult(result: GeoCodeResult?) {}
                override fun onGetReverseGeoCodeResult(result: ReverseGeoCodeResult?) {
                    if (result != null && result.error == SearchResult.ERRORNO.NO_ERROR) {
                        targetName = result.address
                    }
                }
            })
        }
    }

    DisposableEffect(Unit) {
        val map = mapView.map
        map.setOnMapClickListener(object : BaiduMap.OnMapClickListener {
            override fun onMapClick(point: LatLng?) {
                point?.let {
                    targetLatLng = it
                    targetName = null
                    map.clear()
                    currentMarker = map.addOverlay(MarkerOptions().position(it).icon(pinIcon)) as? Marker
                    geoCoder.reverseGeoCode(ReverseGeoCodeOption().location(it))
                }
            }

            override fun onMapPoiClick(poi: MapPoi?) {
                poi?.position?.let {
                    targetLatLng = it
                    targetName = poi.name
                    map.clear()
                    currentMarker = map.addOverlay(MarkerOptions().position(it).icon(pinIcon)) as? Marker
                }
            }
        })

        onDispose {
            geoCoder.destroy()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Edge-to-Edge Map View
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Floating Top Search Capsule
        val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        TopSearchBar(
            queryText = targetName ?: if (targetLatLng != null) "${targetLatLng?.latitude}, ${targetLatLng?.longitude}" else "",
            onSearchClick = { showSearchDialog = true },
            onHistoryClick = onOpenHistory,
            onApiKeyClick = { showApiKeyDialog = true },
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
                    val map = mapView.map
                    targetLatLng?.let {
                        map.animateMapStatus(MapStatusUpdateFactory.newLatLng(it))
                    } ?: run {
                        map.animateMapStatus(MapStatusUpdateFactory.zoomIn())
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
                onClick = {
                    isSatelliteMap = !isSatelliteMap
                    mapView.map.mapType = if (isSatelliteMap) BaiduMap.MAP_TYPE_SATELLITE else BaiduMap.MAP_TYPE_NORMAL
                },
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
                targetLat = targetLatLng?.latitude,
                targetLng = targetLatLng?.longitude,
                onToggleMock = {
                    onToggleMock(targetLatLng?.longitude, targetLatLng?.latitude)
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
                val finalLatLng = if (isBD09) {
                    LatLng(lat, lng)
                } else {
                    val bd = MapUtils.wgs2bd09(lng, lat)
                    LatLng(bd[1], bd[0])
                }
                targetLatLng = finalLatLng
                targetName = "自定義坐標 (${String.format("%.4f", lng)}, ${String.format("%.4f", lat)})"
                val map = mapView.map
                map.clear()
                currentMarker = map.addOverlay(MarkerOptions().position(finalLatLng).icon(pinIcon)) as? Marker
                map.setMapStatus(MapStatusUpdateFactory.newLatLng(finalLatLng))
                showCoordDialog = false
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

    // Dialog: Search Location
    if (showSearchDialog) {
        SearchLocationDialog(
            onDismiss = { showSearchDialog = false },
            onSelectLocation = { name, lat, lng ->
                val pt = LatLng(lat, lng)
                targetLatLng = pt
                targetName = name
                val map = mapView.map
                map.clear()
                currentMarker = map.addOverlay(MarkerOptions().position(pt).icon(pinIcon)) as? Marker
                map.setMapStatus(MapStatusUpdateFactory.newLatLng(pt))
                showSearchDialog = false
            }
        )
    }

    // Dialog: Custom API Key
    if (showApiKeyDialog) {
        CustomApiKeyDialog(
            onDismiss = { showApiKeyDialog = false }
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
    var isBD09 by remember { mutableStateOf(true) }

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
                    RadioButton(selected = isBD09, onClick = { isBD09 = true })
                    Text("百度坐標 (BD09)", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.width(12.dp))
                    RadioButton(selected = !isBD09, onClick = { isBD09 = false })
                    Text("真實/高德 (WGS84)", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val lng = lngText.toDoubleOrNull()
                    val lat = latText.toDoubleOrNull()
                    if (lng != null && lat != null) {
                        onConfirm(lng, lat, isBD09)
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
    var query by remember { mutableStateOf("") }
    val results = remember { mutableStateListOf<Pair<String, LatLng>>() }
    var isSearching by remember { mutableStateOf(false) }

    val suggestionSearch = remember {
        SuggestionSearch.newInstance().apply {
            setOnGetSuggestionResultListener(OnGetSuggestionResultListener { res ->
                isSearching = false
                results.clear()
                res?.allSuggestions?.forEach { s ->
                    val pt = s.pt
                    if (pt != null && !s.key.isNullOrBlank()) {
                        results.add(Pair("${s.key} (${s.city ?: ""}${s.district ?: ""})", pt))
                    }
                }
            })
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            suggestionSearch.destroy()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("搜尋目標地點", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        if (it.isNotBlank()) {
                            isSearching = true
                            suggestionSearch.requestSuggestion(
                                SuggestionSearchOption().city("全国").keyword(it)
                            )
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
                                onClick = { onSelectLocation(item.first, item.second.latitude, item.second.longitude) },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = item.first,
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
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
private fun CustomApiKeyDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { androidx.preference.PreferenceManager.getDefaultSharedPreferences(context) }
    var keyText by remember { mutableStateOf(prefs.getString("custom_maps_api_key", "") ?: "") }
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    val packageName = "top.nkbe.libre"
    val sha1 = "C7:39:86:A1:EA:2E:AD:D4:B3:CF:63:CA:A4:2B:C6:AC:2B:59:E2:B7"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🔑 自訂地圖 API Key", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "百度地圖官方嚴格要求 API Key 必須綁定白名單【應用包名 + 簽名 SHA1】。若地圖白屏或搜尋無反應，請填入您自己的百度地圖 AK。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("應用包名", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text(packageName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                            TextButton(onClick = {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(packageName))
                                GoUtils.DisplayToast(context, "已複製包名")
                            }) {
                                Text("複製", fontSize = 11.sp)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("簽名 SHA1", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text(sha1, style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace), maxLines = 1)
                            }
                            TextButton(onClick = {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(sha1))
                                GoUtils.DisplayToast(context, "已複製 SHA1")
                            }) {
                                Text("複製", fontSize = 11.sp)
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = keyText,
                    onValueChange = { keyText = it },
                    label = { Text("百度地圖 API Key (AK)") },
                    placeholder = { Text("貼上您的專屬 API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalKey = keyText.trim()
                    prefs.edit().putString("custom_maps_api_key", finalKey).apply()
                    if (finalKey.isNotEmpty()) {
                        com.baidu.mapapi.SDKInitializer.setApiKey(finalKey)
                        GoUtils.DisplayToast(context, "自訂 API Key 已保存並生效！")
                    } else {
                        prefs.edit().remove("custom_maps_api_key").apply()
                        com.baidu.mapapi.SDKInitializer.setApiKey(com.zcshou.gogogo.BuildConfig.MAPS_API_KEY)
                        GoUtils.DisplayToast(context, "已恢復預設 API Key")
                    }
                    onDismiss()
                }
            ) {
                Text("保存並生效")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
