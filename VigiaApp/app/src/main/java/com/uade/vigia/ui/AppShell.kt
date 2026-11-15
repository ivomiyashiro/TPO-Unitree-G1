package com.uade.vigia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.uade.vigia.domain.model.Checkpoint
import com.uade.vigia.ui.components.Banner
import com.uade.vigia.ui.components.ButtonKind
import com.uade.vigia.ui.components.StatusChip
import com.uade.vigia.ui.components.Tone
import com.uade.vigia.ui.components.VigiaButton
import com.uade.vigia.ui.screens.AlertsScreen
import com.uade.vigia.ui.screens.ControlScreen
import com.uade.vigia.ui.screens.EscortScreen
import com.uade.vigia.ui.screens.HistoryScreen
import com.uade.vigia.ui.screens.PanelScreen
import com.uade.vigia.ui.screens.TelemetryScreen
import com.uade.vigia.ui.theme.Canvas
import com.uade.vigia.ui.theme.ErrFg
import com.uade.vigia.ui.theme.Ink
import com.uade.vigia.ui.theme.Muted
import com.uade.vigia.ui.theme.Navy
import com.uade.vigia.ui.theme.NavyTint
import com.uade.vigia.ui.theme.Surface
import com.uade.vigia.ui.theme.VigiaIcons
import com.uade.vigia.domain.usecase.Freshness

enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    PANEL("panel", "Panel", VigiaIcons.Panel),
    ESCORT("escort", "Escolta", VigiaIcons.Route),
    ALERTS("alerts", "Alertas", VigiaIcons.Bell),
    HISTORY("history", "Historial", VigiaIcons.Clock),
    CONTROL("control", "Control", VigiaIcons.Sliders)
}

private const val TELEMETRY_ROUTE = "telemetry"

/** Ancho a partir del cual se usa el diseño de tablet: navegación lateral y dos columnas. */
val WideBreakpoint = 840.dp

@Composable
fun AppShell(state: VigiaUiState, vm: VigiaViewModel) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: Tab.PANEL.route
    val currentTab = Tab.entries.firstOrNull { it.route == route } ?: Tab.PANEL
    val go: (Tab) -> Unit = { tab -> nav.goTo(tab.route) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Canvas)) {
        val wide = maxWidth >= WideBreakpoint
        Row(Modifier.fillMaxSize()) {
            if (wide) NavRail(currentTab, state.telemetry.activeAlerts.size, go)
            Column(Modifier.weight(1f).fillMaxHeight()) {
                StatusBar(state, wide)
                GlobalBanners(state, vm, wide)
                Box(Modifier.weight(1f)) {
                    NavHost(nav, startDestination = Tab.PANEL.route) {
                        composable(Tab.PANEL.route) {
                            PanelScreen(state, vm, wide, onOpenTelemetry = { nav.navigate(TELEMETRY_ROUTE) }, onOpenEscort = { go(Tab.ESCORT) }, onOpenAlerts = { go(Tab.ALERTS) })
                        }
                        composable(TELEMETRY_ROUTE) { TelemetryScreen(state, wide) }
                        composable(Tab.ESCORT.route) {
                            EscortScreen(state, vm, wide, onGoPanel = { go(Tab.PANEL) }, onGoHistory = { go(Tab.HISTORY) })
                        }
                        composable(Tab.ALERTS.route) { AlertsScreen(state, vm, wide) }
                        composable(Tab.HISTORY.route) { HistoryScreen(state, vm, wide) }
                        composable(Tab.CONTROL.route) { ControlScreen(state, vm, wide, onChangeRobot = vm::disconnectFromRobot) }
                    }
                }
                if (!wide) BottomBar(currentTab, state.telemetry.activeAlerts.size, go)
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp))
    }
}

