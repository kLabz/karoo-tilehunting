package bzh.klabz.squadrating

import android.app.Application
import bzh.klabz.squadrating.services.ClusterDrawService
import bzh.klabz.squadrating.services.ExploreSquadratsService
import bzh.klabz.squadrating.services.KarooSystemServiceProvider
import bzh.klabz.squadrating.services.StatshuntersTilesProvider
import bzh.klabz.squadrating.services.StatshuntersDownloadService
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val appModule = module {
    singleOf(::KarooSystemServiceProvider)
    singleOf(::StatshuntersTilesProvider)
    singleOf(::ClusterDrawService)
    singleOf(::StatshuntersDownloadService)
    singleOf(::ExploreSquadratsService)
}

class SquadratingApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@SquadratingApplication)
            modules(appModule)
        }
    }
}
