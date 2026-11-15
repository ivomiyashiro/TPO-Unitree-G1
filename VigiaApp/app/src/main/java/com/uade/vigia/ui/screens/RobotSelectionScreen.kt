package com.uade.vigia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uade.vigia.domain.model.RobotInfo
import com.uade.vigia.domain.repository.DataSourceKind
import com.uade.vigia.ui.VigiaUiState
import com.uade.vigia.ui.VigiaViewModel
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import com.uade.vigia.ui.theme.Tile
import com.uade.vigia.ui.batteryTone
import com.uade.vigia.ui.components.ButtonKind
import com.uade.vigia.ui.components.Hint
import com.uade.vigia.ui.components.StatusChip
import com.uade.vigia.ui.components.Tone
import com.uade.vigia.ui.components.VigiaButton
import com.uade.vigia.ui.tone
import com.uade.vigia.ui.theme.Border
import com.uade.vigia.ui.theme.Canvas
import com.uade.vigia.ui.theme.Ink
import com.uade.vigia.ui.theme.Muted
import com.uade.vigia.ui.theme.Navy
import com.uade.vigia.ui.theme.Surface

/** Selección de robot (T01). Solo se puede conectar a un robot en línea. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RobotSelectionScreen(state: VigiaUiState, vm: VigiaViewModel, onConnect: (RobotInfo) -> Unit) {
    Box(Modifier.fillMaxSize().background(Canvas).safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 56.dp, bottom = 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Navy), contentAlignment = Alignment.Center) {
                    Text("V", color = Surface, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.padding(start = 12.dp)) {
                    Text("Vigía", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Consola de operación · G1", color = Muted, fontSize = 12.sp)
                }
            }
            Text("Elegí el robot a operar", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            DataSourcePicker(state, vm)
            state.robots.forEach { robot -> RobotCard(robot, state, onConnect) }
            Hint("Solo se puede conectar a un robot en línea. La conexión no le da órdenes al robot: es solo para supervisarlo.")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RobotCard(robot: RobotInfo, state: VigiaUiState, onConnect: (RobotInfo) -> Unit) {
    val t = state.telemetry
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .border(1.dp, Border, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(robot.name, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(robot.site, color = Muted, fontSize = 12.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                if (robot.available) {
                    StatusChip("En línea", Tone.OK)
                    StatusChip("Batería ${t.batteryPct} %", batteryTone(t.soc))
                    StatusChip(t.robotState.label, t.robotState.tone())
                } else {
                    StatusChip("Sin conexión", Tone.ERROR)
                    StatusChip(robot.lastSeenLabel ?: "Sin datos", Tone.NEUTRAL)
                }
            }
        }
        VigiaButton(
            if (robot.available) "Conectar" else "No disponible",
            { onConnect(robot) },
            Modifier.width(156.dp),
            kind = ButtonKind.PRIMARY,
            enabled = robot.available
        )
    }
}

/**
 * Fuente de datos: el simulador interno (sin red) o el emulador de la cátedra (G1 HMI Lab).
 * La app es la misma; solo cambia de dónde salen batería, inclinación, conexión, LED, voz y gestos.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DataSourcePicker(state: VigiaUiState, vm: VigiaViewModel) {
    val kind = state.dataSource.kind
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            Text("Fuente de datos:", color = Muted, fontSize = 13.sp)
            DataSourceKind.entries.forEach { k ->
                val selected = k == kind
                Box(
                    Modifier.clip(RoundedCornerShape(14.dp)).clickable(role = Role.RadioButton) {
                        if (k == DataSourceKind.SIMULATOR) vm.useSimulator() else vm.useEmulator()
                    }
                ) { StatusChip(if (selected) k.label else "${k.label}", if (selected) Tone.INFO else Tone.NEUTRAL) }
            }
        }
        if (kind == DataSourceKind.EMULATOR || state.sourceError != null || state.connectingSource) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(Tile)
                        .border(1.dp, Border, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 12.dp)
                ) {
                    BasicTextField(
                        value = state.emulatorUrlDraft,
                        onValueChange = vm::updateEmulatorUrl,
                        singleLine = true,
                        textStyle = TextStyle(color = Ink, fontSize = 14.sp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                VigiaButton(if (state.connectingSource) "Probando…" else "Conectar al emulador", vm::useEmulator, Modifier.width(200.dp), enabled = !state.connectingSource)
            }
            when {
                state.sourceError != null -> Hint(state.sourceError, color = Tone.ERROR.fg)
                kind == DataSourceKind.EMULATOR -> Hint("Conectado a ${state.dataSource.emulatorUrl}. Batería, inclinación, conexión, LED, voz y gestos vienen del emulador; el recorrido lo simula la app.")
                else -> Hint("Desde el emulador de Android, 10.0.2.2 es tu computadora. En una tablet real, usá la IP de la computadora en la red local.")
            }
        }
    }
}
