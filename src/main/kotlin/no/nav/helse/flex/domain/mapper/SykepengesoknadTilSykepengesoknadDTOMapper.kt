package no.nav.helse.flex.domain.mapper

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Mottaker
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.mapper.sporsmalprossesering.hentSoknadsPerioderMedFaktiskGrad
import no.nav.helse.flex.juridiskvurdering.JuridiskVurderingKafkaProducer
import no.nav.helse.flex.repository.RedusertVenteperiodeRepository
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsperiodeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import org.springframework.stereotype.Component

@Component
class SykepengesoknadTilSykepengesoknadDTOMapper(
    private val juridiskVurderingKafkaProducer: JuridiskVurderingKafkaProducer,
    private val redusertVenteperiodeRepository: RedusertVenteperiodeRepository,
) {
    fun mapTilSykepengesoknadDTO(
        sykepengesoknad: Sykepengesoknad,
        mottaker: Mottaker? = null,
        erEttersending: Boolean = false,
        endeligVurdering: Boolean = true,
    ): SykepengesoknadDTO {

        fun SykepengesoknadDTO.merkFeilinfo(): SykepengesoknadDTO {
            return if (sykepengesoknad.avbruttFeilinfo == true) {
                this.copy(sendTilGosys = true, merknader = listOf("AVBRUTT_FEILINFO"))
            } else this
        }

        fun Sykepengesoknad.hentSoknadsperioder(): List<SoknadsperiodeDTO> {

            val hentSoknadsPerioderMedFaktiskGrad = hentSoknadsPerioderMedFaktiskGrad(this)
            hentSoknadsPerioderMedFaktiskGrad.second?.let {
                if (endeligVurdering) {
                    juridiskVurderingKafkaProducer.produserMelding(it)
                }
            }
            return hentSoknadsPerioderMedFaktiskGrad.first
        }

        return when (sykepengesoknad.soknadstype) {
            Soknadstype.SELVSTENDIGE_OG_FRILANSERE -> konverterSelvstendigOgFrilanserTilSoknadDTO(
                sykepengesoknad,
                sykepengesoknad.hentSoknadsperioder(),
                redusertVenteperiodeRepository.existsBySykmeldingId(sykepengesoknad.sykmeldingId!!),
            )
            Soknadstype.OPPHOLD_UTLAND -> konverterOppholdUtlandTilSoknadDTO(sykepengesoknad)
            Soknadstype.ARBEIDSLEDIG -> ArbeidsledigsoknadToSykepengesoknadDTO.konverterArbeidsledigTilSykepengesoknadDTO(
                sykepengesoknad
            )
            Soknadstype.BEHANDLINGSDAGER -> konverterTilSykepengesoknadBehandlingsdagerDTO(
                sykepengesoknad,
                mottaker,
                erEttersending
            )
            Soknadstype.ANNET_ARBEIDSFORHOLD -> konverterTilSykepengesoknadDTO(
                sykepengesoknad,
                mottaker,
                erEttersending
            )
            Soknadstype.REISETILSKUDD -> konverterTilSykepengesoknadDTO(sykepengesoknad, mottaker, erEttersending)
            Soknadstype.ARBEIDSTAKERE -> konverterArbeidstakersoknadTilSykepengesoknadDTO(
                sykepengesoknad,
                mottaker,
                erEttersending,
                sykepengesoknad.hentSoknadsperioder()
            )
            // TODO generaliser mer!!
            Soknadstype.GRADERT_REISETILSKUDD -> {
                when (sykepengesoknad.arbeidssituasjon) {
                    Arbeidssituasjon.ARBEIDSTAKER -> konverterArbeidstakersoknadTilSykepengesoknadDTO(
                        sykepengesoknad,
                        mottaker,
                        erEttersending,
                        sykepengesoknad.hentSoknadsperioder()
                    )
                    Arbeidssituasjon.FRILANSER, Arbeidssituasjon.NAERINGSDRIVENDE -> konverterSelvstendigOgFrilanserTilSoknadDTO(
                        sykepengesoknad,
                        sykepengesoknad.hentSoknadsperioder(),
                        redusertVenteperiodeRepository.existsBySykmeldingId(sykepengesoknad.sykmeldingId!!)
                    )
                    Arbeidssituasjon.ARBEIDSLEDIG,
                    Arbeidssituasjon.ANNET -> konverterTilSykepengesoknadDTO(
                        sykepengesoknad,
                        mottaker,
                        erEttersending
                    )
                    else -> throw IllegalStateException("Arbeidssituasjon ${sykepengesoknad.arbeidssituasjon} skal ikke kunne ha gradert reisetilskudd")
                }
            }
        }.merkFeilinfo()
    }
}
