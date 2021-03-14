package pe.fabiosalasm.uyhomefinder.skraper

import mu.KotlinLogging
import org.javamoney.moneta.Money
import org.jsoup.nodes.Document
import org.springframework.web.util.UriComponentsBuilder
import pe.fabiosalasm.uyhomefinder.domain.House
import pe.fabiosalasm.uyhomefinder.domain.Point
import pe.fabiosalasm.uyhomefinder.domain.Post
import pe.fabiosalasm.uyhomefinder.domain.StoreMode
import pe.fabiosalasm.uyhomefinder.extensions.cloneAndReplace
import pe.fabiosalasm.uyhomefinder.extensions.toMoney
import pe.fabiosalasm.uyhomefinder.extensions.withEncodedPath
import java.net.URL
import javax.money.Monetary
import kotlin.math.ceil

private const val MAIN_PAGE_TOTAL_POST_SELECTOR = "li#resultados strong"
private const val MAIN_PAGE_POST_SELECTOR = "div.img-responsive.aviso-ico-contiene"
private const val MAIN_PAGE_POST_LINK_SELECTOR = "img.img-seva.img-responsive"
private const val MAIN_PAGE_GPS_SELECTOR = "span img[src='/img/gpsicon.png']"
private const val MAIN_PAGE_VIDEO_SELECTOR = "span img[src='/img/camicon.png']"
private const val RENTAL_PAGE_ID_SELECTOR = "input#HfCodigoAviso"
private const val RENTAL_PAGE_TITLE_SELECTOR = "div#div_datosBasicos h1.titulo"
private const val RENTAL_PAGE_ADDRESS_SELECTOR = "div#div_datosBasicos h2.direccion"
private const val RENTAL_PAGE_TLF_SELECTOR = "input#HfTelefono"
private const val RENTAL_PAGE_PRICE_SELECTOR = "div#div_datosBasicos div.wrapperFavorito span.precio"
private const val RENTAL_PAGE_DPT_SELECTOR = "nav.breadcrumb-w100 li.breadcrumb-item:nth-child(5) a"
private const val RENTAL_PAGE_NGH_SELECTOR = "nav.breadcrumb-w100 li.breadcrumb-item:nth-child(6) a"
private const val RENTAL_PAGE_DESC_SELECTOR = "section#descripcion div.p-3 p"
private const val RENTAL_PAGE_FEAT_SELECTOR =
    "section#caracteristicas div.p-3 ul#ul_caracteristicas li.list-group-item.border-0"
