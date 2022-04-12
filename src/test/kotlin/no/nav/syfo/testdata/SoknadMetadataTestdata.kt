import no.nav.syfo.domain.Arbeidssituasjon
import no.nav.syfo.domain.Soknadstatus
import no.nav.syfo.domain.Soknadstype
import no.nav.syfo.domain.rest.SoknadMetadata
import no.nav.syfo.model.sykmelding.arbeidsgiver.AktivitetIkkeMuligAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.GradertDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.soknadsopprettelse.tilSoknadsperioder
import no.nav.syfo.util.tilOsloInstant
import java.time.LocalDate
import java.time.LocalDateTime

fun skapSoknadMetadata(
    fnr: String = "12345343432",
    fom: LocalDate = LocalDate.now().minusDays(20),
    tom: LocalDate = LocalDate.now().minusDays(10),
): SoknadMetadata {
    return SoknadMetadata(
        fnr = fnr,
        status = Soknadstatus.NY,
        startSykeforlop = LocalDate.now().minusDays(24),
        fom = fom,
        tom = tom,
        arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
        arbeidsgiverOrgnummer = "123456789",
        arbeidsgiverNavn = "ARBEIDSGIVER A/S",
        sykmeldingId = "289148ba-4c3c-4b3f-b7a3-385b7e7c927d",
        soknadstype = Soknadstype.ARBEIDSTAKERE,
        sykmeldingSkrevet = LocalDateTime.now().minusDays(19).tilOsloInstant(),
        sykmeldingsperioder = listOf(
            SykmeldingsperiodeAGDTO(
                fom = fom,
                tom = LocalDate.now().minusDays(15),
                gradert = GradertDTO(grad = 100, reisetilskudd = false),
                type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                behandlingsdager = null,
                innspillTilArbeidsgiver = null,
                reisetilskudd = false,
            ),
        ).tilSoknadsperioder(),
        egenmeldtSykmelding = null
    )
}
