package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sporsmal.SporsmalBuilder
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Sykepengesoknad
import org.amshove.kluent.`should be`
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.*

class SoknadGenereringTest {

    @Test
    fun `Søknad er første siden 'fom' er før tidligere søknader til samme arbeidsgiver`() {
        val startSykeforloep = LocalDate.of(2023, 1, 1)
        val eksisterendeSoknader = listOf(
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2023, 2, 1),
                tom = LocalDate.of(2023, 2, 1),
                startSykeforlop = startSykeforloep,
                Arbeidssituasjon.ARBEIDSTAKER,
                Soknadstype.ARBEIDSTAKERE
            )
        )

        val soknad = lagSoknad(
            arbeidsgiver = 1,
            fom = LocalDate.of(2023, 1, 1),
            tom = LocalDate.of(2023, 1, 1),
            startSykeforlop = startSykeforloep,
            Arbeidssituasjon.ARBEIDSTAKER,
            Soknadstype.ARBEIDSTAKERE
        )

        erForsteSoknadTilArbeidsgiverIForlop(eksisterendeSoknader, soknad) `should be` true
    }

    @Test
    fun `Søknad er ikke første siden 'fom' er etter tidligere søknader til samme arbeidsgiver`() {
        val startSykeforloep = LocalDate.of(2023, 1, 1)
        val eksisterendeSoknader = listOf(
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2023, 1, 1),
                tom = LocalDate.of(2023, 1, 1),
                startSykeforlop = startSykeforloep,
                Arbeidssituasjon.ARBEIDSTAKER,
                Soknadstype.ARBEIDSTAKERE
            )
        )

        val soknad = lagSoknad(
            arbeidsgiver = 1,
            fom = LocalDate.of(2023, 2, 1),
            tom = LocalDate.of(2023, 2, 1),
            startSykeforlop = startSykeforloep,
            Arbeidssituasjon.ARBEIDSTAKER,
            Soknadstype.ARBEIDSTAKERE
        )

        erForsteSoknadTilArbeidsgiverIForlop(eksisterendeSoknader, soknad) `should be` false
    }

    @Test
    fun `Søknad er første søknad siden tidligere søknad har annen arbeidsgiver`() {
        val startSykeforloep = LocalDate.of(2023, 1, 1)
        val eksisterendeSoknader = listOf(
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2023, 1, 1),
                tom = LocalDate.of(2023, 1, 1),
                startSykeforlop = startSykeforloep,
                Arbeidssituasjon.ARBEIDSTAKER,
                Soknadstype.ARBEIDSTAKERE
            )
        )

        val soknad = lagSoknad(
            arbeidsgiver = 2,
            fom = LocalDate.of(2023, 2, 1),
            tom = LocalDate.of(2023, 2, 1),
            startSykeforlop = startSykeforloep,
            Arbeidssituasjon.ARBEIDSTAKER,
            Soknadstype.ARBEIDSTAKERE
        )

        erForsteSoknadTilArbeidsgiverIForlop(eksisterendeSoknader, soknad) `should be` true
    }

    @Test
    fun `Søknad med arbeidssituasjon NÆRINGSDRIVENDE er første siden 'fom' er før tidligere søknader med samme arbeidssituasjon`() {
        val startSykeforloep = LocalDate.of(2023, 1, 1)
        val eksisterendeSoknader = listOf(
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2023, 2, 1),
                tom = LocalDate.of(2023, 2, 1),
                startSykeforlop = startSykeforloep,
                Arbeidssituasjon.NAERINGSDRIVENDE,
                Soknadstype.SELVSTENDIGE_OG_FRILANSERE
            )
        )

        val soknad = lagSoknad(
            arbeidsgiver = 1,
            fom = LocalDate.of(2023, 1, 1),
            tom = LocalDate.of(2023, 1, 1),
            startSykeforlop = startSykeforloep,
            Arbeidssituasjon.NAERINGSDRIVENDE,
            Soknadstype.SELVSTENDIGE_OG_FRILANSERE
        )

        erForsteSoknadTilArbeidsgiverIForlop(eksisterendeSoknader, soknad) `should be` true
    }

    @Test
    fun `Søknad med arbeidssituasjon NÆRINGSDRIVENDE er ikke første siden siden 'fom' er etter tidligere søknader med samme arbeidssituasjon`() {
        val startSykeforloep = LocalDate.of(2023, 1, 1)
        val eksisterendeSoknader = listOf(
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2023, 1, 1),
                tom = LocalDate.of(2023, 1, 1),
                startSykeforlop = startSykeforloep,
                Arbeidssituasjon.NAERINGSDRIVENDE,
                Soknadstype.SELVSTENDIGE_OG_FRILANSERE
            )
        )

        val soknad = lagSoknad(
            arbeidsgiver = 1,
            fom = LocalDate.of(2023, 2, 1),
            tom = LocalDate.of(2023, 2, 1),
            startSykeforlop = startSykeforloep,
            Arbeidssituasjon.NAERINGSDRIVENDE,
            Soknadstype.SELVSTENDIGE_OG_FRILANSERE
        )

        erForsteSoknadTilArbeidsgiverIForlop(eksisterendeSoknader, soknad) `should be` false
    }

    @Test
    fun `Søknad er første siden tidligere søknad mangler 'fom'`() {
        val startSykeforloep = LocalDate.of(2023, 1, 1)
        val eksisterendeSoknader = listOf(
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2023, 2, 1),
                tom = LocalDate.of(2023, 2, 1),
                startSykeforlop = startSykeforloep,
                Arbeidssituasjon.ARBEIDSTAKER,
                Soknadstype.ARBEIDSTAKERE
            ).copy(fom = null)
        )

        val soknad = lagSoknad(
            arbeidsgiver = 1,
            fom = LocalDate.of(2023, 1, 1),
            tom = LocalDate.of(2023, 1, 1),
            startSykeforlop = startSykeforloep,
            Arbeidssituasjon.ARBEIDSTAKER,
            Soknadstype.ARBEIDSTAKERE
        )

        erForsteSoknadTilArbeidsgiverIForlop(eksisterendeSoknader, soknad) `should be` true
    }

    @Test
    fun `Søknad er første siden tidligere søknad mangler 'sykmeldingId'`() {
        val startSykeforloep = LocalDate.of(2023, 1, 1)
        val eksisterendeSoknader = listOf(
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2023, 1, 1),
                tom = LocalDate.of(2023, 1, 1),
                startSykeforlop = startSykeforloep,
                Arbeidssituasjon.ARBEIDSTAKER,
                Soknadstype.ARBEIDSTAKERE
            ).copy(sykmeldingId = null)
        )

        val soknad = lagSoknad(
            arbeidsgiver = 1,
            fom = LocalDate.of(2023, 2, 1),
            tom = LocalDate.of(2023, 2, 1),
            startSykeforlop = startSykeforloep,
            Arbeidssituasjon.ARBEIDSTAKER,
            Soknadstype.ARBEIDSTAKERE
        )

        erForsteSoknadTilArbeidsgiverIForlop(eksisterendeSoknader, soknad) `should be` true
    }

    @Test
    fun `Søknad er første siden tidligere søknad mangler 'startSykeforlop'`() {
        val startSykeforloep = LocalDate.of(2023, 1, 1)
        val eksisterendeSoknader = listOf(
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2023, 1, 1),
                tom = LocalDate.of(2023, 1, 1),
                startSykeforlop = startSykeforloep,
                Arbeidssituasjon.ARBEIDSTAKER,
                Soknadstype.ARBEIDSTAKERE
            ).copy(startSykeforlop = null)
        )

        val soknad = lagSoknad(
            arbeidsgiver = 1,
            fom = LocalDate.of(2023, 2, 1),
            tom = LocalDate.of(2023, 2, 1),
            startSykeforlop = startSykeforloep,
            Arbeidssituasjon.ARBEIDSTAKER,
            Soknadstype.ARBEIDSTAKERE
        )

        erForsteSoknadTilArbeidsgiverIForlop(eksisterendeSoknader, soknad) `should be` true
    }

    @Test
    fun `Søknad er første siden tidligere søknad til samme arbeidsgiver har forskjellige 'startSykeforloep'`() {
        val startSykeforloep = LocalDate.of(2023, 1, 1)
        val eksisterendeSoknader = listOf(
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2023, 1, 1),
                tom = LocalDate.of(2023, 1, 1),
                startSykeforlop = startSykeforloep.minusDays(1),
                Arbeidssituasjon.ARBEIDSTAKER,
                Soknadstype.ARBEIDSTAKERE
            )
        )

        val soknad = lagSoknad(
            arbeidsgiver = 1,
            fom = LocalDate.of(2023, 2, 1),
            tom = LocalDate.of(2023, 2, 1),
            startSykeforlop = startSykeforloep,
            Arbeidssituasjon.ARBEIDSTAKER,
            Soknadstype.ARBEIDSTAKERE
        )

        erForsteSoknadTilArbeidsgiverIForlop(eksisterendeSoknader, soknad) `should be` true
    }

    @Test
    fun `Søknad er første siden tidligere søknad har annen arbeidssituasjon`() {
        val startSykeforloep = LocalDate.of(2023, 1, 1)
        val eksisterendeSoknader = listOf(
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2023, 1, 1),
                tom = LocalDate.of(2023, 1, 1),
                startSykeforlop = startSykeforloep,
                Arbeidssituasjon.FRILANSER,
                Soknadstype.SELVSTENDIGE_OG_FRILANSERE
            )
        )

        val soknad = lagSoknad(
            arbeidsgiver = 1,
            fom = LocalDate.of(2023, 2, 1),
            tom = LocalDate.of(2023, 2, 1),
            startSykeforlop = startSykeforloep,
            Arbeidssituasjon.NAERINGSDRIVENDE,
            Soknadstype.SELVSTENDIGE_OG_FRILANSERE
        )

        erForsteSoknadTilArbeidsgiverIForlop(eksisterendeSoknader, soknad) `should be` true
    }

    @Test
    fun `Søknad er ikke første da den mangler 'arbeidsgiverOrgnummer'`() {
        val startSykeforloep = LocalDate.of(2023, 1, 1)
        val eksisterendeSoknader = listOf(
            // Tar med søknad med annen arbeidsgiver og tidligere 'fom' for å sikret at vi tester riktig kode.
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2023, 1, 1),
                tom = LocalDate.of(2023, 1, 1),
                startSykeforlop = startSykeforloep,
                Arbeidssituasjon.ARBEIDSTAKER,
                Soknadstype.ARBEIDSTAKERE
            ),
            lagSoknad(
                arbeidsgiver = 2,
                fom = LocalDate.of(2023, 3, 1),
                tom = LocalDate.of(2023, 3, 1),
                startSykeforlop = startSykeforloep,
                Arbeidssituasjon.ARBEIDSTAKER,
                Soknadstype.ARBEIDSTAKERE
            )
        )

        val soknad = lagSoknad(
            arbeidsgiver = 2,
            fom = LocalDate.of(2023, 2, 1),
            tom = LocalDate.of(2023, 2, 1),
            startSykeforlop = startSykeforloep,
            Arbeidssituasjon.ARBEIDSTAKER,
            Soknadstype.ARBEIDSTAKERE
        ).copy(arbeidsgiverOrgnummer = null)
        erForsteSoknadTilArbeidsgiverIForlop(eksisterendeSoknader, soknad) `should be` false
    }

    @Test
    fun `Søknad ikke første siden søknadstype BEHANDLINGSDAGER likestilles med ARBEIDSTAKERE`() {
        val startSykeforloep = LocalDate.of(2023, 1, 1)
        val eksisterendeSoknader = listOf(
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2023, 2, 1),
                tom = LocalDate.of(2023, 2, 1),
                startSykeforlop = startSykeforloep,
                Arbeidssituasjon.ARBEIDSTAKER,
                Soknadstype.BEHANDLINGSDAGER
            )
        )

        val soknad = lagSoknad(
            arbeidsgiver = 1,
            fom = LocalDate.of(2023, 1, 1),
            tom = LocalDate.of(2023, 1, 1),
            startSykeforlop = startSykeforloep,
            Arbeidssituasjon.ARBEIDSTAKER,
            Soknadstype.ARBEIDSTAKERE
        )

        erForsteSoknadTilArbeidsgiverIForlop(eksisterendeSoknader, soknad) `should be` true
    }

    @Test
    fun `Søknad er ikke første siden søknadstype BEHANDLINGSDAGER likestilles med ARBEIDSTAKERE`() {
        val startSykeforloep = LocalDate.of(2023, 1, 1)
        val eksisterendeSoknader = listOf(
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2023, 1, 1),
                tom = LocalDate.of(2023, 1, 1),
                startSykeforlop = startSykeforloep,
                Arbeidssituasjon.ARBEIDSTAKER,
                Soknadstype.BEHANDLINGSDAGER
            )
        )

        val soknad = lagSoknad(
            arbeidsgiver = 1,
            fom = LocalDate.of(2023, 2, 1),
            tom = LocalDate.of(2023, 2, 1),
            startSykeforlop = startSykeforloep,
            Arbeidssituasjon.ARBEIDSTAKER,
            Soknadstype.ARBEIDSTAKERE
        )

        erForsteSoknadTilArbeidsgiverIForlop(eksisterendeSoknader, soknad) `should be` false
    }

    // TODO: Endre når GRADERT_REISETILSKUDD blir behandlet likt ARBEIDSTAKERE og BEHANDLINGSDAGER.
    @Test
    fun `Søknad er ikke første siden arbeidstakere ikke blir sammenligner på grunn av søknadstype GRADERT_REISETILSKUDD`() {
        val startSykeforloep = LocalDate.of(2023, 1, 1)
        val eksisterendeSoknader = listOf(
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2023, 1, 1),
                tom = LocalDate.of(2023, 1, 1),
                startSykeforlop = startSykeforloep,
                Arbeidssituasjon.ARBEIDSTAKER,
                Soknadstype.GRADERT_REISETILSKUDD
            )
        )

        val soknad = lagSoknad(
            arbeidsgiver = 2,
            fom = LocalDate.of(2023, 2, 1),
            tom = LocalDate.of(2023, 2, 1),
            startSykeforlop = startSykeforloep,
            Arbeidssituasjon.ARBEIDSTAKER,
            Soknadstype.ARBEIDSTAKERE
        )

        erForsteSoknadTilArbeidsgiverIForlop(eksisterendeSoknader, soknad) `should be` false
    }

    // .any { sok -> sok.sporsmal.any { it.tag == UTENLANDSK_SYKMELDING_BOSTED } }

    @Test
    fun `Har blitt stilt spørsmål om UTENLANDSK_SYKMELDING_BOSTED i en tidligere søknad i sykeforløpet`() {
        val startSykeforloep = LocalDate.of(2023, 1, 1)
        val eksisterendeSoknader = listOf(
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2023, 1, 1),
                tom = LocalDate.of(2023, 1, 1),
                startSykeforlop = startSykeforloep,
                Arbeidssituasjon.ARBEIDSTAKER,
                Soknadstype.ARBEIDSTAKERE
            ).copy(
                sporsmal = listOf(
                    SporsmalBuilder().id("1").tag(UTENLANDSK_SYKMELDING_BOSTED).svartype(Svartype.JA_NEI).build()
                )
            )
        )

        val soknad = lagSoknad(
            arbeidsgiver = 1,
            fom = LocalDate.of(2023, 2, 1),
            tom = LocalDate.of(2023, 2, 1),
            startSykeforlop = startSykeforloep,
            Arbeidssituasjon.ARBEIDSTAKER,
            Soknadstype.ARBEIDSTAKERE
        )

        harBlittStiltUtlandsSporsmal(eksisterendeSoknader, soknad) `should be` true
    }

    @Test
    fun `Har ikke blitt stilt spørsmål om UTENLANDSK_SYKMELDING_BOSTED i en tidligere søknad i sykeforløpet`() {
        val startSykeforloep = LocalDate.of(2023, 1, 1)
        val eksisterendeSoknader = listOf(
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2023, 1, 1),
                tom = LocalDate.of(2023, 1, 1),
                startSykeforlop = startSykeforloep,
                Arbeidssituasjon.ARBEIDSTAKER,
                Soknadstype.ARBEIDSTAKERE
            )
        )

        val soknad = lagSoknad(
            arbeidsgiver = 1,
            fom = LocalDate.of(2023, 2, 1),
            tom = LocalDate.of(2023, 2, 1),
            startSykeforlop = startSykeforloep,
            Arbeidssituasjon.ARBEIDSTAKER,
            Soknadstype.ARBEIDSTAKERE
        )

        harBlittStiltUtlandsSporsmal(eksisterendeSoknader, soknad) `should be` false
    }

    @Test
    fun `Har blitt stilt spørsmål om UTENLANDSK_SYKMELDING_BOSTED i sykeforløpet men i en senere søknad`() {
        val startSykeforloep = LocalDate.of(2023, 1, 1)
        val eksisterendeSoknader = listOf(
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2023, 2, 1),
                tom = LocalDate.of(2023, 2, 1),
                startSykeforlop = startSykeforloep,
                Arbeidssituasjon.ARBEIDSTAKER,
                Soknadstype.ARBEIDSTAKERE
            ).copy(
                sporsmal = listOf(
                    SporsmalBuilder().id("1").tag(UTENLANDSK_SYKMELDING_BOSTED).svartype(Svartype.JA_NEI).build()
                )
            )
        )

        val soknad = lagSoknad(
            arbeidsgiver = 1,
            fom = LocalDate.of(2023, 1, 1),
            tom = LocalDate.of(2023, 1, 1),
            startSykeforlop = startSykeforloep,
            Arbeidssituasjon.ARBEIDSTAKER,
            Soknadstype.ARBEIDSTAKERE
        )

        harBlittStiltUtlandsSporsmal(eksisterendeSoknader, soknad) `should be` false
    }

    private fun lagSoknad(
        arbeidsgiver: Int,
        fom: LocalDate,
        tom: LocalDate,
        startSykeforlop: LocalDate,
        arbeidsSituasjon: Arbeidssituasjon,
        soknadsType: Soknadstype
    ): Sykepengesoknad {
        return Sykepengesoknad(
            fnr = "fnr",
            id = UUID.randomUUID().toString(),
            sykmeldingId = "uuid-$arbeidsgiver",
            arbeidssituasjon = arbeidsSituasjon,
            arbeidsgiverOrgnummer = "org-$arbeidsgiver",
            startSykeforlop = startSykeforlop,
            fom = fom,
            tom = tom,
            soknadstype = soknadsType,
            status = Soknadstatus.SENDT,
            egenmeldingsdagerFraSykmelding = null,
            utenlandskSykmelding = false,
            opprettet = fom.atStartOfDay().toInstant(ZoneOffset.UTC),
            soknadPerioder = emptyList(),
            sporsmal = emptyList(),
            sykmeldingSkrevet = Instant.now()
        )
    }
}
