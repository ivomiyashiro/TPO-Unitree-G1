package com.uade.vigia.ui.theme

import androidx.compose.ui.graphics.Color
import com.uade.vigia.domain.model.LedColor

// Tokens tomados del prototipo en Figma (Vigía · HMI G1).
val Navy = Color(0xFF1F4D78)
val NavyTint = Color(0xFFE0ECF7)
val Canvas = Color(0xFFF4F6F8)
val Surface = Color(0xFFFFFFFF)
val Border = Color(0xFFE2E8F0)
val Tile = Color(0xFFF8FAFC)
val Ink = Color(0xFF0F172A)
val Muted = Color(0xFF64748B)
val Faint = Color(0xFF94A3B8)
val Disabled = Color(0xFFE2E8F0)
val Danger = Color(0xFFB91C1C)
val Warning = Color(0xFFB45309)

val OkBg = Color(0xFFDCFCE7)
val OkFg = Color(0xFF15803D)
val InfoBg = Color(0xFFDBEAFE)
val InfoFg = Color(0xFF1D4ED8)
val WarnBg = Color(0xFFFEF3C7)
val WarnFg = Color(0xFFB45309)
val ErrBg = Color(0xFFFEE2E2)
val ErrFg = Color(0xFFB91C1C)
val NeutralBg = Color(0xFFF1F5F9)
val NeutralFg = Color(0xFF475569)

val WarnCard = Color(0xFFFFFBEB)
val WarnBorder = Color(0xFFFCD34D)
val ErrCard = Color(0xFFFEF2F2)
val ErrBorder = Color(0xFFF87171)
val OkCard = Color(0xFFF0FDF4)
val OkBorder = Color(0xFFBBF7D0)
val InfoBorder = Color(0xFF93C5FD)

// Plano del recorrido
val MapRacks = Color(0xFFEEF1F4)
val MapGarita = Color(0xFFE2E8F0)
val MapCorridor = Color(0xFFFEF3C7)
val MapCorridorText = Color(0xFF92400E)
val MapCrossing = Color(0xFFFEE2E2)
val MapOffice = Color(0xFFE0ECF7)

fun LedColor.toColor(): Color = when (this) {
    LedColor.GREEN -> Color(0xFF16A34A)
    LedColor.BLUE -> Color(0xFF2563EB)
    LedColor.AMBER -> Color(0xFFD97706)
    LedColor.RED -> Color(0xFFDC2626)
}
