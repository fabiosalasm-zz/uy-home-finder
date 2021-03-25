package pe.fabiosalasm.uyhomefinder.skraper

import pe.fabiosalasm.uyhomefinder.domain.House
import java.net.URL

interface Skraper {
    val name: String get() = this::class.java.simpleName.removeSuffix("Skraper").toLowerCase()
    val urlTemplate: URL
    val urlParams: Map<String, String>?
    val client: SkraperClient

    suspend fun fetchHousesForRental(): Set<House>
}