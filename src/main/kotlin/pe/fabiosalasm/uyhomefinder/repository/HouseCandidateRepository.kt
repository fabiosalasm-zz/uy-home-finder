package pe.fabiosalasm.uyhomefinder.repository

import com.fasterxml.jackson.databind.ObjectMapper
import org.jooq.DSLContext
import org.jooq.JSONB
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import pe.fabiosalasm.uyhomefinder.domain.House
import pe.fabiosalasm.uyhomefinder.extensions.newRecord
import pe.fabiosalasm.uyhomefinder.jooq.tables.references.HOUSE_CANDIDATE

@Repository
class HouseCandidateRepository(private val dslContext: DSLContext, private val objectMapper: ObjectMapper) {

    @Transactional
    fun save(house: House) {
        val record = HOUSE_CANDIDATE.newRecord(house)
        val featuresAsJson = objectMapper.writeValueAsString(house.features)
        record.features = JSONB.valueOf(featuresAsJson)

        dslContext.executeInsert(record)
    }

    fun batchSave(houses: Set<House>) {
        dslContext.loadInto(HOUSE_CANDIDATE)
            .bulkAfter(2)
            .batchAfter(3)
            .commitAfter(3)
            .loadRecords()
    }
}