package com.uade.vigia.data.remote

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.uade.vigia.domain.model.BusyAction
import com.uade.vigia.domain.model.CommandResponse
import com.uade.vigia.domain.model.LedColor
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import java.time.Instant
import kotlin.math.PI

/**
 * Endpoints del emulador de la cátedra G1 HMI Lab v0.7 (ver SDK_CONTRACTS.md del emulador).
 * Las respuestas se leen como JsonObject: solo usamos unos pocos campos de cada una.
 */
interface G1LabApi {
    @GET("api/robots/g1/telemetry")
    suspend fun telemetry(): JsonObject

    @POST("api/robots/g1/commands/greeting")
    suspend fun greeting(@Body body: Map<String, @JvmSuppressWildcards Any> = emptyMap()): Response<JsonObject>

    @POST("api/robots/g1/commands/handshake")
    suspend fun handshake(@Body body: Map<String, @JvmSuppressWildcards Any> = emptyMap()): Response<JsonObject>

    @POST("api/robots/g1/commands/release-arms")
    suspend fun releaseArms(): Response<JsonObject>

    @POST("api/robots/g1/audio/tts")
    suspend fun tts(@Body body: Map<String, @JvmSuppressWildcards Any>): Response<JsonObject>

    @PUT("api/robots/g1/led")
    suspend fun led(@Body body: Map<String, @JvmSuppressWildcards Any>): Response<JsonObject>

    @PUT("api/robots/g1/audio/volume")
    suspend fun volume(@Body body: Map<String, @JvmSuppressWildcards Any>): Response<JsonObject>

    // --- Consola docente: la usan los escenarios de prueba de la pantalla Control ---

    @PATCH("api/simulator/g1/sensors")
    suspend fun injectSensors(@Body body: Map<String, @JvmSuppressWildcards Any>): Response<JsonObject>

    @PATCH("api/simulator/g1/telemetry")
    suspend fun injectTelemetry(@Body body: Map<String, @JvmSuppressWildcards Any>): Response<JsonObject>

    @POST("api/simulator/g1/next-rpc-failure")
    suspend fun nextRpcFailure(@Body body: Map<String, @JvmSuppressWildcards Any>): Response<JsonObject>
}

/** Muestra de telemetría del emulador (la misma forma por REST y por WebSocket). */
data class EmulatorSample(
    val timestamp: Instant,
    val connected: Boolean,
    val valid: Boolean,
    val busy: BusyAction?,
    val soc: Int,
    val soh: Int,
    val rollDeg: Float,
    val pitchDeg: Float,
    val led: LedColor?,
    val volume: Int,
    val lastTts: String?
) {
    companion object {
        fun parse(text: String): EmulatorSample? = runCatching { from(JsonParser.parseString(text).asJsonObject) }.getOrNull()

        fun from(o: JsonObject): EmulatorSample {
            val sensors = o.getAsJsonObject("sensors")
            val bms = sensors.getAsJsonObject("bms_state")
            val rpy = sensors.getAsJsonObject("low_state").getAsJsonObject("imu_state").getAsJsonArray("rpy")
            val led = o.getAsJsonObject("led")
            val tts = o.get("last_tts")
            return EmulatorSample(
                timestamp = Instant.parse(o.get("timestamp").asString.replace("+00:00", "Z")),
                connected = o.get("connected").asBoolean,
                valid = sensors.get("valid").asBoolean,
                busy = if (o.get("mode").asString == "busy") busyFrom(o.get("action")?.takeIf { !it.isJsonNull }?.asString) else null,
                soc = bms.get("soc").asInt,
                soh = bms.get("soh").asInt,
                rollDeg = toDegrees(rpy[0].asDouble),
                pitchDeg = toDegrees(rpy[1].asDouble),
                led = ledFromRgb(led.get("r").asInt, led.get("g").asInt, led.get("b").asInt),
                volume = o.get("volume").asInt,
                lastTts = if (tts != null && tts.isJsonObject) tts.asJsonObject.get("text").asString else null
            )
        }

        private fun toDegrees(rad: Double) = (rad * 180.0 / PI).toFloat()

        private fun busyFrom(action: String?): BusyAction? = when (action) {
            "greeting" -> BusyAction.GREETING
            "handshake" -> BusyAction.HANDSHAKE
            null -> null
            else -> BusyAction.OTHER_GESTURE
        }
    }
}

private val ledRgb = mapOf(
    LedColor.GREEN to Triple(0, 255, 0),
    LedColor.BLUE to Triple(0, 0, 255),
    LedColor.AMBER to Triple(255, 160, 0),
    LedColor.RED to Triple(255, 0, 0)
)

fun LedColor.toRgbBody(): Map<String, Any> = ledRgb.getValue(this).let { (r, g, b) -> mapOf("r" to r, "g" to g, "b" to b) }

/** El emulador acepta cualquier RGB: se muestra el color del código más cercano. Apagado = sin dato. */
fun ledFromRgb(r: Int, g: Int, b: Int): LedColor? {
    if (r + g + b == 0) return null
    return ledRgb.minBy { (_, c) ->
        val (cr, cg, cb) = c
        (r - cr) * (r - cr) + (g - cg) * (g - cg) + (b - cb) * (b - cb)
    }.key
}

/** Traduce la respuesta HTTP del emulador al estado de orden de la app (Tabla XIII). */
fun Response<JsonObject>.toCommandResponse(): CommandResponse {
    val body = body()
    return when (code()) {
        in 200..299 -> {
            val accepted = body?.get("accepted")?.asBoolean ?: body?.get("ok")?.asBoolean ?: false
            val rpc = runCatching { body!!.getAsJsonObject("sdk").getAsJsonObject("rpc").get("rpc_code").asInt }.getOrDefault(0)
            if (accepted && rpc == 0) CommandResponse.confirmed()
            else CommandResponse.rejected("El robot rechazó la orden", rpc)
        }
        409 -> CommandResponse.rejected("Robot ocupado con un gesto", 409)
        503 -> CommandResponse.rejected("Sin conexión con el G1", 503)
        else -> CommandResponse.rejected("Error del emulador (HTTP ${code()})", code())
    }
}
