package com.uade.vigia.domain.usecase

import com.uade.vigia.domain.model.AlertSeverity
import com.uade.vigia.domain.model.Checkpoint
import com.uade.vigia.domain.model.CommandType
import com.uade.vigia.domain.model.MissionStatus
import com.uade.vigia.domain.model.RobotState
import com.uade.vigia.domain.model.Telemetry
import com.uade.vigia.domain.model.Thresholds

data class Availability(val enabled: Boolean, val condition: String)

/**
 * Condiciones de habilitación de cada orden (Tablas XI y XII).
 * [condition] es el texto que ve el guardia al lado del botón: explica cuándo se habilita.
 */
object CommandRules {

    fun condition(type: CommandType): String = when (type) {
        CommandType.START_ESCORT -> "Robot en la base, en línea, con 30 % de batería o más y sin alertas críticas"
        CommandType.PAUSE -> "Escolta en curso"
        CommandType.RESUME -> "Solo con la escolta en pausa o detenida"
        CommandType.CANCEL -> "Pide confirmación"
        CommandType.GO_SAFE -> "Escolta en curso, en pausa o detenida"
        CommandType.STOP -> "Robot caminando, esperando en el cruce o con un gesto"
        CommandType.CONFIRM_CROSSING -> "Robot esperando antes del cruce"
        CommandType.RETURN_TO_BASE -> "Robot quieto en destino o en el punto seguro"
        CommandType.ACK_ALERT -> "Hay una alerta sin reconocer"
        CommandType.GREET -> "Robot quieto y libre"
        CommandType.HANDSHAKE -> "Robot quieto en P4 y libre"
        CommandType.RELEASE_ARMS -> "Hay un gesto en curso"
        CommandType.SPEAK, CommandType.SET_LED, CommandType.SET_VOLUME -> "En línea"
    }

    fun availability(type: CommandType, t: Telemetry, online: Boolean): Availability {
        if (!online) return Availability(false, "Sin conexión con el robot")
        val mission = t.mission
        val inProgress = mission != null && !mission.status.isFinished
        val enabled = when (type) {
            CommandType.START_ESCORT ->
                !inProgress && t.robotState == RobotState.IN_BASE &&
                    t.soc >= Thresholds.BATTERY_MIN_TO_START &&
                    t.activeAlerts.none { it.severity == AlertSeverity.CRITICAL }
            CommandType.PAUSE ->
                mission?.status == MissionStatus.RUNNING &&
                    (t.robotState == RobotState.ON_MISSION || t.robotState == RobotState.WAITING_CROSSING)
            CommandType.RESUME ->
                inProgress && (t.robotState == RobotState.PAUSED || t.robotState == RobotState.STOPPED) &&
                    t.soc > Thresholds.BATTERY_CRITICAL
            CommandType.CANCEL ->
                inProgress && t.robotState in setOf(
                    RobotState.ON_MISSION, RobotState.WAITING_CROSSING, RobotState.PAUSED, RobotState.STOPPED
                )
            CommandType.GO_SAFE ->
                inProgress && t.robotState in setOf(
                    RobotState.ON_MISSION, RobotState.WAITING_CROSSING, RobotState.PAUSED, RobotState.STOPPED
                )
            CommandType.STOP -> t.robotState.isWalking || t.busyAction != null ||
                t.robotState == RobotState.WAITING_CROSSING || t.robotState == RobotState.AT_DESTINATION
            CommandType.CONFIRM_CROSSING -> t.robotState == RobotState.WAITING_CROSSING
            CommandType.RETURN_TO_BASE ->
                t.robotState == RobotState.AT_DESTINATION || t.robotState == RobotState.AT_SAFE ||
                    (t.robotState == RobotState.STOPPED && !inProgress)
            CommandType.ACK_ALERT -> t.activeAlerts.isNotEmpty()
            CommandType.GREET -> !t.isMoving && t.busyAction == null
            CommandType.HANDSHAKE -> !t.isMoving && t.busyAction == null && t.checkpoint == Checkpoint.P4
            CommandType.RELEASE_ARMS -> t.busyAction != null
            CommandType.SPEAK, CommandType.SET_LED, CommandType.SET_VOLUME -> true
        }
        return Availability(enabled, disabledReason(type, t, inProgress).takeUnless { enabled } ?: condition(type))
    }

    private fun disabledReason(type: CommandType, t: Telemetry, inProgress: Boolean): String = when (type) {
        CommandType.START_ESCORT -> when {
            inProgress -> "Ya hay una escolta en curso"
            t.robotState != RobotState.IN_BASE -> "El robot no está en la base"
            t.soc < Thresholds.BATTERY_MIN_TO_START -> "Batería menor al 30 %"
            else -> "Hay alertas críticas sin reconocer"
        }
        CommandType.PAUSE -> if (t.robotState == RobotState.PAUSED) "La escolta ya está en pausa" else "Escolta en curso"
        CommandType.ACK_ALERT -> "No hay alertas sin reconocer"
        else -> condition(type)
    }
}

/** Antigüedad del dato (Principio: cada dato muestra su antigüedad). */
enum class Freshness { FRESH, STALE, OFFLINE }

object DataFreshness {
    fun classify(ageSeconds: Long): Freshness = when {
        ageSeconds > Thresholds.OFFLINE_AFTER_S -> Freshness.OFFLINE
        ageSeconds > Thresholds.STALE_AFTER_S -> Freshness.STALE
        else -> Freshness.FRESH
    }
}
