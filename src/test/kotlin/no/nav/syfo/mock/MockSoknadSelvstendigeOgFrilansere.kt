package no.nav.syfo.mock

import no.nav.syfo.domain.Arbeidssituasjon.FRILANSER
import no.nav.syfo.domain.Arbeidssituasjon.NAERINGSDRIVENDE
import no.nav.syfo.domain.Soknadstatus.NY
import no.nav.syfo.domain.Soknadstatus.SENDT
import no.nav.syfo.domain.Soknadstype
import no.nav.syfo.domain.Sporsmal
import no.nav.syfo.domain.Svar
import no.nav.syfo.domain.Sykepengesoknad
import no.nav.syfo.domain.rest.SoknadMetadata
import no.nav.syfo.model.sykmelding.arbeidsgiver.AktivitetIkkeMuligAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.GradertDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.repository.SykepengesoknadDAO
import no.nav.syfo.soknadsopprettelse.*
import no.nav.syfo.util.tilOsloInstant
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate.of
import java.time.LocalDateTime.now
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE
import java.util.Arrays.asList

@Component
class MockSoknadSelvstendigeOgFrilansere(private val sykepengesoknadDAO: SykepengesoknadDAO?) {

    fun opprettOgLagreNySoknad(): Sykepengesoknad {
        return sykepengesoknadDAO!!.lagreSykepengesoknad(opprettNySoknad())
    }

    fun opprettSendtSoknad(): Sykepengesoknad {
        val soknadMetadata = SoknadMetadata(
            fnr = "fnr-7454630",
            status = SENDT,
            startSykeforlop = of(2018, 5, 20),
            fom = of(2018, 5, 20),
            tom = of(2018, 5, 28),
            arbeidssituasjon = FRILANSER, arbeidsgiverOrgnummer = null, arbeidsgiverNavn = null,
            sykmeldingId = "14e78e84-50a5-45bb-9919-191c54f99691",
            sykmeldingSkrevet = of(2018, 5, 20).atStartOfDay().tilOsloInstant(),
            sykmeldingsperioder = listOf(
                SykmeldingsperiodeAGDTO(
                    fom = of(2018, 5, 20),
                    tom = of(2018, 5, 24),
                    gradert = GradertDTO(grad = 100, reisetilskudd = false),
                    type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                    aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                    behandlingsdager = null,
                    innspillTilArbeidsgiver = null,
                    reisetilskudd = false,
                ),
                SykmeldingsperiodeAGDTO(
                    fom = of(2018, 5, 25),
                    tom = of(2018, 5, 28),
                    gradert = GradertDTO(grad = 40, reisetilskudd = false),
                    type = PeriodetypeDTO.GRADERT,
                    aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                    behandlingsdager = null,
                    innspillTilArbeidsgiver = null,
                    reisetilskudd = false,
                ),
            ).tilSoknadsperioder(),
            soknadstype = Soknadstype.SELVSTENDIGE_OG_FRILANSERE,
            egenmeldtSykmelding = null

        )

        val sykepengesoknad = leggSvarPaSoknad(settOppSoknadSelvstendigOgFrilanser(soknadMetadata, false))
        return sykepengesoknad.copy(sendtNav = Instant.now())
    }

