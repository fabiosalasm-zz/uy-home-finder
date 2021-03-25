package pe.fabiosalasm.uyhomefinder.skraper

import mu.KotlinLogging
import org.javamoney.moneta.Money
import org.jsoup.nodes.Document
import org.springframework.web.util.UriComponentsBuilder
import pe.fabiosalasm.uyhomefinder.domain.Point
import pe.fabiosalasm.uyhomefinder.extensions.toMoney

object GallitoDocumentHelper {
    private val logger = KotlinLogging.logger { }
    private const val RENTAL_PAGE_ID_SELECTOR = "input#HfCodigoAviso"
    private const val RENTAL_PAGE_TITLE_SELECTOR = "div#div_datosBasicos h1.titulo"
    private const val RENTAL_PAGE_ADDRESS_SELECTOR = "div#div_datosBasicos h2.direccion"
    private const val RENTAL_PAGE_TLF_SELECTOR = "input#HfTelefono"
    private const val RENTAL_PAGE_PRICE_SELECTOR = "div#div_datosBasicos div.wrapperFavorito span.precio"
    private const val RENTAL_PAGE_DPT_SELECTOR = "nav.breadcrumb-w100 li.breadcrumb-item:nth-child(5) a"
    private const val RENTAL_PAGE_NGH_SELECTOR = "nav.breadcrumb-w100 li.breadcrumb-item:nth-child(6) a"
    private const val RENTAL_PAGE_DESC_SELECTOR = "section#descripcion div.p-3 p"
    private const val RENTAL_PAGE_WARR_SELECTOR =
        "section#garantias div.p-3 ul#ul_garantias li.list-group-item.border-0"
    private const val RENTAL_PAGE_GALLERY_SELECTOR = "div#galeria div.carousel-item.item a"
    private const val RENTAL_PAGE_GPS_SELECTOR = "div#ubicacion iframe#iframeMapa"
    private const val RENTAL_PAGE_VIDEO_SELECTOR = "div#video iframe#iframe_video"
    private const val RENTAL_PAGE_FEAT_SELECTOR =
        "section#caracteristicas div.p-3 ul#ul_caracteristicas li.list-group-item.border-0"
    private const val TEST = "div.wrapperDatos div.iconoDatos.rounded-circle i.fas.fa-bed"

    fun extractHouseId(doc: Document) =
        doc.selectFirst(RENTAL_PAGE_ID_SELECTOR)?.attr("value")
            ?.trim()
            .also {
                if (it == null)
                    logger.warn { "Error while extracting id from post URL: Cannot find id in '$RENTAL_PAGE_ID_SELECTOR'" }
            }

    fun extractHouseTitle(doc: Document) =
        doc.selectFirst(RENTAL_PAGE_TITLE_SELECTOR)?.ownText()?.trim()
            .also {
                if (it == null)
                    logger.warn {
                        "Error while extracting house title: Cannot find info using css query: '$RENTAL_PAGE_TITLE_SELECTOR'"
                    }
            }

    fun extractHouseAddress(doc: Document) =
        doc.selectFirst(RENTAL_PAGE_ADDRESS_SELECTOR)?.ownText()?.trim()
            .also {
                if (it == null)
                    logger.warn {
                        "Error while extracting house address: Cannot find info using css query: '$RENTAL_PAGE_ADDRESS_SELECTOR'"
                    }
            }

    fun extractHousePhone(doc: Document): String? =
        doc.selectFirst(RENTAL_PAGE_TLF_SELECTOR)?.attr("value")?.trim()
            .also {
                if (it == null)
                    logger.warn {
                        "Error while extracting house phone: Cannot find info using css query: '$RENTAL_PAGE_TLF_SELECTOR'"
                    }
            }

    fun extractHousePrice(doc: Document): Money? {
        var priceText = doc.selectFirst(RENTAL_PAGE_PRICE_SELECTOR)?.ownText()?.trim()
            .also {
                if (it == null)
                    logger.warn {
                        "Error while extracting house price: Cannot find currency using css query: '$RENTAL_PAGE_PRICE_SELECTOR'"
                    }
            } ?: return null

        priceText = when { // the app only recognises as valid currencies: UYU and USD
            priceText.startsWith("\$U ") -> priceText.replace("\$U", "UYU")
            priceText.startsWith("U\$S") -> priceText.replace("U\$S", "USD")
            else -> {
                logger.warn {
                    "Error while extracting house price: Price's currency in $priceText is invalid or unknown"
                }
                null
            }
        } ?: return null

        return try {
            priceText.toMoney()
        } catch (e: Exception) {
            logger.error(e) { "Error while parsing house price: " }
            null
        }
    }

