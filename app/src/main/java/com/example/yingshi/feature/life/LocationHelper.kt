package com.example.yingshi.feature.life

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * FR-19: Helper for fetching the current device location using Android 原生 LocationManager.
 *
 * Round 8 第十三轮: 彻底放弃 Google Play Services 的 FusedLocationProviderClient,
 * 改用 Android 原生 LocationManager (GPS_PROVIDER + NETWORK_PROVIDER).
 *
 * 根因 (第十二轮 logcat 诊断):
 *   - FusedLocationProviderClient.requestLocationUpdates 30 秒一个回调都没收到
 *   - 同一时间 FirebaseMessaging 报 "SERVICE_NOT_AVAILABLE"
 *   - 说明这台三星设备的 Google Play Services 整个挂了/未安装/被禁用
 *   - FusedLocationProviderClient 依赖 Play Services, 所以根本不工作
 *
 * 方案:
 *   - 用 LocationManager.requestLocationUpdates(GPS_PROVIDER) 直接调 GPS 硬件
 *   - 同时用 NETWORK_PROVIDER 做 WiFi/基站定位兜底
 *   - 不依赖 Play Services, 三星/华为/任何 Android 设备都能用
 *   - 坐标系: WGS-84 → GCJ-02 (服务端高德逆地理编码期望 GCJ-02)
 *
 * Round 8 第十九轮: 定位速度优化 (用户反馈"美团/淘宝点一下就定位到了, 我们这个慢").
 * 根因:
 *   - Phase 1 (0-15s) 只接受 accuracy ≤ 15m, GPS 冷启动通常 10-30s 才能达到这个精度
 *   - 室内/弱信号场景可能根本拿不到 ≤ 15m, 必须等到 Phase 2 (15-30s) 才返回
 *   - 没有用 getLastKnownLocation 做首帧秒定位
 *
 * 优化方案 (对标美团/淘宝的"秒定位"体验):
 *   - 启动时立即用 getLastKnownLocation (GPS + NETWORK) 作为 bestRef 初始值
 *     → 0-200ms 返回缓存定位 (精度可能 30-100m, 但能秒定位)
 *   - Phase 1 (0-5s): 接受 accuracy ≤ 50m (WiFi 定位通常 30m 也能过)
 *   - Phase 2 (5-15s): 接受任何精度
 *   - 总超时 15s (不是 30s)
 *   - 永不返回 null, 除非权限/硬件全缺失
 */
object LocationHelper {
    private const val TAG = "LocationHelper"
    private const val DEFAULT_TIMEOUT_MS = 15_000L
    private const val PHASE1_TIMEOUT_MS = 5_000L
    private const val DESIRED_ACCURACY_M = 50f
    private const val GPS_MIN_TIME_MS = 1_000L
    private const val GPS_MIN_DISTANCE_M = 0f

