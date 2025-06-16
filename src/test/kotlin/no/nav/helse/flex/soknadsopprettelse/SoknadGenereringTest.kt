package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.domain.*
import org.amshove.kluent.`should be`
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.*

class SoknadGenereringTest {
    @Test
    fun `Søknad er første til arbeidsgiver siden 'fom' er før tidligere søknader`() {
        val startSykeforloep = LocalDate.of(2023, 1, 1)
        val eksisterendeSoknader =
            listOf(
                lagSoknad(
                    arbeidsgiver = 1,
                    fom = LocalDate.of(2023, 2, 1),
                    tom = LocalDate.of(2023, 2, 1),
                    startSykeforlop = startSykeforloep,
                    arbeidsSituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                    soknadsType = Soknadstype.ARBEIDSTAKERE,
                ),
            )

        val soknad =
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2023, 1, 1),
                tom = LocalDate.of(2023, 1, 1),
                startSykeforlop = startSykeforloep,
                arbeidsSituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                soknadsType = Soknadstype.ARBEIDSTAKERE,
            )

        erForsteSoknadTilArbeidsgiverIForlop(eksisterendeSoknader, soknad) `should be` true
    }

    @Test
    fun `Søknad er ikke første til arbeidsgiver siden 'fom' er etter tidligere søknader`() {
        val startSykeforloep = LocalDate.of(2023, 1, 1)
        val eksisterendeSoknader =
            listOf(
                lagSoknad(
                    arbeidsgiver = 1,
                    fom = LocalDate.of(2023, 1, 1),
                    tom = LocalDate.of(2023, 1, 1),
                    startSykeforlop = startSykeforloep,
                    arbeidsSituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                    soknadsType = Soknadstype.ARBEIDSTAKERE,
                ),
            )

        val soknad =
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2023, 2, 1),
                tom = LocalDate.of(2023, 2, 1),
                startSykeforlop = startSykeforloep,
                arbeidsSituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                soknadsType = Soknadstype.ARBEIDSTAKERE,
            )

        erForsteSoknadTilArbeidsgiverIForlop(eksisterendeSoknader, soknad) `should be` false
    }

    @Test
    fun `Søknad er første søknad til arbeidsgiver siden tidligere søknad har annen arbeidsgiver`() {
        val startSykeforloep = LocalDate.of(2023, 1, 1)
        val eksisterendeSoknader =
            listOf(
                lagSoknad(
                    arbeidsgiver = 1,
                    fom = LocalDate.of(2023, 1, 1),
                    tom = LocalDate.of(2023, 1, 1),
                    startSykeforlop = startSykeforloep,
                    arbeidsSituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                    soknadsType = Soknadstype.ARBEIDSTAKERE,
                ),
            )

        val soknad =
            lagSoknad(
                arbeidsgiver = 2,
                fom = LocalDate.of(2023, 2, 1),
                tom = LocalDate.of(2023, 2, 1),
                startSykeforlop = startSykeforloep,
                arbeidsSituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                soknadsType = Soknadstype.ARBEIDSTAKERE,
            )

        erForsteSoknadTilArbeidsgiverIForlop(eksisterendeSoknader, soknad) `should be` true
    }

    @Test
    fun `NÆRINGSDRIVENDE er første til arbeidsgiver da 'fom' er før tidligere søknader`() {
        val startSykeforloep = LocalDate.of(2023, 1, 1)
        val eksisterendeSoknader =
            listOf(
                lagSoknad(
                    arbeidsgiver = 1,
                    fom = LocalDate.of(2023, 2, 1),
                    tom = LocalDate.of(2023, 2, 1),
                    startSykeforlop = startSykeforloep,
                    arbeidsSituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                    soknadsType = Soknadstype.SELVSTENDIGE_OG_FRILANSERE,
                ),
            )

        val soknad =
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2023, 1, 1),
                tom = LocalDate.of(2023, 1, 1),
                startSykeforlop = startSykeforloep,
                arbeidsSituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                soknadsType = Soknadstype.SELVSTENDIGE_OG_FRILANSERE,
            )

        erForsteSoknadTilArbeidsgiverIForlop(eksisterendeSoknader, soknad) `should be` true
    }

    @Test
    fun `NÆRINGSDRIVENDE er ikke første siden siden 'fom' er etter tidligere søknader med samme arbeidssituasjon`() {
        val startSykeforloep = LocalDate.of(2023, 1, 1)
        val eksisterendeSoknader =
            listOf(
                lagSoknad(
                    arbeidsgiver = 1,
                    fom = LocalDate.of(2023, 1, 1),
                    tom = LocalDate.of(2023, 1, 1),
                    startSykeforlop = startSykeforloep,
                    arbeidsSituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                    soknadsType = Soknadstype.SELVSTENDIGE_OG_FRILANSERE,
                ),
            )

        val soknad =
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2023, 2, 1),
                tom = LocalDate.of(2023, 2, 1),
                startSykeforlop = startSykeforloep,
                arbeidsSituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                soknadsType = Soknadstype.SELVSTENDIGE_OG_FRILANSERE,
            )

        erForsteSoknadTilArbeidsgiverIForlop(eksisterendeSoknader, soknad) `should be` false
    }

    @Test
    fun `Søknad er første til arbeidsgiver siden tidligere søknad mangler 'fom'`() {
        val startSykeforloep = LocalDate.of(2023, 1, 1)
        val eksisterendeSoknader =
            listOf(
                lagSoknad(
                    arbeidsgiver = 1,
                    fom = LocalDate.of(2023, 2, 1),
                    tom = LocalDate.of(2023, 2, 1),
                    startSykeforlop = startSykeforloep,
                    arbeidsSituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                    soknadsType = Soknadstype.ARBEIDSTAKERE,
                ).copy(fom = null),
            )

        val soknad =
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2023, 1, 1),
                tom = LocalDate.of(2023, 1, 1),
                startSykeforlop = startSykeforloep,
                arbeidsSituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                soknadsType = Soknadstype.ARBEIDSTAKERE,
            )

        erForsteSoknadTilArbeidsgiverIForlop(eksisterendeSoknader, soknad) `should be` true
    }

    @Test
    fun `Søknad er første til arbeidsgiver siden tidligere søknad mangler 'sykmeldingId'`() {
        val startSykeforloep = LocalDate.of(2023, 1, 1)
        val eksisterendeSoknader =
            listOf(
                lagSoknad(
                    arbeidsgiver = 1,
                    fom = LocalDate.of(2023, 1, 1),
                    tom = LocalDate.of(2023, 1, 1),
                    startSykeforlop = startSykeforloep,
                    arbeidsSituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                    soknadsType = Soknadstype.ARBEIDSTAKERE,
                ).copy(sykmeldingId = null),
            )

        val soknad =
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2023, 2, 1),
                tom = LocalDate.of(2023, 2, 1),
                startSykeforlop = startSykeforloep,
                arbeidsSituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                soknadsType = Soknadstype.ARBEIDSTAKERE,
            )

        erForsteSoknadTilArbeidsgiverIForlop(eksisterendeSoknader, soknad) `should be` true
    }

    @Test
    fun `Søknad er første til arbeidsgiver siden tidligere søknad mangler 'startSykeforlop'`() {
        val startSykeforloep = LocalDate.of(2023, 1, 1)
        val eksisterendeSoknader =
            listOf(
                lagSoknad(
                    arbeidsgiver = 1,
                    fom = LocalDate.of(2023, 1, 1),
                    tom = LocalDate.of(2023, 1, 1),
                    startSykeforlop = startSykeforloep,
                    arbeidsSituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                    soknadsType = Soknadstype.ARBEIDSTAKERE,
                ).copy(startSykeforlop = null),
            )

        val soknad =
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2023, 2, 1),
                tom = LocalDate.of(2023, 2, 1),
                startSykeforlop = startSykeforloep,
                arbeidsSituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                soknadsType = Soknadstype.ARBEIDSTAKERE,
            )

        erForsteSoknadTilArbeidsgiverIForlop(eksisterendeSoknader, soknad) `should be` true
    }

    @Test
    fun `Søknad er første til arbeidsgiver siden tidligere søknad har forskjellige 'startSykeforloep'`() {
        val startSykeforloep = LocalDate.of(2023, 1, 1)
        val eksisterendeSoknader =
            listOf(
                lagSoknad(
                    arbeidsgiver = 1,
                    fom = LocalDate.of(2023, 1, 1),
                    tom = LocalDate.of(2023, 1, 1),
                    startSykeforlop = startSykeforloep.minusDays(1),
                    arbeidsSituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                    soknadsType = Soknadstype.ARBEIDSTAKERE,
                ),
            )

        val soknad =
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2023, 2, 1),
                tom = LocalDate.of(2023, 2, 1),
                startSykeforlop = startSykeforloep,
                arbeidsSituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                soknadsType = Soknadstype.ARBEIDSTAKERE,
            )

        erForsteSoknadTilArbeidsgiverIForlop(eksisterendeSoknader, soknad) `should be` true
    }

    @Test
    fun `Søknad er første til arbeidsgiver siden tidligere søknad har annen arbeidssituasjon`() {
        val startSykeforloep = LocalDate.of(2023, 1, 1)
        val eksisterendeSoknader =
            listOf(
                lagSoknad(
                    arbeidsgiver = 1,
                    fom = LocalDate.of(2023, 1, 1),
                    tom = LocalDate.of(2023, 1, 1),
                    startSykeforlop = startSykeforloep,
                    arbeidsSituasjon = Arbeidssituasjon.FRILANSER,
                    soknadsType = Soknadstype.SELVSTENDIGE_OG_FRILANSERE,
                ),
            )

        val soknad =
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2023, 2, 1),
                tom = LocalDate.of(2023, 2, 1),
                startSykeforlop = startSykeforloep,
                arbeidsSituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                soknadsType = Soknadstype.SELVSTENDIGE_OG_FRILANSERE,
            )

        erForsteSoknadTilArbeidsgiverIForlop(eksisterendeSoknader, soknad) `should be` true
    }

    @Test
    fun `Søknad er ikke første til arbeidsgiver da den mangler 'arbeidsgiverOrgnummer'`() {
        val startSykeforloep = LocalDate.of(2023, 1, 1)
        val eksisterendeSoknader =
            listOf(
                // Tar med søknad med annen arbeidsgiver og tidligere 'fom' for å sikret at vi tester riktig kode.
                lagSoknad(
                    arbeidsgiver = 1,
                    fom = LocalDate.of(2023, 1, 1),
                    tom = LocalDate.of(2023, 1, 1),
                    startSykeforlop = startSykeforloep,
                    arbeidsSituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                    soknadsType = Soknadstype.ARBEIDSTAKERE,
                ),
                lagSoknad(
                    arbeidsgiver = 2,
                    fom = LocalDate.of(2023, 3, 1),
                    tom = LocalDate.of(2023, 3, 1),
                    startSykeforlop = startSykeforloep,
                    arbeidsSituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                    soknadsType = Soknadstype.ARBEIDSTAKERE,
                ),
            )

        val soknad =
            lagSoknad(
                arbeidsgiver = 2,
                fom = LocalDate.of(2023, 2, 1),
                tom = LocalDate.of(2023, 2, 1),
                startSykeforlop = startSykeforloep,
                arbeidsSituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                soknadsType = Soknadstype.ARBEIDSTAKERE,
            ).copy(arbeidsgiverOrgnummer = null)
        erForsteSoknadTilArbeidsgiverIForlop(eksisterendeSoknader, soknad) `should be` false
    }

    @Test
    fun `Søknad ikke første til arbeidsgiver siden søknadstype BEHANDLINGSDAGER likestilles med ARBEIDSTAKERE`() {
        val startSykeforloep = LocalDate.of(2023, 1, 1)
        val eksisterendeSoknader =
            listOf(
                lagSoknad(
                    arbeidsgiver = 1,
                    fom = LocalDate.of(2023, 2, 1),
                    tom = LocalDate.of(2023, 2, 1),
                    startSykeforlop = startSykeforloep,
                    arbeidsSituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                    Soknadstype.BEHANDLINGSDAGER,
                ),
            )

        val soknad =
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2023, 1, 1),
                tom = LocalDate.of(2023, 1, 1),
                startSykeforlop = startSykeforloep,
                arbeidsSituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                soknadsType = Soknadstype.ARBEIDSTAKERE,
            )

        erForsteSoknadTilArbeidsgiverIForlop(eksisterendeSoknader, soknad) `should be` true
    }

    @Test
    fun `Søknad er ikke første til arbeidsgiver siden søknadstype BEHANDLINGSDAGER likestilles med ARBEIDSTAKERE`() {
        val startSykeforloep = LocalDate.of(2023, 1, 1)
        val eksisterendeSoknader =
            listOf(
                lagSoknad(
                    arbeidsgiver = 1,
                    fom = LocalDate.of(2023, 1, 1),
                    tom = LocalDate.of(2023, 1, 1),
                    startSykeforlop = startSykeforloep,
                    arbeidsSituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                    soknadsType = Soknadstype.BEHANDLINGSDAGER,
                ),
            )

        val soknad =
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2023, 2, 1),
                tom = LocalDate.of(2023, 2, 1),
                startSykeforlop = startSykeforloep,
                arbeidsSituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                soknadsType = Soknadstype.ARBEIDSTAKERE,
            )

        erForsteSoknadTilArbeidsgiverIForlop(eksisterendeSoknader, soknad) `should be` false
    }

    @Test
    fun `Søknad er første til arbeidsgiver siden søknadstype GRADERT_REISETILSKUDD likestilles`() {
        val startSykeforloep = LocalDate.of(2023, 1, 1)
        val eksisterendeSoknader =
            listOf(
                lagSoknad(
                    arbeidsgiver = 1,
                    fom = LocalDate.of(2023, 1, 1),
                    tom = LocalDate.of(2023, 1, 1),
                    startSykeforlop = startSykeforloep,
                    arbeidsSituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                    soknadsType = Soknadstype.GRADERT_REISETILSKUDD,
                ),
            )

        val soknad =
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2023, 2, 1),
                tom = LocalDate.of(2023, 2, 1),
                startSykeforlop = startSykeforloep,
                arbeidsSituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                soknadsType = Soknadstype.ARBEIDSTAKERE,
            )

        erForsteSoknadTilArbeidsgiverIForlop(eksisterendeSoknader, soknad) `should be` false
    }

    @Test
    fun `Har blitt stilt spørsmål om UTENLANDSK_SYKMELDING_BOSTED i en tidligere søknad i sykeforløpet`() {
        val startSykeforloep = LocalDate.of(2023, 1, 1)
        val eksisterendeSoknader =
            listOf(
                lagSoknad(
                    arbeidsgiver = 1,
                    fom = LocalDate.of(2023, 1, 1),
                    tom = LocalDate.of(2023, 1, 1),
                    startSykeforlop = startSykeforloep,
                    arbeidsSituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                    soknadsType = Soknadstype.ARBEIDSTAKERE,
                ).copy(
                    sporsmal =
                        listOf(
                            Sporsmal(
                                id = ("1"),
                                tag = (UTENLANDSK_SYKMELDING_BOSTED),
                                svartype = (Svartype.JA_NEI),
                            ),
                        ),
                ),
            )

        val soknad =
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2023, 2, 1),
                tom = LocalDate.of(2023, 2, 1),
                startSykeforlop = startSykeforloep,
                arbeidsSituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                soknadsType = Soknadstype.ARBEIDSTAKERE,
            )

        harBlittStiltUtlandsSporsmal(eksisterendeSoknader, soknad) `should be` true
    }

    @Test
    fun `Har ikke blitt stilt spørsmål om UTENLANDSK_SYKMELDING_BOSTED i en tidligere søknad i sykeforløpet`() {
        val startSykeforloep = LocalDate.of(2023, 1, 1)
        val eksisterendeSoknader =
            listOf(
                lagSoknad(
                    arbeidsgiver = 1,
                    fom = LocalDate.of(2023, 1, 1),
                    tom = LocalDate.of(2023, 1, 1),
                    startSykeforlop = startSykeforloep,
                    arbeidsSituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                    soknadsType = Soknadstype.ARBEIDSTAKERE,
                ),
            )

        val soknad =
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2023, 2, 1),
                tom = LocalDate.of(2023, 2, 1),
                startSykeforlop = startSykeforloep,
                arbeidsSituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                soknadsType = Soknadstype.ARBEIDSTAKERE,
            )

        harBlittStiltUtlandsSporsmal(eksisterendeSoknader, soknad) `should be` false
    }

    @Test
    fun `Har blitt stilt spørsmål om UTENLANDSK_SYKMELDING_BOSTED i sykeforløpet men i en senere søknad`() {
        val startSykeforloep = LocalDate.of(2023, 1, 1)
        val eksisterendeSoknader =
            listOf(
                lagSoknad(
                    arbeidsgiver = 1,
                    fom = LocalDate.of(2023, 2, 1),
                    tom = LocalDate.of(2023, 2, 1),
                    startSykeforlop = startSykeforloep,
                    arbeidsSituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                    soknadsType = Soknadstype.ARBEIDSTAKERE,
                ).copy(
                    sporsmal =
                        listOf(
                            Sporsmal(
                                id = ("1"),
                                tag = (UTENLANDSK_SYKMELDING_BOSTED),
                                svartype = (Svartype.JA_NEI),
                            ),
                        ),
                ),
            )

        val soknad =
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2023, 1, 1),
                tom = LocalDate.of(2023, 1, 1),
                startSykeforlop = startSykeforloep,
                arbeidsSituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                soknadsType = Soknadstype.ARBEIDSTAKERE,
            )

        harBlittStiltUtlandsSporsmal(eksisterendeSoknader, soknad) `should be` false
    }

    @Test
    fun `Eneste førstegangssøknad skal ha medlemskapspørsmål`() {
        val startSykeforloep = LocalDate.of(2023, 1, 1)

        val soknad =
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2023, 1, 17),
                tom = LocalDate.of(2023, 1, 31),
                startSykeforlop = startSykeforloep,
                arbeidsSituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                soknadsType = Soknadstype.ARBEIDSTAKERE,
            )

        skalHaSporsmalOmMedlemskap(emptyList(), soknad) `should be` true
    }

    @Test
    fun `Påfølgende søknad til samme arbeidsgiver skal ikke ha medlemskapspørsmål`() {
        val startSykeforloep = LocalDate.of(2023, 1, 1)
        val eksisterendeSoknader =
            listOf(
                lagSoknad(
                    arbeidsgiver = 1,
                    fom = LocalDate.of(2023, 1, 1),
                    tom = LocalDate.of(2023, 1, 16),
                    startSykeforlop = startSykeforloep,
                    arbeidsSituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                    soknadsType = Soknadstype.ARBEIDSTAKERE,
                ),
            )

        val soknad =
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2023, 1, 17),
                tom = LocalDate.of(2023, 1, 31),
                startSykeforlop = startSykeforloep,
                arbeidsSituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                soknadsType = Soknadstype.ARBEIDSTAKERE,
            )

        skalHaSporsmalOmMedlemskap(eksisterendeSoknader, soknad) `should be` false
    }

    @Test
    fun `Førstegangssøknad skal ha medlemskapspørsmål siden samtidig førstegangssøknad til annen arbeidsgiver ikke har`() {
        val startSykeforloep = LocalDate.of(2023, 1, 1)
        val eksisterendeSoknader =
            listOf(
                lagSoknad(
                    arbeidsgiver = 1,
                    fom = LocalDate.of(2023, 2, 1),
                    tom = LocalDate.of(2023, 2, 1),
                    startSykeforlop = startSykeforloep,
                    arbeidsSituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                    soknadsType = Soknadstype.ARBEIDSTAKERE,
                ),
            )

        val soknad =
            lagSoknad(
                arbeidsgiver = 2,
                fom = LocalDate.of(2023, 2, 1),
                tom = LocalDate.of(2023, 2, 1),
                startSykeforlop = startSykeforloep,
                arbeidsSituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                soknadsType = Soknadstype.ARBEIDSTAKERE,
            )

        skalHaSporsmalOmMedlemskap(eksisterendeSoknader, soknad) `should be` true
    }

    @Test
    fun `Førstegangssøknad skal ikke ha medlemskapspørsmål når samtidig førstegangssøknad til annen arbeidsgiver har`() {
        val startSykeforloep = LocalDate.of(2023, 1, 1)
        val eksisterendeSoknader =
            listOf(
                lagSoknad(
                    arbeidsgiver = 1,
                    fom = LocalDate.of(2023, 2, 1),
                    tom = LocalDate.of(2023, 2, 1),
                    startSykeforlop = startSykeforloep,
                    arbeidsSituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                    soknadsType = Soknadstype.ARBEIDSTAKERE,
                ).copy(
                    sporsmal =
                        listOf(
                            Sporsmal(
                                id = ("1"),
                                tag = (ARBEID_UTENFOR_NORGE),
                                svartype = (Svartype.JA_NEI),
                            ),
                        ),
                ),
            )

        val soknad =
            lagSoknad(
                arbeidsgiver = 2,
                fom = LocalDate.of(2023, 2, 1),
                tom = LocalDate.of(2023, 2, 10),
                startSykeforlop = startSykeforloep,
                arbeidsSituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                soknadsType = Soknadstype.ARBEIDSTAKERE,
            )

        skalHaSporsmalOmMedlemskap(eksisterendeSoknader, soknad) `should be` false
    }

    @Test
    fun `Førstegangssøknad skal ha medlemskapspørsmål når samtidig førstegangssøknad til annen arbeidsgiver er slettet`() {
        val startSykeforloep = LocalDate.of(2023, 1, 1)
        val eksisterendeSoknader =
            listOf(
                lagSoknad(
                    arbeidsgiver = 1,
                    fom = LocalDate.of(2023, 2, 1),
                    tom = LocalDate.of(2023, 2, 15),
                    startSykeforlop = startSykeforloep,
                    arbeidsSituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                    soknadsType = Soknadstype.ARBEIDSTAKERE,
                ).copy(
                    sporsmal =
                        listOf(
                            Sporsmal(
                                id = ("1"),
                                tag = (ARBEID_UTENFOR_NORGE),
                                svartype = (Svartype.JA_NEI),
                            ),
                        ),
                    status = Soknadstatus.SLETTET,
                ),
            )

        val soknad =
            lagSoknad(
                arbeidsgiver = 2,
                fom = LocalDate.of(2023, 2, 1),
                tom = LocalDate.of(2023, 2, 15),
                startSykeforlop = startSykeforloep,
                arbeidsSituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                soknadsType = Soknadstype.ARBEIDSTAKERE,
            )

        skalHaSporsmalOmMedlemskap(eksisterendeSoknader, soknad) `should be` true
    }

    @Test
    fun `Tilbakedatert søknad skal ha medlemskapspørsmål siden den får et annet startSykeforlop`() {
        val eksisterendeSoknader =
            listOf(
                lagSoknad(
                    arbeidsgiver = 1,
                    fom = LocalDate.of(2023, 2, 11),
                    tom = LocalDate.of(2023, 2, 20),
                    startSykeforlop = LocalDate.of(2023, 2, 11),
                    arbeidsSituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                    soknadsType = Soknadstype.ARBEIDSTAKERE,
                ).copy(
                    sporsmal =
                        listOf(
                            Sporsmal(
                                id = ("1"),
                                tag = (ARBEID_UTENFOR_NORGE),
                                svartype = (Svartype.JA_NEI),
                            ),
                        ),
                ),
            )

        val soknad =
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2023, 2, 1),
                tom = LocalDate.of(2023, 2, 15),
                startSykeforlop = LocalDate.of(2023, 1, 1),
                arbeidsSituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                soknadsType = Soknadstype.ARBEIDSTAKERE,
            )

        skalHaSporsmalOmMedlemskap(eksisterendeSoknader, soknad) `should be` true
    }

    @Test
    fun `Søknad skal ha medlemskapspørsmål selv om annen søknadstype har spørsmål om ARBEID_UTENFOR_NORGE`() {
        val startSykeforlop = LocalDate.of(2025, 5, 14)
        val eksisterendeSoknader =
            listOf(
                lagSoknad(
                    arbeidsgiver = 1,
                    fom = LocalDate.of(2025, 5, 14),
                    tom = LocalDate.of(2025, 6, 2),
                    startSykeforlop = startSykeforlop,
                    arbeidsSituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                    soknadsType = Soknadstype.SELVSTENDIGE_OG_FRILANSERE,
                ).copy(
                    arbeidsgiverOrgnummer = null,
                    arbeidsgiverNavn = null,
                    status = Soknadstatus.SENDT,
                    sporsmal =
                        listOf(
                            Sporsmal(
                                id = ("1"),
                                tag = (ARBEID_UTENFOR_NORGE),
                                svartype = (Svartype.JA_NEI),
                            ),
                        ),
                ),
            )

        val soknad =
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2025, 5, 14),
                tom = LocalDate.of(2025, 6, 2),
                startSykeforlop = startSykeforlop,
                arbeidsSituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                soknadsType = Soknadstype.ARBEIDSTAKERE,
                Soknadstatus.NY,
            )

        skalHaSporsmalOmMedlemskap(eksisterendeSoknader, soknad) `should be` true
    }

    @Test
    fun `Førstegangssøknad skal ha medlemskapspørsmål siden annen søknad med spørsmål tilhører et annet syketilfelle`() {
        val eksisterendeSoknader =
            listOf(
                lagSoknad(
                    arbeidsgiver = 1,
                    fom = LocalDate.of(2023, 2, 1),
                    tom = LocalDate.of(2023, 2, 15),
                    startSykeforlop = LocalDate.of(2023, 1, 1),
                    arbeidsSituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                    soknadsType = Soknadstype.ARBEIDSTAKERE,
                ).copy(
                    sporsmal =
                        listOf(
                            Sporsmal(
                                id = ("1"),
                                tag = (ARBEID_UTENFOR_NORGE),
                                svartype = (Svartype.JA_NEI),
                            ),
                        ),
                ),
            )

        val soknad =
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2023, 3, 10),
                tom = LocalDate.of(2023, 2, 20),
                startSykeforlop = LocalDate.of(2023, 3, 10),
                arbeidsSituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                soknadsType = Soknadstype.ARBEIDSTAKERE,
            )

        skalHaSporsmalOmMedlemskap(eksisterendeSoknader, soknad) `should be` true
    }

    private fun lagSoknad(
        arbeidsgiver: Int,
        fom: LocalDate,
        tom: LocalDate,
        startSykeforlop: LocalDate,
        arbeidsSituasjon: Arbeidssituasjon,
        soknadsType: Soknadstype,
        status: Soknadstatus? = Soknadstatus.SENDT,
    ): Sykepengesoknad =
        Sykepengesoknad(
            fnr = "11111111111",
            id = UUID.randomUUID().toString(),
            sykmeldingId = "uuid-$arbeidsgiver",
            arbeidssituasjon = arbeidsSituasjon,
            arbeidsgiverOrgnummer = "org-$arbeidsgiver",
            startSykeforlop = startSykeforlop,
            fom = fom,
            tom = tom,
            soknadstype = soknadsType,
            status = status!!,
            egenmeldingsdagerFraSykmelding = null,
            utenlandskSykmelding = false,
            opprettet = fom.atStartOfDay().toInstant(ZoneOffset.UTC),
            soknadPerioder = emptyList(),
            sporsmal = emptyList(),
            sykmeldingSkrevet = Instant.now(),
            forstegangssoknad = false,
        )
}
