package com.zcshou.gogogo

import android.app.Application
import androidx.preference.PreferenceManager
import com.elvishew.xlog.LogConfiguration
import com.elvishew.xlog.LogLevel
import com.elvishew.xlog.XLog
import com.elvishew.xlog.printer.ConsolePrinter
import com.elvishew.xlog.printer.file.FilePrinter
import com.elvishew.xlog.printer.file.backup.NeverBackupStrategy
import com.elvishew.xlog.printer.file.clean.FileLastModifiedCleanStrategy
import com.elvishew.xlog.printer.file.naming.ChangelessFileNameGenerator
import org.osmdroid.config.Configuration
import java.io.File

/**
 * Application Entry point for LibreGo in Kotlin.
 * Fully open-source, using OpenStreetMap (osmdroid) without API key restrictions.
 */
class GoApplication : Application() {

    companion object {
        const val APP_NAME = "LibreGo"
        const val LOG_FILE_NAME = "$APP_NAME.log"
        private const val MAX_TIME = 1000L * 60 * 60 * 24 * 3 // 3 days
    }

    override fun onCreate() {
        super.onCreate()

        try {
            initXlog()
        } catch (_: Exception) {}

        // Initialize OpenStreetMap (osmdroid) configuration
        try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            Configuration.getInstance().load(this, prefs)
            Configuration.getInstance().userAgentValue = packageName
            XLog.i("GoApplication: osmdroid initialized with package: $packageName")
        } catch (e: Throwable) {
            XLog.e("GoApplication: osmdroid configuration error", e)
        }

        try {
            com.zcshou.shizuku.ShizukuManager.init(this)
        } catch (e: Throwable) {
            XLog.e("GoApplication: ShizukuManager init error", e)
        }
    }

    private fun initXlog() {
        val logPath = getExternalFilesDir("Logs")
        if (logPath != null) {
            val config = LogConfiguration.Builder()
                .logLevel(LogLevel.ALL)
                .tag(APP_NAME)
                .enableThreadInfo()
                .enableStackTrace(2)
                .enableBorder()
                .build()

            val printer = FilePrinter.Builder(File(logPath, LOG_FILE_NAME).path)
                .fileNameGenerator(ChangelessFileNameGenerator(LOG_FILE_NAME))
                .backupStrategy(NeverBackupStrategy())
                .cleanStrategy(FileLastModifiedCleanStrategy(MAX_TIME))
                .build()

            XLog.init(config, ConsolePrinter(), printer)
        }
    }
}
