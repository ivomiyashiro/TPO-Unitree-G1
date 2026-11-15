package com.uade.vigia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uade.vigia.domain.model.SafetyMessages
import com.uade.vigia.domain.model.CommandType
import com.uade.vigia.domain.model.LedColor
import com.uade.vigia.domain.usecase.CommandRules
import com.uade.vigia.ui.VigiaUiState
import com.uade.vigia.ui.VigiaViewModel
import com.uade.vigia.ui.components.ButtonKind
import com.uade.vigia.ui.components.CardHeader
import com.uade.vigia.ui.components.ConfirmDialog
import com.uade.vigia.ui.components.Hint
import com.uade.vigia.ui.components.HoldToConfirmButton
import com.uade.vigia.ui.components.StatusChip
import com.uade.vigia.ui.components.Tone
import com.uade.vigia.ui.components.VigiaButton
import com.uade.vigia.ui.components.VigiaCard
import com.uade.vigia.ui.tone
import com.uade.vigia.ui.theme.Border
import com.uade.vigia.ui.theme.Ink
import com.uade.vigia.ui.theme.Muted
import com.uade.vigia.ui.theme.Navy
import com.uade.vigia.ui.theme.Tile
import com.uade.vigia.ui.theme.toColor

private val voiceMessages = listOf(
    "Bienvenida" to "Bienvenido al centro logístico. Voy a acompañarlo hasta la oficina de recepción.",
    "Normas de seguridad" to SafetyMessages.all.joinToString(" "),
    "Espere: cruce" to "Espere. Vamos a cruzar la calle de autoelevadores cuando el paso esté libre.",
    "Llegamos a destino" to "Llegamos a destino. Gracias por su visita."
)

/** Control e interacción (T14): órdenes de misión y de interacción con su estado. */
@Composable
fun ControlScreen(state: VigiaUiState, vm: VigiaViewModel, wide: Boolean, onChangeRobot: () -> Unit) {
    var confirmCancel by remember { mutableStateOf(false) }
    var confirmHandshake by remember { mutableStateOf(false) }
    TwoPane(
        wide,
        main = {
            VigiaCard(spacing = 10) {
                CardHeader("Órdenes de misión") {
                    state.telemetry.mission?.let { StatusChip("Escolta ${it.status.label.lowercase()}", it.status.tone()) }
                }
                MissionOrderRow(state, CommandType.START_ESCORT, "Iniciar escolta", wide, onClick = null)
                MissionOrderRow(state, CommandType.PAUSE, "Pausar", wide) { vm.command(CommandType.PAUSE) }
                MissionOrderRow(state, CommandType.RESUME, "Reanudar", wide) { vm.command(CommandType.RESUME) }
                MissionOrderRow(state, CommandType.CANCEL, "Cancelar escolta", wide) { confirmCancel = true }
                MissionOrderRow(state, CommandType.GO_SAFE, "Ir a punto seguro", wide) { vm.command(CommandType.GO_SAFE) }
                MissionOrderRow(state, CommandType.RETURN_TO_BASE, "Volver a la base", wide) { vm.command(CommandType.RETURN_TO_BASE) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HoldToConfirmButton("Detener · mantener 2 s", state.availability(CommandType.STOP).enabled, { vm.command(CommandType.STOP) }, Modifier.width(if (wide) 176.dp else 150.dp))
                    Text(CommandRules.condition(CommandType.STOP), color = Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    StatusOf(state, CommandType.STOP)
                }
                Hint(
                    "Cada orden muestra su estado: pendiente, confirmada, rechazada o sin respuesta. Confirmada significa aceptada por el robot, no cumplida. " +
                        "Detener es un comando de software simulado: no reemplaza la parada de emergencia física."
                )
            }
            ScenariosCard(vm)
        },
        side = {
            VigiaCard(spacing = 10) {
                Text("Gestos", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                GestureRow(state, CommandType.GREET, "Saludar") { vm.command(CommandType.GREET) }
                GestureRow(state, CommandType.HANDSHAKE, "Dar la mano") { confirmHandshake = true }
                GestureRow(state, CommandType.RELEASE_ARMS, "Liberar brazos") { vm.command(CommandType.RELEASE_ARMS) }
                Hint("Dar la mano pide confirmación y solo funciona con el robot quieto en P4.")
            }
            VoiceCard(state, vm)
            LedCard(state, vm)
            VigiaButton("Cambiar de robot", onChangeRobot, Modifier.fillMaxWidth())
        }
    )
    if (confirmCancel) {
        ConfirmDialog(
            "¿Cancelar la escolta?", "El robot deja de acompañar al visitante y vuelve a la base.", "Cancelar escolta",
            onConfirm = { confirmCancel = false; vm.command(CommandType.CANCEL) }, onDismiss = { confirmCancel = false }, danger = true
        )
    }
    if (confirmHandshake) {
        ConfirmDialog(
            "¿Dar la mano al visitante?", "Es un gesto de contacto. Avisale al visitante antes: puede no aceptarlo.", "Dar la mano",
            onConfirm = { confirmHandshake = false; vm.command(CommandType.HANDSHAKE) }, onDismiss = { confirmHandshake = false }
        )
    }
}

@Composable
private fun MissionOrderRow(state: VigiaUiState, type: CommandType, label: String, wide: Boolean, onClick: (() -> Unit)?) {
    val availability = state.availability(type)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        VigiaButton(
            if (state.isPending(type)) "$label · enviando…" else label,
            { onClick?.invoke() },
            Modifier.width(if (wide) 176.dp else 150.dp),
            enabled = availability.enabled && onClick != null
        )
        Text(
            if (onClick == null && availability.enabled) "Desde el Panel, con el chaleco verificado" else availability.condition,
            color = Muted, fontSize = 13.sp, modifier = Modifier.weight(1f)
        )
        StatusOf(state, type)
    }
}

@Composable
private fun StatusOf(state: VigiaUiState, type: CommandType) {
    state.lastByType[type]?.let { StatusChip(it.status.label, it.status.tone()) }
}

@Composable
private fun GestureRow(state: VigiaUiState, type: CommandType, label: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        VigiaButton(
            if (state.isPending(type)) "$label · enviando…" else label, onClick, Modifier.weight(1f),
            enabled = state.availability(type).enabled
        )
        StatusOf(state, type)
    }
}

