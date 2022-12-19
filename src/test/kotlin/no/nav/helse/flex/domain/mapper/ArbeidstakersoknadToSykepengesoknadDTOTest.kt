package no.nav.helse.flex.domain.mapper

import no.nav.helse.flex.arbeidstaker.gammeltEgenmeldingSpm
import no.nav.helse.flex.domain.Mottaker
import no.nav.helse.flex.domain.Soknadsperiode
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Svar
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Sykmeldingstype.AKTIVITET_IKKE_MULIG
import no.nav.helse.flex.domain.Sykmeldingstype.GRADERT
import no.nav.helse.flex.domain.mapper.sporsmalprossesering.arbeidGjenopptattDato
import no.nav.helse.flex.domain.mapper.sporsmalprossesering.finnUtdanning
import no.nav.helse.flex.domain.mapper.sporsmalprossesering.getStillingsprosent
import no.nav.helse.flex.domain.mapper.sporsmalprossesering.harSoktSykepengerUnderUtlandsopphold
import no.nav.helse.flex.domain.mapper.sporsmalprossesering.hentEgenmeldinger
import no.nav.helse.flex.domain.mapper.sporsmalprossesering.hentFeriePermUtlandListe
import no.nav.helse.flex.domain.mapper.sporsmalprossesering.hentSoknadsPerioderMedFaktiskGrad
import no.nav.helse.flex.domain.sporsmalBuilder
import no.nav.helse.flex.mock.gammeltFormatOpprettNySoknadMedFeriesporsmalSomUndersporsmal
import no.nav.helse.flex.mock.gammeltFormatOpprettSendtSoknadMedFeriesporsmalSomUndersporsmal
import no.nav.helse.flex.mock.opprettSendtSoknad
import no.nav.helse.flex.soknadsopprettelse.FERIE
import no.nav.helse.flex.soknadsopprettelse.FERIE_NAR
import no.nav.helse.flex.soknadsopprettelse.FERIE_NAR_V2
import no.nav.helse.flex.soknadsopprettelse.FERIE_PERMISJON_UTLAND
import no.nav.helse.flex.soknadsopprettelse.FERIE_V2
import no.nav.helse.flex.soknadsopprettelse.FULLTIDSSTUDIUM
import no.nav.helse.flex.soknadsopprettelse.PERMISJON
import no.nav.helse.flex.soknadsopprettelse.PERMISJON_NAR
import no.nav.helse.flex.soknadsopprettelse.PERMISJON_V2
import no.nav.helse.flex.soknadsopprettelse.TILBAKE_NAR
import no.nav.helse.flex.soknadsopprettelse.UTLAND
import no.nav.helse.flex.soknadsopprettelse.UTLANDSOPPHOLD_SOKT_SYKEPENGER
import no.nav.helse.flex.soknadsopprettelse.UTLAND_NAR
import no.nav.helse.flex.soknadsopprettelse.UTLAND_NAR_V2
import no.nav.helse.flex.soknadsopprettelse.UTLAND_V2
import no.nav.helse.flex.sykepengesoknad.kafka.*
import no.nav.helse.flex.testutil.besvarsporsmal
import no.nav.helse.flex.util.tilOsloLocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.THURSDAY
import java.time.LocalDate
import java.time.LocalDate.now
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE
import java.time.temporal.TemporalAdjusters.next
import java.util.Collections.emptyList
import java.util.Collections.singletonList

@ExtendWith(MockitoExtension::class)
class ArbeidstakersoknadToSykepengesoknadDTOTest {

    @Test
    fun periodeTest() {
        val fom = now().minusDays(3)
        val tom = now().minusDays(1)

        val (fom1, tom1) = (
            """{"fom":"${fom.format(ISO_LOCAL_DATE)}","tom":"${tom.format(ISO_LOCAL_DATE)}"}"""
            ).getJsonPeriode()

        assertThat(fom1).isEqualTo(fom)
        assertThat(tom1).isEqualTo(tom)
    }

    @Test
    fun parserGammelFormatPaPeriode() {
        val (fom1, tom1) = (
            """{"fom":"03.03.2019","tom":"06.03.2019"}"""
            ).getJsonPeriode()

        assertThat(fom1).isEqualTo(LocalDate.of(2019, 3, 3))
        assertThat(tom1).isEqualTo(LocalDate.of(2019, 3, 6))
    }

