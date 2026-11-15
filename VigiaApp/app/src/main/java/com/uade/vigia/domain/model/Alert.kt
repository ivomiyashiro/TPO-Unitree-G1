package com.uade.vigia.domain.model

import java.time.Instant

enum class AlertSeverity(val label: String) {
    INFO("Aviso"),
    WARNING("Advertencia"),
    CRITICAL("Crítica")
}

enum class AlertType {
    CROSSING_WAIT,
    BATTERY_LOW,
    BATTERY_CRITICAL,
    VISITOR_LAGGING,
    TILT,
    COMMAND_REJECTED
}

data class Alert(
    val id: String,
    val type: AlertType,
    val severity: AlertSeverity,
    val title: String,
    val detail: String,
    val raisedAt: Instant,
    val checkpoint: Checkpoint,
    val missionId: String? = null,
    val acknowledgedAt: Instant? = null,
    val acknowledgedBy: String? = null
)

/** Umbrales del documento (sección 7). Son decisiones del grupo para la simulación. */
object Thresholds {
    const val BATTERY_WARNING = 25.0
    const val BATTERY_CRITICAL = 10.0
    const val BATTERY_MIN_TO_START = 30.0
    const val TILT_CRITICAL_DEG = 15f
    const val VISITOR_LAGGING_M = 3f
    const val VISITOR_LOST_M = 8f
    const val STALE_AFTER_S = 3L
    const val OFFLINE_AFTER_S = 10L
    const val COMMAND_TIMEOUT_MS = 5_000L
    const val ESCORT_SPEED_MPS = 0.5f
    const val OPERATOR = "guardia-turno-1"
}

/** Normas de seguridad que el robot dice en P1 (Propia 2: normas entregadas). */
object SafetyMessages {
    val all = listOf(
        "Use el chaleco reflectivo en todo momento.",
        "Camine siempre por el pasillo peatonal señalizado.",
        "No ingrese a la zona de racks: es de acceso restringido.",
        "Los autoelevadores tienen prioridad: espere en el cruce."
    )
}
