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
            },
        )
        val sslContext = SSLContext.getInstance("TLSv1.2").apply {
            init(null, trustAllCerts, SecureRandom())
        }
        network.client.newBuilder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    // ===== sojson.v4 解密（原始HTML → 解码HTML）=====
    private fun decodeHtml(raw: String): String {
        val match = Regex("var html = '([^']+)'").find(raw) ?: return raw
        return try {
            URLDecoder.decode(match.groupValues[1].reversed(), "UTF-8")
        } catch (_: Exception) {
            raw
        }
    }

    // ===== 从 Response 获取解码后的 Document =====
    private fun Response.parseDoc() =
        decodeHtml(body.string()).let { org.jsoup.Jsoup.parse(it) }

    // ===== Tag slugs（完整69个）=====
    private val tagSlugs = arrayOf(
        "", "uncensored", "cartoon", "zipai", "toupai", "caption", "hotel", "ktv",
        "classroom", "office", "outside", "wc", "car", "home", "woman", "student",
        "teacher", "lady", "ol", "model", "celebrity", "anchor", "mother", "sister",
        "big-breast", "beauty-breast", "big-ass", "beauty-back", "powder", "solppy",
        "virgin", "beauty-leg", "black-big", "sm", "bundling", "foot-love", "rape",
        "homosexual", "adultery", "fornication", "uniform", "stockings", "nurse",
        "cosplay", "hostess", "sexy-ingerie", "back-fuck", "injection", "masturbation",
        "oral-copulation", "deep-throat", "spray-tide", "group", "breast-fuck",
        "yan-shot", "anal-copulation", "vegetarian", "pure", "hairless", "avlady",
        "bestiality", "loli", "star", "toys", "shemale", "other", "massage",
        "exhibitionism", "chest-shot",
    )

    // ===== 搜索路由 =====
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val catFilter = filters.firstOrNull { it is CategoryFilter } as? CategoryFilter
        val tagFilter = filters.firstOrNull { it is TagFilter } as? TagFilter

        val catPath = when (catFilter?.state) {
            1 -> "cn"; 2 -> "hk"; 3 -> "jp"; 4 -> "kr"
            5 -> "southeast-asia"; 6 -> "tw"; 7 -> "west"
            else -> null
        }

        val tagIdx = tagFilter?.state ?: 0
        val tagSlug = if (tagIdx in 1 until tagSlugs.size) tagSlugs[tagIdx] else null

        val url = when {
            tagSlug != null -> {
                if (page <= 1) {
                    "$baseUrl/videos/tag/$tagSlug.html"
                } else {
                    "$baseUrl/videos/tag/$tagSlug/$page.html"
                }
            }
            catPath != null -> {
                if (page <= 1) {
                    "$baseUrl/videos/category/$catPath.html"
                } else {
                    "$baseUrl/videos/category/$catPath/$page.html"
                }
            }
            query.isNotBlank() -> {
                val encoded = java.net.URLEncoder.encode(query, "UTF-8")
                "$baseUrl/videos/search.html?key=$encoded&page=$page"
            }
            else -> return latestUpdatesRequest(page)
        }
        return GET(url, headers)
    }

    override fun searchAnimeParse(response: Response): AnimesPage = parseListPage(response.parseDoc())

    // ===== 最新/热门 =====
    override fun latestUpdatesRequest(page: Int): Request =
        GET(if (page <= 1) baseUrl else "$baseUrl/videos/index/$page.html", headers)

    override fun latestUpdatesParse(response: Response): AnimesPage = parseListPage(response.parseDoc())

    override fun popularAnimeRequest(page: Int): Request = latestUpdatesRequest(page)

    override fun popularAnimeParse(response: Response): AnimesPage = latestUpdatesParse(response)

    // ===== 列表页解析 =====
    private fun parseListPage(doc: org.jsoup.nodes.Document): AnimesPage {
        val items = doc.select("div.videos-item")
        val animeList = items.map { item ->
            SAnime.create().apply {
                val linkEl = item.selectFirst("a.thumbnail-cover-link")
                    ?: item.selectFirst("a[href*=play]")
                val href = linkEl?.attr("href") ?: ""
                url = if (href.startsWith("http")) href else baseUrl + href

                val titleEl = item.selectFirst(".videos-title a")
                title = titleEl?.attr("title")?.trim()
                    ?: titleEl?.text()?.trim()
                    ?: linkEl?.attr("alt")?.trim() ?: ""

                val imgEl = item.selectFirst("img.videos-thumbnail") ?: item.selectFirst("img")
                thumbnail_url = imgEl?.attr("data-original")
                    ?: imgEl?.attr("data-src")
                    ?: imgEl?.attr("src") ?: ""
            }
        }
        return AnimesPage(animeList, doc.select("a.older-posts").isNotEmpty())
    }

    // ===== 动漫详情 =====
    override fun animeDetailsParse(response: Response): SAnime {
        val doc = response.parseDoc()
        val rawTitle = doc.select("title").text()
        val title = rawTitle.substringBefore(" - ").trim()
        return SAnime.create().apply {
            this.title = title.ifEmpty { rawTitle }
            description = doc.select("meta[property=og:description]").attr("content")
                .ifEmpty { doc.select(".video-description, .detail-video-description").text() }
                .ifEmpty { doc.select("meta[name=description]").attr("content") }
            genre = doc.select("a[href*=tag]").joinToString(", ") { it.text().trim() }
            val views = doc.select(".detail-video-views").text()
            if (views.isNotBlank()) description = "$views\n$description"
        }
    }

    // ===== 剧集列表（重写 Request 添加 Referer 反反爬）=====
    override fun episodeListRequest(episode: SAnime): Request {
        val pageUrl = if (episode.url.startsWith("http")) episode.url else baseUrl + episode.url
        return GET(
            pageUrl,
            headers.newBuilder()
                .add("Referer", baseUrl + "/")
                .build(),
        )
    }

    override fun episodeListParse(response: Response): List<SEpisode> {
        return try {
            val doc = response.parseDoc()
            val title = doc.select("title").text().substringBefore(" - ").trim()
            val path = response.request.url.encodedPath
            val query = response.request.url.encodedQuery
            val episodeUrl = if (query != null) "$path?$query" else path
            listOf(
                SEpisode.create().apply {
                    name = title
                    url = episodeUrl
                },
            )
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ===== 视频提取：加 Referer 反反爬 + M3U8 提取 =====
    override fun videoListRequest(episode: SEpisode): Request {
        // Step 1: 请求剧集页面（带 Referer）
        val pageUrl = if (episode.url.startsWith("http")) episode.url else baseUrl + episode.url
        val pageResponse = client.newCall(
            GET(
                pageUrl,
                headers.newBuilder()
                    .add("Referer", baseUrl + "/")
                    .build(),
            ),
        ).execute()
        val decoded = pageResponse.use { resp ->
            decodeHtml(resp.body?.string() ?: "")
        }

        // Step 2: 从解码后的页面提取 M3U8 URL
        val jsM3u8 = Regex("""let video_url\s*=\s*'([^']+\.m3u8[^']*)'""")
            .find(decoded)?.groupValues?.get(1)
        if (jsM3u8 != null) {
            return GET(jsM3u8, headers)
        }

        // Step 2b: 尝试从 Artplayer 配置提取
        val artM3u8 = Regex("""url\s*:\s*'([^']+\.m3u8[^']*)'""")
            .find(decoded)?.groupValues?.get(1)
        if (artM3u8 != null) {
            return GET(artM3u8, headers)
        }

        // Step 2c: 通用 M3U8 正则提取
        val m3u8Url = Regex("""https?://[^\s"'<>]+\.m3u8[^\s"'<>]*""")
            .find(decoded)?.value ?: ""
        if (m3u8Url.isNotBlank()) {
            return GET(m3u8Url, headers)
        }
        // fallback: 返回原始页面请求
        return GET(pageUrl, headers)
    }

    override fun videoListParse(response: Response): List<Video> {
        val videoUrl = response.request.url.toString()
        return if (videoUrl.contains(".m3u8")) {
            listOf(Video(videoUrl, "HLS", videoUrl))
        } else {
            emptyList()
        }
    }

    // ===== 过滤器 =====
    override fun getFilterList(): AnimeFilterList = AnimeFilterList(CategoryFilter(), TagFilter())

    class CategoryFilter : AnimeFilter.Select<String>(
        "分类",
        arrayOf("全部", "国产", "香港", "日本", "韩国", "东南亚", "台湾", "欧美"),
        0,
    )

    class TagFilter : AnimeFilter.Select<String>(
        "标签",
        arrayOf(
            "无", "无碼", "H動畫", "自拍", "偷拍", "中文字幕", "酒店", "KTV",
            "教室", "辦公室", "野外", "洗手間", "車震", "家裡", "少婦", "學生",
            "老師", "小姐", "OL", "嫩模", "網紅", "主播", "媽媽", "姐姐",
            "巨乳", "美乳", "巨臀", "美背", "三點粉", "多汁", "處女", "美腿",
            "巨屌", "SM", "捆綁", "戀足", "強姦", "同性", "迷姦", "近親相姦",
            "制服誘惑", "絲襪", "護士", "Cosplay", "空姐", "情趣內衣", "后入",
            "無套中出", "自慰", "口爆", "深喉", "潮吹", "群P", "乳交", "顏射",
            "肛交", "素人", "清純可愛", "白虎", "女優", "人獸", "蘯莉", "明星",
            "情趣玩具", "人妖", "另類", "按摩", "露出", "胸射",
        ),
        0,
    )

    // ===== Headers =====
    override fun headersBuilder() = super.headersBuilder()
        .add("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
        .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        .add("Accept-Language", "zh-CN,zh;q=0.9")
}
