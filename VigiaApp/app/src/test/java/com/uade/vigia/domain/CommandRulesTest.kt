package com.uade.vigia.domain

import com.uade.vigia.domain.model.Alert
import com.uade.vigia.domain.model.AlertSeverity
import com.uade.vigia.domain.model.AlertType
import com.uade.vigia.domain.model.BusyAction
import com.uade.vigia.domain.model.Checkpoint
import com.uade.vigia.domain.model.CommandType
import com.uade.vigia.domain.model.Mission
import com.uade.vigia.domain.model.MissionStatus
import com.uade.vigia.domain.model.RobotState
import com.uade.vigia.domain.model.Telemetry
import com.uade.vigia.domain.usecase.CommandRules
import com.uade.vigia.domain.usecase.DataFreshness
import com.uade.vigia.domain.usecase.Freshness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CommandRulesTest {

    private val inBase = Telemetry(robotState = RobotState.IN_BASE, soc = 85.0)
    private val running = Telemetry(
        robotState = RobotState.ON_MISSION,
        speedMps = 0.5f,
        mission = Mission("E-0001", "Proveedor", "P4", MissionStatus.RUNNING, Checkpoint.P2, 40, Instant.now())
    )

    private fun enabled(type: CommandType, t: Telemetry, online: Boolean = true) =
        CommandRules.availability(type, t, online).enabled

    @Test fun `iniciar escolta requiere robot en base con 30 por ciento`() {
        assertTrue(enabled(CommandType.START_ESCORT, inBase))
        assertFalse(enabled(CommandType.START_ESCORT, inBase.copy(soc = 29.0)))
        assertEquals("Batería menor al 30 %", CommandRules.availability(CommandType.START_ESCORT, inBase.copy(soc = 29.0), true).condition)
        assertFalse(enabled(CommandType.START_ESCORT, running))
    }

    @Test fun `iniciar escolta se bloquea con una alerta crítica sin reconocer`() {
        val tilt = Alert("a", AlertType.TILT, AlertSeverity.CRITICAL, "Inclinación", "", Instant.now(), Checkpoint.BASE)
        assertFalse(enabled(CommandType.START_ESCORT, inBase.copy(alerts = listOf(tilt))))
        val acknowledged = tilt.copy(acknowledgedAt = Instant.now())
        assertTrue(enabled(CommandType.START_ESCORT, inBase.copy(alerts = listOf(acknowledged))))
    }

    @Test fun `pausar solo con escolta en curso y reanudar solo en pausa`() {
        assertTrue(enabled(CommandType.PAUSE, running))
        assertFalse(enabled(CommandType.RESUME, running))
        val paused = running.copy(robotState = RobotState.PAUSED, speedMps = 0f, mission = running.mission!!.copy(status = MissionStatus.PAUSED))
        assertFalse(enabled(CommandType.PAUSE, paused))
        assertTrue(enabled(CommandType.RESUME, paused))
        assertFalse(enabled(CommandType.RESUME, paused.copy(soc = 8.0)))
    }

    @Test fun `sin conexión ninguna orden está habilitada`() {
        CommandType.entries.forEach { assertFalse(it.name, enabled(it, running, online = false)) }
        assertEquals("Sin conexión con el robot", CommandRules.availability(CommandType.STOP, running, false).condition)
    }

    @Test fun `dar la mano solo con el robot quieto en P4 y libre`() {
        val atP4 = running.copy(robotState = RobotState.AT_DESTINATION, speedMps = 0f, checkpoint = Checkpoint.P4)
        assertTrue(enabled(CommandType.HANDSHAKE, atP4))
        assertFalse(enabled(CommandType.HANDSHAKE, atP4.copy(busyAction = BusyAction.GREETING)))
        assertFalse(enabled(CommandType.HANDSHAKE, running))
        assertTrue(enabled(CommandType.RELEASE_ARMS, atP4.copy(busyAction = BusyAction.HANDSHAKE)))
    }

    @Test fun `detener se habilita caminando, esperando o con un gesto`() {
        assertTrue(enabled(CommandType.STOP, running))
        assertTrue(enabled(CommandType.STOP, running.copy(robotState = RobotState.WAITING_CROSSING, speedMps = 0f)))
        assertTrue(enabled(CommandType.STOP, inBase.copy(busyAction = BusyAction.GREETING)))
        assertFalse(enabled(CommandType.STOP, inBase))
    }

    @Test fun `antigüedad del dato`() {
        assertEquals(Freshness.FRESH, DataFreshness.classify(3))
        assertEquals(Freshness.STALE, DataFreshness.classify(4))
        assertEquals(Freshness.STALE, DataFreshness.classify(10))
        assertEquals(Freshness.OFFLINE, DataFreshness.classify(11))
    }
}
