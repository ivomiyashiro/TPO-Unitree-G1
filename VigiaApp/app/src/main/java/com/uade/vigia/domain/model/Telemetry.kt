package com.uade.vigia.domain.model

import java.time.Instant

/** Robot que la app puede supervisar (pantalla de selección). */
data class RobotInfo(
    val id: String,
    val name: String,
    val site: String,
    val available: Boolean,
    val lastSeenLabel: String? = null
)

enum class LinkStatus(val label: String) {
    ONLINE("En línea"),
    DEGRADED("Degradada"),
    OFFLINE("Sin conexión")
}

/** Estados del robot (Figura 2 del documento). */
enum class RobotState(val label: String, val code: String) {
    IN_BASE("En base", "IDLE"),
    ON_MISSION("Escoltando", "ON_MISSION"),
    WAITING_CROSSING("Esperando en cruce", "WAITING"),
    PAUSED("En pausa", "PAUSED"),
    STOPPED("Detenido", "STOPPED"),
    AT_DESTINATION("En destino", "AT_DESTINATION"),
    RETURNING("Volviendo a la base", "RETURNING"),
    GOING_SAFE("Yendo a punto seguro", "GOING_SAFE"),
    AT_SAFE("En punto seguro", "AT_SAFE");

    val isWalking: Boolean get() = this == ON_MISSION || this == RETURNING || this == GOING_SAFE
}

/** Estado de la misión, informado aparte del estado del robot. */
enum class MissionStatus(val label: String) {
    RUNNING("En curso"),
    PAUSED("En pausa"),
    COMPLETED("Completada"),
    CANCELLED("Cancelada"),
    ABORTED("Abortada");

    val isFinished: Boolean get() = this == COMPLETED || this == CANCELLED || this == ABORTED
}

/** Puntos del recorrido (Tabla II). [routeMeters] es la distancia desde BASE por el pasillo. */
enum class Checkpoint(val label: String, val place: String, val routeMeters: Float) {
    BASE("BASE", "Estación de carga en el hall", 0f),
    P1("P1", "Hall", 4f),
    P2("P2", "Pasillo peatonal", 14f),
    P3("P3", "Antes del cruce de autoelevadores", 24f),
    P4("P4", "Oficina de recepción de mercadería", 46f),
    SAFE("SAFE", "Nicho junto a la garita", 0f);

    companion object {
        val route = listOf(BASE, P1, P2, P3, P4)
    }
}

enum class VisitorState(val label: String) {
    NONE("Sin visitante"),
    WITH_ROBOT("Junto"),
    LAGGING("Rezagado"),
    LOST("Perdido"),
    DELIVERED("Entregado")
}

/** Código de colores del LED (Tabla X). */
enum class LedColor(val label: String, val meaning: String, val hint: String) {
    GREEN("Verde", "disponible", "en la base"),
    BLUE("Azul", "escoltando", "seguir al robot"),
    AMBER("Ámbar", "esperando", "no avanzar"),
    RED("Rojo", "detenido", "mantener distancia")
}

enum class BusyAction(val label: String) {
    GREETING("Saludando"),
    HANDSHAKE("Dando la mano"),
    OTHER_GESTURE("Haciendo un gesto")
}

data class Mission(
    val id: String,
    val visitor: String,
    val destination: String,
    val status: MissionStatus,
    val checkpoint: Checkpoint,
    val progressPct: Int,
    val startedAt: Instant,
    val endedAt: Instant? = null,
    val distanceM: Float = 0f,
    val crossingWaitSince: Instant? = null
)

/** Posición sobre el plano, normalizada 0..1 (para dibujarla) y en metros (para mostrarla). */
data class Pose(val mapX: Float, val mapY: Float, val xM: Float, val yM: Float)

data class Telemetry(
    val timestamp: Instant = Instant.now(),
    val valid: Boolean = true,
    val link: LinkStatus = LinkStatus.ONLINE,
    val latencyMs: Int = 42,
    val soc: Double = 85.0,
    val soh: Int = 98,
    val robotState: RobotState = RobotState.IN_BASE,
    val busyAction: BusyAction? = null,
    val mission: Mission? = null,
    val checkpoint: Checkpoint = Checkpoint.BASE,
    val pose: Pose = Pose(0f, 0f, 0f, 0f),
    val speedMps: Float = 0f,
    val rollDeg: Float = 0f,
    val pitchDeg: Float = 0f,
    val visitorState: VisitorState = VisitorState.NONE,
    val visitorDistanceM: Float = 0f,
    val safetyGiven: Int = 0,
    val safetyPlanned: Int = 4,
    val led: LedColor = LedColor.GREEN,
    val volumePct: Int = 60,
    val lastTts: String? = null,
    val alerts: List<Alert> = emptyList()
) {
    val batteryPct: Int get() = soc.toInt()
    val minutesLeft: Int get() = (soc * 1.2).toInt()
    val tiltDeg: Float get() = maxOf(kotlin.math.abs(rollDeg), kotlin.math.abs(pitchDeg))
    val isMoving: Boolean get() = speedMps > 0f
    val activeAlerts: List<Alert> get() = alerts.filter { it.acknowledgedAt == null }
    val acknowledgedAlerts: List<Alert> get() = alerts.filter { it.acknowledgedAt != null }
    val hasMissionInProgress: Boolean get() = mission?.status?.isFinished == false
}
