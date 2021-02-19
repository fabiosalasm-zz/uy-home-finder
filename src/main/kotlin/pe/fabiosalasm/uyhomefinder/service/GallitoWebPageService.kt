package pe.fabiosalasm.uyhomefinder.service

import it.skrape.core.htmlDocument
import it.skrape.extract
import it.skrape.extractIt
import it.skrape.selects.eachHref
import it.skrape.selects.eachText
import org.springframework.stereotype.Service
import org.springframework.web.util.UriComponentsBuilder
import pe.fabiosalasm.uyhomefinder.House
import pe.fabiosalasm.uyhomefinder.Post
import pe.fabiosalasm.uyhomefinder.applyHouseFilters
import pe.fabiosalasm.uyhomefinder.catalogFeatures
import pe.fabiosalasm.uyhomefinder.extensions.cloneAndReplace
import pe.fabiosalasm.uyhomefinder.extensions.toMoney
import pe.fabiosalasm.uyhomefinder.extensions.toScraperRunner
import java.net.URL
import kotlin.math.ceil

const val MAIN_PAGE_POST_SELECTOR = "div.img-responsive.aviso-ico-contiene"
const val MAIN_PAGE_POST_LINK_SELECTOR = "img.img-seva.img-responsive"
const val MAIN_PAGE_GPS_SELECTOR = "span img[src='/img/gpsicon.png']"
const val MAIN_PAGE_VIDEO_SELECTOR = "span img[src='/img/camicon.png']"
const val RENTAL_PAGE_ID_SELECTOR = "input#HfCodigoAviso"
const val RENTAL_PAGE_TITLE_SELECTOR = "div#div_datosBasicos h1.titulo"
const val RENTAL_PAGE_ADDRESS_SELECTOR = "div#div_datosBasicos h2.direccion"
const val RENTAL_PAGE_TLF_SELECTOR = "input#HfTelefono"
const val RENTAL_PAGE_PRICE_SELECTOR = "div#div_datosBasicos div.wrapperFavorito span.precio"
const val RENTAL_PAGE_DPT_SELECTOR = "nav.breadcrumb-w100 li.breadcrumb-item"
const val RENTAL_PAGE_DESC_SELECTOR = "section#descripcion div.p-3 p"
const val RENTAL_PAGE_FEAT_SELECTOR =
    "section#caracteristicas div.p-3 ul#ul_caracteristicas li.list-group-item.border-0"
const val RENTAL_PAGE_WARR_SELECTOR = "section#garantias div.p-3 ul#ul_garantias li.list-group-item.border-0"
const val RENTAL_PAGE_GALLERY_SELECTOR = "div#galeria div.carousel-item.item a"
const val RENTAL_PAGE_GPS_SELECTOR = "div#ubicacion iframe#iframeMapa"
const val RENTAL_PAGE_VIDEO_SELECTOR = "div#video iframe#iframe_video"

@Service
class GallitoWebPageService {

    fun getHousesForRent(): Set<House> {
        //TODO: Put these constants in a database
        val minSquareMeter = 75
        val maxPrice = 30_000
        val pageSize = 80

        val urlTemplate = """
            https://www.gallito.com.uy/inmuebles/casas/alquiler/pre-0-{maxPrice}-pesos/sup-{minSquareMeter}-500-metros!cant={size}
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
            .map { post -> // follow each link to get detailed info (represented as RentalPage)
                URL(post.link)
                    .toScraperRunner().extractIt<House> {
                        htmlDocument {
                            it.id = RENTAL_PAGE_ID_SELECTOR { findFirst { this } }.attribute("value").toLong()
                            it.title = RENTAL_PAGE_TITLE_SELECTOR { findFirst { text } }
                            it.link = post.link
                            it.address = RENTAL_PAGE_ADDRESS_SELECTOR { findFirst { text } }
                            it.telephone = RENTAL_PAGE_TLF_SELECTOR { findFirst { this } }.attribute("value")
                            it.price = RENTAL_PAGE_PRICE_SELECTOR { findFirst { text.toMoney() } }
                            it.department = RENTAL_PAGE_DPT_SELECTOR { findSecondLast { text } }
                            it.neighbourhood = RENTAL_PAGE_DPT_SELECTOR { findLast { text } }

                            it.description = RENTAL_PAGE_DESC_SELECTOR { findAll { this } }.eachText
                                .joinToString(" ").removeSurrounding(" ")

                            val rawFeatures = RENTAL_PAGE_FEAT_SELECTOR { findAll { this } }.eachText
                            it.features = catalogFeatures(rawFeatures)

                            it.warranties = RENTAL_PAGE_WARR_SELECTOR { findAll { this } }.eachText
                                .map { it.removeSurrounding(" ") }

                            it.pictureLinks = RENTAL_PAGE_GALLERY_SELECTOR { findAll { this } }.eachHref

                            if (post.hasGPS) {
                                val gpsURL = RENTAL_PAGE_GPS_SELECTOR { findFirst { this } }.attribute("src")
                                it.geoReference = UriComponentsBuilder.fromUriString(gpsURL).build()
                                    .queryParams.getFirst("q")!!
                            }

                            if (post.hasVideo) {
                                it.videoLink = RENTAL_PAGE_VIDEO_SELECTOR { findFirst { this } }.attribute("src")
                            }
                        }
                    }
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
        val count = url.toScraperRunner().extract {
            htmlDocument {
                //TODO: make this code dont assume array size and cast
                "li#resultados strong" { findFirst { text } }.split(" ")[1].toDouble()
            }
        }

        return ceil(count.div(pageSize)).toInt() //TODO: evaluate when count < size
    }

    private fun getPosts(url: URL, pages: Int): Set<Post> {
        val posts = mutableSetOf<Post>()
        for (page in 1..pages) {
            // obtain all the posts in each page and save them for further analysis
            val postsInPage = url.cloneAndReplace(query = "pag=${page}")
                .toScraperRunner().extract {
                    htmlDocument {
                        // we dont want to throw exceptions if an html element is not found. Applies only for findAll()
                        relaxed = true

                        MAIN_PAGE_POST_SELECTOR { findAll { this } }
                            .mapNotNull {
                                relaxed = true

                                val link = it.findAll(MAIN_PAGE_POST_LINK_SELECTOR)
                                    .firstOrNull()
                                    ?.attribute("alt")

                                if (link != null) {
                                    Post().apply {
                                        this.link = link
                                        this.hasGPS = it.findAll(MAIN_PAGE_GPS_SELECTOR).firstOrNull() != null
                                        this.hasVideo = it.findAll(MAIN_PAGE_VIDEO_SELECTOR).firstOrNull() != null
                                    }
                                } else {
                                    null
                                }
                            }
                            .toSet()
                    }
                }

            posts.addAll(postsInPage)
        }

        return posts
    }
}