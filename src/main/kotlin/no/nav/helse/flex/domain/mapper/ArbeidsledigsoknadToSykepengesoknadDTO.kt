package no.nav.helse.flex.domain.mapper

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.mapper.sporsmalprossesering.hentArbeidUtenforNorge
import no.nav.helse.flex.domain.mapper.sporsmalprossesering.hentPermitteringer
import no.nav.helse.flex.soknadsopprettelse.ARBEIDSLEDIG_UTLAND
import no.nav.helse.flex.soknadsopprettelse.FULLTIDSSTUDIUM
import no.nav.helse.flex.soknadsopprettelse.HVILKE_ANDRE_INNTEKTSKILDER
import no.nav.helse.flex.soknadsopprettelse.INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD
import no.nav.helse.flex.soknadsopprettelse.INNTEKTSKILDE_ANNET
import no.nav.helse.flex.soknadsopprettelse.INNTEKTSKILDE_FOSTERHJEM
import no.nav.helse.flex.soknadsopprettelse.INNTEKTSKILDE_FRILANSER
import no.nav.helse.flex.soknadsopprettelse.INNTEKTSKILDE_JORDBRUKER
import no.nav.helse.flex.soknadsopprettelse.INNTEKTSKILDE_OMSORGSLONN
import no.nav.helse.flex.soknadsopprettelse.INNTEKTSKILDE_SELVSTENDIG
import no.nav.helse.flex.soknadsopprettelse.INNTEKTSKILDE_SELVSTENDIG_DAGMAMMA
import no.nav.helse.flex.soknadsopprettelse.UTDANNING
import no.nav.helse.flex.soknadsopprettelse.UTDANNING_START
import no.nav.helse.flex.soknadsopprettelse.UTLANDSOPPHOLD_SOKT_SYKEPENGER
import no.nav.helse.flex.soknadsopprettelse.UTLAND_NAR
import no.nav.helse.flex.sykepengesoknad.kafka.ArbeidssituasjonDTO
import no.nav.helse.flex.sykepengesoknad.kafka.FravarDTO
import no.nav.helse.flex.sykepengesoknad.kafka.FravarstypeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.InntektskildeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.InntektskildetypeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsperiodeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.helse.flex.util.tilOsloLocalDateTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE
import java.util.*
import java.util.Collections.emptyList

object ArbeidsledigsoknadToSykepengesoknadDTO {

    fun konverterArbeidsledigTilSykepengesoknadDTO(sykepengesoknad: Sykepengesoknad): SykepengesoknadDTO {
        return SykepengesoknadDTO(
            type = SoknadstypeDTO.ARBEIDSLEDIG,
            arbeidssituasjon = ArbeidssituasjonDTO.ARBEIDSLEDIG,
            id = sykepengesoknad.id,
            status = sykepengesoknad.status.tilSoknadstatusDTO(),
            fnr = sykepengesoknad.fnr,
            sykmeldingId = sykepengesoknad.sykmeldingId,
            korrigerer = sykepengesoknad.korrigerer,
            korrigertAv = sykepengesoknad.korrigertAv,
            soktUtenlandsopphold = harSoktSykepengerUnderUtlandsopphold(sykepengesoknad),
            fom = sykepengesoknad.fom,
            tom = sykepengesoknad.tom,
            startSyketilfelle = sykepengesoknad.startSykeforlop,
            sykmeldingSkrevet = sykepengesoknad.sykmeldingSkrevet?.tilOsloLocalDateTime(),
            opprettet = sykepengesoknad.opprettet?.tilOsloLocalDateTime(),
            sendtNav = sykepengesoknad.sendtNav?.tilOsloLocalDateTime(),
            fravar = samleFravaerListe(sykepengesoknad),
            egenmeldtSykmelding = sykepengesoknad.egenmeldtSykmelding,
            andreInntektskilder = hentInntektListe(sykepengesoknad),
            soknadsperioder = hentSoknadsPerioder(sykepengesoknad),
            sporsmal = sykepengesoknad.sporsmal.map { it.tilSporsmalDTO() },
            avsendertype = sykepengesoknad.avsendertype?.tilAvsendertypeDTO(),
            permitteringer = sykepengesoknad.hentPermitteringer(),
            merknaderFraSykmelding = sykepengesoknad.merknaderFraSykmelding.tilMerknadDTO(),
            arbeidUtenforNorge = sykepengesoknad.hentArbeidUtenforNorge(),
        )
    }

    private fun harSoktSykepengerUnderUtlandsopphold(sykepengesoknad: Sykepengesoknad): Boolean? {
        return if (!harSvartJaPaUtland(sykepengesoknad)) {
            null
        } else {
            sykepengesoknad.getOptionalSporsmalMedTag(UTLANDSOPPHOLD_SOKT_SYKEPENGER)
                .map { it.forsteSvar }
                .map { "JA" == it }
                .orElse(null)
        }
    }