    @Test
    fun konverteringEnkleFelter() {
        val sykepengesoknad = gammeltFormatOpprettSendtSoknadMedFeriesporsmalSomUndersporsmal()
        val soknad =
            konverterArbeidstakersoknadTilSykepengesoknadDTO(
                sykepengesoknad,
                Mottaker.ARBEIDSGIVER_OG_NAV,
                false,
                hentSoknadsPerioderMedFaktiskGrad(sykepengesoknad).first
            )

        assertThat(soknad.id).isEqualTo(sykepengesoknad.id)
        assertThat(soknad.type).isEqualTo(SoknadstypeDTO.valueOf(sykepengesoknad.soknadstype.toString()))
        assertThat(soknad.status.name).isEqualTo(sykepengesoknad.status.name)
        assertThat(soknad.sykmeldingId).isEqualTo(sykepengesoknad.sykmeldingId)
        assertThat(soknad.arbeidsgiver!!.orgnummer).isEqualTo(sykepengesoknad.arbeidsgiverOrgnummer)
        assertThat(soknad.arbeidsgiver!!.navn).isEqualTo(sykepengesoknad.arbeidsgiverNavn)
        assertThat(soknad.arbeidssituasjon).isEqualTo(sykepengesoknad.arbeidssituasjon?.tilArbeidssituasjonDTO())
        assertThat(soknad.korrigerer).isEqualTo(sykepengesoknad.korrigerer)
        assertThat(soknad.korrigertAv).isEqualTo(sykepengesoknad.korrigertAv)
        assertThat(soknad.fom).isEqualTo(sykepengesoknad.fom)
        assertThat(soknad.tom).isEqualTo(sykepengesoknad.tom)
        assertThat(soknad.startSyketilfelle).isEqualTo(sykepengesoknad.startSykeforlop)
        assertThat(soknad.sykmeldingSkrevet).isEqualTo(sykepengesoknad.sykmeldingSkrevet?.tilOsloLocalDateTime())
        assertThat(soknad.opprettet).isEqualTo(sykepengesoknad.opprettet?.tilOsloLocalDateTime())
        assertThat(soknad.sendtNav).isEqualTo(sykepengesoknad.sendtNav?.tilOsloLocalDateTime())
        assertThat(soknad.ettersending).isFalse()
        assertThat(soknad.mottaker).isEqualTo(MottakerDTO.ARBEIDSGIVER_OG_NAV)
    }

    @Test
    fun soktUtenlandsoppholdSettesTilNullHvisSporsmaletIkkeFinnes() {
        val sykepengesoknad = gammeltFormatOpprettSendtSoknadMedFeriesporsmalSomUndersporsmal()

        val soktUtenlandsopphold = harSoktSykepengerUnderUtlandsopphold(sykepengesoknad)

        assertNull(soktUtenlandsopphold)
    }

    @Test
    fun soktUtenlandsoppholdSettesTilTrueHvisJa() {
        var sykepengesoknad = gammeltFormatOpprettSendtSoknadMedFeriesporsmalSomUndersporsmal()
        sykepengesoknad = sykepengesoknad.replaceSporsmal(
            sykepengesoknad.getSporsmalMedTag(UTLAND).toBuilder()
                .undersporsmal(
                    listOf(
                        sykepengesoknad.getSporsmalMedTag(UTLAND_NAR),
                        sporsmalBuilder()
                            .tag(UTLANDSOPPHOLD_SOKT_SYKEPENGER)
                            .svartype(Svartype.JA_NEI)
                            .svar(listOf(Svar(null, "JA")))
                            .build()
                    )
                )
                .build()
        )

        val soktUtenlandsopphold = harSoktSykepengerUnderUtlandsopphold(sykepengesoknad)

        assertThat(soktUtenlandsopphold).isTrue()
    }

    @Test
    fun soktUtenlandsoppholdSettesTilFalseHvisNei() {
        var sykepengesoknad = gammeltFormatOpprettSendtSoknadMedFeriesporsmalSomUndersporsmal()
        sykepengesoknad = sykepengesoknad.replaceSporsmal(
            sykepengesoknad.getSporsmalMedTag(UTLAND).toBuilder()
                .undersporsmal(
                    listOf(
                        sykepengesoknad.getSporsmalMedTag(UTLAND_NAR),
                        sporsmalBuilder()
                            .tag(UTLANDSOPPHOLD_SOKT_SYKEPENGER)
                            .svartype(Svartype.JA_NEI)
                            .svar(listOf(Svar(null, "NEI")))
                            .build()
                    )
                )
                .build()
        )

        val soktUtenlandsopphold = harSoktSykepengerUnderUtlandsopphold(sykepengesoknad)

        assertThat(soktUtenlandsopphold).isFalse()
    }

    @Test
    fun soktUtenlandsoppholdSettesTilNullHvisIkkeBesvart() {
        val sykepengesoknad = gammeltFormatOpprettSendtSoknadMedFeriesporsmalSomUndersporsmal()
        sykepengesoknad.replaceSporsmal(
            sykepengesoknad.getSporsmalMedTag(UTLAND).toBuilder()
                .undersporsmal(
                    listOf(
                        sykepengesoknad.getSporsmalMedTag(UTLAND_NAR),
                        sporsmalBuilder()
                            .tag(UTLANDSOPPHOLD_SOKT_SYKEPENGER)
                            .svartype(Svartype.JA_NEI)
                            .svar(emptyList())
                            .build()
                    )
                )
                .build()
        )

        val soktUtenlandsopphold = harSoktSykepengerUnderUtlandsopphold(sykepengesoknad)

        assertNull(soktUtenlandsopphold)
    }

