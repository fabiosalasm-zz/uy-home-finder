package pe.fabiosalasm.uyhomefinder

import it.skrape.core.Scraper
import it.skrape.core.fetcher.HttpFetcher
import it.skrape.core.fetcher.Request
import it.skrape.core.fetcher.UrlBuilder
import it.skrape.skrape

data class ScrapingTarget(
    val host: String = "localhost",
    val port: Int = 80,
    val path: String = "",
    val queryParam: Map<String, String> = emptyMap()
) {

    fun toScraper(): Scraper<Request> {
        return skrape(HttpFetcher) {
            request {
                url = urlBuilder {
                    protocol =
                        if (this@ScrapingTarget.port == 80) UrlBuilder.Protocol.HTTP else UrlBuilder.Protocol.HTTPS
                    host = this@ScrapingTarget.host
                    port = this@ScrapingTarget.port
                    path = this@ScrapingTarget.path
                    queryParam = this@ScrapingTarget.queryParam
                }
            }

            preConfigured
        }
    }
}