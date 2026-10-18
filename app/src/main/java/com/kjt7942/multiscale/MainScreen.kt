package com.kjt7942.multiscale

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

fun fmt(n: Int): String = String.format(Locale.US, "%,d", n)

private val SLOT_NAMES = listOf("A", "B", "C", "D", "E", "F", "G", "H")

/** 화면 크기에 따른 여백/글자 크기. 폰(짧은 변 < 480dp)은 촘촘하게, 패드는 넉넉하게. */
private class Dim(val compact: Boolean) {
    val pad: Dp = if (compact) 8.dp else 16.dp
    val gap: Dp = if (compact) 6.dp else 12.dp
    val radius: Dp = if (compact) 14.dp else 22.dp
    val heroValue = if (compact) 30.sp else 52.sp
    val kpiValue = if (compact) 20.sp else 36.sp
    val kpiLabel = if (compact) 11.sp else 13.sp
    val title = if (compact) 17.sp else 22.sp
    val stepValue = if (compact) 18.sp else 26.sp
    val bottomH: Dp = if (compact) 52.dp else 80.dp
    val kpiH: Dp = if (compact) 64.dp else 112.dp
}

@Composable
fun MainScreen(
    vm: SorterViewModel,
    onRequestPermission: () -> Unit,
    onOpenBtSettings: () -> Unit
) {
    val ui = vm.ui
    val p = paletteFor(ui.layout.theme)
    var settingsTab by remember { mutableStateOf<Int?>(null) }
    var showDevices by remember { mutableStateOf(false) }
    var editMode by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<Int?>(null) }

    BoxWithConstraints(Modifier.fillMaxSize().background(p.bg)) {
        val wide = maxWidth > maxHeight
        val d = Dim(minOf(maxWidth, maxHeight) < 480.dp)

        Column(
            Modifier.fillMaxSize().displayCutoutPadding().padding(d.pad),
            verticalArrangement = Arrangement.spacedBy(d.gap)
        ) {
            TopBar(ui, p, d, onLink = { showDevices = true }, onSettings = { settingsTab = 0 })
            if (ui.updateReadyTag != null) {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.primary)
                        .clickable { vm.installUpdate() }.padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("⬆ 새 버전 ${ui.updateReadyTag} 설치하기", color = p.onPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
            val found = ui.detectedChannels
            if (found != null) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.topupBg).padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("펌웨어가 저울 ${found}개를 보내고 있어요 (설정: ${ui.layout.channels}개)",
                        color = p.topupText, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    SmallButton("${found}개로 맞추기", p, p.primary, p.onPrimary) { vm.applyDetectedChannels() }
                }
            }
            if (editMode) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.hero).padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("자리 바꾸기 — 저울 두 개를 차례로 누르면 서로 바뀝니다 (빈 칸으로 옮길 수도 있어요)",
                        color = p.heroText, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    SmallButton("초기화", p, p.btn, p.btnText) { vm.resetCells(); picked = null }
                    SmallButton("완료", p, p.accent, p.onAccent) { editMode = false; picked = null }
                }
            }

            if (wide) {
                Row(Modifier.fillMaxWidth().height(d.kpiH), horizontalArrangement = Arrangement.spacedBy(d.gap)) {
                    HeroCard(ui, p, d, Modifier.weight(2.1f).fillMaxHeight()) { vm.forceUnlock() }
                    KpiCard("목표 · 오차", fmt(ui.target), "±${ui.tolerance}", p, d, Modifier.weight(1f).fillMaxHeight())
                    KpiCard(if (ui.topup) "보태기 합계" else "전체 합계", fmt(ui.sum), "g", p, d, Modifier.weight(1f).fillMaxHeight())
                    KpiCard("완료 상자", "${ui.boxes}", "", p, d, Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                HeroCard(ui, p, d, Modifier.fillMaxWidth().height(d.kpiH + 12.dp)) { vm.forceUnlock() }
                Row(Modifier.fillMaxWidth().height(d.kpiH - 8.dp), horizontalArrangement = Arrangement.spacedBy(d.gap)) {
                    KpiCard("목표 · 오차", fmt(ui.target), "±${ui.tolerance}", p, d, Modifier.weight(1.2f).fillMaxHeight())
                    KpiCard(if (ui.topup) "보태기 합계" else "합계", fmt(ui.sum), "g", p, d, Modifier.weight(1f).fillMaxHeight())
                    KpiCard("상자", "${ui.boxes}", "", p, d, Modifier.weight(0.7f).fillMaxHeight())
                }
            }

            TrayGrid(vm, ui, p, d, editMode, picked, Modifier.weight(1f).fillMaxWidth()) { cell ->
                val a = picked
                if (a == null) picked = cell
                else if (a == cell) picked = null
                else { vm.swapCells(a, cell); picked = null }
            }

            if (wide) {
                Row(Modifier.fillMaxWidth().height(d.bottomH), horizontalArrangement = Arrangement.spacedBy(d.gap)) {
                    Row(Modifier.weight(1f).fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(d.gap)) {
                        Steppers(vm, ui, p, d, Modifier.weight(1f).fillMaxHeight())
                    }
                    Actions(vm, ui, p, d, Modifier.fillMaxHeight().widthIn(max = if (d.compact) 260.dp else 360.dp)) { settingsTab = 1 }
                }
            } else {
                Steppers(vm, ui, p, d, Modifier.fillMaxWidth().height(d.bottomH * 2 + d.gap), twoRows = true)
                Actions(vm, ui, p, d, Modifier.fillMaxWidth().height(d.bottomH)) { settingsTab = 1 }
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
                    color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }

        if (showDevices) {
            DeviceDialog(vm, ui, p, onClose = { showDevices = false },
                onRequestPermission = onRequestPermission, onOpenBtSettings = onOpenBtSettings)
        }
        val tab = settingsTab
        if (tab != null) {
            SettingsDialog(vm, ui, p, tab, onTab = { settingsTab = it }, onClose = { settingsTab = null },
                onDevices = { settingsTab = null; showDevices = true },
                onEditCells = { settingsTab = null; editMode = true; picked = null })
        }
    }
}