    @Test
    fun soktUtenlandsoppholdSettesTilNullHvisModerspmIkkeFinnes() {
        var sykepengesoknad = gammeltFormatOpprettSendtSoknadMedFeriesporsmalSomUndersporsmal()
        sykepengesoknad.replaceSporsmal(
            sykepengesoknad.getSporsmalMedTag(UTLAND).toBuilder()
                .undersporsmal(
                    listOf(
                        sykepengesoknad.getSporsmalMedTag(UTLAND_NAR),
                        sporsmalBuilder()
                            .tag(UTLANDSOPPHOLD_SOKT_SYKEPENGER)
                            .svartype(Svartype.JA_NEI)
                            .svar(listOf(Svar(null, "JA")))
                            .build()
                    )
                )
                .build()
        )
        sykepengesoknad = sykepengesoknad.replaceSporsmal(
            sykepengesoknad.getSporsmalMedTag(FERIE_PERMISJON_UTLAND).toBuilder().svar(emptyList()).build()
        )

        val soktUtenlandsopphold = harSoktSykepengerUnderUtlandsopphold(sykepengesoknad)

        assertNull(soktUtenlandsopphold)
    }

    @Test
    fun soktUtenlandsoppholdSettesTilNullHvisModerspmIkkeFinnesForUtlandV2() {
        val sykepengesoknad = opprettSendtSoknad()
        sykepengesoknad.replaceSporsmal(
            sykepengesoknad.getSporsmalMedTag(UTLAND_V2).toBuilder()
                .undersporsmal(
                    listOf(
                        sykepengesoknad.getSporsmalMedTag(UTLAND_NAR_V2),
                        sporsmalBuilder()
                            .tag(UTLANDSOPPHOLD_SOKT_SYKEPENGER)
                            .svartype(Svartype.JA_NEI)
                            .svar(listOf(Svar(null, "JA")))
                            .build()
                    )
                )
                .build()
        )
        sykepengesoknad.replaceSporsmal(
            sykepengesoknad.getSporsmalMedTag(UTLAND_V2).toBuilder().svar(emptyList()).build()
        )

        val soktUtenlandsopphold = harSoktSykepengerUnderUtlandsopphold(sykepengesoknad)

        assertNull(soktUtenlandsopphold)
    }

    @Test
    fun arbeidGjenopptattDatoSettesLikBesvartDato() {
        val toDagerSiden = now().minusDays(2).format(ISO_LOCAL_DATE)
        var sykepengesoknad = gammeltFormatOpprettSendtSoknadMedFeriesporsmalSomUndersporsmal()
        sykepengesoknad = sykepengesoknad.replaceSporsmal(
            sykepengesoknad.getSporsmalMedTag(TILBAKE_NAR).toBuilder()
                .svar(listOf(Svar(null, toDagerSiden)))
                .build()
        )

        val arbeidGjenopptattDato = arbeidGjenopptattDato(sykepengesoknad)

        assertThat(arbeidGjenopptattDato).isEqualTo(toDagerSiden)
    }

    @Test
    fun arbeidGjenopptattSettesTilNullHvisIkkeBesvart() {
        var sykepengesoknad = gammeltFormatOpprettSendtSoknadMedFeriesporsmalSomUndersporsmal()
        sykepengesoknad = sykepengesoknad.replaceSporsmal(
            sykepengesoknad.getSporsmalMedTag(TILBAKE_NAR).toBuilder()
                .svar(emptyList())
                .build()
        )

        val arbeidGjenopptattDato = arbeidGjenopptattDato(sykepengesoknad)

        assertThat(arbeidGjenopptattDato).isNull()
    }

    @Test
    fun utenFravaerMedFerieSporsmalSomUndersporsmal() {
        var sykepengesoknad = gammeltFormatOpprettSendtSoknadMedFeriesporsmalSomUndersporsmal()
        sykepengesoknad = sykepengesoknad.replaceSporsmal(
            sykepengesoknad.getSporsmalMedTag(FERIE_NAR).toBuilder()
                .svar(emptyList())
                .build()
        )
        sykepengesoknad = sykepengesoknad.replaceSporsmal(
            sykepengesoknad.getSporsmalMedTag(UTLAND_NAR).toBuilder()
                .svar(emptyList())
                .build()
        )

        val fravar = hentFeriePermUtlandListe(sykepengesoknad)

        assertThat(fravar).isEmpty()
    }

