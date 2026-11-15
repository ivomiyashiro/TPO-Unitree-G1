package com.uade.vigia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uade.vigia.domain.model.AlertType
import com.uade.vigia.domain.model.CommandType
import com.uade.vigia.domain.model.EventKind
import com.uade.vigia.domain.model.MissionStatus
import com.uade.vigia.domain.model.RobotState
import com.uade.vigia.domain.usecase.Freshness
import com.uade.vigia.ui.VigiaUiState
import com.uade.vigia.ui.VigiaViewModel
import com.uade.vigia.ui.clock
import com.uade.vigia.ui.color
import com.uade.vigia.ui.components.Banner
import com.uade.vigia.ui.components.ButtonKind
import com.uade.vigia.ui.components.CardHeader
import com.uade.vigia.ui.components.ConfirmDialog
import com.uade.vigia.ui.components.DataTile
import com.uade.vigia.ui.components.EmptyState
import com.uade.vigia.ui.components.Hint
import com.uade.vigia.ui.components.StatusChip
import com.uade.vigia.ui.components.Tone
import com.uade.vigia.ui.components.VigiaButton
import com.uade.vigia.ui.components.VigiaCard
import com.uade.vigia.ui.es
import com.uade.vigia.ui.formatDuration
import com.uade.vigia.ui.tone
import com.uade.vigia.ui.theme.Ink
import com.uade.vigia.ui.theme.Muted
import com.uade.vigia.ui.theme.VigiaIcons
import java.time.Duration
import java.time.Instant

/** Escolta: en curso (T04/T05/T09–T11), en destino (T06) y finalizada (T07). */
@Composable
fun EscortScreen(state: VigiaUiState, vm: VigiaViewModel, wide: Boolean, onGoPanel: () -> Unit, onGoHistory: () -> Unit) {
    val t = state.telemetry
    val mission = t.mission
    when {
        mission == null -> ScreenColumn(wide) {
            EmptyState(
                "No hay una escolta en curso",
                "Registrá al visitante e iniciá la escolta desde el Panel. El robot tiene que estar en la base, en línea y con al menos 30 % de batería.",
            ) { VigiaButton("Ir al Panel", onGoPanel, Modifier.fillMaxWidth(), kind = ButtonKind.PRIMARY) }
            MapCard(state, routeTitle(state))
        }
        mission.status.isFinished -> EscortSummary(state, vm, wide, onGoPanel, onGoHistory)
        t.robotState == RobotState.AT_DESTINATION && state.freshness != Freshness.OFFLINE -> ArrivalView(state, vm, wide)
        else -> TwoPane(
            wide,
            main = {
                if (t.robotState == RobotState.WAITING_CROSSING && state.online) CrossingPrompt(state, vm)
                MapCard(state, routeTitle(state))
                EscortProgressCard(state)
            },
            side = { OrdersCard(state, vm) }
        )
    }
}

@Composable
private fun CrossingPrompt(state: VigiaUiState, vm: VigiaViewModel) {
    val crossingAlert = state.telemetry.activeAlerts.firstOrNull { it.type == AlertType.CROSSING_WAIT }
    Banner(
        "El robot espera antes del cruce de autoelevadores",
        "Avisó por voz al visitante que espere. Confirmá cuando el paso esté libre: sin tu confirmación el robot no cruza.",
        Tone.INFO
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OrderButton(state, CommandType.CONFIRM_CROSSING, "Cruce libre · continuar", vm, ButtonKind.PRIMARY, Modifier.weight(1f))
        VigiaButton(
            "Seguir esperando",
            { crossingAlert?.let { vm.acknowledge(it.id) } },
            Modifier.weight(1f),
            enabled = crossingAlert != null && state.online
        )
    }
}

@Composable
private fun ArrivalView(state: VigiaUiState, vm: VigiaViewModel, wide: Boolean) {
    val t = state.telemetry
    val m = t.mission ?: return
    var confirmHandshake by remember { mutableStateOf(false) }
    TwoPane(
        wide,
        main = {
            VigiaCard {
                Text("Llegada a la oficina de recepción", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("El robot avisó la llegada y despidió al visitante por voz. Está quieto en P4.", color = Ink, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OrderButton(state, CommandType.RETURN_TO_BASE, "Volver a la base", vm, ButtonKind.PRIMARY, Modifier.weight(1f))
                    VigiaButton(
                        if (state.isPending(CommandType.HANDSHAKE)) "Dar la mano · enviando…" else "Dar la mano · pide confirmación",
                        { confirmHandshake = true },
                        Modifier.weight(1f),
                        enabled = state.availability(CommandType.HANDSHAKE).enabled
                    )
                }
                Hint("Dar la mano solo se habilita con el robot quieto. Es un gesto de contacto: la app pide confirmarlo antes de enviarlo. Si nadie lo indica, el robot vuelve solo a la base en 45 s.")
            }
            MapCard(state, routeTitle(state))
        },
        side = {
            VigiaCard {
                CardHeader("Escolta ${m.id}") { StatusChip("En destino", Tone.OK) }
                val elapsed = Duration.between(m.startedAt, Instant.now()).seconds
                Text("Escolta ${formatDuration(elapsed)} · ${m.distanceM.toInt()} m", color = Ink, fontSize = 14.sp)
                ProgressBar(1f)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DataTile("Visitante", t.visitorState.label, "en P4", Modifier.weight(1f), t.visitorState.color(), compact = true)
                    DataTile("Normas", "${t.safetyGiven} de ${t.safetyPlanned}", "por voz", Modifier.weight(1f), compact = true)
                    DataTile("LED", t.led.label, t.led.meaning, Modifier.weight(1f), compact = true)
                }
            }
            InteractionCard(state)
        }
    )
    if (confirmHandshake) {
        ConfirmDialog(
            title = "¿Dar la mano al visitante?",
            body = "Es un gesto de contacto con un robot de 35 kg. Avisale al visitante antes: puede no aceptarlo. El robot está quieto en P4.",
            confirmLabel = "Dar la mano",
            onConfirm = { confirmHandshake = false; vm.command(CommandType.HANDSHAKE) },
            onDismiss = { confirmHandshake = false }
        )
    }
}

