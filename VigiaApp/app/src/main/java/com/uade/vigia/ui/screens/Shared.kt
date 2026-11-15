package com.uade.vigia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.uade.vigia.domain.model.CommandStatus
import com.uade.vigia.domain.model.CommandType
import com.uade.vigia.domain.model.RobotState
import com.uade.vigia.domain.usecase.Freshness
import com.uade.vigia.ui.VigiaUiState
import com.uade.vigia.ui.VigiaViewModel
import com.uade.vigia.ui.color
import com.uade.vigia.ui.components.ButtonKind
import com.uade.vigia.ui.components.CardHeader
import com.uade.vigia.ui.components.ConfirmDialog
import com.uade.vigia.ui.components.DataTile
import com.uade.vigia.ui.components.Hint
import com.uade.vigia.ui.components.HoldToConfirmButton
import com.uade.vigia.ui.components.RouteMapView
import com.uade.vigia.ui.components.StatusChip
import com.uade.vigia.ui.components.Tone
import com.uade.vigia.ui.components.VigiaButton
import com.uade.vigia.ui.components.VigiaCard
import com.uade.vigia.ui.es
import com.uade.vigia.ui.movementSummary
import com.uade.vigia.ui.tone
import com.uade.vigia.ui.theme.Border
import com.uade.vigia.ui.theme.Faint
import com.uade.vigia.ui.theme.Ink
import com.uade.vigia.ui.theme.Muted
import com.uade.vigia.ui.theme.Navy
import com.uade.vigia.ui.theme.Tile

/** Contenedor desplazable de cada pantalla. */
@Composable
fun ScreenColumn(wide: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(if (wide) 24.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content
    )
}

/** Columna principal + columna lateral de 380 dp en tablet; una sola columna en teléfono. */
@Composable
fun TwoPane(
    wide: Boolean,
    main: @Composable ColumnScope.() -> Unit,
    side: @Composable ColumnScope.() -> Unit
) {
    ScreenColumn(wide) {
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp), content = main)
                Column(Modifier.width(380.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = side)
            }
        } else {
            main()
            side()
        }
    }
}

val VigiaUiState.stale: Boolean get() = freshness != Freshness.FRESH

@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, stale: Boolean = false) {
    Box(modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Border)) {
        Box(
            Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight()
                .clip(RoundedCornerShape(4.dp)).background(if (stale) Faint else Navy)
        )
    }
}

@Composable
fun MapCard(state: VigiaUiState, title: String) {
    val t = state.telemetry
    VigiaCard {
        CardHeader(title) {
            if (state.stale) StatusChip("Dato antiguo · ${state.ageSeconds} s", Tone.NEUTRAL)
            else StatusChip("LED ${t.led.label.lowercase()} · ${t.led.meaning}", t.led.tone())
        }
        RouteMapView(t, state.stale)
    }
}

fun routeTitle(state: VigiaUiState): String {
    val t = state.telemetry
    if (state.freshness == Freshness.OFFLINE) return "Recorrido · dato antiguo"
    return when {
        !t.hasMissionInProgress -> "Recorrido"
        t.robotState == RobotState.ON_MISSION && t.isMoving -> "Recorrido · punto ${t.checkpoint.label} de P4"
        else -> "Recorrido · en ${t.checkpoint.label}"
    }
}

/** Escolta en curso: punto, avance, visitante, normas y LED (T04/T05). */
@Composable
fun EscortProgressCard(state: VigiaUiState) {
    val t = state.telemetry
    val m = t.mission ?: return
    val offline = state.freshness == Freshness.OFFLINE
    VigiaCard {
        CardHeader("Escolta ${m.id}") {
            when {
                offline -> StatusChip("Sin datos", Tone.NEUTRAL)
                t.robotState == RobotState.WAITING_CROSSING -> StatusChip("En espera", Tone.WARNING)
                else -> StatusChip(m.status.label, m.status.tone())
            }
        }
        Text(
            if (offline) "Último dato: ${t.movementSummary().replaceFirstChar { it.lowercase() }}" else t.movementSummary(),
            color = if (offline) Faint else Ink, fontSize = 14.sp
        )
        ProgressBar(m.progressPct / 100f, stale = offline)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val sub = if (offline) "hace ${state.ageSeconds} s" else null
            DataTile("Visitante", t.visitorState.label, sub ?: "a ${t.visitorDistanceM.es()} m", Modifier.weight(1f), t.visitorState.color(), offline)
            DataTile("Normas dichas", "${t.safetyGiven} de ${t.safetyPlanned}", "mensajes de voz", Modifier.weight(1f), stale = offline)
            DataTile("LED", t.led.label, sub ?: t.led.hint, Modifier.weight(1f), stale = offline)
        }
    }
}

