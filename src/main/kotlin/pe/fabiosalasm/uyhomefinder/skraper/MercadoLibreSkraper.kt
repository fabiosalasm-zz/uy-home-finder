package pe.fabiosalasm.uyhomefinder.skraper

import mu.KotlinLogging
import org.javamoney.moneta.Money
import org.jsoup.nodes.Document
import org.springframework.web.util.UriComponentsBuilder
import pe.fabiosalasm.uyhomefinder.domain.House
import pe.fabiosalasm.uyhomefinder.domain.Point
import pe.fabiosalasm.uyhomefinder.domain.StoreMode
import pe.fabiosalasm.uyhomefinder.extensions.orElse
import pe.fabiosalasm.uyhomefinder.extensions.replace
import pe.fabiosalasm.uyhomefinder.extensions.toMoney
import pe.fabiosalasm.uyhomefinder.extensions.withEncodedPath
import java.net.URL
import java.time.LocalDate
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
    override fun fetchHousesForRental(): Set<House> {
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

        return departments
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
                    .asSequence()
                    .mapNotNull { postUrl ->
                        //TODO: In case page cannot be fetched, return null and warn behaviour to further investigation
                        val doc = client.fetchDocument(url = postUrl)!!

                        House(
                            id = extractHouseId(postUrl).orEmpty(),
                            source = name,
                            title = extractHouseTitle(doc).orEmpty(),
                            link = postUrl,
                            address = extractHouseAddress(doc).orEmpty(),
                            price = extractHousePrice(doc)
                                .orElse(Money.zero(Monetary.getCurrency("UYU"))),
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

    private fun extractHouseLocation(doc: Document): Point? {
        val pointAsText = doc.getElementsByTag("script")
            .asSequence()
            .filter { it.hasAttr("type") && it.attr("type") == "text/javascript" }
            .filter {
                it.data()
                    ?.contains("var mapContainer = document.getElementById('sectionDynamicMap');")
                    ?: false
            }
            .mapNotNull { scriptElement ->
                val googleMapsUrl = scriptElement.data()
                    ?.substringAfter("mapImage.setAttribute('srcset', '")
                    ?.substringBefore("= 1x,")
                    ?: return@mapNotNull null

                try {
                    UriComponentsBuilder.fromUriString(googleMapsUrl)
                        .build().queryParams.getFirst("center")
                        ?.replace("%2C", ",")
                } catch (e: Exception) {
                    logger.warn {
                        "Error while parsing HTML document. Cannot find location info in text: $googleMapsUrl"
                    }
                    null
                }
            }
            .firstOrNull()
            .also {
                if (it == null)
                    logger.warn {
                        "Error while parsing HTML document. Cannot find <script> with location url"
                    }
            } ?: return null

        return try {
            Point.fromText(pointAsText)
        } catch (e: Exception) {
            logger.warn { "Error while parsing HTML document: cannot parse $pointAsText to Point(latitude, longitude)" }
            null
        }
    }

    private fun extractHousePicLinks(doc: Document): List<String> {
        return doc.select("label.gallery__thumbnail img")
            .mapNotNull { it.attr("src") }
            .toList()
    }

    private fun catalogFeatures(doc: Document): Map<String, Any> {
        val features = doc.select("ul.specs-list li.specs-item")
            .mapNotNull { element ->
                val key = element.selectFirst("strong")?.ownText()?.trim()
                    .also {
                        if (it == null)
                            logger.warn {
                                "Error while parsing HTML document: Cannot find css query ${element.cssSelector()} strong"
                            }
                    } ?: return@mapNotNull null

                val value = element.selectFirst("span")?.ownText()?.trim()
                    .also {
                        if (it == null)
                            logger.warn {
                                "Error while parsing HTML document: Cannot find css query ${element.cssSelector()} span"
                            }
                    } ?: return@mapNotNull null

                when (key) {
                    "Superficie total" -> {
                        value.substringBefore(" m²").toIntOrNull()
                            ?.let { "totalSqlMeters" to it }
                            .also {
                                if (it == null)
                                    logger.warn {
                                        "Error while extracting house feature 'totalSqMeters'. Expected pattern (\\d m²) but found $value"
                                    }
                            }
                    }
                    "Superficie cubierta" -> {
                        value.substringBefore(" m²").toIntOrNull()
                            ?.let { "houseSqMeters" to it }
                            .also {
                                if (it == null)
                                    logger.warn {
                                        "Error while extracting house feature 'houseSqMeters'. Expected pattern (\\d m²) but found $value"
                                    }
                            }
                    }
                    "Ambientes" -> {
                        value.toIntOrNull()
                            ?.let { "numberSpaces" to it }
                            .also {
                                if (it == null)
                                    logger.warn {
                                        "Error while extracting house feature 'numberSpaces'. Expected integer value but found: $value"
                                    }
                            }
                    }
                    "Dormitorios" -> {
                        value.toIntOrNull()
                            ?.let { "numberBedrooms" to it }
                            .also {
                                if (it == null)
                                    logger.warn {
                                        "Error while extracting house feature 'numberBedrooms'. Expected integer value but found: $value"
                                    }
                            }
                    }
                    "Baños" -> {
                        value.toIntOrNull()
                            ?.let { "numberBathrooms" to it }
                            .also {
                                if (it == null)
                                    logger.warn {
                                        "Error while extracting house feature 'numberBathrooms'. Expected integer value but found: $value"
                                    }
                            }
                    }
                    "Cantidad de pisos" -> {
                        value.toIntOrNull()
                            ?.let { "numberFloors" to it }
                            .also {
                                if (it == null)
                                    logger.warn {
                                        "Error while extracting house feature 'numberFloors'. Expected integer value but found: $value"
                                    }
                            }
                    }
                    "Antigüedad" -> {
                        value.substringBefore(" años").toIntOrNull()
                            ?.let {
                                if (it > 1000) "constructionYear" to it
                                else "constructionYear" to LocalDate.now().minusYears(it.toLong()).year
                            }.also {
                                if (it == null)
                                    logger.warn {
                                        "Error while extracting house feature 'constructionYear'. Expected pattern (\\d años) but found $value"
                                    }
                            }
                    }
                    "Cocheras" -> {
                        value.toIntOrNull()
                            ?.let { "numberGarages" to it }
                            .also {
                                if (it == null)
                                    logger.warn {
                                        "Error while extracting house feature 'numberGarages'. Expected integer value but found: $value"
                                    }
                            }
                    }
                    else -> {
                        logger.warn { "Ignoring feature: $key -> $value" }
                        null
                    }
                }
            }
            .distinct()
            .toMap()

        val additionalFeatures = doc.select("ul.attribute-list li")
            .mapNotNull { element ->
                when (val key = element.ownText().trim()) {
                    "Placards" -> "hasWardrobe" to true
                    "Cocina" -> "hasKitchen" to true
                    "Baño social" -> "hasVisitBathroom" to true
                    "Comedor" -> "hasDiningRoom" to true
                    "Aire acondicionado" -> "hasAirConditioner" to true
                    "Altillo" -> "hasAttic" to true
                    "Living" -> "hasLivingRoom" to true
                    "Jardín" -> "hasGarden" to true
                    "Balcón" -> "hasBalcony" to true
                    "Terraza" -> "hasTerrace" to true
                    "Patio" -> "hasYard" to true
                    "Vestidor" -> "hasDressingRoom" to true
                    "Dormitorio de servicio" -> "hasServiceRoom" to true
                    "seguridad 24 horas" -> "has247Security" to true
                    "Piscina" -> "hasPool" to true
                    "Calefacción" -> "hasHeatingSystem" to true
                    else -> {
                        logger.warn { "Ignoring feature: $key -> ${true}" }
                        null
                    }
                }
            }
            .distinct()
            .toMap()

        return features + additionalFeatures
    }

    private fun extractHouseDescription(doc: Document): String? {
        val cssQuery = "div.item-description__text p"
        return doc.selectFirst(cssQuery)?.text()?.trim()
            .also {
                if (it == null)
                    logger.warn {
                        "Error while extracting house description: Cannot find css query: $cssQuery"
                    }
            }
    }

    private fun extractHouseNeighbourhood(doc: Document): String? {
        val cssQuery = "ul.vip-navigation-breadcrumb-list li:nth-child(5) a span"

        return doc.selectFirst(cssQuery)?.ownText()?.trim()
            .also {
                if (it == null)
                    logger.warn {
                        "Error while extracting house neighbourhood: Cannot find css query: $cssQuery"
                    }
            }
    }

    private fun extractHousePrice(doc: Document): Money? {
        val currencyCssQuery = "span.price-tag-symbol"
        val amountCssQuery = "span.price-tag-fraction"

        val currencyText = doc.selectFirst(currencyCssQuery)?.ownText()?.trim()
            .also {
                if (it == null)
                    logger.warn {
                        "Error while extracting house price: Cannot find currency using css query: $currencyCssQuery"
                    }
            } ?: return null

        val currency = when {
            currencyText.startsWith("\$") -> currencyText.replace("\$", "UYU")
            currencyText.startsWith("U\$S") -> currencyText.replace("U\$S", "USD")
            else -> {
                logger.warn {
                    "Error while extracting house price: Currency $currencyText is invalid or unknown"
                }
                null
            }
        } ?: return null

        val amount = doc.selectFirst(amountCssQuery)?.ownText()?.trim()
            .also {
                if (it == null)
                    logger.warn {
                        "Error while extracting house price: Cannot find amount using css query: $amountCssQuery"
                    }
            } ?: return null

        return try {
            "$currency $amount".toMoney()
        } catch (e: Exception) {
            logger.error(e) { "Error while parsing house price: " }
            null
        }
    }

    private fun extractHouseAddress(doc: Document): String? {
        val addressCssQuery = "h2.map-address"
        val locationCssQuery = "h3.map-location"

        val address = doc.selectFirst(addressCssQuery)?.ownText()?.trim()
            .also {
                if (it == null)
                    logger.warn {
                        "Error while extracting house address. Cannot find css query: $addressCssQuery"
                    }
            } ?: return null

        val location = doc.selectFirst(locationCssQuery)?.ownText()?.trim()
            .also {
                if (it == null)
                    logger.warn {
                        "Error while extracting house address. Cannot find css query: $locationCssQuery"
                    }
            } ?: return null

        return "$address, $location"
    }

    private fun extractHouseTitle(doc: Document): String? {
        val cssQuery = "h1.item-title__primary"
        return doc.selectFirst(cssQuery)?.ownText()?.trim()
            .also {
                if (it == null)
                    logger.warn {
                        "Error while extracting house title: Cannot find info using css query: $cssQuery"
                    }
            }
    }

    private fun extractHouseId(urlString: String): String? {
        return """(https://casa.mercadolibre.com.uy/MLU-)(\d{2,13})(-\w+)""".toRegex().find(urlString)
            ?.groupValues?.get(2)
            ?.trim()
            .also {
                if (it == null)
                    logger.warn { "Error while extracting id from post URL: Cannot find id in $urlString" }
            }
    }

    private fun getPosts(url: URL, pages: Int): Set<String> {
        return (1..pages)
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

                document.select("a.ui-search-result__content.ui-search-link")
                    .mapNotNull { element ->
                        // posts link usually include additional info for tracking purpose, we can remove that
                        element.attr("href").replace("""JM#\S+""".toRegex(), "JM")
                        //TODO: Ensure text extracted is an actual link
                    }
                    .toList()
            }
            .flatten()
            .toSet()
    }

    private fun calculateTotalPages(url: URL): Int? {
        val pageSize = 48
        val cssQuery = "span.ui-search-search-result__quantity-results"

        val totalResultsText = client.fetchDocument(url = url.withEncodedPath().toString())
            ?.selectFirst(cssQuery)
            ?.ownText()
            .also {
                if (it == null)
                    logger.warn { "Error while extracting total pages available: cannot find info using css query: $" }
            } ?: return null

        val count = """(\d{1,10}) resultados""".toRegex().find(totalResultsText)
            ?.groupValues?.get(1)?.toDouble()
            .also {
                if (it == null)
                    logger.warn { "Error while extracting total pages available: cannot parse text '$totalResultsText'" }
            } ?: return null

        return ceil(count.div(pageSize)).toInt()
    }
}