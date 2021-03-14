package pe.fabiosalasm.uyhomefinder.skraper

import mu.KotlinLogging
import org.javamoney.moneta.Money
import org.jsoup.nodes.Document
import org.springframework.web.util.UriComponentsBuilder
import pe.fabiosalasm.uyhomefinder.domain.House
import pe.fabiosalasm.uyhomefinder.domain.Post
import pe.fabiosalasm.uyhomefinder.domain.StoreMode
import pe.fabiosalasm.uyhomefinder.extensions.appendPath
import pe.fabiosalasm.uyhomefinder.extensions.toMoney
import pe.fabiosalasm.uyhomefinder.extensions.withEncodedPath
import java.net.URL
import javax.money.Monetary

private const val MAIN_PAGE_POST_SELECTOR = "a.holder-link.checkMob"
private const val RENTAL_PAGE_ID_SELECTOR = "i.icon-heart.animatable"
private const val RENTAL_PAGE_TITLE_SELECTOR = "h1.likeh2.titulo.one-line-txt"
private const val RENTAL_PAGE_TLF_SELECTOR = "span.lineInmo"
private const val RENTAL_PAGE_PRICE_SELECTOR = "p.precio-final"
private const val RENTAL_PAGE_NGH_SELECTOR = "a.part-breadcrumbs:nth-child(10)"
private const val RENTAL_PAGE_DESC_SELECTOR = "div#descripcion p"
private const val RENTAL_PAGE_WARR_SELECTOR = "div#garantias p"
private const val RENTAL_PAGE_GALLERY_SELECTOR = "div#slickAmpliadas img.imageBig"

