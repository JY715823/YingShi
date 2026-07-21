package com.example.yingshi.feature.life

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.CoordinateConverter
import com.amap.api.maps.MapView
import com.amap.api.maps.model.CameraPosition
import com.amap.api.maps.model.LatLng
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.core.ServiceSettings
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.RegeocodeQuery
import com.amap.api.services.geocoder.RegeocodeResult
import com.amap.api.services.geocoder.GeocodeResult
import com.amap.api.services.help.Inputtips
import com.amap.api.services.help.InputtipsQuery
import com.amap.api.services.help.Tip
import com.example.yingshi.ui.theme.YingShiTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Round 7 阶段 7: 位置选择页。
 *
 * - 高德 MapView 显示地图，中心固定 marker (Compose 图标覆盖层)
 * - 拖动地图 → 中心点变化 → 逆地理编码 → 更新底部地址条
 * - 顶部搜索框 → Inputtips 自动补全 → 点击某 tip → 地图移动到该点
 * - "重新定位"按钮 → LocationHelper (WGS-84) → 转 GCJ-02 → 地图移动
 * - "确定"按钮 → 返回选中的 lat/lng/label (GCJ-02) 给调用方
 *
 * 坐标系说明：高德 MapView 使用 GCJ-02。LocationHelper 返回 WGS-84，
 * 通过 CoordinateConverter 转 GCJ-02 后再显示。返回值 lat/lng 为 GCJ-02。
 */
class LifeLocationPickerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 高德隐私合规初始化 (SDK 9.6.0+ 必须)
        runCatching {
            com.amap.api.maps.MapsInitializer.updatePrivacyShow(this, true, true)
            com.amap.api.maps.MapsInitializer.updatePrivacyAgree(this, true)
            ServiceSettings.updatePrivacyShow(this, true, true)
            ServiceSettings.updatePrivacyAgree(this, true)
        }
        val initialLat = intent.getDoubleExtra(EXTRA_INITIAL_LAT, Double.NaN).takeIf { !it.isNaN() }
        val initialLng = intent.getDoubleExtra(EXTRA_INITIAL_LNG, Double.NaN).takeIf { !it.isNaN() }
        val initialLabel = intent.getStringExtra(EXTRA_INITIAL_LABEL)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "选择位置"

        setContent {
            YingShiTheme {
                LifeLocationPickerScreen(
                    initialLat = initialLat,
                    initialLng = initialLng,
                    initialLabel = initialLabel,
                    title = title,
                    onConfirm = { lat, lng, label ->
                        val data = Intent().apply {
                            putExtra(EXTRA_RESULT_LAT, lat)
                            putExtra(EXTRA_RESULT_LNG, lng)
                            putExtra(EXTRA_RESULT_LABEL, label)
                        }
                        setResult(Activity.RESULT_OK, data)
                        finish()
                    },
                    onBack = {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    },
                )
            }
        }
    }

    companion object {
        internal const val EXTRA_INITIAL_LAT = "life_location_picker_initial_lat"
        internal const val EXTRA_INITIAL_LNG = "life_location_picker_initial_lng"
        internal const val EXTRA_INITIAL_LABEL = "life_location_picker_initial_label"
        internal const val EXTRA_TITLE = "life_location_picker_title"
        internal const val EXTRA_RESULT_LAT = "life_location_picker_result_lat"
        internal const val EXTRA_RESULT_LNG = "life_location_picker_result_lng"
        internal const val EXTRA_RESULT_LABEL = "life_location_picker_result_label"

        fun intent(
            context: Context,
            initialLat: Double? = null,
            initialLng: Double? = null,
            initialLabel: String? = null,
            title: String = "选择位置",
        ): Intent {
            return Intent(context, LifeLocationPickerActivity::class.java).apply {
                initialLat?.let { putExtra(EXTRA_INITIAL_LAT, it) }
                initialLng?.let { putExtra(EXTRA_INITIAL_LNG, it) }
                initialLabel?.let { putExtra(EXTRA_INITIAL_LABEL, it) }
                putExtra(EXTRA_TITLE, title)
            }
        }
    }
}

