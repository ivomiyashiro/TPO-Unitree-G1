package com.uade.vigia.data.simulation

import com.uade.vigia.domain.model.Alert
import com.uade.vigia.domain.model.AlertSeverity
import com.uade.vigia.domain.model.AlertType
import com.uade.vigia.domain.model.BusyAction
import com.uade.vigia.domain.model.Checkpoint
import com.uade.vigia.domain.model.CommandResponse
import com.uade.vigia.domain.model.CommandType
import com.uade.vigia.domain.model.EventTone
import com.uade.vigia.domain.model.LedColor
import com.uade.vigia.domain.model.Mission
import com.uade.vigia.domain.model.MissionStatus
import com.uade.vigia.domain.model.RobotCommand
import com.uade.vigia.domain.model.RobotEvent
import com.uade.vigia.domain.model.RobotInfo
import com.uade.vigia.domain.model.RobotState
import com.uade.vigia.domain.model.RouteMap
import com.uade.vigia.domain.model.Telemetry
import com.uade.vigia.domain.model.Thresholds
import com.uade.vigia.domain.model.VisitorState
import com.uade.vigia.domain.repository.RobotRepository
import com.uade.vigia.domain.repository.ScenarioControls
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.UUID
import kotlin.math.abs
import kotlin.random.Random

/**
 * Simulador propio del G1. Es la fuente de datos por defecto: la app funciona sin robot, servidor ni internet.
 *
 * Recorre BASE → P1 → P2 → P3 → P4 a 0,5 m/s, saluda y da las normas en P1, espera en P3 hasta que el guardia
 * confirma el cruce, avisa la llegada en P4 y vuelve a BASE. Publica una muestra de telemetría por segundo.
 * Mientras la conexión está cortada sigue simulando al robot pero no publica muestras ni responde órdenes.
 */
