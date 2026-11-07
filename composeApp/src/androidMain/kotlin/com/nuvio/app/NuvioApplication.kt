package com.nuvio.app

import android.app.Application
import android.os.Build
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.memory.MemoryCache
import coil3.network.CacheStrategy
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.nuvio.app.core.poster.CustomPosterFallbackInterceptor
import com.nuvio.app.core.poster.RetryingImageInterceptor
import okio.Path.Companion.toOkioPath
import java.io.File

/**
 * Custom Application class that implements [SingletonImageLoader.Factory] to guarantee
 * the Coil ImageLoader (with [CustomPosterFallbackInterceptor]) is created before any
 * image request. Without this, Coil's auto-init ContentProvider creates the singleton
 * before MainActivity.onCreate, and interceptors registered later are ignored.
 */
class NuvioApplication : Application(), SingletonImageLoader.Factory {

    override fun newImageLoader(context: android.content.Context): ImageLoader {
        return ImageLoader.Builder(context)
            .crossfade(true)
            // Artwork is the bulk of what this app shows. Keep a generous memory cache so posters
            // survive scrolling, and a large disk cache so an evicted bitmap comes back instantly
            // instead of hitting the network again.
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(cacheDir, "image_cache").toOkioPath())
                    .maxSizeBytes(512L * 1024L * 1024L)
                    .build()
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .components {
                // Outermost: retries transient network failures so a poster that failed once while
                // scrolling does not stay blank for the rest of the session.
                add(RetryingImageInterceptor())
                add(CustomPosterFallbackInterceptor())
                add(SvgDecoder.Factory())
                add(
                    KtorNetworkFetcherFactory(
                        // Always cache artwork: poster CDNs are immutable, and respecting a
                        // "no-cache" header here means re-downloading the same image constantly.
                        cacheStrategy = { CacheStrategy.DEFAULT },
                    ),
                )
                if (Build.VERSION.SDK_INT >= 28) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }
}
