package now.link.mastigias.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import now.link.mastigias.core.logging.LogManager
import now.link.mastigias.domain.logging.AppLogger
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LoggingModule {

    @Provides
    @Singleton
    fun provideAppLogger(): AppLogger = LogManager
}
