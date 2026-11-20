package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ApiProfileDao {
    @Query("SELECT * FROM api_profiles ORDER BY isDefault DESC, createdAt DESC")
    fun observeAllProfiles(): Flow<List<ApiProfileEntity>>

    @Query("SELECT * FROM api_profiles WHERE id = :id LIMIT 1")
    suspend fun getProfileById(id: String): ApiProfileEntity?

    @Query("SELECT * FROM api_profiles WHERE isDefault = 1 AND isEnabled = 1 LIMIT 1")
    suspend fun getDefaultProfile(): ApiProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: ApiProfileEntity)

    @Query("UPDATE api_profiles SET isDefault = CASE WHEN id = :id THEN 1 ELSE 0 END")
    suspend fun setDefaultProfile(id: String)

    @Query("DELETE FROM api_profiles WHERE id = :id")
    suspend fun deleteProfile(id: String)

    @Query("UPDATE api_profiles SET totalTokensUsed = totalTokensUsed + :tokens, estimatedCostUsd = estimatedCostUsd + :cost WHERE id = :id")
    suspend fun recordUsage(id: String, tokens: Long, cost: Double)
}

@Dao
interface DiscoveredModelDao {
    @Query("SELECT * FROM discovered_models WHERE profileId = :profileId ORDER BY category ASC, modelId ASC")
    fun observeModelsForProfile(profileId: String): Flow<List<DiscoveredModelEntity>>

    @Query("SELECT * FROM discovered_models ORDER BY category ASC, modelId ASC")
    fun observeAllModels(): Flow<List<DiscoveredModelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertModels(models: List<DiscoveredModelEntity>)

    @Query("DELETE FROM discovered_models WHERE profileId = :profileId AND isManual = 0")
    suspend fun clearDiscoveredForProfile(profileId: String)
}

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun observeAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getProjectById(id: String): ProjectEntity?

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    fun observeProjectById(id: String): Flow<ProjectEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProject(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProject(id: String)
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC")
    fun observeSessions(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions WHERE id = :id LIMIT 1")
    suspend fun getSessionById(id: String): ChatSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: ChatSessionEntity)

    @Query("DELETE FROM chat_sessions WHERE id = :id")
    suspend fun deleteSession(id: String)

    // Paginated tail query for 1GB RAM devices
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecentMessages(sessionId: String, limit: Int): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesForSession(sessionId: String): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE content LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT 50")
    suspend fun searchMessages(query: String): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId AND timestamp > :afterTimestamp")
    suspend fun deleteMessagesAfter(sessionId: String, afterTimestamp: Long)
}

@Dao
interface AgentTaskDao {
    @Query("SELECT * FROM agent_tasks ORDER BY updatedAt DESC")
    fun observeAllTasks(): Flow<List<AgentTaskEntity>>

    @Query("SELECT * FROM agent_tasks WHERE projectId = :projectId ORDER BY orderIndex ASC, createdAt ASC")
    fun observeTasksForProject(projectId: String): Flow<List<AgentTaskEntity>>

    @Query("SELECT * FROM agent_tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: String): AgentTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(task: AgentTaskEntity)

    @Update
    suspend fun updateTask(task: AgentTaskEntity)

    @Query("DELETE FROM agent_tasks WHERE id = :id")
    suspend fun deleteTask(id: String)
}

@Dao
interface SkillDao {
    @Query("SELECT * FROM skills ORDER BY isEnabled DESC, name ASC")
    fun observeAllSkills(): Flow<List<SkillEntity>>

    @Query("SELECT * FROM skills WHERE isEnabled = 1")
    suspend fun getEnabledSkills(): List<SkillEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSkill(skill: SkillEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(skills: List<SkillEntity>)

    @Query("DELETE FROM skills WHERE id = :id")
    suspend fun deleteSkill(id: String)
}

@Dao
interface BuildJobDao {
    @Query("SELECT * FROM build_jobs WHERE projectId = :projectId ORDER BY createdAt DESC LIMIT 20")
    fun observeBuildsForProject(projectId: String): Flow<List<BuildJobEntity>>

    @Query("SELECT * FROM build_jobs ORDER BY createdAt DESC LIMIT 25")
    fun observeRecentBuilds(): Flow<List<BuildJobEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBuild(job: BuildJobEntity)
}

@Dao
interface SnapshotDao {
    @Query("SELECT * FROM project_snapshots WHERE projectId = :projectId ORDER BY createdAt DESC")
    fun observeSnapshots(projectId: String): Flow<List<ProjectSnapshotEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(snapshot: ProjectSnapshotEntity)

    @Query("DELETE FROM project_snapshots WHERE id = :id")
    suspend fun deleteSnapshot(id: String)
}
