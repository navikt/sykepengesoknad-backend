package no.nav.helse.flex.fakes

import no.nav.helse.flex.domain.Soknadsperiode
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svar
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.repository.NormalisertSoknad
import java.util.*

/**
 * Denne funksjonen gjør det motsatte av normaliseringen – den bygger et domene‑objekt
 * fra de "flatlagte" DB‑postene.
 */
fun NormalisertSoknad.denormaliser(): Sykepengesoknad {
    val dbSoknad = this.soknad

    // Konverter DB‑poster for perioder til domeneobjekter.
    val perioder =
        this.perioder.map { periodeDb ->
            Soknadsperiode(
                fom = periodeDb.fom,
                tom = periodeDb.tom,
                grad = periodeDb.grad,
                sykmeldingstype = periodeDb.sykmeldingstype,
            )
        }

    // Bygg en midlertidig mappe (id -> Sporsmal) for alle sporsmalene.
    // Her antar vi at domeneklassen Sporsmal har feltene:
    // tag, sporsmalstekst, undertekst, svartype, max, min, kriterieForVisningAvUndersporsmal, metadata,
    // svar (liste over Svar) og undersporsmal (liste over Sporsmal).
    val sporsmalMap: MutableMap<String, Sporsmal> =
        this.sporsmal.associate { spmDb ->
            spmDb.id to
                Sporsmal(
                    id = spmDb.id,
                    tag = spmDb.tag,
                    sporsmalstekst = spmDb.sporsmalstekst,
                    undertekst = spmDb.undertekst,
                    svartype = spmDb.svartype,
                    max = spmDb.max,
                    min = spmDb.min,
                    kriterieForVisningAvUndersporsmal = spmDb.kriterieForVisningAvUndersporsmal,
                    // TODO Her må du implementere deserialiseringen etter eget behov:
                    metadata = null,
                    svar = mutableListOf(),
                    undersporsmal = mutableListOf(),
                )
        }.toMutableMap()

    // Legg til svar for hvert sporsmal.
// Vi går gjennom alle svar‑poster og kobler dem til riktig sporsmal.
    this.svar.forEach { svarDb ->
        (sporsmalMap[svarDb.sporsmalId]?.svar as MutableList<Svar>).add(
            Svar(
                verdi = svarDb.verdi,
                id = svarDb.id,
            ),
        )
    }

// Bygg opp den hierarkiske strukturen for sporsmalene.
// Dersom en DB‑post for sporsmal har en ikke-null underSporsmalId, skal den plasseres
// som et underspørsmål til sporsmålet med den ID-en.
    val topLevelSporsmal = mutableListOf<Sporsmal>()
    this.sporsmal.forEach { spmDb ->
        val spm = sporsmalMap[spmDb.id]!!
        if (spmDb.underSporsmalId != null) {
            (sporsmalMap[spmDb.underSporsmalId]?.undersporsmal as MutableList<Sporsmal>).add(spm)
        } else {
            topLevelSporsmal.add(spm)
        }
    }

    // Konstruer det originale Sykepengesoknad‑domenet.
    // Her hentes den opprinnelige søknads-IDen fra feltet sykepengesoknadUuid.
    return Sykepengesoknad(
        id = dbSoknad.sykepengesoknadUuid,
        fnr = dbSoknad.fnr,
        soknadstype = dbSoknad.soknadstype,
        status = dbSoknad.status,
        opprettet = dbSoknad.opprettet,
        avbruttDato = dbSoknad.avbruttDato,
        sendtNav = dbSoknad.sendtNav,
        korrigerer = dbSoknad.korrigerer,
        korrigertAv = dbSoknad.korrigertAv,
        opprinnelse = dbSoknad.opprinnelse,
        avsendertype = dbSoknad.avsendertype,
        sykmeldingId = dbSoknad.sykmeldingUuid,
        fom = dbSoknad.fom,
        tom = dbSoknad.tom,
        startSykeforlop = dbSoknad.startSykeforlop,
        sykmeldingSkrevet = dbSoknad.sykmeldingSkrevet,
        sykmeldingSignaturDato = dbSoknad.sykmeldingSignaturDato,
        sendtArbeidsgiver = dbSoknad.sendtArbeidsgiver,
        arbeidsgiverOrgnummer = dbSoknad.arbeidsgiverOrgnummer,
        arbeidsgiverNavn = dbSoknad.arbeidsgiverNavn,
        arbeidssituasjon = dbSoknad.arbeidssituasjon,
        egenmeldtSykmelding = dbSoknad.egenmeldtSykmelding,
        // Husk å deserialisere feltene dersom de er lagret som serialiserte strenger:
        // TODO Her må du implementere deserialiseringen etter eget behov:
        merknaderFraSykmelding = emptyList(),
        opprettetAvInntektsmelding = dbSoknad.opprettetAvInntektsmelding,
        utenlandskSykmelding = dbSoknad.utenlandskSykmelding,
        sendt = dbSoknad.sendt,
        egenmeldingsdagerFraSykmelding = dbSoknad.egenmeldingsdagerFraSykmelding,
        forstegangssoknad = dbSoknad.forstegangssoknad,
        tidligereArbeidsgiverOrgnummer = dbSoknad.tidligereArbeidsgiverOrgnummer,
        aktivertDato = dbSoknad.aktivertDato,
        fiskerBlad = dbSoknad.fiskerBlad,
        // TODO Her må du implementere deserialiseringen etter eget behov:
        arbeidsforholdFraAareg = emptyList(),
        friskTilArbeidVedtakId = dbSoknad.friskTilArbeidVedtakId,
        soknadPerioder = perioder,
        sporsmal = topLevelSporsmal,
    )
}