// ---------------------------------------------------------------------------
// 상단/지표
// ---------------------------------------------------------------------------
@Composable
private fun TopBar(ui: UiState, p: Palette, d: Dim, onLink: () -> Unit, onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("MultiScale", color = p.text, fontSize = d.title, fontWeight = FontWeight.ExtraBold)
        val (label, color) = when {
            ui.demo -> "테스트 모드" to p.err
            ui.link == LinkState.CONNECTED -> "${ui.linkName.ifEmpty { "HC-06" }} 연결됨" to p.ok
            ui.link == LinkState.CONNECTING -> "연결 중…" to Color(0xFFD99A00)
            else -> "연결 안 됨" to p.err
        }
        Row(
            Modifier.clip(RoundedCornerShape(999.dp)).background(p.card).border(1.dp, p.outline, RoundedCornerShape(999.dp))
                .clickable { onLink() }.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Text(label, color = p.text, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.clip(RoundedCornerShape(12.dp)).background(p.card).border(1.dp, p.outline, RoundedCornerShape(12.dp))
                .clickable { onSettings() }.padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("⚙ 설정", color = p.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HeroCard(ui: UiState, p: Palette, d: Dim, modifier: Modifier, onClick: () -> Unit) {
    val ok = ui.comboWeight != null
    val status = when {
        ui.link != LinkState.CONNECTED && !ui.demo -> "연결 대기"
        ok && ui.comboLocked -> "조합 완료 · 잠금"
        ok -> "조합 완료"
        ui.shortage != null -> "${fmt(ui.shortage)}g 부족"
        else -> "조합 찾는 중"
    }
    val shape = RoundedCornerShape(d.radius)
    Row(
        modifier.clip(shape).background(p.hero).clickable { onClick() }.padding(horizontal = if (d.compact) 12.dp else 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.Center) {
            Text("조합 무게", color = p.heroSub, fontSize = d.kpiLabel)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    if (ok) fmt(ui.comboWeight ?: 0) else "—",
                    color = p.heroText, fontSize = d.heroValue, fontWeight = FontWeight.Bold, maxLines = 1
                )
                if (ok) Text(" g", color = p.heroSub, fontSize = d.kpiLabel, modifier = Modifier.padding(bottom = 6.dp))
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically)) {
            Text(
                status,
                modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(if (ok) p.accent else p.heroSub.copy(alpha = 0.25f))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                color = if (ok) p.onAccent else p.heroText, fontSize = if (d.compact) 11.sp else 13.sp, fontWeight = FontWeight.ExtraBold
            )
            val nums = ui.roles.withIndex().filter { it.value == Role.COMBO }.map { it.index + 1 }
            if (nums.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (n in nums.take(7)) {
                        Text("${n}번", color = p.heroText, fontSize = if (d.compact) 11.sp else 13.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(p.heroSub.copy(alpha = 0.25f)).padding(horizontal = 7.dp, vertical = 2.dp))
                    }
                    if (nums.size > 7) Text("+${nums.size - 7}", color = p.heroSub, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun KpiCard(label: String, value: String, unit: String, p: Palette, d: Dim, modifier: Modifier) {
    val shape = RoundedCornerShape(d.radius)
    Column(
        modifier.clip(shape).background(p.card).border(1.dp, p.outline, shape).padding(horizontal = if (d.compact) 10.dp else 18.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(label, color = p.sub, fontSize = d.kpiLabel, maxLines = 1)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = p.text, fontSize = d.kpiValue, fontWeight = FontWeight.Bold, maxLines = 1)
            if (unit.isNotEmpty()) Text(" $unit", color = p.sub, fontSize = d.kpiLabel, modifier = Modifier.padding(bottom = 4.dp), maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------------------
// 저울 격자
// ---------------------------------------------------------------------------
@Composable
private fun TrayGrid(
    vm: SorterViewModel, ui: UiState, p: Palette, d: Dim, edit: Boolean, picked: Int?,
    modifier: Modifier, onPick: (Int) -> Unit
) {
    val lay = ui.layout
    Column(modifier, verticalArrangement = Arrangement.spacedBy(d.gap)) {
        for (r in 0 until lay.rows) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(d.gap)) {
                for (c in 0 until lay.cols) {
                    val cell = r * lay.cols + c
                    val tray = lay.cells.getOrElse(cell) { -1 }
                    val cellMod = Modifier.weight(1f).fillMaxHeight()
                    if (tray in ui.weights.indices) {
                        TrayTile(
                            tray, ui.weights[tray], ui.roles[tray], p, d, selected = edit && picked == cell, edit = edit,
                            modifier = cellMod
                        ) { if (edit) onPick(cell) else vm.onTrayClick(tray) }
                    } else if (edit) {
                        val shape = RoundedCornerShape(d.radius)
                        Box(
                            cellMod.clip(shape).border(2.dp, if (picked == cell) p.primary else p.outline, shape)
                                .clickable { onPick(cell) },
                            contentAlignment = Alignment.Center
                        ) { Text("빈 칸", color = p.sub, fontSize = 12.sp) }
                    } else {
                        Spacer(cellMod)
                    }
                }
            }
        }
    }
}

@Composable
private fun TrayTile(
    tray: Int, weight: Int, role: Role, p: Palette, d: Dim, selected: Boolean, edit: Boolean,
    modifier: Modifier, onClick: () -> Unit
) {
    val bg = when (role) {
        Role.COMBO -> p.accent
        Role.TOPUP -> p.topupBg
        else -> p.card
    }
    val fg = when (role) {
        Role.COMBO -> p.onAccent
        Role.TOPUP -> p.topupText
        else -> p.text
    }
    val subFg = when (role) {
        Role.COMBO -> p.onAccentSub
        Role.TOPUP -> p.topupText
        else -> p.sub
    }
    val shape = RoundedCornerShape(d.radius)
    val borderColor = when {
        selected -> p.primary
        role == Role.NONE -> p.outline
        else -> Color.Transparent
    }
    BoxWithConstraints(
        modifier.clip(shape).background(bg).border(if (selected) 3.dp else 1.dp, borderColor, shape).clickable { onClick() }
    ) {
        val big = minOf(maxWidth.value * 0.27f, maxHeight.value * 0.36f).coerceIn(13f, 46f)
        val small = (maxWidth.value * 0.12f).coerceIn(10f, 14f)
        val inner = if (d.compact) 8.dp else 14.dp
        Column(Modifier.fillMaxSize().padding(inner), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${tray + 1}번", color = subFg, fontSize = small.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                val tag = when (role) {
                    Role.COMBO -> "조합"
                    Role.TOPUP -> "보태기"
                    else -> ""
                }
                if (tag.isNotEmpty() && maxWidth > 70.dp) Text(tag, color = subFg, fontSize = small.sp, maxLines = 1)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                val text = if (weight > 0) fmt(weight) else if (weight == -1) "ERR" else "0"
                val color = when {
                    weight == -1 -> if (role == Role.NONE) p.err else fg
                    weight > 0 -> fg
                    else -> subFg
                }
                Text(text, color = color, fontSize = big.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Clip)
                if (weight >= 0 && maxWidth > 95.dp) Text(" g", color = subFg, fontSize = small.sp, modifier = Modifier.padding(bottom = 3.dp))
            }
            val frac = if (weight > 0) (weight / 1000f).coerceIn(0f, 1f) else 0f
            Box(
                Modifier.fillMaxWidth().height(if (d.compact) 4.dp else 7.dp).clip(RoundedCornerShape(99.dp))
                    .background(if (role == Role.COMBO) Color.White.copy(alpha = 0.3f) else p.track)
            ) {
                Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(if (role == Role.COMBO) Color.White else p.bar))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 하단: 값 조절 / 동작 버튼
// ---------------------------------------------------------------------------
@Composable
private fun Steppers(vm: SorterViewModel, ui: UiState, p: Palette, d: Dim, modifier: Modifier, twoRows: Boolean = false) {
    val items = listOf<Triple<String, String, Pair<(Int) -> Unit, (Int) -> Unit>>>(
        Triple("목표 무게", fmt(ui.target), Pair({ m: Int -> vm.changeSetting("target", -10 * m) }, { m: Int -> vm.changeSetting("target", 10 * m) })),
        Triple("허용 오차", "${ui.tolerance}", Pair({ m: Int -> vm.changeSetting("tol", -5 * m) }, { m: Int -> vm.changeSetting("tol", 5 * m) })),
        Triple("최소 개수", "${ui.minComb}", Pair({ _: Int -> vm.changeSetting("min", -1) }, { _: Int -> vm.changeSetting("min", 1) })),
        Triple("최대 개수", "${ui.maxComb}", Pair({ _: Int -> vm.changeSetting("max", -1) }, { _: Int -> vm.changeSetting("max", 1) }))
    )
    if (twoRows) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(d.gap)) {
            for (r in 0 until 2) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(d.gap)) {
                    for (c in 0 until 2) {
                        val s = items[r * 2 + c]
                        Stepper(s.first, s.second, p, d, Modifier.weight(1f).fillMaxHeight(), s.third.first, s.third.second)
                    }
                }
            }
        }
    } else {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(d.gap)) {
            for (s in items) Stepper(s.first, s.second, p, d, Modifier.weight(1f).fillMaxHeight(), s.third.first, s.third.second)
        }
    }
}

@Composable
private fun Stepper(
    label: String, value: String, p: Palette, d: Dim, modifier: Modifier,
    onMinus: (Int) -> Unit, onPlus: (Int) -> Unit
) {
    val shape = RoundedCornerShape(if (d.compact) 14.dp else 20.dp)
    Row(
        modifier.clip(shape).background(p.card).border(1.dp, p.outline, shape).padding(start = if (d.compact) 8.dp else 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(label, color = p.sub, fontSize = if (d.compact) 10.sp else 12.sp, maxLines = 1)
            Text(value, color = p.text, fontSize = d.stepValue, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        HoldButton("−", p, d, onMinus)
        Spacer(Modifier.width(4.dp))
        HoldButton("+", p, d, onPlus)
    }
}

/** 누르면 1단계, 0.4초 이상 누르고 있으면 0.1초마다 5단계씩 반복. */
@Composable
private fun HoldButton(label: String, p: Palette, d: Dim, onStep: (Int) -> Unit) {
    val scope = rememberCoroutineScope()
    val current by rememberUpdatedState(onStep)
    val s = if (d.compact) 34.dp else 42.dp
    Box(
        Modifier.size(s).clip(RoundedCornerShape(12.dp)).background(p.btn)
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
private fun Actions(vm: SorterViewModel, ui: UiState, p: Palette, d: Dim, modifier: Modifier, onPreset: () -> Unit) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(d.gap)) {
        ActionButton("영점", p, d, Modifier.weight(1f).fillMaxHeight()) { vm.tare() }
        ActionButton("보태기", p, d, Modifier.weight(1f).fillMaxHeight(), active = ui.topup) { vm.toggleTopup() }
        ActionButton("프리셋", p, d, Modifier.weight(1f).fillMaxHeight(), primary = true) { onPreset() }
    }
}

@Composable
private fun ActionButton(
    label: String, p: Palette, d: Dim, modifier: Modifier,
    active: Boolean = false, primary: Boolean = false, onClick: () -> Unit
) {
    val shape = RoundedCornerShape(if (d.compact) 14.dp else 20.dp)
    val on = active || primary
    Box(
        modifier.clip(shape).background(if (on) p.primary else p.card)
            .border(1.dp, if (on) p.primary else p.outline, shape).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (on) p.onPrimary else p.text,
            fontSize = if (d.compact) 14.sp else 16.sp, fontWeight = FontWeight.ExtraBold)
    }
}

// ---------------------------------------------------------------------------
// 설정(화면·배열 / 프리셋 / 연결·정보) / 기기 선택
// ---------------------------------------------------------------------------
@Composable
fun SmallButton(label: String, p: Palette, bg: Color, fg: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier.height(38.dp).clip(shape).background(bg).border(1.dp, p.outline, shape)
            .clickable { onClick() }.padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, p: Palette, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.height(40.dp).clip(shape).background(if (selected) p.primary else p.btn)
            .clickable { onClick() }.padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) p.onPrimary else p.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun NumRow(label: String, value: String, p: Palette, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, color = p.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        SmallButton("−", p, p.btn, p.text, onMinus)
        Text(value, color = p.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(48.dp))
        SmallButton("+", p, p.btn, p.text, onPlus)
    }
}

@Composable
private fun Section(title: String, p: Palette, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = p.sub, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
fun SettingsDialog(
    vm: SorterViewModel, ui: UiState, p: Palette, tab: Int, onTab: (Int) -> Unit, onClose: () -> Unit,
    onDevices: () -> Unit, onEditCells: () -> Unit
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.94f).clip(RoundedCornerShape(20.dp))
                .background(p.bg).border(1.dp, p.outline, RoundedCornerShape(20.dp)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("설정", color = p.text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                Choice("화면·배열", tab == 0, p) { onTab(0) }
                Choice("프리셋", tab == 1, p) { onTab(1) }
                Choice("연결·정보", tab == 2, p) { onTab(2) }
                SmallButton("닫기", p, p.primary, p.onPrimary, onClose)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    0 -> LayoutTab(vm, ui, p, onEditCells)
                    1 -> PresetTab(vm, ui, p, onClose)
                    else -> InfoTab(vm, ui, p, onDevices)
                }
            }
        }
    }
}

@Composable
private fun LayoutTab(vm: SorterViewModel, ui: UiState, p: Palette, onEditCells: () -> Unit) {
    val lay = ui.layout
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Section("화면 방향", p) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice("가로", lay.orientation == "landscape", p) { vm.setOrientation("landscape") }
                Choice("세로", lay.orientation == "portrait", p) { vm.setOrientation("portrait") }
                Choice("자동 회전", lay.orientation == "auto", p) { vm.setOrientation("auto") }
            }
        }
        Section("테마", p) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (t in THEMES) {
                    val shape = RoundedCornerShape(14.dp)
                    Column(
                        Modifier.width(112.dp).clip(shape).background(t.bg)
                            .border(if (t.id == lay.theme) 3.dp else 1.dp, if (t.id == lay.theme) p.primary else p.outline, shape)
                            .clickable { vm.setTheme(t.id) }.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.weight(1f).height(26.dp).clip(RoundedCornerShape(6.dp)).background(t.hero))
                            Box(Modifier.weight(1f).height(26.dp).clip(RoundedCornerShape(6.dp)).background(t.accent))
                            Box(Modifier.weight(1f).height(26.dp).clip(RoundedCornerShape(6.dp)).background(t.card).border(1.dp, t.outline, RoundedCornerShape(6.dp)))
                        }
                        Text(t.label, color = t.text, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
        Section("저울 배열 (DIY: 저울을 늘리거나 줄이면 여기서 맞추세요)", p) {
            NumRow("저울 개수", "${lay.channels}", p,
                onMinus = { vm.setChannels(lay.channels - 1) }, onPlus = { vm.setChannels(lay.channels + 1) })
            NumRow("한 줄에 놓을 저울 수", "${lay.cols}", p,
                onMinus = { vm.setCols(lay.cols - 1) }, onPlus = { vm.setCols(lay.cols + 1) })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("번호 순서", color = p.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Choice("아랫줄부터", lay.bottomFirst, p) { vm.setBottomFirst(true) }
                Choice("윗줄부터", !lay.bottomFirst, p) { vm.setBottomFirst(false) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton("화면에서 자리 바꾸기", p, p.primary, p.onPrimary, onEditCells)
                SmallButton("자리 초기화", p, p.btn, p.text) { vm.resetCells() }
            }
            Text("‘아랫줄부터’는 1번이 맨 아래 줄 왼쪽에서 시작합니다(현재 하드웨어: 아랫줄 1~6, 윗줄 7~12). 개수나 줄 수를 바꾸면 자리가 기본 배열로 초기화됩니다. 펌웨어가 보내는 저울 수와 같아야 합니다.",
                color = p.sub, fontSize = 12.sp)
        }
        Section("보태기 모드에서 박스를 올리는 저울 (눌러서 선택)", p) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (t in 0 until lay.channels) {
                    Choice("${t + 1}", t in lay.topup, p) { vm.toggleTopupTray(t) }
                }
            }
        }
    }
}

