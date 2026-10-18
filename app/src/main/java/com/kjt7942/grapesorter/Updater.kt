package com.kjt7942.grapesorter

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** 설치 결과 같은 안내 문구를 화면으로 전달하는 통로. */
object UpdateBus {
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    fun post(text: String) {
        messages.tryEmit(text)
    }
}

object Versions {
    /** "v1.2.3" / "1.2.3-beta" 에서 숫자 부분만 뽑는다. 해석할 수 없으면 null. */
    fun parse(tag: String): List<Int>? {
        val core = tag.trim().removePrefix("v").removePrefix("V").substringBefore("-")
        if (core.isEmpty()) return null
        val parts = core.split(".").map { it.toIntOrNull() }
        if (parts.any { it == null }) return null
        return parts.map { it!! }
    }

    fun isNewer(remoteTag: String, localName: String): Boolean {
        val r = parse(remoteTag) ?: return false
        val l = parse(localName) ?: return false
        val n = maxOf(r.size, l.size)
        for (i in 0 until n) {
            val a = if (i < r.size) r[i] else 0
            val b = if (i < l.size) l[i] else 0
            if (a != b) return a > b
        }
        return false
    }
}

class Release(
    val tag: String,
    val apkName: String,
    val apkUrl: String,
    val size: Long,
    val sha256: String?
)

/** GitHub Releases 에서 최신 APK 를 찾아 내려받고, PackageInstaller 로 설치한다. */
class Updater(private val ctx: Context) {

    suspend fun fetchLatest(): Release? = withContext(Dispatchers.IO) {
        val url = URL("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", USER_AGENT)
            val code = conn.responseCode
            if (code == 404) return@withContext null // 아직 릴리스가 없음
            if (code != 200) throw IOException("HTTP $code")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val tag = json.getString("tag_name")
            val assets = json.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val name = a.getString("name")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    val digest = a.optString("digest", "")
                    return@withContext Release(
                        tag = tag,
                        apkName = name,
                        apkUrl = a.getString("browser_download_url"),
                        size = a.optLong("size", 0L),
                        sha256 = if (digest.startsWith("sha256:")) digest.removePrefix("sha256:") else null
                    )
                }
            }
            null
        } finally {
            conn.disconnect()
        }
    }

    suspend fun download(r: Release, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates")
        dir.mkdirs()
        dir.listFiles()?.forEach { if (it.name != r.apkName) it.delete() }
        val dest = File(dir, r.apkName)
        if (dest.exists() && r.size > 0 && dest.length() == r.size) return@withContext dest

        val tmp = File(dir, r.apkName + ".part")
        val conn = URL(r.apkUrl).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.connect()
            if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode}")
            val total = if (conn.contentLengthLong > 0) conn.contentLengthLong else r.size
            val digest = MessageDigest.getInstance("SHA-256")
            var done = 0L
            var lastPercent = -1
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(32 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val p = (done * 100 / total).toInt()
                            if (p != lastPercent) {
                                lastPercent = p
                                onProgress(p)
                            }
                        }
                    }
                }
            }
            if (r.size > 0 && tmp.length() != r.size) throw IOException("파일 크기가 맞지 않습니다")
            if (r.sha256 != null) {
                val hex = digest.digest().joinToString("") { "%02x".format(it) }
                if (!hex.equals(r.sha256, ignoreCase = true)) throw IOException("체크섬이 맞지 않습니다")
            }
            if (!tmp.renameTo(dest)) throw IOException("파일 저장 실패")
            dest
        } finally {
            conn.disconnect()
        }
    }

    /** 이 앱에 '출처를 알 수 없는 앱 설치' 권한이 켜져 있는지. */
    fun canInstall(): Boolean = ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    }

    /**
     * 시스템 설치 세션으로 APK 를 설치한다. 처음에는 확인 창이 한 번 뜨지만,
     * 이 앱이 설치한 앱의 업데이트는 안드로이드 12 이상에서 확인 없이 진행될 수 있다.
     */
    fun install(apk: File) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setSize(apk.length())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("update.apk", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val intent = Intent(ctx, InstallResultReceiver::class.java).setAction(ACTION_INSTALL_RESULT)
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags = flags or PendingIntent.FLAG_MUTABLE
            val pending = PendingIntent.getBroadcast(ctx, id, intent, flags)
            session.commit(pending.intentSender)
        }
    }

    companion object {
        const val ACTION_INSTALL_RESULT = "com.kjt7942.grapesorter.INSTALL_RESULT"
        private const val USER_AGENT = "grape-sorter-app"
    }
}

/** 설치 세션의 결과를 받는다. 사용자 확인이 필요하면 확인 화면을 띄운다. */
class InstallResultReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm: Intent? = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirm)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> UpdateBus.post("업데이트 설치 완료")
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "알 수 없는 오류"
                UpdateBus.post("업데이트 설치 실패: $msg")
            }
        }
    }
}
