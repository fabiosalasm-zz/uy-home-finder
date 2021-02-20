package pe.fabiosalasm.uyhomefinder.extensions

import it.skrape.core.Scraper
import it.skrape.core.fetcher.HttpFetcher
import it.skrape.core.fetcher.Request
import it.skrape.skrape
import org.springframework.web.util.UriComponentsBuilder
import java.net.URL

fun URL.toScraperRunner(): Scraper<Request> {
    check(this.protocol == "http" || this.protocol == "https") { "Invalid URL protocol. Expected http(s)" }
    return skrape(HttpFetcher) {
        request {
            url = this@toScraperRunner.toString()
            followRedirects = true
        }

        preConfigured
    }
}

fun URL.cloneAndReplace(path: String = "", query: String = ""): URL {
    check(path.startsWith("/")) { "Path should start with /" }

    val originalUrl = UriComponentsBuilder.fromUriString(this.toString()).build()

    val builder = UriComponentsBuilder.newInstance()
    builder.scheme(originalUrl.scheme)
    builder.port(originalUrl.port)
    builder.host(originalUrl.host)

    if (path.isNotEmpty()) {
        builder.path(path)
    } else if (!originalUrl.path.isNullOrEmpty()) {
        builder.path(originalUrl.path!!)
    }

    if (query.isNotEmpty()) {
        builder.query(query)
    } else if (!originalUrl.query.isNullOrEmpty()) {
        builder.query(originalUrl.query!!)
    }

    return URL(builder.build().toUriString())
}

fun URL.appendPath(path: String): URL {
    check(path.startsWith("/")) { "Path should start with /" }

    val originalUrl = UriComponentsBuilder.fromUriString(this.toString()).build()

    val builder = UriComponentsBuilder.newInstance()
    builder.scheme(originalUrl.scheme)
    builder.port(originalUrl.port)
    builder.host(originalUrl.host)

    if (originalUrl.path.isNullOrEmpty()) {
        builder.path(path)
    } else {
        builder.path(originalUrl.path!!.trimEnd('/') + path)
    }

    if (!originalUrl.query.isNullOrEmpty()) {
        builder.query(originalUrl.query!!)
    }

    return URL(builder.build().toUriString())
}
