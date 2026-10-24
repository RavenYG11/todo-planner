package id.rama.todoplanner.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Database SQLite lokal (Room). File-nya hanya ada di penyimpanan internal aplikasi.
 *
 * RONDE 5 — MIGRASI NON-DESTRUKTIF 1 -> 2 (WAJIB: data user TIDAK boleh hilang
 * saat update aplikasi):
 *  - activities.intervalValue  INTEGER (baru, nullable)
 *  - activities.intervalUnit   TEXT    (baru, nullable)
 *  - activities.doneTimestamps TEXT NOT NULL DEFAULT '' (baru)
 * Kolom lama tidak diubah/dihapus; backup JSON lama tetap bisa diimpor karena
 * field baru di BackupManager dibaca dengan nilai default bila tidak ada.
 * TIDAK memakai fallbackToDestructiveMigration — migrasi eksplisit di bawah.
 */
@Database(entities = [ActivityEntity::class, TargetEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun activityDao(): ActivityDao
    abstract fun targetDao(): TargetDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE activities ADD COLUMN intervalValue INTEGER")
                db.execSQL("ALTER TABLE activities ADD COLUMN intervalUnit TEXT")
                db.execSQL("ALTER TABLE activities ADD COLUMN doneTimestamps TEXT NOT NULL DEFAULT ''")
            }
        }

        @Volatile private var instance: AppDatabase? = null
        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "todo_planner.db"
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
