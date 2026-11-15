package com.uade.vigia.domain.model

import java.time.Instant

enum class CommandType(val label: String, val wire: String) {
    // Órdenes de operación (Tabla XI)
    START_ESCORT("Iniciar escolta", "START_ESCORT"),
    PAUSE("Pausar", "PAUSE_ESCORT"),
    RESUME("Reanudar", "RESUME_ESCORT"),
    CANCEL("Cancelar escolta", "CANCEL_ESCORT"),
    GO_SAFE("Ir a punto seguro", "GO_SAFE"),
    STOP("Detener", "STOP_MOVE"),
    CONFIRM_CROSSING("Cruce libre", "CONFIRM_CROSSING"),
    RETURN_TO_BASE("Volver a la base", "RETURN_TO_BASE"),
    ACK_ALERT("Reconocer alerta", "ACK_ALERT"),

    // Órdenes de interacción (Tabla XII)
    GREET("Saludar", "GREETING"),
    HANDSHAKE("Dar la mano", "HANDSHAKE"),
    RELEASE_ARMS("Liberar brazos", "RELEASE_ARMS"),
    SPEAK("Hablar", "TTS"),
    SET_LED("Cambiar LED", "SET_LED"),
    SET_VOLUME("Cambiar volumen", "SET_VOLUME")
}

data class RobotCommand(
    val type: CommandType,
    val params: Map<String, String> = emptyMap()
) {
    /** Texto corto para la UI y el historial, por ejemplo "Hablar: Bienvenida". */
    val description: String
        get() = when (type) {
            CommandType.SPEAK -> "Mensaje: “${params[PARAM_LABEL] ?: params[PARAM_TEXT]}”"
            CommandType.SET_LED -> "LED ${params[PARAM_LED]?.let { LedColor.valueOf(it).label.lowercase() } ?: ""}"
            CommandType.SET_VOLUME -> "Volumen ${params[PARAM_VOLUME]} %"
            else -> type.label
        }

    companion object {
        const val PARAM_MISSION_ID = "missionId"
        const val PARAM_VISITOR = "visitor"
        const val PARAM_DESTINATION = "destination"
        const val PARAM_ALERT_ID = "alertId"
        const val PARAM_TEXT = "text"
        const val PARAM_LABEL = "label"
        const val PARAM_LED = "led"
        const val PARAM_VOLUME = "volume"
    }
}

/** Estados de una orden (Tabla XIII). */
enum class CommandStatus(val label: String) {
    PENDING("Pendiente"),
    CONFIRMED("Confirmada"),
    REJECTED("Rechazada"),
    NO_RESPONSE("Sin respuesta")
}

/** Respuesta del robot a una orden. "Sin respuesta" no viaja: la asigna la app. */
data class CommandResponse(
    val status: CommandStatus,
    val reason: String? = null,
    val rpcCode: Int = 0
) {
    companion object {
        fun confirmed() = CommandResponse(CommandStatus.CONFIRMED)
        fun rejected(reason: String, rpcCode: Int = 1) = CommandResponse(CommandStatus.REJECTED, reason, rpcCode)
    }
}

data class CommandRecord(
    val id: String,
    val command: RobotCommand,
    val status: CommandStatus,
    val sentAt: Instant,
    val respondedAt: Instant? = null,
    val reason: String? = null,
    val rpcCode: Int = 0,
    val missionId: String? = null
)
