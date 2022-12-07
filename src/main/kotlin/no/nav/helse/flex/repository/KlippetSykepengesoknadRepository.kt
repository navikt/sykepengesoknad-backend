package no.nav.helse.flex.repository

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface KlippetSykepengesoknadRepository : CrudRepository<KlippetSykepengesoknadDbRecord, String>

@Table("klippet_sykepengesoknad")
data class KlippetSykepengesoknadDbRecord(
    @Id
    val id: String? = null,
    val sykepengesoknadUuid: String,
    val sykmeldingUuid: String,
    val klippVariant: KlippVariant,
    val periodeFor: String,
    val periodeEtter: String?,
    val timestamp: Instant,
)

enum class KlippVariant {
    SOKNAD_STARTER_FOR_SLUTTER_INNI,
    SOKNAD_STARTER_INNI_SLUTTER_ETTER,
    SOKNAD_STARTER_FOR_SLUTTER_ETTER,
    SOKNAD_STARTER_INNI_SLUTTER_INNI,
    SYKMELDING_STARTER_FOR_SLUTTER_INNI,
}
