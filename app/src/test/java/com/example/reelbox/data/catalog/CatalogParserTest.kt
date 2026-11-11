package com.example.reelbox.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CatalogParserTest {
    private val sample = """
    {"credits":[{"film":"F","author":"A","license":"CC BY 3.0","licenseUrl":"u","url":"x"}],
     "titles":[
      {"id":"m1","type":"movie","title":"Movie","year":2010,"genres":["Sci-Fi"],"maturity":"U/A 13+",
       "description":"d","cast":["X"],"featured":true,"trendingRank":2,"poster":"media/m1_poster.jpg",
       "backdrop":"media/m1_backdrop.jpg","video":"media/m1.mp4","durationMs":70000,"introStartMs":0,
       "introEndMs":0,"subtitles":null},
      {"id":"s1","type":"series","title":"Show","year":2025,"genres":["Kids"],"maturity":"U",
       "description":"d","cast":[],"featured":false,"trendingRank":1,"poster":"p","backdrop":"b",
       "episodes":[
        {"id":"e2","season":1,"number":2,"title":"Two","description":"","thumbnail":"t2","video":"v2",
         "durationMs":50000,"introStartMs":0,"introEndMs":12000,"subtitles":"media/e2.srt"},
        {"id":"e1","season":1,"number":1,"title":"One","description":"","thumbnail":"t1","video":"v1",
         "durationMs":50000,"introStartMs":0,"introEndMs":12000,"subtitles":null}]}]}
    """.trimIndent()

    @Test fun parsesMovieFields() {
        val m = CatalogParser.parse(sample).title("m1")!!
        assertEquals(TitleType.MOVIE, m.type)
        assertEquals(Maturity.UA13, m.maturity)
        assertEquals(70_000L, m.media!!.durationMs)
        assertNull(m.media!!.subtitles)
        assertTrue(m.featured)
    }

    @Test fun parsesSeriesSortedEpisodesAndNext() {
        val c = CatalogParser.parse(sample)
        val s = c.title("s1")!!
        assertEquals(listOf("e1", "e2"), s.episodes.map { it.id })
        assertEquals("media/e2.srt", s.episodes[1].media.subtitles)
        assertTrue(s.episodes[0].media.hasIntro)
        assertEquals("e2", c.nextEpisode("e1")!!.id)
        assertNull(c.nextEpisode("e2"))
        assertEquals(100_000L, s.totalDurationMs)
    }

    @Test fun parsesCredits() = assertEquals("CC BY 3.0", CatalogParser.parse(sample).credits.single().license)

    /** The committed catalog must satisfy requirements §3. */
    @Test fun bundledCatalogMeetsContentRequirements() {
        val file = listOf("src/main/assets/catalog.json", "app/src/main/assets/catalog.json").map(::File).first { it.exists() }
        val c = CatalogParser.parse(file.readText())
        assertTrue(c.titles.count { it.type == TitleType.MOVIE } >= 6)
        val series = c.titles.filter { it.isSeries }
        assertTrue(series.size >= 2 && series.all { it.episodes.size >= 3 })
        assertTrue(c.genres.size >= 4)
        assertTrue(c.titles.count { t -> t.media?.subtitles != null || t.episodes.any { it.media.subtitles != null } } >= 2)
        val all = c.titles.mapNotNull { it.media } + c.titles.flatMap { t -> t.episodes.map { it.media } }
        assertTrue(all.all { it.durationMs in 45_000..120_500 })
        assertEquals(c.titles.size, c.titles.map { it.id }.toSet().size)
    }
}
