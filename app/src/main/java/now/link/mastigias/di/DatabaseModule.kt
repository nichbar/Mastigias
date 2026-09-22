package now.link.mastigias.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import now.link.mastigias.data.database.MastigiasDatabase
import now.link.mastigias.data.database.dao.TrackDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideMastigiasDatabase(
        @ApplicationContext context: Context
    ): MastigiasDatabase {
        return Room.databaseBuilder(
            context,
            MastigiasDatabase::class.java,
            MastigiasDatabase.DATABASE_NAME
        )
            .addMigrations(MastigiasDatabase.MIGRATION_1_2)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideTrackDao(database: MastigiasDatabase): TrackDao {
        return database.trackDao()
    }
}
