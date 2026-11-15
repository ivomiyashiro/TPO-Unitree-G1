package com.uade.vigia.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uade.vigia.domain.model.CommandType
import com.uade.vigia.domain.model.Telemetry
import com.uade.vigia.ui.VigiaUiState
import com.uade.vigia.ui.VigiaViewModel
import com.uade.vigia.ui.components.ButtonKind
import com.uade.vigia.ui.components.CardHeader
import com.uade.vigia.ui.components.ConfirmDialog
import com.uade.vigia.ui.components.DataTile
import com.uade.vigia.ui.components.Hint
import com.uade.vigia.ui.components.Tone
import com.uade.vigia.ui.components.VigiaButton
import com.uade.vigia.ui.components.VigiaCard
import com.uade.vigia.ui.es
import com.uade.vigia.ui.theme.Border
import com.uade.vigia.ui.theme.Ink
import com.uade.vigia.ui.theme.Muted
import com.uade.vigia.ui.theme.Navy
import com.uade.vigia.ui.theme.OkFg
import com.uade.vigia.ui.theme.Tile
import com.uade.vigia.ui.theme.VigiaIcons

/** Panel de supervisión (T02 / T03). */
@Composable
fun PanelScreen(
    state: VigiaUiState,
    vm: VigiaViewModel,
    wide: Boolean,
    onOpenTelemetry: () -> Unit,
    onOpenEscort: () -> Unit,
    onOpenAlerts: () -> Unit
) {
    var confirmStart by remember { mutableStateOf(false) }
    val t = state.telemetry
    TwoPane(
        wide,
        main = {
            MapCard(state, routeTitle(state))
            if (t.hasMissionInProgress) {
                EscortProgressCard(state)
                VigiaButton("Ver escolta y órdenes", onOpenEscort, Modifier.fillMaxWidth(), kind = ButtonKind.PRIMARY)
            } else {
                NewEscortCard(state, vm, onStart = { confirmStart = true })
            }
        },
        side = {
            TelemetrySummaryCard(state, onOpenTelemetry)
            AlertsSummaryCard(state, onOpenAlerts)
        }
    )
    if (confirmStart) {
        ConfirmDialog(
            title = "¿Iniciar la escolta?",
            body = "El robot va a ir a P1, saludar y dar las 4 normas de seguridad. Después camina hasta la oficina de recepción. " +
                "Podés pausarlo o detenerlo en cualquier momento.",
            confirmLabel = "Iniciar escolta",
            onConfirm = {
                confirmStart = false
                vm.startEscort()
                onOpenEscort()
            },
            onDismiss = { confirmStart = false }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(VigiaIcons.Check, null, tint = OkFg, modifier = Modifier.size(18.dp))
                Text("  Visitante con chaleco reflectivo · verificado por el guardia", color = OkFg, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun NewEscortCard(state: VigiaUiState, vm: VigiaViewModel, onStart: () -> Unit) {
    val form = state.form
    val availability = state.availability(CommandType.START_ESCORT)
    VigiaCard(spacing = 8) {
        Text("Nueva escolta", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text("Visitante", color = Muted, fontSize = 12.sp)
        FieldBox {
            BasicTextField(
                value = form.visitor,
                onValueChange = { vm.updateForm(form.copy(visitor = it.take(40))) },
                singleLine = true,
                textStyle = TextStyle(color = Ink, fontSize = 15.sp),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Hint("Solo una etiqueta (empresa o motivo). No se guardan datos personales del visitante.")
        Text("Destino", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        FieldBox { Text(form.destination, color = Ink, fontSize = 15.sp) }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Checkbox) { vm.updateForm(form.copy(vestVerified = !form.vestVerified)) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = form.vestVerified,
                onCheckedChange = { vm.updateForm(form.copy(vestVerified = it)) },
                colors = CheckboxDefaults.colors(checkedColor = OkFg, uncheckedColor = Muted)
            )
            Text("El visitante tiene chaleco reflectivo", color = Ink, fontSize = 14.sp)
        }
        VigiaButton("Iniciar escolta", onStart, Modifier.fillMaxWidth(), kind = ButtonKind.PRIMARY, enabled = state.canStartEscort)
        if (availability.enabled) {
            Hint("Se habilita cuando el robot está en la base, en línea, con 30 % de batería o más y el chaleco verificado.")
        } else {
            Hint("No se puede iniciar: ${availability.condition.replaceFirstChar { it.lowercase() }}.", color = Tone.WARNING.fg)
        }
    }
}

@Composable
private fun FieldBox(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Tile)
            .border(1.dp, Border, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) { content() }
}

@Composable
private fun TelemetrySummaryCard(state: VigiaUiState, onOpen: () -> Unit) {
    val t: Telemetry = state.telemetry
    val stale = state.stale
    VigiaCard {
        CardHeader("Telemetría") {
            Text(
                "Ver todo ›", color = Navy, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(role = Role.Button, onClick = onOpen).padding(6.dp)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DataTile("Batería", "${t.batteryPct} %", "Salud ${t.soh} %", Modifier.weight(1f), stale = stale)
            DataTile(
                "Conexión", if (state.online) "En línea" else "Sin conexión",
                if (state.online) "${t.latencyMs} ms" else "hace ${state.ageSeconds} s", Modifier.weight(1f),
                valueColor = if (state.online) Ink else Tone.ERROR.fg
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DataTile("Movimiento", if (t.isMoving) "Caminando" else "Quieto", "${t.speedMps.es()} m/s", Modifier.weight(1f), stale = stale)
            DataTile("Inclinación", "${t.tiltDeg.es()}°", "roll y pitch", Modifier.weight(1f), stale = stale)
        }
    }
}

@Composable
private fun AlertsSummaryCard(state: VigiaUiState, onOpen: () -> Unit) {
    val active = state.telemetry.activeAlerts
    VigiaCard {
        CardHeader("Alertas") {
            if (active.isNotEmpty()) {
                Text(
                    "Ver alertas ›", color = Navy, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(role = Role.Button, onClick = onOpen).padding(6.dp)
                )
            }
        }
        if (active.isEmpty()) {
            Text("Sin alertas activas.", color = Muted, fontSize = 14.sp)
        } else {
            active.take(3).forEach { a ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(severityTone(a.severity).card).padding(10.dp)) {
                    Text(a.title, color = severityTone(a.severity).fg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(a.severity.label, color = Muted, fontSize = 12.sp)
                }
            }
        }
    }
}
