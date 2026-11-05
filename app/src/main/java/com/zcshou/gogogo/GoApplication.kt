package com.zcshou.gogogo

import android.app.Application
import com.baidu.location.LocationClient
import com.baidu.mapapi.CoordType
import com.baidu.mapapi.SDKInitializer
import com.elvishew.xlog.LogConfiguration
import com.elvishew.xlog.LogLevel
import com.elvishew.xlog.XLog
import com.elvishew.xlog.printer.ConsolePrinter
import com.elvishew.xlog.printer.file.FilePrinter
import com.elvishew.xlog.printer.file.backup.NeverBackupStrategy
import com.elvishew.xlog.printer.file.clean.FileLastModifiedCleanStrategy
import com.elvishew.xlog.printer.file.naming.ChangelessFileNameGenerator
import java.io.File

/**
 * Application Entry point for LibreGo in Kotlin.
 */
class GoApplication : Application() {

    companion object {
        const val APP_NAME = "LibreGo"
        const val LOG_FILE_NAME = "$APP_NAME.log"
        private const val MAX_TIME = 1000L * 60 * 60 * 24 * 3 // 3 days
    }

    override fun onCreate() {
        super.onCreate()

        initXlog()

        SDKInitializer.setAgreePrivacy(this, true)
        LocationClient.setAgreePrivacy(true)
        SDKInitializer.setApiKey(BuildConfig.MAPS_API_KEY)
        SDKInitializer.initialize(this)
        SDKInitializer.setCoordType(CoordType.BD09LL)

        com.zcshou.shizuku.ShizukuManager.init(this)
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

            val consolePrinter = ConsolePrinter()
            val filePrinter = FilePrinter.Builder(logPath.path)
                .fileNameGenerator(ChangelessFileNameGenerator(LOG_FILE_NAME))
                .backupStrategy(NeverBackupStrategy())
                .cleanStrategy(FileLastModifiedCleanStrategy(MAX_TIME))
                .build()

            XLog.init(config, consolePrinter, filePrinter)
        }
    }
}
