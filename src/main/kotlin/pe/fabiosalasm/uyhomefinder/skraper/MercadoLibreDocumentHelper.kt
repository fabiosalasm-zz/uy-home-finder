package pe.fabiosalasm.uyhomefinder.skraper

import mu.KotlinLogging
import org.javamoney.moneta.Money
import org.jsoup.nodes.Document
import org.springframework.web.util.UriComponentsBuilder
import pe.fabiosalasm.uyhomefinder.domain.Point
import pe.fabiosalasm.uyhomefinder.extensions.toMoney
import java.time.LocalDate

object MercadoLibreDocumentHelper {
    private val logger = KotlinLogging.logger { }
    private const val HOUSE_TITLE_CSS_QUERY = "h1.item-title__primary"
    private const val ADDRESS_CSS_QUERY = "h2.map-address"
    private const val LOCATION_CSS_QUERY = "h3.map-location"
    private const val CURRENCY_CSS_QUERY = "span.price-tag-symbol"
    private const val AMOUNT_CSS_QUERY = "span.price-tag-fraction"
    private const val NEIGHBOURHOOD_CSS_QUERY = "ul.vip-navigation-breadcrumb-list li:nth-child(5) a span"
    private const val DEPARTMENT_CSS_QUERY = "ul.vip-navigation-breadcrumb-list li:nth-child(4) a span"
    private const val DESCRIPTION_CSS_QUERY = "div.item-description__text p"
    private const val PIC_CSS_QUERY = "label.gallery__thumbnail img"
    private const val URL_PATTERN = """(https://casa.mercadolibre.com.uy/MLU-)(\d{2,13})(\S+)"""
    private const val TOTAL_SEARCH_RESULTS_CSS_QUERY = "span.ui-search-search-result__quantity-results"
    private const val POST_LINK_CSS_QUERY = "a.ui-search-result__content.ui-search-link"

    fun extractHouseTitle(doc: Document): String? {
        return doc.selectFirst(HOUSE_TITLE_CSS_QUERY)?.ownText()?.trim()
            .also {
                if (it == null)
                    logger.warn {
                        "Error while extracting house title: Cannot find info using css query: $HOUSE_TITLE_CSS_QUERY"
                    }
            }
    }

    fun extractHouseAddress(doc: Document): String? {
        val address = doc.selectFirst(ADDRESS_CSS_QUERY)?.ownText()?.trim()
            .also {
                if (it == null)
                    logger.warn {
                        "Error while extracting house address. Cannot find css query: $ADDRESS_CSS_QUERY"
                    }
            } ?: return null

        val location = doc.selectFirst(LOCATION_CSS_QUERY)?.ownText()?.trim()
            .also {
                if (it == null)
                    logger.warn {
                        "Error while extracting house address. Cannot find css query: $LOCATION_CSS_QUERY"
                    }
            } ?: return null

        return "$address, $location"
    }

    fun extractHousePrice(doc: Document): Money? {
        var currencyText = doc.selectFirst(CURRENCY_CSS_QUERY)?.ownText()?.trim()
            .also {
                if (it == null)
                    logger.warn {
                        "Error while extracting house price: Cannot find currency using css query: $CURRENCY_CSS_QUERY"
                    }
            } ?: return null

        currencyText = when { // the app only recognises as valid currencies: UYU and USD
            currencyText.startsWith("\$") -> currencyText.replace("\$", "UYU")
            currencyText.startsWith("U\$S") -> currencyText.replace("U\$S", "USD")
            else -> {
                logger.warn {
                    "Error while extracting house price: Currency $currencyText is invalid or unknown"
                }
                null
            }
        } ?: return null

        val amountText = doc.selectFirst(AMOUNT_CSS_QUERY)?.ownText()?.trim()
            .also {
                if (it == null)
                    logger.warn {
                        "Error while extracting house price: Cannot find amount using css query: $AMOUNT_CSS_QUERY"
                    }
            } ?: return null

        return try {
            "$currencyText $amountText".toMoney()
        } catch (e: Exception) {
            logger.error(e) { "Error while parsing house price: " }
            null
        }
    }

