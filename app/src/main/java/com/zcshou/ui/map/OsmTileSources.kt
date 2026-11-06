package com.zcshou.ui.map

import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.MapTileIndex

/**
 * Public, unrestricted tile sources for osmdroid.
 * 100% free, no API key, no package name or SHA1 binding.
 */
object OsmTileSources {

    // 1. OpenStreetMap standard global tiles
    val OSM_MAPNIK = TileSourceFactory.MAPNIK

    // 2. AutoNavi (高德) public vector map tiles (Fast CDN in China, complete Chinese roads and labels)
    val AMAP_VECTOR = object : OnlineTileSourceBase(
        "AutoNavi-Vector",
        1, 19, 256, ".png",
        arrayOf(
            "https://wprd01.is.autonavi.com/appmaptile",
            "https://wprd02.is.autonavi.com/appmaptile",
            "https://wprd03.is.autonavi.com/appmaptile",
            "https://wprd04.is.autonavi.com/appmaptile"
        )
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            val z = MapTileIndex.getZoom(pMapTileIndex)
            return "$baseUrl?x=$x&y=$y&z=$z&lang=zh_cn&size=1&scale=1&style=7"
        }
    }

    // 3. AutoNavi (高德) public satellite aerial tiles
    val AMAP_SATELLITE = object : OnlineTileSourceBase(
        "AutoNavi-Satellite",
        1, 19, 256, ".jpg",
        arrayOf(
            "https://wprd01.is.autonavi.com/appmaptile",
            "https://wprd02.is.autonavi.com/appmaptile",
            "https://wprd03.is.autonavi.com/appmaptile",
            "https://wprd04.is.autonavi.com/appmaptile"
        )
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            val z = MapTileIndex.getZoom(pMapTileIndex)
            return "$baseUrl?x=$x&y=$y&z=$z&lang=zh_cn&size=1&scale=1&style=6"
        }
    }
}