/** Fila "Última orden" con su estado (separado del estado del robot). */
@Composable
fun LastCommandRow(state: VigiaUiState, types: Set<CommandType>? = null) {
    val record = state.lastCommand?.takeIf { types == null || it.command.type in types } ?: return
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Tile).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Última orden", color = Muted, fontSize = 12.sp)
            Text(record.command.description, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        StatusChip(record.status.label, record.status.tone())
    }
    val hint = when (record.status) {
        CommandStatus.PENDING -> "Esperando respuesta del robot (máx. 5 s). El botón queda bloqueado para no enviar la orden dos veces."
        CommandStatus.CONFIRMED -> "Confirmada = el robot aceptó la orden. Lo que hace realmente se ve en el recorrido y la telemetría."
        CommandStatus.REJECTED -> "Rechazada: ${record.reason} (código ${record.rpcCode})."
        CommandStatus.NO_RESPONSE -> "La orden no fue confirmada y no se reenvía sola. Detener desde la app no funciona sin conexión: no reemplaza una parada de emergencia física."
    }
    Hint(hint, color = if (record.status == CommandStatus.NO_RESPONSE || record.status == CommandStatus.REJECTED) Tone.ERROR.fg else Muted)
}

/** Barra de órdenes de la escolta: Pausar/Reanudar, punto seguro, cancelar y detener. */
@Composable
fun OrdersCard(state: VigiaUiState, vm: VigiaViewModel) {
    var confirmCancel by remember { mutableStateOf(false) }
    val paused = state.telemetry.robotState == RobotState.PAUSED || state.telemetry.robotState == RobotState.STOPPED
    VigiaCard(spacing = 10) {
        Text("Órdenes", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        if (paused) {
            OrderButton(state, CommandType.RESUME, "Reanudar", vm, ButtonKind.PRIMARY)
        } else {
            OrderButton(state, CommandType.PAUSE, "Pausar", vm)
        }
        OrderButton(state, CommandType.GO_SAFE, "Ir a punto seguro", vm)
        val cancel = state.availability(CommandType.CANCEL)
        VigiaButton("Cancelar escolta", { confirmCancel = true }, Modifier.fillMaxWidth(), enabled = cancel.enabled)
        val stop = state.availability(CommandType.STOP)
        HoldToConfirmButton(
            if (state.online) "Detener · mantener 2 s" else "Detener · sin conexión",
            enabled = stop.enabled,
            onConfirmed = { vm.command(CommandType.STOP) }
        )
        LastCommandRow(state)
    }
    if (confirmCancel) {
        ConfirmDialog(
            title = "¿Cancelar la escolta?",
            body = "El robot deja de acompañar al visitante y vuelve a la base. Vas a tener que completar vos el acompañamiento.",
            confirmLabel = "Cancelar escolta",
            danger = true,
            onConfirm = { confirmCancel = false; vm.command(CommandType.CANCEL) },
            onDismiss = { confirmCancel = false }
        )
    }
}

@Composable
fun OrderButton(
    state: VigiaUiState,
    type: CommandType,
    label: String,
    vm: VigiaViewModel,
    kind: ButtonKind = ButtonKind.SECONDARY,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    val pending = state.isPending(type)
    VigiaButton(
        if (pending) "$label · enviando…" else label,
        { vm.command(type) },
        modifier,
        kind = kind,
        enabled = state.availability(type).enabled
    )
}
