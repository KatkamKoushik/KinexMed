package com.kinexmed.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.kinexmed.data.dao.ChatMessageDao
import com.kinexmed.data.dao.EvidenceEventDao
import com.kinexmed.data.dao.RepDao
import com.kinexmed.data.dao.SessionDao
import com.kinexmed.data.entity.ChatMessageEntity
import com.kinexmed.data.entity.EvidenceEventEntity
import com.kinexmed.data.entity.RepEntity
import com.kinexmed.data.entity.SessionEntity

@Database(
    entities = [SessionEntity::class, RepEntity::class, EvidenceEventEntity::class, ChatMessageEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun repDao(): RepDao
    abstract fun evidenceEventDao(): EvidenceEventDao
    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kinexmed_database.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
