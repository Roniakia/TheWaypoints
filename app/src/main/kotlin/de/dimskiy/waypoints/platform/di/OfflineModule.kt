package de.dimskiy.waypoints.platform.di

import android.content.Context
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.dimskiy.waypoints.platform.offline.*
import java.io.File
import javax.inject.Singleton
import javax.inject.Provider

@Module
@InstallIn(SingletonComponent::class)
object OfflineModule {
    @Provides @Singleton fun transport(): PackTransport = HttpsPackTransport()
    @Provides @Singleton fun store(@ApplicationContext context: Context) = RegionPackStore(File(context.noBackupFilesDir, "poi-regions"))
    @Provides @Singleton fun catalogue(@ApplicationContext context: Context, transport: PackTransport) = PoiCatalogueRepository(
        File(context.noBackupFilesDir, "poi-catalogue.json"), transport) {
        context.assets.open("poi-catalogue/catalog.json").use { it.readBytes() } to
            context.assets.open("poi-catalogue/catalog.json.sig").use { it.readBytes() }
    }
    @Provides @Singleton fun downloads(@ApplicationContext context: Context, @BaseModule.TimeProvider time: Provider<Long>) =
        RegionDownloads(WorkManager.getInstance(context), File(context.noBackupFilesDir, "poi-downloads")) { time.get() }
}