    @Test
    fun feriePermUtlandFinnesIkkeNarFerieSporsmalVarUndersporsmal() {
        var sykepengesoknad = gammeltFormatOpprettSendtSoknadMedFeriesporsmalSomUndersporsmal()
        sykepengesoknad = sykepengesoknad.fjernSporsmal(FERIE_PERMISJON_UTLAND)

        val fravar = hentFeriePermUtlandListe(sykepengesoknad)

        assertThat(fravar).isEmpty()
    }

    @Test
    fun feriePermUtlandFinnesIkkeNarFerieSporsmalVarHovedsporsmal() {
        var sykepengesoknad = opprettSendtSoknad()
        sykepengesoknad = sykepengesoknad.fjernSporsmal(FERIE_V2)
        sykepengesoknad = sykepengesoknad.fjernSporsmal(PERMISJON_V2)
        sykepengesoknad = sykepengesoknad.fjernSporsmal(UTLAND_V2)

        val fravar = hentFeriePermUtlandListe(sykepengesoknad)

        assertThat(fravar).isEmpty()
    }

    @Test
    fun medFravaerMedFerieSporsmalSomUndersporsmal() {
        val sykepengesoknad = gammeltFormatOpprettSendtSoknadMedFeriesporsmalSomUndersporsmal()

        val forventetFravar = ArrayList<FravarDTO>()
        forventetFravar.add(
            FravarDTO(
                sykepengesoknad.fom!!.plusDays(2),
                sykepengesoknad.fom!!.plusDays(4),
                FravarstypeDTO.FERIE
            )
        )
        forventetFravar.add(
            FravarDTO(
                sykepengesoknad.fom!!.plusDays(1),
                sykepengesoknad.fom!!.plusDays(2),
                FravarstypeDTO.UTLANDSOPPHOLD
            )
        )
        forventetFravar.add(
            FravarDTO(
                sykepengesoknad.fom!!.plusDays(4),
                sykepengesoknad.fom!!.plusDays(6),
                FravarstypeDTO.UTLANDSOPPHOLD
            )
        )

        val fravar = hentFeriePermUtlandListe(sykepengesoknad)

        assertThat(fravar).isEqualTo(forventetFravar)
    }

    @Test
    fun utenFravaer() {
        var sykepengesoknad = opprettSendtSoknad()
        sykepengesoknad = sykepengesoknad.replaceSporsmal(
            sykepengesoknad.getSporsmalMedTag(FERIE_NAR_V2).toBuilder()
                .svar(emptyList())
                .build()
        )
        sykepengesoknad = sykepengesoknad.replaceSporsmal(
            sykepengesoknad.getSporsmalMedTag(UTLAND_NAR_V2).toBuilder()
                .svar(emptyList())
                .build()
        )

        val fravar = hentFeriePermUtlandListe(sykepengesoknad)

        assertThat(fravar).isEmpty()
    }

    @Test
    fun medFravaer() {
        val sykepengesoknad = opprettSendtSoknad()

        val forventetFravar = ArrayList<FravarDTO>()
        forventetFravar.add(
            FravarDTO(
                sykepengesoknad.fom!!.plusDays(1),
                sykepengesoknad.fom!!.plusDays(2),
                FravarstypeDTO.FERIE
            )
        )
        forventetFravar.add(
            FravarDTO(
                sykepengesoknad.fom!!.plusDays(1),
                sykepengesoknad.fom!!.plusDays(1),
                FravarstypeDTO.UTLANDSOPPHOLD
            )
        )
        forventetFravar.add(
            FravarDTO(
                sykepengesoknad.fom!!.plusDays(4),
                sykepengesoknad.fom!!.plusDays(6),
                FravarstypeDTO.UTLANDSOPPHOLD
            )
        )
        val fravar = hentFeriePermUtlandListe(sykepengesoknad)
        assertThat(fravar).isEqualTo(forventetFravar)
    }

    @Test
    fun feriefravarSettes() {
        val fom = now().minusDays(4)
        val tom = now().minusDays(2)
        var sykepengesoknad = gammeltFormatOpprettSendtSoknadMedFeriesporsmalSomUndersporsmal()
        sykepengesoknad = sykepengesoknad.replaceSporsmal(
            sykepengesoknad.getSporsmalMedTag(FERIE_NAR).toBuilder()
                .svar(
                    listOf(
                        Svar(
                            null,
                            "{\"fom\":\"" + fom.format(ISO_LOCAL_DATE) +
                                "\",\"tom\":\"" + tom.format(ISO_LOCAL_DATE) + "\"}"
                        )
                    )
                )
                .build()
        )

        val (fom1, tom1, type) = hentFeriePermUtlandListe(sykepengesoknad).stream()
            .filter { (_, _, type) -> FravarstypeDTO.FERIE == type }
            .findFirst()
            .get()

        assertThat(fom1).isEqualTo(fom)
        assertThat(tom1).isEqualTo(tom)
        assertThat(type).isEqualTo(FravarstypeDTO.FERIE)
    }

