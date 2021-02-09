package pe.fabiosalasm.uyhomefinder

import it.skrape.core.fetcher.Request
import it.skrape.core.fetcher.UrlBuilder

fun createRequest(
    protocol: UrlBuilder.Protocol = UrlBuilder.Protocol.HTTPS,
    host: String, port: Int = 443, path: String? = null, queryParam: Map<String, String> = emptyMap()
): Request {
    val urlBuilder = UrlBuilder().apply {
        this.protocol = protocol
        this.host = host
        this.port = port
        this.path = path ?: ""
        this.queryParam = queryParam
    }

    return Request(url = urlBuilder.toString())
}