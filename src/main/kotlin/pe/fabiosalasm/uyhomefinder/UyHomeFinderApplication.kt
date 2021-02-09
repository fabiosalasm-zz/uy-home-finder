package pe.fabiosalasm.uyhomefinder

import it.skrape.core.htmlDocument
import it.skrape.extractIt
import it.skrape.selects.eachAttribute
import org.javamoney.moneta.Money
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import javax.money.Monetary

data class MainPage(
    var links: List<String> = emptyList()
)

data class HousePage(
    var title: String = "no-title",
    var price: Money = Money.of(0, Monetary.getCurrency("UYU")),
    var department: String = "Montevideo",
    var neighbourhood: String = "Montevideo",
    var description: String = "no-description",
    var features: List<String> = emptyList(),
    var warranties: List<String> = emptyList()
)

@SpringBootApplication
class UyHomeFinderApplication

fun main(args: Array<String>) {
    runApplication<UyHomeFinderApplication>(*args)

    val gallitoWebTarget = ScrapingTarget(
        host = "www.gallito.com.uy",
        port = 443,
        path = "/inmuebles/casas/alquiler"
    ).toScraper()

    val mainPageXPath = "div.img-responsive.aviso-ico-contiene img.img-seva.img-responsive"
    val eachAttribute = "eachAttribute"
    val mainPage = gallitoWebTarget.extractIt<MainPage> {
        htmlDocument {
            val x = mainPageXPath { findAll { this } }
            it.links = x.eachAttribute("alt")
        }
    }

    val rentalPageTitleXPath = "h1.titulo"
    val rentalPagePriceXPath = "span.precio"
    val rentalPageDptXPath = "li.breadcrumb-item"

    mainPage.links.forEach {
        val rentalPage = gallitoWebTarget.apply {
            request {
                url = it
            }
        }.extractIt<HousePage> {
            htmlDocument {
                it.title = rentalPageTitleXPath { findFirst { text } }
                it.price = rentalPagePriceXPath { findFirst { text.toMoney() } }
                it.department = rentalPageDptXPath { findSecondLast { text } }
                it.neighbourhood = rentalPageDptXPath { findLast { text } }
            }
        }
        println(rentalPage)
    }
}
