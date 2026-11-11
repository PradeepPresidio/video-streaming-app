package com.example.reelbox.data.catalog

import org.json.JSONArray
import org.json.JSONObject

/** Parses assets/catalog.json (written by tools/prepare_media.sh). */
object CatalogParser {
    fun parse(json: String): Catalog {
        val root = JSONObject(json)
        val titles = root.getJSONArray("titles").objects().map { parseTitle(it) }
        val credits = root.optJSONArray("credits")?.objects()?.map {
            Credit(it.getString("film"), it.getString("author"), it.getString("license"),
                it.optString("licenseUrl"), it.optString("url"))
        }.orEmpty()
        return Catalog(titles, credits)
    }

    private fun parseTitle(o: JSONObject): Title {
        val id = o.getString("id")
        val type = when (o.getString("type")) {
            "movie" -> TitleType.MOVIE
            "series" -> TitleType.SERIES
            else -> throw IllegalArgumentException("Unknown type for $id")
        }
        val episodes = if (type == TitleType.SERIES) {
            o.getJSONArray("episodes").objects().map { e ->
                Episode(
                    id = e.getString("id"), titleId = id, season = e.optInt("season", 1),
                    number = e.getInt("number"), title = e.getString("title"),
                    description = e.optString("description"), thumbnail = e.getString("thumbnail"),
                    media = parseMedia(e),
                )
            }.sortedWith(compareBy({ it.season }, { it.number }))
        } else emptyList()
        return Title(
            id = id, type = type, title = o.getString("title"), year = o.getInt("year"),
            genres = o.getJSONArray("genres").strings(), maturity = Maturity.fromLabel(o.getString("maturity")),
            description = o.getString("description"), cast = o.optJSONArray("cast")?.strings().orEmpty(),
            poster = o.getString("poster"), backdrop = o.getString("backdrop"),
            featured = o.optBoolean("featured"), trendingRank = o.optInt("trendingRank", Int.MAX_VALUE),
            media = if (type == TitleType.MOVIE) parseMedia(o) else null,
            episodes = episodes,
        )
    }

    private fun parseMedia(o: JSONObject) = MediaInfo(
        video = o.getString("video"),
        durationMs = o.getLong("durationMs"),
        introStartMs = o.optLong("introStartMs", 0),
        introEndMs = o.optLong("introEndMs", 0),
        subtitles = if (o.isNull("subtitles")) null else o.optString("subtitles").ifEmpty { null },
    )

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
}
