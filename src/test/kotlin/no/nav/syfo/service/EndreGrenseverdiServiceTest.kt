package no.nav.syfo.service

import no.nav.syfo.BaseTestClass
import no.nav.syfo.controller.domain.sykepengesoknad.RSSykepengesoknad
import no.nav.syfo.domain.Arbeidssituasjon.ANNET
import no.nav.syfo.domain.Arbeidssituasjon.ARBEIDSLEDIG
import no.nav.syfo.domain.Arbeidssituasjon.ARBEIDSTAKER
import no.nav.syfo.domain.Arbeidssituasjon.FRILANSER
import no.nav.syfo.domain.Arbeidssituasjon.NAERINGSDRIVENDE
import no.nav.syfo.domain.Soknadstatus.FREMTIDIG
import no.nav.syfo.domain.Soknadstatus.NY
import no.nav.syfo.domain.Soknadstype
import no.nav.syfo.domain.Sykepengesoknad
import no.nav.syfo.domain.rest.SoknadMetadata
import no.nav.syfo.hentSoknader
import no.nav.syfo.model.sykmelding.arbeidsgiver.AktivitetIkkeMuligAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.GradertDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.repository.SykepengesoknadDAO
import no.nav.syfo.soknadsopprettelse.PERMITTERT_NAA
import no.nav.syfo.soknadsopprettelse.PERMITTERT_NAA_NAR
import no.nav.syfo.soknadsopprettelse.settOppSoknadAnnetArbeidsforhold
import no.nav.syfo.soknadsopprettelse.settOppSoknadArbeidsledig
import no.nav.syfo.soknadsopprettelse.settOppSoknadArbeidstaker
import no.nav.syfo.soknadsopprettelse.settOppSoknadSelvstendigOgFrilanser
import no.nav.syfo.soknadsopprettelse.settOppSykepengesoknadBehandlingsdager
import no.nav.syfo.soknadsopprettelse.tilSoknadsperioder
import no.nav.syfo.util.DatoUtil.datoErInnenforMinMax
import no.nav.syfo.util.tilOsloInstant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

class EndreGrenseverdiServiceTest : BaseTestClass() {

    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    @BeforeEach
    @AfterEach
    fun setUp() {
        databaseReset.resetDatabase()
    }

    final val fnr = "fnr"
    final val aktorid = fnr + "00"
    private val fom = LocalDate.of(2020, 8, 1)
    private val soknadMetadata = SoknadMetadata(
        fnr = fnr,
        status = NY,
        startSykeforlop = fom,
        fom = fom,
        tom = fom.plusDays(10),
        arbeidssituasjon = ARBEIDSTAKER,
        arbeidsgiverNavn = "ARB",
        arbeidsgiverOrgnummer = "222",
        soknadstype = Soknadstype.ARBEIDSTAKERE,
        sykmeldingId = "289148ba-4c3c-4b3f-b7a3-385b7e7c927d",
        sykmeldingSkrevet = fom.atStartOfDay().tilOsloInstant(),
        sykmeldingsperioder = listOf(
            SykmeldingsperiodeAGDTO(
                fom = fom,
                tom = fom.plusDays(10),
                gradert = GradertDTO(grad = 100, reisetilskudd = false),
                type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                behandlingsdager = null,
                innspillTilArbeidsgiver = null,
                reisetilskudd = false,
            ),
        ).tilSoknadsperioder(),
    )

    private fun settFeilGrense(soknad: Sykepengesoknad): Sykepengesoknad {
        return soknad.replaceSporsmal(
            soknad.getSporsmalMedTagOrNull(PERMITTERT_NAA_NAR)!!.copy(
                min = "2020-05-05"
            )
        )
    }

    fun List<RSSykepengesoknad>.alleHarRiktigGrense(): Boolean {
        return this.all {
            val sporsmal = it.getSporsmalMedTagOrNull(PERMITTERT_NAA_NAR)
            if (sporsmal == null) {
                return@all true
            } else {
                return@all datoErInnenforMinMax(LocalDate.of(2020, 2, 1), sporsmal.min, sporsmal.max)
            }
        }
    }

    @Test
    fun `EndreGrenseverdiService - Arbeidtaker NY`() {
        var soknad = settOppSoknadArbeidstaker(
            soknadMetadata,
            true,
            fom,
        )
        soknad = settFeilGrense(soknad)

        sykepengesoknadDAO.lagreSykepengesoknad(soknad)

        val soknader = hentSoknader(fnr)
        assertThat(soknader.size).isEqualTo(1)
        assertThat(soknader.alleHarRiktigGrense()).isTrue()
    }

