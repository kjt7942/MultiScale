package com.kjt7942.multiscale

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.WindowManager

/**
 * 업데이트 설치가 끝나면(ACTION_MY_PACKAGE_REPLACED) 앱을 다시 띄운다.
 * 안드로이드는 백그라운드에서 화면을 여는 걸 막지만, '다른 앱 위에 표시' 권한이 있으면
 * 1px 투명 창을 잠깐 띄워 앱이 '보이는 상태'가 되게 한 뒤 실행할 수 있다.
 */
class RelaunchReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val app = context.applicationContext
        val pending = goAsync()
        val handler = Handler(Looper.getMainLooper())

        var overlay: View? = null
        val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (Settings.canDrawOverlays(app)) {
            try {
                val v = View(app)
                val lp = WindowManager.LayoutParams(
                    1, 1,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT
                )
                wm.addView(v, lp)
                overlay = v
            } catch (_: Exception) {
            }
        }
        try {
            val launch = app.packageManager.getLaunchIntentForPackage(app.packageName)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                app.startActivity(launch)
            }
        } catch (_: Exception) {
        }
        handler.postDelayed({
            try {
                overlay?.let { wm.removeView(it) }
            } catch (_: Exception) {
            }
            pending.finish()
        }, 3000)
    }
}
