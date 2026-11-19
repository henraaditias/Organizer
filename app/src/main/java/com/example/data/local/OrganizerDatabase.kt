package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.domain.FailureCategory
import com.example.domain.FileCategory
import com.example.domain.OperationStage
import com.example.domain.RunStatus
import com.example.domain.RunTriggerType

class OrganizerTypeConverters {
    @TypeConverter
    fun fromFileCategory(value: FileCategory): String = value.name

    @TypeConverter
    fun toFileCategory(value: String): FileCategory =
        runCatching { FileCategory.valueOf(value) }.getOrDefault(FileCategory.OTHERS)

    @TypeConverter
    fun fromOperationStage(value: OperationStage): String = value.name

    @TypeConverter
    fun toOperationStage(value: String): OperationStage =
        runCatching { OperationStage.valueOf(value) }.getOrDefault(OperationStage.FAILED)

    @TypeConverter
    fun fromFailureCategory(value: FailureCategory?): String? = value?.name

    @TypeConverter
    fun toFailureCategory(value: String?): FailureCategory? =
        value?.let { runCatching { FailureCategory.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun fromRunTriggerType(value: RunTriggerType): String = value.name

    @TypeConverter
    fun toRunTriggerType(value: String): RunTriggerType =
        runCatching { RunTriggerType.valueOf(value) }.getOrDefault(RunTriggerType.MANUAL)

    @TypeConverter
    fun fromRunStatus(value: RunStatus): String = value.name

    @TypeConverter
    fun toRunStatus(value: String): RunStatus =
        runCatching { RunStatus.valueOf(value) }.getOrDefault(RunStatus.CANCELLED)
}

@Database(
    entities = [
        SourceFolderEntity::class,
        ExcludedSubfolderEntity::class,
        OrganizationRunEntity::class,
        FileOperationEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(OrganizerTypeConverters::class)
abstract class OrganizerDatabase : RoomDatabase() {
    abstract fun organizerDao(): OrganizerDao

    companion object {
        @Volatile
        private var INSTANCE: OrganizerDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE organization_runs ADD COLUMN destinationFolderName TEXT")
                db.execSQL("ALTER TABLE organization_runs ADD COLUMN currentFileBytesProcessed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE organization_runs ADD COLUMN currentFileTotalBytes INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): OrganizerDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    OrganizerDatabase::class.java,
                    "organizer_local.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