@Composable
private fun VoiceCard(state: VigiaUiState, vm: VigiaViewModel) {
    val enabled = state.availability(CommandType.SPEAK).enabled
    var volume by remember { mutableFloatStateOf(state.telemetry.volumePct.toFloat()) }
    VigiaCard(spacing = 8) {
        Text("Mensajes de voz", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        voiceMessages.forEach { (label, text) ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Tile)
                    .clickable(enabled = enabled, role = Role.Button) { vm.speak(label, text) }
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, color = Ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text("Hablar ›", color = if (enabled) Navy else Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Volumen", color = Muted, fontSize = 13.sp)
            Slider(
                value = volume,
                onValueChange = { volume = it },
                onValueChangeFinished = { vm.setVolume(volume.toInt()) },
                valueRange = 0f..100f,
                enabled = state.availability(CommandType.SET_VOLUME).enabled,
                colors = SliderDefaults.colors(thumbColor = Navy, activeTrackColor = Navy, inactiveTrackColor = Border),
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
            )
            Text("${volume.toInt()} %", color = Ink, fontSize = 13.sp)
        }
        state.lastByType[CommandType.SPEAK]?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(it.command.description, color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                StatusChip(it.status.label, it.status.tone())
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LedCard(state: VigiaUiState, vm: VigiaViewModel) {
    val enabled = state.availability(CommandType.SET_LED).enabled
    VigiaCard(spacing = 8) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("LED", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            LedColor.entries.forEach { color ->
                val selected = state.telemetry.led == color
                Box(
                    Modifier
                        .padding(start = 8.dp)
                        .size(30.dp)
                        .clip(CircleShape)
                        .then(if (selected) Modifier.border(3.dp, Ink, CircleShape) else Modifier)
                        .padding(if (selected) 4.dp else 0.dp)
                        .clip(CircleShape)
                        .background(color.toColor())
                        .clickable(enabled = enabled, role = Role.RadioButton) { vm.setLed(color) }
                        .semantics { contentDescription = "LED ${color.label}: ${color.meaning}" }
                )
            }
        }
        Hint("El LED sigue siempre el mismo código: verde disponible, azul escoltando, ámbar esperando, rojo detenido. El robot lo vuelve a cambiar en el próximo cambio de estado.")
    }
}

/** Escenarios de prueba del simulador interno (sección 12): reemplazan a la consola docente del emulador. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScenariosCard(vm: VigiaViewModel) {
    VigiaCard(spacing = 10, background = Tile) {
        CardHeader("Escenarios de prueba") { StatusChip("Simulador interno", Tone.INFO) }
        Hint("Provocan las situaciones de la Tabla XVI del documento. Solo existen en el simulador: con un robot real no se muestran.")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            VigiaButton("Batería en 19 %", vm::scenarioLowBattery)
            VigiaButton("Visitante rezagado", vm::scenarioVisitorLagging)
            VigiaButton("Inclinación de 20°", vm::scenarioTilt)
            VigiaButton("Cortar conexión 25 s", vm::scenarioDisconnect, kind = ButtonKind.WARNING)
            VigiaButton("Fallar próxima orden", vm::scenarioFailNext)
        }
    }
}
