package com.zcshou.utils

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Coordinate transformation utilities for WGS-84, GCJ-02, and BD-09.
 */
object MapUtils {

    const val PI = 3.14159265358979324
    const val A = 6378245.0
    const val EE = 0.00669342162296594323
    const val X_PI = PI * 3000.0 / 180.0

    @JvmStatic
    fun bd2wgs(lon: Double, lat: Double): DoubleArray {
        val bd2Gcj = bd09togcj02(lon, lat)
        return gcj02towgs84(bd2Gcj[0], bd2Gcj[1])
    }

    /**
     * WGS84 -> BD-09
     * @return [longitude, latitude]
     */
    @JvmStatic
    fun wgs2bd09(lng: Double, lat: Double): DoubleArray {
        var dlat = transformLat(lng - 105.0, lat - 35.0)
        var dlng = transformLon(lng - 105.0, lat - 35.0)
        val radlat = lat / 180.0 * PI
        var magic = sin(radlat)
        magic = 1 - EE * magic * magic
        val sqrtmagic = sqrt(magic)
        dlat = (dlat * 180.0) / ((A * (1 - EE)) / (magic * sqrtmagic) * PI)
        dlng = (dlng * 180.0) / (A / sqrtmagic * cos(radlat) * PI)
        val mglat = lat + dlat
        val mglng = lng + dlng

        val z = sqrt(mglng * mglng + mglat * mglat) + 0.00002 * sin(mglat * X_PI)
        val theta = atan2(mglat, mglng) + 0.000003 * cos(mglng * X_PI)
        val bdLng = z * cos(theta) + 0.0065
        val bdLat = z * sin(theta) + 0.006
        return doubleArrayOf(bdLng, bdLat)
    }

    @JvmStatic
    fun bd09togcj02(bdLon: Double, bdLat: Double): DoubleArray {
        val x = bdLon - 0.0065
        val y = bdLat - 0.006
        val z = sqrt(x * x + y * y) - 0.00002 * sin(y * X_PI)
        val theta = atan2(y, x) - 0.000003 * cos(x * X_PI)
        val ggLng = z * cos(theta)
        val ggLat = z * sin(theta)
        return doubleArrayOf(ggLng, ggLat)
    }

    @JvmStatic
    fun gcj02towgs84(lng: Double, lat: Double): DoubleArray {
        var dlat = transformLat(lng - 105.0, lat - 35.0)
        var dlng = transformLon(lng - 105.0, lat - 35.0)
        val radlat = lat / 180.0 * PI
        var magic = sin(radlat)
        magic = 1 - EE * magic * magic
        val sqrtmagic = sqrt(magic)
        dlat = (dlat * 180.0) / ((A * (1 - EE)) / (magic * sqrtmagic) * PI)
        dlng = (dlng * 180.0) / (A / sqrtmagic * cos(radlat) * PI)
        val mglat = lat + dlat
        val mglng = lng + dlng
        return doubleArrayOf(lng * 2 - mglng, lat * 2 - mglat)
    }

    private fun transformLat(lat: Double, lon: Double): Double {
        var ret = -100.0 + 2.0 * lat + 3.0 * lon + 0.2 * lon * lon + 0.1 * lat * lon + 0.2 * sqrt(abs(lat))
        ret += (20.0 * sin(6.0 * lat * PI) + 20.0 * sin(2.0 * lat * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(lon * PI) + 40.0 * sin(lon / 3.0 * PI)) * 2.0 / 3.0
        ret += (160.0 * sin(lon / 12.0 * PI) + 320 * sin(lon * PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLon(lat: Double, lon: Double): Double {
        var ret = 300.0 + lat + 2.0 * lon + 0.1 * lat * lat + 0.1 * lat * lon + 0.1 * sqrt(abs(lat))
        ret += (20.0 * sin(6.0 * lat * PI) + 20.0 * sin(2.0 * lat * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(lat * PI) + 40.0 * sin(lat / 3.0 * PI)) * 2.0 / 3.0
        ret += (150.0 * sin(lat / 12.0 * PI) + 300.0 * sin(lat / 30.0 * PI)) * 2.0 / 3.0
        return ret
    }
}
