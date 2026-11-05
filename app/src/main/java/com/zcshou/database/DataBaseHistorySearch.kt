package com.zcshou.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.elvishew.xlog.XLog

/**
 * SQLite helper for search history in LibreGo.
 */
class DataBaseHistorySearch(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        const val TABLE_NAME = "HistorySearch"
        const val DB_COLUMN_ID = "DB_COLUMN_ID"
        const val DB_COLUMN_KEY = "DB_COLUMN_KEY"
        const val DB_COLUMN_DESCRIPTION = "DB_COLUMN_DESCRIPTION"
        const val DB_COLUMN_TIMESTAMP = "DB_COLUMN_TIMESTAMP"
        const val DB_COLUMN_IS_LOCATION = "DB_COLUMN_IS_LOCATION"
        const val DB_COLUMN_LONGITUDE_WGS84 = "DB_COLUMN_LONGITUDE_WGS84"
        const val DB_COLUMN_LATITUDE_WGS84 = "DB_COLUMN_LATITUDE_WGS84"
        const val DB_COLUMN_LONGITUDE_CUSTOM = "DB_COLUMN_LONGITUDE_CUSTOM"
        const val DB_COLUMN_LATITUDE_CUSTOM = "DB_COLUMN_LATITUDE_CUSTOM"

        const val DB_SEARCH_TYPE_KEY = 0
        const val DB_SEARCH_TYPE_RESULT = 1

        private const val DB_VERSION = 1
        private const val DB_NAME = "HistorySearch.db"
        private const val CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS $TABLE_NAME (
                $DB_COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $DB_COLUMN_KEY TEXT NOT NULL,
                $DB_COLUMN_DESCRIPTION TEXT,
                $DB_COLUMN_TIMESTAMP BIGINT NOT NULL,
                $DB_COLUMN_IS_LOCATION INTEGER NOT NULL,
                $DB_COLUMN_LONGITUDE_WGS84 TEXT,
                $DB_COLUMN_LATITUDE_WGS84 TEXT,
                $DB_COLUMN_LONGITUDE_CUSTOM TEXT,
                $DB_COLUMN_LATITUDE_CUSTOM TEXT
            )
        """

        @JvmStatic
        fun saveHistorySearch(db: SQLiteDatabase?, values: ContentValues?) {
            if (db == null || values == null) return
            try {
                val key = values.getAsString(DB_COLUMN_KEY) ?: return
                db.delete(TABLE_NAME, "$DB_COLUMN_KEY = ?", arrayOf(key))
                db.insert(TABLE_NAME, null, values)
            } catch (e: Exception) {
                XLog.e("DATABASE: insert error", e)
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
