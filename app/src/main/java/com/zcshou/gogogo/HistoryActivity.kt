package com.zcshou.gogogo

import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.SearchView
import android.widget.SimpleAdapter
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.elvishew.xlog.XLog
import com.zcshou.database.DataBaseHistoryLocation
import com.zcshou.utils.GoUtils
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/**
 * Modern History Activity for LibreGo in Kotlin.
 */
class HistoryActivity : BaseActivity() {

    companion object {
        const val KEY_ID = "KEY_ID"
        const val KEY_LOCATION = "KEY_LOCATION"
        const val KEY_TIME = "KEY_TIME"
        const val KEY_LNG_LAT_WGS = "KEY_LNG_LAT_WGS"
        const val KEY_LNG_LAT_CUSTOM = "KEY_LNG_LAT_CUSTOM"
    }

    private lateinit var mRecordListView: ListView
    private lateinit var noRecordText: TextView
    private lateinit var mSearchLayout: LinearLayout
    private var mHistoryLocationDB: SQLiteDatabase? = null
    private var mAllRecord: MutableList<Map<String, Any>> = mutableListOf()
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT < 35) {
            window.statusBarColor = ContextCompat.getColor(this, R.color.colorPrimary)
        }

        setContentView(R.layout.activity_history)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.nav_menu_history)
        }

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        initLocationDataBase()
        initSearchView()
        initRecordListView()
    }

    override fun onDestroy() {
        mHistoryLocationDB?.close()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_history, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_delete -> {
                AlertDialog.Builder(this)
                    .setTitle("警告")
                    .setMessage("確定要刪除全部歷史記錄嗎？")
                    .setPositiveButton("確定") { _, _ ->
                        if (deleteRecord(-1)) {
                            GoUtils.DisplayToast(this, getString(R.string.history_delete_ok))
                            updateRecordList()
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun initLocationDataBase() {
        try {
            val hisLocDBHelper = DataBaseHistoryLocation(applicationContext)
            mHistoryLocationDB = hisLocDBHelper.writableDatabase
        } catch (e: Exception) {
            XLog.e("HistoryActivity: initLocationDataBase error", e)
        }
        recordArchive()
    }

    private fun fetchAllRecord(): MutableList<Map<String, Any>> {
        val data = mutableListOf<Map<String, Any>>()
        val db = mHistoryLocationDB ?: return data

        try {
            val cursor = db.query(
                DataBaseHistoryLocation.TABLE_NAME, null,
                "${DataBaseHistoryLocation.DB_COLUMN_ID} > ?", arrayOf("0"),
                null, null, "${DataBaseHistoryLocation.DB_COLUMN_TIMESTAMP} DESC", null
            )

            cursor?.use {
                while (it.moveToNext()) {
                    val id = it.getInt(0)
                    val location = it.getString(1) ?: ""
                    val longitude = it.getString(2) ?: "0.0"
                    val latitude = it.getString(3) ?: "0.0"
                    val timestamp = it.getLong(4)
                    val bd09Longitude = it.getString(5) ?: "0.0"
                    val bd09Latitude = it.getString(6) ?: "0.0"

                    val doubleLongitude = BigDecimal(longitude).setScale(7, RoundingMode.HALF_UP).toDouble()
                    val doubleLatitude = BigDecimal(latitude).setScale(7, RoundingMode.HALF_UP).toDouble()
                    val doubleBDLongitude = BigDecimal(bd09Longitude).setScale(7, RoundingMode.HALF_UP).toDouble()
                    val doubleBDLatitude = BigDecimal(bd09Latitude).setScale(7, RoundingMode.HALF_UP).toDouble()

                    val item = mutableMapOf<String, Any>(
                        KEY_ID to id.toString(),
                        KEY_LOCATION to location,
                        KEY_TIME to GoUtils.timeStamp2Date(timestamp.toString()),
                        KEY_LNG_LAT_WGS to "[經度:$doubleLongitude 緯度:$doubleLatitude]",
                        KEY_LNG_LAT_CUSTOM to "[經度:$doubleBDLongitude 緯度:$doubleBDLatitude]"
                    )
                    data.add(item)
                }
            }
        } catch (e: Exception) {
            data.clear()
            XLog.e("HistoryActivity: fetchAllRecord error", e)
        }

        return data
    }

    private fun recordArchive() {
        val limits = try {
            sharedPreferences.getString("setting_pos_history", getString(R.string.history_expiration))?.toDouble() ?: 7.0
        } catch (_: Exception) {
            7.0
        }
        val weekSecond = (limits * 24 * 60 * 60).toLong()

        try {
            mHistoryLocationDB?.delete(
                DataBaseHistoryLocation.TABLE_NAME,
                "${DataBaseHistoryLocation.DB_COLUMN_TIMESTAMP} < ?",
                arrayOf((System.currentTimeMillis() / 1000 - weekSecond).toString())
            )
        } catch (e: Exception) {
            XLog.e("HistoryActivity: recordArchive error", e)
        }
    }

    private fun deleteRecord(id: Int): Boolean {
        val db = mHistoryLocationDB ?: return false
        return try {
            if (id <= -1) {
                db.delete(DataBaseHistoryLocation.TABLE_NAME, null, null)
            } else {
                db.delete(
                    DataBaseHistoryLocation.TABLE_NAME,
                    "${DataBaseHistoryLocation.DB_COLUMN_ID} = ?",
                    arrayOf(id.toString())
                )
            }
            true
        } catch (e: Exception) {
            XLog.e("HistoryActivity: deleteRecord error", e)
            false
        }
    }

    private fun initSearchView() {
        val searchView = findViewById<SearchView>(R.id.searchView)
        searchView.onActionViewExpanded()
        searchView.isSubmitButtonEnabled = false
        searchView.isFocusable = false
        searchView.clearFocus()

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false

            override fun onQueryTextChange(newText: String?): Boolean {
                val filtered = if (newText.isNullOrEmpty()) {
                    mAllRecord
                } else {
                    mAllRecord.filter { it.toString().contains(newText, ignoreCase = true) }
                }

                val adapter = SimpleAdapter(
                    this@HistoryActivity,
                    filtered,
                    R.layout.history_item,
                    arrayOf(KEY_ID, KEY_LOCATION, KEY_TIME, KEY_LNG_LAT_WGS, KEY_LNG_LAT_CUSTOM),
                    intArrayOf(R.id.LocationID, R.id.LocationText, R.id.TimeText, R.id.WGSLatLngText, R.id.BDLatLngText)
                )
                mRecordListView.adapter = adapter
                return false
            }
        })
    }

    private fun showDeleteDialog(locID: String) {
        AlertDialog.Builder(this)
            .setTitle("警告")
            .setMessage("確定要刪除該項歷史記錄嗎？")
            .setPositiveButton("確定") { _, _ ->
                if (deleteRecord(locID.toIntOrNull() ?: -1)) {
                    GoUtils.DisplayToast(this, getString(R.string.history_delete_ok))
                    updateRecordList()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showInputDialog(locID: String, name: String) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(name)
        }

        AlertDialog.Builder(this)
            .setTitle("修改名稱")
            .setView(input)
            .setPositiveButton("確認") { _, _ ->
                val userInput = input.text.toString().trim()
                DataBaseHistoryLocation.updateHistoryLocation(mHistoryLocationDB, locID, userInput)
                updateRecordList()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun randomOffset(longitude: String, latitude: String): Array<String> {
        val defaultOffset = getString(R.string.setting_random_offset_default)
        val lonMaxOffset = sharedPreferences.getString("setting_lon_max_offset", defaultOffset)?.toDoubleOrNull() ?: 10.0
        val latMaxOffset = sharedPreferences.getString("setting_lat_max_offset", defaultOffset)?.toDoubleOrNull() ?: 10.0
        var lon = longitude.toDoubleOrNull() ?: 0.0
        var lat = latitude.toDoubleOrNull() ?: 0.0

        val randomLonOffset = (Math.random() * 2 - 1) * lonMaxOffset
        val randomLatOffset = (Math.random() * 2 - 1) * latMaxOffset

        lon += randomLonOffset / 111320.0
        lat += randomLatOffset / 110574.0

        GoUtils.DisplayToast(
            this,
            String.format(Locale.US, "隨機微擾：經度 %.1fm, 緯度 %.1fm", randomLonOffset, randomLatOffset)
        )
        return arrayOf(lon.toString(), lat.toString())
    }

    private fun initRecordListView() {
        noRecordText = findViewById(R.id.record_no_textview)
        mSearchLayout = findViewById(R.id.search_linear)
        mRecordListView = findViewById(R.id.record_list_view)

        mRecordListView.setOnItemClickListener { _, view, _, _ ->
            val name = (view.findViewById<TextView>(R.id.LocationText)).text.toString()
            val bd09LatLng = (view.findViewById<TextView>(R.id.BDLatLngText)).text.toString()
            val cleanStr = bd09LatLng.substring(bd09LatLng.indexOf('[') + 1, bd09LatLng.indexOf(']'))
            val parts = cleanStr.split(" ")
            var bd09Longitude = parts[0].substring(parts[0].indexOf(':') + 1)
            var bd09Latitude = parts[1].substring(parts[1].indexOf(':') + 1)

            if (sharedPreferences.getBoolean("setting_random_offset", false)) {
                val offsetResult = randomOffset(bd09Longitude, bd09Latitude)
                bd09Longitude = offsetResult[0]
                bd09Latitude = offsetResult[1]
            }

            if (!MainActivity.showLocation(name, bd09Longitude, bd09Latitude)) {
                GoUtils.DisplayToast(this, getString(R.string.history_error_location))
            }
            finish()
        }

        mRecordListView.setOnItemLongClickListener { _, view, _, _ ->
            val popupMenu = PopupMenu(this, view).apply {
                gravity = Gravity.END or Gravity.BOTTOM
                menu.add("編輯")
                menu.add("刪除")
                setOnMenuItemClickListener { item ->
                    val locID = view.findViewById<TextView>(R.id.LocationID).text.toString()
                    val name = view.findViewById<TextView>(R.id.LocationText).text.toString()
                    when (item.title.toString()) {
                        "編輯" -> {
                            showInputDialog(locID, name)
                            true
                        }
                        "刪除" -> {
                            showDeleteDialog(locID)
                            true
                        }
                        else -> false
                    }
                }
            }
            popupMenu.show()
            true
        }

        updateRecordList()
    }

    private fun updateRecordList() {
        mAllRecord = fetchAllRecord()

        if (mAllRecord.isEmpty()) {
            mRecordListView.visibility = View.GONE
            mSearchLayout.visibility = View.GONE
            noRecordText.visibility = View.VISIBLE
        } else {
            noRecordText.visibility = View.GONE
            mRecordListView.visibility = View.VISIBLE
            mSearchLayout.visibility = View.VISIBLE

            val adapter = SimpleAdapter(
                this,
                mAllRecord,
                R.layout.history_item,
                arrayOf(KEY_ID, KEY_LOCATION, KEY_TIME, KEY_LNG_LAT_WGS, KEY_LNG_LAT_CUSTOM),
                intArrayOf(R.id.LocationID, R.id.LocationText, R.id.TimeText, R.id.WGSLatLngText, R.id.BDLatLngText)
            )
            mRecordListView.adapter = adapter
        }
    }
}
