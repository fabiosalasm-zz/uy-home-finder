package pe.fabiosalasm.uyhomefinder

import org.javamoney.moneta.Money
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import javax.money.Monetary

data class Post(
    var link: String = "no-link",
    var hasGPS: Boolean = false,
    var hasVideo: Boolean = false
)

data class House(
    var id: Long = 0,
    var title: String = "no-title",
    var link: String = "no-link",
    var address: String = "no-address",
    var telephone: String = "00000000",
    var price: Money = Money.of(0, Monetary.getCurrency("UYU")),
    var pictureLinks: List<String> = emptyList(),
    var geoReference: String? = null,
    var videoLink: String? = null,
    var department: String = "Montevideo",
    var neighbourhood: String = "Montevideo",
    var description: String = "no-description",
    var features: Map<String, Any> = emptyMap(),
    var warranties: List<String> = emptyList()
)

@SpringBootApplication
class UyHomeFinderApplication

fun main(args: Array<String>) {
    runApplication<UyHomeFinderApplication>(*args)
}
