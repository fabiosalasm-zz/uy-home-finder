package pe.fabiosalasm.uyhomefinder.skraper
/**
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.javamoney.moneta.Money
import org.jsoup.nodes.Document
import org.springframework.web.util.UriComponentsBuilder
import pe.fabiosalasm.uyhomefinder.domain.House
import pe.fabiosalasm.uyhomefinder.domain.Post
import pe.fabiosalasm.uyhomefinder.domain.StoreMode
import pe.fabiosalasm.uyhomefinder.extensions.cloneAndReplace
import pe.fabiosalasm.uyhomefinder.extensions.orElse
import pe.fabiosalasm.uyhomefinder.extensions.toMoney
import pe.fabiosalasm.uyhomefinder.extensions.withEncodedPath
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
private const val MAIN_PAGE_POST_SELECTOR = "div.img-responsive.aviso-ico-contiene"
private const val MAIN_PAGE_POST_LINK_SELECTOR = "img.img-seva.img-responsive"
private const val MAIN_PAGE_GPS_SELECTOR = "span img[src='/img/gpsicon.png']"
private const val MAIN_PAGE_VIDEO_SELECTOR = "span img[src='/img/camicon.png']"

private const val RENTAL_PAGE_FEAT_SELECTOR =
    "section#caracteristicas div.p-3 ul#ul_caracteristicas li.list-group-item.border-0"
**/

