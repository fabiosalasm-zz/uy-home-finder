package pe.fabiosalasm.uyhomefinder.service

import it.skrape.core.htmlDocument
import it.skrape.extract
import org.springframework.stereotype.Service
import org.springframework.web.util.UriComponentsBuilder
import pe.fabiosalasm.uyhomefinder.House
import pe.fabiosalasm.uyhomefinder.Post
import pe.fabiosalasm.uyhomefinder.extensions.cloneAndReplace
import pe.fabiosalasm.uyhomefinder.extensions.toScraperRunner
import java.net.URL

@Service
class InfoCasasWebPageService {

    fun getHousesForRent(): Set<House> {
        //TODO: Put these constants in a database
        val department = "montevideo"
        val minSquareMeter = 70
        val maxPrice = 30_000

        //TODO: the URL involves a department pre-filter, so the whole operation should
        // be done per department (Montevideo and Canelones only considered for now)
        val urlTemplate = """
            https://www.infocasas.com.uy/alquiler/casas/{department}/hasta-{maxPrice}/pesos/m2-desde-{minSquareMeter}/edificados
        """.trimIndent()

        val urlVariables = mapOf(
            "department" to department,
            "maxPrice" to maxPrice,
            "minSquareMeter" to minSquareMeter
        )

        val pages = calculateTotalPages(urlTemplate, urlVariables)

        return emptySet()
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
    fun calculateTotalPages(urlTemplate: String, urlVariables: Map<String, Any>): Int {
        var lastPageFound = false
        var lastPageCandidate = "pagina1"

        while (!lastPageFound) {
            val url = UriComponentsBuilder.fromUriString("$urlTemplate/$lastPageCandidate").build()
                .expand(urlVariables)
                .toUri().toURL()

            lastPageFound = url.toScraperRunner().extract {
                htmlDocument {
                    relaxed = true
                    // if there is no next page, it means we have reached the las group of existing pages
                    // we will go to the last one of them and mark it as the last page
                    val nextPageLink = "a[title='Página Siguiente'].next" { findAll { this } }.firstOrNull()
                    if (nextPageLink == null) {
                        val secondToLast = ("a.numbers" { findLast { text } }).toInt()
                        lastPageCandidate = "pagina" + secondToLast.plus(1)
                        true
                    } else {
                        lastPageCandidate = "pagina" + ("a.numbers" { findLast { text } })
                        false
                    }
                }
            }
        }

        return lastPageCandidate.removePrefix("pagina").toInt()
    }

    private fun getPosts(url: URL, pages: Int) {
        val posts = mutableSetOf<Post>()
        for (page in 1..pages) {
            url.cloneAndReplace()
        }
        TODO()
    }
}