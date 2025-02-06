package no.nav.helse.flex.repository

import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface SykepengesoknadRepository : CrudRepository<SykepengesoknadDbRecord, String> {
    fun findBySykepengesoknadUuid(sykepengesoknadUuid: String): SykepengesoknadDbRecord?

    fun findByFriskTilArbeidVedtakId(vedtakId: String): List<SykepengesoknadDbRecord>

    fun findBySykepengesoknadUuidIn(sykepengesoknadUuid: List<String>): List<SykepengesoknadDbRecord>

    fun findByFnrIn(fnrs: List<String>): List<SykepengesoknadDbRecord>

    fun findBySykmeldingUuid(sykmeldingUuid: String): List<SykepengesoknadDbRecord>

    @Modifying
    @Query(
        """
        UPDATE sykepengesoknad
        SET aktivert_julesoknad_kandidat = true
        WHERE id = :id
          AND status = 'FREMTIDIG'
        """,
    )
    fun settErAktivertJulesoknadKandidat(id: String): Boolean

    @Query(
        """
        SELECT COALESCE(aktivert_julesoknad_kandidat, FALSE)
        FROM sykepengesoknad
        WHERE sykepengesoknad_uuid = :id
        """,
    )
    fun erAktivertJulesoknadKandidat(id: String): Boolean

    @Query(
        """
        SELECT sykepengesoknad_uuid
        FROM sykepengesoknad
        WHERE fnr IN (:identer)
        AND (status = 'NY' OR status = 'UTKAST_TIL_KORRIGERING')
        AND fom IS NOT NULL AND fom < :fom
        ORDER BY fom ASC
        LIMIT 1
        """,
    )
    fun findEldsteSoknaden(
        identer: List<String>,
        fom: LocalDate?,
    ): String?

    @Query(
        """
        SELECT *
        FROM sykepengesoknad
        WHERE status = 'FREMTIDIG'
        AND TOM < :now
        """,
    )
    fun finnSoknaderSomSkalAktiveres(now: LocalDate): List<SykepengesoknadDbRecord>
}
