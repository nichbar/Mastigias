package now.link.mastigias.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import now.link.mastigias.data.database.dao.TrackDao
import now.link.mastigias.data.database.entity.TrackEntity
import now.link.mastigias.data.database.entity.TrackFtsEntity

@Database(
    entities = [
        TrackEntity::class,
        TrackFtsEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class MastigiasDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao

    companion object {
        const val DATABASE_NAME = "mastigias_database"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracks ADD COLUMN date_added INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tracks_date_added ON tracks(date_added)")
            }
        }
    }
}
