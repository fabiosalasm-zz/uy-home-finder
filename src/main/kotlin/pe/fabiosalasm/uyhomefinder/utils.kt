package pe.fabiosalasm.uyhomefinder

import org.javamoney.moneta.Money
import pe.fabiosalasm.uyhomefinder.extensions.toMoney
import javax.money.Monetary

fun catalogFeatures(rawFeatures: List<String>): Map<String, Any> {
    val result = mutableMapOf<String, Any>()
    val extras = mutableListOf<String>()

    rawFeatures.forEach { rawFeature ->
        when {
            """Padrón: \w+""".toRegex().containsMatchIn(rawFeature) -> {
                result["register"] = rawFeature.removePrefix("Padrón: ")
            }
            """Estado: \w+""".toRegex().containsMatchIn(rawFeature) -> {
                result["buildingState"] = rawFeature.removePrefix("Estado: ")
            }
            """(\d) (Baño[s]?)""".toRegex().containsMatchIn(rawFeature) -> {
                result["numberBathrooms"] = """(\d) (Baño[s]?)""".toRegex().find(rawFeature)!!
                    .groupValues[1].toInt()
            }
            rawFeature == "Cocina" -> {
                result["hasKitchen"] = true
                result["kitchenSize"] = "NORMAL"
            }

            rawFeature == "Kitchenette" -> {
                result["hasKitchen"] = true
                result["kitchenSize"] = "SMALL"
            }

            """Techo: \w+""".toRegex().containsMatchIn(rawFeature) -> {
                result["roofType"] = rawFeature.removePrefix("Techo: ")
            }
            """(Sup. construida:) (\d{1,5})m²""".toRegex().containsMatchIn(rawFeature) -> {
                result["sqMeters"] = """(Sup. construida:) (\d{1,5})m²""".toRegex().find(rawFeature)!!
                    .groupValues[2].toInt()

                //TODO: the regex filter is not working as expected?
            }
            """Gastos Comunes: \$(U\d+)""".toRegex().containsMatchIn(rawFeature) -> {
                result["commonExpenses"] = """Gastos Comunes: \$(U\d+)""".toRegex().find(rawFeature)!!
                    .groupValues[1].replace("U", "\$U ") // polishing string to change to it money
                    .toMoney()
            }
            """(Año:) (\d+)""".toRegex().containsMatchIn(rawFeature) -> {
                result["constructionYear"] = """(Año:) (\d+)""".toRegex().find(rawFeature)!!
                    .groupValues[2].toInt()
            }
            """(Cantidad de plantas:) (\d+)""".toRegex().containsMatchIn(rawFeature) -> {
                result["numberFloors"] = """(Cantidad de plantas:) (\d+)""".toRegex().find(rawFeature)!!
                    .groupValues[2].toInt()
            }
            else -> {
                extras.add(rawFeature)
            }
        }
    }

    if (extras.isNotEmpty()) {
        result["extras"] = extras
    }

    return result
}

fun applyHouseFilters(house: House): Boolean {
    var isValid = true

    if (house.price.currency == Monetary.getCurrency("UYU")) {
        isValid = isValid && priceLessThan(Money.of(30_000.00, "UYU"), house)
    } else if (house.price.currency == Monetary.getCurrency("USD")) {
        isValid = isValid && priceLessThan(Money.of(1_000.00, "USD"), house)
    }

    return isValid
        && squareMetersMoreThan(70, house)
        && onlySafeNeighbourhoods(house)
        && onlyAvailable(house)
        && onlyForFamily(house)
        && onlyWithPictures(house)
}

//TODO: maxPrice should come a database? These properties are the same, regardless the web source
fun priceLessThan(maxPrice: Money, house: House): Boolean {
    require(maxPrice.currency == house.price.currency)
    { "House price currency different than upperPrice currency" }

    return house.price.isLessThan(maxPrice)
}

//TODO: minSquareMeters should come a database? These properties are the same, regardless the web source
fun squareMetersMoreThan(minSquareMeters: Int, house: House): Boolean {
    return if (house.features.containsKey("sqMeters")) {
        val houseSquareMeters = house.features["sqMeters"] as Int
        houseSquareMeters > minSquareMeters
    } else true
}

//TODO: get info from a database? These properties are the same, regardless the web source
fun onlySafeNeighbourhoods(house: House): Boolean {
    return when (house.department) {
        "Montevideo" -> when (house.neighbourhood.toLowerCase()) {
            "casavalle", "nuevo paris", "cerro", "union", "colon", "peñarol",
            "paso de la arena", "belvedere", "la paloma", "punta de rieles" -> false
            else -> true
        }
        "Maldonado", "Rocha", "San Jose", "Durazno" -> false
        else -> true
    }
}

//https://stackoverflow.com/questions/55748235/kotlin-check-for-words-in-string
fun onlyAvailable(house: House): Boolean {
    val keywords = listOf("alquilada", "alquilado")
    val rx = Regex("\\b(?:${keywords.joinToString(separator = "|")})\\b")
    return !rx.containsMatchIn(house.title)
}

fun onlyForFamily(house: House): Boolean {
    val keywords = listOf("masajista", "eróticas", "eróticos")
    val rx = Regex("\\b(?:${keywords.joinToString(separator = "|")})\\b")
    return !rx.containsMatchIn(house.title)
}

fun onlyWithPictures(house: House): Boolean {
    return house.pictureLinks
        .find { it.contains("img_nodisponible.jpg") }
        .isNullOrEmpty()
}