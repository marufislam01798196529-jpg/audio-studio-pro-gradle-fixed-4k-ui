package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ApiProfileEntity::class,
        DiscoveredModelEntity::class,
        ProjectEntity::class,
        ChatSessionEntity::class,
        ChatMessageEntity::class,
        AgentTaskEntity::class,
        SkillEntity::class,
        BuildJobEntity::class,
        ProjectSnapshotEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class XThrexDatabase : RoomDatabase() {
    abstract fun apiProfileDao(): ApiProfileDao
    abstract fun discoveredModelDao(): DiscoveredModelDao
    abstract fun projectDao(): ProjectDao
    abstract fun chatDao(): ChatDao
    abstract fun agentTaskDao(): AgentTaskDao
    abstract fun skillDao(): SkillDao
    abstract fun buildJobDao(): BuildJobDao
    abstract fun snapshotDao(): SnapshotDao

    companion object {
        @Volatile
        private var INSTANCE: XThrexDatabase? = null

        fun getInstance(context: Context): XThrexDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    XThrexDatabase::class.java,
                    "xthrex_workspace.db",
                ).build().also { INSTANCE = it }
            }
        }
    }
}
