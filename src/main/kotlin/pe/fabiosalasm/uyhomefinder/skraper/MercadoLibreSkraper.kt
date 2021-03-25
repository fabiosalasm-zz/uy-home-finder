package pe.fabiosalasm.uyhomefinder.skraper

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toSet
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.reactor.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import mu.withLoggingContext
import org.javamoney.moneta.Money
import org.springframework.web.util.UriComponentsBuilder
import pe.fabiosalasm.uyhomefinder.domain.House
import pe.fabiosalasm.uyhomefinder.domain.StoreMode
import pe.fabiosalasm.uyhomefinder.extensions.orElse
import pe.fabiosalasm.uyhomefinder.extensions.replace
import pe.fabiosalasm.uyhomefinder.extensions.withEncodedPath
import pe.fabiosalasm.uyhomefinder.skraper.MercadoLibreDocumentHelper.catalogFeatures
import pe.fabiosalasm.uyhomefinder.skraper.MercadoLibreDocumentHelper.extractHouseAddress
import pe.fabiosalasm.uyhomefinder.skraper.MercadoLibreDocumentHelper.extractHouseDepartment
import pe.fabiosalasm.uyhomefinder.skraper.MercadoLibreDocumentHelper.extractHouseDescription
import pe.fabiosalasm.uyhomefinder.skraper.MercadoLibreDocumentHelper.extractHouseId
import pe.fabiosalasm.uyhomefinder.skraper.MercadoLibreDocumentHelper.extractHouseLocation
import pe.fabiosalasm.uyhomefinder.skraper.MercadoLibreDocumentHelper.extractHouseNeighbourhood
import pe.fabiosalasm.uyhomefinder.skraper.MercadoLibreDocumentHelper.extractHousePicLinks
import pe.fabiosalasm.uyhomefinder.skraper.MercadoLibreDocumentHelper.extractHousePrice
import pe.fabiosalasm.uyhomefinder.skraper.MercadoLibreDocumentHelper.extractHouseTitle
import pe.fabiosalasm.uyhomefinder.skraper.MercadoLibreDocumentHelper.extractPostUrls
import pe.fabiosalasm.uyhomefinder.skraper.MercadoLibreDocumentHelper.extractTotalSearchResults
import java.net.URL
import javax.money.Monetary
import kotlin.math.ceil

class MercadoLibreSkraper(
    override val urlTemplate: URL,
    override val urlParams: Map<String, String>?,
    override val client: SkraperClient
) : Skraper {

    private companion object {
        val logger = KotlinLogging.logger { }
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun fetchHousesForRental(): Set<House> {
        return withContext(client.scheduler.asCoroutineDispatcher()) {
            val mainUrl = UriComponentsBuilder.fromUriString(urlTemplate.toString()).build()
                .expand(urlParams!!)
                .toUri().toURL()

            logger.info { "Searching houses for rental: (host: ${mainUrl.host}, url: ${mainUrl.withEncodedPath()})" }

            val pages = calculateTotalPages(mainUrl)
            if (pages == null) {
                logger.error { "Cannot finish scraping of ${mainUrl.host}: Cannot calculate total pages" }
                return@withContext emptySet<House>()
            }

            logger.info { "Pages to cover: $pages" }

            (1..pages).asFlow()
                .onEach { logger.info { "Analyzing page #$it" } }
                .transform { page ->
                    getPostUrlsPerPage(mainUrl, page).forEach {
                        emit(it)
                    }
                }
                .onEach { logger.info { "Analyzing post $it" } }
                .buffer()
                .mapNotNull { postUrl ->
                    val doc = client.fetchDocument(url = postUrl)
                        .also {
                            if (it == null)
                                logger.warn { "Error while fetching page in url: $postUrl. No HTML content available" }
                        } ?: return@mapNotNull null

                    val houseId = extractHouseId(postUrl) ?: return@mapNotNull null

                    withLoggingContext("house.id" to houseId) {
                        House(
                            id = houseId,
                            source = name,
                            title = extractHouseTitle(doc).orEmpty(),
                            link = postUrl,
                            address = extractHouseAddress(doc).orEmpty(),
                            price = extractHousePrice(doc).orElse(Money.zero(Monetary.getCurrency("UYU"))),
                            department = extractHouseDepartment(doc)?.capitalize().orEmpty(),
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
                .filter { house ->
                    house.isValid()
                        && house.isLocatedInSafeNeighbourhood()
                        && house.isNearByCapital()
                        && house.isAvailableForRental()
                        && house.allowsPets()
                        && house.isForFamily()
                        && house.hasAvailablePics()
                }.toSet()
        }
    }

    /**
     * Gets all the post urls in a particular page. Depending on the page number we need to change the URL path
     * to represent the pagination in action.
     *
     * Each post can be pre-filtered (done in [MercadoLibreDocumentHelper.extractPostUrls]) prior to see their details
     * in case of mercadolibre.com, mainly because the post comes along with the department/neighbourhood information.
     */
    private suspend fun getPostUrlsPerPage(mainUrl: URL, currentPage: Int): Set<String> = coroutineScope {
        val urlWithPagination = when (currentPage) {
            1 -> mainUrl
            else -> mainUrl.replace("_PriceRange", "_Desde_${(48 * (currentPage - 1)) + 1}_PriceRange")
        }

        val document = client.fetchDocument(urlWithPagination.withEncodedPath().toString())

        if (document == null) {
            logger.warn { "Error while fetching page in url: $urlWithPagination. No HTML content available" }
            emptySet()
        } else {
            extractPostUrls(document)
        }
    }

    private suspend fun calculateTotalPages(url: URL): Int? {
        val pageSize = 48

        val document = client.fetchDocument(url = url.withEncodedPath().toString())
        if (document == null)
            logger.warn { "Error while fetching page in url: $url. No HTML content available" }

        return document
            ?.let { extractTotalSearchResults(it) }
            ?.let { ceil(it.div(pageSize)).toInt() }
    }
}