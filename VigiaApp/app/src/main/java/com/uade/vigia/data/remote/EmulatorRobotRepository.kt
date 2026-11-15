package com.uade.vigia.data.remote

import com.uade.vigia.data.simulation.SimulationRobotRepository
import com.uade.vigia.domain.model.CommandResponse
import com.uade.vigia.domain.model.CommandStatus
import com.uade.vigia.domain.model.CommandType
import com.uade.vigia.domain.model.LedColor
import com.uade.vigia.domain.model.RobotCommand
import com.uade.vigia.domain.model.RobotEvent
import com.uade.vigia.domain.model.RobotInfo
import com.uade.vigia.domain.model.Telemetry
import com.uade.vigia.domain.repository.RobotRepository
import com.uade.vigia.domain.repository.ScenarioControls
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.PI

/**
 * Segunda implementación de [RobotRepository]: se conecta al emulador de la cátedra (G1 HMI Lab v0.7).
 *
 * El emulador cubre conexión, batería (BMS), inclinación (IMU), LED, volumen, voz y gestos.
 * No implementa locomoción ni misiones, así que el recorrido lo sigue resolviendo el simulador de la app,
 * alimentado con la batería, la inclinación y la conexión que informa el emulador (sección 11 del documento).
 */
class EmulatorRobotRepository(
    private val sim: SimulationRobotRepository,
    baseUrl: String,
    parentScope: CoroutineScope
) : RobotRepository, ScenarioControls {

    val baseUrl: String = baseUrl.trim().trimEnd('/') + "/"
    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext.job))

    private val http = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()
    private val api: G1LabApi = Retrofit.Builder()
        .baseUrl(this.baseUrl)
        .client(http)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(G1LabApi::class.java)

    private val sample = MutableStateFlow<EmulatorSample?>(null)
    /** Latencia medida como ida y vuelta de un GET: no depende de que los relojes estén sincronizados. */
    private val latencyMs = MutableStateFlow(0)
    private var socket: WebSocket? = null
    @Volatile private var generation = 0
    @Volatile private var running = false

    override val robots: List<RobotInfo> get() = sim.robots
    override val events: SharedFlow<RobotEvent> get() = sim.events

    override val telemetry: StateFlow<Telemetry> = combine(sim.telemetry, sample, latencyMs) { t, e, rtt -> merge(t, e, rtt) }
        .stateIn(scope, SharingStarted.Eagerly, sim.telemetry.value)

    /** Comprueba que el emulador responda antes de usarlo. */
    suspend fun probe(): Result<Unit> = runCatching { EmulatorSample.from(api.telemetry()) }.map { }

    fun start() {
        running = true
        openSocket()
        scope.launch {
            while (true) {
                val t0 = System.nanoTime()
                if (runCatching { api.telemetry() }.isSuccess) latencyMs.value = ((System.nanoTime() - t0) / 1_000_000).toInt()
                delay(5_000)
            }
        }
        // El LED y los mensajes de voz que decide el recorrido simulado se envían al emulador,
        // así el emulador muestra lo mismo que la app (y lo registra en sus eventos).
        scope.launch {
            sim.telemetry.map { it.led }.distinctUntilChanged().collect { color ->
                runCatching { api.led(color.toRgbBody()) }
            }
        }
        scope.launch {
            sim.telemetry.map { it.lastTts }.filterNotNull().distinctUntilChanged().drop(1).collect { text ->
                runCatching { api.tts(mapOf("text" to text.take(200))) }
            }
        }
    }

    fun stop() {
        running = false
        generation++
        socket?.close(1000, null)
        scope.cancel()
        sim.useInternalSensors()
    }

    override fun reconnect() {
        if (!running) return
        socket?.cancel()
        openSocket()
    }

    private fun openSocket() {
        val gen = ++generation
        val url = baseUrl.replaceFirst("http", "ws") + "ws/robots/g1/telemetry"
        socket = http.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (gen != generation) return
                val e = EmulatorSample.parse(text) ?: return
                sample.value = e
                sim.applyExternalSensors(e.soc.toDouble(), e.soh, e.rollDeg, e.pitchDeg)
                sim.setExternalLink(e.connected && e.valid)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = lost(gen)
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = lost(gen)
        })
    }

    /** Se cortó el WebSocket: dejan de llegar muestras y se reintenta cada 3 s. */
    private fun lost(gen: Int) {
        if (gen != generation || !running) return
        sim.setExternalLink(false)
        scope.launch {
            delay(3_000)
            if (gen == generation && running) openSocket()
        }
    }

    private fun merge(t: Telemetry, e: EmulatorSample?, rtt: Int): Telemetry {
        if (e == null) return t
        return t.copy(
            valid = e.connected && e.valid,
            latencyMs = rtt,
            busyAction = e.busy ?: t.busyAction,
            led = e.led ?: t.led,
            volumePct = e.volume,
            lastTts = e.lastTts ?: t.lastTts
        )
    }

    // -------------------------------------------------------------------- órdenes

    override suspend fun send(command: RobotCommand): CommandResponse {
        val p = command.params
        val call: (suspend () -> retrofit2.Response<com.google.gson.JsonObject>)? = when (command.type) {
            CommandType.GREET -> { { api.greeting() } }
            CommandType.HANDSHAKE -> { { api.handshake() } }
            CommandType.RELEASE_ARMS -> { { api.releaseArms() } }
            CommandType.SPEAK -> { { api.tts(mapOf("text" to p[RobotCommand.PARAM_TEXT].orEmpty().take(200))) } }
            CommandType.SET_LED -> { { api.led(LedColor.valueOf(p.getValue(RobotCommand.PARAM_LED)).toRgbBody()) } }
            CommandType.SET_VOLUME -> { { api.volume(mapOf("volume" to (p[RobotCommand.PARAM_VOLUME]?.toIntOrNull() ?: 50))) } }
            // Recorrido, pausa, cruce, punto seguro, detener: no existen en el emulador, las resuelve el simulador.
            else -> null
        }
        if (call == null) return sim.send(command)

        val response = try {
            call.invoke().toCommandResponse()
        } catch (e: IOException) {
            awaitCancellation() // el emulador no respondió: a los 5 s la app marca la orden "Sin respuesta"
        }
        if (response.status == CommandStatus.CONFIRMED && command.type == CommandType.SET_LED) {
            sim.send(command) // mantiene el mismo LED en el recorrido simulado
        }
        return response
    }

    // ------------------------------------------------- escenarios (consola docente)

    override fun lowBattery() = teacher { api.injectSensors(mapOf("bms_state" to mapOf("soc" to 19))) }

    override fun tilt() = teacher {
        api.injectSensors(mapOf("imu_state" to mapOf("rpy" to listOf(20.0 * PI / 180.0, 0.0, 0.0))))
        delay(10_000)
        api.injectSensors(mapOf("imu_state" to mapOf("rpy" to listOf(0.0, 0.0, 0.0))))
    }

    override fun disconnect() = teacher {
        api.injectTelemetry(mapOf("connected" to false))
        delay(25_000)
        api.injectTelemetry(mapOf("connected" to true))
    }

    override fun failNextCommand() = teacher {
        api.nextRpcFailure(mapOf("method" to "LocoClient.SetTaskId", "code" to 3104))
    }

    override fun visitorLagging() = sim.visitorLagging()

    private fun teacher(block: suspend () -> Unit) {
        scope.launch { runCatching { block() } }
    }
}