private fun NavHostController.goTo(route: String) {
    navigate(route) {
        popUpTo(Tab.PANEL.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun NavRail(current: Tab, alertCount: Int, onSelect: (Tab) -> Unit) {
    Column(
        Modifier
            .width(96.dp)
            .fillMaxHeight()
            .background(Surface)
            .statusBarsPadding()
            .padding(top = 16.dp, start = 8.dp, end = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Navy),
            contentAlignment = Alignment.Center
        ) { Text("V", color = Surface, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(16.dp))
        Tab.entries.forEach { tab ->
            val selected = tab == current
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) NavyTint else Surface)
                    .clickable(role = Role.Tab) { onSelect(tab) }
                    .semantics { this.selected = selected }
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TabIcon(tab, selected, if (tab == Tab.ALERTS) alertCount else 0)
                Text(
                    tab.label,
                    color = if (selected) Navy else Muted,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun TabIcon(tab: Tab, selected: Boolean, badge: Int) {
    BadgedBox(badge = { if (badge > 0) Badge(containerColor = ErrFg, contentColor = Surface) { Text("$badge") } }) {
        Icon(tab.icon, contentDescription = null, tint = if (selected) Navy else Muted, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun BottomBar(current: Tab, alertCount: Int, onSelect: (Tab) -> Unit) {
    NavigationBar(containerColor = Surface) {
        Tab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == current,
                onClick = { onSelect(tab) },
                icon = { TabIcon(tab, tab == current, if (tab == Tab.ALERTS) alertCount else 0) },
                label = { Text(tab.label, fontSize = 12.sp) },
                colors = NavigationBarItemDefaults.colors(indicatorColor = NavyTint, selectedTextColor = Navy, unselectedTextColor = Muted)
            )
        }
    }
}

/** Barra de estado: robot, conexión, batería, estado y antigüedad del dato. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusBar(state: VigiaUiState, wide: Boolean) {
    val t = state.telemetry
    val robot = state.selectedRobot
    val offline = state.freshness == Freshness.OFFLINE
    val stale = state.freshness != Freshness.FRESH
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (offline) Tone.ERROR.card else Surface)
            .statusBarsPadding()
            .padding(horizontal = if (wide) 24.dp else 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(if (wide) Modifier else Modifier.weight(1f)) {
            Text(robot?.name ?: "G1", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(robot?.site ?: "", color = Muted, fontSize = 12.sp)
        }
        FlowRow(
            modifier = if (wide) Modifier.weight(1f) else Modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically
        ) {
            when (state.freshness) {
                Freshness.FRESH -> StatusChip("En línea", Tone.OK)
                Freshness.STALE -> StatusChip("Datos antiguos", Tone.WARNING)
                Freshness.OFFLINE -> StatusChip("Sin conexión", Tone.ERROR)
            }
            if (stale) StatusChip("Batería ${t.batteryPct} % · antiguo", Tone.NEUTRAL)
            else StatusChip("Batería ${t.batteryPct} %", batteryTone(t.soc))
            if (stale) StatusChip("Último estado: ${t.robotState.label.lowercase()}", Tone.NEUTRAL)
            else StatusChip(t.robotState.label, t.robotState.tone())
            if (wide) {
                Text(
                    if (stale) "Último dato hace ${state.ageSeconds} s" else "Actualizado hace ${state.ageSeconds.coerceAtLeast(1)} s",
                    color = if (stale) ErrFg else Muted,
                    fontSize = 12.sp,
                    fontWeight = if (stale) FontWeight.Medium else FontWeight.Normal
                )
            }
        }
    }
}

/** Avisos que se superponen a cualquier pantalla: desconexión y orden sin respuesta al reconectar. */
@Composable
private fun GlobalBanners(state: VigiaUiState, vm: VigiaViewModel, wide: Boolean) {
    val pad = Modifier.padding(start = if (wide) 24.dp else 16.dp, end = if (wide) 24.dp else 16.dp, top = 16.dp)
    val lastPoint = state.telemetry.checkpoint.takeIf { it != Checkpoint.BASE }?.label ?: "BASE"
    if (state.freshness == Freshness.OFFLINE) {
        Column(pad, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Banner(
                "Se perdió la conexión con el robot",
                "Lo que ves es el último estado conocido. Las órdenes están deshabilitadas hasta que vuelva la conexión. " +
                    "Si no vuelve en 2 minutos, andá al último punto conocido ($lastPoint).",
                Tone.ERROR
            )
            VigiaButton("Reintentar conexión", vm::retryConnection, Modifier.fillMaxWidth())
        }
    } else if (state.freshness == Freshness.FRESH && state.unanswered != null) {
        val label = state.unanswered.command.description
        val stillValid = state.availability(state.unanswered.command.type).enabled
        Column(pad, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Banner(
                "Conexión recuperada · “$label” no se ejecutó",
                "El robot siguió: ${state.telemetry.movementSummary().replaceFirstChar { it.lowercase() }}. " +
                    if (stillValid) "La orden quedó sin respuesta y no se reenvía sola. Decidí si la volvés a enviar."
                    else "La orden quedó sin respuesta y ya no se puede enviar en el estado actual del robot.",
                Tone.WARNING
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (stillValid) {
                    VigiaButton("Reenviar $label", vm::resendUnanswered, Modifier.weight(1f), kind = ButtonKind.PRIMARY)
                    VigiaButton("Continuar sin reenviar", vm::dismissUnanswered, Modifier.weight(1f))
                } else {
                    VigiaButton("Entendido", vm::dismissUnanswered, Modifier.weight(1f))
                }
            }
        }
    }
}
