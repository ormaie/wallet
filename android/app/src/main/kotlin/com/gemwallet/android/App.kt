package com.gemwallet.android

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.gemwallet.android.application.assets.cases.GetActiveAssetsInfo
import com.gemwallet.android.application.notifications.cases.ShowSystemNotification
import com.gemwallet.android.application.transactions.cases.GetTransactions
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import uniffi.gemstone.initializeForeignFutureResultStructs
import javax.inject.Inject

@HiltAndroidApp
class App :
    Application(),
    SingletonImageLoader.Factory {

    @Inject
    lateinit var appLifecycleCoordinator: Lazy<AppLifecycleCoordinator>

    @Inject
    lateinit var getActiveAssetsInfo: Lazy<GetActiveAssetsInfo>

    @Inject
    lateinit var getTransactions: Lazy<GetTransactions>

    @Inject
    lateinit var showSystemNotification: Lazy<ShowSystemNotification>

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            getActiveAssetsInfo.get()
            getTransactions.get()
        }
        appScope.launch {
            showSystemNotification.get().ensureChannels()
            val coordinator = appLifecycleCoordinator.get()
            withContext(Dispatchers.Main) {
                ProcessLifecycleOwner.get().lifecycle.addObserver(coordinator)
            }
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(this)
        .components {
            add(OkHttpNetworkFetcherFactory(callFactory = ::imageHttpClient))
        }
        .memoryCache {
            MemoryCache.Builder()
                .maxSizePercent(this, 0.25)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(512L * 1024 * 1024)
                .build()
        }
        .build()

    private fun imageHttpClient() = OkHttpClient.Builder()
        .dispatcher(Dispatcher().apply { maxRequestsPerHost = IMAGE_REQUESTS_PER_HOST })
        .build()

    companion object {
        private const val IMAGE_REQUESTS_PER_HOST = 16

        init {
            System.loadLibrary("gemstone")
            initializeForeignFutureResultStructs()
        }
    }
}
