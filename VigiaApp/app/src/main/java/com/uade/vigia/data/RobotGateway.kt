package com.uade.vigia.data

import com.uade.vigia.data.remote.EmulatorRobotRepository
import com.uade.vigia.data.simulation.SimulationRobotRepository
import com.uade.vigia.domain.model.CommandResponse
import com.uade.vigia.domain.model.RobotCommand
import com.uade.vigia.domain.model.RobotEvent
import com.uade.vigia.domain.model.RobotInfo
import com.uade.vigia.domain.model.Telemetry
import com.uade.vigia.domain.repository.DataSource
import com.uade.vigia.domain.repository.DataSourceKind
import com.uade.vigia.domain.repository.DataSourceSelector
import com.uade.vigia.domain.repository.RobotRepository
import com.uade.vigia.domain.repository.ScenarioControls
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * Punto único de acceso al robot. Delega en la fuente elegida: el simulador interno (por defecto)
 * o el emulador de la cátedra. El resto de la app solo ve [RobotRepository].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RobotGateway(
    private val sim: SimulationRobotRepository,
    private val scope: CoroutineScope
) : RobotRepository, ScenarioControls, DataSourceSelector {

    private val active = MutableStateFlow<RobotRepository>(sim)
    private var emulator: EmulatorRobotRepository? = null

    private val _source = MutableStateFlow(DataSource(DataSourceKind.SIMULATOR, DEFAULT_EMULATOR_URL))
    override val source: StateFlow<DataSource> = _source.asStateFlow()

    override val robots: List<RobotInfo> get() = active.value.robots
    override val events: SharedFlow<RobotEvent> get() = sim.events
    override val telemetry: StateFlow<Telemetry> = active.flatMapLatest { it.telemetry }
        .stateIn(scope, SharingStarted.Eagerly, sim.telemetry.value)

    override suspend fun send(command: RobotCommand): CommandResponse = active.value.send(command)
    override fun reconnect() = active.value.reconnect()

    override fun useSimulator() {
        emulator?.stop()
        emulator = null
        active.value = sim
        _source.value = _source.value.copy(kind = DataSourceKind.SIMULATOR)
    }

    override suspend fun useEmulator(url: String): Result<Unit> {
        val candidate = EmulatorRobotRepository(sim, url, scope)
        return candidate.probe().onSuccess {
            emulator?.stop()
            candidate.start()
            emulator = candidate
            active.value = candidate
            _source.value = DataSource(DataSourceKind.EMULATOR, candidate.baseUrl.trimEnd('/'))
        }.onFailure { candidate.stop() }
    }

    private val scenarios: ScenarioControls get() = active.value as ScenarioControls
    override fun lowBattery() = scenarios.lowBattery()
    override fun visitorLagging() = scenarios.visitorLagging()
    override fun tilt() = scenarios.tilt()
    override fun disconnect() = scenarios.disconnect()
    override fun failNextCommand() = scenarios.failNextCommand()

    companion object {
        /** Desde el emulador de Android, 10.0.2.2 es la computadora donde corre el G1 HMI Lab. */
        const val DEFAULT_EMULATOR_URL = "http://10.0.2.2:8000"
    }
}