    fun opprettNySoknad(): Sykepengesoknad {
        val soknadMetadata = SoknadMetadata(
            fnr = "fnr-7454630",
            status = NY,
            startSykeforlop = of(2018, 6, 1),
            fom = of(2018, 6, 1),
            tom = of(2018, 6, 10),
            arbeidssituasjon = NAERINGSDRIVENDE, arbeidsgiverOrgnummer = null, arbeidsgiverNavn = null,
            sykmeldingId = "289148ba-4c3c-4b3f-b7a3-385b7e7c927d",
            sykmeldingSkrevet = of(2018, 6, 1).atStartOfDay().tilOsloInstant(),
            sykmeldingsperioder = listOf(
                SykmeldingsperiodeAGDTO(
                    fom = of(2018, 6, 1),
                    tom = of(2018, 6, 5),
                    gradert = GradertDTO(grad = 100, reisetilskudd = false),
                    type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                    aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                    behandlingsdager = null,
                    innspillTilArbeidsgiver = null,
                    reisetilskudd = false,
                ),
                SykmeldingsperiodeAGDTO(
                    fom = of(2018, 6, 6),
                    tom = of(2018, 6, 10),
                    gradert = GradertDTO(grad = 40, reisetilskudd = false),
                    type = PeriodetypeDTO.GRADERT,
                    aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                    behandlingsdager = null,
                    innspillTilArbeidsgiver = null,
                    reisetilskudd = false,
                ),
            ).tilSoknadsperioder(),
            soknadstype = Soknadstype.SELVSTENDIGE_OG_FRILANSERE,
            egenmeldtSykmelding = null

        )

        return leggSvarPaSoknad(settOppSoknadSelvstendigOgFrilanser(soknadMetadata, false))
    }

    private fun leggSvarPaSoknad(sykepengesoknad: Sykepengesoknad): Sykepengesoknad {
        return sykepengesoknad.replaceSporsmal(ansvarserklaring(sykepengesoknad))
            .replaceSporsmal(tilbakeIArbeid(sykepengesoknad))
            .replaceSporsmal(jobbetDu100Prosent(sykepengesoknad))
            .replaceSporsmal(jobbetDuGradert(sykepengesoknad))
            .replaceSporsmal(andreInntektskilder(sykepengesoknad))
            .replaceSporsmal(harDuOppholdtDegIUtlandet(sykepengesoknad))
            .replaceSporsmal(harDuStudert(sykepengesoknad))
            .replaceSporsmal(bekreftOpplysninger(sykepengesoknad))
    }

    private fun bekreftOpplysninger(sykepengesoknad: Sykepengesoknad): Sporsmal {
        return sykepengesoknad.getSporsmalMedTag(BEKREFT_OPPLYSNINGER).toBuilder()
            .svar(listOf(Svar(null, "CHECKED", null)))
            .build()
    }

    private fun harDuStudert(sykepengesoknad: Sykepengesoknad): Sporsmal {
        return sykepengesoknad.getSporsmalMedTag(UTDANNING).toBuilder()
            .svar(listOf(Svar(null, "JA", null)))
            .undersporsmal(
                asList(
                    sykepengesoknad.getSporsmalMedTag(UTDANNING_START).toBuilder()
                        .svar(listOf(Svar(null, sykepengesoknad.fom!!.plusDays(3).format(ISO_LOCAL_DATE), null)))
                        .build(),
                    sykepengesoknad.getSporsmalMedTag(FULLTIDSSTUDIUM).toBuilder()
                        .svar(listOf(Svar(null, "JA", null)))
                        .build()
                )
            )
            .build()
    }

    private fun harDuOppholdtDegIUtlandet(sykepengesoknad: Sykepengesoknad): Sporsmal {
        return sykepengesoknad.getSporsmalMedTag(UTLAND).toBuilder()
            .svar(listOf(Svar(null, "JA", null)))
            .undersporsmal(
                asList(
                    sykepengesoknad.getSporsmalMedTag(PERIODER).toBuilder()
                        .svar(
                            listOf(
                                Svar(
                                    null,
                                    "{\"fom\":\"" + sykepengesoknad.fom!!.plusDays(2).format(ISO_LOCAL_DATE) +
                                        "\",\"tom\":\"" + sykepengesoknad.fom!!.plusDays(4)
                                        .format(ISO_LOCAL_DATE) + "\"}",
                                    null
                                )
                            )
                        )
                        .build(),
                    sykepengesoknad.getSporsmalMedTag(UTLANDSOPPHOLD_SOKT_SYKEPENGER).toBuilder()
                        .svar(listOf(Svar(null, "NEI", null)))
                        .build()
                )
            )
            .build()
    }

