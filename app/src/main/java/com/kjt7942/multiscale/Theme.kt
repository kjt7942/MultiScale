package com.kjt7942.multiscale

import androidx.compose.ui.graphics.Color

/** 화면 색 모음. 테마를 늘리려면 THEMES 에 한 줄 추가하면 된다. */
class Palette(
    val id: String, val label: String, val dark: Boolean,
    val bg: Color, val card: Color, val text: Color, val sub: Color, val outline: Color,
    val hero: Color, val heroText: Color, val heroSub: Color,
    val accent: Color, val onAccent: Color, val onAccentSub: Color,
    val topupBg: Color, val topupText: Color,
    val track: Color, val bar: Color,
    val btn: Color, val btnText: Color, val primary: Color, val onPrimary: Color,
    val err: Color, val ok: Color
)

val THEMES: List<Palette> = listOf(
    Palette(
        "grape", "포도 (밝게)", false,
        bg = Color(0xFFF5F2EC), card = Color(0xFFFFFFFF), text = Color(0xFF1C1426), sub = Color(0xFF7A6F8A), outline = Color(0xFFE3DDEC),
        hero = Color(0xFF3B1F6B), heroText = Color(0xFFFFFFFF), heroSub = Color(0xFFCBB8F0),
        accent = Color(0xFF58B01A), onAccent = Color(0xFFFFFFFF), onAccentSub = Color(0xFFE4F8CC),
        topupBg = Color(0xFFD9E6FF), topupText = Color(0xFF1E3A8A),
        track = Color(0xFFEEE9F4), bar = Color(0xFFB7A6DC),
        btn = Color(0xFFF0ECF6), btnText = Color(0xFF3B1F6B), primary = Color(0xFF3B1F6B), onPrimary = Color(0xFFFFFFFF),
        err = Color(0xFFDC2626), ok = Color(0xFF58B01A)
    ),
    Palette(
        "night", "밤 포도 (어둡게)", true,
        bg = Color(0xFF15111D), card = Color(0xFF1F1A2B), text = Color(0xFFEDE8F8), sub = Color(0xFF9A90B0), outline = Color(0xFF2A2338),
        hero = Color(0xFF2E1F52), heroText = Color(0xFFFFFFFF), heroSub = Color(0xFFB9A8E0),
        accent = Color(0xFF2B3D1D), onAccent = Color(0xFFE9FFC2), onAccentSub = Color(0xFFC9F77D),
        topupBg = Color(0xFF1E2F5C), topupText = Color(0xFFBFD4FF),
        track = Color(0xFF2A2338), bar = Color(0xFF6C5AA8),
        btn = Color(0xFF2A2338), btnText = Color(0xFFE5DFF3), primary = Color(0xFF8B5CF6), onPrimary = Color(0xFFFFFFFF),
        err = Color(0xFFF87171), ok = Color(0xFFB6F26B)
    ),
    Palette(
        "muscat", "청포도 (밝게)", false,
        bg = Color(0xFFF1F6E8), card = Color(0xFFFFFFFF), text = Color(0xFF15250B), sub = Color(0xFF65785A), outline = Color(0xFFDCE8CB),
        hero = Color(0xFF2F6B0F), heroText = Color(0xFFFFFFFF), heroSub = Color(0xFFCDEBB4),
        accent = Color(0xFF7DB82E), onAccent = Color(0xFF10250A), onAccentSub = Color(0xFF2E5410),
        topupBg = Color(0xFFFFF0C2), topupText = Color(0xFF7A4B00),
        track = Color(0xFFE7EFD9), bar = Color(0xFF9CC96B),
        btn = Color(0xFFE9F2DB), btnText = Color(0xFF2F6B0F), primary = Color(0xFF2F6B0F), onPrimary = Color(0xFFFFFFFF),
        err = Color(0xFFDC2626), ok = Color(0xFF3F8F12)
    ),
    Palette(
        "oled", "블랙 (OLED)", true,
        bg = Color(0xFF000000), card = Color(0xFF141414), text = Color(0xFFF2F0F7), sub = Color(0xFF8E8AA0), outline = Color(0xFF262626),
        hero = Color(0xFF1C1C24), heroText = Color(0xFFFFFFFF), heroSub = Color(0xFF9A96B0),
        accent = Color(0xFF7DE05C), onAccent = Color(0xFF102008), onAccentSub = Color(0xFF2E5410),
        topupBg = Color(0xFF16305F), topupText = Color(0xFFBFD4FF),
        track = Color(0xFF2A2A2A), bar = Color(0xFF6C5AA8),
        btn = Color(0xFF232323), btnText = Color(0xFFE5DFF3), primary = Color(0xFF7DE05C), onPrimary = Color(0xFF102008),
        err = Color(0xFFF87171), ok = Color(0xFF7DE05C)
    ),
    Palette(
        "contrast", "고대비 (작업장)", false,
        bg = Color(0xFFFFFFFF), card = Color(0xFFF1F1F1), text = Color(0xFF000000), sub = Color(0xFF444444), outline = Color(0xFF111111),
        hero = Color(0xFF111111), heroText = Color(0xFFFFFFFF), heroSub = Color(0xFFD0D0D0),
        accent = Color(0xFF00A650), onAccent = Color(0xFFFFFFFF), onAccentSub = Color(0xFFE0FFEE),
        topupBg = Color(0xFF0B57D0), topupText = Color(0xFFFFFFFF),
        track = Color(0xFFD9D9D9), bar = Color(0xFF555555),
        btn = Color(0xFFE2E2E2), btnText = Color(0xFF000000), primary = Color(0xFF111111), onPrimary = Color(0xFFFFFFFF),
        err = Color(0xFFD00000), ok = Color(0xFF00A650)
    )
)

fun paletteFor(id: String): Palette = THEMES.firstOrNull { it.id == id } ?: THEMES[0]
