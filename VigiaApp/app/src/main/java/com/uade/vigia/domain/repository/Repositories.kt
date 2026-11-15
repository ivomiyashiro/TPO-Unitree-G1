package com.uade.vigia.domain.repository

import com.uade.vigia.domain.model.CommandRecord
import com.uade.vigia.domain.model.CommandResponse
import com.uade.vigia.domain.model.EventKind
import com.uade.vigia.domain.model.EventTone
import com.uade.vigia.domain.model.HistoryEvent
import com.uade.vigia.domain.model.MissionStatus
import com.uade.vigia.domain.model.MissionSummary
import com.uade.vigia.domain.model.RobotCommand
import com.uade.vigia.domain.model.RobotEvent
import com.uade.vigia.domain.model.RobotInfo
import com.uade.vigia.domain.model.Telemetry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant

/**
 * Fuente de datos del robot. La UI no sabe si del otro lado hay un simulador o el emulador de la cátedra.
 * Mientras no hay conexión no llegan muestras nuevas: la antigüedad del dato la calcula quien consume.
 */
interface RobotRepository {
    val robots: List<RobotInfo>
    val telemetry: StateFlow<Telemetry>
    val events: SharedFlow<RobotEvent>

    /** Envía una orden. Puede no volver nunca (sin conexión): el tiempo de espera lo pone el caso de uso. */
    suspend fun send(command: RobotCommand): CommandResponse

    /** Pide reintentar la conexión con el robot. */
    fun reconnect()
}

/** Escenarios de prueba (sección 12). Solo los ofrece el simulador interno. */
interface ScenarioControls {
    fun lowBattery()
    fun visitorLagging()
    fun tilt()
    fun disconnect()
    fun failNextCommand()
}

/** Historial guardado en el dispositivo (Room). */
interface HistoryRepository {
    fun missions(): Flow<List<MissionSummary>>
    fun events(missionId: String): Flow<List<HistoryEvent>>
    suspend fun nextMissionId(): String
    suspend fun recordMissionStarted(id: String, visitor: String, destination: String, at: Instant)
    suspend fun recordMissionFinished(id: String, status: MissionStatus, distanceM: Float, at: Instant)
    suspend fun log(missionId: String?, at: Instant, kind: EventKind, text: String, tone: EventTone)
    suspend fun logCommand(record: CommandRecord)
}