@Composable
private fun InteractionCard(state: VigiaUiState) {
    val items = listOf(CommandType.GREET, CommandType.SPEAK, CommandType.HANDSHAKE).mapNotNull { state.lastByType[it] }
    VigiaCard {
        Text("Interacción", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        if (items.isEmpty()) {
            Text("El robot saludó en P1 y despidió al visitante. Los gestos y mensajes que envíes desde Control aparecen acá con su estado.", color = Muted, fontSize = 13.sp)
        }
        items.forEach { r ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.command.description, color = Ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
                StatusChip(r.status.label, r.status.tone())
            }
        }
    }
}

/** Resumen de la escolta terminada (T07). */
@Composable
private fun EscortSummary(state: VigiaUiState, vm: VigiaViewModel, wide: Boolean, onGoPanel: () -> Unit, onGoHistory: () -> Unit) {
    val t = state.telemetry
    val m = t.mission ?: return
    val events = state.currentMissionEvents
    val alerts = events.count { it.kind == EventKind.ALERT }
    val commands = events.filter { it.kind == EventKind.COMMAND }
    val title = when (m.status) {
        MissionStatus.COMPLETED -> "Escolta ${m.id} completada"
        MissionStatus.CANCELLED -> "Escolta ${m.id} cancelada"
        else -> "Escolta ${m.id} abortada"
    }
    val tone = m.status.tone()
    val duration = Duration.between(m.startedAt, m.endedAt ?: Instant.now()).seconds
    TwoPane(
        wide,
        main = {
            VigiaCard(background = tone.card, border = tone.border) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(VigiaIcons.Check, null, tint = tone.fg, modifier = Modifier.size(24.dp))
                    Text("  $title", color = tone.fg, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DataTile("Duración", formatDuration(duration), m.status.label.lowercase(), Modifier.weight(1f))
                    DataTile("Recorrido", "${m.distanceM.es(0)} m", if (m.status == MissionStatus.COMPLETED) "ida y vuelta" else "hasta la orden", Modifier.weight(1f))
                    DataTile("Alertas", "$alerts", if (alerts == 1) "registrada" else "registradas", Modifier.weight(1f))
                    DataTile(
                        "Órdenes", "${commands.size}",
                        if (commands.none { it.tone.name == "ERROR" || it.tone.name == "WARNING" }) "todas confirmadas" else "con fallas",
                        Modifier.weight(1f)
                    )
                }
            }
            VigiaCard(spacing = 10) {
                Text("Eventos de la escolta", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                if (events.isEmpty()) Text("Cargando eventos guardados…", color = Muted, fontSize = 14.sp)
                events.forEach { EventRow(it.at.clock(), it.text, it.tone.color()) }
            }
        },
        side = {
            VigiaCard(spacing = 10) {
                Text("¿Qué sigue?", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                if (t.robotState != RobotState.IN_BASE) {
                    StatusChip(t.robotState.label, t.robotState.tone())
                    if (t.robotState == RobotState.AT_SAFE) {
                        OrderButton(state, CommandType.RETURN_TO_BASE, "Volver a la base", vm, ButtonKind.PRIMARY)
                    }
                }
                VigiaButton("Nueva escolta", onGoPanel, Modifier.fillMaxWidth(), kind = ButtonKind.PRIMARY, enabled = t.robotState == RobotState.IN_BASE)
                VigiaButton("Ver historial", onGoHistory, Modifier.fillMaxWidth())
                Hint("La escolta quedó guardada en el dispositivo con todos sus eventos y órdenes.")
            }
        }
    )
}

@Composable
fun EventRow(time: String, text: String, dot: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.Top) {
        Text(time, color = Muted, fontSize = 13.sp, modifier = Modifier.width(72.dp))
        Box(Modifier.padding(top = 5.dp).size(7.dp).clip(CircleShape).background(dot))
        Text(text, color = Ink, fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp).weight(1f))
    }
}
