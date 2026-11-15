package com.uade.vigia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uade.vigia.domain.model.Alert
import com.uade.vigia.domain.model.AlertSeverity
import com.uade.vigia.domain.model.AlertType
import com.uade.vigia.domain.model.CommandType
import com.uade.vigia.ui.VigiaUiState
import com.uade.vigia.ui.VigiaViewModel
import com.uade.vigia.ui.clock
import com.uade.vigia.ui.components.ButtonKind
import com.uade.vigia.ui.components.CardHeader
import com.uade.vigia.ui.components.EmptyState
import com.uade.vigia.ui.components.StatusChip
import com.uade.vigia.ui.components.Tone
import com.uade.vigia.ui.components.VigiaButton
import com.uade.vigia.ui.components.VigiaCard
import com.uade.vigia.ui.theme.Ink
import com.uade.vigia.ui.theme.Muted
import com.uade.vigia.ui.theme.Tile

fun severityTone(s: AlertSeverity): Tone = when (s) {
    AlertSeverity.INFO -> Tone.INFO
    AlertSeverity.WARNING -> Tone.WARNING
    AlertSeverity.CRITICAL -> Tone.ERROR
}

/** Alertas activas con la acción sugerida, y las reconocidas hoy (T13). */
@Composable
fun AlertsScreen(state: VigiaUiState, vm: VigiaViewModel, wide: Boolean) {
    val active = state.telemetry.activeAlerts.sortedByDescending { it.severity.ordinal }
    val acknowledged = state.telemetry.acknowledgedAlerts.sortedByDescending { it.acknowledgedAt }
    TwoPane(
        wide,
        main = {
            Text("Activas", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (active.isEmpty()) {
                EmptyState("Sin alertas activas", "Cuando el robot detecte batería baja, un visitante rezagado, una inclinación anormal o una orden rechazada, va a aparecer acá con la acción sugerida.")
            }
            active.forEach { AlertCard(it, state, vm) }
        },
        side = {
            VigiaCard(spacing = 8) {
                Text("Reconocidas hoy", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                if (acknowledged.isEmpty()) Text("Todavía no hay alertas reconocidas.", color = Muted, fontSize = 14.sp)
                acknowledged.forEach { a ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Tile).padding(10.dp)) {
                        Text(a.title, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("Reconocida ${a.acknowledgedAt?.clock()} · ${a.acknowledgedBy}", color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlertCard(alert: Alert, state: VigiaUiState, vm: VigiaViewModel) {
    val tone = severityTone(alert.severity)
    VigiaCard(background = tone.card, border = tone.border) {
        CardHeader(alert.title) { StatusChip(alert.severity.label, tone) }
        Text("${alert.detail} ${alert.raisedAt.clock()} · ${alert.checkpoint.label}", color = Ink, fontSize = 13.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val mod = Modifier.widthIn(min = 128.dp)
            when (alert.type) {
                AlertType.BATTERY_LOW, AlertType.BATTERY_CRITICAL ->
                    if (state.availability(CommandType.GO_SAFE).enabled)
                        OrderButton(state, CommandType.GO_SAFE, "Ir a punto seguro", vm, ButtonKind.WARNING, mod)
                    else if (state.telemetry.robotState != com.uade.vigia.domain.model.RobotState.IN_BASE &&
                        state.availability(CommandType.RETURN_TO_BASE).enabled)
                        OrderButton(state, CommandType.RETURN_TO_BASE, "Volver a la base", vm, ButtonKind.WARNING, mod)
                AlertType.VISITOR_LAGGING -> {
                    VigiaButton("Seguir esperando", { vm.acknowledge(alert.id) }, mod, enabled = state.online)
                    OrderButton(state, CommandType.RESUME, "Reanudar", vm, modifier = mod)
                    OrderButton(state, CommandType.CANCEL, "Cancelar escolta", vm, modifier = mod)
                }
                AlertType.CROSSING_WAIT ->
                    OrderButton(state, CommandType.CONFIRM_CROSSING, "Cruce libre · continuar", vm, ButtonKind.PRIMARY, mod)
                // Incidente: el robot ya se detuvo; el guardia decide cómo seguir (Tabla IV del documento).
                AlertType.TILT -> {
                    OrderButton(state, CommandType.GO_SAFE, "Ir a punto seguro", vm, ButtonKind.WARNING, mod)
                    OrderButton(state, CommandType.RESUME, "Reanudar · área despejada", vm, modifier = mod)
                    OrderButton(state, CommandType.CANCEL, "Cancelar escolta", vm, modifier = mod)
                }
                AlertType.COMMAND_REJECTED -> Unit
            }
            VigiaButton(
                if (state.isPending(CommandType.ACK_ALERT)) "Reconociendo…" else "Reconocer",
                { vm.acknowledge(alert.id) }, mod,
                enabled = state.online && !state.isPending(CommandType.ACK_ALERT)
            )
        }
    }
}
