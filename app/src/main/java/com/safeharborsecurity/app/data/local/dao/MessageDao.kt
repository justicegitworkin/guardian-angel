package com.safeharborsecurity.app.data.local.dao

import androidx.room.*
import com.safeharborsecurity.app.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSession(sessionId: String): Flow<List<MessageEntity>>

    /**
     * Returns the most recent [limit] messages for the session in chronological order.
     * Used to build the conversation context window sent to the Claude API — avoids loading
     * the entire unbounded history into memory.
     */
    @Query(
        "SELECT * FROM chat_messages WHERE sessionId = :sessionId " +
        "ORDER BY timestamp DESC LIMIT :limit"
    )
    suspend fun getRecentMessagesForSession(sessionId: String, limit: Int = 20): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun clearSession(sessionId: String)

    @Query("DELETE FROM chat_messages WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}