    private fun andreInntektskilder(sykepengesoknad: Sykepengesoknad): Sporsmal {
        return sykepengesoknad.getSporsmalMedTag(ANDRE_INNTEKTSKILDER).toBuilder()
            .svar(listOf(Svar(null, "JA", null)))
            .undersporsmal(
                listOf(
                    sykepengesoknad.getSporsmalMedTag(HVILKE_ANDRE_INNTEKTSKILDER).toBuilder()
                        .undersporsmal(
                            asList(
                                sykepengesoknad.getSporsmalMedTag(INNTEKTSKILDE_ARBEIDSFORHOLD).toBuilder()
                                    .svar(listOf(Svar(null, "CHECKED", null)))
                                    .undersporsmal(
                                        listOf(
                                            sykepengesoknad.getSporsmalMedTag(INNTEKTSKILDE_ARBEIDSFORHOLD + ER_DU_SYKMELDT)
                                                .toBuilder()
                                                .svar(listOf(Svar(null, "NEI", null)))
                                                .build()
                                        )
                                    )
                                    .build(),
                                sykepengesoknad.getSporsmalMedTag(INNTEKTSKILDE_JORDBRUKER).toBuilder()
                                    .svar(listOf(Svar(null, "CHECKED", null)))
                                    .undersporsmal(
                                        listOf(
                                            sykepengesoknad.getSporsmalMedTag(INNTEKTSKILDE_JORDBRUKER + ER_DU_SYKMELDT)
                                                .toBuilder()
                                                .svar(listOf(Svar(null, "JA", null)))
                                                .build()
                                        )
                                    )
                                    .build(),
                                sykepengesoknad.getSporsmalMedTag(INNTEKTSKILDE_FRILANSER_SELVSTENDIG).toBuilder()
                                    .svar(listOf(Svar(null, "CHECKED", null)))
                                    .undersporsmal(
                                        listOf(
                                            sykepengesoknad.getSporsmalMedTag(INNTEKTSKILDE_FRILANSER_SELVSTENDIG + ER_DU_SYKMELDT)
                                                .toBuilder()
                                                .svar(listOf(Svar(null, "JA", null)))
                                                .build()
                                        )
                                    )
                                    .build(),
                                sykepengesoknad.getSporsmalMedTag(INNTEKTSKILDE_ANNET).toBuilder()
                                    .svar(listOf(Svar(null, "CHECKED", null)))
                                    .build()
                            )
                        )
                        .build()
                )
            )
            .build()
    }

    private fun jobbetDuGradert(sykepengesoknad: Sykepengesoknad): Sporsmal {
        return sykepengesoknad.getSporsmalMedTag(JOBBET_DU_GRADERT + "1").toBuilder()
            .svar(listOf(Svar(null, "NEI", null)))
            .build()
    }

    private fun jobbetDu100Prosent(sykepengesoknad: Sykepengesoknad): Sporsmal {
        return sykepengesoknad.getSporsmalMedTag(JOBBET_DU_100_PROSENT + "0").toBuilder()
            .svar(listOf(Svar(null, "NEI", null)))
            .build()
    }

    private fun tilbakeIArbeid(sykepengesoknad: Sykepengesoknad): Sporsmal {
        return sykepengesoknad.getSporsmalMedTag(TILBAKE_I_ARBEID).toBuilder()
            .svar(listOf(Svar(null, "JA", null)))
            .undersporsmal(
                listOf(
                    sykepengesoknad.getSporsmalMedTag(TILBAKE_NAR).toBuilder()
                        .svar(listOf(Svar(null, sykepengesoknad.fom!!.plusDays(7).format(ISO_LOCAL_DATE), null)))
                        .build()
                )
            )
            .build()
    }

    private fun ansvarserklaring(sykepengesoknad: Sykepengesoknad): Sporsmal {
        return sykepengesoknad.getSporsmalMedTag(ANSVARSERKLARING).toBuilder()
            .svar(listOf(Svar(null, "CHECKED", null)))
            .build()
    }
}
