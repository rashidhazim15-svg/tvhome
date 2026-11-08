package com.hamad.tvhome.data

import com.hamad.tvhome.launcher.CardStyle
import com.hamad.tvhome.launcher.HeroSlide
import com.hamad.tvhome.launcher.MediaItem
import com.hamad.tvhome.launcher.MediaType
import com.hamad.tvhome.launcher.Provider
import com.hamad.tvhome.launcher.Shelf
import com.hamad.tvhome.launcher.TabContent
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class TmdbRepository(
    private val client: TmdbClient,
    private val prefs: TvPrefs,
) {
    val enabled: Boolean get() = client.enabled

    suspend fun home(installedPackages: Set<String>): TabContent = coroutineScope {
        val region = prefs.region
        val trendingDeferred = async { client.trending() }
        val popularMoviesDeferred = async { client.popular(MediaType.MOVIE) }
        val popularTvDeferred = async { client.popular(MediaType.TV) }

        val providers = Provider.entries.filter { it.packageName in installedPackages }
            .take(2)
        val providerRows = providers.map { provider ->
            async { provider to client.providerTop(provider, region, MediaType.MOVIE) }
        }

        val trending = trendingDeferred.await()
        val popularMovies = popularMoviesDeferred.await()
        val popularTv = popularTvDeferred.await()
        val providerResults = providerRows.awaitAll()

        val shelves = ArrayList<Shelf>()
        shelves += Shelf("trending", "Trending Now", CardStyle.Wide, trending.take(24))
        providerResults.forEach { (provider, items) ->
            if (items.isNotEmpty()) {
                shelves += Shelf(
                    "provider_${provider.name}",
                    "Top on ${provider.displayName}",
                    CardStyle.Wide,
                    items.take(24),
                )
            }
        }
        if (popularMovies.isNotEmpty()) {
            shelves += Shelf("popular_movies", "Popular Movies", CardStyle.Poster, popularMovies.take(24))
        }
        if (popularTv.isNotEmpty()) {
            shelves += Shelf("popular_tv", "Popular TV Shows", CardStyle.Poster, popularTv.take(24))
        }

        val heroSeeds = ArrayList<Pair<MediaItem, String>>()
        trending.take(5).forEach { heroSeeds += it to "Trending" }
        providerResults.forEach { (provider, items) ->
            items.take(2).forEach { heroSeeds += it to "Featured on ${provider.displayName}" }
        }
        TabContent(hero = buildHero(heroSeeds), shelves = shelves)
    }

    suspend fun movies(): TabContent = coroutineScope {
        val popular = async { client.popular(MediaType.MOVIE) }
        val trending = async { client.trending() }
        val topRated = async { client.topRated(MediaType.MOVIE) }
        val action = async { client.discover(MediaType.MOVIE, 28) }
        val comedy = async { client.discover(MediaType.MOVIE, 35) }
        val drama = async { client.discover(MediaType.MOVIE, 18) }
        val scifi = async { client.discover(MediaType.MOVIE, 878) }

        val popularList = popular.await()
        val trendingMovies = trending.await().filter { it.type == MediaType.MOVIE }
        val shelves = listOfNotNull(
            trendingMovies.takeIf { it.isNotEmpty() }?.let { Shelf("trending_movies", "Trending Now", CardStyle.Wide, it.take(24)) },
            topRated.await().takeIf { it.isNotEmpty() }?.let { Shelf("top_rated_movies", "Top Rated", CardStyle.Poster, it.take(24)) },
            action.await().takeIf { it.isNotEmpty() }?.let { Shelf("genre_action", "Action & Adventure", CardStyle.Poster, it.take(24)) },
            comedy.await().takeIf { it.isNotEmpty() }?.let { Shelf("genre_comedy", "Comedy", CardStyle.Poster, it.take(24)) },
            drama.await().takeIf { it.isNotEmpty() }?.let { Shelf("genre_drama", "Drama", CardStyle.Poster, it.take(24)) },
            scifi.await().takeIf { it.isNotEmpty() }?.let { Shelf("genre_scifi", "Science Fiction", CardStyle.Poster, it.take(24)) },
        )
        val seed = if (trendingMovies.isNotEmpty()) trendingMovies else popularList
        TabContent(
            hero = buildHero(seed.take(5).map { it to (if (it.trending) "Trending" else "Popular Movies") }),
            shelves = shelves,
        )
    }

    suspend fun tv(): TabContent = coroutineScope {
        val popular = async { client.popular(MediaType.TV) }
        val trending = async { client.trending() }
        val topRated = async { client.topRated(MediaType.TV) }
        val action = async { client.discover(MediaType.TV, 10759) }
        val comedy = async { client.discover(MediaType.TV, 35) }
        val drama = async { client.discover(MediaType.TV, 18) }
        val scifi = async { client.discover(MediaType.TV, 10765) }

        val popularList = popular.await()
        val trendingShows = trending.await().filter { it.type == MediaType.TV }
        val shelves = listOfNotNull(
            trendingShows.takeIf { it.isNotEmpty() }?.let { Shelf("trending_tv", "Trending Now", CardStyle.Wide, it.take(24)) },
            topRated.await().takeIf { it.isNotEmpty() }?.let { Shelf("top_rated_tv", "Top Rated", CardStyle.Poster, it.take(24)) },
            action.await().takeIf { it.isNotEmpty() }?.let { Shelf("genre_tv_action", "Action & Adventure", CardStyle.Poster, it.take(24)) },
            comedy.await().takeIf { it.isNotEmpty() }?.let { Shelf("genre_tv_comedy", "Comedy", CardStyle.Poster, it.take(24)) },
            drama.await().takeIf { it.isNotEmpty() }?.let { Shelf("genre_tv_drama", "Drama", CardStyle.Poster, it.take(24)) },
            scifi.await().takeIf { it.isNotEmpty() }?.let { Shelf("genre_tv_scifi", "Sci-Fi & Fantasy", CardStyle.Poster, it.take(24)) },
        )
        val seed = if (trendingShows.isNotEmpty()) trendingShows else popularList
        TabContent(
            hero = buildHero(seed.take(5).map { it to (if (it.trending) "Trending" else "Popular TV Shows") }),
            shelves = shelves,
        )
    }

    suspend fun search(query: String): List<MediaItem> = client.searchMulti(query)

    suspend fun details(item: MediaItem): MediaItem = client.details(item.tmdbId, item.type)

    private suspend fun buildHero(seeds: List<Pair<MediaItem, String>>): List<HeroSlide> {
        val seen = HashSet<String>()
        val deduped = seeds.filter { seen.add("${it.first.type}-${it.first.tmdbId}") }
        val withLogos = client.withLogos(deduped.map { it.first })
        return deduped.mapIndexed { index, (item, attribution) ->
            HeroSlide(withLogos.getOrNull(index) ?: item, attribution)
        }
    }
}
