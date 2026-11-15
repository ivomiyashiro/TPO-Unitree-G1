package com.uade.vigia.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uade.vigia.domain.model.Thresholds
import com.uade.vigia.ui.VigiaUiState
import com.uade.vigia.ui.clock
import com.uade.vigia.ui.color
import com.uade.vigia.ui.components.DataTile
import com.uade.vigia.ui.components.Hint
import com.uade.vigia.ui.components.Tone
import com.uade.vigia.ui.components.VigiaCard
import com.uade.vigia.ui.es
import com.uade.vigia.ui.theme.Ink

/** Telemetría completa (T12): todos los datos de la Tabla IX con su antigüedad. */
@Composable
fun TelemetryScreen(state: VigiaUiState, wide: Boolean) {
    val t = state.telemetry
    val stale = state.stale
    val m = t.mission
    val tiles = listOf(
        Triple("Conexión", if (state.online) "En línea" else "Sin conexión", if (state.online) "latencia ${t.latencyMs} ms" else "hace ${state.ageSeconds} s"),
        Triple("Batería (SOC)", "${t.batteryPct} %", "≈ ${t.minutesLeft} min"),
        Triple("Salud batería (SOH)", "${t.soh} %", "BMS"),
        Triple("Estado", t.robotState.label, t.busyAction?.label ?: t.robotState.code),
        Triple("Misión", m?.id ?: "Sin misión", m?.let { "${it.progressPct} % · ${it.status.label.lowercase()}" } ?: "en la base"),
        Triple("Ubicación", t.checkpoint.label, "x ${t.pose.xM.es()} · y ${t.pose.yM.es()} m"),
        Triple("Movimiento", if (t.isMoving) "Caminando" else "Quieto", "${t.speedMps.es()} m/s"),
        Triple("Inclinación", "${t.rollDeg.es()}° / ${t.pitchDeg.es()}°", "roll / pitch"),
        Triple("Alertas activas", "${t.activeAlerts.size}", "sin reconocer")
    )
    TwoPane(
        wide,
        main = {
            VigiaCard {
                Text("Telemetría · actualizada cada 1 s", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                tiles.chunked(if (wide) 3 else 2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { (label, value, detail) ->
                            val warn = (label == "Inclinación" && t.tiltDeg > Thresholds.TILT_CRITICAL_DEG) ||
                                (label == "Batería (SOC)" && t.soc < Thresholds.BATTERY_WARNING)
                            DataTile(
                                label, value, detail, Modifier.weight(1f),
                                valueColor = if (warn) Tone.ERROR.fg else Ink,
                                stale = stale && label != "Conexión"
                            )
                        }
                        repeat((if (wide) 3 else 2) - row.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        },
        side = {
            VigiaCard {
                Text("Variables del servicio", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                DataTile("Seguimiento del visitante", t.visitorState.label, if (t.visitorState == com.uade.vigia.domain.model.VisitorState.NONE) "sin escolta" else "a ${t.visitorDistanceM.es()} m", Modifier.fillMaxWidth(), t.visitorState.color(), stale)
                DataTile("Normas entregadas", "${t.safetyGiven} de ${t.safetyPlanned}", "mensajes de voz", Modifier.fillMaxWidth(), stale = stale)
                DataTile("Señal LED", t.led.label, t.led.meaning, Modifier.fillMaxWidth(), stale = stale)
            }
            VigiaCard {
                Hint(
                    "Última muestra: ${t.timestamp.clock()} · hace ${state.ageSeconds} s. Si pasan más de 3 s sin datos se marcan " +
                        "como antiguos; con más de 10 s la app pasa a sin conexión." +
                        (t.lastTts?.let { "\nÚltimo mensaje de voz: “$it”" } ?: "")
                )
            }
        }
    )
}