    @Test
    fun permisjonsfravarSettes() {
        val fom = now().minusDays(4)
        val tom = now().minusDays(2)
        var sykepengesoknad = gammeltFormatOpprettSendtSoknadMedFeriesporsmalSomUndersporsmal()
        sykepengesoknad = sykepengesoknad.replaceSporsmal(
            sykepengesoknad.getSporsmalMedTag(PERMISJON).toBuilder()
                .svar(listOf(Svar(null, "CHECKED")))
                .build()
        )
        sykepengesoknad = sykepengesoknad.replaceSporsmal(
            sykepengesoknad.getSporsmalMedTag(PERMISJON_NAR).toBuilder()
                .svar(
                    listOf(
                        Svar(
                            null,
                            "{\"fom\":\"" + fom.format(ISO_LOCAL_DATE) +
                                "\",\"tom\":\"" + tom.format(ISO_LOCAL_DATE) + "\"}"
                        )
                    )
                )
                .build()
        )

        val (fom1, tom1, type) = hentFeriePermUtlandListe(sykepengesoknad).stream()
            .filter { (_, _, type) -> FravarstypeDTO.PERMISJON == type }
            .findFirst()
            .get()

        assertThat(fom1).isEqualTo(fom)
        assertThat(tom1).isEqualTo(tom)
        assertThat(type).isEqualTo(FravarstypeDTO.PERMISJON)
    }

    @Test
    fun utlandsfravarSettesMedFeriesporsmalSomUndersporsmal() {
        val fom = now().minusDays(4)
        val tom = now().minusDays(2)
        var sykepengesoknad = gammeltFormatOpprettSendtSoknadMedFeriesporsmalSomUndersporsmal()
        sykepengesoknad = sykepengesoknad.replaceSporsmal(
            sykepengesoknad.getSporsmalMedTag(UTLAND).toBuilder()
                .svar(listOf(Svar(null, "CHECKED")))
                .build()
        )
        sykepengesoknad = sykepengesoknad.replaceSporsmal(
            sykepengesoknad.getSporsmalMedTag(UTLAND_NAR).toBuilder()
                .svar(
                    listOf(
                        Svar(
                            null,
                            "{\"fom\":\"" + fom.format(ISO_LOCAL_DATE) +
                                "\",\"tom\":\"" + tom.format(ISO_LOCAL_DATE) + "\"}"
                        )
                    )
                )
                .build()
        )

        val (fom1, tom1, type) = hentFeriePermUtlandListe(sykepengesoknad).stream()
            .filter { (_, _, type) -> FravarstypeDTO.UTLANDSOPPHOLD == type }
            .findFirst()
            .get()

        assertThat(fom1).isEqualTo(fom)
        assertThat(tom1).isEqualTo(tom)
        assertThat(type).isEqualTo(FravarstypeDTO.UTLANDSOPPHOLD)
    }

    @Test
    fun utlandsfravarSettes() {
        val fom = now().minusDays(4)
        val tom = now().minusDays(2)
        var sykepengesoknad = opprettSendtSoknad()
        sykepengesoknad = sykepengesoknad.replaceSporsmal(
            sykepengesoknad.getSporsmalMedTag(UTLAND_V2).toBuilder()
                .svar(listOf(Svar(null, "JA")))
                .build()
        )
        sykepengesoknad = sykepengesoknad.replaceSporsmal(
            sykepengesoknad.getSporsmalMedTag(UTLAND_NAR_V2).toBuilder()
                .svar(
                    listOf(
                        Svar(
                            null,
                            "{\"fom\":\"" + fom.format(ISO_LOCAL_DATE) +
                                "\",\"tom\":\"" + tom.format(ISO_LOCAL_DATE) + "\"}"
                        )
                    )
                )
                .build()
        )

        val (fom1, tom1, type) = hentFeriePermUtlandListe(sykepengesoknad).stream()
            .filter { (_, _, type) -> FravarstypeDTO.UTLANDSOPPHOLD == type }
            .findFirst()
            .get()

        assertThat(fom1).isEqualTo(fom)
        assertThat(tom1).isEqualTo(tom)
        assertThat(type).isEqualTo(FravarstypeDTO.UTLANDSOPPHOLD)
    }

    @Test
    fun utdanningDeltid() {
        val sykepengesoknad = gammeltFormatOpprettSendtSoknadMedFeriesporsmalSomUndersporsmal()

        val (fom, tom, type) = finnUtdanning(sykepengesoknad).get()

        assertThat(type).isEqualTo(FravarstypeDTO.UTDANNING_DELTID)
        assertThat(fom).isEqualTo(sykepengesoknad.fom!!.plusDays(3))
        assertThat(tom).isNull()
    }

