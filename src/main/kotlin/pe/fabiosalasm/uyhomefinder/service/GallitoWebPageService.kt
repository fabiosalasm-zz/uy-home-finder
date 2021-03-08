package pe.fabiosalasm.uyhomefinder.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import mu.KotlinLogging
import org.javamoney.moneta.Money
import org.jsoup.nodes.Document
import org.springframework.stereotype.Service
import org.springframework.web.util.UriComponentsBuilder
import pe.fabiosalasm.uyhomefinder.domain.House
import pe.fabiosalasm.uyhomefinder.domain.Post
import pe.fabiosalasm.uyhomefinder.domain.StoreMode
import pe.fabiosalasm.uyhomefinder.extensions.cloneAndReplace
import pe.fabiosalasm.uyhomefinder.extensions.toMoney
import pe.fabiosalasm.uyhomefinder.repository.ConfigRepository
import pe.fabiosalasm.uyhomefinder.repository.HouseCandidateRepository
import pe.fabiosalasm.uyhomefinder.skraper.SkraperClient
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

@Service
class GallitoWebPageService(
    private val skraperClient: SkraperClient,
    private val objectMapper: ObjectMapper,
    private val configRepository: ConfigRepository,
    private val houseCandidateRepository: HouseCandidateRepository
) {

    private val alias = "gallito"

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

    fun getHousesForRent() {
        val configRecord = configRepository.getOneByAlias(alias)
            ?: throw IllegalStateException("configuration doesn't exists for alias: $alias")

        val urlTemplate = configRecord.urlTemplate!!
        val urlParams = configRecord.urlTemplateParams?.let {
            objectMapper.readValue<Map<String, Any>>(it.data())
        } ?: throw IllegalStateException("url template params should exists for alias: $alias")

        val url = UriComponentsBuilder.fromUriString(urlTemplate).build()
            .expand(urlParams)
            .toUri().toURL()

        val pages = calculateTotalPages(url, urlParams)
        logger.info { "The search returned $pages pages" }

        val posts = getPosts(url, pages)
        logger.info { "Found ${posts.size} posts in all pages" }

        val houses = posts
            .asSequence()
            .mapNotNull { post ->
                //TODO: NPE
                val doc = skraperClient.fetchDocument(
                    url = post.link,
                    shouldReditect = validateIfRedirectPage,
                    getRedirectUrl = getRedirectUrl
                )!!

                House(
                    sourceId = extractHouseId(doc),
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
                    geoReference = extractHouseGeoRef(post, doc),
                    videoLink = extractHouseVideoLink(post, doc),
                    storeMode = StoreMode.AUTOMATIC
                )
            }
            .filter { house ->
                house.isValid()
                    && house.isLocatedInSafeNeighbourhood()
                    && house.isNearByCapital()
                    && house.isAvailableForRental()
                    && house.isForFamily()
                    && house.hasAvailablePics()
            }
            .toSet()
        logger.info { "Got ${houses.size} house candidates after filtering posts" }

        houseCandidateRepository.cleanAndSave(alias, houses)
    }

    //TODO: Extract when logic
    private fun extractHouseVideoLink(
        post: Post,
        doc: Document
    ) = when (post.hasVideo) {
        true -> doc.selectFirst(RENTAL_PAGE_VIDEO_SELECTOR)
            ?.attr("src")
        false -> null
    }

    //TODO: Extract when logic
    private fun extractHouseGeoRef(
        post: Post,
        doc: Document
    ) = when (post.hasGPS) {
        true -> doc.selectFirst(RENTAL_PAGE_GPS_SELECTOR)
            ?.attr("src")
            ?.let {
                UriComponentsBuilder.fromUriString(it)
                    .build().queryParams.getFirst("q")
            }
        false -> null
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
        val text = skraperClient.fetchDocument(url = url.toString())
            ?.selectFirst(MAIN_PAGE_TOTAL_POST_SELECTOR)
            ?.ownText() ?: "de 0"

        //TODO: make this code dont assume array size and cast
        val count = text.split(" ")[1].toDouble()

        return ceil(count.div(pageSize)).toInt() //TODO: evaluate when count < size
    }

    private fun getPosts(url: URL, pages: Int): Set<Post> {
        return (1..pages)
            .mapNotNull { page ->
                skraperClient.fetchDocument(url.cloneAndReplace(query = "pag=${page}").toString())
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
        .mapNotNull { it.ownText()?.removeSurrounding(" ") }.toList()

    private fun extractHouseDescription(doc: Document) = doc.select(RENTAL_PAGE_DESC_SELECTOR)
        .joinToString(" ") { ele ->
            ele.ownText().removeSurrounding(" ")
        }.removeSurrounding(" ")

    private fun extractHouseNeighbourhood(doc: Document) =
        doc.selectFirst(RENTAL_PAGE_NGH_SELECTOR)?.ownText()?.removeSurrounding(" ").orEmpty()

    private fun extractHouseDepartment(doc: Document) =
        doc.selectFirst(RENTAL_PAGE_DPT_SELECTOR)?.ownText()?.removeSurrounding(" ").orEmpty()

    private fun extractHousePrice(doc: Document): Money {
        return doc.selectFirst(RENTAL_PAGE_PRICE_SELECTOR)?.ownText()?.removeSurrounding(" ")
            ?.let {
                when {
                    it.startsWith("\$U ") -> it.replace("\$U", "UYU")
                    it.startsWith("U\$S") -> it.replace("U\$S", "USD")
                    else -> throw IllegalArgumentException("Price expressed as: $it is invalid or unknown")
                }
            }?.toMoney() ?: Money.zero(Monetary.getCurrency("UYU"))
    }

    private fun extractHousePhone(doc: Document) =
        doc.selectFirst(RENTAL_PAGE_TLF_SELECTOR)?.attr("value")?.removeSurrounding(" ").orEmpty()

    private fun extractHouseAddress(doc: Document) =
        doc.selectFirst(RENTAL_PAGE_ADDRESS_SELECTOR)?.ownText()?.removeSurrounding(" ").orEmpty()

    private fun extractHouseTitle(doc: Document) =
        doc.selectFirst(RENTAL_PAGE_TITLE_SELECTOR)?.ownText()?.removeSurrounding(" ").orEmpty()

    private fun extractHouseId(doc: Document) =
        doc.selectFirst(RENTAL_PAGE_ID_SELECTOR)?.attr("value")
            ?.removeSurrounding(" ")?.let {
                "$alias-$it"
            }.orEmpty()
}