package com.uade.vigia.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uade.vigia.domain.model.Checkpoint
import com.uade.vigia.domain.model.RobotState
import com.uade.vigia.domain.model.RouteMap
import com.uade.vigia.domain.model.Telemetry
import com.uade.vigia.domain.model.VisitorState
import com.uade.vigia.ui.theme.Border
import com.uade.vigia.ui.theme.ErrFg
import com.uade.vigia.ui.theme.Faint
import com.uade.vigia.ui.theme.MapCorridor
import com.uade.vigia.ui.theme.MapCorridorText
import com.uade.vigia.ui.theme.MapCrossing
import com.uade.vigia.ui.theme.MapGarita
import com.uade.vigia.ui.theme.MapOffice
import com.uade.vigia.ui.theme.MapRacks
import com.uade.vigia.ui.theme.Muted
import com.uade.vigia.ui.theme.Navy
import com.uade.vigia.ui.theme.NeutralFg
import com.uade.vigia.ui.theme.OkFg
import com.uade.vigia.ui.theme.Surface
import com.uade.vigia.ui.theme.Tile
import com.uade.vigia.ui.theme.toColor

/** Zona del plano en coordenadas normalizadas (proporciones del plano 700 × 210 del prototipo). */
private data class Zone(val l: Float, val t: Float, val r: Float, val b: Float, val color: Color, val label: String?, val labelColor: Color = Muted)

private val zones = listOf(
    Zone(0f, 0f, 1f, 0.25f, MapRacks, "Racks · acceso restringido"),
    Zone(0f, 0.25f, 0.1167f, 1f, MapGarita, "Garita"),
    Zone(0.1167f, 0.25f, 0.3667f, 1f, Surface, "Hall"),
    Zone(0.3667f, 0.25f, 0.8f, 1f, Surface, null),
    Zone(0.3667f, 0.475f, 0.8f, 0.775f, MapCorridor, null),
    Zone(0.5833f, 0f, 0.6833f, 1f, MapCrossing, "Cruce", ErrFg),
    Zone(0.8f, 0.25f, 1f, 1f, MapOffice, "Oficina")
)

/**
 * Mini plano del recorrido con los puntos, el robot (con el color de su LED) y el visitante.
 * Con datos antiguos el robot se dibuja en gris y punteado: es la última posición conocida.
 */
@Composable
fun RouteMapView(telemetry: Telemetry, stale: Boolean, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val reached = reachedCheckpoints(telemetry)
    val description = if (stale) "Plano del recorrido. Última posición conocida del robot: ${telemetry.checkpoint.label}"
    else "Plano del recorrido. Robot en ${telemetry.checkpoint.label}, LED ${telemetry.led.label.lowercase()}"
    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(10f / 3f)
            .clip(RoundedCornerShape(8.dp))
            .semantics { contentDescription = description }
    ) {
        drawRect(Tile)
        zones.forEach { z ->
            val tl = Offset(z.l * size.width, z.t * size.height)
            val sz = Size((z.r - z.l) * size.width, (z.b - z.t) * size.height)
            drawRect(z.color.withStale(stale), tl, sz)
            drawRect(Border, tl, sz, style = Stroke(1.dp.toPx()))
            z.label?.let { label(measurer, it, tl + Offset(4.dp.toPx(), 3.dp.toPx()), z.labelColor.withStale(stale), bold = false) }
        }
        label(measurer, "Pasillo peatonal", Offset(0.3724f * size.width, 0.789f * size.height), MapCorridorText.withStale(stale), bold = false)

        // Puntos del recorrido
        val r = 6.dp.toPx()
        RouteMap.points.forEach { (cp, p) ->
            val c = Offset(p.first * size.width, p.second * size.height)
            when (cp) {
                Checkpoint.BASE, Checkpoint.SAFE -> {
                    val color = if (cp == Checkpoint.SAFE) OkFg else Navy
                    drawCircle(Surface, r, c)
                    drawCircle(color.withStale(stale), r - 1.dp.toPx(), c, style = Stroke(2.dp.toPx()))
                    label(measurer, cp.label, c + Offset(r + 3.dp.toPx(), -r), color.withStale(stale), bold = true)
                }
                else -> {
                    if (cp in reached) drawCircle(Navy.withStale(stale), r, c)
                    else {
                        drawCircle(Surface, r, c)
                        drawCircle(Navy.withStale(stale), r - 1.dp.toPx(), c, style = Stroke(2.dp.toPx()))
                    }
                    label(measurer, cp.label, c + Offset(-r + 1.dp.toPx(), r + 2.dp.toPx()), Navy.withStale(stale), bold = true)
                }
            }
        }

        // Visitante: detrás del robot, más lejos si está rezagado.
        val robot = Offset(telemetry.pose.mapX * size.width, telemetry.pose.mapY * size.height)
        val showVisitor = telemetry.visitorState != VisitorState.NONE && telemetry.robotState != RobotState.IN_BASE
        if (showVisitor) {
            val back = (15f + telemetry.visitorDistanceM * 4f).dp.toPx()
            val visitorColor = if (telemetry.visitorState == VisitorState.LAGGING) ErrFg else NeutralFg
            drawCircle(visitorColor.withStale(stale), 5.dp.toPx(), robot - Offset(back, 0f))
        }

        // Robot G1 con el anillo del color del LED.
        if (stale) {
            drawCircle(Faint, 15.dp.toPx(), robot, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))))
            drawCircle(Faint, 9.dp.toPx(), robot)
            label(measurer, "G1 · última posición conocida", robot + Offset(-10.dp.toPx(), -30.dp.toPx()), Faint, bold = true)
        } else {
            drawCircle(Surface, 15.dp.toPx(), robot)
            drawCircle(telemetry.led.toColor(), 13.5f.dp.toPx(), robot, style = Stroke(3.dp.toPx()))
            drawCircle(Navy, 9.dp.toPx(), robot)
            label(measurer, "G1", robot + Offset(-7.dp.toPx(), -30.dp.toPx()), Navy, bold = true, size = 11)
        }
    }
}

private fun reachedCheckpoints(t: Telemetry): Set<Checkpoint> {
    val mission = t.mission ?: return emptySet()
    if (mission.status.isFinished && t.robotState == RobotState.IN_BASE) return emptySet()
    val idx = Checkpoint.route.indexOf(t.checkpoint)
    return if (idx < 0) emptySet() else Checkpoint.route.subList(1, idx + 1).toSet()
}

private fun Color.withStale(stale: Boolean): Color = if (stale) copy(alpha = alpha * 0.55f) else this

private fun DrawScope.label(measurer: TextMeasurer, text: String, at: Offset, color: Color, bold: Boolean, size: Int = 10) {
    drawText(
        measurer, text, at,
        style = TextStyle(color = color, fontSize = size.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    )
}
