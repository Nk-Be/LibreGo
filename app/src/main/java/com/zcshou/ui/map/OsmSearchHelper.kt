package com.zcshou.ui.map

import com.elvishew.xlog.XLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Open public search & reverse geocoding powered by OSM Nominatim & Photon.
 * Zero token, zero package name restriction.
 */
object OsmSearchHelper {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    data class PlaceResult(
        val title: String,
        val subtitle: String,
        val latitude: Double,
        val longitude: Double
    )

    suspend fun searchPlaces(keyword: String): List<PlaceResult> = withContext(Dispatchers.IO) {
        val list = mutableListOf<PlaceResult>()
        if (keyword.isBlank()) return@withContext list

        val encodedQuery = try {
            URLEncoder.encode(keyword.trim(), "UTF-8")
        } catch (_: Exception) {
            keyword.trim()
        }

        // 1. Try Photon (Fast OSM search mirror)
        try {
            val url = "https://photon.komoot.io/api/?q=$encodedQuery&limit=12&lang=zh"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "LibreGo/2.1.0 (Android Open Source)")
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val bodyStr = response.body?.string().orEmpty()
                val json = JSONObject(bodyStr)
                val features = json.optJSONArray("features") ?: JSONArray()
                for (i in 0 until features.length()) {
                    val feat = features.getJSONObject(i)
                    val geom = feat.getJSONObject("geometry")
                    val coords = geom.getJSONArray("coordinates")
                    val lon = coords.getDouble(0)
                    val lat = coords.getDouble(1)

                    val props = feat.getJSONObject("properties")
                    val name = props.optString("name", keyword)
                    val city = props.optString("city", props.optString("state", ""))
                    val country = props.optString("country", "")
                    val sub = listOf(city, country).filter { it.isNotBlank() }.joinToString(", ")

                    list.add(PlaceResult(name, sub.ifBlank { "$lat, $lon" }, lat, lon))
                }
            }
        } catch (e: Exception) {
            XLog.w("OsmSearchHelper: Photon search failed, fallback to Nominatim", e)
        }

        // 2. Fallback to Nominatim if list is empty
        if (list.isEmpty()) {
            try {
                val url = "https://nominatim.openstreetmap.org/search?format=json&q=$encodedQuery&limit=10&accept-language=zh-CN"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "LibreGo-Android/2.1.0")
                    .build()

                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string().orEmpty()
                    val array = JSONArray(bodyStr)
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        val lat = item.getDouble("lat")
                        val lon = item.getDouble("lon")
                        val displayName = item.getString("display_name")
                        val title = displayName.split(",").firstOrNull()?.trim() ?: keyword
                        list.add(PlaceResult(title, displayName, lat, lon))
                    }
                }
            } catch (e: Exception) {
                XLog.e("OsmSearchHelper: Nominatim search failed", e)
            }
        }

        list
    }

    suspend fun reverseGeocode(lat: Double, lon: Double): String = withContext(Dispatchers.IO) {
        try {
            val url = "https://nominatim.openstreetmap.org/reverse?format=json&lat=$lat&lon=$lon&accept-language=zh-CN"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "LibreGo-Android/2.1.0")
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val bodyStr = response.body?.string().orEmpty()
                val json = JSONObject(bodyStr)
                return@withContext json.optString("display_name", String.format("%.6f, %.6f", lat, lon))
            }
        } catch (e: Exception) {
            XLog.w("OsmSearchHelper: reverseGeocode failed", e)
        }
        String.format("%.6f°, %.6f°", lat, lon)
    }
}
