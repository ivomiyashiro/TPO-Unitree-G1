package com.uade.vigia.domain

import com.uade.vigia.domain.model.CommandRecord
import com.uade.vigia.domain.model.CommandResponse
import com.uade.vigia.domain.model.CommandStatus
import com.uade.vigia.domain.model.CommandType
import com.uade.vigia.domain.model.EventKind
import com.uade.vigia.domain.model.EventTone
import com.uade.vigia.domain.model.HistoryEvent
import com.uade.vigia.domain.model.MissionStatus
import com.uade.vigia.domain.model.MissionSummary
import com.uade.vigia.domain.model.RobotCommand
import com.uade.vigia.domain.model.RobotEvent
import com.uade.vigia.domain.model.RobotInfo
import com.uade.vigia.domain.model.Telemetry
import com.uade.vigia.domain.repository.HistoryRepository
import com.uade.vigia.domain.repository.RobotRepository
import com.uade.vigia.domain.usecase.SendCommandUseCase
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class SendCommandUseCaseTest {

    private class FakeRobot(private val reply: suspend () -> CommandResponse) : RobotRepository {
        override val robots = emptyList<RobotInfo>()
        override val telemetry = MutableStateFlow(Telemetry())
        override val events = MutableSharedFlow<RobotEvent>()
        override suspend fun send(command: RobotCommand) = reply()
        override fun reconnect() = Unit
    }

    private class FakeHistory : HistoryRepository {
        val commands = mutableListOf<CommandRecord>()
        override fun missions(): Flow<List<MissionSummary>> = emptyFlow()
        override fun events(missionId: String): Flow<List<HistoryEvent>> = emptyFlow()
        override suspend fun nextMissionId() = "E-0001"
        override suspend fun recordMissionStarted(id: String, visitor: String, destination: String, at: Instant) = Unit
        override suspend fun recordMissionFinished(id: String, status: MissionStatus, distanceM: Float, at: Instant) = Unit
        override suspend fun log(missionId: String?, at: Instant, kind: EventKind, text: String, tone: EventTone) = Unit
        override suspend fun logCommand(record: CommandRecord) { commands += record }
    }

    @Test fun `una orden aceptada queda confirmada y registrada`() = runTest {
        val history = FakeHistory()
        val useCase = SendCommandUseCase(FakeRobot { CommandResponse.confirmed() }, history)
        val record = useCase(RobotCommand(CommandType.PAUSE), "E-0001")
        assertEquals(CommandStatus.CONFIRMED, record.status)
        assertEquals("E-0001", record.missionId)
        assertEquals(listOf(record), history.commands)
    }

    @Test fun `una orden rechazada conserva el motivo y el código`() = runTest {
        val useCase = SendCommandUseCase(FakeRobot { CommandResponse.rejected("Robot ocupado", 3203) }, FakeHistory())
        val record = useCase(RobotCommand(CommandType.GREET), null)
        assertEquals(CommandStatus.REJECTED, record.status)
        assertEquals("Robot ocupado", record.reason)
        assertEquals(3203, record.rpcCode)
    }

    @Test fun `sin respuesta en 5 s la orden queda sin respuesta y no se reenvía`() = runTest {
        var calls = 0
        val history = FakeHistory()
        val useCase = SendCommandUseCase(FakeRobot { calls++; awaitCancellation() }, history)
        val record = useCase(RobotCommand(CommandType.PAUSE), "E-0001")
        assertEquals(CommandStatus.NO_RESPONSE, record.status)
        assertNull(record.respondedAt)
        assertEquals(5_000L, currentTime)
        assertEquals(1, calls)
        assertEquals(1, history.commands.size)
    }
}
