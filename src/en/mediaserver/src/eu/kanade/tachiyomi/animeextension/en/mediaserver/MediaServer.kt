package eu.kanade.tachiyomi.animeextension.en.mediaserver

import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import okhttp3.Request

class MediaServer : AnimeHttpSource() {
    override val name = "MediaServer"
    override val baseUrl = "https://example.com"
    override val lang = "en"
    override val supportsLatest = false

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request = throw UnsupportedOperationException()
    override fun searchAnimeParse(response: Response): AnimesPage = throw UnsupportedOperationException()
    override fun animeDetailsParse(response: Response): SAnime = throw UnsupportedOperationException()
    override fun episodeListParse(response: Response): List<SEpisode> = throw UnsupportedOperationException()
    override fun videoListParse(response: Response): List<Video> = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): AnimesPage = throw UnsupportedOperationException()
    override fun videoUrlParse(document: org.jsoup.nodes.Document): String = throw UnsupportedOperationException()
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun List<Video>.sort(): List<Video> = this
}
