package no.nav.helse.flex.kafka.producer

import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Mottaker
import no.nav.helse.flex.domain.Soknadstype.ARBEIDSTAKERE
import no.nav.helse.flex.domain.Soknadstype.GRADERT_REISETILSKUDD
import no.nav.helse.flex.domain.Soknadstype.SELVSTENDIGE_OG_FRILANSERE
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.mapper.SykepengesoknadTilSykepengesoknadDTOMapper
import no.nav.helse.flex.util.tilOsloLocalDateTime
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate

@Component
class SoknadProducer(
    private val kafkaProducer: AivenKafkaProducer,
    private val sykepengesoknadTilSykepengesoknadDTOMapper: SykepengesoknadTilSykepengesoknadDTOMapper,
) {
    @WithSpan
    fun soknadEvent(
        sykepengesoknad: Sykepengesoknad,
        mottaker: Mottaker? = null,
        erEttersending: Boolean = false,
        dodsdato: LocalDate? = null,
        opprinneligSendt: Instant? = null,
    ) {
        val sykepengesoknadDTO =
            sykepengesoknadTilSykepengesoknadDTOMapper
                .mapTilSykepengesoknadDTO(
                    sykepengesoknad,
                    mottaker,
                    erEttersending,
                    sykepengesoknad.skalLeggeJuridiskVurderingPaKafka(),
                ).copy(
                    dodsdato = dodsdato,
                    opprinneligSendt = opprinneligSendt?.tilOsloLocalDateTime(),
                )

        kafkaProducer.produserMelding(sykepengesoknadDTO)
    }

    private fun Sykepengesoknad.skalLeggeJuridiskVurderingPaKafka(): Boolean =
        when (soknadstype) {
            ARBEIDSTAKERE, SELVSTENDIGE_OG_FRILANSERE -> {
                true
            }

            GRADERT_REISETILSKUDD -> {
                when (arbeidssituasjon) {
                    Arbeidssituasjon.ARBEIDSTAKER, Arbeidssituasjon.NAERINGSDRIVENDE, Arbeidssituasjon.FRILANSER -> true
                    else -> false
                }
            }

            else -> false
        }
}
