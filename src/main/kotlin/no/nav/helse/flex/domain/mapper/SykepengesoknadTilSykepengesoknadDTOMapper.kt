package no.nav.helse.flex.domain.mapper

import no.nav.helse.flex.domain.Mottaker
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.mapper.sporsmalprossesering.hentSoknadsPerioderMedFaktiskGrad
import no.nav.helse.flex.juridiskvurdering.JuridiskVurderingKafkaProducer
import no.nav.helse.flex.medlemskap.MedlemskapVurderingRepository
import no.nav.helse.flex.repository.RedusertVenteperiodeRepository
import no.nav.helse.flex.soknadsopprettelse.MEDLEMSKAP_OPPHOLDSTILLATELSE
import no.nav.helse.flex.soknadsopprettelse.MEDLEMSKAP_OPPHOLD_UTENFOR_EOS
import no.nav.helse.flex.soknadsopprettelse.MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE
import no.nav.helse.flex.soknadsopprettelse.MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE
import no.nav.helse.flex.sykepengesoknad.kafka.ArbeidssituasjonDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsperiodeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import org.springframework.stereotype.Component

@Component
class SykepengesoknadTilSykepengesoknadDTOMapper(
    private val juridiskVurderingKafkaProducer: JuridiskVurderingKafkaProducer,
    private val redusertVenteperiodeRepository: RedusertVenteperiodeRepository,
    private val medlemskapVurderingRepository: MedlemskapVurderingRepository,
) {
    fun mapTilSykepengesoknadDTO(
        sykepengesoknad: Sykepengesoknad,
        mottaker: Mottaker? = null,
        erEttersending: Boolean = false,
        endeligVurdering: Boolean = true,
    ): SykepengesoknadDTO {
        return when (sykepengesoknad.soknadstype) {
            Soknadstype.OPPHOLD_UTLAND -> konverterOppholdUtlandTilSoknadDTO(sykepengesoknad)

            Soknadstype.ARBEIDSTAKERE,
            Soknadstype.BEHANDLINGSDAGER,
            Soknadstype.ARBEIDSLEDIG,
            Soknadstype.SELVSTENDIGE_OG_FRILANSERE,
            Soknadstype.ANNET_ARBEIDSFORHOLD,
            Soknadstype.GRADERT_REISETILSKUDD,
            Soknadstype.REISETILSKUDD,
            ->
                konverterTilSykepengesoknadDTO(
                    sykepengesoknad,
                    mottaker,
                    erEttersending,
                    sykepengesoknad.hentSoknadsperioder(endeligVurdering),
                )
        }
            .merkSelvstendigOgFrilanserMedRedusertVenteperiode()
            .merkFeilinfo(sykepengesoknad.avbruttFeilinfo)
            .merkMedMedlemskapStatus()
    }

    private fun Sykepengesoknad.hentSoknadsperioder(endeligVurdering: Boolean): List<SoknadsperiodeDTO> {
        if (soknadstype in listOf(Soknadstype.BEHANDLINGSDAGER, Soknadstype.ARBEIDSLEDIG)) {
            return soknadPerioder!!.map {
                SoknadsperiodeDTO(
                    fom = it.fom,
                    tom = it.tom,
                    sykmeldingsgrad = it.grad,
                    sykmeldingstype = it.sykmeldingstype?.tilSykmeldingstypeDTO(),
                )
            }
        } else {
            val hentSoknadsPerioderMedFaktiskGrad = hentSoknadsPerioderMedFaktiskGrad(this)
            hentSoknadsPerioderMedFaktiskGrad.second?.let {
                if (endeligVurdering) {
                    // TODO: Denne burde ligge et annet sted
                    juridiskVurderingKafkaProducer.produserMelding(it)
                }
            }
            return hentSoknadsPerioderMedFaktiskGrad.first
        }
    }

    private fun SykepengesoknadDTO.merkFeilinfo(avbruttFeilinfo: Boolean?): SykepengesoknadDTO {
        return if (avbruttFeilinfo == true) {
            this.copy(sendTilGosys = true, merknader = listOf("AVBRUTT_FEILINFO"))
        } else {
            this
        }
    }

    private fun SykepengesoknadDTO.merkSelvstendigOgFrilanserMedRedusertVenteperiode(): SykepengesoknadDTO {
        return if (
            arbeidssituasjon == ArbeidssituasjonDTO.FRILANSER ||
            arbeidssituasjon == ArbeidssituasjonDTO.SELVSTENDIG_NARINGSDRIVENDE
        ) {
            copy(harRedusertVenteperiode = redusertVenteperiodeRepository.existsBySykmeldingId(sykmeldingId!!))
        } else {
            this
        }
    }

    private fun SykepengesoknadDTO.merkMedMedlemskapStatus(): SykepengesoknadDTO {
        if (!listOf(
                SoknadstypeDTO.ARBEIDSTAKERE,
                SoknadstypeDTO.GRADERT_REISETILSKUDD,
            ).contains(type)
        ) {
            return this
        }

        val medlemskapVurdering =
            medlemskapVurderingRepository.findBySykepengesoknadIdAndFomAndTom(id, fom!!, tom!!)

        when (medlemskapVurdering?.svartype) {
            "JA", "NEI" -> return copy(medlemskapVurdering = medlemskapVurdering.svartype)
            "UAVKLART" -> {
                sporsmal?.firstOrNull {
                    it.tag in
                        listOf(
                            MEDLEMSKAP_OPPHOLDSTILLATELSE,
                            MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE,
                            MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE,
                            MEDLEMSKAP_OPPHOLD_UTENFOR_EOS,
                        )
                }?.let {
                    return copy(medlemskapVurdering = medlemskapVurdering.svartype)
                }
            }
        }
        return this
    }
}
