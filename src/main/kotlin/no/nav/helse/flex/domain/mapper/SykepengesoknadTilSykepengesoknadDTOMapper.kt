package no.nav.helse.flex.domain.mapper

import no.nav.helse.flex.domain.Mottaker
import no.nav.helse.flex.domain.Periode
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.mapper.sporsmalprossesering.hentSoknadsPerioderMedFaktiskGrad
import no.nav.helse.flex.frisktilarbeid.FriskTilArbeidRepository
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
import no.nav.helse.flex.util.serialisertTilString
import org.springframework.stereotype.Component

@Component
class SykepengesoknadTilSykepengesoknadDTOMapper(
    private val juridiskVurderingKafkaProducer: JuridiskVurderingKafkaProducer,
    private val redusertVenteperiodeRepository: RedusertVenteperiodeRepository,
    private val medlemskapVurderingRepository: MedlemskapVurderingRepository,
    private val friskTilArbeidRepository: FriskTilArbeidRepository,
) {
    fun mapTilSykepengesoknadDTO(
        sykepengesoknad: Sykepengesoknad,
        mottaker: Mottaker? = null,
        erEttersending: Boolean = false,
        endeligVurdering: Boolean = true,
    ): SykepengesoknadDTO =
        when (sykepengesoknad.soknadstype) {
            Soknadstype.OPPHOLD_UTLAND -> konverterOppholdUtlandTilSoknadDTO(sykepengesoknad)

            Soknadstype.ARBEIDSTAKERE,
            Soknadstype.BEHANDLINGSDAGER,
            Soknadstype.ARBEIDSLEDIG,
            Soknadstype.SELVSTENDIGE_OG_FRILANSERE,
            Soknadstype.ANNET_ARBEIDSFORHOLD,
            Soknadstype.GRADERT_REISETILSKUDD,
            Soknadstype.REISETILSKUDD,
            Soknadstype.FRISKMELDT_TIL_ARBEIDSFORMIDLING,
            ->
                konverterTilSykepengesoknadDTO(
                    sykepengesoknad,
                    mottaker,
                    erEttersending,
                    sykepengesoknad.hentSoknadsperioder(endeligVurdering),
                )
        }.merkSelvstendigOgFrilanserMedRedusertVenteperiode()
            .merkMedMedlemskapStatus()
            .fyllMedDataFraFriskTilArbeidTabell()

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

    private fun SykepengesoknadDTO.merkSelvstendigOgFrilanserMedRedusertVenteperiode(): SykepengesoknadDTO =
        if (
            arbeidssituasjon == ArbeidssituasjonDTO.FRILANSER ||
            arbeidssituasjon == ArbeidssituasjonDTO.SELVSTENDIG_NARINGSDRIVENDE
        ) {
            copy(harRedusertVenteperiode = redusertVenteperiodeRepository.existsBySykmeldingId(sykmeldingId!!))
        } else {
            this
        }

    private fun SykepengesoknadDTO.fyllMedDataFraFriskTilArbeidTabell(): SykepengesoknadDTO {
        if (type != SoknadstypeDTO.FRISKMELDT_TIL_ARBEIDSFORMIDLING) {
            return this
        }

        friskTilArbeidRepository.findById(friskTilArbeidVedtakId!!).let {
            val friskTilArbeidDbRecord = it.get()
            return copy(
                friskTilArbeidVedtakPeriode =
                    Periode(
                        friskTilArbeidDbRecord.fom,
                        friskTilArbeidDbRecord.tom,
                    ).serialisertTilString(),
                ignorerArbeidssokerregister = friskTilArbeidDbRecord.ignorerArbeidssokerregister,
            )
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
                sporsmal
                    ?.firstOrNull {
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
