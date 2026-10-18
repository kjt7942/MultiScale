package com.kjt7942.multiscale

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

fun fmt(n: Int): String = String.format(Locale.US, "%,d", n)

class Palette(
    val bg: Color, val card: Color, val cardBorder: Color, val text: Color, val grey: Color,
    val dim: Color, val err: Color, val sumValue: Color,
    val comboOnBg: Color, val comboOnBorder: Color, val comboTitle: Color, val comboValue: Color,
    val topupBg: Color, val topupBorder: Color,
    val btnBg: Color, val btnBorder: Color, val btnText: Color
)

fun palette(dark: Boolean): Palette = if (dark) {
    Palette(
        bg = Color(0xFF121212), card = Color(0xFF1E1E1E), cardBorder = Color(0xFF333333),
        text = Color(0xFFE0E0E0), grey = Color(0xFF858585), dim = Color(0xFF555555),
        err = Color(0xFFEF4444), sumValue = Color(0xFFF87171),
        comboOnBg = Color(0xFF064E3B), comboOnBorder = Color(0xFF059669),
        comboTitle = Color(0xFF6EE7B7), comboValue = Color(0xFFA7F3D0),
        topupBg = Color(0xFF1E3A8A), topupBorder = Color(0xFF3B82F6),
        btnBg = Color(0xFF2D2D2D), btnBorder = Color(0xFF404040), btnText = Color(0xFFE0E0E0)
    )
} else {
    Palette(
        bg = Color(0xFFF4F6F8), card = Color(0xFFFFFFFF), cardBorder = Color(0xFF9CA3AF),
        text = Color(0xFF1F2937), grey = Color(0xFF9CA3AF), dim = Color(0xFF9CA3AF),
        err = Color(0xFFEF4444), sumValue = Color(0xFF1F2937),
        comboOnBg = Color(0xFFECFDF5), comboOnBorder = Color(0xFF10B981),
        comboTitle = Color(0xFF065F46), comboValue = Color(0xFF047857),
        topupBg = Color(0xFFDBEAFE), topupBorder = Color(0xFF2563EB),
        btnBg = Color(0xFFFFFFFF), btnBorder = Color(0xFF9CA3AF), btnText = Color(0xFF4B5563)
    )
}

private val NUMS = listOf("①", "②", "③", "④", "⑤", "⑥", "⑦", "⑧", "⑨", "⑩", "⑪", "⑫")
private val SLOT_NAMES = listOf("A", "B", "C", "D", "E", "F", "G", "H")
private val Blue = Color(0xFF2563EB)

@Composable
fun MainScreen(
    vm: SorterViewModel,
    onRequestPermission: () -> Unit,
    onOpenBtSettings: () -> Unit
) {
    val ui = vm.ui
    val p = palette(ui.dark)
    var showManage by remember { mutableStateOf(false) }
    var showDevices by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(p.bg)) {
        Column(
            Modifier.fillMaxSize().displayCutoutPadding().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StatusStrip(ui, p, onLink = { showDevices = true }, onUpdate = { vm.installUpdate() })
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LeftPanel(vm, ui, p, Modifier.weight(10f).fillMaxHeight())
                RightPanel(vm, ui, p, Modifier.weight(8f).fillMaxHeight(), onManage = { showManage = true })
            }
        }

        if (ui.message != null) {
            Box(
                Modifier.fillMaxSize().background(Color(0x66000000)).clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null
                ) { vm.dismissMessage() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    ui.message,
                    modifier = Modifier.widthIn(max = 520.dp).clip(RoundedCornerShape(20.dp))
                        .background(Color(0xD9000000)).padding(horizontal = 28.dp, vertical = 24.dp),
                    color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }

        if (showDevices) {
            DeviceDialog(vm, ui, p, onClose = { showDevices = false },
                onRequestPermission = onRequestPermission, onOpenBtSettings = onOpenBtSettings)
        }
        if (showManage) {
            ManageDialog(vm, ui, p, onClose = { showManage = false }, onDevices = {
                showManage = false
                showDevices = true
            })
        }
    }
}

