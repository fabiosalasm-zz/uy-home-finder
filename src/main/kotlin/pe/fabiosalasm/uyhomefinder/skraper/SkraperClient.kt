package pe.fabiosalasm.uyhomefinder.skraper

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.springframework.core.io.ByteArrayResource
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import java.net.URI

const val DEFAULT_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/72.0.3626.121 Safari/537.36"

enum class HttpMethodType {
    GET,
    POST
}

//TODO: Handle properly for null cases
interface SkraperClient {
    fun request(
        url: String,
        method: HttpMethodType = HttpMethodType.GET,
        headers: Map<String, String> = mapOf("User-Agent" to DEFAULT_USER_AGENT)
    ): ByteArray?

    fun fetchDocument(
        url: String,
        method: HttpMethodType = HttpMethodType.GET,
        headers: Map<String, String> = mapOf("User-Agent" to DEFAULT_USER_AGENT),
        shouldReditect: (Document) -> Boolean = {_ -> false},
        getRedirectUrl: (Document) -> String? = {_ -> null}
    ): Document? {
        val document = request(url, method, headers)
            ?.let {
                Jsoup.parse(it.toString(charset = Charsets.UTF_8))
            }

        return if (document != null) {
            when (shouldReditect(document)) {
                false -> document
                true -> getRedirectUrl(document)?.let { fetchDocument(it, method, headers) }
            }
        } else null
    }
}
//TODO: only works for GET http requests
class SpringReactiveSkraperClient(private val webClient: WebClient) : SkraperClient {
    override fun request(url: String, method: HttpMethodType, headers: Map<String, String>): ByteArray? {
        return webClient.get()
            .uri(URI(url))
            .headers { headers.forEach { (k, v) -> it[k] = v } }
            .retrieve()
            .bodyToMono<ByteArrayResource>()
            .map { it.byteArray }
            .block()
    }
}