    @SuppressLint("MissingPermission")
    suspend fun currentLocation(
        context: Context,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        @Suppress("UNUSED_PARAMETER") preferFresh: Boolean = true,
    ): LocationSnapshot? {
        val appContext = context.applicationContext
        if (!hasLocationPermission(appContext)) {
            Log.d(TAG, "Location permission not granted; skipping.")
            return null
        }
        val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: run {
                Log.w(TAG, "LocationManager unavailable.")
                return null
            }

        val gpsEnabled = try {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        } catch (_: Exception) {
            false
        }
        val networkEnabled = try {
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (_: Exception) {
            false
        }
        Log.d(TAG, "Providers: GPS=$gpsEnabled NETWORK=$networkEnabled")
        if (!gpsEnabled && !networkEnabled) {
            Log.w(TAG, "All location providers are disabled.")
            return null
        }

        val bestRef = AtomicReference<Location?>(null)
        val acceptedRef = AtomicReference<Location?>(null)
        val startTime = System.currentTimeMillis()

        // Round 8 第十九轮: 用 getLastKnownLocation 做首帧秒定位 (对标美团/淘宝).
        // GPS + NETWORK 两个 provider 的缓存定位都查, 取精度更高的一个作为 bestRef 初始值.
        // 如果缓存定位精度已 ≤ DESIRED_ACCURACY_M (50m), 直接作为 acceptedRef, 0-200ms 返回.
        try {
            val lastGps = if (gpsEnabled) {
                runCatching { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull()
            } else null
            val lastNetwork = if (networkEnabled) {
                runCatching { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull()
            } else null
            val candidates = listOfNotNull(lastGps, lastNetwork)
            if (candidates.isNotEmpty()) {
                val bestCache = candidates.minByOrNull { it.accuracy }
                if (bestCache != null) {
                    bestRef.set(bestCache)
                    Log.d(TAG, "getLastKnownLocation cache: provider=${bestCache.provider} " +
                        "accuracy=${bestCache.accuracy}m age=${(System.currentTimeMillis() - bestCache.time)}ms")
                    // 缓存定位精度足够好, 直接接受 (Phase 1 标准)
                    if (bestCache.accuracy <= DESIRED_ACCURACY_M) {
                        acceptedRef.set(bestCache)
                        Log.d(TAG, "Cache accepted (accuracy ≤ ${DESIRED_ACCURACY_M}m): instant fix")
                    }
                }
            } else {
                Log.d(TAG, "getLastKnownLocation: no cached location for either provider")
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "getLastKnownLocation denied: ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "getLastKnownLocation failed: ${e.message}")
        }

        // Round 8 第十三轮: 用原生 LocationManager, 同时监听 GPS + NETWORK
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val elapsed = System.currentTimeMillis() - startTime
                val provider = location.provider ?: "?"
                Log.d(TAG, "Location update from $provider: (${location.latitude}, ${location.longitude}) accuracy=${location.accuracy}m elapsed=${elapsed}ms")
                // 记录最佳结果
                val prev = bestRef.get()
                if (prev == null || location.accuracy < prev.accuracy) {
                    bestRef.set(location)
                }
                // Phase 1 (0-15s): 只接受 GPS fix (accuracy ≤ 15m)
                // Phase 2 (15-30s): 接受任何精度
                val inPhase1 = elapsed < PHASE1_TIMEOUT_MS
                val acceptable = if (inPhase1) {
                    location.accuracy <= DESIRED_ACCURACY_M
                } else {
                    true
                }
                if (acceptable) {
                    Log.d(TAG, "Accepting location from $provider: accuracy=${location.accuracy}m elapsed=${elapsed}ms phase=${if (inPhase1) 1 else 2}")
                    acceptedRef.set(location)
                }
            }

            override fun onProviderDisabled(provider: String) {
                Log.d(TAG, "Provider disabled: $provider")
            }

            override fun onProviderEnabled(provider: String) {
                Log.d(TAG, "Provider enabled: $provider")
            }

            @Deprecated("legacy, but still required on older API")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
                Log.d(TAG, "Provider $provider status changed: $status")
            }
        }

        return try {
            // 注册 GPS + NETWORK 两个 provider (谁先返回合格的 fix 就用谁)
            if (gpsEnabled) {
                try {
                    lm.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        GPS_MIN_TIME_MS,
                        GPS_MIN_DISTANCE_M,
                        listener,
                        Looper.getMainLooper(),
                    )
                    Log.d(TAG, "Registered GPS_PROVIDER listener.")
                } catch (e: SecurityException) {
                    Log.w(TAG, "GPS_PROVIDER requestLocationUpdates denied: ${e.message}")
                } catch (e: Exception) {
                    Log.w(TAG, "GPS_PROVIDER requestLocationUpdates failed: ${e.message}")
                }
            }
            if (networkEnabled) {
                try {
                    lm.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        GPS_MIN_TIME_MS,
                        GPS_MIN_DISTANCE_M,
                        listener,
                        Looper.getMainLooper(),
                    )
                    Log.d(TAG, "Registered NETWORK_PROVIDER listener.")
                } catch (e: SecurityException) {
                    Log.w(TAG, "NETWORK_PROVIDER requestLocationUpdates denied: ${e.message}")
                } catch (e: Exception) {
                    Log.w(TAG, "NETWORK_PROVIDER requestLocationUpdates failed: ${e.message}")
                }
            }

            // 等待合格结果或超时
            withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine<Location?> { cont ->
                    // 启动一个轮询线程, 检查 acceptedRef (Phase 1/2 已接受的) 或 bestRef (Phase 2)
                    val checkThread = Thread {
                        while (cont.isActive) {
                            try {
                                Thread.sleep(200)
                            } catch (_: InterruptedException) {
                                break
                            }
                            // 1. 优先检查 acceptedRef (listener 已接受的)
                            val accepted = acceptedRef.get()
                            if (accepted != null) {
                                if (cont.isActive) {
                                    Log.d(TAG, "Poll thread returning accepted location: accuracy=${accepted.accuracy}m provider=${accepted.provider}")
                                    cont.resume(accepted)
                                }
                                return@Thread
                            }
                            // 2. Phase 2 (15s后): 检查 bestRef 是否可用 (任何精度都接受)
                            val elapsed = System.currentTimeMillis() - startTime
                            if (elapsed >= PHASE1_TIMEOUT_MS) {
                                val best = bestRef.get()
                                if (best != null) {
                                    if (cont.isActive) {
                                        Log.d(TAG, "Poll thread returning bestSoFar (Phase 2): accuracy=${best.accuracy}m provider=${best.provider}")
                                        cont.resume(best)
                                    }
                                    return@Thread
                                }
                            }
                        }
                    }
                    checkThread.isDaemon = true
                    checkThread.name = "LocationHelper-poll"
                    checkThread.start()
                    cont.invokeOnCancellation {
                        checkThread.interrupt()
                    }
                }
            }

