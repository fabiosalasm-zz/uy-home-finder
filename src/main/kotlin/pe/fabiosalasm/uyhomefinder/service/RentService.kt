package pe.fabiosalasm.uyhomefinder.service

import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import pe.fabiosalasm.uyhomefinder.repository.HouseCandidateRepository
import pe.fabiosalasm.uyhomefinder.skraper.Skraper
import java.lang.IllegalArgumentException

@Service
class RentService(
    private val skrapers: Set<Skraper>,
    private val houseCandidateRepository: HouseCandidateRepository
) {

    private companion object {
        val logger = KotlinLogging.logger {}
    }

    fun importHousesFromAllSkrapers() {
        skrapers.forEach { skraper ->
            val houses = skraper.fetchHousesForRental()
            if (houses.isNotEmpty()) {
                logger.info { "Got ${houses.size} house candidates to save" }
                houseCandidateRepository.cleanAndSave(skraper.name, houses)
            } else {
                logger.warn { "Got ZERO houses. Check websource or filters in case qualified candidates are discarded" }
            }
        }
    }

    @Transactional
    fun importHousesFrom(skraperName: String) {
        val skraper = skrapers.find { skraperName == it.name }
            ?: throw IllegalArgumentException("$skraperName not exists")

        val houses = skraper.fetchHousesForRental()
        if (houses.isNotEmpty()) {
            logger.info { "Got ${houses.size} house candidates to save" }
            houseCandidateRepository.cleanAndSave(skraperName, houses)
        } else {
            logger.warn { "Got ZERO houses. Check websource or filters in case qualified candidates are discarded" }
        }
    }
}