    @Test
    fun utdanningFulltid() {
        var sykepengesoknad = gammeltFormatOpprettSendtSoknadMedFeriesporsmalSomUndersporsmal()
        sykepengesoknad = sykepengesoknad.replaceSporsmal(
            sykepengesoknad.getSporsmalMedTag(FULLTIDSSTUDIUM).toBuilder()
                .svar(listOf(Svar(null, "JA")))
                .build()
        )

        val (fom, tom, type) = finnUtdanning(sykepengesoknad).get()

        assertThat(type).isEqualTo(FravarstypeDTO.UTDANNING_FULLTID)
        assertThat(fom).isEqualTo(sykepengesoknad.fom!!.plusDays(3))
        assertThat(tom).isNull()
    }

    @Test
    fun egenmeldingTest() {
        val fom = now().minusDays(10)
        val tom = now().minusDays(5)
        val sykepengesoknad = gammeltFormatOpprettSendtSoknadMedFeriesporsmalSomUndersporsmal()
            .copy(sporsmal = listOf(gammeltEgenmeldingSpm(fom)))
            .besvarsporsmal(
                "EGENMELDINGER_NAR",
                "{\"fom\":\"" + fom.format(ISO_LOCAL_DATE) +
                    "\",\"tom\":\"" + tom.format(ISO_LOCAL_DATE) + "\"}"
            )
            .besvarsporsmal("TIDLIGERE_EGENMELDING", "CHECKED")

        val egenmeldinger = hentEgenmeldinger(sykepengesoknad)

        assertThat(egenmeldinger).hasSize(1)
        assertThat(egenmeldinger[0].fom).isEqualTo(fom)
        assertThat(egenmeldinger[0].tom).isEqualTo(tom)
    }

    @Test
    fun mapperKorrigertArbeidstidProsentBesvart() {
        val mandag = now().with(next(MONDAY))
        val sykepengesoknad = gammeltFormatOpprettNySoknadMedFeriesporsmalSomUndersporsmal().copy(
            soknadPerioder = listOf(Soknadsperiode(mandag, mandag.with(next(THURSDAY)), 100, null))
        )

        val korrigertArbeidstid = hentSoknadsPerioderMedFaktiskGrad(sykepengesoknad).first

        assertThat(korrigertArbeidstid[0].sykmeldingsgrad).isEqualTo(100)
        assertThat(korrigertArbeidstid[0].avtaltTimer).isEqualTo(37.5)
        assertThat(korrigertArbeidstid[0].faktiskGrad).isEqualTo(79)
        assertThat(korrigertArbeidstid[0].faktiskTimer).isNull()
    }

    @Test
    fun mapperKorrigertArbeidstidTimerBesvart() {
        val mandag = now().with(next(MONDAY))
        var sykepengesoknad = gammeltFormatOpprettNySoknadMedFeriesporsmalSomUndersporsmal().copy(
            soknadPerioder = (
                listOf(
                    Soknadsperiode(now(), now(), 100, null),
                    Soknadsperiode(mandag, mandag.with(next(THURSDAY)), 40, null)
                )
                )
        )

        sykepengesoknad = sykepengesoknad.replaceSporsmal(
            sykepengesoknad.getSporsmalMedTag(TILBAKE_NAR).copy(
                svar = emptyList()
            )
        )

        val korrigertArbeidstid = hentSoknadsPerioderMedFaktiskGrad(sykepengesoknad).first

        assertThat(korrigertArbeidstid[1].sykmeldingsgrad).isEqualTo(40)
        assertThat(korrigertArbeidstid[1].avtaltTimer).isEqualTo(37.5)
        assertThat(korrigertArbeidstid[1].faktiskGrad).isEqualTo(100)
        assertThat(korrigertArbeidstid[1].faktiskTimer).isEqualTo(66.0)
    }

    @Test
    fun mapperKorrigertArbeidstidMedArbeidGjenopptattDatoSatt() {
        val mandag = now().with(next(MONDAY))
        var sykepengesoknad = gammeltFormatOpprettNySoknadMedFeriesporsmalSomUndersporsmal().copy(
            soknadPerioder = listOf(
                Soknadsperiode(now(), now(), 100, null),
                Soknadsperiode(mandag, mandag.with(next(THURSDAY)), 40, null)
            )
        )

        sykepengesoknad = sykepengesoknad.replaceSporsmal(
            sykepengesoknad.getSporsmalMedTag(TILBAKE_NAR).toBuilder()
                .svar(listOf<Svar>(Svar(null, mandag.with(next(THURSDAY)).format(ISO_LOCAL_DATE))))
                .build()
        )

        val korrigertArbeidstid = hentSoknadsPerioderMedFaktiskGrad(sykepengesoknad).first

        assertThat(korrigertArbeidstid[1].sykmeldingsgrad).isEqualTo(40)
        assertThat(korrigertArbeidstid[1].avtaltTimer).isEqualTo(37.5)
        assertThat(korrigertArbeidstid[1].faktiskGrad).isEqualTo(100)
        assertThat(korrigertArbeidstid[1].faktiskTimer).isEqualTo(66.0)
    }

