package com.kjt7942.multiscale

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {
    private var crashScreen = false

    private val vm: SorterViewModel by viewModels()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            vm.onPermissionResult()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 지난 실행이 비정상 종료였다면 원인을 먼저 보여준다(캡처해서 알려주세요).
        val crash = CrashLog.take(this)
        if (crash != null) {
            crashScreen = true
            val text = android.widget.TextView(this).apply {
                this.text = "지난 실행에서 오류가 발생했습니다.\n\n$crash"
                textSize = 11f
                setTextIsSelectable(true)
                setTextColor(android.graphics.Color.WHITE)
                setPadding(32, 32, 32, 32)
            }
            val button = android.widget.Button(this).apply {
                this.text = "앱 실행"
                setOnClickListener { crashScreen = false; recreate() }
            }
            val box = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setBackgroundColor(android.graphics.Color.BLACK)
                addView(android.widget.ScrollView(this@MainActivity).apply { addView(text) },
                    android.widget.LinearLayout.LayoutParams(-1, 0, 1f))
                addView(button)
            }
            setContentView(box)
            return
        }

        // 저울 옆에 두고 쓰는 화면이라 전체화면 + 화면 켜짐 유지
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        requestedOrientation = orientationFlag(vm.ui.layout.orientation)
        setContent {
            // 설정에서 가로/세로/자동을 바꾸면 바로 반영한다.
            val wanted = vm.ui.layout.orientation
            androidx.compose.runtime.LaunchedEffect(wanted) { requestedOrientation = orientationFlag(wanted) }
            MainScreen(
                vm = vm,
                onRequestPermission = { requestBluetoothPermission() },
                onOpenBtSettings = { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
            )
        }

        if (vm.hasBtPermission()) {
            vm.autoConnect()
        } else {
            requestBluetoothPermission()
        }
    }

    override fun onResume() {
        super.onResume()
        if (crashScreen) return
        // 설정 화면에서 권한/페어링을 바꾸고 돌아온 경우
        vm.onPermissionResult()
    }

    private fun orientationFlag(o: String): Int = when (o) {
        "portrait" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        "auto" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    private fun requestBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
        }
    }
}
