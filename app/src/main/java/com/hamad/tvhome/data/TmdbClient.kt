package com.hamad.tvhome.data

import com.hamad.tvhome.BuildConfig
import com.hamad.tvhome.launcher.MediaItem
import com.hamad.tvhome.launcher.MediaType
import com.hamad.tvhome.launcher.Provider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Thin, dependency-free TMDB v4 client (Bearer read token). Only real network data
 * is ever surfaced; when the token is missing every call reports [enabled] = false and
 * the UI hides the corresponding content instead of inventing placeholders.
 */
class TmdbClient(
    private val token: String,
    http: OkHttpClient = defaultHttp(),
) {
    val enabled: Boolean get() = token.isNotBlank()

    private val client = http.newBuilder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val base = "https://api.themoviedb.org/3"

    /**
     * TMDB issues two credentials: a v4 *read access token* (long JWT, used as a Bearer
     * token) and a v3 *API key* (32 hex chars, passed as the `api_key` query parameter).
     * Both are accepted here so either value works in local.properties.
     */
    private val isApiKey = token.matches(Regex("[0-9a-fA-F]{32}"))

    suspend fun get(path: String, params: Map<String, String> = emptyMap()): JSONObject =
        withContext(Dispatchers.IO) {
            val url = StringBuilder(base).append(path)
            val allParams = if (isApiKey) params + ("api_key" to token) else params
            val query = allParams.entries.joinToString("&") { "${it.key}=${it.value}" }
            if (query.isNotEmpty()) url.append('?').append(query)
            val request = Request.Builder()
                .url(url.toString())
                .apply { if (!isApiKey) header("Authorization", "Bearer $token") }
                .header("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw TmdbException(response.code, body.take(200))
                }
                JSONObject(body)
            }
        }

    suspend fun trending(page: Int = 1): List<MediaItem> {
        val json = get("/trending/all/week", mapOf("language" to "en-US", "page" to page.toString()))
        return json.optJSONArray("results").toItems().filter { it.type in listOf(MediaType.MOVIE, MediaType.TV) }
            .map { it.copy(trending = true) }
    }

    suspend fun popular(type: MediaType, page: Int = 1): List<MediaItem> {
        val path = if (type == MediaType.MOVIE) "/movie/popular" else "/tv/popular"
        return get(path, mapOf("language" to "en-US", "page" to page.toString())).optJSONArray("results").toItems(type)
    }

    suspend fun topRated(type: MediaType, page: Int = 1): List<MediaItem> {
        val path = if (type == MediaType.MOVIE) "/movie/top_rated" else "/tv/top_rated"
        return get(path, mapOf("language" to "en-US", "page" to page.toString())).optJSONArray("results").toItems(type)
    }

    suspend fun providerTop(provider: Provider, region: String, type: MediaType, page: Int = 1): List<MediaItem> {
        val path = if (type == MediaType.MOVIE) "/discover/movie" else "/discover/tv"
        val json = get(
            path,
            mapOf(
                "language" to "en-US",
                "page" to page.toString(),
                "sort_by" to "popularity.desc",
                "watch_region" to region,
                "with_watch_providers" to provider.tmdbProviderId.toString(),
                "with_watch_monetization_types" to "flatrate",
                "vote_count.gte" to "30",
            ),
        )
        return json.optJSONArray("results").toItems(type).map { it.copy(provider = provider) }
    }

    suspend fun discover(type: MediaType, genreId: Int, page: Int = 1): List<MediaItem> {
        val path = if (type == MediaType.MOVIE) "/discover/movie" else "/discover/tv"
        val json = get(
            path,
            mapOf(
                "language" to "en-US",
                "page" to page.toString(),
                "sort_by" to "popularity.desc",
                "with_genres" to genreId.toString(),
                "vote_count.gte" to "50",
            ),
        )
        return json.optJSONArray("results").toItems(type)
    }

    suspend fun searchMulti(query: String): List<MediaItem> {
        val json = get(
            "/search/multi",
            mapOf("language" to "en-US", "query" to query, "include_adult" to "false", "page" to "1"),
        )
        return json.optJSONArray("results").toItems().filter { it.type in listOf(MediaType.MOVIE, MediaType.TV) }
    }

    suspend fun details(id: Int, type: MediaType): MediaItem {
        val path = if (type == MediaType.MOVIE) "/movie/$id" else "/tv/$id"
        val json = get(path, mapOf("language" to "en-US", "append_to_response" to "images"))
        val base = parseItem(json, type)
        val logo = json.optJSONObject("images")?.optJSONArray("logos").bestLogo()
        return base.copy(logoPath = logo)
    }

    /** Fetches English title logos for hero artwork, in parallel and best-effort. */
    suspend fun withLogos(items: List<MediaItem>): List<MediaItem> {
        if (!enabled) return items
        val logoCache = HashMap<String, String?>()
        return items.map { item ->
            val key = "${item.type}-${item.tmdbId}"
            val logo = if (logoCache.containsKey(key)) {
                logoCache[key]
            } else {
                val path = if (item.type == MediaType.MOVIE) "/movie/${item.tmdbId}/images" else "/tv/${item.tmdbId}/images"
                val value = runCatching {
                    get(path, mapOf("include_image_language" to "en,null")).optJSONArray("logos").bestLogo()
                }.getOrNull()
                logoCache[key] = value
                value
            }
            item.copy(logoPath = logo ?: item.logoPath)
        }
    }

    private fun JSONArray?.toItems(type: MediaType? = null): List<MediaItem> {
        if (this == null) return emptyList()
        val out = ArrayList<MediaItem>(length())
        for (i in 0 until length()) {
            val o = optJSONObject(i) ?: continue
            val parsedType = type ?: when (o.optString("media_type")) {
                "tv" -> MediaType.TV
                "movie" -> MediaType.MOVIE
                else -> null
            } ?: continue
            out.add(parseItem(o, parsedType))
        }
        return out
    }

    private fun parseItem(o: JSONObject, type: MediaType): MediaItem {
        val date = when (type) {
            MediaType.MOVIE -> o.optString("release_date")
            MediaType.TV -> o.optString("first_air_date")
        }
        val genreIds = o.optJSONArray("genre_ids")
        val genres = ArrayList<String>()
        if (genreIds != null) {
            for (i in 0 until genreIds.length()) {
                GenreNames.forType(type)[genreIds.optInt(i)]?.let { genres.add(it) }
            }
        }
        o.optJSONArray("genres")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.optString("name")?.takeIf { it.isNotBlank() }?.let { genres.add(it) }
            }
        }
        return MediaItem(
            tmdbId = o.optInt("id"),
            type = type,
            title = o.optString(if (type == MediaType.MOVIE) "title" else "name").ifBlank { o.optString("title") },
            logoPath = null,
            backdropPath = o.optString("backdrop_path").takeIf { it.isNotBlank() && it != "null" },
            posterPath = o.optString("poster_path").takeIf { it.isNotBlank() && it != "null" },
            year = date.take(4).takeIf { it.length == 4 },
            genres = genres.distinct(),
            overview = o.optString("overview").takeIf { it.isNotBlank() },
            rating = o.optDouble("vote_average").takeIf { !it.isNaN() && it > 0 },
        )
    }

    private fun JSONArray?.bestLogo(): String? {
        if (this == null) return null
        var best: JSONObject? = null
        var bestScore = -1f
        for (i in 0 until length()) {
            val o = optJSONObject(i) ?: continue
            val lang = o.optString("iso_639_1")
            if (lang != "en" && lang != "null" && lang.isNotEmpty()) continue
            val score = o.optDouble("vote_average", 0.0).toFloat() + if (lang == "en") 10f else 0f
            if (score > bestScore) {
                bestScore = score
                best = o
            }
        }
        return best?.optString("file_path")?.takeIf { it.isNotBlank() }
    }

    class TmdbException(val code: Int, val body: String) : Exception("TMDB HTTP $code")
}

