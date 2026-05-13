package no.nav.helse.flex.domain.sykmelding

import no.nav.helse.flex.domain.FiskerBlad
import no.nav.helse.flex.domain.Merknad
import no.nav.helse.flex.domain.Periode
import no.nav.helse.flex.domain.Sykmeldingstype
import no.nav.helse.flex.soknadsopprettelse.brukerHarOppgittForsikring
import no.nav.helse.flex.soknadsopprettelse.prettyOrgnavn
import no.nav.helse.flex.soknadsopprettelse.tilMerknader
import no.nav.helse.flex.util.EnumUtil
import no.nav.syfo.sykmelding.kafka.model.ShortNameKafkaDTO
import no.nav.syfo.sykmelding.kafka.model.SykmeldingKafkaMessageDTO
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime

data class SykmeldingTilSoknadOpprettelse(
    val sykmeldingId: String,
    val sykmeldingsperioder: List<Sykmeldingsperiode>,
    val eventTimestamp: OffsetDateTime,
    val behandletTidspunkt: Instant,
    val signaturDato: Instant?,
    val erUtlandskSykmelding: Boolean,
    val brukerHarOppgittForsikring: Boolean,
    val egenmeldt: Boolean,
    val egenmeldingsdagerFraSykmelding: String?,
    val meldingTilNavDagerFraSykmelding: List<Periode>?,
    val fiskerBlad: FiskerBlad?,
    val merknader: List<Merknad>?,
    val arbeidsgiverOrgnummer: String?,
    val arbeidsgiverNavn: String?,
    val tidligereArbeidsgiverOrgnummer: String?,
    val erPapirsykmelding: Boolean,
)

data class Sykmeldingsperiode(
    val fom: LocalDate,
    val tom: LocalDate,
    val type: Sykmeldingstype,
    val gradert: Gradert?,
    val reisetilskudd: Boolean,
)

data class Gradert(
    val grad: Int,
    val reisetilskudd: Boolean,
)

fun SykmeldingKafkaMessageDTO.tilSykmeldingTilSoknadOpprettelse() =
    SykmeldingTilSoknadOpprettelse(
        sykmeldingId = this.sykmelding.id,
        sykmeldingsperioder =
            this.sykmelding.sykmeldingsperioder.map {
                Sykmeldingsperiode(
                    fom = it.fom,
                    tom = it.tom,
                    type =
                        EnumUtil.konverter(
                            Sykmeldingstype::class.java,
                            it.type.name,
                        )!!,
                    gradert = it.gradert?.let { gradert -> Gradert(gradert.grad, gradert.reisetilskudd) },
                    reisetilskudd = it.reisetilskudd,
                )
            },
        eventTimestamp = this.event.timestamp,
        behandletTidspunkt = this.sykmelding.behandletTidspunkt.toInstant(),
        signaturDato = this.sykmelding.signaturDato?.toInstant(),
        erUtlandskSykmelding = this.sykmelding.utenlandskSykmelding != null,
        brukerHarOppgittForsikring = this.brukerHarOppgittForsikring(),
        egenmeldt = this.sykmelding.egenmeldt,
        egenmeldingsdagerFraSykmelding =
            this.event.sporsmals
                ?.firstOrNull { spm ->
                    spm.shortName == ShortNameKafkaDTO.EGENMELDINGSDAGER
                }?.svar,
        meldingTilNavDagerFraSykmelding =
            this.event.brukerSvar
                ?.egenmeldingsperioder
                ?.svar
                ?.map { Periode(it.fom, it.tom) },
        fiskerBlad =
            EnumUtil.konverter(
                FiskerBlad::class.java,
                this.event.brukerSvar
                    ?.fisker
                    ?.blad
                    ?.svar
                    ?.name,
            ),
        merknader = this.sykmelding.merknader?.tilMerknader(),
        arbeidsgiverOrgnummer = this.event.arbeidsgiver?.orgnummer,
        arbeidsgiverNavn =
            this.event.arbeidsgiver
                ?.orgNavn
                ?.prettyOrgnavn(),
        tidligereArbeidsgiverOrgnummer = this.event.tidligereArbeidsgiver?.orgnummer,
        erPapirsykmelding = this.sykmelding.papirsykmelding,
    )
