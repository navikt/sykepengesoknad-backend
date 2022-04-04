package no.nav.syfo.domain.mapper

import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsperiodeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.syfo.domain.Arbeidssituasjon
import no.nav.syfo.domain.Mottaker
import no.nav.syfo.domain.Soknadstype
import no.nav.syfo.domain.Sykepengesoknad
import no.nav.syfo.domain.mapper.sporsmalprossesering.hentSoknadsPerioderMedFaktiskGrad
import no.nav.syfo.juridiskvurdering.JuridiskVurderingKafkaProducer
import no.nav.syfo.repository.RedusertVenteperiodeRepository
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
