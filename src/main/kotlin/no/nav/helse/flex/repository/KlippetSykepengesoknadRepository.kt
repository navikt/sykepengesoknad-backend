package no.nav.helse.flex.repository

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface KlippetSykepengesoknadRepository : CrudRepository<KlippetSykepengesoknadDbRecord, String> {
    fun findBySykmeldingUuid(sykmeldingUuid: String): KlippetSykepengesoknadDbRecord?
}

@Table("klippet_sykepengesoknad")
data class KlippetSykepengesoknadDbRecord(
    @Id
    val id: String? = null,
    val sykepengesoknadUuid: String,
    val sykmeldingUuid: String,
    val klippVariant: KlippVariant,
    val periodeFor: String,
    val periodeEtter: String?,
    val timestamp: Instant
)

/**
 ### Varianten av klipp som sier om det er en søknad eller sykmelding som ble klippet og hvilke del som var overlappende.
 ### [SOKNAD_STARTER_INNI_SLUTTER_ETTER] soknaden er klippet og sykmeldingen starter inni og slutter etter
 ### [SYKMELDING_STARTER_FOR_SLUTTER_INNI] sykmeldingen er klippet og søknaden starter før og slutter inni
 */
enum class KlippVariant {
    SOKNAD_STARTER_FOR_SLUTTER_INNI,
    SOKNAD_STARTER_INNI_SLUTTER_ETTER,
    SOKNAD_STARTER_FOR_SLUTTER_ETTER,
    SOKNAD_STARTER_INNI_SLUTTER_INNI,
    SYKMELDING_STARTER_FOR_SLUTTER_INNI,
    SYKMELDING_STARTER_INNI_SLUTTER_ETTER,
    SYKMELDING_STARTER_INNI_SLUTTER_INNI,
    SYKMELDING_STARTER_FOR_SLUTTER_ETTER
}