class SimulationRobotRepository(
    private val scope: CoroutineScope,
    private val clock: () -> Instant = Instant::now,
    private val random: Random = Random.Default
) : RobotRepository, ScenarioControls {

    override val robots = listOf(
        RobotInfo("GO-01", "G1 · GO-01", "Control de acceso · Centro logístico", available = true),
        RobotInfo("GO-02", "G1 · GO-02", "Muelle de carga · Centro logístico", available = false, lastSeenLabel = "Último dato hace 3 h")
    )

    private enum class Phase { IDLE, TO_P1, GREETING, TO_P3, WAITING_CROSSING, TO_P4, AT_DESTINATION, RETURNING, GOING_SAFE, AT_SAFE, PAUSED, STOPPED }

    private val walkingPhases = setOf(Phase.TO_P1, Phase.TO_P3, Phase.TO_P4, Phase.RETURNING, Phase.GOING_SAFE)
    private val walkingOrWaiting = setOf(Phase.TO_P1, Phase.TO_P3, Phase.TO_P4, Phase.WAITING_CROSSING)

    private val mutex = Mutex()
    private val _telemetry = MutableStateFlow(Telemetry(pose = RouteMap.poseAt(0f), timestamp = clock()))
    override val telemetry: StateFlow<Telemetry> = _telemetry.asStateFlow()
    private val _events = MutableSharedFlow<RobotEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<RobotEvent> = _events.asSharedFlow()

    // --- Estado interno del robot simulado ---
    private var phase = Phase.IDLE
    private var resumePhase = Phase.IDLE
    private var routePos = 0f
    private var odometer = 0f
    private var reachedP4 = false
    private var mission: Mission? = null
    private var checkpoint = Checkpoint.BASE
    private var soc = 85.0
    private var led = LedColor.GREEN
    private var volume = 60
    private var lastTts: String? = null
    private var busy: BusyAction? = null
    private var busyTicks = 0
    private var safetyGiven = 0
    private var greetingStep = 0
    private var destinationTicks = 0
    private var visitorState = VisitorState.NONE
    private var visitorDistance = 0f
    private var lagTicks = 0
    private var roll = 0.3f
    private var pitch = -0.2f
    private var tiltTicks = 0
    private var safeFrom = RouteMap.poseAt(0f)
    private var safeProgress = 0f
    private var safeDistance = 1f
    private var alerts = listOf<Alert>()
    private var batteryWarned = false
    private var batteryCritical = false
    private var tiltAlerted = false
    private var linkDownUntil: Instant? = null
    private var failNext = false

    init {
        scope.launch {
            while (isActive) {
                delay(TICK_MS)
                mutex.withLock { tick() }
            }
        }
    }

    // ------------------------------------------------------------------ órdenes

    override suspend fun send(command: RobotCommand): CommandResponse {
        delay(NETWORK_DELAY_MS)
        if (isLinkDown()) awaitCancellation() // la orden no llega: la app la marca "Sin respuesta" a los 5 s
        return mutex.withLock {
            val response = handle(command)
            publish()
            response
        }
    }

    override fun reconnect() {
        scope.launch {
            mutex.withLock {
                if (linkDownUntil != null) {
                    linkDownUntil = null
                    milestone("Conexión recuperada", EventTone.OK)
                }
                publish()
            }
        }
    }

    private fun handle(command: RobotCommand): CommandResponse {
        val type = command.type
        if (failNext && type != CommandType.ACK_ALERT) {
            failNext = false
            raiseAlert(
                AlertType.COMMAND_REJECTED, AlertSeverity.WARNING,
                "Orden rechazada: ${type.label} (código $RPC_FORCED_FAILURE)",
                "El robot no aceptó la orden. Podés volver a enviarla."
            )
            return CommandResponse.rejected("El robot rechazó la orden", RPC_FORCED_FAILURE)
        }
        val inProgress = mission?.status?.isFinished == false
        return when (type) {
            CommandType.START_ESCORT -> {
                when {
                    phase != Phase.IDLE || inProgress -> CommandResponse.rejected("El robot está ocupado", RPC_BUSY)
                    soc < Thresholds.BATTERY_MIN_TO_START -> CommandResponse.rejected("Batería baja", RPC_LOW_BATTERY)
                    else -> {
                        startEscort(command)
                        CommandResponse.confirmed()
                    }
                }
            }
            CommandType.PAUSE -> {
                if (phase in walkingOrWaiting || phase == Phase.GREETING) {
                    hold(Phase.PAUSED, LedColor.AMBER)
                    CommandResponse.confirmed()
                } else CommandResponse.rejected("No hay una escolta en curso", RPC_INVALID_STATE)
            }
            CommandType.RESUME -> {
                when {
                    !(phase == Phase.PAUSED || phase == Phase.STOPPED) || !inProgress ->
                        CommandResponse.rejected("La escolta no está en pausa", RPC_INVALID_STATE)
                    visitorDistance > Thresholds.VISITOR_LAGGING_M ->
                        CommandResponse.rejected("El visitante no está junto al robot", RPC_VISITOR_AWAY)
                    soc <= Thresholds.BATTERY_CRITICAL -> CommandResponse.rejected("Batería crítica", RPC_LOW_BATTERY)
                    else -> {
                        phase = resumePhase
                        led = if (phase == Phase.WAITING_CROSSING) LedColor.AMBER else LedColor.BLUE
                        mission = mission?.copy(status = MissionStatus.RUNNING)
                        milestone("Escolta reanudada", EventTone.INFO)
                        CommandResponse.confirmed()
                    }
                }
            }
            CommandType.CANCEL -> {
                if (inProgress && phase != Phase.RETURNING) {
                    finishMission(MissionStatus.CANCELLED, "Escolta cancelada por el guardia · vuelve a la base", EventTone.NEUTRAL)
                    visitorState = VisitorState.NONE
                    startReturn()
                    CommandResponse.confirmed()
                } else CommandResponse.rejected("No hay una escolta para cancelar", RPC_INVALID_STATE)
            }
            CommandType.GO_SAFE -> {
                if (inProgress && phase in setOf(Phase.TO_P1, Phase.GREETING, Phase.TO_P3, Phase.WAITING_CROSSING, Phase.TO_P4, Phase.PAUSED, Phase.STOPPED)) {
                    finishMission(MissionStatus.ABORTED, "Ir a punto seguro · escolta abortada", EventTone.WARNING)
                    goSafe()
                    CommandResponse.confirmed()
                } else CommandResponse.rejected("Solo durante una escolta", RPC_INVALID_STATE)
            }
            CommandType.STOP -> {
                // Siempre se acepta si llega.
                if (phase != Phase.STOPPED && (phase in walkingPhases || busy != null || phase == Phase.GREETING ||
                        phase == Phase.WAITING_CROSSING || phase == Phase.AT_DESTINATION)) {
                    hold(Phase.STOPPED, LedColor.RED)
                }
                busy = null
                CommandResponse.confirmed()
            }
            CommandType.CONFIRM_CROSSING -> {
                if (phase == Phase.WAITING_CROSSING) {
                    phase = Phase.TO_P4
                    led = LedColor.BLUE
                    acknowledge(alerts.filter { it.type == AlertType.CROSSING_WAIT && it.acknowledgedAt == null }.map { it.id })
                    mission = mission?.copy(crossingWaitSince = null)
                    say("Cruzamos. Camine detrás de mí.")
                    CommandResponse.confirmed()
                } else CommandResponse.rejected("El robot no está esperando en el cruce", RPC_INVALID_STATE)
            }
            CommandType.RETURN_TO_BASE -> {
                when (phase) {
                    Phase.AT_DESTINATION -> { startReturn(); CommandResponse.confirmed() }
                    Phase.AT_SAFE -> { routePos = SAFE_TO_BASE_M; startReturn(); CommandResponse.confirmed() }
                    Phase.STOPPED -> if (!inProgress) { startReturn(); CommandResponse.confirmed() }
                        else CommandResponse.rejected("Hay una escolta en curso", RPC_INVALID_STATE)
                    else -> CommandResponse.rejected("El robot no puede volver ahora", RPC_INVALID_STATE)
                }
            }
            CommandType.ACK_ALERT -> {
                val id = command.params[RobotCommand.PARAM_ALERT_ID]
                if (alerts.any { it.id == id && it.acknowledgedAt == null }) {
                    acknowledge(listOfNotNull(id))
                    CommandResponse.confirmed()
                } else CommandResponse.rejected("La alerta ya fue reconocida", RPC_INVALID_STATE)
            }
            CommandType.GREET -> {
                if (phase in walkingPhases || busy != null) CommandResponse.rejected("Robot ocupado", RPC_BUSY)
                else { startGesture(BusyAction.GREETING, 3); CommandResponse.confirmed() }
            }
            CommandType.HANDSHAKE -> {
                when {
                    phase in walkingPhases || busy != null -> CommandResponse.rejected("Robot ocupado", RPC_BUSY)
                    checkpoint != Checkpoint.P4 -> CommandResponse.rejected("Solo en P4", RPC_INVALID_STATE)
                    else -> { startGesture(BusyAction.HANDSHAKE, 4); CommandResponse.confirmed() }
                }
            }
            CommandType.RELEASE_ARMS -> {
                if (busy != null) { busy = null; busyTicks = 0; CommandResponse.confirmed() }
                else CommandResponse.rejected("No hay un gesto en curso", RPC_INVALID_STATE)
            }
            CommandType.SPEAK -> {
                say(command.params[RobotCommand.PARAM_TEXT].orEmpty())
                CommandResponse.confirmed()
            }
            CommandType.SET_LED -> {
                led = command.params[RobotCommand.PARAM_LED]?.let { LedColor.valueOf(it) } ?: led
                CommandResponse.confirmed()
            }
            CommandType.SET_VOLUME -> {
                volume = command.params[RobotCommand.PARAM_VOLUME]?.toIntOrNull()?.coerceIn(0, 100) ?: volume
                CommandResponse.confirmed()
            }
        }
    }

    // --------------------------------------------------------------- escenarios

    override fun lowBattery() = scenario {
        soc = 19.0
        milestone("Escenario de prueba: batería en 19 %", EventTone.WARNING)
    }

    override fun visitorLagging() = scenario {
        if (visitorState == VisitorState.WITH_ROBOT) lagTicks = LAG_DURATION_TICKS
    }

    override fun tilt() = scenario {
        roll = 20f
        tiltTicks = TILT_DURATION_TICKS
    }

    override fun disconnect() = scenario {
        linkDownUntil = clock().plusSeconds(OUTAGE_SECONDS)
    }

    override fun failNextCommand() = scenario { failNext = true }

    private fun scenario(block: () -> Unit) {
        scope.launch { mutex.withLock { block(); publish() } }
    }

    // --------------------------------------------------------------------- tick

    private fun tick() {
        val until = linkDownUntil
        if (until != null && !clock().isBefore(until)) {
            linkDownUntil = null
            milestone("Conexión recuperada", EventTone.OK)
        }

        if (busy != null && --busyTicks <= 0) busy = null

        when (phase) {
            Phase.TO_P1 -> {
                advance(STEP_M)
                if (routePos >= Checkpoint.P1.routeMeters) arriveP1()
            }
            Phase.GREETING -> greet()
            Phase.TO_P3 -> {
                advance(STEP_M)
                if (routePos >= Checkpoint.P2.routeMeters && checkpoint == Checkpoint.P1) checkpoint = Checkpoint.P2
                if (routePos >= Checkpoint.P3.routeMeters) arriveP3()
            }
            Phase.TO_P4 -> {
                advance(STEP_M)
                if (routePos >= Checkpoint.P4.routeMeters) arriveP4()
            }
            Phase.AT_DESTINATION -> if (++destinationTicks >= AUTO_RETURN_TICKS) startReturn()
            Phase.RETURNING -> {
                advance(-STEP_M)
                checkpoint = Checkpoint.route.last { it.routeMeters <= routePos }
                if (routePos <= 0f) arriveBase()
            }
            Phase.GOING_SAFE -> {
                safeProgress = (safeProgress + STEP_M / safeDistance).coerceAtMost(1f)
                odometer += STEP_M
                if (safeProgress >= 1f) {
                    phase = Phase.AT_SAFE
                    checkpoint = Checkpoint.SAFE
                    led = LedColor.AMBER
                    milestone("Robot en el punto seguro · espera una orden", EventTone.INFO)
                }
            }
            Phase.IDLE, Phase.WAITING_CROSSING, Phase.AT_SAFE, Phase.PAUSED, Phase.STOPPED -> Unit
        }

        updateVisitor()
        updateBattery()
        updateTilt()
        publish()
    }

    private fun startEscort(command: RobotCommand) {
        val now = clock()
        val id = command.params[RobotCommand.PARAM_MISSION_ID] ?: "E-${random.nextInt(100, 999)}"
        val visitor = command.params[RobotCommand.PARAM_VISITOR].orEmpty().ifBlank { "Visitante" }
        val destination = command.params[RobotCommand.PARAM_DESTINATION].orEmpty().ifBlank { Checkpoint.P4.place }
        mission = Mission(id, visitor, destination, MissionStatus.RUNNING, Checkpoint.BASE, 0, now)
        phase = Phase.TO_P1
        routePos = 0f
        odometer = 0f
        reachedP4 = false
        checkpoint = Checkpoint.BASE
        safetyGiven = 0
        greetingStep = 0
        led = LedColor.BLUE
        visitorState = VisitorState.WITH_ROBOT
        visitorDistance = 1.4f
        _events.tryEmit(RobotEvent.MissionStarted(id, visitor, destination, now))
    }

    private fun arriveP1() {
        routePos = Checkpoint.P1.routeMeters
        checkpoint = Checkpoint.P1
        phase = Phase.GREETING
        greetingStep = 0
        startGesture(BusyAction.GREETING, 3)
        say("Bienvenido al centro logístico. Voy a acompañarlo hasta la oficina de recepción.")
    }

    private fun greet() {
        greetingStep++
        // Una norma cada 2 s, después del saludo.
        if (greetingStep >= 4 && greetingStep % 2 == 0 && safetyGiven < SAFETY_MESSAGES.size) {
            say(SAFETY_MESSAGES[safetyGiven])
            safetyGiven++
        }
        if (safetyGiven == SAFETY_MESSAGES.size && greetingStep >= 12) {
            milestone("P1 · saludo y ${SAFETY_MESSAGES.size} normas de seguridad dichas", EventTone.INFO)
            phase = Phase.TO_P3
        }
    }

    private fun arriveP3() {
        routePos = Checkpoint.P3.routeMeters
        checkpoint = Checkpoint.P3
        phase = Phase.WAITING_CROSSING
        led = LedColor.AMBER
        mission = mission?.copy(crossingWaitSince = clock())
        say("Espere. Vamos a cruzar la calle de autoelevadores cuando el paso esté libre.")
        milestone("P3 · espera antes del cruce", EventTone.WARNING)
        raiseAlert(
            AlertType.CROSSING_WAIT, AlertSeverity.INFO,
            "El robot espera antes del cruce de autoelevadores",
            "Avisó por voz al visitante que espere. Confirmá cuando el paso esté libre: sin tu confirmación el robot no cruza."
        )
    }

    private fun arriveP4() {
        routePos = Checkpoint.P4.routeMeters
        checkpoint = Checkpoint.P4
        reachedP4 = true
        phase = Phase.AT_DESTINATION
        destinationTicks = 0
        visitorState = VisitorState.DELIVERED
        say("Llegamos a destino. Gracias por su visita.")
        milestone("P4 · llegada y despedida", EventTone.INFO)
    }

    private fun startReturn() {
        phase = Phase.RETURNING
        led = if (mission?.status == MissionStatus.RUNNING) LedColor.BLUE else LedColor.GREEN
        busy = null
    }

    private fun arriveBase() {
        routePos = 0f
        phase = Phase.IDLE
        checkpoint = Checkpoint.BASE
        led = LedColor.GREEN
        visitorState = VisitorState.NONE
        visitorDistance = 0f
        if (mission?.status?.isFinished == false) {
            finishMission(MissionStatus.COMPLETED, "Regreso a la base · cargando", EventTone.OK)
        } else {
            milestone("Robot en la base · cargando", EventTone.OK)
        }
    }

    private fun goSafe() {
        safeFrom = RouteMap.poseAt(routePos)
        safeProgress = 0f
        safeDistance = routePos + 3f
        phase = Phase.GOING_SAFE
        led = LedColor.AMBER
        visitorState = VisitorState.NONE
        busy = null
    }

    private fun hold(target: Phase, color: LedColor) {
        if (phase != Phase.PAUSED && phase != Phase.STOPPED) resumePhase = phase
        phase = target
        led = color
        busy = null
        if (mission?.status == MissionStatus.RUNNING) mission = mission?.copy(status = MissionStatus.PAUSED)
        milestone(if (target == Phase.PAUSED) "Escolta en pausa" else "Robot detenido", EventTone.WARNING)
    }

    private fun finishMission(status: MissionStatus, text: String, tone: EventTone) {
        val current = mission ?: return
        val now = clock()
        mission = current.copy(status = status, endedAt = now, distanceM = odometer, crossingWaitSince = null)
        milestone(text, tone)
        _events.tryEmit(RobotEvent.MissionFinished(current.id, status, odometer, now))
    }

    private fun startGesture(action: BusyAction, seconds: Int) {
        busy = action
        busyTicks = seconds
    }

    private fun advance(delta: Float) {
        routePos = (routePos + delta).coerceIn(0f, Checkpoint.P4.routeMeters)
        odometer += abs(delta)
    }

    private fun updateVisitor() {
        if (visitorState == VisitorState.NONE || visitorState == VisitorState.DELIVERED) return
        if (lagTicks > 0) {
            lagTicks--
            visitorDistance = (visitorDistance + 0.8f).coerceAtMost(4.2f)
            if (visitorDistance > Thresholds.VISITOR_LAGGING_M && visitorState == VisitorState.WITH_ROBOT) {
                visitorState = VisitorState.LAGGING
                if (phase in walkingOrWaiting || phase == Phase.GREETING) hold(Phase.PAUSED, LedColor.AMBER)
                say("Por favor, acérquese al robot.")
                raiseAlert(
                    AlertType.VISITOR_LAGGING, AlertSeverity.WARNING,
                    "Visitante rezagado · a ${"%.1f".format(visitorDistance).replace('.', ',')} m",
                    "El robot se detuvo y le pidió por voz que se acerque. Reanudar se rechaza hasta que el visitante esté junto al robot."
                )
            }
            if (lagTicks == 0) {
                visitorState = VisitorState.WITH_ROBOT
                visitorDistance = 1.3f
                milestone("El visitante volvió junto al robot", EventTone.INFO)
            }
            return
        }
        visitorDistance = if (phase in walkingPhases) 1.1f + random.nextFloat() * 0.5f else visitorDistance.coerceAtLeast(1.1f)
    }

    private fun updateBattery() {
        soc = when {
            phase == Phase.IDLE -> (soc + CHARGE_PER_TICK).coerceAtMost(100.0)
            phase in walkingPhases -> soc - DRAIN_WALKING_PER_TICK
            else -> soc - DRAIN_IDLE_PER_TICK
        }.coerceAtLeast(0.0)

        if (soc < Thresholds.BATTERY_WARNING && !batteryWarned) {
            batteryWarned = true
            raiseAlert(
                AlertType.BATTERY_LOW, AlertSeverity.WARNING,
                "Batería baja · ${soc.toInt()} %",
                "Quedan unos ${(soc * 1.2).toInt()} minutos. Con menos de 10 % el robot vuelve solo a la base."
            )
        }
        if (soc < Thresholds.BATTERY_CRITICAL && !batteryCritical) {
            batteryCritical = true
            raiseAlert(
                AlertType.BATTERY_CRITICAL, AlertSeverity.CRITICAL,
                "Batería crítica · ${soc.toInt()} %",
                "El robot abortó la escolta y vuelve solo a la base."
            )
            if (mission?.status?.isFinished == false) {
                finishMission(MissionStatus.ABORTED, "Escolta abortada por batería crítica", EventTone.ERROR)
                startReturn()
            }
        }
        if (soc > Thresholds.BATTERY_MIN_TO_START) {
            batteryWarned = false
            batteryCritical = false
        }
    }

    private fun updateTilt() {
        if (tiltTicks > 0) {
            if (abs(roll) > Thresholds.TILT_CRITICAL_DEG && !tiltAlerted) {
                tiltAlerted = true
                hold(Phase.STOPPED, LedColor.RED)
                raiseAlert(
                    AlertType.TILT, AlertSeverity.CRITICAL,
                    "Inclinación anormal · ${roll.toInt()}°",
                    "El robot se detuvo. Despejá el área y avisá a mantenimiento antes de reanudar."
                )
            }
            if (--tiltTicks == 0) {
                roll = 0.4f
                tiltAlerted = false
            }
        } else {
            val moving = phase in walkingPhases
            roll = if (moving) (random.nextFloat() * 2.4f - 1.2f) else 0.3f
            pitch = if (moving) (random.nextFloat() * 1.6f - 0.8f) else -0.2f
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun say(text: String) {
        lastTts = text
    }

    private fun milestone(text: String, tone: EventTone) {
        _events.tryEmit(RobotEvent.Milestone(mission?.id, text, tone, clock()))
    }

    private fun raiseAlert(type: AlertType, severity: AlertSeverity, title: String, detail: String) {
        val alert = Alert(
            id = UUID.randomUUID().toString(),
            type = type,
            severity = severity,
            title = title,
            detail = detail,
            raisedAt = clock(),
            checkpoint = checkpoint,
            missionId = mission?.takeIf { !it.status.isFinished }?.id
        )
        alerts = alerts + alert
        _events.tryEmit(RobotEvent.AlertRaised(alert, alert.raisedAt))
    }

    private fun acknowledge(ids: List<String>) {
        val now = clock()
        alerts = alerts.map {
            if (it.id in ids && it.acknowledgedAt == null) it.copy(acknowledgedAt = now, acknowledgedBy = Thresholds.OPERATOR) else it
        }
    }

    private fun isLinkDown(): Boolean = linkDownUntil?.let { clock().isBefore(it) } == true

    private fun robotState(): RobotState = when (phase) {
        Phase.IDLE -> RobotState.IN_BASE
        Phase.TO_P1, Phase.GREETING, Phase.TO_P3, Phase.TO_P4 -> RobotState.ON_MISSION
        Phase.WAITING_CROSSING -> RobotState.WAITING_CROSSING
        Phase.AT_DESTINATION -> RobotState.AT_DESTINATION
        Phase.RETURNING -> RobotState.RETURNING
        Phase.GOING_SAFE -> RobotState.GOING_SAFE
        Phase.AT_SAFE -> RobotState.AT_SAFE
        Phase.PAUSED -> RobotState.PAUSED
        Phase.STOPPED -> RobotState.STOPPED
    }

    private fun publish() {
        if (isLinkDown()) return
        val pose = when (phase) {
            Phase.GOING_SAFE -> RouteMap.lerp(safeFrom, RouteMap.SAFE_POSE, safeProgress)
            Phase.AT_SAFE -> RouteMap.SAFE_POSE
            else -> RouteMap.poseAt(routePos)
        }
        val progress = if (reachedP4) 100 else ((routePos / Checkpoint.P4.routeMeters) * 100).toInt()
        _telemetry.value = Telemetry(
            timestamp = clock(),
            valid = true,
            link = com.uade.vigia.domain.model.LinkStatus.ONLINE,
            latencyMs = 38 + random.nextInt(0, 18),
            soc = soc,
            soh = 98,
            robotState = robotState(),
            busyAction = busy,
            mission = mission?.let { m ->
                if (m.status.isFinished) m else m.copy(checkpoint = checkpoint, progressPct = progress, distanceM = odometer)
            },
            checkpoint = checkpoint,
            pose = pose,
            speedMps = if (phase in walkingPhases) Thresholds.ESCORT_SPEED_MPS else 0f,
            rollDeg = roll,
            pitchDeg = pitch,
            visitorState = visitorState,
            visitorDistanceM = visitorDistance,
            safetyGiven = safetyGiven,
            safetyPlanned = SAFETY_MESSAGES.size,
            led = led,
            volumePct = volume,
            lastTts = lastTts,
            alerts = alerts
        )
    }

    companion object {
        const val TICK_MS = 1_000L
        private const val NETWORK_DELAY_MS = 450L
        private const val STEP_M = Thresholds.ESCORT_SPEED_MPS
        private const val SAFE_TO_BASE_M = 3f
        private const val AUTO_RETURN_TICKS = 45
        private const val LAG_DURATION_TICKS = 20
        private const val TILT_DURATION_TICKS = 8
        private const val OUTAGE_SECONDS = 25L
        private const val CHARGE_PER_TICK = 0.08
        private const val DRAIN_WALKING_PER_TICK = 0.03
        private const val DRAIN_IDLE_PER_TICK = 0.01

        const val RPC_INVALID_STATE = 3001
        const val RPC_LOW_BATTERY = 3002
        const val RPC_FORCED_FAILURE = 3104
        const val RPC_VISITOR_AWAY = 3201
        const val RPC_BUSY = 3203

        val SAFETY_MESSAGES = com.uade.vigia.domain.model.SafetyMessages.all
    }
}
