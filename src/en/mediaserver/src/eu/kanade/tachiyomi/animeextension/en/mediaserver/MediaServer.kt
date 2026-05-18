package eu.kanade.tachiyomi.animeextension.en.mediaserver

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class MediaServer : AnimeHttpSource() {

    override val baseUrl: String
        get() = "https://example.com"

    override val lang: String
        get() = "en"

    override val name: String
        get() = "MediaServer"

    override val supportsLatest: Boolean
        get() = false

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        return GET("$baseUrl/search?q=$query&page=$page", headers)
    }

    override fun searchAnimeParse(response: Response): AnimesPage {
        return AnimesPage(emptyList(), false)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/latest?page=$page", headers)
    }

    override fun latestUpdatesParse(response: Response): AnimesPage {
        return AnimesPage(emptyList(), false)
    }

    override fun popularAnimeRequest(page: Int): Request = latestUpdatesRequest(page)
    override fun popularAnimeParse(response: Response): AnimesPage = latestUpdatesParse(response)

    override fun animeDetailsParse(response: Response): SAnime {
        return SAnime.create().apply { title = "" }
    }

    override fun episodeListParse(response: Response): List<SEpisode> {
        return emptyList()
    }

    override fun videoListParse(response: Response): List<Video> {
        return emptyList()
    }

    override fun videoUrlParse(response: Response): String {
        return ""
    }

    override fun getFilterList(): AnimeFilterList {
        return AnimeFilterList()
    }
}
