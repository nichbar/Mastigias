package now.link.mastigias.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import now.link.mastigias.data.database.dao.TrackDao
import now.link.mastigias.data.database.entity.TrackEntity
import now.link.mastigias.data.database.entity.TrackFtsEntity

@Database(
    entities = [
        TrackEntity::class,
        TrackFtsEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class MastigiasDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao

    companion object {
        const val DATABASE_NAME = "mastigias_database"
    }
}
