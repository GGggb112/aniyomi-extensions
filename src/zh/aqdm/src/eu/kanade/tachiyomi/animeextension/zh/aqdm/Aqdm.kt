package eu.kanade.tachiyomi.animeextension.zh.aqdm

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLDecoder
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class Aqdm : AnimeHttpSource() {

    override val baseUrl: String
        get() = "https://vip.aqdm609.com:20844"

    override val lang: String
        get() = "zh"

    override val name: String
        get() = "爱情岛论坛"

    override val supportsLatest: Boolean
        get() = true

    // ===== SSL 忽略自签名证书 =====
    override val client: OkHttpClient by lazy {
        val trustAllCerts = arrayOf<TrustManager>(
            object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
            }
        )
        val sslContext = SSLContext.getInstance("TLSv1.2").apply {
            init(null, trustAllCerts, SecureRandom())
        }
        network.client.newBuilder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    // ===== sojson.v4 解密 =====
    private fun decodeHtml(raw: String): String {
        val match = Regex("var html = '([^']+)'").find(raw)
            ?: return raw
        val encoded = match.groupValues[1]
        val reversed = encoded.reversed()
        return try {
            URLDecoder.decode(reversed, "UTF-8")
        } catch (e: Exception) {
            raw
        }
    }

    private fun fetchDoc(response: Response): Document {
        val raw = response.body?.string() ?: return Jsoup.parse("<html></html>")
        val decoded = decodeHtml(raw)
        return Jsoup.parse(decoded)
    }

    // ===== 搜索（含分类/标签路由）=====
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        // 分类过滤
        val categoryFilter = filters.firstOrNull { it is CategoryFilter } as? CategoryFilter
        val tagFilter = filters.firstOrNull { it is TagFilter } as? TagFilter

        val catPath = when (categoryFilter?.selected) {
            1 -> "cn"       // 国产
            2 -> "hk"       // 香港
            3 -> "jp"       // 日本
            4 -> "kr"       // 韩国
            5 -> "southeast-asia" // 东南亚
            6 -> "tw"       // 台湾
            7 -> "west"     // 欧美
            else -> null
        }

        val tagPath = tagFilter?.state?.takeIf { it.isNotBlank() }

        return when {
            tagPath != null -> {
                GET("$baseUrl/videos/tag/$tagPath.html", headers)
            }
            catPath != null -> {
                if (page <= 1) GET("$baseUrl/videos/category/$catPath.html", headers)
                else GET("$baseUrl/videos/category/$catPath/page/$page.html", headers)
            }
            query.isNotBlank() -> {
                val encoded = java.net.URLEncoder.encode(query, "UTF-8")
                GET("$baseUrl/videos/search.html?key=$encoded&page=$page", headers)
            }
            else -> latestUpdatesRequest(page)
        }
    }

    override fun searchAnimeParse(response: Response): AnimesPage {
        return parseListPage(fetchDoc(response))
    }

    // ===== 最新/热门 =====
    override fun latestUpdatesRequest(page: Int): Request {
        return if (page <= 1) GET(baseUrl, headers)
        else GET("$baseUrl/videos/index/$page.html", headers)
    }

    override fun latestUpdatesParse(response: Response): AnimesPage {
        return parseListPage(fetchDoc(response))
    }

    override fun popularAnimeRequest(page: Int): Request = latestUpdatesRequest(page)
    override fun popularAnimeParse(response: Response): AnimesPage = latestUpdatesParse(response)

    // ===== 列表页解析 =====
    private fun parseListPage(doc: Document): AnimesPage {
        val items = doc.select("div.video-item, li.video-item, div[class*=video-item]")
        val animeList = items.map { item ->
            SAnime.create().apply {
                // 找链接
                val linkEl = item.selectFirst("a[href*=play]")
                    ?: item.selectFirst("a.thumb-link")
                    ?: item.selectFirst("a")
                val href = linkEl?.attr("href") ?: ""
                url = if (href.startsWith("http")) href else baseUrl + href

                // 标题
                title = item.select(".video-title, .title, h3, h4").text()
                    .ifEmpty { linkEl?.attr("title") ?: "" }
                    .ifEmpty { linkEl?.text() ?: "" }

                // 缩略图
                thumbnail_url = item.selectFirst("img")?.attr("data-original")
                    ?: item.selectFirst("img")?.attr("data-src")
                    ?: item.selectFirst("img")?.attr("src")
                    ?: ""
            }
        }

        val hasNext = doc.select("a.older-posts, a.next, .pagination a:contains(下一)").isNotEmpty()
        return AnimesPage(animeList, hasNext)
    }

    // ===== 动漫详情 =====
    override fun animeDetailsParse(response: Response): SAnime {
        val doc = fetchDoc(response)
        val rawTitle = doc.select("title").text()
        val title = rawTitle.substringBefore(" - ").trim()
        return SAnime.create().apply {
            this.title = title.ifEmpty { rawTitle }
            description = doc.select(".video-description, .description, .info, .detail").text()
            genre = doc.select(".tags a, .tag a, a[href*=tag]").joinToString(", ") { it.text() }
        }
    }

    // ===== 集数列表 =====
    override fun episodeListParse(response: Response): List<SEpisode> {
        val doc = fetchDoc(response)
        val title = doc.select("title").text().substringBefore(" - ").trim()
        return listOf(
            SEpisode.create().apply {
                name = title
                url = response.request.url.toString()
            }
        )
    }

    // ===== 视频列表 =====
    override fun videoListParse(response: Response): List<Video> {
        val doc = fetchDoc(response)
        val html = doc.html()

        // 提取 m3u8 地址
        val m3u8Regex = Regex("""https?://[^\s"'<>]+\.m3u8[^\s"'<>]*""")
        val m3u8Url = m3u8Regex.find(html)?.value ?: run {
            // 备用：找包含 playlist 或 video 的 CDN链接
            val altRegex = Regex("""https?://cdn\.aqd-tv\.com[^\s"'<>]+""")
            altRegex.find(html)?.value ?: ""
        }

        return if (m3u8Url.isNotBlank()) {
            listOf(Video(m3u8Url, "HLS", videoUrl = m3u8Url))
        } else {
            emptyList()
        }
    }

    override fun videoUrlParse(response: Response): String {
        val doc = fetchDoc(response)
        val html = doc.html()
        return Regex("""https?://[^\s"'<>]+\.m3u8[^\s"'<>]*""").find(html)?.value
            ?: ""
    }

    // ===== 过滤器 =====
    override fun getFilterList(): AnimeFilterList {
        return AnimeFilterList(
            CategoryFilter(),
            TagFilter(),
        )
    }

    // 分类过滤器
    class CategoryFilter : AnimeFilter.Select<String>(
        "分类",
        "category",
        arrayOf(
            "全部", "国产", "香港", "日本", "韩国", "东南亚", "台湾", "欧美"
        ),
        0
    )

    // 标签过滤器
    class TagFilter : AnimeFilter.Text("标签")

    // ===== Headers =====
    override fun headersBuilder(): okhttp3.Headers.Builder {
        return okhttp3.Headers.Builder().apply {
            add("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            add("Accept-Language", "zh-CN,zh;q=0.9")
        }
    }
}
