package no.nav.syfo.sykmelding.kafka.model.sykmelding.arbeidsgiver

import java.time.LocalDate
import java.time.OffsetDateTime

data class ArbeidsgiverSykmelding(
    val id: String,
    val mottattTidspunkt: OffsetDateTime,
    val syketilfelleStartDato: LocalDate?,
    val behandletTidspunkt: OffsetDateTime,
    val arbeidsgiver: ArbeidsgiverAGDTO,
    val sykmeldingsperioder: List<SykmeldingsperiodeAGDTO>,
    val prognose: PrognoseAGDTO?,
    val tiltakArbeidsplassen: String?,
    val meldingTilArbeidsgiver: String?,
    val kontaktMedPasient: KontaktMedPasientAGDTO,
    val behandler: BehandlerAGDTO?,
    val egenmeldt: Boolean,
    val papirsykmelding: Boolean,
    val harRedusertArbeidsgiverperiode: Boolean,
    val merknader: List<no.nav.syfo.sykmelding.kafka.model.Merknad>?,
    val utenlandskSykmelding: UtenlandskSykmeldingAGDTO?,
    val signaturDato: OffsetDateTime?,
)
