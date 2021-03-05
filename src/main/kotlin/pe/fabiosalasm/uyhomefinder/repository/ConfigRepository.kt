package pe.fabiosalasm.uyhomefinder.repository

import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import pe.fabiosalasm.uyhomefinder.jooq.tables.records.WebConfigRecord
import pe.fabiosalasm.uyhomefinder.jooq.tables.references.WEB_CONFIG

@Repository
class ConfigRepository(private val dslContext: DSLContext) {

    @Transactional
    fun getOneByAlias(alias: String): WebConfigRecord? {
        return dslContext.selectFrom(WEB_CONFIG)
            .where(WEB_CONFIG.ALIAS.eq(alias))
            .fetchOne()
            ?.into(WebConfigRecord::class.java)
    }
}