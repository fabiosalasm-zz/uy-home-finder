package pe.fabiosalasm.uyhomefinder.service

import mu.KotlinLogging
import org.jsoup.Jsoup
import org.springframework.stereotype.Service
import org.springframework.web.util.UriComponentsBuilder
import pe.fabiosalasm.uyhomefinder.House
import pe.fabiosalasm.uyhomefinder.Post
import pe.fabiosalasm.uyhomefinder.applyHouseFilters
import pe.fabiosalasm.uyhomefinder.catalogFeatures
import pe.fabiosalasm.uyhomefinder.extensions.cloneAndReplace
import pe.fabiosalasm.uyhomefinder.extensions.toMoney
import java.lang.IllegalArgumentException
import java.net.URL
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
private const val RENTAL_PAGE_DPT_SELECTOR = "nav.breadcrumb-w100 li.breadcrumb-item:nth-child(5)"
private const val RENTAL_PAGE_NGH_SELECTOR = "nav.breadcrumb-w100 li.breadcrumb-item:nth-child(6)"
private const val RENTAL_PAGE_DESC_SELECTOR = "section#descripcion div.p-3 p"
private const val RENTAL_PAGE_FEAT_SELECTOR =
    "section#caracteristicas div.p-3 ul#ul_caracteristicas li.list-group-item.border-0"
private const val RENTAL_PAGE_WARR_SELECTOR = "section#garantias div.p-3 ul#ul_garantias li.list-group-item.border-0"
private const val RENTAL_PAGE_GALLERY_SELECTOR = "div#galeria div.carousel-item.item a"
private const val RENTAL_PAGE_GPS_SELECTOR = "div#ubicacion iframe#iframeMapa"
private const val RENTAL_PAGE_VIDEO_SELECTOR = "div#video iframe#iframe_video"

@Service
class GallitoWebPageService {

    private companion object {
        val logger = KotlinLogging.logger {}
    }

    fun getHousesForRent(): Set<House> {
        //TODO: Put these constants in a database
        val minSquareMeter = 75
        val maxPrice = 1_000
        val pageSize = 80

        val urlTemplate = """
            https://www.gallito.com.uy/inmuebles/casas/alquiler/pre-0-{maxPrice}-dolares/sup-{minSquareMeter}-500-metros!cant={size}
        """.trimIndent()

        val url = UriComponentsBuilder.fromUriString(urlTemplate).build()
            .expand(
                mapOf(
                    "maxPrice" to maxPrice,
                    "minSquareMeter" to minSquareMeter,
                    "size" to pageSize
                )
            )
            .toUri().toURL()

        val pages = calculateTotalPages(url, pageSize)

        return getPosts(url, pages)
            .asSequence()
            .mapNotNull { post ->
                val doc = Jsoup.connect(post.link).timeout(30_000).get()!!

                val id = doc.selectFirst(RENTAL_PAGE_ID_SELECTOR)?.attr("value")?.toLong()
                if (id == null) {
                    logger.warn { "cannot find id in post: ${post.link}" }
                    null
                }

                val title = doc.selectFirst(RENTAL_PAGE_TITLE_SELECTOR)?.text()
                if (title == null) {
                    logger.warn { "cannot find title in post: ${post.link}" }
                    null
                }

                val address = doc.selectFirst(RENTAL_PAGE_ADDRESS_SELECTOR)?.text()
                if (address == null) {
                    logger.warn { "cannot find address in post: ${post.link}" }
                    null
                }

                val telephone = doc.selectFirst(RENTAL_PAGE_TLF_SELECTOR)?.attr("value")
                if (telephone == null) {
                    logger.warn { "cannot find telephone in post: ${post.link}" }
                    null
                }

                val price = doc.selectFirst(RENTAL_PAGE_PRICE_SELECTOR)?.text()
                    ?.let {
                        when {
                            it.startsWith("\$U ") -> it.replace("\$U", "UYU")
                            it.startsWith("U\$S") -> it.replace("U\$S", "USD")
                            else -> throw IllegalArgumentException("Price expressed as: $it is invalid or unknown")
                        }
                    }?.toMoney()

                if (price == null) {
                    logger.warn { "cannot find/process price in post: ${post.link}" }
                    null
                }

                val department = doc.selectFirst(RENTAL_PAGE_DPT_SELECTOR)?.text()
                if (department == null) {
                    logger.warn { "cannot find department in post: ${post.link}" }
                    null
                }

                val neighbourhood = doc.selectFirst(RENTAL_PAGE_NGH_SELECTOR)?.text()
                if (neighbourhood == null) {
                    logger.warn { "cannot find neighbourhood in post: ${post.link}" }
                    null
                }

                val description = doc.select(RENTAL_PAGE_DESC_SELECTOR)
                    .joinToString(" ") { ele ->
                        ele.text().removeSurrounding(" ")
                    }.removeSurrounding(" ")

                val rawFeatures = doc.select(RENTAL_PAGE_FEAT_SELECTOR)
                    .mapNotNull { it.text() }
                val features = catalogFeatures(rawFeatures)

                val warranties = doc.select(RENTAL_PAGE_WARR_SELECTOR)
                    .mapNotNull { it.text()?.removeSurrounding(" ") }.toList()

                val pictureLinks = doc.select(RENTAL_PAGE_GALLERY_SELECTOR)
                    .mapNotNull { it.attr("href") }.toList()

                val geoReference = if (post.hasGPS) {
                    doc.selectFirst(RENTAL_PAGE_GPS_SELECTOR)
                        ?.attr("src")
                        ?.let {
                            UriComponentsBuilder.fromUriString(it)
                                .build().queryParams.getFirst("q")
                        }
                } else {
                    null
                }

                val videoLink = if (post.hasVideo) {
                    doc.selectFirst(RENTAL_PAGE_VIDEO_SELECTOR)
                        ?.attr("src")
                } else {
                    null
                }

                House(
                    id = id!!,
                    title = title!!,
                    link = post.link,
                    address = address!!,
                    telephone = telephone!!,
                    price = price!!,
                    department = department!!,
                    neighbourhood = neighbourhood!!,
                    description = description,
                    features = features,
                    warranties = warranties,
                    pictureLinks = pictureLinks,
                    geoReference = geoReference,
                    videoLink = videoLink
                )
            }
            .filter(::applyHouseFilters)
            .toSet()
        // TODO: save the details, if not existing
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
    private fun calculateTotalPages(url: URL, pageSize: Int): Int {
        val doc = Jsoup.connect(url.toString()).timeout(30_000).get()!!

        val text = doc.select(MAIN_PAGE_TOTAL_POST_SELECTOR)
            .firstOrNull()
            ?.text() ?: "de 0"

        //TODO: make this code dont assume array size and cast
        val count = text.split(" ")[1].toDouble()

        return ceil(count.div(pageSize)).toInt() //TODO: evaluate when count < size
    }

    private fun getPosts(url: URL, pages: Int): Set<Post> {
        return (1..pages)
            .map { page ->
                val doc = Jsoup.connect(url.cloneAndReplace(query = "pag=${page}").toString()).timeout(30_000).get()!!
                doc.select(MAIN_PAGE_POST_SELECTOR)
                    .mapNotNull { ele ->
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
}