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
import org.jsoup.nodes.Document
import org.springframework.web.util.UriComponentsBuilder
import pe.fabiosalasm.uyhomefinder.domain.House
import pe.fabiosalasm.uyhomefinder.domain.StoreMode
import pe.fabiosalasm.uyhomefinder.extensions.cloneAndReplace
import pe.fabiosalasm.uyhomefinder.extensions.orElse
import pe.fabiosalasm.uyhomefinder.extensions.toMoney
import pe.fabiosalasm.uyhomefinder.extensions.withEncodedPath
import pe.fabiosalasm.uyhomefinder.skraper.GallitoDocumentHelper.catalogFeatures
import pe.fabiosalasm.uyhomefinder.skraper.GallitoDocumentHelper.extractHouseAddress
import pe.fabiosalasm.uyhomefinder.skraper.GallitoDocumentHelper.extractHouseDepartment
import pe.fabiosalasm.uyhomefinder.skraper.GallitoDocumentHelper.extractHouseDescription
import pe.fabiosalasm.uyhomefinder.skraper.GallitoDocumentHelper.extractHouseId
import pe.fabiosalasm.uyhomefinder.skraper.GallitoDocumentHelper.extractHouseLocation
import pe.fabiosalasm.uyhomefinder.skraper.GallitoDocumentHelper.extractHouseNeighbourhood
import pe.fabiosalasm.uyhomefinder.skraper.GallitoDocumentHelper.extractHousePhone
import pe.fabiosalasm.uyhomefinder.skraper.GallitoDocumentHelper.extractHousePicLinks
import pe.fabiosalasm.uyhomefinder.skraper.GallitoDocumentHelper.extractHousePrice
import pe.fabiosalasm.uyhomefinder.skraper.GallitoDocumentHelper.extractHouseTitle
import pe.fabiosalasm.uyhomefinder.skraper.GallitoDocumentHelper.extractHouseVideoLink
import pe.fabiosalasm.uyhomefinder.skraper.GallitoDocumentHelper.extractHouseWarranties
import java.net.URL
import javax.money.Monetary
import kotlin.math.ceil

private const val MAIN_PAGE_TOTAL_POST_SELECTOR = "li#resultados strong"


class GallitoSkraper(
    override val urlTemplate: URL,
    override val urlParams: Map<String, String>?,
    override val client: SkraperClient
) : Skraper {

    private companion object {
        val logger = KotlinLogging.logger {}
        val validateIfRedirectPage: (Document) -> Boolean = { document ->
            document
                .head()
                .selectFirst("title")
                ?.ownText()?.equals("Object moved") ?: false
        }
        val getRedirectUrl: (Document) -> String? = { document ->
            document.selectFirst("h2 a")?.attr("href")
        }
    }

    override suspend fun fetchHousesForRental(): Set<House> {
        return withContext(client.scheduler.asCoroutineDispatcher()) {
            val mainUrl = UriComponentsBuilder.fromUriString(urlTemplate.toString()).build()
                .expand(urlParams!!)
                .toUri().toURL()

            logger.info { "Searching houses for rental: (host: ${mainUrl.host}, url: ${mainUrl.withEncodedPath()})" }

            val pages = calculateTotalPages(mainUrl, urlParams)
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
                    val doc = client.fetchDocument(
                        url = postUrl,
                        shouldReditect = validateIfRedirectPage,
                        getRedirectUrl = getRedirectUrl
                    ).also {
                        if (it == null)
                            logger.warn { "Error while fetching page in url: $postUrl. No HTML content available" }
                    } ?: return@mapNotNull null

                    val houseId = extractHouseId(doc) ?: return@mapNotNull null

                    withLoggingContext("house.id" to houseId) {
                        House(
                            id = houseId,
                            source = name,
                            title = extractHouseTitle(doc).orEmpty(),
                            link = postUrl,
                            address = extractHouseAddress(doc).orEmpty(),
                            telephone = extractHousePhone(doc),
                            price = extractHousePrice(doc).orElse(Money.zero(Monetary.getCurrency("UYU"))),
                            department = extractHouseDepartment(doc).orEmpty(),
                            neighbourhood = extractHouseNeighbourhood(doc).orEmpty(),
                            description = extractHouseDescription(doc),
                            features = catalogFeatures(doc),
                            warranties = extractHouseWarranties(doc),
                            pictureLinks = extractHousePicLinks(doc),
                            location = extractHouseLocation(doc),
                            videoLink = extractHouseVideoLink(doc),
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

    //TODO: CSS QUERYING IN A HELPER CLASS
    private suspend fun getPostUrlsPerPage(mainUrl: URL, currentPage: Int): Set<String> = coroutineScope {
        val urlWithPagination = mainUrl.cloneAndReplace(query = "pag=${currentPage}").withEncodedPath()

        val document = client.fetchDocument(urlWithPagination.toString())
            .also {
                if (it == null)
                    logger.warn { "Error while fetching document with url: $urlWithPagination. No HTML content available" }
            } ?: return@coroutineScope emptySet()

        document.select("div.img-responsive.aviso-ico-contiene img.img-seva.img-responsive")
            .map { element -> element.attr("alt") }
            .toSet()
    }

    /**
     * Calculate the total amount of pages
     *
     * Assumptions:
     * gallito.uy only allows to show 40|80 posts per page, so we will work the max amount of posts per page.
     * Additionally, it also shows the total of posts based on the url.
     *
     * Objective:
     * Fetch the total of posts and divide it with the posts per page (const)
     */
    private suspend fun calculateTotalPages(url: URL, urlParams: Map<String, String>?): Int? = coroutineScope {
        val pageSize = urlParams?.getOrDefault("pageSize", "80")?.toInt() ?: 80

        val textSearchText = client.fetchDocument(url.withEncodedPath().toString())
            ?.selectFirst(MAIN_PAGE_TOTAL_POST_SELECTOR)
            ?.ownText()
            .also {
                if (it == null)
                    logger.warn { "Error while extracting total pages available: cannot find info using css query: $MAIN_PAGE_TOTAL_POST_SELECTOR" }
            } ?: return@coroutineScope null

        val count = """de (\d{1,10})""".toRegex().find(textSearchText)
            ?.groupValues?.get(1)?.toDouble()
            .also {
                if (it == null)
                    logger.warn { "Error while extracting total pages available: cannot parse text '$textSearchText'" }
            } ?: return@coroutineScope null

        ceil(count.div(pageSize)).toInt()
    }
}