@Composable
private fun LifeLocationPickerScreen(
    initialLat: Double?,
    initialLng: Double?,
    initialLabel: String?,
    title: String,
    onConfirm: (Double, Double, String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var aMap by remember { mutableStateOf<AMap?>(null) }
    var selectedLatLng by remember { mutableStateOf<LatLng?>(null) }
    var addressLabel by remember { mutableStateOf(initialLabel ?: "") }
    var isLocating by remember { mutableStateOf(false) }
    var isGeocoding by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val searchResults = remember { mutableStateListOf<Tip>() }
    var isSearching by remember { mutableStateOf(false) }
    val geocodeSearch = remember { GeocodeSearch(context) }

    // 初始地图定位
    LaunchedEffect(aMap) {
        val map = aMap ?: return@LaunchedEffect
        val target = if (initialLat != null && initialLng != null) {
            LatLng(initialLat, initialLng)
        } else {
            // Round 8 第十轮: LocationHelper 已返回 GCJ-02, 直接用, 不再做二次转换.
            val loc = withContext(Dispatchers.IO) { LocationHelper.currentLocation(context) }
            if (loc != null) {
                LatLng(loc.latitude, loc.longitude)
            } else {
                LatLng(39.9042, 116.4074) // 默认: 北京
            }
        }
        selectedLatLng = target
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(target, 16f))
        if (initialLabel.isNullOrBlank() && initialLat != null && initialLng != null) {
            triggerReverseGeocode(geocodeSearch, target) { label ->
                addressLabel = label
            }
        }
    }

    // 地图相机变化监听 → 逆地理编码
    DisposableEffect(aMap) {
        val map = aMap ?: return@DisposableEffect onDispose {}
        val listener = object : AMap.OnCameraChangeListener {
            override fun onCameraChange(position: CameraPosition?) {}
            override fun onCameraChangeFinish(position: CameraPosition?) {
                val target = position?.target ?: return
                selectedLatLng = target
                isGeocoding = true
                triggerReverseGeocode(geocodeSearch, target) { label ->
                    addressLabel = label
                    isGeocoding = false
                }
            }
        }
        map.setOnCameraChangeListener(listener)
        onDispose { map.setOnCameraChangeListener(null) }
    }

    // 搜索 debounce + Inputtips
    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            searchResults.clear()
            isSearching = false
            return@LaunchedEffect
        }
        delay(350) // debounce
        isSearching = true
        val query = InputtipsQuery(searchQuery, "") // 空城市 = 全国搜索
        val inputtips = Inputtips(context, query)
        inputtips.setInputtipsListener { tips, _ ->
            searchResults.clear()
            tips?.filter { it.point != null }?.let { searchResults.addAll(it) }
            isSearching = false
        }
        inputtips.requestInputtipsAsyn()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
    ) {
        // 地图层
        AndroidView(
            factory = { ctx ->
                MapView(ctx).also { mv ->
                    mv.onCreate(null)
                    mv.onResume()
                    aMap = mv.map
                    mapView = mv
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // 中心 marker (固定在屏幕中央)
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.LocationOn,
                contentDescription = null,
                tint = Color(0xFFE53935),
                modifier = Modifier.size(40.dp),
            )
        }

        // 顶部栏: 返回 + 标题 + 搜索框
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .background(Color.White.copy(alpha = 0.96f)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回",
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
            }
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("搜索地点") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { /* 触发 Inputtips 已自动 */ }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(24.dp),
            )
            // 搜索结果下拉
            if (searchResults.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .background(Color.White),
                ) {
                    items(searchResults) { tip ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val point = tip.point ?: return@clickable
                                    val latLng = LatLng(point.latitude, point.longitude)
                                    selectedLatLng = latLng
                                    addressLabel = tip.name ?: tip.address ?: ""
                                    aMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
                                    searchQuery = ""
                                    searchResults.clear()
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LocationOn,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = tip.name ?: "未知",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                tip.address?.takeIf { it.isNotBlank() }?.let { addr ->
                                    Text(
                                        text = addr,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 底部栏: 当前地址 + 重新定位 + 确定
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            color = Color.White,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isGeocoding) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = addressLabel.ifBlank { "拖动地图选择位置" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            if (isLocating) return@TextButton
                            isLocating = true
                            scope.launch {
                                val loc = withContext(Dispatchers.IO) {
                                    LocationHelper.currentLocation(context)
                                }
                                isLocating = false
                                if (loc != null) {
                                    // Round 8 第十轮: LocationHelper 已返回 GCJ-02, 直接用
                                    val gcj02 = LatLng(loc.latitude, loc.longitude)
                                    selectedLatLng = gcj02
                                    aMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(gcj02, 16f))
                                    isGeocoding = true
                                    triggerReverseGeocode(geocodeSearch, gcj02) { label ->
                                        addressLabel = label
                                        isGeocoding = false
                                    }
                                }
                            }
                        },
                        enabled = !isLocating,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MyLocation,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (isLocating) "定位中…" else "重新定位")
                    }
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            val target = selectedLatLng
                            if (target != null) {
                                onConfirm(target.latitude, target.longitude, addressLabel.ifBlank { "未命名位置" })
                            }
                        },
                    ) {
                        Text(
                            text = "确定",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }

    // MapView 生命周期管理
    DisposableEffect(Unit) {
        onDispose {
            mapView?.onPause()
            mapView?.onDestroy()
        }
    }
}

/**
 * 触发逆地理编码，成功后回调 label。
 */
private fun triggerReverseGeocode(
    geocodeSearch: GeocodeSearch,
    latLng: LatLng,
    onResult: (String) -> Unit,
) {
    geocodeSearch.setOnGeocodeSearchListener(object : GeocodeSearch.OnGeocodeSearchListener {
        override fun onRegeocodeSearched(result: RegeocodeResult?, rCode: Int) {
            val addr = result?.regeocodeAddress
            val label = addr?.formatAddress?.takeIf { it.isNotBlank() }
                ?: addr?.pois?.takeIf { it.isNotEmpty() }?.get(0)?.title
                ?: "未知位置"
            onResult(label)
        }

        override fun onGeocodeSearched(result: GeocodeResult?, rCode: Int) {}
    })
    val query = RegeocodeQuery(LatLonPoint(latLng.latitude, latLng.longitude), 200f, GeocodeSearch.AMAP)
    geocodeSearch.getFromLocationAsyn(query)
}