    @Test
    fun mapperKorrigertArbeidstidTimerBesvartMedFravar() {
        val mandag = now().with(next(MONDAY))
        var sykepengesoknad = gammeltFormatOpprettNySoknadMedFeriesporsmalSomUndersporsmal().copy(
            soknadPerioder = (
                listOf(
                    Soknadsperiode(now(), now(), 100, null),
                    Soknadsperiode(mandag, mandag.with(next(THURSDAY)), 40, null)
                )
                )
        )
        sykepengesoknad = sykepengesoknad
            .replaceSporsmal(
                sykepengesoknad.getSporsmalMedTag(TILBAKE_NAR).toBuilder()
                    .svar(emptyList())
                    .build()
            )
            .replaceSporsmal(
                sykepengesoknad.getSporsmalMedTag(FERIE_PERMISJON_UTLAND).toBuilder()
                    .svar(singletonList(Svar(null, verdi = "JA")))
                    .build()
            )
            .replaceSporsmal(
                sykepengesoknad.getSporsmalMedTag(FERIE).toBuilder()
                    .svar(singletonList(Svar(null, verdi = "CHECKED")))
                    .build()
            )
            .replaceSporsmal(
                sykepengesoknad.getSporsmalMedTag(UTLAND).toBuilder()
                    .svar(emptyList())
                    .build()
            )
            .replaceSporsmal(
                sykepengesoknad.getSporsmalMedTag(FERIE_NAR).toBuilder()
                    .svar(
                        singletonList(
                            Svar(
                                null,
                                verdi = "{\"fom\":\"${mandag.format(ISO_LOCAL_DATE)}\",\"tom\":\"${
                                mandag.plusDays(
                                    1
                                ).format(ISO_LOCAL_DATE)
                                }\"}"
                            )
                        )
                    )
                    .build()
            )

        val korrigertArbeidstid = hentSoknadsPerioderMedFaktiskGrad(sykepengesoknad).first

