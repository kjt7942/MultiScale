package com.kjt7942.multiscale

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.random.Random

class BtDevice(val name: String, val address: String)

data class UiState(
    val weights: List<Int> = List(CHANNELS) { -1 },
    val roles: List<Role> = List(CHANNELS) { Role.NONE },
    val sum: Int = 0,
    val comboWeight: Int? = null,
    val comboLocked: Boolean = false,
    val shortage: Int? = null,
    val target: Int = 2050,
    val minComb: Int = 3,
    val maxComb: Int = 4,
    val tolerance: Int = 50,
    val presetIndex: Int? = null,
    val topup: Boolean = false,
    val lockEnabled: Boolean = true,
    val dark: Boolean = true,
    val presets: List<Preset?> = List(8) { null },
    val link: LinkState = LinkState.DISCONNECTED,
    val linkName: String = "",
    val btPermission: Boolean = true,
    val demo: Boolean = false,
    val message: String? = null,
    val firmware: String? = null,
    val updateStatus: String = "",
    val updateReadyTag: String? = null,
    val boxes: Int = 0
)

class SorterViewModel(app: Application) : AndroidViewModel(app), BluetoothLink.Listener {

    private val prefs = Prefs(app)
    private val engine = SorterEngine { System.currentTimeMillis() }
    private val parser = PacketParser()
    private val link = BluetoothLink(this)
    private val updater = Updater(app)

    var ui by mutableStateOf(UiState())
        private set

    private var presets: List<Preset?> = List(8) { null }
    private var lastDevice: String? = null
    private var dark = true

    private var lastLed: List<Int>? = null
    private var lastLedSentMs = 0L
    private var lastDataMs = 0L
    private var staleFed = false
    private var connected = false
    private var demo = false
    private var demoWeights = IntArray(CHANNELS)
    private var messageJob: Job? = null
    private var readyApk: File? = null
    private var updateBusy = false
    private var lastRawWeights: IntArray = IntArray(CHANNELS) { -1 }
    private var demoJob: Job? = null

    init {
        val s = prefs.read()
        engine.target = s.target
        engine.minComb = s.minComb
        engine.maxComb = s.maxComb
        engine.tolerance = s.tolerance
        engine.lockEnabled = s.lockEnabled
        engine.presetIndex = s.presetIndex
        engine.calMultipliers = s.cal.toDoubleArray()
        engine.restore(s.topup, s.minComb)
        engine.topupMode = s.topup
        presets = s.presets
        lastDevice = s.lastDevice
        dark = s.dark
        process(IntArray(CHANNELS) { -1 }) // 연결 전에는 에러 표시
        syncSettings()
        ui = ui.copy(btPermission = hasBtPermission())

        viewModelScope.launch {
            // 데이터가 끊긴 채 화면이 마지막 값에 머무르지 않게 감시한다.
            while (true) {
                delay(1000)
                val now = System.currentTimeMillis()
                if (connected && !demo && lastDataMs > 0 && now - lastDataMs > 3000 && !staleFed) {
                    staleFed = true
                    process(IntArray(CHANNELS) { -1 })
                }
                // LED(=조합) 표시는 바뀔 때만 보내므로, 블루투스에서 한 번 유실돼도 복구되게 2초마다 다시 보낸다.
                val led = lastLed
                if (connected && !demo && led != null && now - lastLedSentMs > 2000) {
                    lastLedSentMs = now
                    link.send("<${led.joinToString(",")}>\n")
                }
            }
        }
        viewModelScope.launch {
            UpdateBus.messages.collect { showMessage(it, 4000) }
        }
        viewModelScope.launch {
            delay(4000)
            while (true) {
                checkUpdate(manual = false)
                delay(6 * 3600 * 1000L)
            }
        }
    }

    // ---- 블루투스 ------------------------------------------------------------
    private fun adapter() = getApplication<Application>()
        .getSystemService(BluetoothManager::class.java)?.adapter