    fun extractHouseNeighbourhood(doc: Document): String? {
        return doc.selectFirst(NEIGHBOURHOOD_CSS_QUERY)?.ownText()?.trim()
            .also {
                if (it == null)
                    logger.warn {
                        "Error while extracting house neighbourhood: Cannot find css query: $NEIGHBOURHOOD_CSS_QUERY"
                    }
            }
    }

    fun extractHouseDepartment(doc: Document): String? {
        return doc.selectFirst(DEPARTMENT_CSS_QUERY)?.text()?.trim()
            .also {
                if (it == null)
                    logger.warn {
                        "Error while extracting house department: Cannot find css query: $DEPARTMENT_CSS_QUERY"
                    }
            }
    }

    fun extractHouseDescription(doc: Document): String? {
        return doc.selectFirst(DESCRIPTION_CSS_QUERY)?.text()?.trim()
            .also {
                if (it == null)
                    logger.warn {
                        "Error while extracting house description: Cannot find css query: $DESCRIPTION_CSS_QUERY"
                    }
            }
    }

    fun extractHousePicLinks(doc: Document): List<String> {
        return doc.select(PIC_CSS_QUERY)
            .mapNotNull { it.attr("src") }
            .toList()
    }

    fun extractHouseLocation(doc: Document): Point? {
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

    fun extractHouseId(urlString: String): String? {
        return URL_PATTERN.toRegex().find(urlString)
            ?.groupValues?.get(2)
            ?.trim()
            .also {
                if (it == null)
                    logger.warn { "Error while extracting id from post URL: Cannot find id in $urlString" }
            }
    }

    //TODO: return Set<URL>
    fun extractPostUrls(doc: Document): Set<String> {
        return doc.select(POST_LINK_CSS_QUERY)
            .mapNotNull { element ->
                // need to remove tracking information comming from links
                val link = element.attr("href").replace("""JM#\S+""".toRegex(), "JM") + "?redirectFromSimilar=true"
                val location = element.selectFirst("span.ui-search-item__group__element.ui-search-item__location")
                    ?.ownText()
                    ?: return@mapNotNull null

                val locationSplitted = location.split(", ")
                if (locationSplitted.size != 3) {
                    return@mapNotNull null
                }

                // filter similar to House.isNearByCapital
                return@mapNotNull when (locationSplitted[2].trim()) { // department
                    "Montevideo" -> link
                    "Canelones" -> when (locationSplitted[1].trim()) { // neighbourhood
                        "Aeropuerto Internacional De Carrasco",
                        "Barra de Carrasco", "Ciudad de la costa",
                        "Lomas de Solymar", "Colinas de Solymar",
                        "El Pinar", "General Líber Seregni",
                        "Lagomar", "La Paz", "Médanos de Solymar",
                        "Montes de Solymar", "Parque de Solymar",
                        "Paso de Carrasco", "San José de Carrasco",
                        "Shangrilá", "Solymar",
                        -> link
                        else -> null
                    }
                    else -> null
                }
            }
            .toSet()
    }

    fun extractTotalSearchResults(doc: Document): Double? {
        val totalResultsText = doc.selectFirst(TOTAL_SEARCH_RESULTS_CSS_QUERY)
            ?.ownText()
            ?.trim()
            .also {
                if (it == null)
                    logger.warn { "Error while extracting total pages available: cannot find info using css query: $TOTAL_SEARCH_RESULTS_CSS_QUERY" }
            } ?: return null

        return """(\d{1,10}) resultados""".toRegex().find(totalResultsText)
            ?.groupValues?.get(1)?.toDouble()
            .also {
                if (it == null)
                    logger.warn { "Error while extracting total pages available: cannot parse text '$totalResultsText'" }
            }
    }

    fun catalogFeatures(doc: Document): Map<String, Any> {
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
                    else -> null
                }
            }
            .distinct()
            .toMap()

        val additionalFeatures = doc.select("ul.attribute-list li")
            .mapNotNull { element ->
                when (element.ownText().trim()) {
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
                    else -> null
                }
            }
            .distinct()
            .toMap()

        return features + additionalFeatures
    }
}