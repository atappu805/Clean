package com.saurav.pixelmusic

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.os.Build
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.ExistingWorkPolicy
import coil.ImageLoaderFactory
import com.saurav.pixelmusic.data.preferences.UserPreferencesRepository
import com.saurav.pixelmusic.data.repository.ArtistImageRepository
import com.saurav.pixelmusic.presentation.viewmodel.LibraryStateHolder
import com.saurav.pixelmusic.presentation.viewmodel.ThemeStateHolder
import com.saurav.pixelmusic.utils.AlbumArtCacheManager
import com.saurav.pixelmusic.utils.AlbumArtUtils
import com.saurav.pixelmusic.utils.CrashHandler
import com.saurav.pixelmusic.utils.AppLocaleManager
import com.saurav.pixelmusic.utils.PixelLogger
import com.saurav.pixelmusic.utils.PixelHttpLoggingInterceptor
import com.saurav.pixelmusic.utils.MediaItemBuilder
import com.saurav.pixelmusic.utils.MediaMetadataRetrieverPool
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import com.saurav.pixelmusic.utils.UpdateWorker
import android.os.Looper
import com.saurav.pixelmusic.utils.potoken.PoTokenGenerator
import kotlinx.coroutines.runBlocking
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.PoTokenProvider as NpPoTokenProvider
import org.schabi.newpipe.extractor.services.youtube.PoTokenResult as NpPoTokenResult
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import saurav.shru.pixelmusic.innertube.YouTube
import java.util.Locale
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody




private const val NEWPIPE_DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

/**
 * Adapts the app's shared WebView BotGuard minter to NewPipeExtractor's
 * PoTokenProvider, mirroring the NewPipe app's PoTokenProviderImpl.
 */
private object NewPipePoTokenProvider : NpPoTokenProvider {
    private const val FALLBACK_VISITOR_DATA = "CgtPQVo1TndqTldzWSjE0bC3Bg%3D%3D"

    @Volatile
    private var generator: PoTokenGenerator? = null

    fun initialize(generator: PoTokenGenerator) {
        this.generator = generator
    }

    override fun getWebClientPoToken(videoId: String): NpPoTokenResult? {
        val gen = generator ?: return null
        // The extractor calls this synchronously off the main thread; never block main.
        if (Looper.myLooper() == Looper.getMainLooper()) return null
        val sessionId = YouTube.visitorData?.takeIf { it.isNotBlank() } ?: FALLBACK_VISITOR_DATA
        return try {
            val token = runBlocking { gen.getWebClientPoToken(videoId, sessionId) } ?: return null
            NpPoTokenResult(sessionId, token.playerRequestPoToken, token.streamingDataPoToken)
        } catch (e: Exception) {
            Timber.tag("NewPipePoToken").w(e, "PO token minting failed")
            null
        }
    }
}

@HiltAndroidApp
class PixelMusicApplication : Application(), ImageLoaderFactory, Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var imageLoader: dagger.Lazy<ImageLoader>

    @Inject
    lateinit var localArtworkCoilFetcherFactory: dagger.Lazy<com.saurav.pixelmusic.data.image.LocalArtworkCoilFetcher.Factory>

    @Inject
    lateinit var themeStateHolder: dagger.Lazy<ThemeStateHolder>

    @Inject
    lateinit var artistImageRepository: dagger.Lazy<ArtistImageRepository>

    @Inject
    lateinit var libraryStateHolder: dagger.Lazy<LibraryStateHolder>

    @Inject
    lateinit var userPreferencesRepository: dagger.Lazy<UserPreferencesRepository>

    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // AÑADE EL COMPANION OBJECT
    companion object {
        const val NOTIFICATION_CHANNEL_ID = "pixelmusic_music_channel"
    }

    private val appLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            libraryStateHolder.get().restoreAfterTrimIfNeeded()
            
            // 1. Get the current day (Days since epoch)
            val currentDay = java.lang.System.currentTimeMillis() / (1000 * 60 * 60 * 24)
            
            // 2. Access a simple SharedPreferences file just for updates
            val prefs = getSharedPreferences("PixelMusicUpdatePrefs", Context.MODE_PRIVATE)
            val lastCheckDay = prefs.getLong("last_check_day", 0)

            // 3. If the app hasn't checked today, run it and save the new day
            if (currentDay != lastCheckDay) {
                prefs.edit().putLong("last_check_day", currentDay).apply()

                val oneTimeCheck = OneTimeWorkRequestBuilder<UpdateWorker>().build()
                WorkManager.getInstance(this@PixelMusicApplication).enqueueUniqueWork(
                    "AppOpenUpdateCheck",
                    ExistingWorkPolicy.REPLACE,
                    oneTimeCheck
                )
            }
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocaleManager.wrapContext(base))
    }

