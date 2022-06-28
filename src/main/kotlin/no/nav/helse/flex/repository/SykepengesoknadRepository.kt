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
        WHERE fnr IN (:identer)
        AND (status = 'NY' OR status = 'UTKAST_TIL_KORRIGERING')
        AND fom IS NOT NULL AND fom < :fom
        ORDER BY fom ASC
        LIMIT 1
        """
    )
    fun findEldsteSoknaden(identer: List<String>, fom: LocalDate?): String?

    @Query(
        """
        WITH nyesteOrg AS (
            SELECT arbeidsgiver_orgnummer, max(tom) nyeste_tom
            FROM sykepengesoknad
            WHERE arbeidsgiver_orgnummer IS NOT NULL
            AND arbeidsgiver_navn IS NOT NULL
            GROUP BY arbeidsgiver_orgnummer
        )
        SELECT sykepengesoknad.arbeidsgiver_orgnummer, sykepengesoknad.arbeidsgiver_navn, sykepengesoknad.sykepengesoknad_uuid
        FROM nyesteOrg
        INNER JOIN sykepengesoknad
        ON sykepengesoknad.arbeidsgiver_orgnummer = nyesteOrg.arbeidsgiver_orgnummer
        AND sykepengesoknad.tom = nyesteOrg.nyeste_tom
        """
    )
    fun findLatestOrgnavn(): List<Organisasjon>
}

data class Organisasjon(
    val arbeidsgiver_orgnummer: String,
    val arbeidsgiver_navn: String,
    val sykepengesoknad_uuid: String,
)