@Composable
fun StatusStrip(ui: UiState, p: Palette, onLink: () -> Unit, onUpdate: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
        val (label, color) = when {
            ui.demo -> "● 테스트 모드 (가짜 무게)" to Color(0xFFF87171)
            ui.link == LinkState.CONNECTED -> "● 연결됨 ${ui.linkName}" to Color(0xFF34D399)
            ui.link == LinkState.CONNECTING -> "● 연결 중… ${ui.linkName}" to Color(0xFFFBBF24)
            else -> "● 연결 안 됨 (눌러서 연결)" to Color(0xFFF87171)
        }
        Text(label, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { onLink() }.padding(end = 12.dp))
        if (ui.updateReadyTag != null) {
            Text(
                "⬆ 새 버전 ${ui.updateReadyTag} 설치",
                color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Blue)
                    .clickable { onUpdate() }.padding(horizontal = 10.dp, vertical = 2.dp)
            )
        }
        Spacer(Modifier.weight(1f))
        Text("v${BuildConfig.VERSION_NAME}", color = p.grey, fontSize = 12.sp)
    }
}

@Composable
fun LeftPanel(vm: SorterViewModel, ui: UiState, p: Palette, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.weight(6f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (col in 0 until 2) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (row in 0 until 6) {
                        val i = col * 6 + row
                        WeightCard(NUMS[i], ui.weights[i], ui.roles[i], p, Modifier.weight(1f).fillMaxWidth()) {
                            vm.onTrayClick(i)
                        }
                    }
                }
            }
        }
        Row(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.card)
                .border(2.dp, p.cardBorder, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (ui.topup) "보태기 합계" else "합계", color = p.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("${fmt(ui.sum)} g", color = p.sumValue, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun WeightCard(num: String, weight: Int, role: Role, p: Palette, modifier: Modifier, onClick: () -> Unit) {
    val bg = when (role) {
        Role.TOPUP -> p.topupBg
        Role.COMBO -> p.comboOnBg
        else -> p.card
    }
    val border = when (role) {
        Role.TOPUP -> p.topupBorder
        Role.COMBO -> p.comboOnBorder
        else -> p.cardBorder
    }
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier.clip(shape).background(bg).border(2.dp, border, shape)
            .clickable { onClick() }.padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(num, color = p.grey, fontSize = 15.sp)
        Spacer(Modifier.weight(1f))
        val text: String
        val color: Color
        if (weight > 0) {
            text = "${fmt(weight)} g"; color = p.text
        } else if (weight == -1) {
            text = "에러(ERR)"; color = p.err
        } else {
            text = "${fmt(weight)} g"; color = p.dim
        }
        Text(text, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun RightPanel(vm: SorterViewModel, ui: UiState, p: Palette, modifier: Modifier, onManage: () -> Unit) {
    val rowMod = Modifier.fillMaxWidth()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val preset = if (ui.presetIndex != null) "슬롯 ${SLOT_NAMES[ui.presetIndex]}" else "수동설정"
        SettingRow(
            "$preset : ${fmt(ui.target)}g (${ui.minComb}~${ui.maxComb}개)", p, rowMod.weight(1f),
            onMinus = { m -> if (m == 1) vm.cyclePreset(-1) },
            onPlus = { m -> if (m == 1) vm.cyclePreset(1) }
        )
        SettingRow("목표무게 : ${fmt(ui.target)} g", p, rowMod.weight(1f),
            onMinus = { m -> vm.changeSetting("target", -10 * m) },
            onPlus = { m -> vm.changeSetting("target", 10 * m) })
        SettingRow("허용오차 : +${ui.tolerance} g", p, rowMod.weight(1f),
            onMinus = { m -> vm.changeSetting("tol", -5 * m) },
            onPlus = { m -> vm.changeSetting("tol", 5 * m) })
        SettingRow("최소조합 : ${ui.minComb} 개", p, rowMod.weight(1f),
            onMinus = { _ -> vm.changeSetting("min", -1) },
            onPlus = { _ -> vm.changeSetting("min", 1) })
        SettingRow("최대조합 : ${ui.maxComb} 개", p, rowMod.weight(1f),
            onMinus = { _ -> vm.changeSetting("max", -1) },
            onPlus = { _ -> vm.changeSetting("max", 1) })

        ComboCard(ui, p, rowMod.weight(2.2f)) { vm.forceUnlock() }

        Row(rowMod.weight(1.3f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton("영점", p, Modifier.weight(1f).fillMaxHeight()) { vm.tare() }
            ActionButton("보태기", p, Modifier.weight(1f).fillMaxHeight(), active = ui.topup) { vm.toggleTopup() }
            ActionButton("관리", p, Modifier.weight(1f).fillMaxHeight()) { onManage() }
            ActionButton(if (ui.dark) "밝게" else "어둡게", p, Modifier.weight(1f).fillMaxHeight()) { vm.toggleTheme() }
        }
    }
}

@Composable
fun SettingRow(text: String, p: Palette, modifier: Modifier, onMinus: (Int) -> Unit, onPlus: (Int) -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier.clip(shape).background(p.card).border(2.dp, p.cardBorder, shape).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HoldButton("−", p, onMinus)
        Text(text, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = p.text,
            fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        HoldButton("+", p, onPlus)
    }
}

/** 누르면 1단계, 0.4초 이상 누르고 있으면 0.1초마다 5단계씩 반복(기존 HoldButton 과 같은 동작). */
@Composable
fun HoldButton(label: String, p: Palette, onStep: (Int) -> Unit) {
    val scope = rememberCoroutineScope()
    val current by rememberUpdatedState(onStep)
    Box(
        Modifier.size(width = 44.dp, height = 34.dp).clip(RoundedCornerShape(10.dp)).background(p.btnBg)
            .border(2.dp, p.btnBorder, RoundedCornerShape(10.dp))
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    current(1)
                    val job = scope.launch {
                        delay(400)
                        while (true) {
                            current(5)
                            delay(100)
                        }
                    }
                    tryAwaitRelease()
                    job.cancel()
                })
            },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = p.btnText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ComboCard(ui: UiState, p: Palette, modifier: Modifier, onClick: () -> Unit) {
    val ok = ui.comboWeight != null
    val shape = RoundedCornerShape(20.dp)
    val bg = if (ok) p.comboOnBg else p.card
    val border = if (ok) p.comboOnBorder else p.cardBorder
    val title = when {
        ok && ui.comboLocked -> "조합잠금"
        !ok && ui.shortage != null -> "${fmt(ui.shortage)}g\n부족"
        else -> "조합무게"
    }
    Row(
        modifier.clip(shape).background(bg).border(3.dp, border, shape)
            .clickable { onClick() }.padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = p.comboTitle, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Text(
            if (ok) "${fmt(ui.comboWeight ?: 0)} g" else "조합실패",
            color = if (ok) p.comboValue else p.text, fontSize = 34.sp, fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ActionButton(label: String, p: Palette, modifier: Modifier, active: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val bg = if (active) Blue else p.btnBg
    val border = if (active) Color(0xFF1E40AF) else p.btnBorder
    val fg = if (active) Color.White else p.btnText
    Box(
        modifier.clip(shape).background(bg).border(2.dp, border, shape).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = fg, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

// ---------------------------------------------------------------------------
// 관리(프리셋·연결·업데이트) / 기기 선택
// ---------------------------------------------------------------------------
@Composable
fun ManageDialog(vm: SorterViewModel, ui: UiState, p: Palette, onClose: () -> Unit, onDevices: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.94f).clip(RoundedCornerShape(16.dp))
                .background(p.bg).border(2.dp, p.cardBorder, RoundedCornerShape(16.dp)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("제품 등록 및 관리", color = p.text, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                SmallButton("블루투스 연결", p, Blue, Color.White, onDevices)
                SmallButton(if (ui.demo) "테스트모드:켜짐" else "테스트모드:꺼짐", p,
                    if (ui.demo) Color(0xFFB45309) else p.btnBg, if (ui.demo) Color.White else p.btnText) { vm.toggleDemo() }
                SmallButton(if (ui.lockEnabled) "조합잠금:켜짐" else "조합잠금:꺼짐", p,
                    if (ui.lockEnabled) Color(0xFF059669) else Color(0xFF6B7280), Color.White) { vm.toggleLock() }
                SmallButton("비우기", p, Color(0xFFEF4444), Color.White) { vm.clearPresets() }
            }
            Text("버튼을 짧게 터치하면 불러오기, 2초간 길게 누르면 현재 설정이 저장됩니다.",
                color = p.grey, fontSize = 13.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (r in 0 until 2) {
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (c in 0 until 4) {
                            val i = r * 4 + c
                            PresetSlot(i, ui.presets[i], ui.presetIndex == i, p, Modifier.weight(1f).fillMaxHeight(),
                                onLoad = { vm.loadPreset(i); onClose() }, onSave = { vm.savePreset(i) })
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "앱 v${BuildConfig.VERSION_NAME}" +
                        (if (ui.firmware != null) " · 펌웨어 ${ui.firmware}" else "") +
                        (if (ui.updateStatus.isNotEmpty()) "  |  ${ui.updateStatus}" else ""),
                    color = p.grey, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 2
                )
                if (ui.updateReadyTag != null) {
                    SmallButton("지금 업데이트", p, Blue, Color.White) { vm.installUpdate() }
                }
                SmallButton(
                    if (ui.relaunchOk) "자동 재실행:켜짐" else "자동 재실행:허용하기", p,
                    if (ui.relaunchOk) p.btnBg else Blue, if (ui.relaunchOk) p.btnText else Color.White
                ) { if (!ui.relaunchOk) vm.openOverlaySettings() }
                SmallButton("업데이트 확인", p, p.btnBg, p.btnText) { vm.checkUpdate(manual = true) }
                SmallButton("닫기", p, Color(0xFF6B7280), Color.White, onClose)
            }
        }
    }
}

@Composable
fun SmallButton(label: String, p: Palette, bg: Color, fg: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier.height(40.dp).clip(shape).background(bg).border(BorderStroke(1.dp, p.btnBorder), shape)
            .clickable { onClick() }.padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PresetSlot(
    index: Int, preset: Preset?, selected: Boolean, p: Palette, modifier: Modifier,
    onLoad: () -> Unit, onSave: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val load by rememberUpdatedState(onLoad)
    val save by rememberUpdatedState(onSave)
    var savedFlash by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val text = when {
        savedFlash -> "슬롯 ${SLOT_NAMES[index]}\n저장됨!"
        preset == null -> "슬롯 ${SLOT_NAMES[index]}\n(비어있음)"
        else -> "슬롯 ${SLOT_NAMES[index]}\n${fmt(preset.target)}g +${preset.tolerance}\n(${preset.minComb}~${preset.maxComb}개)"
    }
    Box(
        modifier.clip(shape)
            .background(if (savedFlash) Color(0xFF059669) else p.card)
            .border(2.dp, if (selected) Blue else p.cardBorder, shape)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    var longDone = false
                    val job = scope.launch {
                        delay(2000)
                        longDone = true
                        save()
                        savedFlash = true
                        delay(900)
                        savedFlash = false
                    }
                    val released = tryAwaitRelease()
                    if (released && !longDone) {
                        job.cancel()
                        load()
                    } else if (!longDone) {
                        job.cancel()
                    }
                })
            },
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (savedFlash) Color.White else p.text, fontSize = 16.sp,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Composable
fun DeviceDialog(
    vm: SorterViewModel, ui: UiState, p: Palette, onClose: () -> Unit,
    onRequestPermission: () -> Unit, onOpenBtSettings: () -> Unit
) {
    var devices by remember { mutableStateOf(vm.bondedDevices()) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth(0.8f).fillMaxHeight(0.92f).clip(RoundedCornerShape(16.dp))
                .background(p.bg).border(2.dp, p.cardBorder, RoundedCornerShape(16.dp)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("블루투스 연결", color = p.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("먼저 폰 설정 > 블루투스에서 저울 모듈(HC-05/HC-06, 비밀번호 1234 또는 0000)을 페어링하세요.",
                color = p.grey, fontSize = 13.sp)
            if (!ui.btPermission) {
                Text("블루투스 권한이 필요합니다.", color = p.err, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                SmallButton("권한 허용", p, Blue, Color.White) { onRequestPermission() }
            } else if (devices.isEmpty()) {
                Text("페어링된 기기가 없습니다.", color = p.text, fontSize = 14.sp)
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(devices) { d ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(p.card)
                                .border(1.dp, p.cardBorder, RoundedCornerShape(10.dp))
                                .clickable { vm.connectTo(d.address); onClose() }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(d.name, color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f))
                            Text(d.address, color = p.grey, fontSize = 12.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.weight(if (devices.isEmpty() || !ui.btPermission) 1f else 0.0001f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton("블루투스 설정 열기", p, p.btnBg, p.btnText) { onOpenBtSettings() }
                SmallButton("목록 새로고침", p, p.btnBg, p.btnText) { devices = vm.bondedDevices() }
                if (ui.link != LinkState.DISCONNECTED) {
                    SmallButton("연결 끊기", p, Color(0xFFEF4444), Color.White) { vm.disconnectLink(); onClose() }
                }
                Spacer(Modifier.width(1.dp).weight(1f))
                SmallButton("닫기", p, Color(0xFF6B7280), Color.White, onClose)
            }
        }
    }
}