override fun onCreate() {
    super.onCreate()

    // Unconditional — prints even if PixelLogger.init somehow fails.
    android.util.Log.e("PM-BOOT", "Application.onCreate entered")

    // 1. Init the logger sink FIRST (before anything else may log)
    PixelLogger.init(this)

// 2. Observe the verbose-logging toggle and drive PixelLogger
startupScope.launch {
    try {
        userPreferencesRepository.get().verboseLoggingEnabledFlow.collect { enabled ->
            PixelLogger.setEnabled(enabled)
        }
    } catch (t: Throwable) {
        android.util.Log.e("PM-BOOT", "verboseLogging collector crashed", t)
    }
}

    MediaItemBuilder.initialize(this)
    com.saurav.pixelmusic.data.session.ListenTogetherFirebase.init(this)

    val smartImageEntryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
        this,
        com.saurav.pixelmusic.presentation.components.SmartImageEntryPoint::class.java
    )
    com.saurav.pixelmusic.presentation.components.SmartImageCache.initialize(
        smartImageEntryPoint.connectivityStateHolder(),
        smartImageEntryPoint.userPreferencesRepository()
    )

    val newPipeHttpClient = OkHttpClient.Builder()
        .addInterceptor(PixelHttpLoggingInterceptor("newpipe"))
        .build()
        // Device locale/country, mirroring the NewPipe app: the content country
        // influences which streams YouTube considers available for this region.
        val deviceLocale = Locale.getDefault()
        val deviceCountry = deviceLocale.country.takeIf { it.isNotBlank() }
        val newPipeLocalization = Localization(deviceLocale.language, deviceCountry)
        val newPipeContentCountry = ContentCountry(deviceCountry?.uppercase() ?: "US")
        NewPipe.init(object : Downloader() {
            override fun execute(request: Request): Response {
                val builder = okhttp3.Request.Builder().url(request.url())
                var hasUserAgent = false
                request.headers()?.forEach { (key, values) ->
                    if (key.equals("User-Agent", ignoreCase = true)) hasUserAgent = true
                    values.forEach { builder.addHeader(key, it) }
                }
                // The NewPipe app forces a desktop Firefox UA so YouTube treats the
                // requests as a regular browser; extractor-supplied headers still win.
                if (!hasUserAgent) {
                    builder.header("User-Agent", NEWPIPE_DESKTOP_UA)
                }
                if (request.httpMethod() == "POST") {
                    val body = request.dataToSend() ?: ByteArray(0)
                    builder.post(body.toRequestBody(null))
                }
                val okHttpResponse = newPipeHttpClient.newCall(builder.build()).execute()
                val headersMap = mutableMapOf<String, List<String>>()
                okHttpResponse.headers.names().forEach { name ->
                    headersMap[name] = okHttpResponse.headers.values(name)
                }
                return Response(okHttpResponse.code, okHttpResponse.message, headersMap, okHttpResponse.body.string(), okHttpResponse.request.url.toString())
            }
        }, newPipeLocalization, newPipeContentCountry)
        // NewPipe-app-style PO tokens: without a registered provider the extractor
        // cannot attach &pot= params to stream URLs and more of them 403.
        NewPipePoTokenProvider.initialize(PoTokenGenerator.shared(this))
        YoutubeStreamExtractor.setPoTokenProvider(NewPipePoTokenProvider)

        // Initialize Last.fm client
        com.saurav.pixelmusic.data.lastfm.LastFM.initialize(
            apiKey = BuildConfig.LASTFM_API_KEY,
            secret = BuildConfig.LASTFM_SECRET
        )
        startupScope.launch {
            val prefs = userPreferencesRepository.get()
            val savedApiKey = prefs.lastfmApiKeyFlow.first()
            val savedSecret = prefs.lastfmApiSecretFlow.first()
            if (savedApiKey.isNotEmpty() && savedSecret.isNotEmpty()) {
                com.saurav.pixelmusic.data.lastfm.LastFM.initialize(
                    apiKey = savedApiKey,
                    secret = savedSecret
                )
            }
            val sessionKey = prefs.lastfmSessionFlow.first()
            if (sessionKey.isNotEmpty()) {
                com.saurav.pixelmusic.data.lastfm.LastFM.sessionKey = sessionKey
            }
        }

        // Bind Content Language and Country to YouTube.locale
        startupScope.launch {
            kotlinx.coroutines.flow.combine(
                userPreferencesRepository.get().contentLanguageFlow,
                userPreferencesRepository.get().contentCountryFlow
            ) { language, country ->
                saurav.shru.pixelmusic.innertube.models.YouTubeLocale(
                    gl = country,
                    hl = language
                )
            }.collect { locale ->
                saurav.shru.pixelmusic.innertube.YouTube.locale = locale
            }
        }

        com.saurav.pixelmusic.utils.InnerTubeXPlayer.initialize(this)

        startupScope.launch {
            if (saurav.shru.pixelmusic.innertube.YouTube.visitorData.isNullOrBlank()) {
                val fresh = com.saurav.pixelmusic.utils.InnerTubeXPlayer.getFreshVisitorData()
                if (!fresh.isNullOrBlank()) {
                    saurav.shru.pixelmusic.innertube.YouTube.visitorData = fresh
                }
            }
        }

        // Benchmark variant intentionally restarts/kills app process during tests.
        // Avoid persisting those events as user-facing crash reports.
        if (BuildConfig.BUILD_TYPE != "benchmark") {
            CrashHandler.install(this)
        }

        Timber.uprootAll()
        Timber.plant(ReleaseTree())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "PixelMusic Music Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleObserver)

        // DNS pre-warming
        startupScope.launch {
            try {
                java.net.InetAddress.getAllByName("music.youtube.com")
                java.net.InetAddress.getAllByName("googlevideo.com")
            } catch (e: Exception) {
                Timber.w(e, "DNS pre-warming failed")
            }
        }

        startupScope.launch {
            AlbumArtUtils.migrateLegacyCacheLocation(this@PixelMusicApplication)
            val savedLimit = runCatching {
                userPreferencesRepository.get().albumArtCacheLimitMbFlow.first()
            }.getOrNull()
            if (savedLimit != null) {
                AlbumArtCacheManager.configuredCacheLimitMb = savedLimit.toLong()
            }
        }

        // Schedule the daily update checker
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val updateRequest = PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "PixelMusicUpdateCheck",
            ExistingPeriodicWorkPolicy.KEEP,
            updateRequest
        )
    }

    override fun newImageLoader(): ImageLoader {
        return imageLoader.get().newBuilder()
            .memoryCache {
                coil.memory.MemoryCache.Builder(this)
                    .maxSizePercent(0.15) 
                    .build()
            }
            .diskCache {
                coil.disk.DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02) 
                    .build()
            }
            .components {
                add(localArtworkCoilFetcherFactory.get())
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    add(coil.decode.ImageDecoderDecoder.Factory())
                } else {
                    add(coil.decode.GifDecoder.Factory())
                }
            }
            .build()
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        imageLoader.get().memoryCache?.trimMemory(level)

        if (
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE ||
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
            level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
        ) {
            themeStateHolder.get().trimMemory(level)
        }

        if (
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
            level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
        ) {
            artistImageRepository.get().clearCache()
            MediaMetadataRetrieverPool.clear()
            imageLoader.get().memoryCache?.clear()
        }

        libraryStateHolder.get().trimMemory(level)

        if (
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE
        ) {
            imageLoader.get().memoryCache?.clear()
        }
    }

    // 3. Sobrescribe el método para proveer la configuración de WorkManager
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

}