    fun extractHouseDepartment(doc: Document) =
        doc.selectFirst(RENTAL_PAGE_DPT_SELECTOR)?.ownText()?.trim()
            .also {
                if (it == null)
                    logger.warn {
                        "Error while extracting house department: Cannot find css query: '$RENTAL_PAGE_DPT_SELECTOR'"
                    }
            }

    fun extractHouseNeighbourhood(doc: Document) =
        doc.selectFirst(RENTAL_PAGE_NGH_SELECTOR)?.ownText()?.trim()
            .also {
                if (it == null)
                    logger.warn {
                        "Error while extracting house neighbourhood: Cannot find css query: '$RENTAL_PAGE_NGH_SELECTOR'"
                    }
            }

    fun extractHouseDescription(doc: Document): String {
        return doc.select(RENTAL_PAGE_DESC_SELECTOR)
            .joinToString(" ") { ele -> ele.wholeText().trim() }.trim()
            .also {
                if (it.isEmpty())
                    logger.warn {
                        "Error while extracting house description: Cannot find css query: '$RENTAL_PAGE_DESC_SELECTOR' or there is no content"
                    }
            }
    }

    fun extractHouseWarranties(doc: Document) = doc.select(RENTAL_PAGE_WARR_SELECTOR)
        .mapNotNull { it.ownText()?.trim() }.toList()

    fun extractHousePicLinks(doc: Document) = doc.select(RENTAL_PAGE_GALLERY_SELECTOR)
        .mapNotNull { it.attr("href") }.toList()

    fun extractHouseLocation(doc: Document): Point? {
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

    fun extractHouseVideoLink(doc: Document) =
        doc.selectFirst(RENTAL_PAGE_VIDEO_SELECTOR)
            ?.attr("src")

    fun catalogFeatures(document: Document): Map<String, Any> {
        val features = document.select(RENTAL_PAGE_FEAT_SELECTOR)
            .mapNotNull { it.ownText() }
            .mapNotNull { rawFeature ->
                when {
                    """Padrón: \w+""".toRegex().containsMatchIn(rawFeature) -> {
                        "register" to rawFeature.removePrefix("Padrón: ")
                    }
                    """Estado: \w+""".toRegex().containsMatchIn(rawFeature) -> {
                        "buildingState" to rawFeature.removePrefix("Estado: ")
                    }
                    """(\d) (Baño[s]?)""".toRegex().containsMatchIn(rawFeature) -> {
                        "numberBathrooms" to ("""(\d) (Baño[s]?)""".toRegex().find(rawFeature)!!
                            .groupValues[1].toInt())
                    }
                    rawFeature == "Cocina" || rawFeature == "Kitchenette" -> {
                        "hasKitchen" to true
                    }
                    """Techo: \w+""".toRegex().containsMatchIn(rawFeature) -> {
                        "roofType" to rawFeature.removePrefix("Techo: ")
                    }
                    """(Sup. construida:) (\d{1,5})m²""".toRegex().containsMatchIn(rawFeature) -> {
                        "houseSqMeters" to """(Sup. construida:) (\d{1,5})m²""".toRegex().find(rawFeature)!!
                            .groupValues[2].toInt()
                    }
                    """Gastos Comunes: \$(U\d+)""".toRegex().containsMatchIn(rawFeature) -> {
                        "commonExpenses" to ("""Gastos Comunes: \$(U\d+)""".toRegex().find(rawFeature)!!
                            .groupValues[1].replace("U", "UYU ") // polishing string to cast it into money
                            .toMoney())
                    }
                    """(Año:) (\d+)""".toRegex().containsMatchIn(rawFeature) -> {
                        "constructionYear" to ("""(Año:) (\d+)""".toRegex().find(rawFeature)!!
                            .groupValues[2].toInt())
                    }
                    """(Cantidad de plantas:) (\d+)""".toRegex().containsMatchIn(rawFeature) -> {
                        "numberFloors" to ("""(Cantidad de plantas:) (\d+)""".toRegex().find(rawFeature)!!
                            .groupValues[2].toInt())
                    }
                    rawFeature == "Baño social" || rawFeature == "Baño de Servicio" -> "hasVisitBathroom" to true
                    rawFeature == "Aire aconodicionado" -> "hasAirConditioner" to true
                    else -> null
                }
            }
            .distinct()
            .toMap().toMutableMap()

        val bedroomFeature = document.selectFirst(TEST)
            ?.parent()
            ?.nextElementSibling()
            ?.ownText()

        if (bedroomFeature != null && """(más de )?(\d) dormitorios""".toRegex().containsMatchIn(bedroomFeature)) {
            features["numberBedrooms"] =
                """(más de )?(\d) dormitorios""".toRegex().find(bedroomFeature)!!.groupValues[2].toInt()
        }

        return features
    }
}