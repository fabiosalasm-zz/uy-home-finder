package pe.fabiosalasm.uyhomefinder.skraper

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.javamoney.moneta.Money
import org.springframework.web.util.UriComponentsBuilder
import pe.fabiosalasm.uyhomefinder.domain.House
import pe.fabiosalasm.uyhomefinder.domain.StoreMode
import pe.fabiosalasm.uyhomefinder.extensions.orElse
import pe.fabiosalasm.uyhomefinder.extensions.replace
import pe.fabiosalasm.uyhomefinder.extensions.withEncodedPath
import pe.fabiosalasm.uyhomefinder.skraper.MercadoLibreDocumentHelper.catalogFeatures
import pe.fabiosalasm.uyhomefinder.skraper.MercadoLibreDocumentHelper.extractHouseAddress
import pe.fabiosalasm.uyhomefinder.skraper.MercadoLibreDocumentHelper.extractHouseDescription
import pe.fabiosalasm.uyhomefinder.skraper.MercadoLibreDocumentHelper.extractHouseId
import pe.fabiosalasm.uyhomefinder.skraper.MercadoLibreDocumentHelper.extractHouseLocation
import pe.fabiosalasm.uyhomefinder.skraper.MercadoLibreDocumentHelper.extractHouseNeighbourhood
import pe.fabiosalasm.uyhomefinder.skraper.MercadoLibreDocumentHelper.extractHousePicLinks
import pe.fabiosalasm.uyhomefinder.skraper.MercadoLibreDocumentHelper.extractHousePrice
import pe.fabiosalasm.uyhomefinder.skraper.MercadoLibreDocumentHelper.extractHouseTitle
import pe.fabiosalasm.uyhomefinder.skraper.MercadoLibreDocumentHelper.extractPostLink
import pe.fabiosalasm.uyhomefinder.skraper.MercadoLibreDocumentHelper.extractTotalSearchResults
import java.net.URL
import javax.money.Monetary
import kotlin.math.ceil

class MercadoLibreSkraper(
    override val urlTemplate: URL,
    override val urlParams: Map<String, Any>?,
    override val client: SkraperClient
) : Skraper {

    private companion object {
        val logger = KotlinLogging.logger { }
    }

    @Suppress("UNCHECKED_CAST")
    override fun fetchHousesForRental(): Set<House> = runBlocking {
        //TODO: Validate that URL template includes all url params
        val departments = when (urlParams!!["department"]) {
            is Map<*, *> -> {
                (urlParams["department"] as Map<String, String>).values
            }
            is String -> listOf(urlParams["department"] as String)
            else -> throw IllegalArgumentException(
                "Configuration error: Expecting key 'department' " +
                    "in 'urlParams' attribute to be either a String or HashMap<String, String>"
            )
        }

        departments
            .mapNotNull { department ->
                val urlParamsCopy = HashMap(urlParams)
                urlParamsCopy["department"] = department

                val url = UriComponentsBuilder.fromUriString(urlTemplate.toString()).build()
                    .expand(urlParamsCopy)
                    .toUri().toURL()

                logger.info { "Searching houses for rental: (host: ${url.host}, department: $department, url: ${url.withEncodedPath()})" }

                val pages = calculateTotalPages(url)
                if (pages == null) {
                    logger.error { "Cannot complete scraping of '${url.host}': Cannot calculate total pages" }
                    return@mapNotNull pages
                }

                logger.info { "Pages to cover: $pages" }

                val posts = getPosts(url, pages)
                logger.info { "Posts to analyze: ${posts.size}" }

                posts
                    .map { postUrl ->
                        async {
                            val doc = client.fetchDocument(url = postUrl)
                                .also {
                                    if (it == null)
                                        logger.warn {
                                            "Error while fetching page in url: $postUrl. No HTML content available"
                                        }
                                } ?: return@async null

                            House(
                                id = extractHouseId(postUrl).orEmpty(),
                                source = name,
                                title = extractHouseTitle(doc).orEmpty(),
                                link = postUrl,
                                address = extractHouseAddress(doc).orEmpty(),
                                price = extractHousePrice(doc).orElse(Money.zero(Monetary.getCurrency("UYU"))),
                                department = department.capitalize(),
                                neighbourhood = extractHouseNeighbourhood(doc).orEmpty(),
                                description = extractHouseDescription(doc).orEmpty(),
                                features = catalogFeatures(doc),
                                warranties = emptyList(), //warranties are ocasionally included in description and requires NLP to get fetched
                                pictureLinks = extractHousePicLinks(doc),
                                location = extractHouseLocation(doc),
                                storeMode = StoreMode.AUTOMATIC
                            )
                        }
                    }
                    .awaitAll()
                    .filterNotNull()
                    .toSet()
            }
            .flatten()
            .toSet()
    }

    private fun getPosts(url: URL, pages: Int): Set<String> = runBlocking {
        return@runBlocking (1..pages)
            .mapNotNull { page ->
                // Pagination is done by overriding base URL with pagination info, but instead of having the common
                // implementation of page=1,2,3...n, it's implemented as:
                // page 1 = no info
                // page n (n > 1) = Desde_${(post_per_page*(page-1)) +1}
                val urlWithPagination = if (page == 1) {
                    url
                } else {
                    url.replace("_PriceRange", "Desde_${(48 * (page - 1)) + 1}_PriceRange")
                }

                val document = client.fetchDocument(urlWithPagination.withEncodedPath().toString())
                    .also {
                        if (it == null)
                            logger.warn {
                                "Error while fetching page in url: $urlWithPagination. No HTML content available"
                            }
                    } ?: return@mapNotNull null

                extractPostLink(document)
            }
            .flatten()
            .toSet()
    }

    private fun calculateTotalPages(url: URL): Int? = runBlocking {
        val pageSize = 48

        val document = client.fetchDocument(url = url.withEncodedPath().toString())
            .also {
                if (it == null)
                    logger.warn {
                        "Error while fetching page in url: $url. No HTML content available"
                    }
            } ?: return@runBlocking null

        val count = extractTotalSearchResults(document) ?: return@runBlocking null

        return@runBlocking ceil(count.div(pageSize)).toInt()
    }
}