package com.uade.vigia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uade.vigia.domain.model.EventKind
import com.uade.vigia.domain.model.MissionSummary
import com.uade.vigia.ui.HistoryFilter
import com.uade.vigia.ui.VigiaUiState
import com.uade.vigia.ui.VigiaViewModel
import com.uade.vigia.ui.clock
import com.uade.vigia.ui.color
import com.uade.vigia.ui.components.EmptyState
import com.uade.vigia.ui.components.StatusChip
import com.uade.vigia.ui.components.Tone
import com.uade.vigia.ui.dayAndTime
import com.uade.vigia.ui.formatDuration
import com.uade.vigia.ui.tone
import com.uade.vigia.ui.theme.Border
import com.uade.vigia.ui.theme.Ink
import com.uade.vigia.ui.theme.Muted
import com.uade.vigia.ui.theme.NavyTint
import com.uade.vigia.ui.theme.NeutralFg
import com.uade.vigia.ui.theme.Surface
import com.uade.vigia.ui.theme.Tile
import java.time.Duration
import java.time.Instant

/** Historial guardado en el dispositivo (T08). Funciona sin conexión. */
@Composable
fun HistoryScreen(state: VigiaUiState, vm: VigiaViewModel, wide: Boolean) {
    val missions = when (state.historyFilter) {
        HistoryFilter.ALERTS -> state.missions.filter { it.alertCount > 0 }
        else -> state.missions
    }
    val events = state.selectedHistoryEvents.filter {
        when (state.historyFilter) {
            HistoryFilter.ALL -> true
            HistoryFilter.ESCORTS -> it.kind == EventKind.MISSION
            HistoryFilter.COMMANDS -> it.kind == EventKind.COMMAND
            HistoryFilter.ALERTS -> it.kind == EventKind.ALERT
        }
    }
    TwoPane(
        wide,
        main = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HistoryFilter.entries.forEach { f ->
                    val sel = f == state.historyFilter
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (sel) Tone.INFO.bg else Tone.NEUTRAL.bg)
                            .clickable(role = Role.Tab) { vm.setHistoryFilter(f) }
                            .semantics { selected = sel }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(if (sel) Tone.INFO.fg else NeutralFg))
                        Text(f.label, color = if (sel) Tone.INFO.fg else NeutralFg, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            if (missions.isEmpty()) {
                EmptyState(
                    if (state.missions.isEmpty()) "Todavía no hay escoltas registradas" else "No hay escoltas con alertas",
                    "Cada escolta queda guardada en el dispositivo con sus eventos, órdenes y alertas, aunque se corte la conexión."
                )
            } else {
                MissionTable(missions, state.selectedHistoryId, wide, vm::selectHistory)
            }
        },
        side = {
            val selected = state.missions.firstOrNull { it.id == state.selectedHistoryId }
            if (selected != null) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surface)
                        .border(1.dp, Border, RoundedCornerShape(12.dp)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Detalle · ${selected.id}", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("${selected.visitor} · ${selected.distanceM.toInt()} m", color = Muted, fontSize = 13.sp)
                    if (events.isEmpty()) Text("Sin eventos para este filtro.", color = Muted, fontSize = 14.sp)
                    events.forEach { EventRow(it.at.clock(), it.text, it.tone.color()) }
                }
            }
        }
    )
}

@Composable
private fun MissionTable(missions: List<MissionSummary>, selectedId: String?, wide: Boolean, onSelect: (String) -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(Surface).border(1.dp, Border, shape)) {
        Row(Modifier.fillMaxWidth().background(Tile).padding(horizontal = 14.dp, vertical = 12.dp)) {
            HeaderCell("Escolta", 1f)
            HeaderCell("Inicio", 1f)
            if (wide) HeaderCell("Duración", 1f)
            HeaderCell("Alertas", 0.8f)
            HeaderCell("Estado", 1.2f)
        }
        missions.forEach { m ->
            val sel = m.id == selectedId
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(if (sel) NavyTint else Surface)
                    .clickable { onSelect(m.id) }
                    .semantics { selected = sel }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(m.id, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(m.startedAt.dayAndTime(), color = Ink, fontSize = 13.sp, modifier = Modifier.weight(1f))
                if (wide) {
                    val secs = Duration.between(m.startedAt, m.endedAt ?: Instant.now()).seconds
                    Text(if (m.endedAt == null) "en curso" else formatDuration(secs), color = Ink, fontSize = 13.sp, modifier = Modifier.weight(1f))
                }
                Text("${m.alertCount}", color = Ink, fontSize = 13.sp, modifier = Modifier.weight(0.8f))
                Box(Modifier.weight(1.2f)) { StatusChip(m.status.label, m.status.tone()) }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.HeaderCell(text: String, weight: Float) {
    Text(text, color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(weight))
}
