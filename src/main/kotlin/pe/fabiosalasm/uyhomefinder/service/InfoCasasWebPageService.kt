package pe.fabiosalasm.uyhomefinder.service

import mu.KotlinLogging
import org.jsoup.Jsoup
import org.springframework.stereotype.Service
import org.springframework.web.util.UriComponentsBuilder
import pe.fabiosalasm.uyhomefinder.House
import pe.fabiosalasm.uyhomefinder.Post
import pe.fabiosalasm.uyhomefinder.extensions.appendPath
import pe.fabiosalasm.uyhomefinder.extensions.toMoney
import java.lang.IllegalArgumentException
import java.net.URL

private const val MAIN_PAGE_POST_SELECTOR = "a.holder-link.checkMob"
private const val RENTAL_PAGE_ID_SELECTOR = "i.icon-heart.animatable"
private const val RENTAL_PAGE_TITLE_SELECTOR = "h1.likeh2.titulo.one-line-txt"
private const val RENTAL_PAGE_TLF_SELECTOR = "span.lineInmo"
private const val RENTAL_PAGE_PRICE_SELECTOR = "p.precio-final"
private const val RENTAL_PAGE_NGH_SELECTOR = "a.part-breadcrumbs:nth-child(10)"
private const val RENTAL_PAGE_DESC_SELECTOR = "div#descripcion p"
private const val RENTAL_PAGE_WARR_SELECTOR = "div#garantias p"
private const val RENTAL_PAGE_GALLERY_SELECTOR = "div#slickAmpliadas img.imageBig"

@Service
class InfoCasasWebPageService {

    private companion object {
        val logger = KotlinLogging.logger {}
    }

    fun getHousesForRent(): Set<House> {
        //TODO: Put these constants in a database
        val department = "montevideo"
        val minSquareMeter = 70
        val maxPrice = 1_000

        //TODO: the URL involves a department pre-filter, so the whole operation should
        // be done per department (Montevideo and Canelones only considered for now)
        val urlTemplate = """
            https://www.infocasas.com.uy/alquiler/casas/{department}/hasta-{maxPrice}/dolares/m2-desde-{minSquareMeter}/edificados
        """.trimIndent()

        val url = UriComponentsBuilder.fromUriString(urlTemplate).build()
            .expand(
                mapOf(
                    "department" to department,
                    "maxPrice" to maxPrice,
                    "minSquareMeter" to minSquareMeter
                )
            )
            .toUri().toURL()

        val pages = calculateTotalPages(url)

        return getPosts(url, pages)
            .asSequence()
            .map { post ->
                val doc = Jsoup.connect(post.link).timeout(30_000).get()!!

                val id = doc.selectFirst(RENTAL_PAGE_ID_SELECTOR)?.attr("data-id")?.toLong()
                if (id == null) {
                    logger.warn { "cannot find id in post: ${post.link}" }
                    null
                }

                val title = doc.selectFirst(RENTAL_PAGE_TITLE_SELECTOR)?.text()
                if (title == null) {
                    logger.warn { "cannot find title in post: ${post.link}" }
                    null
                }

                val address = "TODO"

                val telephone = doc.selectFirst(RENTAL_PAGE_TLF_SELECTOR)?.text()
                if (telephone == null) {
                    logger.warn { "cannot find telephone in post: ${post.link}" }
                }

                val price = doc.selectFirst(RENTAL_PAGE_PRICE_SELECTOR)?.text()
                    ?.let {
                        when {
                            it.startsWith("\$") -> it.replace("\$", "UYU")
                            it.startsWith("U\$S") -> it.replace("U\$S", "USD")
                            else -> throw IllegalArgumentException("Price expressed as: $it is invalid or unknown")
                        }
                    }?.toMoney()

                if (price == null) {
                    logger.warn { "cannot find/process price in post: ${post.link}" }
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

                val features = mapOf<String, Any>("TODO" to "TODO")

                val warranties = doc.select(RENTAL_PAGE_WARR_SELECTOR)
                    .mapNotNull { it.text()?.removeSurrounding(" ") }

                val pictureLinks = doc.select(RENTAL_PAGE_GALLERY_SELECTOR)
                    .mapNotNull { it.attr("src") }

                House(
                    id = id!!,
                    title = title!!,
                    link = post.link,
                    address = "TODO",
                    telephone = telephone!!,
                    price = price!!,
                    department = "Montevideo",
                    neighbourhood = neighbourhood!!,
                    description = description,
                    features = features,
                    warranties = warranties,
                    pictureLinks = pictureLinks
                )
            }
            //.filter(::applyHouseFilters)
            .toSet()
    }

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
    fun calculateTotalPages(url: URL): Int {
        var lastPageFound = false
        var lastPageCandidate = 1

        while (!lastPageFound) {
            val doc = Jsoup.connect(url.appendPath("/pagina${lastPageCandidate}").toString())
                .timeout(30_000).get()!!

            val nextPageLink = doc.selectFirst("a[title='Página Siguiente'].next")
            if (nextPageLink == null) {
                lastPageCandidate = doc.select("a.numbers").last()!!.text().toInt() + 1
                lastPageFound = true
            } else {
                lastPageCandidate = doc.select("a.numbers").last()!!.text().toInt()
                lastPageFound = false
            }
        }

        return lastPageCandidate
    }

    private fun getPosts(url: URL, pages: Int): Set<Post> {
        return (1..pages)
            .map { page ->
                val doc = Jsoup.connect(url.appendPath("/pagina${page}").toString()).timeout(30_000).get()!!
                doc.select(MAIN_PAGE_POST_SELECTOR)
                    .mapNotNull { it.attr("href") }
                    .filter { !it.startsWith("https") }
                    .map {
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