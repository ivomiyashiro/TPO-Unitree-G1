package com.uade.vigia.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Una escolta. No guarda datos personales del visitante, solo una etiqueta (Riesgo 5). */
@Entity(tableName = "missions")
data class MissionEntity(
    @PrimaryKey val id: String,
    val visitor: String,
    val destination: String,
    val status: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val distanceM: Float = 0f
)

/**
 * Evento del historial: hito de la escolta, orden enviada o alerta.
 * [missionId] es nulo para órdenes dadas fuera de una escolta (por ejemplo un saludo en la base).
 */
@Entity(tableName = "events", indices = [Index("missionId")])
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val missionId: String?,
    val at: Long,
    val kind: String,
    val text: String,
    val tone: String,
    @ColumnInfo(defaultValue = "0") val rejected: Boolean = false
)

/** Fila de la tabla de historial con los conteos calculados en la consulta. */
data class MissionRow(
    val id: String,
    val visitor: String,
    val destination: String,
    val status: String,
    val startedAt: Long,
    val endedAt: Long?,
    val distanceM: Float,
    val alertCount: Int,
    val commandCount: Int,
    val rejectedCount: Int
)
