@file:UseSerializers(UrlAsStringSerializer::class, MoneyAsObjectSerializer::class)

package pe.fabiosalasm.uyhomefinder.dto.json

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonObject
import org.javamoney.moneta.Money
import java.net.URL

@Serializable
data class HouseDto(
    val id: Long,
    val source: String,
    val title: String,
    val link: URL,
    val address: String,
    val department: String,
    val neighbourhood: String,
    val description: String,
    val telephone: String,
    val price: Money,
    val pictureLinks: Set<URL>,
    val videoLink: URL? = null,
    val features: JsonObject? = null,
    val warranties: List<String>?,
    val location: PointDto? = null
) {
    init {
        require(pictureLinks.isNotEmpty()) { "'pictureLinks' attribute should not be empty" }
    }
}

@Serializable
data class PointDto(
    val latitude: Double,
    val longitude: Double
)