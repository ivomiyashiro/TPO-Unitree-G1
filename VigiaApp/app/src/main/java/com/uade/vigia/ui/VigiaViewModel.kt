package com.uade.vigia.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.uade.vigia.VigiaApplication
import com.uade.vigia.domain.model.CommandRecord
import com.uade.vigia.domain.model.CommandStatus
import com.uade.vigia.domain.model.CommandType
import com.uade.vigia.domain.model.HistoryEvent
import com.uade.vigia.domain.model.LedColor
import com.uade.vigia.domain.model.MissionSummary
import com.uade.vigia.domain.model.RobotCommand
import com.uade.vigia.domain.model.RobotInfo
import com.uade.vigia.domain.model.Telemetry
import com.uade.vigia.domain.repository.DataSource
import com.uade.vigia.domain.repository.DataSourceKind
import com.uade.vigia.domain.repository.DataSourceSelector
import com.uade.vigia.domain.repository.HistoryRepository
import com.uade.vigia.domain.repository.RobotRepository
import com.uade.vigia.domain.repository.ScenarioControls
import com.uade.vigia.domain.usecase.Availability
import com.uade.vigia.domain.usecase.CommandRules
import com.uade.vigia.domain.usecase.DataFreshness
import com.uade.vigia.domain.usecase.Freshness
import com.uade.vigia.domain.usecase.SendCommandUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.util.UUID

enum class HistoryFilter(val label: String) { ALL("Todo"), ESCORTS("Escoltas"), COMMANDS("Órdenes"), ALERTS("Alertas") }

data class NewEscortForm(
    val visitor: String = "Proveedor · Transporte Sur",
    val destination: String = "Oficina de recepción de mercadería (P4)",
    val vestVerified: Boolean = false
)

data class VigiaUiState(
    val robots: List<RobotInfo> = emptyList(),
    val selectedRobot: RobotInfo? = null,
    val telemetry: Telemetry = Telemetry(),
    val ageSeconds: Long = 0,
    val freshness: Freshness = Freshness.FRESH,
    val lastCommand: CommandRecord? = null,
    val pending: Set<CommandType> = emptySet(),
    val lastByType: Map<CommandType, CommandRecord> = emptyMap(),
    /** Orden que quedó sin respuesta durante un corte: se muestra al volver la conexión. */
    val unanswered: CommandRecord? = null,
    val missions: List<MissionSummary> = emptyList(),
    val currentMissionEvents: List<HistoryEvent> = emptyList(),
    val historyFilter: HistoryFilter = HistoryFilter.ALL,
    val selectedHistoryId: String? = null,
    val selectedHistoryEvents: List<HistoryEvent> = emptyList(),
    val form: NewEscortForm = NewEscortForm(),
    val message: String? = null,
    val dataSource: DataSource = DataSource(DataSourceKind.SIMULATOR, ""),
    val emulatorUrlDraft: String = "",
    val connectingSource: Boolean = false,
    val sourceError: String? = null
) {
    val online: Boolean get() = freshness != Freshness.OFFLINE
    fun availability(type: CommandType): Availability {
        val base = CommandRules.availability(type, telemetry, online)
        return if (type in pending) base.copy(enabled = false) else base
    }
    fun isPending(type: CommandType) = type in pending
    val canStartEscort: Boolean
        get() = availability(CommandType.START_ESCORT).enabled && form.vestVerified && form.visitor.isNotBlank()
}