    @Test
    fun `EndreGrenseverdiService - Arbeidtaker FREMTIDIG`() {
        var soknad = settOppSoknadArbeidstaker(
            soknadMetadata.copy(status = FREMTIDIG),
            true,
            fom,
        )
        soknad = settFeilGrense(soknad)

        sykepengesoknadDAO.lagreSykepengesoknad(soknad)

        val soknader = hentSoknader(fnr)
        assertThat(soknader.size).isEqualTo(1)
        assertThat(soknader.alleHarRiktigGrense()).isTrue()
    }

    @Test
    fun `EndreGrenseverdiService - Arbeidsledig NY`() {
        var soknad = settOppSoknadArbeidsledig(
            soknadMetadata.copy(arbeidssituasjon = ARBEIDSLEDIG),
            true
        )
        soknad = settFeilGrense(soknad)

        sykepengesoknadDAO.lagreSykepengesoknad(soknad)

        val soknader = hentSoknader(fnr)
        assertThat(soknader.size).isEqualTo(1)
        assertThat(soknader.alleHarRiktigGrense()).isTrue()
    }

    @Test
    fun `EndreGrenseverdiService - Arbeidsledig FREMTIDIG`() {
        var soknad = settOppSoknadArbeidsledig(
            soknadMetadata.copy(status = FREMTIDIG, arbeidssituasjon = ARBEIDSLEDIG),
            true
        )
        soknad = settFeilGrense(soknad)

        sykepengesoknadDAO.lagreSykepengesoknad(soknad)

        val soknader = hentSoknader(fnr)
        assertThat(soknader.size).isEqualTo(1)
        assertThat(soknader.alleHarRiktigGrense()).isTrue()
    }

    @Test
    fun `EndreGrenseverdiService - Annet NY`() {
        var soknad = settOppSoknadAnnetArbeidsforhold(
            soknadMetadata.copy(arbeidssituasjon = ANNET),
            true
        )
        soknad = settFeilGrense(soknad)

        sykepengesoknadDAO.lagreSykepengesoknad(soknad)

        val soknader = hentSoknader(fnr)
        assertThat(soknader.size).isEqualTo(1)
        assertThat(soknader.alleHarRiktigGrense()).isTrue()
    }

    @Test
    fun `EndreGrenseverdiService - Annet FREMTIDIG`() {
        var soknad = settOppSoknadAnnetArbeidsforhold(
            soknadMetadata.copy(status = FREMTIDIG, arbeidssituasjon = ANNET),
            true
        )
        soknad = settFeilGrense(soknad)

        sykepengesoknadDAO.lagreSykepengesoknad(soknad)

        val soknader = hentSoknader(fnr)
        assertThat(soknader.size).isEqualTo(1)
        assertThat(soknader.alleHarRiktigGrense()).isTrue()
    }

    @Test
    fun `EndreGrenseverdiService - Ikke behandlingsdager`() {
        val soknad = settOppSykepengesoknadBehandlingsdager(
            soknadMetadata,
            true,
            fom
        )
        sykepengesoknadDAO.lagreSykepengesoknad(soknad)

        val soknader = hentSoknader(fnr)
        assertThat(soknader.size).isEqualTo(1)
        assertThat(
            soknader.all { sok ->
                sok.sporsmal?.find { spm ->
                    spm.tag === PERMITTERT_NAA
                } == null
            }
        ).isTrue()
    }

    @Test
    fun `EndreGrenseverdiService - Ikke frilanser`() {
        val soknad = settOppSoknadSelvstendigOgFrilanser(
            soknadMetadata.copy(arbeidssituasjon = FRILANSER),
            true
        )
        sykepengesoknadDAO.lagreSykepengesoknad(soknad)

        val soknader = hentSoknader(fnr)
        assertThat(soknader.size).isEqualTo(1)
        assertThat(
            soknader.all { sok ->
                sok.sporsmal?.find { spm ->
                    spm.tag === PERMITTERT_NAA
                } == null
            }
        ).isTrue()
    }

    @Test
    fun `EndreGrenseverdiService - Ikke selvstendig`() {
        val soknad = settOppSoknadSelvstendigOgFrilanser(
            soknadMetadata.copy(arbeidssituasjon = NAERINGSDRIVENDE),
            true
        )
        sykepengesoknadDAO.lagreSykepengesoknad(soknad)

        val soknader = hentSoknader(fnr)
        assertThat(soknader.size).isEqualTo(1)
        assertThat(
            soknader.all { sok ->
                sok.sporsmal?.find { spm ->
                    spm.tag === PERMITTERT_NAA
                } == null
            }
        ).isTrue()
    }
}