private const val RENTAL_PAGE_WARR_SELECTOR = "section#garantias div.p-3 ul#ul_garantias li.list-group-item.border-0"
private const val RENTAL_PAGE_GALLERY_SELECTOR = "div#galeria div.carousel-item.item a"
private const val RENTAL_PAGE_GPS_SELECTOR = "div#ubicacion iframe#iframeMapa"
private const val RENTAL_PAGE_VIDEO_SELECTOR = "div#video iframe#iframe_video"

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
        val url = UriComponentsBuilder.fromUriString(urlTemplate.toString()).build()
            .expand(urlParams!!)
            .toUri().toURL()

        logger.info { "Searching houses for rental: (host: ${url.host}, url: $url)" }

        val pages = calculateTotalPages(url, urlParams)
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
                    id = extractHouseId(doc),
                    source = name,
                    title = extractHouseTitle(doc),
                    link = post.link,
                    address = extractHouseAddress(doc),
                    telephone = extractHousePhone(doc),
                    price = extractHousePrice(doc),
                    department = extractHouseDepartment(doc),
                    neighbourhood = extractHouseNeighbourhood(doc),
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
    private fun calculateTotalPages(url: URL, urlParams: Map<String, Any>?): Int {
        val pageSize = urlParams?.getOrDefault("pageSize", 80) as Int
        val text = client.fetchDocument(url.withEncodedPath().toString())
            ?.selectFirst(MAIN_PAGE_TOTAL_POST_SELECTOR)
            ?.ownText() ?: "de 0"

        //TODO: make this code dont assume array size and cast
        val count = text.split(" ")[1].toDouble()

        return ceil(count.div(pageSize)).toInt() //TODO: evaluate when count < size
    }

    private fun getPosts(url: URL, pages: Int): Set<Post> {
        return (1..pages)
            .mapNotNull { page ->
                client.fetchDocument(url.withEncodedPath().cloneAndReplace(query = "pag=${page}").toString())
                    ?.select(MAIN_PAGE_POST_SELECTOR)
                    ?.mapNotNull { ele ->
                        val link = ele.selectFirst(MAIN_PAGE_POST_LINK_SELECTOR)
                            ?.attr("alt")

                        if (link == null) {
                            null
                        } else {
                            val hasGPS = ele.selectFirst(MAIN_PAGE_GPS_SELECTOR) != null
                            val hasVideo = ele.selectFirst(MAIN_PAGE_VIDEO_SELECTOR) != null
                            Post(link, hasGPS, hasVideo)
                        }
                    }
            }
            .flatten()
            .toSet()
    }

    private fun extractHouseVideoLink(doc: Document) =
        doc.selectFirst(RENTAL_PAGE_VIDEO_SELECTOR)
            ?.attr("src")

    private fun extractHouseLocation(doc: Document): Point? {
        val attribute = doc.selectFirst(RENTAL_PAGE_GPS_SELECTOR)?.attr("src")
        return if (attribute != null) {
            val pointAsText = UriComponentsBuilder.fromUriString(attribute)
                .build().queryParams.getFirst("q")
                .orEmpty()

            try {
                Point.fromText(pointAsText)
            } catch (e: Exception) {
                logger.warn { "Error while parsing HTML document: cannot parse $pointAsText to Point(latitude, longitude)" }
                null
            }
        } else {
            null
        }
    }

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

    private fun extractHousePicLinks(doc: Document) = doc.select(RENTAL_PAGE_GALLERY_SELECTOR)
        .mapNotNull { it.attr("href") }.toList()

    private fun extractHouseWarranties(doc: Document) = doc.select(RENTAL_PAGE_WARR_SELECTOR)
        .mapNotNull { it.ownText()?.trim() }.toList()

    private fun extractHouseDescription(doc: Document) = doc.select(RENTAL_PAGE_DESC_SELECTOR)
        .joinToString(" ") { ele ->
            ele.ownText().trim()
        }.trim()

    private fun extractHouseNeighbourhood(doc: Document) =
        doc.selectFirst(RENTAL_PAGE_NGH_SELECTOR)?.ownText()?.trim().orEmpty()

    private fun extractHouseDepartment(doc: Document) =
        doc.selectFirst(RENTAL_PAGE_DPT_SELECTOR)?.ownText()?.trim().orEmpty()

    private fun extractHousePrice(doc: Document): Money {
        return doc.selectFirst(RENTAL_PAGE_PRICE_SELECTOR)?.ownText()?.trim()
            ?.let {
                when {
                    it.startsWith("\$U ") -> it.replace("\$U", "UYU")
                    it.startsWith("U\$S") -> it.replace("U\$S", "USD")
                    else -> throw IllegalArgumentException("Error while parsing HTML document: cannot parse $it as currency")
                }
            }?.toMoney() ?: Money.zero(Monetary.getCurrency("UYU"))
    }

    private fun extractHousePhone(doc: Document): String? =
        doc.selectFirst(RENTAL_PAGE_TLF_SELECTOR)?.attr("value")?.trim()

    private fun extractHouseAddress(doc: Document) =
        doc.selectFirst(RENTAL_PAGE_ADDRESS_SELECTOR)?.ownText()?.trim().orEmpty()

    private fun extractHouseTitle(doc: Document) =
        doc.selectFirst(RENTAL_PAGE_TITLE_SELECTOR)?.ownText()?.trim().orEmpty()

    private fun extractHouseId(doc: Document) =
        doc.selectFirst(RENTAL_PAGE_ID_SELECTOR)?.attr("value")
            ?.trim().orEmpty()
}