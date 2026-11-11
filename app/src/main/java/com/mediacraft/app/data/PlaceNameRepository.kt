package com.mediacraft.app.data

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.annotation.RequiresApi
import com.mediacraft.app.domain.GeoCoordinates
import com.mediacraft.app.domain.PlaceName
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class PlaceNameRepository internal constructor(
    private val localeTag: String,
    private val lookup: suspend (latitude: Double, longitude: Double) -> PlaceName?,
) {
    constructor(context: Context, locale: Locale = Locale.getDefault()) : this(
        localeTag = locale.toLanguageTag(),
        lookup = geocoderLookup(context.applicationContext, locale),
    )

    private val cache = object : LinkedHashMap<CacheKey, PlaceName>(CACHE_CAPACITY, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, PlaceName>?): Boolean =
            size > CACHE_CAPACITY
    }
    private val cacheMutex = Mutex()

    suspend fun resolve(coordinates: GeoCoordinates): PlaceName? {
        val key = CacheKey(coordinates.latitude, coordinates.longitude, localeTag)
        cacheMutex.withLock { cache[key] }?.let { return it }

        val resolved = lookup(coordinates.latitude, coordinates.longitude) ?: return null
        cacheMutex.withLock { cache[key] = resolved }
        return resolved
    }

    private data class CacheKey(
        val latitude: Double,
        val longitude: Double,
        val localeTag: String,
    )

    private companion object {
        private const val CACHE_CAPACITY = 64

        fun geocoderLookup(context: Context, locale: Locale): suspend (Double, Double) -> PlaceName? {
            val geocoder = Geocoder(context, locale)
            return { latitude, longitude ->
                check(Geocoder.isPresent()) { "当前设备没有可用的地名解析服务" }
                val address = if (Build.VERSION.SDK_INT >= 33) {
                    lookupAsync(geocoder, latitude, longitude)
                } else {
                    lookupBlocking(geocoder, latitude, longitude)
                }
                address?.toPlaceName()
            }
        }

        @Suppress("DEPRECATION")
        suspend fun lookupBlocking(geocoder: Geocoder, latitude: Double, longitude: Double): Address? =
            withContext(Dispatchers.IO) { geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull() }

        @RequiresApi(33)
        suspend fun lookupAsync(geocoder: Geocoder, latitude: Double, longitude: Double): Address? =
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        if (continuation.isActive) continuation.resume(addresses.firstOrNull())
                    }

                    override fun onError(errorMessage: String?) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                IllegalStateException(errorMessage?.takeIf(String::isNotBlank) ?: "地名解析失败"),
                            )
                        }
                    }
                })
            }

        fun Address.toPlaceName(): PlaceName {
            val title = listOf(countryName, adminArea, locality, subAdminArea, featureName)
                .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
                .distinct()
                .joinToString(" · ")
                .takeIf(String::isNotBlank)
                ?: throw IllegalStateException("地名解析结果缺少可显示的地点名称")
            val address = getAddressLine(0)?.trim()?.takeIf { it.isNotBlank() && it != title }
            return PlaceName(title = title, address = address)
        }
    }
}