        assertThat(korrigertArbeidstid[1].sykmeldingsgrad).isEqualTo(40)
        assertThat(korrigertArbeidstid[1].avtaltTimer).isEqualTo(37.5)
        assertThat(korrigertArbeidstid[1].faktiskGrad).isEqualTo(100)
        assertThat(korrigertArbeidstid[1].faktiskTimer).isEqualTo(66.0)
    }

    @Test
    fun soknadsperioderUtenFravarBlirTattMed() {
        var sykepengesoknad = gammeltFormatOpprettNySoknadMedFeriesporsmalSomUndersporsmal().copy(
            soknadPerioder = (listOf(Soknadsperiode(now().minusDays(19), now().minusDays(15), 100, null)))
        )
        sykepengesoknad = sykepengesoknad.replaceSporsmal(
            sykepengesoknad.getSporsmalMedTag("ARBEID_UNDERVEIS_100_PROSENT_0").toBuilder()
                .svar(listOf<Svar>(Svar(null, "NEI")))
                .build()
        )
        sykepengesoknad = sykepengesoknad.replaceSporsmal(
            sykepengesoknad.getSporsmalMedTag("JOBBET_DU_GRADERT_1").toBuilder()
                .svar(listOf<Svar>(Svar(null, "NEI")))
                .build()
        )

        val soknadsperiodeDTOList = hentSoknadsPerioderMedFaktiskGrad(sykepengesoknad).first

        assertThat(soknadsperiodeDTOList).hasSize(1)
        assertThat(soknadsperiodeDTOList[0].fom).isEqualTo(now().minusDays(19))
        assertThat(soknadsperiodeDTOList[0].tom).isEqualTo(now().minusDays(15))
        assertThat(soknadsperiodeDTOList[0].sykmeldingsgrad).isEqualTo(100)
    }

    @Test
    fun utkastTilEndringSkalBliNy() {
        val sykepengesoknad = gammeltFormatOpprettNySoknadMedFeriesporsmalSomUndersporsmal().copy(
            status = (Soknadstatus.UTKAST_TIL_KORRIGERING)
        )

        val status = sykepengesoknad.status.tilSoknadstatusDTO()

        assertThat(status).isEqualByComparingTo(SoknadsstatusDTO.NY)
    }

    @Test
    fun soknadSomIkkeErSendtSkalHaNullMottaker() {
        val sykepengesoknad = gammeltFormatOpprettNySoknadMedFeriesporsmalSomUndersporsmal()

        val soknad = konverterArbeidstakersoknadTilSykepengesoknadDTO(
            sykepengesoknad,
            null,
            false,
            hentSoknadsPerioderMedFaktiskGrad(sykepengesoknad).first
        )

        assertThat(soknad.mottaker).isNull()
    }

    @Test
    fun soknadSomErEttersendtSkalHaEttersendingLikTrue() {
        val sykepengesoknad = gammeltFormatOpprettNySoknadMedFeriesporsmalSomUndersporsmal().copy(
            status = (Soknadstatus.SENDT)
        )

        val soknad =
            konverterArbeidstakersoknadTilSykepengesoknadDTO(
                sykepengesoknad,
                Mottaker.ARBEIDSGIVER_OG_NAV,
                true,
                hentSoknadsPerioderMedFaktiskGrad(sykepengesoknad).first
            )
        val ettersending = soknad.ettersending
        val mottaker = soknad.mottaker

        assertThat(mottaker).isEqualTo(MottakerDTO.ARBEIDSGIVER_OG_NAV)
        assertThat(ettersending).isTrue()
    }

    @Test
    fun mapperSoknadsperioderSelvOmViHarEndretAntallPeriodesporsmal() {
        val sykepengesoknaden = gammeltFormatOpprettNySoknadMedFeriesporsmalSomUndersporsmal().copy(
            soknadPerioder = (
                listOf(
                    Soknadsperiode(now().minusDays(19), now().minusDays(15), 100, AKTIVITET_IKKE_MULIG),
                    Soknadsperiode(now().minusDays(14), now().minusDays(10), 40, GRADERT)
                )
                )
        )

        val sykepengesoknad =
            sykepengesoknaden.copy(sporsmal = sykepengesoknaden.sporsmal.filter { s -> s.tag != "JOBBET_DU_GRADERT_1" })

        val soknadsperioder =
            konverterArbeidstakersoknadTilSykepengesoknadDTO(
                sykepengesoknad,
                null,
                false,
                hentSoknadsPerioderMedFaktiskGrad(sykepengesoknad).first
            ).soknadsperioder

        assertThat(soknadsperioder!!.size).isEqualTo(2)

        assertThat(soknadsperioder[0].fom).isEqualTo(sykepengesoknad.soknadPerioder!!.get(0).fom)
        assertThat(soknadsperioder[0].tom).isEqualTo(sykepengesoknad.soknadPerioder!!.get(0).tom)
        assertThat(soknadsperioder[0].sykmeldingsgrad).isEqualTo(sykepengesoknad.soknadPerioder!!.get(0).grad)
        assertThat(soknadsperioder[0].sykmeldingstype).isEqualTo(SykmeldingstypeDTO.AKTIVITET_IKKE_MULIG)
        assertThat(soknadsperioder[0].avtaltTimer).isEqualTo(37.5)
        assertThat(soknadsperioder[0].faktiskGrad).isEqualTo(79)
        assertThat(soknadsperioder[0].faktiskTimer).isEqualTo(null)

        assertThat(soknadsperioder[1].fom).isEqualTo(sykepengesoknad.soknadPerioder!!.get(1).fom)
        assertThat(soknadsperioder[1].tom).isEqualTo(sykepengesoknad.soknadPerioder!!.get(1).tom)
        assertThat(soknadsperioder[1].sykmeldingsgrad).isEqualTo(sykepengesoknad.soknadPerioder!!.get(1).grad)
        assertThat(soknadsperioder[1].sykmeldingstype).isEqualTo(SykmeldingstypeDTO.GRADERT)
        assertThat(soknadsperioder[1].avtaltTimer).isEqualTo(null)
        assertThat(soknadsperioder[1].faktiskGrad).isEqualTo(null)
        assertThat(soknadsperioder[1].faktiskTimer).isEqualTo(null)
    }

    @Test
    fun faktiskArbeidsgradTarIkkeMedFerieSomErUtenforPerioden() {
        val faktiskTimer = 45.0
        val avtaltTimer = 37.5
        val periode = Soknadsperiode(
            LocalDate.of(2019, 9, 16),
            LocalDate.of(2019, 9, 29),
            50,
            GRADERT
        )
        val ferieOgPermisjonPerioder = listOf(
            FravarDTO(
                LocalDate.of(2019, 10, 9),
                LocalDate.of(2019, 10, 13),
                FravarstypeDTO.UTLANDSOPPHOLD
            )
        )

        val faktiskArbeidsgrad = getStillingsprosent(faktiskTimer, avtaltTimer, periode, emptyList(), null)!!
        val faktiskArbeidsgradMedFerieEtterPerioden =
            getStillingsprosent(faktiskTimer, avtaltTimer, periode, ferieOgPermisjonPerioder, null)!!

        assertThat(faktiskArbeidsgrad).isEqualTo(60)
        assertThat(faktiskArbeidsgradMedFerieEtterPerioden).isEqualTo(60)
    }
}