@OptIn(ExperimentalCoroutinesApi::class)
class VigiaViewModel(
    private val robot: RobotRepository,
    private val scenarios: ScenarioControls,
    private val history: HistoryRepository,
    private val sendCommand: SendCommandUseCase,
    private val sources: DataSourceSelector
) : ViewModel() {

    private val _state = MutableStateFlow(VigiaUiState(robots = robot.robots))
    val state: StateFlow<VigiaUiState> = _state.asStateFlow()

    private val selectedHistory = MutableStateFlow<String?>(null)

    init {
        // Telemetría + reloj: la antigüedad del dato se recalcula cada segundo aunque no lleguen muestras.
        val ticker = flow { while (true) { emit(Unit); delay(1_000) } }
        viewModelScope.launch {
            combine(robot.telemetry, ticker) { t, _ -> t }.collect { t ->
                val age = Duration.between(t.timestamp, Instant.now()).seconds.coerceAtLeast(0)
                // Lectura marcada como no válida por el robot (por ejemplo, el emulador desconectado) = sin conexión.
                val freshness = if (!t.valid) Freshness.OFFLINE else DataFreshness.classify(age)
                _state.update { it.copy(telemetry = t, ageSeconds = age, freshness = freshness) }
            }
        }
        viewModelScope.launch {
            sources.source.collect { src ->
                _state.update {
                    it.copy(dataSource = src, emulatorUrlDraft = it.emulatorUrlDraft.ifBlank { src.emulatorUrl }, robots = robot.robots)
                }
            }
        }
        viewModelScope.launch {
            history.missions().collect { list -> _state.update { it.copy(missions = list) } }
        }
        viewModelScope.launch {
            robot.telemetry.map { it.mission?.id }.distinctUntilChanged()
                .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else history.events(id) }
                .collect { events -> _state.update { it.copy(currentMissionEvents = events) } }
        }
        viewModelScope.launch {
            combine(selectedHistory, history.missions()) { sel, missions -> sel ?: missions.firstOrNull()?.id }
                .distinctUntilChanged()
                .flatMapLatest { id ->
                    if (id == null) flowOf(null to emptyList()) else history.events(id).map { id to it }
                }
                .collect { (id, events) -> _state.update { it.copy(selectedHistoryId = id, selectedHistoryEvents = events) } }
        }
    }

    // ---------------------------------------------------------------- selección

    fun selectRobot(robotInfo: RobotInfo) {
        _state.update { it.copy(selectedRobot = robotInfo) }
    }

    fun useSimulator() {
        sources.useSimulator()
        _state.update { it.copy(sourceError = null) }
    }

    fun updateEmulatorUrl(url: String) = _state.update { it.copy(emulatorUrlDraft = url, sourceError = null) }

    fun useEmulator() {
        val url = _state.value.emulatorUrlDraft
        _state.update { it.copy(connectingSource = true, sourceError = null) }
        viewModelScope.launch {
            val result = sources.useEmulator(url)
            _state.update {
                it.copy(
                    connectingSource = false,
                    sourceError = result.exceptionOrNull()?.let { e ->
                        "No responde el emulador en $url (${e.javaClass.simpleName}). ¿Está corriendo uvicorn?"
                    }
                )
            }
        }
    }

    fun disconnectFromRobot() {
        _state.update { it.copy(selectedRobot = null) }
    }

    // -------------------------------------------------------------------- orden

    fun updateForm(form: NewEscortForm) = _state.update { it.copy(form = form) }

    fun startEscort() {
        val form = _state.value.form
        viewModelScope.launch {
            val id = history.nextMissionId()
            val ok = send(
                RobotCommand(
                    CommandType.START_ESCORT,
                    mapOf(
                        RobotCommand.PARAM_MISSION_ID to id,
                        RobotCommand.PARAM_VISITOR to form.visitor,
                        RobotCommand.PARAM_DESTINATION to form.destination
                    )
                )
            )
            if (ok) _state.update { it.copy(form = it.form.copy(vestVerified = false)) }
        }
    }

    fun command(type: CommandType) {
        viewModelScope.launch { send(RobotCommand(type)) }
    }

    fun acknowledge(alertId: String) {
        viewModelScope.launch { send(RobotCommand(CommandType.ACK_ALERT, mapOf(RobotCommand.PARAM_ALERT_ID to alertId))) }
    }

    fun speak(label: String, text: String) {
        viewModelScope.launch {
            send(RobotCommand(CommandType.SPEAK, mapOf(RobotCommand.PARAM_TEXT to text, RobotCommand.PARAM_LABEL to label)))
        }
    }

    fun setLed(color: LedColor) {
        viewModelScope.launch { send(RobotCommand(CommandType.SET_LED, mapOf(RobotCommand.PARAM_LED to color.name))) }
    }

    fun setVolume(volume: Int) {
        viewModelScope.launch { send(RobotCommand(CommandType.SET_VOLUME, mapOf(RobotCommand.PARAM_VOLUME to volume.toString()))) }
    }

    fun resendUnanswered() {
        val record = _state.value.unanswered ?: return
        _state.update { it.copy(unanswered = null) }
        viewModelScope.launch { send(record.command) }
    }

    fun dismissUnanswered() = _state.update { it.copy(unanswered = null) }

    fun retryConnection() = robot.reconnect()

    fun consumeMessage() = _state.update { it.copy(message = null) }

    /**
     * Envía una orden y refleja su estado: Pendiente → Confirmada / Rechazada / Sin respuesta.
     * Mientras está pendiente su botón queda bloqueado para no enviarla dos veces.
     */
    private suspend fun send(command: RobotCommand): Boolean {
        val type = command.type
        if (type in _state.value.pending) return false
        val missionId = _state.value.telemetry.mission?.takeIf { !it.status.isFinished }?.id
        val pendingRecord = CommandRecord(UUID.randomUUID().toString(), command, CommandStatus.PENDING, Instant.now(), missionId = missionId)
        _state.update {
            it.copy(
                pending = it.pending + type,
                lastCommand = pendingRecord,
                lastByType = it.lastByType + (type to pendingRecord)
            )
        }
        val record = sendCommand(command, missionId)
        _state.update {
            it.copy(
                pending = it.pending - type,
                lastCommand = record,
                lastByType = it.lastByType + (type to record),
                unanswered = if (record.status == CommandStatus.NO_RESPONSE) record else it.unanswered,
                message = when (record.status) {
                    CommandStatus.REJECTED -> "${command.description} rechazada: ${record.reason} (código ${record.rpcCode})"
                    CommandStatus.NO_RESPONSE -> "${command.description} sin respuesta del robot en 5 s"
                    else -> it.message
                }
            )
        }
        return record.status == CommandStatus.CONFIRMED
    }

    // ---------------------------------------------------------------- historial

    fun selectHistory(id: String) {
        selectedHistory.value = id
    }

    fun setHistoryFilter(filter: HistoryFilter) = _state.update { it.copy(historyFilter = filter) }

    // ---------------------------------------------------------------- escenarios

    fun scenarioLowBattery() = scenarios.lowBattery()
    fun scenarioVisitorLagging() = scenarios.visitorLagging()
    fun scenarioTilt() = scenarios.tilt()
    fun scenarioDisconnect() = scenarios.disconnect()
    fun scenarioFailNext() = scenarios.failNextCommand()

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as VigiaApplication
                val c = app.container
                VigiaViewModel(c.robotRepository, c.scenarioControls, c.historyRepository, c.sendCommand, c.dataSourceSelector)
            }
        }
    }
}
