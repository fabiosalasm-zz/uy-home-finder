package pe.fabiosalasm.uyhomefinder.repository

import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import org.jooq.DSLContext
import org.jooq.JSONB
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import pe.fabiosalasm.uyhomefinder.domain.House
import pe.fabiosalasm.uyhomefinder.extensions.newRecord
import pe.fabiosalasm.uyhomefinder.jooq.tables.references.HOUSE_CANDIDATE

@Repository
class HouseCandidateRepository(private val dslContext: DSLContext, private val objectMapper: ObjectMapper) {

    private companion object {
        val logger = KotlinLogging.logger {}
    }

    @Transactional
    fun cleanAndSave(alias: String, houses: Set<House>) {
        val deletedCount = dslContext.deleteFrom(HOUSE_CANDIDATE)
            .where(HOUSE_CANDIDATE.SOURCE_ID.like("$alias-%"))
            .execute()
        logger.info { "Cleaned $deletedCount old candidates" }

        val houseRecords = houses
            .map { house ->
                val record = HOUSE_CANDIDATE.newRecord(house)
                val featuresAsJson = objectMapper.writeValueAsString(house.features)
                record.features = JSONB.valueOf(featuresAsJson)
                record
            }
            .toSet()

        val results = dslContext.loadInto(HOUSE_CANDIDATE)
            .bulkAfter(2)
            .batchAfter(3)
            .commitAfter(3)
            .loadRecords(houseRecords)
            .fieldsCorresponding()
            .execute()

        logger.info { "Saved: ${results.stored()}" }
        logger.info { "Ignored: ${results.ignored()}" }
    }
}