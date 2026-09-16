package now.link.mastigias.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import now.link.mastigias.data.taglib.TagLibEngineImpl
import now.link.mastigias.domain.engine.TagEngine
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class EngineModule {

    @Binds
    @Singleton
    abstract fun bindTagEngine(
        tagLibEngineImpl: TagLibEngineImpl
    ): TagEngine
}