    fun hasBtPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(
            getApplication(), android.Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun onPermissionResult() {
        ui = ui.copy(btPermission = hasBtPermission())
        if (hasBtPermission()) autoConnect()
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun bondedDevices(): List<BtDevice> {
        if (!hasBtPermission()) return emptyList()
        return try {
            adapter()?.bondedDevices?.map { BtDevice(it.name ?: it.address, it.address) } ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    fun autoConnect() {
        val addr = lastDevice ?: return
        if (!hasBtPermission() || demo) return
        connectTo(addr)
    }

    fun connectTo(address: String) {
        val device: BluetoothDevice = try {
            adapter()?.getRemoteDevice(address) ?: return
        } catch (e: IllegalArgumentException) {
            return
        }
        demo = false
        lastDevice = address
        persist()
        lastLed = null
        link.connect(device)
        ui = ui.copy(demo = false)
    }

    fun disconnectLink() {
        link.disconnect()
    }

    override fun onState(state: LinkState, detail: String) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            connected = state == LinkState.CONNECTED
            ui = ui.copy(link = state, linkName = detail)
            if (state == LinkState.CONNECTED) {
                lastLed = null
                lastDataMs = System.currentTimeMillis()
                staleFed = false
                parser.resetErrHold()
                link.send("<VER>\n")
            } else if (!demo) {
                process(IntArray(CHANNELS) { -1 })
            }
        }
    }

    override fun onLine(line: String) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            for (e in parser.parseLine(line)) {
                when (e) {
                    is Event.Weights -> {
                        if (!demo) {
                            lastDataMs = System.currentTimeMillis()
                            staleFed = false
                            process(e.values)
                        }
                    }
                    is Event.Version -> ui = ui.copy(firmware = e.text)
                    is Event.TareDone -> showMessage("영점 조정이 완료되었습니다.", 2000)
                    is Event.TareOffsets -> {}
                }
            }
        }
    }

    // ---- 계산 결과 반영 --------------------------------------------------------
    private fun process(raw: IntArray) {
        lastRawWeights = raw
        val s = engine.onWeights(raw)
        ui = ui.copy(
            weights = s.weights,
            roles = s.roles,
            sum = s.sum,
            comboWeight = s.comboWeight,
            comboLocked = s.locked,
            shortage = s.shortage,
            boxes = engine.boxesDone
        )
        emitLed(s.leds)
    }

    private fun emitLed(indices: List<Int>) {
        val key = indices.sorted()
        if (key == lastLed) return // 바뀐 게 없으면 보내지 않는다
        lastLed = key
        if (!demo && connected) {
            lastLedSentMs = System.currentTimeMillis()
            link.send("<${indices.joinToString(",")}>\n")
        }
    }

    private fun syncSettings() {
        ui = ui.copy(
            target = engine.target,
            minComb = engine.minComb,
            maxComb = engine.maxComb,
            tolerance = engine.tolerance,
            presetIndex = engine.presetIndex,
            topup = engine.topupMode,
            lockEnabled = engine.lockEnabled,
            presets = presets,
            dark = dark
        )
    }

    private fun persist() {
        prefs.write(
            Saved(
                target = engine.target, minComb = engine.minComb, maxComb = engine.maxComb,
                tolerance = engine.tolerance, dark = dark, topup = engine.topupMode,
                lockEnabled = engine.lockEnabled, presetIndex = engine.presetIndex,
                presets = presets, cal = engine.calMultipliers.toList(), lastDevice = lastDevice
            )
        )
    }

    private fun reprocess() {
        // 설정이 바뀌면 같은 무게로 다시 계산한다.
        process(if (demo) demoWeights else lastRawWeights)
    }

    // ---- 화면 동작 ------------------------------------------------------------
    fun showMessage(text: String, ms: Int) {
        messageJob?.cancel()
        ui = ui.copy(message = text)
        messageJob = viewModelScope.launch {
            delay(ms.toLong())
            ui = ui.copy(message = null)
        }
    }

    fun dismissMessage() {
        messageJob?.cancel()
        ui = ui.copy(message = null)
    }

    fun changeSetting(kind: String, delta: Int) {
        engine.changeSetting(kind, delta)
        syncSettings()
        persist()
        reprocess()
    }

    fun cyclePreset(direction: Int) {
        if (presets.all { it == null }) return
        var idx = engine.presetIndex ?: 0
        for (k in 0 until 8) {
            idx = ((idx + direction) % 8 + 8) % 8
            val p = presets[idx]
            if (p != null) {
                loadPreset(idx)
                return
            }
        }
    }

    fun loadPreset(index: Int) {
        val p = presets[index] ?: return
        engine.loadPreset(index, p)
        syncSettings()
        persist()
        reprocess()
    }

    fun savePreset(index: Int) {
        val list = presets.toMutableList()
        list[index] = Preset(engine.target, engine.minComb, engine.maxComb, engine.tolerance)
        presets = list
        engine.presetIndex = index
        syncSettings()
        persist()
    }

    fun clearPresets() {
        presets = List(8) { null }
        engine.presetIndex = null
        syncSettings()
        persist()
    }

    fun toggleTopup() {
        engine.toggleTopup()
        syncSettings()
        persist()
        reprocess()
        showMessage(
            if (engine.topupMode) "보태기 모드 켜짐.\n빈 박스를 올리고 영점을 눌러주세요."
            else "보태기 모드 꺼짐.\n박스를 치우고 영점을 눌러주세요.", 4000
        )
    }

