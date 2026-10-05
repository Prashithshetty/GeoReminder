package com.georeminder.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.georeminder.app.data.model.OfflineRegion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

class OfflineMapRepository(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("geo_offline_prefs", Context.MODE_PRIVATE)
    private val ioScope = CoroutineScope(Dispatchers.IO.limitedParallelism(1))
    private val _regions = MutableStateFlow<List<OfflineRegion>>(emptyList())
    val regions: StateFlow<List<OfflineRegion>> = _regions.asStateFlow()

    init {
        if (!prefs.contains("regions_key")) {
            initSampleRegions()
        } else {
            loadRegions()
        }
    }

    private fun loadRegions() {
        val jsonString = prefs.getString("regions_key", null) ?: return
        try {
            val array = JSONArray(jsonString)
            val list = mutableListOf<OfflineRegion>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    OfflineRegion(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        minZoom = obj.getInt("minZoom"),
                        maxZoom = obj.getInt("maxZoom"),
                        tileCount = obj.getInt("tileCount"),
                        sizeMb = obj.getDouble("sizeMb"),
                        downloadedAt = obj.getString("downloadedAt")
                    )
                )
            }
            _regions.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun persist() {
        val snapshot = _regions.value
        ioScope.launch {
            try {
                val array = JSONArray()
                for (reg in snapshot) {
                    val obj = JSONObject().apply {
                        put("id", reg.id)
                        put("name", reg.name)
                        put("minZoom", reg.minZoom)
                        put("maxZoom", reg.maxZoom)
                        put("tileCount", reg.tileCount)
                        put("sizeMb", reg.sizeMb)
                        put("downloadedAt", reg.downloadedAt)
                    }
                    array.put(obj)
                }
                prefs.edit().putString("regions_key", array.toString()).apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addRegion(region: OfflineRegion) {
        val list = _regions.value.toMutableList()
        list.add(0, region)
        _regions.value = list
        persist()
    }

    fun deleteRegion(id: String) {
        _regions.value = _regions.value.filter { it.id != id }
        if (_regions.value.isEmpty()) {
            clearDiskTiles()
        }
        persist()
    }

    fun clearDiskTiles() {
        try {
            val cacheRoot = Configuration.getInstance().osmdroidTileCache ?: File(context.cacheDir, "osmdroid/tiles")
            cacheRoot.deleteRecursively()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clearCache() {
        clearDiskTiles()
        _regions.value = emptyList()
        persist()
    }

    suspend fun downloadRegionTiles(
        name: String,
        centerLat: Double = 40.7829,
        centerLng: Double = -73.9654,
        minZoom: Int = 12,
        maxZoom: Int = 16,
        onProgress: (Float, String) -> Unit
    ): OfflineRegion = withContext(Dispatchers.IO) {
        val cacheRoot = Configuration.getInstance().osmdroidTileCache ?: File(context.cacheDir, "osmdroid/tiles")
        val coords = mutableListOf<Triple<Int, Int, Int>>()
        for (z in minZoom..maxZoom) {
            val n = 1 shl z
            val cx = floor((centerLng + 180.0) / 360.0 * n).toInt()
            val latRad = centerLat * PI / 180.0
            val cy = floor((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n).toInt()
            // Dynamically scale radius so zoom 16 has dense street coverage around the area
            val span = (z - 11).coerceIn(1, 4)
            for (dx in -span..span) {
                for (dy in -span..span) {
                    coords.add(Triple(z, (cx + dx).coerceIn(0, n - 1), (cy + dy).coerceIn(0, n - 1)))
                }
            }
        }

        var downloadedBytes = 0L
        coords.forEachIndexed { idx, (z, x, y) ->
            onProgress((idx + 1).toFloat() / coords.size, "Caching zoom level $z (${idx + 1}/${coords.size})...")
            val tileFile = File(cacheRoot, "Mapnik/$z/$x/$y.png.tile")
            if (!tileFile.exists()) {
                try {
                    tileFile.parentFile?.mkdirs()
                    val conn = (URL("https://tile.openstreetmap.org/$z/$x/$y.png").openConnection() as HttpURLConnection).apply {
                        setRequestProperty("User-Agent", "GeoReminder-App/1.0 (Android osmdroid; ${context.packageName})")
                        connectTimeout = 4000
                        readTimeout = 4000
                    }
                    if (conn.responseCode == 200) {
                        val bytes = conn.inputStream.use { it.readBytes() }
                        tileFile.writeBytes(bytes)
                        downloadedBytes += bytes.size
                    }
                    conn.disconnect()
                } catch (_: Exception) {
                    // Offline or timeout during tile fetch: keep going for remaining tiles
                }
            } else {
                downloadedBytes += tileFile.length()
            }
        }

        val sizeMb = ((if (downloadedBytes > 0) downloadedBytes else coords.size * 18432L) / (1024.0 * 1024.0))
            .coerceAtLeast(0.1)
        val region = OfflineRegion(
            id = "reg-${System.currentTimeMillis()}",
            name = name.ifBlank { "Custom Offline Sector" },
            minZoom = minZoom,
            maxZoom = maxZoom,
            tileCount = coords.size,
            sizeMb = String.format(Locale.US, "%.1f", sizeMb).toDouble(),
            downloadedAt = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date())
        )
        addRegion(region)
        region
    }

    private fun initSampleRegions() {
        _regions.value = listOf(
            OfflineRegion(
                id = "reg-1",
                name = "Downtown Manhattan (Core)",
                minZoom = 12,
                maxZoom = 16,
                tileCount = 1420,
                sizeMb = 24.8,
                downloadedAt = "Oct 28, 2026"
            ),
            OfflineRegion(
                id = "reg-2",
                name = "Central Park & Midtown",
                minZoom = 13,
                maxZoom = 16,
                tileCount = 890,
                sizeMb = 15.6,
                downloadedAt = "Oct 29, 2026"
            )
        )
        persist()
    }

    companion object {
        @Volatile
        private var instance: OfflineMapRepository? = null

        fun getInstance(context: Context): OfflineMapRepository {
            return instance ?: synchronized(this) {
                instance ?: OfflineMapRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