@Composable
private fun PresetTab(vm: SorterViewModel, ui: UiState, p: Palette, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("짧게 누르면 불러오기, 2초 길게 누르면 지금 설정이 저장됩니다.", color = p.sub, fontSize = 13.sp, modifier = Modifier.weight(1f))
            SmallButton("전부 비우기", p, p.err, Color.White) { vm.clearPresets() }
        }
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
    }
}

@Composable
private fun InfoTab(vm: SorterViewModel, ui: UiState, p: Palette, onDevices: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Section("연결", p) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton("블루투스 연결", p, p.primary, p.onPrimary, onDevices)
                SmallButton(if (ui.demo) "테스트모드: 켜짐" else "테스트모드: 꺼짐", p,
                    if (ui.demo) Color(0xFFB45309) else p.btn, if (ui.demo) Color.White else p.text) { vm.toggleDemo() }
            }
        }
        Section("조합", p) {
            SmallButton(if (ui.lockEnabled) "조합잠금: 켜짐" else "조합잠금: 꺼짐", p,
                if (ui.lockEnabled) p.ok else p.btn, if (ui.lockEnabled) Color.White else p.text) { vm.toggleLock() }
        }
        Section("앱 · 펌웨어", p) {
            Text(
                "앱 v${BuildConfig.VERSION_NAME}" +
                    (if (ui.firmware != null) " · 펌웨어 ${ui.firmware}" else " · 펌웨어 응답 없음") +
                    (if (ui.lastTx.isNotEmpty()) " · 마지막 명령 ${ui.lastTx}" else "") +
                    (if (ui.updateStatus.isNotEmpty()) "\n${ui.updateStatus}" else ""),
                color = p.text, fontSize = 13.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (ui.updateReadyTag != null) SmallButton("지금 업데이트", p, p.primary, p.onPrimary) { vm.installUpdate() }
                SmallButton(
                    if (ui.relaunchOk) "자동 재실행: 켜짐" else "자동 재실행: 허용하기", p,
                    if (ui.relaunchOk) p.btn else p.primary, if (ui.relaunchOk) p.text else p.onPrimary
                ) { if (!ui.relaunchOk) vm.openOverlaySettings() }
                SmallButton("업데이트 확인", p, p.btn, p.text) { vm.checkUpdate(manual = true) }
            }
        }
    }
}

