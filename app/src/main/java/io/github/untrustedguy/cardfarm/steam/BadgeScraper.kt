package io.github.untrustedguy.cardfarm.steam

import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/** Thrown when the community site no longer accepts our access token. */
class WebSessionExpiredException : IOException("Steam web session expired")

/**
 * Scrapes steamcommunity.com badge pages to find games with remaining card
 * drops — the same data source ArchiSteamFarm uses.
 */
class BadgeScraper {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val sessionId: String = run {
        val bytes = ByteArray(12)
        SecureRandom().nextBytes(bytes)
        bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Fetches every badge page of the account and returns the games that have
     * trading card badges. [accessToken] is the short-lived Steam access token
     * used as the steamLoginSecure cookie.
     */
    @Throws(IOException::class, WebSessionExpiredException::class)
    fun fetchBadges(steamId64: Long, accessToken: String): List<BadgeGame> {
        val games = mutableListOf<BadgeGame>()
        var page = 1
        var totalPages = 1

        while (page <= totalPages) {
            val doc = fetchPage(steamId64, accessToken, page)

            if (page == 1) {
                totalPages = doc.select("a.pagelink")
                    .mapNotNull { it.text().trim().toIntOrNull() }
                    .maxOrNull() ?: 1
            }

            for (row in doc.select("div.badge_row")) {
                val overlayHref = row.selectFirst("a.badge_row_overlay")?.attr("href") ?: continue
                val appId = GAMECARDS_APP_ID.find(overlayHref)?.groupValues?.get(1)?.toIntOrNull()
                    ?: continue

                val name = row.selectFirst(".badge_title")?.ownText()?.trim()
                    ?.removeSuffix("View details")?.trim()
                    ?: "App $appId"

                val progressText = row.select(".progress_info_bold").text()
                val drops = DROPS_REMAINING.find(progressText)?.groupValues?.get(1)?.toIntOrNull() ?: 0

                val playtime = row.selectFirst(".badge_title_stats_playtime")?.text()?.trim()
                    .orEmpty()
                    .ifEmpty { "No playtime" }

                games.add(BadgeGame(appId, name, drops, playtime))
            }

            page++
        }

        return games.sortedWith(
            compareByDescending<BadgeGame> { it.dropsRemaining }.thenBy { it.name }
        )
    }

    private fun fetchPage(steamId64: Long, accessToken: String, page: Int): Document {
        val url = "https://steamcommunity.com/profiles/$steamId64/badges?l=english&p=$page"
        val request = Request.Builder()
            .url(url)
            .header(
                "Cookie",
                "sessionid=$sessionId; steamLoginSecure=$steamId64%7C%7C$accessToken; Steam_Language=english",
            )
            .header("User-Agent", USER_AGENT)
            .build()

        client.newCall(request).execute().use { response ->
            val finalUrl = response.request.url.toString()
            if (finalUrl.contains("/login", ignoreCase = true)) {
                throw WebSessionExpiredException()
            }
            if (!response.isSuccessful) {
                throw IOException("Badge page request failed: HTTP ${response.code}")
            }
            val body = response.body?.string() ?: throw IOException("Empty badge page response")
            if (body.contains("g_steamID = false")) {
                throw WebSessionExpiredException()
            }
            return Jsoup.parse(body, finalUrl)
        }
    }

    private companion object {
        val GAMECARDS_APP_ID = Regex("""/gamecards/(\d+)""")
        val DROPS_REMAINING = Regex("""(\d+) card drops? remaining""")
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    }
}
