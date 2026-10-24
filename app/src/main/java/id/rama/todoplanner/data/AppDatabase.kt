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
 * MIGRASI NON-DESTRUKTIF (WAJIB: data user TIDAK boleh hilang saat update):
 *  - 1 -> 2 (ronde 5): activities.intervalValue INTEGER (nullable),
 *    activities.intervalUnit TEXT (nullable),
 *    activities.doneTimestamps TEXT NOT NULL DEFAULT ''.
 *  - 2 -> 3 (ronde 6): activities.iconEmoji TEXT NOT NULL DEFAULT ''
 *    (logo emoji per kegiatan; "" = tanpa logo).
 *  - 3 -> 4 (ronde 7): activities.dailyDoneLog TEXT NOT NULL DEFAULT ''
 *    (daftar waktu penandaan selesai Daily Reminder per hari; "" = belum ada).
 * Kolom lama tidak pernah diubah/dihapus; backup JSON lama tetap bisa
 * diimpor karena field baru di BackupManager dibaca dengan nilai default
 * bila tidak ada. TIDAK memakai fallbackToDestructiveMigration.
 */
@Database(entities = [ActivityEntity::class, TargetEntity::class], version = 4, exportSchema = false)
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

        /** RONDE 6 — kolom logo emoji per kegiatan. */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE activities ADD COLUMN iconEmoji TEXT NOT NULL DEFAULT ''")
            }
        }

        /** RONDE 7 — kolom daftar penandaan selesai Daily Reminder per hari. */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE activities ADD COLUMN dailyDoneLog TEXT NOT NULL DEFAULT ''")
            }
        }

        @Volatile private var instance: AppDatabase? = null
        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "todo_planner.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { instance = it }
            }
    }
}
