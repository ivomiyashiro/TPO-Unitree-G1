package com.uade.vigia.di

import android.content.Context
import com.uade.vigia.data.RobotGateway
import com.uade.vigia.data.local.RoomHistoryRepository
import com.uade.vigia.data.local.VigiaDatabase
import com.uade.vigia.data.simulation.SimulationRobotRepository
import com.uade.vigia.domain.repository.DataSourceSelector
import com.uade.vigia.domain.repository.HistoryRepository
import com.uade.vigia.domain.repository.RobotRepository
import com.uade.vigia.domain.repository.ScenarioControls
import com.uade.vigia.domain.usecase.RecordRobotEventsUseCase
import com.uade.vigia.domain.usecase.SendCommandUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Inyección de dependencias manual. Vive en la Application, así el simulador y el registro del historial
 * siguen funcionando aunque la Activity se recree (por ejemplo al rotar la tablet).
 */
class AppContainer(context: Context) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database = VigiaDatabase.create(context)
    private val simulator = SimulationRobotRepository(appScope)
    private val gateway = RobotGateway(simulator, appScope)

    val robotRepository: RobotRepository = gateway
    val scenarioControls: ScenarioControls = gateway
    val dataSourceSelector: DataSourceSelector = gateway
    val historyRepository: HistoryRepository = RoomHistoryRepository(database.missionDao())
    val sendCommand = SendCommandUseCase(robotRepository, historyRepository)

    init {
        val recordEvents = RecordRobotEventsUseCase(robotRepository, historyRepository)
        appScope.launch { recordEvents() }
    }
}
