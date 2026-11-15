package com.uade.vigia.ui

import androidx.compose.ui.graphics.Color
import com.uade.vigia.domain.model.Checkpoint
import com.uade.vigia.domain.model.CommandStatus
import com.uade.vigia.domain.model.EventTone
import com.uade.vigia.domain.model.LedColor
import com.uade.vigia.domain.model.MissionStatus
import com.uade.vigia.domain.model.RobotState
import com.uade.vigia.domain.model.Telemetry
import com.uade.vigia.domain.model.Thresholds
import com.uade.vigia.domain.model.VisitorState
import com.uade.vigia.ui.components.Tone
import com.uade.vigia.ui.theme.Muted
import com.uade.vigia.ui.theme.Navy
import com.uade.vigia.ui.theme.OkFg
import com.uade.vigia.ui.theme.Warning
import com.uade.vigia.ui.theme.ErrFg
import com.uade.vigia.ui.theme.InfoFg
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val zone: ZoneId get() = ZoneId.systemDefault()
private val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss")
private val hourFmt = DateTimeFormatter.ofPattern("HH:mm")
private val dayFmt = DateTimeFormatter.ofPattern("dd/MM HH:mm")

/** Número con coma decimal, como en el prototipo: 1,4 · 0,5. */
fun Float.es(decimals: Int = 1): String = String.format(Locale.US, "%.${decimals}f", this).replace('.', ',')
fun Double.es(decimals: Int = 1): String = toFloat().es(decimals)

fun Instant.clock(): String = timeFmt.format(atZone(zone))

fun Instant.dayAndTime(): String {
    val local = atZone(zone)
    return if (local.toLocalDate() == LocalDate.now(zone)) "Hoy ${hourFmt.format(local)}" else dayFmt.format(local)
}

fun formatDuration(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "$m min ${"%02d".format(s)} s" else "$s s"
}

fun CommandStatus.tone(): Tone = when (this) {
    CommandStatus.PENDING -> Tone.INFO
    CommandStatus.CONFIRMED -> Tone.OK
    CommandStatus.REJECTED -> Tone.ERROR
    CommandStatus.NO_RESPONSE -> Tone.NEUTRAL
}

fun MissionStatus.tone(): Tone = when (this) {
    MissionStatus.RUNNING -> Tone.INFO
    MissionStatus.PAUSED -> Tone.WARNING
    MissionStatus.COMPLETED -> Tone.OK
    MissionStatus.CANCELLED -> Tone.NEUTRAL
    MissionStatus.ABORTED -> Tone.ERROR
}

fun RobotState.tone(): Tone = when (this) {
    RobotState.IN_BASE -> Tone.NEUTRAL
    RobotState.ON_MISSION, RobotState.RETURNING -> Tone.INFO
    RobotState.AT_DESTINATION -> Tone.OK
    RobotState.WAITING_CROSSING, RobotState.PAUSED, RobotState.GOING_SAFE, RobotState.AT_SAFE -> Tone.WARNING
    RobotState.STOPPED -> Tone.ERROR
}

fun LedColor.tone(): Tone = when (this) {
    LedColor.GREEN -> Tone.OK
    LedColor.BLUE -> Tone.INFO
    LedColor.AMBER -> Tone.WARNING
    LedColor.RED -> Tone.ERROR
}

fun EventTone.color(): Color = when (this) {
    EventTone.NEUTRAL -> Muted
    EventTone.INFO -> Navy
    EventTone.OK -> OkFg
    EventTone.WARNING -> Warning
    EventTone.ERROR -> ErrFg
}

fun batteryTone(soc: Double): Tone = when {
    soc < Thresholds.BATTERY_CRITICAL -> Tone.ERROR
    soc < Thresholds.BATTERY_WARNING -> Tone.WARNING
    else -> Tone.OK
}

fun VisitorState.color(): Color = when (this) {
    VisitorState.WITH_ROBOT, VisitorState.DELIVERED -> OkFg
    VisitorState.LAGGING -> Warning
    VisitorState.LOST -> ErrFg
    VisitorState.NONE -> Muted
}

/** "Camino a P3 · pasillo peatonal · 0,5 m/s" */
fun Telemetry.movementSummary(): String {
    val next = Checkpoint.route.getOrNull(Checkpoint.route.indexOf(checkpoint) + 1)
    return when (robotState) {
        RobotState.ON_MISSION ->
            if (isMoving && next != null) "Camino a ${next.label} · ${next.place.lowercase()} · ${speedMps.es()} m/s"
            else "En ${checkpoint.label} · ${busyAction?.label?.lowercase() ?: "dando las normas de seguridad"}"
        RobotState.WAITING_CROSSING -> {
            val wait = mission?.crossingWaitSince?.let { java.time.Duration.between(it, Instant.now()).seconds } ?: 0
            "Detenido en P3 · antes del cruce · espera de $wait s"
        }
        RobotState.PAUSED -> "En pausa en ${checkpoint.label} · quieto"
        RobotState.STOPPED -> "Detenido en ${checkpoint.label} · LED rojo"
        RobotState.AT_DESTINATION -> "En P4 · ${checkpoint.place.lowercase()}"
        RobotState.RETURNING -> "Volviendo a la base · ${speedMps.es()} m/s"
        RobotState.GOING_SAFE -> "Yendo al punto seguro · ${speedMps.es()} m/s"
        RobotState.AT_SAFE -> "En el punto seguro · esperando una orden"
        RobotState.IN_BASE -> "En la base · cargando"
    }
}
