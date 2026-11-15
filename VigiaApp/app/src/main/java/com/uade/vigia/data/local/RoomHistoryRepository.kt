package com.uade.vigia.data.local

import com.uade.vigia.domain.model.CommandRecord
import com.uade.vigia.domain.model.CommandStatus
import com.uade.vigia.domain.model.EventKind
import com.uade.vigia.domain.model.EventTone
import com.uade.vigia.domain.model.HistoryEvent
import com.uade.vigia.domain.model.MissionStatus
import com.uade.vigia.domain.model.MissionSummary
import com.uade.vigia.domain.model.Thresholds
import com.uade.vigia.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

class RoomHistoryRepository(private val dao: MissionDao) : HistoryRepository {

    override fun missions(): Flow<List<MissionSummary>> = dao.missions().map { rows ->
        rows.map { r ->
            MissionSummary(
                id = r.id,
                visitor = r.visitor,
                destination = r.destination,
                status = MissionStatus.valueOf(r.status),
                startedAt = Instant.ofEpochMilli(r.startedAt),
                endedAt = r.endedAt?.let(Instant::ofEpochMilli),
                distanceM = r.distanceM,
                alertCount = r.alertCount,
                commandCount = r.commandCount,
                rejectedCommandCount = r.rejectedCount
            )
        }
    }

    override fun events(missionId: String): Flow<List<HistoryEvent>> = dao.events(missionId).map { list ->
        list.map {
            HistoryEvent(it.id, it.missionId, Instant.ofEpochMilli(it.at), EventKind.valueOf(it.kind), it.text, EventTone.valueOf(it.tone))
        }
    }

    /** Identificador correlativo de escolta: E-0001, E-0002… */
    override suspend fun nextMissionId(): String {
        val max = dao.missionIds().mapNotNull { it.removePrefix("E-").toIntOrNull() }.maxOrNull() ?: 0
        return "E-%04d".format(max + 1)
    }

    override suspend fun recordMissionStarted(id: String, visitor: String, destination: String, at: Instant) {
        dao.insertMission(MissionEntity(id, visitor, destination, MissionStatus.RUNNING.name, at.toEpochMilli()))
    }

    override suspend fun recordMissionFinished(id: String, status: MissionStatus, distanceM: Float, at: Instant) {
        dao.finishMission(id, status.name, at.toEpochMilli(), distanceM)
    }

    override suspend fun log(missionId: String?, at: Instant, kind: EventKind, text: String, tone: EventTone) {
        dao.insertEvent(EventEntity(missionId = missionId, at = at.toEpochMilli(), kind = kind.name, text = text, tone = tone.name))
    }

    override suspend fun logCommand(record: CommandRecord) {
        val status = record.status.label.lowercase()
        val detail = when (record.status) {
            CommandStatus.REJECTED -> " · ${record.reason ?: "sin motivo"} (código ${record.rpcCode})"
            CommandStatus.NO_RESPONSE -> " · no se reenvía sola"
            else -> ""
        }
        val tone = when (record.status) {
            CommandStatus.CONFIRMED -> EventTone.INFO
            CommandStatus.REJECTED -> EventTone.ERROR
            CommandStatus.NO_RESPONSE -> EventTone.WARNING
            CommandStatus.PENDING -> EventTone.NEUTRAL
        }
        dao.insertEvent(
            EventEntity(
                missionId = record.missionId,
                at = record.sentAt.toEpochMilli(),
                kind = EventKind.COMMAND.name,
                text = "${record.command.description} · $status por ${Thresholds.OPERATOR}$detail",
                tone = tone.name,
                rejected = record.status == CommandStatus.REJECTED || record.status == CommandStatus.NO_RESPONSE
            )
        )
    }
}