            // 清理: 移除 listener
            try {
                lm.removeUpdates(listener)
                Log.d(TAG, "Removed location updates.")
            } catch (_: Exception) {
            }

            // Round 8 第十一轮: 超时后用 acceptedRef/bestRef 兜底, 绝不返回 null
            val finalLoc = acceptedRef.get() ?: bestRef.get()
            if (finalLoc != null) {
                val (gcjLat, gcjLng) = wgs84ToGcj02(finalLoc.latitude, finalLoc.longitude)
                Log.d(TAG, "Returning GCJ-02 location: ($gcjLat, $gcjLng) accuracy=${finalLoc.accuracy}m provider=${finalLoc.provider}")
                LocationSnapshot(latitude = gcjLat, longitude = gcjLng)
            } else {
                Log.w(TAG, "No location updates received within ${timeoutMs}ms.")
                null
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException reading location: ${e.message}")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read location: ${e.message}")
            null
        }
    }

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    // ---- Round 8 第十九轮: 持续定位 (对标美团/淘宝的"app 内常驻定位缓存") ----
    // 用户反馈: "我想到是启动的时候获取得到缓存, 隔一段时间进行刷新, 这个间隔时间就要好好把控了."
    // 间隔 30s (不常用, 省电), 最小距离 50m (移动才更新, 静止不刷).
    // 持续定位的生命周期绑定到 LifeConsoleScreen (今日痕迹页), 进入页面启动, 离开停止.
    // 拍照上传时优先读 latestSnapshot, 0ms 返回缓存; 为 null 才 fallback 到 currentLocation 一次性请求.

    private val latestSnapshotRef = AtomicReference<LocationSnapshot?>(null)
    private val continuousListenerRef = AtomicReference<LocationListener?>(null)
    private val continuousActive = AtomicBoolean(false)
    private val lastAppContextRef = AtomicReference<Context?>(null)
    private const val CONTINUOUS_INTERVAL_MS = 30_000L
    private const val CONTINUOUS_MIN_DISTANCE_M = 50f

    /**
     * 启动持续定位. 进入今日痕迹页时调用.
     * 重复调用安全 (idempotent): 已在运行则直接返回.
     */
    @SuppressLint("MissingPermission")
    fun startContinuousUpdates(context: Context) {
        val appContext = context.applicationContext
        if (!hasLocationPermission(appContext)) {
            Log.d(TAG, "startContinuousUpdates: permission not granted, skip.")
            return
        }
        // 记录 appContext, stopContinuousUpdates 时用它拿 LocationManager 移除 listener.
        lastAppContextRef.set(appContext)
        if (!continuousActive.compareAndSet(false, true)) {
            Log.d(TAG, "startContinuousUpdates: already running, skip.")
            return
        }
        val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: run {
            continuousActive.set(false)
            Log.w(TAG, "startContinuousUpdates: LocationManager unavailable.")
            return
        }
        val gpsEnabled = try { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) } catch (_: Exception) { false }
        val networkEnabled = try { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) } catch (_: Exception) { false }
        if (!gpsEnabled && !networkEnabled) {
            continuousActive.set(false)
            Log.w(TAG, "startContinuousUpdates: all providers disabled.")
            return
        }

        // 首帧立即用 getLastKnownLocation 灌一次缓存, 避免进入页面后 30s 内 latestSnapshot 都是 null.
        try {
            val lastGps = if (gpsEnabled) runCatching { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull() else null
            val lastNetwork = if (networkEnabled) runCatching { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull() else null
            val bestCache = listOfNotNull(lastGps, lastNetwork).minByOrNull { it.accuracy }
            if (bestCache != null) {
                val (lat, lng) = wgs84ToGcj02(bestCache.latitude, bestCache.longitude)
                latestSnapshotRef.set(LocationSnapshot(lat, lng))
                Log.d(TAG, "startContinuousUpdates: primed cache from getLastKnownLocation accuracy=${bestCache.accuracy}m")
            }
        } catch (e: Exception) {
            Log.w(TAG, "startContinuousUpdates: getLastKnownLocation failed: ${e.message}")
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val (lat, lng) = wgs84ToGcj02(location.latitude, location.longitude)
                latestSnapshotRef.set(LocationSnapshot(lat, lng))
                Log.d(TAG, "Continuous update: ($lat, $lng) accuracy=${location.accuracy}m provider=${location.provider}")
            }
            override fun onProviderDisabled(provider: String) {}
            override fun onProviderEnabled(provider: String) {}
            @Deprecated("legacy")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
        continuousListenerRef.set(listener)

        if (gpsEnabled) {
            try {
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    CONTINUOUS_INTERVAL_MS,
                    CONTINUOUS_MIN_DISTANCE_M,
                    listener,
                    Looper.getMainLooper(),
                )
            } catch (e: SecurityException) {
                Log.w(TAG, "startContinuousUpdates: GPS denied: ${e.message}")
            } catch (e: Exception) {
                Log.w(TAG, "startContinuousUpdates: GPS failed: ${e.message}")
            }
        }
        if (networkEnabled) {
            try {
                lm.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    CONTINUOUS_INTERVAL_MS,
                    CONTINUOUS_MIN_DISTANCE_M,
                    listener,
                    Looper.getMainLooper(),
                )
            } catch (e: SecurityException) {
                Log.w(TAG, "startContinuousUpdates: NETWORK denied: ${e.message}")
            } catch (e: Exception) {
                Log.w(TAG, "startContinuousUpdates: NETWORK failed: ${e.message}")
            }
        }
        Log.d(TAG, "startContinuousUpdates: started (interval=${CONTINUOUS_INTERVAL_MS}ms minDistance=${CONTINUOUS_MIN_DISTANCE_M}m)")
    }

    /**
     * 停止持续定位. 离开今日痕迹页时调用.
     * 重复调用安全. 不清空 latestSnapshot (允许下一次进入页面时仍有缓存可用).
     */
    fun stopContinuousUpdates() {
        if (!continuousActive.compareAndSet(true, false)) {
            return
        }
        val listener = continuousListenerRef.getAndSet(null) ?: return
        try {
            val appContext = lastAppContextRef.get()
            if (appContext != null) {
                val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                lm?.removeUpdates(listener)
            }
        } catch (e: Exception) {
            Log.w(TAG, "stopContinuousUpdates: failed: ${e.message}")
        }
        Log.d(TAG, "stopContinuousUpdates: stopped.")
    }

    /**
     * 读持续定位缓存. 0ms 返回, 可能为 null (持续定位未启动或还没拿到 fix).
     */
    fun latestSnapshot(): LocationSnapshot? = latestSnapshotRef.get()

    /**
     * 快速获取当前定位 (拍照上传时用).
     * 优先读 latestSnapshot (0ms); 为 null 才 fallback 到 currentLocation 一次性请求 (5-15s).
     */
    suspend fun currentLocationFast(context: Context): LocationSnapshot? {
        latestSnapshot()?.let { cached ->
            Log.d(TAG, "currentLocationFast: returning cached snapshot ($cached)")
            return cached
        }
        Log.d(TAG, "currentLocationFast: no cache, fallback to currentLocation one-shot")
        return currentLocation(context)
    }

    /**
     * WGS-84 → GCJ-02 坐标转换 (中国境内坐标偏移算法).
     * 与高德 CoordinateConverter 转换结果一致, 但不依赖高德 SDK.
     * 国外坐标直接返回原值 (不偏移).
     *
     * Round 8 第十四轮: 改为 public, 供 LifeConsoleUploadBridge 读取 EXIF GPS 后转换坐标系.
     */
    fun wgs84ToGcj02Public(wgsLat: Double, wgsLng: Double): Pair<Double, Double> {
        return wgs84ToGcj02(wgsLat, wgsLng)
    }

    private fun wgs84ToGcj02(wgsLat: Double, wgsLng: Double): Pair<Double, Double> {
        if (outOfChina(wgsLat, wgsLng)) return Pair(wgsLat, wgsLng)
        val a = 6378245.0
        val ee = 0.00669342162296594323
        val pi = Math.PI
        var dLat = transformLat(wgsLng - 105.0, wgsLat - 35.0)
        var dLng = transformLng(wgsLng - 105.0, wgsLat - 35.0)
        val radLat = wgsLat / 180.0 * pi
        var magic = sin(radLat)
        magic = 1 - ee * magic * magic
        val sqrtMagic = sqrt(magic)
        dLat = (dLat * 180.0) / ((a * (1 - ee)) / (magic * sqrtMagic) * pi)
        dLng = (dLng * 180.0) / (a / sqrtMagic * cos(radLat) * pi)
        return Pair(wgsLat + dLat, wgsLng + dLng)
    }

    private fun outOfChina(lat: Double, lng: Double): Boolean {
        return lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271
    }

    private fun transformLat(x: Double, y: Double): Double {
        val pi = Math.PI
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * pi) + 20.0 * sin(2.0 * x * pi)) * 2.0 / 3.0
        ret += (20.0 * sin(y * pi) + 40.0 * sin(y / 3.0 * pi)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * pi) + 320 * sin(y * pi / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLng(x: Double, y: Double): Double {
        val pi = Math.PI
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * pi) + 20.0 * sin(2.0 * x * pi)) * 2.0 / 3.0
        ret += (20.0 * sin(x * pi) + 40.0 * sin(x / 3.0 * pi)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * pi) + 300.0 * sin(x / 30.0 * pi)) * 2.0 / 3.0
        return ret
    }
}

/**
 * Plain location data holder. Latitude/longitude in degrees (GCJ-02 坐标系).
 */
data class LocationSnapshot(
    val latitude: Double,
    val longitude: Double,
)
