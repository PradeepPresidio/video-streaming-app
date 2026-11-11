package com.example.reelbox.data.catalog

enum class TitleType { MOVIE, SERIES }

/** Maturity ratings, ordered from most to least permissive audience. */
enum class Maturity(val label: String) {
    U("U"), UA7("U/A 7+"), UA13("U/A 13+"), UA16("U/A 16+"), A("A");

    val kidsSafe: Boolean get() = this == U || this == UA7

    companion object {
        fun fromLabel(label: String): Maturity =
            entries.firstOrNull { it.label == label } ?: throw IllegalArgumentException("Unknown maturity rating: $label")
    }
}

/** One playable video: a movie or a single episode. Paths are relative to the assets root. */
data class MediaInfo(
    val video: String,
    val durationMs: Long,
    val introStartMs: Long,
    val introEndMs: Long,
    val subtitles: String?,
) {
    val hasIntro: Boolean get() = introEndMs > introStartMs
}

data class Episode(
    val id: String,
    val titleId: String,
    val season: Int,
    val number: Int,
    val title: String,
    val description: String,
    val thumbnail: String,
    val media: MediaInfo,
)

data class Title(
    val id: String,
    val type: TitleType,
    val title: String,
    val year: Int,
    val genres: List<String>,
    val maturity: Maturity,
    val description: String,
    val cast: List<String>,
    val poster: String,
    val backdrop: String,
    val featured: Boolean,
    val trendingRank: Int,
    /** Non-null for movies. */
    val media: MediaInfo?,
    /** Non-empty for series, sorted by season then number. */
    val episodes: List<Episode>,
) {
    val isSeries: Boolean get() = type == TitleType.SERIES
    val seasons: List<Int> get() = episodes.map { it.season }.distinct().sorted()
    val totalDurationMs: Long get() = media?.durationMs ?: episodes.sumOf { it.media.durationMs }
}

data class Credit(val film: String, val author: String, val license: String, val licenseUrl: String, val url: String)

data class Catalog(val titles: List<Title>, val credits: List<Credit>) {
    private val byId = titles.associateBy { it.id }
    private val episodesById = titles.flatMap { it.episodes }.associateBy { it.id }

    fun title(id: String): Title? = byId[id]
    fun episode(id: String): Episode? = episodesById[id]
    val genres: List<String> get() = titles.flatMap { it.genres }.distinct()

    /** The episode after [episodeId] in the same series, or null if it is the last one. */
    fun nextEpisode(episodeId: String): Episode? {
        val ep = episode(episodeId) ?: return null
        val list = title(ep.titleId)?.episodes ?: return null
        val idx = list.indexOfFirst { it.id == episodeId }
        return list.getOrNull(idx + 1)
    }
}

/** URI helpers: Coil reads file:///android_asset/…, ExoPlayer reads asset:///… */
object AssetUris {
    fun image(path: String): String = "file:///android_asset/$path"
    fun media(path: String): String = "asset:///$path"
}
