package no.nav.helse.flex.repository

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.util.serialisertTilString
import java.util.*
import kotlin.collections.ArrayList
import kotlin.random.Random

class UUIDProducer(seed: String) {
    private val random = Random(UUID.fromString(seed).mostSignificantBits)

    fun next(): String = UUID.nameUUIDFromBytes(random.nextBytes(16)).toString()
}

fun Sykepengesoknad.normaliser(): NormalisertSoknad {
    val uuidGenerator = UUIDProducer(this.id)

    val sykepengesoknadDbId = uuidGenerator.next()
    val soknad =
        SykepengesoknadDbRecord(
            id = sykepengesoknadDbId,
            sykepengesoknadUuid = id,
            fnr = fnr,
            soknadstype = soknadstype,
            status = status,
            opprettet = opprettet,
            avbruttDato = avbruttDato,
            sendtNav = sendtNav,
            korrigerer = korrigerer,
            korrigertAv = korrigertAv,
            opprinnelse = opprinnelse,
            avsendertype = avsendertype,
            sykmeldingUuid = sykmeldingId,
            fom = fom,
            tom = tom,
            startSykeforlop = startSykeforlop,
            sykmeldingSkrevet = sykmeldingSkrevet,
            sykmeldingSignaturDato = sykmeldingSignaturDato,
            sendtArbeidsgiver = sendtArbeidsgiver,
            arbeidsgiverOrgnummer = arbeidsgiverOrgnummer,
            arbeidsgiverNavn = arbeidsgiverNavn,
            arbeidssituasjon = arbeidssituasjon,
            egenmeldtSykmelding = egenmeldtSykmelding,
            merknaderFraSykmelding = merknaderFraSykmelding?.serialisertTilString(),
            avbruttFeilinfo = avbruttFeilinfo,
            opprettetAvInntektsmelding = opprettetAvInntektsmelding,
            utenlandskSykmelding = utenlandskSykmelding,
            sendt = sendt,
            egenmeldingsdagerFraSykmelding = egenmeldingsdagerFraSykmelding,
            forstegangssoknad = forstegangssoknad,
            tidligereArbeidsgiverOrgnummer = tidligereArbeidsgiverOrgnummer,
            aktivertDato = aktivertDato,
            fiskerBlad = fiskerBlad,
        )
    val perioder =
        this.soknadPerioder
            ?.map {
                SoknadsperiodeDbRecord(
                    id = uuidGenerator.next(),
                    sykepengesoknadId = sykepengesoknadDbId,
                    fom = it.fom,
                    tom = it.tom,
                    grad = it.grad,
                    sykmeldingstype = it.sykmeldingstype,
                )
            }

    val sporsmal = ArrayList<SporsmalDbRecord>()
    val svar = ArrayList<SvarDbRecord>()

    fun lagreSporsmal(
        s: Sporsmal,
        underSporsmalId: String?,
    ) {
        val spmDbRecord =
            SporsmalDbRecord(
                id = uuidGenerator.next(),
                sykepengesoknadId = sykepengesoknadDbId,
                underSporsmalId = underSporsmalId,
                tag = s.tag,
                sporsmalstekst = s.sporsmalstekst,
                undertekst = s.undertekst,
                svartype = s.svartype,
                max = s.max,
                min = s.min,
                kriterieForVisningAvUndersporsmal = s.kriterieForVisningAvUndersporsmal,
            )
        sporsmal.add(spmDbRecord)
        s.svar.forEach { sv ->
            svar.add(SvarDbRecord(id = uuidGenerator.next(), sporsmalId = spmDbRecord.id, verdi = sv.verdi))
        }
        s.undersporsmal.forEach {
            lagreSporsmal(it, spmDbRecord.id)
        }
    }

    this.sporsmal.forEach { s -> lagreSporsmal(s, null) }

    return NormalisertSoknad(
        soknad = soknad,
        perioder = perioder ?: emptyList(),
        svar = svar,
        sporsmal = sporsmal,
    )
}

data class NormalisertSoknad(
    val soknad: SykepengesoknadDbRecord,
    val sporsmal: List<SporsmalDbRecord>,
    val perioder: List<SoknadsperiodeDbRecord>,
    val svar: List<SvarDbRecord>,
)