class InfocasasSkraper(
    override val urlTemplate: URL,
    override val urlParams: Map<String, Any>?,
    override val client: SkraperClient
) : Skraper {

    private companion object {
        val logger = KotlinLogging.logger {}
    }

    @Suppress("UNCHECKED_CAST")
    override fun fetchHousesForRental(): Set<House> {
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

        return departments
            .map { department ->
                val urlParamsCopy = HashMap(urlParams)
                urlParamsCopy["department"] = department

                val url = UriComponentsBuilder.fromUriString(urlTemplate.toString()).build()
                    .expand(urlParamsCopy)
                    .toUri().toURL()

                logger.info { "Searching houses for rental: (host: ${url.host}, department: $department, url: $url)" }

                val pages = calculateTotalPages(url)
                logger.info { "Pages to cover: $pages" }

                val posts = getPosts(url, pages)
                logger.info { "Posts to analyze: ${posts.size}" }

                posts
                    .asSequence()
                    .mapNotNull { post ->
                        //TODO: NPE
                        val doc = client.fetchDocument(
                            url = post.link
                        )!!

                        House(
                            id = extractHouseId(doc),
                            source = name,
                            title = extractHouseTitle(doc),
                            link = post.link,
                            address = extractHouseNeighbourhood(doc), // by default infocasas does not specify address
                            telephone = extractHousePhone(doc),
                            price = extractHousePrice(doc),
                            department = department.capitalize(),
                            neighbourhood = extractHouseNeighbourhood(doc),
                            description = extractHouseDescription(doc),
                            features = catalogFeatures(doc),
                            warranties = extractHouseWarranties(doc),
                            pictureLinks = extractHousePicLinks(doc),
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
            .flatten()
            .toSet()
    }

    private fun catalogFeatures(doc: Document): Map<String, Any> {
        return doc.select("div.ficha-tecnica div.lista")
            .filter { it.selectFirst("div.dato.auto-q.nicer-title") == null }
            .mapNotNull {
                val key = it.selectFirst("p")?.ownText().orEmpty()
                val value = it.selectFirst("div.dato")?.ownText().orEmpty()
                when (key) {
                    "Baños:" -> {
                        if (value.contains("+")) {
                            val x = value.split("+")[0].toIntOrNull()
                            if (x == null) null else "numberBathrooms" to x
                        } else {
                            val x = value.toIntOrNull()
                            if (x == null) null else "numberBathrooms" to x
                        }
                    }
                    "M² del terreno:" -> "totalSqMeters" to value.replace(".", "").toInt()
                    "M² edificados:" -> "houseSqMeters" to value.replace(".", "").toInt()
                    "Estado:" -> "buildingState" to value
                    "Plantas:" -> "numberFloors" to value
                    "Dormitorios:" -> {
                        if (value.contains("+")) {
                            val x = value.split("+")[0].toIntOrNull()
                            if (x == null) null else "numberBedrooms" to x
                        } else {
                            val x = value.toIntOrNull()
                            if (x == null) null else "numberBedrooms" to x
                        }
                    }
                    "Garajes:" -> {
                        if (value.contains("+")) {
                            val x = value.split("+")[0].toIntOrNull()
                            if (x == null) null else "numberGarages" to x
                        } else {
                            val x = value.toIntOrNull()
                            if (x == null) null else "numberGarages" to x
                        }
                    }
                    "Gastos Comunes:" -> "commonExpenses" to value
                    else -> null
                }
            }
            .toMap()
    }

    private fun extractHousePicLinks(doc: Document) = doc.select(RENTAL_PAGE_GALLERY_SELECTOR)
        .mapNotNull { it.attr("src") }

    private fun extractHouseWarranties(doc: Document) = doc.select(RENTAL_PAGE_WARR_SELECTOR)
        .mapNotNull { it.ownText()?.trim() }

    private fun extractHouseDescription(doc: Document): String {
        val description = doc.select(RENTAL_PAGE_DESC_SELECTOR)
            .joinToString(" ") { it.ownText().trim() }
            .trim()

        val additionalDescription = doc.select("div#descripcion p span")
            .joinToString(" ") { it.attr("data-hidden-dato").trim() }
            .trim()

        return "$description $additionalDescription"
    }

    private fun extractHouseNeighbourhood(doc: Document) =
        doc.selectFirst(RENTAL_PAGE_NGH_SELECTOR)?.ownText().orEmpty()

    private fun extractHousePrice(doc: Document) = doc.selectFirst(RENTAL_PAGE_PRICE_SELECTOR)?.ownText()
        ?.let {
            when {
                it.startsWith("\$") -> it.replace("\$", "UYU")
                it.startsWith("U\$S") -> it.replace("U\$S", "USD")
                else -> throw IllegalArgumentException("Price expressed as: $it is invalid or unknown")
            }
        }?.toMoney() ?: Money.zero(Monetary.getCurrency("UYU"))

    private fun extractHousePhone(doc: Document): String? = doc.selectFirst(RENTAL_PAGE_TLF_SELECTOR)?.ownText()
        ?.let {
            when (it.contains(" ")) {
                true -> it.split(" ")[0]
                false -> it
            }
        }

    private fun extractHouseTitle(doc: Document) =
        doc.selectFirst(RENTAL_PAGE_TITLE_SELECTOR)?.ownText()?.trim().orEmpty()

    private fun extractHouseId(doc: Document) = doc.selectFirst(RENTAL_PAGE_ID_SELECTOR)?.attr("data-id")
        ?.trim().orEmpty()

    /**
     * Calculate the total amount of pages
     *
     * Assumptions:
     * infocasas.com.uy doesn't show the total of pages, so the calculation is based on looping
     * through the available pages until reach the last one.
     *
     * Objective:
     * Calculate the total of pages by looping through the available pages until reaching the last one.
     * The last one can be detected based on elements in the HTML web page.
     *
     * Steps:
     * 1. Enter to the first page, go to the page navigation buttons and check if 'Pagina Siguiente'(>)
     * exists.
     * 2. If exists, select the previous button, get it's number and use it as first page. Return to 1.
     * 3. If not, select the previous button, get it's number and use it as the number of pages
     */
    private fun calculateTotalPages(url: URL): Int {
        var lastPageFound = false
        var lastPageCandidate = 1

        while (!lastPageFound) {
            //TODO: NPE
            val doc = client.fetchDocument(
                url.withEncodedPath()
                    .appendPath("/pagina${lastPageCandidate}")
                    .toString()
            )!!

            val nextPageLink = doc.selectFirst("a[title='Página Siguiente'].next")
            if (nextPageLink == null) {
                lastPageCandidate = doc.select("a.numbers").last()!!.ownText().toInt() + 1
                lastPageFound = true
            } else {
                lastPageCandidate = doc.select("a.numbers").last()!!.ownText().toInt()
                lastPageFound = false
            }
        }

        return lastPageCandidate
    }

    private fun getPosts(url: URL, pages: Int): Set<Post> {
        return (1..pages)
            .mapNotNull { page ->
                client.fetchDocument(url.withEncodedPath().appendPath("/pagina${page}").toString())
                    ?.select(MAIN_PAGE_POST_SELECTOR)
                    ?.mapNotNull { it.attr("href") }
                    ?.filter { !it.startsWith("https") }
                    ?.map {
                        val b = UriComponentsBuilder.fromUri(url.toURI())
                        Post(
                            link = b.replacePath(it).build().toUriString(),
                            hasGPS = false,
                            hasVideo = false
                        )
                    }
            }
            .flatten()
            .toSet()
    }
}