    fun toggleLock() {
        engine.toggleLock()
        syncSettings()
        persist()
        reprocess()
    }

    fun toggleTheme() {
        dark = !dark
        syncSettings()
        persist()
    }

    fun tare() {
        engine.onTare()
        showMessage("영점 조정 중입니다.\n저울에서 손을 떼세요.", 10000)
        if (demo) {
            viewModelScope.launch {
                delay(1500)
                showMessage("영점 조정이 완료되었습니다.", 2000)
            }
        } else if (connected) {
            link.send("<TARE>\n")
        } else {
            showMessage("블루투스가 연결되어 있지 않습니다.", 2500)
        }
    }

    fun forceUnlock() {
        engine.forceUnlock()
        if (demo) {
            demoWeights = IntArray(CHANNELS) { Random.nextInt(500, 1001) }
            showMessage("모든 저울의 무게가 무작위로 변경되었습니다.", 1000)
        } else {
            showMessage("조합을 다시 찾습니다.", 900)
        }
        reprocess()
    }

    /** 테스트 모드에서만: 저울을 눌러 비우거나(0g) 새 송이를 올린다(500~1000g). */
    fun onTrayClick(i: Int) {
        if (!demo) {
            // 실제 연결 상태: 무게가 있고 조합/보태기에 쓰이는 저울이면 아두이노에 '비우기'를 요청한다.
            // (시뮬레이터 펌웨어 전용 명령: <E번호>, 번호는 1부터)
            if (connected && ui.weights[i] > 0 && ui.roles[i] != Role.NONE) link.send("<E${i + 1}>\n")
            return
        }
        demoWeights[i] = if (demoWeights[i] > 0) 0 else Random.nextInt(500, 1001)
        reprocess()
    }

    // ---- 테스트 모드(장치 없이 화면 확인) ---------------------------------------
    fun toggleDemo() {
        demo = !demo
        demoJob?.cancel()
        if (demo) {
            link.disconnect()
            demoWeights = IntArray(CHANNELS) { if (Random.nextInt(10) > 0) Random.nextInt(500, 1001) else 0 }
            lastLed = null
            ui = ui.copy(demo = true)
            reprocess()
            // 안정 판정은 시간이 필요해서 실기처럼 주기적으로 다시 계산한다.
            demoJob = viewModelScope.launch {
                while (true) {
                    delay(500)
                    reprocess()
                }
            }
        } else {
            ui = ui.copy(demo = false)
            process(IntArray(CHANNELS) { -1 })
            autoConnect()
        }
    }

    // ---- 업데이트 -------------------------------------------------------------
    fun checkUpdate(manual: Boolean) {
        if (updateBusy) return
        updateBusy = true
        viewModelScope.launch {
            try {
                if (manual || ui.updateReadyTag == null) ui = ui.copy(updateStatus = "업데이트 확인 중…")
                val r = updater.fetchLatest()
                if (r == null) {
                    ui = ui.copy(updateStatus = if (manual) "릴리스를 찾지 못했습니다." else "")
                } else if (!Versions.isNewer(r.tag, BuildConfig.VERSION_NAME)) {
                    ui = ui.copy(updateStatus = "최신 버전입니다. (${BuildConfig.VERSION_NAME})")
                } else {
                    ui = ui.copy(updateStatus = "새 버전 ${r.tag} 내려받는 중… 0%")
                    val file = updater.download(r) { p ->
                        viewModelScope.launch(Dispatchers.Main.immediate) {
                            ui = ui.copy(updateStatus = "새 버전 ${r.tag} 내려받는 중… $p%")
                        }
                    }
                    readyApk = file
                    ui = ui.copy(updateReadyTag = r.tag, updateStatus = "새 버전 ${r.tag} 설치 준비 완료")
                }
            } catch (e: Exception) {
                ui = ui.copy(updateStatus = if (manual) "업데이트 확인 실패: ${e.message}" else "")
            } finally {
                updateBusy = false
            }
        }
    }

    fun installUpdate() {
        val apk = readyApk ?: return
        if (!updater.canInstall()) {
            showMessage("'이 앱의 앱 설치 허용'을 켠 뒤\n다시 눌러주세요.", 4000)
            updater.openInstallPermissionSettings()
            return
        }
        try {
            updater.install(apk)
            showMessage("설치를 시작합니다.", 2500)
        } catch (e: Exception) {
            showMessage("설치 시작 실패: ${e.message}", 4000)
        }
    }

    override fun onCleared() {
        link.disconnect()
        super.onCleared()
    }
}
