package pe.fabiosalasm.uyhomefinder.domain

import org.javamoney.moneta.Money
import java.math.BigDecimal

enum class StoreMode {
    MANUAL, AUTOMATIC
}

data class Point(
    val latitude: Double,
    val longitude: Double
) {
    companion object {
        fun fromText(text: String): Point {
            require(
                """(-?\d{1,19}),(-?\d{1,19})""".toRegex().containsMatchIn(text)
            ) { "cannot find a point in text: $text" }
            val pointValues = text.split(",")
            return Point(pointValues[0].toDouble(), pointValues[1].toDouble())
        }
    }
}

data class House(
    val id: String,
    val source: String,
    val title: String,
    val link: String,
    val address: String,
    val department: String,
    val neighbourhood: String,
    val description: String,
    val telephone: String? = null,
    val price: Money,
    val pictureLinks: List<String> = emptyList(),
    val videoLink: String? = null,
    val features: Map<String, Any> = emptyMap(),
    val warranties: List<String> = emptyList(),
    val location: Point? = null,
    val storeMode: StoreMode
) {

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
                "sayago",
                "punta de rieles", "punta rieles" -> false
                else -> true
            }
            else -> true
        }
    }

    fun isValid(): Boolean {
        return this.id.isNotEmpty()
            && this.title.isNotEmpty()
            && this.link.isNotEmpty()
            && this.address.isNotEmpty()
            && this.price.numberStripped > BigDecimal.ZERO
            && this.pictureLinks.isNotEmpty()
            && this.department.isNotEmpty()
            && this.neighbourhood.isNotEmpty()
            && this.description.isNotEmpty()
            && this.features.isNotEmpty()
    }

    fun isNearByCapital(): Boolean {
        return when (this.department) {
            "Montevideo", "Canelones" -> true
            else -> false
        }
    }

    fun allowsPets(): Boolean {
        return !this.description.contains("no mascotas")
            && !this.description.contains("No mascotas")
    }

    //https://stackoverflow.com/questions/55748235/kotlin-check-for-words-in-string
    fun isAvailableForRental(): Boolean {
        val keywords = listOf(
            "alquilada", "alquilado",
            "ALQUILADA",
            "ALQUILADO"
        )

        val rx = Regex("\\b(?:${keywords.joinToString(separator = "|")})\\b")
        return !rx.containsMatchIn(this.title)
    }

    fun isForFamily(): Boolean {
        val keywords = listOf("masajista", "eróticas", "eróticos")
        val rx = Regex("\\b(?:${keywords.joinToString(separator = "|")})\\b")
        return !rx.containsMatchIn(this.title)
    }

    fun hasAvailablePics(): Boolean {
        return this.pictureLinks
            .find { it.contains("img_nodisponible.jpg") }
            .isNullOrEmpty()
    }
}