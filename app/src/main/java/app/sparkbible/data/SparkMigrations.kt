package app.sparkbible.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object SparkMigrations {
    const val INDEX_MOMENTS_AT =
        "CREATE INDEX IF NOT EXISTS `index_moments_at` ON `moments` (`at`)"

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(INDEX_MOMENTS_AT)
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE progress ADD COLUMN finishedBooks TEXT NOT NULL DEFAULT ''")
        }
    }

    val ALL = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
