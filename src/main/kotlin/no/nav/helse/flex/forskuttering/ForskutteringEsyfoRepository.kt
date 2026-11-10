package no.nav.helse.flex.forskuttering

import no.nav.helse.flex.forskuttering.domain.ForskutteringEsyfo
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ForskutteringEsyfoRepository : CrudRepository<ForskutteringEsyfo, String> {
    @Modifying
    @Query(
        """
        INSERT INTO forskuttering_esyfo (
            oppdatert, timestamp, narmeste_leder_id, orgnummer, bruker_fnr,
            aktiv_fom, aktiv_tom, arbeidsgiver_forskutterer
        ) VALUES (
            :#{T(java.sql.Timestamp).from(#forskuttering.oppdatert)},
            :#{T(java.sql.Timestamp).from(#forskuttering.timestamp)},
            :#{#forskuttering.narmesteLederId}, :#{#forskuttering.orgnummer}, :#{#forskuttering.brukerFnr},
            :#{#forskuttering.aktivFom}, :#{#forskuttering.aktivTom}, :#{#forskuttering.arbeidsgiverForskutterer}
        )
        ON CONFLICT (narmeste_leder_id) DO UPDATE SET
            oppdatert = EXCLUDED.oppdatert,
            timestamp = EXCLUDED.timestamp,
            orgnummer = EXCLUDED.orgnummer,
            bruker_fnr = EXCLUDED.bruker_fnr,
            aktiv_fom = EXCLUDED.aktiv_fom,
            aktiv_tom = EXCLUDED.aktiv_tom,
            arbeidsgiver_forskutterer = EXCLUDED.arbeidsgiver_forskutterer
        """,
    )
    fun lagre(forskuttering: ForskutteringEsyfo): Int

    @Modifying
    @Query("DELETE FROM forskuttering_esyfo WHERE narmeste_leder_id = :narmesteLederId")
    fun slett(narmesteLederId: UUID): Int

    fun findByNarmesteLederId(narmesteLederId: UUID): ForskutteringEsyfo?
}
