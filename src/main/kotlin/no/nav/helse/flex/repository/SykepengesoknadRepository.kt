package no.nav.helse.flex.repository

import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface SykepengesoknadRepository : CrudRepository<SykepengesoknadDbRecord, String> {

    fun findBySykepengesoknadUuid(sykepengesoknadUuid: String): SykepengesoknadDbRecord?
    fun findBySykepengesoknadUuidIn(sykepengesoknadUuid: List<String>): List<SykepengesoknadDbRecord>

    @Query(
        """
        SELECT sykepengesoknad_uuid
        FROM sykepengesoknad
        WHERE fnr = :fnr
        AND (status = 'NY' OR status = 'UTKAST_TIL_KORRIGERING')
        ORDER BY fom ASC
        LIMIT 1
        """
    )
    fun findEldsteSoknaden(fnr: String, fom: LocalDate?): String
}
