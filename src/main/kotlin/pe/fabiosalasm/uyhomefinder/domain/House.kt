package pe.fabiosalasm.uyhomefinder.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import org.javamoney.moneta.Money
import java.math.BigDecimal
import javax.money.Monetary

enum class StoreMode {
    MANUAL, AUTOMATIC
}

data class House(
    var sourceId: String = "",
    var title: String = "",
    var link: String = "",
    var address: String = "",
    var telephone: String = "",
    var price: Money = Money.zero(Monetary.getCurrency("UYU")),
    var pictureLinks: List<String> = emptyList(),
    var geoReference: String? = null,
    var videoLink: String? = null,
    var department: String = "",
    var neighbourhood: String = "",
    var description: String = "",
    var features: Map<String, Any> = emptyMap(),
    var warranties: List<String> = emptyList(),
    var storeMode: StoreMode
) {

    @JsonIgnore
    fun isLocatedInSafeNeighbourhood(): Boolean {
        return when (this.department) {
            "Montevideo" -> when (this.neighbourhood.toLowerCase()) {
                "casavalle",
                "nuevo paris", "nuevo parís",
                "cerro",
                "union", "unión",
                "colon", "colón",
                "peñarol",
                "paso de la arena",
                "belvedere",
                "la paloma",
                "punta de rieles", "punta rieles" -> false
                else -> true
            }
            else -> true
        }
    }

    @JsonIgnore
    fun isValid(): Boolean {
        return this.sourceId.isNotEmpty()
            && this.title.isNotEmpty()
            && this.link.isNotEmpty()
            && this.address.isNotEmpty()
            && this.telephone.isNotEmpty()
            && this.price.numberStripped != BigDecimal.ZERO
            && this.pictureLinks.isNotEmpty()
            && this.department.isNotEmpty()
            && this.neighbourhood.isNotEmpty()
            && this.description.isNotEmpty()
            && this.features.isNotEmpty()
            && this.warranties.isNotEmpty()
    }

    @JsonIgnore
    fun isNearByCapital(): Boolean {
        return when (this.department) {
            "Montevideo", "Canelones" -> true
            else -> false
        }
    }

    //https://stackoverflow.com/questions/55748235/kotlin-check-for-words-in-string
    @JsonIgnore
    fun isAvailableForRental(): Boolean {
        val keywords = listOf(
            "alquilada", "alquilado",
            " ALQUILADA",
            "ALQUILADO"
        )

        val rx = Regex("\\b(?:${keywords.joinToString(separator = "|")})\\b")
        return !rx.containsMatchIn(this.title)
    }

    @JsonIgnore
    fun isForFamily(): Boolean {
        val keywords = listOf("masajista", "eróticas", "eróticos")
        val rx = Regex("\\b(?:${keywords.joinToString(separator = "|")})\\b")
        return !rx.containsMatchIn(this.title)
    }

    @JsonIgnore
    fun hasAvailablePics(): Boolean {
        return this.pictureLinks
            .find { it.contains("img_nodisponible.jpg") }
            .isNullOrEmpty()
    }
}