    private fun harSvartJaPaUtland(sykepengesoknad: Sykepengesoknad): Boolean {
        val utland = sykepengesoknad.getOptionalSporsmalMedTag(ARBEIDSLEDIG_UTLAND)
        return utland.isPresent && "CHECKED" == utland.get().forsteSvar
    }

    private fun hentSoknadsPerioder(sykepengesoknad: Sykepengesoknad): List<SoknadsperiodeDTO> {
        return sykepengesoknad.soknadPerioder!!.map {
            SoknadsperiodeDTO(
                fom = it.fom,
                tom = it.tom,
                sykmeldingsgrad = it.grad,
                sykmeldingstype = it.sykmeldingstype?.tilSykmeldingstypeDTO()
            )
        }
    }

    private fun samleFravaerListe(soknad: Sykepengesoknad): List<FravarDTO> {
        return hentFeriePermUtlandListe(soknad) + listOfNotNull(finnUtdanning(soknad))
    }

    private fun finnUtdanning(soknad: Sykepengesoknad): FravarDTO? {
        if (!soknad.getOptionalSporsmalMedTag(UTDANNING).isPresent) {
            return null
        }

        val startsvar = soknad.getSporsmalMedTag(UTDANNING_START).forsteSvar
        if ("JA" != soknad.getSporsmalMedTag(UTDANNING).forsteSvar || startsvar == null) {
            return null
        }
        val fravar =
            if ("JA" == soknad.getSporsmalMedTag(FULLTIDSSTUDIUM).forsteSvar) FravarstypeDTO.UTDANNING_FULLTID else FravarstypeDTO.UTDANNING_DELTID
        return FravarDTO(
            fom = LocalDate.parse(startsvar, ISO_LOCAL_DATE),
            type = fravar
        )
    }

    private fun hentFeriePermUtlandListe(sykepengesoknad: Sykepengesoknad): List<FravarDTO> {
        val fravarliste = ArrayList<FravarDTO>()

        if ("CHECKED" == sykepengesoknad.getSporsmalMedTagOrNull(ARBEIDSLEDIG_UTLAND)?.forsteSvar) {
            fravarliste.addAll(hentFravar(sykepengesoknad.getSporsmalMedTag(UTLAND_NAR)))
        }

        return fravarliste
    }

    private fun hentFravar(sporsmal: Sporsmal): List<FravarDTO> {
        val fravarliste = ArrayList<FravarDTO>()
        val svarliste = sporsmal.svar

        for (svar in svarliste) {
            val periode = svar.verdi.getJsonPeriode()
            fravarliste.add(
                FravarDTO(
                    fom = periode.fom,
                    tom = periode.tom,
                    type = FravarstypeDTO.UTLANDSOPPHOLD
                )
            )
        }
        return fravarliste
    }

    private fun hentInntektListe(soknad: Sykepengesoknad): List<InntektskildeDTO> {
        val andreinntektsporsmal = soknad.getSporsmalMedTagOrNull(HVILKE_ANDRE_INNTEKTSKILDER)
        return if ("JA" == andreinntektsporsmal?.forsteSvar)
            andreinntektsporsmal.undersporsmal[0].undersporsmal
                .filter { it.svar.isNotEmpty() }
                .map {
                    InntektskildeDTO(
                        mapSporsmalTilInntektskildetype(it),
                        "JA" == it.undersporsmal[0].forsteSvar
                    )
                }
        else
            emptyList()
    }

    private fun mapSporsmalTilInntektskildetype(sporsmal: Sporsmal): InntektskildetypeDTO? {
        return when (sporsmal.tag) {
            INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD -> InntektskildetypeDTO.ANDRE_ARBEIDSFORHOLD
            INNTEKTSKILDE_SELVSTENDIG -> InntektskildetypeDTO.SELVSTENDIG_NARINGSDRIVENDE
            INNTEKTSKILDE_SELVSTENDIG_DAGMAMMA -> InntektskildetypeDTO.SELVSTENDIG_NARINGSDRIVENDE_DAGMAMMA
            INNTEKTSKILDE_JORDBRUKER -> InntektskildetypeDTO.JORDBRUKER_FISKER_REINDRIFTSUTOVER
            INNTEKTSKILDE_FRILANSER -> InntektskildetypeDTO.FRILANSER
            INNTEKTSKILDE_FOSTERHJEM -> InntektskildetypeDTO.FOSTERHJEMGODTGJORELSE
            INNTEKTSKILDE_OMSORGSLONN -> InntektskildetypeDTO.OMSORGSLONN
            INNTEKTSKILDE_ANNET -> InntektskildetypeDTO.ANNET
            else -> null
        }
    }
}
