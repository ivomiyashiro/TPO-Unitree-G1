package com.uade.vigia.data

import com.uade.vigia.data.simulation.SimulationRobotRepository
import com.uade.vigia.domain.model.AlertType
import com.uade.vigia.domain.model.Checkpoint
import com.uade.vigia.domain.model.CommandStatus
import com.uade.vigia.domain.model.CommandType
import com.uade.vigia.domain.model.LedColor
import com.uade.vigia.domain.model.MissionStatus
import com.uade.vigia.domain.model.RobotCommand
import com.uade.vigia.domain.model.RobotState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class SimulationRobotRepositoryTest {

    private fun TestScope.simulator() = SimulationRobotRepository(
        scope = backgroundScope,
        clock = { Instant.ofEpochMilli(testScheduler.currentTime) },
        random = Random(1)
    )

    private fun start() = RobotCommand(
        CommandType.START_ESCORT,
        mapOf(RobotCommand.PARAM_MISSION_ID to "E-0001", RobotCommand.PARAM_VISITOR to "Proveedor")
    )

    @Test fun `escolta completa - espera en el cruce hasta la confirmación del guardia`() = runTest {
        val sim = simulator()
        assertEquals(CommandStatus.CONFIRMED, sim.send(start()).status)
        assertEquals(LedColor.BLUE, sim.telemetry.value.led)

        advanceTimeBy(90_000); runCurrent()
        val waiting = sim.telemetry.value
        assertEquals(RobotState.WAITING_CROSSING, waiting.robotState)
        assertEquals(Checkpoint.P3, waiting.checkpoint)
        assertEquals(LedColor.AMBER, waiting.led)
        assertEquals(4, waiting.safetyGiven)
        assertTrue(waiting.activeAlerts.any { it.type == AlertType.CROSSING_WAIT })

        // Sin confirmación no cruza.
        advanceTimeBy(30_000); runCurrent()
        assertEquals(RobotState.WAITING_CROSSING, sim.telemetry.value.robotState)

        assertEquals(CommandStatus.CONFIRMED, sim.send(RobotCommand(CommandType.CONFIRM_CROSSING)).status)
        assertTrue(sim.telemetry.value.activeAlerts.none { it.type == AlertType.CROSSING_WAIT })

        advanceTimeBy(50_000); runCurrent()
        assertEquals(RobotState.AT_DESTINATION, sim.telemetry.value.robotState)

        assertEquals(CommandStatus.CONFIRMED, sim.send(RobotCommand(CommandType.RETURN_TO_BASE)).status)
        advanceTimeBy(100_000); runCurrent()
        val done = sim.telemetry.value
        assertEquals(RobotState.IN_BASE, done.robotState)
        assertEquals(MissionStatus.COMPLETED, done.mission?.status)
        assertEquals(92f, done.mission!!.distanceM, 0.5f)
    }

    @Test fun `reanudar se rechaza mientras el visitante está rezagado`() = runTest {
        val sim = simulator()
        sim.send(start())
        advanceTimeBy(30_000); runCurrent()
        sim.visitorLagging()
        advanceTimeBy(6_000); runCurrent()
        assertEquals(RobotState.PAUSED, sim.telemetry.value.robotState)
        val resume = sim.send(RobotCommand(CommandType.RESUME))
        assertEquals(CommandStatus.REJECTED, resume.status)
        assertEquals(SimulationRobotRepository.RPC_VISITOR_AWAY, resume.rpcCode)

        advanceTimeBy(20_000); runCurrent()
        assertEquals(CommandStatus.CONFIRMED, sim.send(RobotCommand(CommandType.RESUME)).status)
    }

    @Test fun `sin conexión no llegan muestras ni respuestas`() = runTest {
        val sim = simulator()
        runCurrent()
        sim.disconnect()
        runCurrent()
        val before = sim.telemetry.value.timestamp
        advanceTimeBy(5_000); runCurrent()
        assertEquals(before, sim.telemetry.value.timestamp)
        val reply = async { withTimeoutOrNull(5_000) { sim.send(RobotCommand(CommandType.GREET)) } }
        advanceTimeBy(6_000); runCurrent()
        assertNull(reply.await())

        sim.reconnect()
        advanceTimeBy(1_500); runCurrent()
        assertTrue(sim.telemetry.value.timestamp.isAfter(before))
    }

    @Test fun `fallar la próxima orden la rechaza con código 3104 y genera una alerta`() = runTest {
        val sim = simulator()
        sim.failNextCommand()
        runCurrent()
        val r = sim.send(RobotCommand(CommandType.GREET))
        assertEquals(CommandStatus.REJECTED, r.status)
        assertEquals(3104, r.rpcCode)
        assertTrue(sim.telemetry.value.activeAlerts.any { it.type == AlertType.COMMAND_REJECTED })
    }
}
