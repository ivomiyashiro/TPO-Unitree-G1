package com.uade.vigia.domain.model

import java.time.Instant

enum class EventKind { MISSION, COMMAND, ALERT }

enum class EventTone { NEUTRAL, INFO, OK, WARNING, ERROR }

/** Evento que el robot informa durante la escolta (llegada a un punto, alerta, fin). */
sealed interface RobotEvent {
    val at: Instant
    val missionId: String?

    data class MissionStarted(override val missionId: String, val visitor: String, val destination: String, override val at: Instant) : RobotEvent
    data class Milestone(override val missionId: String?, val text: String, val tone: EventTone, override val at: Instant) : RobotEvent
    data class AlertRaised(val alert: Alert, override val at: Instant) : RobotEvent {
        override val missionId: String? get() = alert.missionId
    }
    data class MissionFinished(override val missionId: String, val status: MissionStatus, val distanceM: Float, override val at: Instant) : RobotEvent
}

data class HistoryEvent(
    val id: Long,
    val missionId: String?,
    val at: Instant,
    val kind: EventKind,
    val text: String,
    val tone: EventTone
)

data class MissionSummary(
    val id: String,
    val visitor: String,
    val destination: String,
    val status: MissionStatus,
    val startedAt: Instant,
    val endedAt: Instant?,
    val distanceM: Float,
    val alertCount: Int,
    val commandCount: Int,
    val rejectedCommandCount: Int
)