@Composable
private fun PresetSlot(
    index: Int, preset: Preset?, selected: Boolean, p: Palette, modifier: Modifier,
    onLoad: () -> Unit, onSave: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val load by rememberUpdatedState(onLoad)
    val save by rememberUpdatedState(onSave)
    var savedFlash by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    val text = when {
        savedFlash -> "슬롯 ${SLOT_NAMES[index]}\n저장됨!"
        preset == null -> "슬롯 ${SLOT_NAMES[index]}\n(비어있음)"
        else -> "슬롯 ${SLOT_NAMES[index]}\n${fmt(preset.target)}g ±${preset.tolerance}\n(${preset.minComb}~${preset.maxComb}개)"
    }
    Box(
        modifier.clip(shape)
            .background(if (savedFlash) p.ok else p.card)
            .border(if (selected) 3.dp else 1.dp, if (selected) p.primary else p.outline, shape)
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
        Text(text, color = if (savedFlash) Color.White else p.text, fontSize = 15.sp,
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
            Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.92f).clip(RoundedCornerShape(20.dp))
                .background(p.bg).border(1.dp, p.outline, RoundedCornerShape(20.dp)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("블루투스 연결", color = p.text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text("먼저 폰 설정 > 블루투스에서 저울 모듈(HC-05/HC-06, 비밀번호 1234 또는 0000)을 페어링하세요.",
                color = p.sub, fontSize = 13.sp)
            if (!ui.btPermission) {
                Text("블루투스 권한이 필요합니다.", color = p.err, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                SmallButton("권한 허용", p, p.primary, p.onPrimary) { onRequestPermission() }
            } else if (devices.isEmpty()) {
                Text("페어링된 기기가 없습니다.", color = p.text, fontSize = 14.sp)
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(devices) { d ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.card)
                                .border(1.dp, p.outline, RoundedCornerShape(12.dp))
                                .clickable { vm.connectTo(d.address); onClose() }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(d.name, color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f))
                            Text(d.address, color = p.sub, fontSize = 12.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.weight(if (devices.isEmpty() || !ui.btPermission) 1f else 0.0001f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton("블루투스 설정 열기", p, p.btn, p.text) { onOpenBtSettings() }
                SmallButton("목록 새로고침", p, p.btn, p.text) { devices = vm.bondedDevices() }
                if (ui.link != LinkState.DISCONNECTED) {
                    SmallButton("연결 끊기", p, p.err, Color.White) { vm.disconnectLink(); onClose() }
                }
                Spacer(Modifier.width(1.dp).weight(1f))
                SmallButton("닫기", p, p.primary, p.onPrimary, onClose)
            }
        }
    }
}
