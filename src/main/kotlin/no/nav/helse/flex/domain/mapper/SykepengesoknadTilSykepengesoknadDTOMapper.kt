package no.nav.helse.flex.domain.mapper

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Mottaker
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.mapper.sporsmalprossesering.hentSoknadsPerioderMedFaktiskGrad
import no.nav.helse.flex.juridiskvurdering.JuridiskVurderingKafkaProducer
import no.nav.helse.flex.repository.RedusertVenteperiodeRepository
import no.nav.helse.flex.sykepengesoknad.kafka.ArbeidssituasjonDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsperiodeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import org.springframework.stereotype.Component

@Component
class SykepengesoknadTilSykepengesoknadDTOMapper(
    private val juridiskVurderingKafkaProducer: JuridiskVurderingKafkaProducer,
    private val redusertVenteperiodeRepository: RedusertVenteperiodeRepository
) {
    fun mapTilSykepengesoknadDTO(
        sykepengesoknad: Sykepengesoknad,
        mottaker: Mottaker? = null,
        erEttersending: Boolean = false,
        endeligVurdering: Boolean = true
    ): SykepengesoknadDTO {
        return when (sykepengesoknad.soknadstype) {
            Soknadstype.OPPHOLD_UTLAND -> konverterOppholdUtlandTilSoknadDTO(sykepengesoknad)

            Soknadstype.ARBEIDSTAKERE,
            Soknadstype.BEHANDLINGSDAGER,
            Soknadstype.ARBEIDSLEDIG,
            Soknadstype.SELVSTENDIGE_OG_FRILANSERE,
            Soknadstype.ANNET_ARBEIDSFORHOLD,
            Soknadstype.REISETILSKUDD -> konverterTilSykepengesoknadDTO(
                sykepengesoknad,
                mottaker,
                erEttersending,
                sykepengesoknad.hentSoknadsperioder(endeligVurdering)
            )

            // TODO generaliser mer!!
            Soknadstype.GRADERT_REISETILSKUDD -> {
                when (sykepengesoknad.arbeidssituasjon) {
                    Arbeidssituasjon.ARBEIDSTAKER,
                    Arbeidssituasjon.FRILANSER,
                    Arbeidssituasjon.NAERINGSDRIVENDE,
                    Arbeidssituasjon.ARBEIDSLEDIG,
                    Arbeidssituasjon.ANNET -> konverterTilSykepengesoknadDTO(
                        sykepengesoknad,
                        mottaker,
                        erEttersending,
                        sykepengesoknad.hentSoknadsperioder(endeligVurdering)
                    )

                    else -> throw IllegalStateException("Arbeidssituasjon ${sykepengesoknad.arbeidssituasjon} skal ikke kunne ha gradert reisetilskudd")
                }
            }
        }
            .merkSelvstendigOgFrilanserMedRedusertVenteperiode()
            .merkFeilinfo(sykepengesoknad.avbruttFeilinfo)
    }

    private fun Sykepengesoknad.hentSoknadsperioder(endeligVurdering: Boolean): List<SoknadsperiodeDTO> {
        val hentSoknadsPerioderMedFaktiskGrad = hentSoknadsPerioderMedFaktiskGrad(this)
        hentSoknadsPerioderMedFaktiskGrad.second?.let {
            if (endeligVurdering) {
                // TODO: Denne burde ligge et annet sted
                juridiskVurderingKafkaProducer.produserMelding(it)
            }
        }
        return hentSoknadsPerioderMedFaktiskGrad.first
    }

    private fun SykepengesoknadDTO.merkFeilinfo(avbruttFeilinfo: Boolean?): SykepengesoknadDTO {
        return if (avbruttFeilinfo == true) {
            this.copy(sendTilGosys = true, merknader = listOf("AVBRUTT_FEILINFO"))
        } else {
            this
        }
    }

    private fun SykepengesoknadDTO.merkSelvstendigOgFrilanserMedRedusertVenteperiode(): SykepengesoknadDTO {
        return if (arbeidssituasjon == ArbeidssituasjonDTO.FRILANSER || arbeidssituasjon == ArbeidssituasjonDTO.SELVSTENDIG_NARINGSDRIVENDE) {
            copy(harRedusertVenteperiode = redusertVenteperiodeRepository.existsBySykmeldingId(sykmeldingId!!))
        } else {
            this
        }
    }
}
