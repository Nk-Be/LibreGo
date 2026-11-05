package com.zcshou.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.elvishew.xlog.XLog

/**
 * SQLite helper for location history in LibreGo.
 */
class DataBaseHistoryLocation(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        const val TABLE_NAME = "HistoryLocation"
        const val DB_COLUMN_ID = "DB_COLUMN_ID"
        const val DB_COLUMN_LOCATION = "DB_COLUMN_LOCATION"
        const val DB_COLUMN_LONGITUDE_WGS84 = "DB_COLUMN_LONGITUDE_WGS84"
        const val DB_COLUMN_LATITUDE_WGS84 = "DB_COLUMN_LATITUDE_WGS84"
        const val DB_COLUMN_TIMESTAMP = "DB_COLUMN_TIMESTAMP"
        const val DB_COLUMN_LONGITUDE_CUSTOM = "DB_COLUMN_LONGITUDE_CUSTOM"
        const val DB_COLUMN_LATITUDE_CUSTOM = "DB_COLUMN_LATITUDE_CUSTOM"

        private const val DB_VERSION = 1
        private const val DB_NAME = "HistoryLocation.db"
        private const val CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS $TABLE_NAME (
                $DB_COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $DB_COLUMN_LOCATION TEXT,
                $DB_COLUMN_LONGITUDE_WGS84 TEXT NOT NULL,
                $DB_COLUMN_LATITUDE_WGS84 TEXT NOT NULL,
                $DB_COLUMN_TIMESTAMP BIGINT NOT NULL,
                $DB_COLUMN_LONGITUDE_CUSTOM TEXT NOT NULL,
                $DB_COLUMN_LATITUDE_CUSTOM TEXT NOT NULL
            )
        """

        @JvmStatic
        fun saveHistoryLocation(db: SQLiteDatabase?, values: ContentValues?) {
            if (db == null || values == null) return
            try {
                val lng = values.getAsString(DB_COLUMN_LONGITUDE_WGS84)
                val lat = values.getAsString(DB_COLUMN_LATITUDE_WGS84)
                db.delete(
                    TABLE_NAME,
                    "$DB_COLUMN_LONGITUDE_WGS84 = ? AND $DB_COLUMN_LATITUDE_WGS84 = ?",
                    arrayOf(lng, lat)
                )
                db.insert(TABLE_NAME, null, values)
            } catch (e: Exception) {
                XLog.e("DATABASE: insert error", e)
            }
        }

        @JvmStatic
        fun updateHistoryLocation(db: SQLiteDatabase?, locID: String, location: String) {
            if (db == null) return
            try {
                val values = ContentValues().apply {
                    put(DB_COLUMN_LOCATION, location)
                }
                db.update(TABLE_NAME, values, "$DB_COLUMN_ID = ?", arrayOf(locID))
            } catch (e: Exception) {
                XLog.e("DATABASE: update error", e)
            }
        }
    }

    override fun onCreate(sqLiteDatabase: SQLiteDatabase) {
        sqLiteDatabase.execSQL(CREATE_TABLE)
    }

    override fun onUpgrade(sqLiteDatabase: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        sqLiteDatabase.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(sqLiteDatabase)
    }
}
