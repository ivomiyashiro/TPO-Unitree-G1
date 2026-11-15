package com.uade.vigia.domain.usecase

import com.uade.vigia.domain.model.CommandRecord
import com.uade.vigia.domain.model.CommandResponse
import com.uade.vigia.domain.model.CommandStatus
import com.uade.vigia.domain.model.EventKind
import com.uade.vigia.domain.model.EventTone
import com.uade.vigia.domain.model.RobotCommand
import com.uade.vigia.domain.model.RobotEvent
import com.uade.vigia.domain.model.Thresholds
import com.uade.vigia.domain.repository.HistoryRepository
import com.uade.vigia.domain.repository.RobotRepository
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.util.UUID

/**
 * Envía una orden y espera la respuesta como máximo 5 s.
 * Si no llega, la orden queda "Sin respuesta" y no se reenvía sola (Riesgo 4).
 * Cada orden queda registrada en el historial con su estado.
 */
class SendCommandUseCase(
    private val robot: RobotRepository,
    private val history: HistoryRepository,
    private val timeoutMs: Long = Thresholds.COMMAND_TIMEOUT_MS,
    private val clock: () -> Instant = Instant::now
) {
    suspend operator fun invoke(command: RobotCommand, missionId: String?): CommandRecord {
        val sentAt = clock()
        val response = withTimeoutOrNull(timeoutMs) { robot.send(command) }
            ?: CommandResponse(CommandStatus.NO_RESPONSE)
        val record = CommandRecord(
            id = UUID.randomUUID().toString(),
            command = command,
            status = response.status,
            sentAt = sentAt,
            respondedAt = if (response.status == CommandStatus.NO_RESPONSE) null else clock(),
            reason = response.reason,
            rpcCode = response.rpcCode,
            missionId = missionId ?: command.params[RobotCommand.PARAM_MISSION_ID]
        )
        history.logCommand(record)
        return record
    }
}

/** Guarda en el historial los eventos que informa el robot durante la escolta. */
class RecordRobotEventsUseCase(
    private val robot: RobotRepository,
    private val history: HistoryRepository
) {
    suspend operator fun invoke() {
        robot.events.collect { event ->
            when (event) {
                is RobotEvent.MissionStarted ->
                    history.recordMissionStarted(event.missionId, event.visitor, event.destination, event.at)
                is RobotEvent.Milestone ->
                    history.log(event.missionId, event.at, EventKind.MISSION, event.text, event.tone)
                is RobotEvent.AlertRaised ->
                    history.log(
                        event.missionId, event.at, EventKind.ALERT,
                        "Alerta: ${event.alert.title}",
                        if (event.alert.severity.name == "CRITICAL") EventTone.ERROR else EventTone.WARNING
                    )
                is RobotEvent.MissionFinished ->
                    history.recordMissionFinished(event.missionId, event.status, event.distanceM, event.at)
            }
        }
    }
}
