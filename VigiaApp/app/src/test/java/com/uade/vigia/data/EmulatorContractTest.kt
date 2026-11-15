package com.uade.vigia.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.uade.vigia.data.remote.EmulatorSample
import com.uade.vigia.data.remote.ledFromRgb
import com.uade.vigia.data.remote.toCommandResponse
import com.uade.vigia.domain.model.BusyAction
import com.uade.vigia.domain.model.CommandStatus
import com.uade.vigia.domain.model.LedColor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Response

/** Contrato con el emulador G1 HMI Lab v0.7: respuestas copiadas de una ejecución real. */
class EmulatorContractTest {

    private val sample = """
        {"type":"telemetry","robot_id":"g1","timestamp":"2026-11-15T12:35:28.754327+00:00","sample_seq":1,
         "connected":false,"mode":"busy","action":"greeting","battery_percent":19,"temperature_c":32.0,
         "led":{"r":0,"g":0,"b":255},"volume":70,"last_tts":{"text":"hola","speaker_id":0,"index":0},
         "sensors":{"source":"teacher_injected","valid":false,
           "bms_state":{"soc":19,"soh":91,"current":0,"bmsvoltage":[0,0,0],"temperature":[0,0,0,0,0,0,0,0,0,0,0,0]},
           "low_state":{"mode_pr":0,"mode_machine":0,"tick":0,"imu_state":{"rpy":[0.349,-0.1,0.0],
             "quaternion":[1,0,0,0],"gyroscope":[0,0,0],"accelerometer":[0,0,9.81]}},
           "sensor_seq":2,"sensor_updated_at":"2026-11-15T12:35:28.753999+00:00"}}
    """.trimIndent()

    @Test fun `lee batería, inclinación en grados, LED, gesto y validez`() {
        val e = EmulatorSample.parse(sample)!!
        assertFalse(e.connected)
        assertFalse(e.valid)
        assertEquals(19, e.soc)
        assertEquals(91, e.soh)
        assertEquals(20f, e.rollDeg, 0.1f)
        assertEquals(LedColor.BLUE, e.led)
        assertEquals(BusyAction.GREETING, e.busy)
        assertEquals("hola", e.lastTts)
    }

    @Test fun `LED apagado no se interpreta como un color`() {
        assertNull(ledFromRgb(0, 0, 0))
        assertEquals(LedColor.AMBER, ledFromRgb(250, 150, 10))
    }

    private fun response(code: Int, body: String): Response<JsonObject> =
        if (code in 200..299) Response.success(code, JsonParser.parseString(body).asJsonObject)
        else Response.error(code, body.toResponseBody("application/json".toMediaType()))

    @Test fun `202 aceptada es confirmada y accepted false es rechazada con el código RPC`() {
        assertEquals(CommandStatus.CONFIRMED, response(202, """{"accepted":true,"sdk":{"rpc":{"rpc_code":0}}}""").toCommandResponse().status)
        val rejected = response(202, """{"accepted":false,"sdk":{"rpc":{"rpc_code":3104}}}""").toCommandResponse()
        assertEquals(CommandStatus.REJECTED, rejected.status)
        assertEquals(3104, rejected.rpcCode)
    }

    @Test fun `409 ocupado y 503 sin conexión son rechazos con motivo`() {
        assertEquals(409, response(409, """{"detail":"ocupado"}""").toCommandResponse().rpcCode)
        assertEquals("Sin conexión con el G1", response(503, """{"detail":"x"}""").toCommandResponse().reason)
    }
}
