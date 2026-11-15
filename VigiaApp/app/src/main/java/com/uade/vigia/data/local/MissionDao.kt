package com.uade.vigia.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// Los métodos suspend devuelven Long/Int: con Room 2.6 + KSP2 un suspend que devuelve Unit no compila.
@Dao
interface MissionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMission(mission: MissionEntity): Long

    @Query("UPDATE missions SET status = :status, endedAt = :endedAt, distanceM = :distanceM WHERE id = :id")
    suspend fun finishMission(id: String, status: String, endedAt: Long, distanceM: Float): Int

    @Insert
    suspend fun insertEvent(event: EventEntity): Long

    @Query(
        """
        SELECT m.id, m.visitor, m.destination, m.status, m.startedAt, m.endedAt, m.distanceM,
            (SELECT COUNT(*) FROM events e WHERE e.missionId = m.id AND e.kind = 'ALERT') AS alertCount,
            (SELECT COUNT(*) FROM events e WHERE e.missionId = m.id AND e.kind = 'COMMAND') AS commandCount,
            (SELECT COUNT(*) FROM events e WHERE e.missionId = m.id AND e.kind = 'COMMAND' AND e.rejected = 1) AS rejectedCount
        FROM missions m ORDER BY m.startedAt DESC
        """
    )
    fun missions(): Flow<List<MissionRow>>

    @Query("SELECT * FROM events WHERE missionId = :missionId ORDER BY at ASC, id ASC")
    fun events(missionId: String): Flow<List<EventEntity>>

    @Query("SELECT id FROM missions")
    suspend fun missionIds(): List<String>
}