object GenreNames {
    private val movie = mapOf(
        28 to "Action", 12 to "Adventure", 16 to "Animation", 35 to "Comedy", 80 to "Crime",
        99 to "Documentary", 18 to "Drama", 10751 to "Family", 14 to "Fantasy", 36 to "History",
        27 to "Horror", 10402 to "Music", 9648 to "Mystery", 10749 to "Romance", 878 to "Sci-Fi",
        10770 to "TV Movie", 53 to "Thriller", 10752 to "War", 37 to "Western",
    )
    private val tv = mapOf(
        10759 to "Action", 16 to "Animation", 35 to "Comedy", 80 to "Crime", 99 to "Documentary",
        18 to "Drama", 10751 to "Family", 10762 to "Kids", 9648 to "Mystery", 10763 to "News",
        10764 to "Reality", 10765 to "Sci-Fi", 10766 to "Soap", 10767 to "Talk", 10768 to "War",
        37 to "Western",
    )

    fun forType(type: MediaType): Map<Int, String> = if (type == MediaType.MOVIE) movie else tv
}

fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
    .cache(null)
    .connectTimeout(12, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .build()

object TmdbImages {
    private const val BASE = "https://image.tmdb.org/t/p"
    fun backdrop(path: String?, width: Int = 1280): String? = path?.let { "$BASE/w$width$it" }
    fun logo(path: String?, width: Int = 500): String? = path?.let { "$BASE/w$width$it" }
    fun poster(path: String?, width: Int = 342): String? = path?.let { "$BASE/w$width$it" }
    fun profile(path: String?, width: Int = 185): String? = path?.let { "$BASE/w$width$it" }
}

val TmdbConfigured: Boolean get() = BuildConfig.TMDB_READ_TOKEN.isNotBlank()
