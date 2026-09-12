package com.tvparapobres.app

import android.app.Application
import java.io.File

/** Guarda cualquier crash en crash.txt para mostrarlo en CrashReportActivity. */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sb = StringBuilder()
                sb.append("version=").append(BuildConfig.VERSION_NAME).append('\n')
                sb.append("thread=").append(t.name).append('\n')
                sb.append(android.util.Log.getStackTraceString(e))
                File(filesDir, "crash.txt").writeText(sb.toString())
            } catch (_: Exception) {
            }
            if (prev != null) prev.uncaughtException(t, e)
            else android.os.Process.killProcess(android.os.Process.myPid())
        }
    }
}
