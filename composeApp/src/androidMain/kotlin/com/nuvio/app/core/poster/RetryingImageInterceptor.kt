package com.nuvio.app.core.poster

import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageResult
import kotlinx.coroutines.delay
import java.io.IOException

/**
 * Retries artwork requests that failed for a transient reason (connection reset, timeout, DNS
 * hiccup, CDN throttling). Without this a single failed request leaves the image blank until the
 * composable is disposed, which is what makes posters "disappear" after scrolling around.
 */
class RetryingImageInterceptor(
    private val maxAttempts: Int = 3,
    private val initialDelayMs: Long = 200L,
) : Interceptor {

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        var attempt = 1
        var result = chain.proceed()
        while (result is ErrorResult && attempt < maxAttempts && result.isRetryable()) {
            delay(initialDelayMs * attempt)
            attempt += 1
            result = chain.proceed()
        }
        return result
    }

    private fun ErrorResult.isRetryable(): Boolean {
        val cause = throwable
        return cause is IOException || cause?.cause is IOException
    }
}
