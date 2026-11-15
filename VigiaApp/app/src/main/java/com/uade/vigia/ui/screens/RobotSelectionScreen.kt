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
import com.uade.vigia.ui.VigiaUiState
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
fun RobotSelectionScreen(state: VigiaUiState, onConnect: (RobotInfo) -> Unit) {
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
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                Text("Fuente de datos:", color = Muted, fontSize = 13.sp)
                StatusChip("Simulador interno", Tone.INFO)
                StatusChip("Emulador de la cátedra · no disponible", Tone.NEUTRAL)
            }
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