/**
class GallitoSkraper(
    override val urlTemplate: URL,
    override val urlParams: Map<String, Any>?,
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

    override fun fetchHousesForRental(): Set<House> {
        //TODO: Validate that URL template includes all url params
        val url = UriComponentsBuilder.fromUriString(urlTemplate.toString()).build()
            .expand(urlParams!!)
            .toUri().toURL()

        logger.info { "Searching houses for rental: (host: ${url.host}, url: $url)" }

        val pages = calculateTotalPages(url, urlParams)
        if (pages == null) {
            logger.error { "Cannot complete scraping of '${url.host}': Cannot calculate total pages" }
            return emptySet()
        }

        logger.info { "Pages to cover: $pages" }

        val posts = getPosts(url, pages)
        logger.info { "Posts to analyze: ${posts.size}" }

        return posts
            .asSequence()
            .mapNotNull { post ->
                //TODO: NPE
                val doc = client.fetchDocument(
                    url = post.link,
                    shouldReditect = validateIfRedirectPage,
                    getRedirectUrl = getRedirectUrl
                )!!

                House(
                    id = extractHouseId(doc).orEmpty(),
                    source = name,
                    title = extractHouseTitle(doc).orEmpty(),
                    link = post.link,
                    address = extractHouseAddress(doc).orEmpty(),
                    telephone = extractHousePhone(doc),
                    price = extractHousePrice(doc).orElse(Money.zero(Monetary.getCurrency("UYU"))),
                    department = extractHouseDepartment(doc).orEmpty(),
                    neighbourhood = extractHouseNeighbourhood(doc).orEmpty(),
                    description = extractHouseDescription(doc),
                    features = catalogFeatures(doc),
                    warranties = extractHouseWarranties(doc),
                    pictureLinks = extractHousePicLinks(doc),
                    location = when (post.hasGPS) {
                        true -> extractHouseLocation(doc)
                        false -> null
                    },
                    videoLink = when (post.hasVideo) {
                        true -> extractHouseVideoLink(doc)
                        false -> null
                    },
                    storeMode = StoreMode.AUTOMATIC
                )
            }
            .filter { house ->
                house.isValid()
                    && house.isLocatedInSafeNeighbourhood()
                    && house.isNearByCapital()
                    && house.isAvailableForRental()
                    && house.allowsPets()
                    && house.isForFamily()
                    && house.hasAvailablePics()
            }
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
    private fun calculateTotalPages(url: URL, urlParams: Map<String, Any>?): Int? = runBlocking {
        val pageSize = urlParams?.getOrDefault("pageSize", 80) as Int

        val textSearchText = client.fetchDocument(url.withEncodedPath().toString())
            ?.selectFirst(MAIN_PAGE_TOTAL_POST_SELECTOR)
            ?.ownText()
            .also {
                if (it == null)
                    logger.warn { "Error while extracting total pages available: cannot find info using css query: $MAIN_PAGE_TOTAL_POST_SELECTOR" }
            } ?: return@runBlocking null

        val count = """de (\d{1,10})""".toRegex().find(textSearchText)
            ?.groupValues?.get(1)?.toDouble()
            .also {
                if (it == null)
                    logger.warn { "Error while extracting total pages available: cannot parse text '$textSearchText'" }
            } ?: return@runBlocking null

        ceil(count.div(pageSize)).toInt()
    }

    private fun getPosts(url: URL, pages: Int): Set<Post> {
        return (1..pages)
            .mapNotNull PostSelector@{ page ->
                val document =
                    client.fetchDocument(url.cloneAndReplace(query = "pag=${page}").withEncodedPath().toString())
                        .also {
                            if (it == null)
                                logger.warn {
                                    "Error while fetching page in url: $url. No HTML content available"
                                }
                        } ?: return@PostSelector null


                document.select(MAIN_PAGE_POST_SELECTOR)
                    .mapNotNull LinkSelector@{ element ->
                        val link = element.selectFirst(MAIN_PAGE_POST_LINK_SELECTOR)
                            ?.attr("alt")
                            .also {
                                if (it == null)
                                    logger.warn {
                                        "Error while extracting post link. Cannot find css query: $MAIN_PAGE_POST_LINK_SELECTOR"
                                    }
                            } ?: return@LinkSelector null

                        val hasGPS = element.selectFirst(MAIN_PAGE_GPS_SELECTOR) != null
                        val hasVideo = element.selectFirst(MAIN_PAGE_VIDEO_SELECTOR) != null
                        Post(link, hasGPS, hasVideo)
                    }
            }
            .flatten()
            .toSet()
    }

    //TODO: implement function similar to Mercadolibre
    private fun catalogFeatures(document: Document): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        val extras = mutableListOf<String>()

        document.select(RENTAL_PAGE_FEAT_SELECTOR)
            .mapNotNull { it.ownText() }
            .forEach { rawFeature ->
                when {
                    """Padrón: \w+""".toRegex().containsMatchIn(rawFeature) -> {
                        result["register"] = rawFeature.removePrefix("Padrón: ")
                    }
                    """Estado: \w+""".toRegex().containsMatchIn(rawFeature) -> {
                        result["buildingState"] = rawFeature.removePrefix("Estado: ")
                    }
                    """(\d) (Baño[s]?)""".toRegex().containsMatchIn(rawFeature) -> {
                        result["numberBathrooms"] = """(\d) (Baño[s]?)""".toRegex().find(rawFeature)!!
                            .groupValues[1].toInt()
                    }
                    rawFeature == "Cocina" -> {
                        result["hasKitchen"] = true
                        result["kitchenSize"] = "NORMAL"
                    }

                    rawFeature == "Kitchenette" -> {
                        result["hasKitchen"] = true
                        result["kitchenSize"] = "SMALL"
                    }

                    """Techo: \w+""".toRegex().containsMatchIn(rawFeature) -> {
                        result["roofType"] = rawFeature.removePrefix("Techo: ")
                    }
                    """(Sup. construida:) (\d{1,5})m²""".toRegex().containsMatchIn(rawFeature) -> {
                        result["totalSqMeters"] = """(Sup. construida:) (\d{1,5})m²""".toRegex().find(rawFeature)!!
                            .groupValues[2].toInt()

                        //TODO: the regex filter is not working as expected?
                    }
                    """Gastos Comunes: \$(U\d+)""".toRegex().containsMatchIn(rawFeature) -> {
                        result["commonExpenses"] = """Gastos Comunes: \$(U\d+)""".toRegex().find(rawFeature)!!
                            .groupValues[1].replace("U", "UYU ") // polishing string to change to it money
                            .toMoney()
                    }
                    """(Año:) (\d+)""".toRegex().containsMatchIn(rawFeature) -> {
                        result["constructionYear"] = """(Año:) (\d+)""".toRegex().find(rawFeature)!!
                            .groupValues[2].toInt()
                    }
                    """(Cantidad de plantas:) (\d+)""".toRegex().containsMatchIn(rawFeature) -> {
                        result["numberFloors"] = """(Cantidad de plantas:) (\d+)""".toRegex().find(rawFeature)!!
                            .groupValues[2].toInt()
                    }
                    else -> {
                        extras.add(rawFeature)
                    }
                }
            }

        if (extras.isNotEmpty()) {
            result["extras"] = extras
        }

        return result
    }
} **/