package com.kjt7942.multiscale

import android.app.Application
import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/** 앱이 비정상 종료되면 원인을 파일에 남기고, 다음 실행 때 화면에 보여준다. */
object CrashLog {
    private fun file(ctx: Context) = File(ctx.filesDir, "last_crash.txt")

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                file(app).writeText("v${BuildConfig.VERSION_NAME} thread=${t.name}\n$sw")
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(t, e)
        }
    }

    /** 남아 있는 기록을 한 번만 돌려주고 지운다. */
    fun take(ctx: Context): String? = try {
        val f = file(ctx)
        if (f.exists()) f.readText().also { f.delete() } else null
    } catch (_: Throwable) {
        null
    }
}

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}
