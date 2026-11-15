package com.uade.vigia.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uade.vigia.ui.theme.Border
import com.uade.vigia.ui.theme.Danger
import com.uade.vigia.ui.theme.Disabled
import com.uade.vigia.ui.theme.ErrBg
import com.uade.vigia.ui.theme.ErrBorder
import com.uade.vigia.ui.theme.ErrCard
import com.uade.vigia.ui.theme.ErrFg
import com.uade.vigia.ui.theme.Faint
import com.uade.vigia.ui.theme.InfoBg
import com.uade.vigia.ui.theme.InfoBorder
import com.uade.vigia.ui.theme.InfoFg
import com.uade.vigia.ui.theme.Ink
import com.uade.vigia.ui.theme.Muted
import com.uade.vigia.ui.theme.Navy
import com.uade.vigia.ui.theme.NeutralBg
import com.uade.vigia.ui.theme.NeutralFg
import com.uade.vigia.ui.theme.OkBg
import com.uade.vigia.ui.theme.OkBorder
import com.uade.vigia.ui.theme.OkCard
import com.uade.vigia.ui.theme.OkFg
import com.uade.vigia.ui.theme.Surface
import com.uade.vigia.ui.theme.Tile
import com.uade.vigia.ui.theme.WarnBg
import com.uade.vigia.ui.theme.WarnBorder
import com.uade.vigia.ui.theme.WarnCard
import com.uade.vigia.ui.theme.WarnFg
import com.uade.vigia.ui.theme.Warning
import kotlinx.coroutines.launch

/** Tono de un chip o aviso. Pendiente = Info, Confirmada = Ok, Rechazada = Error, Sin respuesta = Neutro. */
enum class Tone(val bg: Color, val fg: Color, val card: Color, val border: Color) {
    OK(OkBg, OkFg, OkCard, OkBorder),
    INFO(InfoBg, InfoFg, InfoBg, InfoBorder),
    WARNING(WarnBg, WarnFg, WarnCard, WarnBorder),
    ERROR(ErrBg, ErrFg, ErrCard, ErrBorder),
    NEUTRAL(NeutralBg, NeutralFg, NeutralBg, Border)
}

@Composable
fun StatusChip(text: String, tone: Tone, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(tone.bg)
            .padding(start = 10.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(tone.fg))
        Text(text, color = tone.fg, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
fun VigiaCard(
    modifier: Modifier = Modifier,
    background: Color = Surface,
    border: Color = Border,
    spacing: Int = 12,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(spacing.dp),
        content = content
    )
}

@Composable
fun CardHeader(title: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** Dato de telemetría: etiqueta, valor grande y detalle. Si el dato es antiguo se muestra en gris. */
@Composable
fun DataTile(
    label: String,
    value: String,
    detail: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Ink,
    stale: Boolean = false,
    compact: Boolean = false
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Tile)
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .padding(if (compact) 10.dp else 12.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label, color = Muted, fontSize = if (compact) 11.sp else 12.sp, maxLines = 1)
        Text(value, color = if (stale) Faint else valueColor, fontSize = if (compact) 17.sp else 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(detail, color = Muted, fontSize = if (compact) 11.sp else 12.sp, maxLines = 1)
    }
}

enum class ButtonKind { PRIMARY, SECONDARY, DANGER, WARNING }

/** Botón de orden. Alto 48 dp. Deshabilitado = condición de habilitación no cumplida. */
@Composable
fun VigiaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.SECONDARY,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(10.dp)
    val (bg, fg, border) = when {
        !enabled -> Triple(Disabled, Faint, null)
        kind == ButtonKind.PRIMARY -> Triple(Navy, Surface, null)
        kind == ButtonKind.DANGER -> Triple(Danger, Surface, null)
        kind == ButtonKind.WARNING -> Triple(Warning, Surface, null)
        else -> Triple(Surface, Navy, BorderStroke(1.5.dp, Navy))
    }
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(bg)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = fg, fontSize = 15.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
    }
}

/**
 * Detener: hay que mantenerlo presionado 2 s para evitar órdenes accidentales (Riesgo 3).
 * Mientras se mantiene, se llena de izquierda a derecha.
 */
@Composable
fun HoldToConfirmButton(
    text: String,
    enabled: Boolean,
    onConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
    holdMillis: Int = 2_000
) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .background(if (enabled) Danger else Disabled)
            .then(
                if (enabled) Modifier.pointerInput(onConfirmed) {
                    detectTapGestures(onPress = {
                        val job = scope.launch {
                            progress.snapTo(0f)
                            progress.animateTo(1f, tween(holdMillis, easing = LinearEasing))
                            onConfirmed()
                            progress.snapTo(0f)
                        }
                        tryAwaitRelease()
                        if (job.isActive) {
                            job.cancel()
                            scope.launch { progress.animateTo(0f, tween(150)) }
                        }
                    })
                } else Modifier
            )
            .semantics { role = Role.Button; contentDescription = "$text. Mantener presionado dos segundos" },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(progress.value)
                .background(Color(0xFF7F1D1D))
        )
        Text(text, color = if (enabled) Surface else Faint, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

/** Aviso con barra lateral de color (alerta, desconexión, conexión recuperada). */
@Composable
fun Banner(title: String, body: String, tone: Tone, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (tone == Tone.INFO) Color(0xFFF5F9FF) else tone.card)
            .border(1.dp, tone.fg.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Box(Modifier.width(4.dp).height(44.dp).clip(RoundedCornerShape(2.dp)).background(tone.fg))
        Spacer(Modifier.width(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = tone.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(body, color = Ink, fontSize = 13.sp)
        }
    }
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier, color: Color = Muted) {
    Text(text, color = color, fontSize = 12.sp, modifier = modifier)
}

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = false,
    extra: @Composable ColumnScope.() -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        shape = RoundedCornerShape(16.dp),
        title = { Text(title, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(body, color = Ink, fontSize = 14.sp)
                extra()
            }
        },
        confirmButton = {
            VigiaButton(confirmLabel, onConfirm, kind = if (danger) ButtonKind.DANGER else ButtonKind.PRIMARY, modifier = Modifier.width(160.dp))
        },
        dismissButton = {
            VigiaButton("Cancelar", onDismiss, modifier = Modifier.width(160.dp))
        }
    )
}

/** Estado vacío: qué pasa y qué puede hacer el guardia. */
@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    VigiaCard(modifier) {
        Text(title, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(body, color = Muted, fontSize = 14.sp)
        action?.invoke()
    }
}
