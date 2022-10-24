package no.nav.helse.flex.aktivering

import io.micrometer.core.instrument.MeterRegistry
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.rest.SoknadMetadata
import no.nav.helse.flex.kafka.producer.SoknadProducer
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.repository.SykepengesoknadRepository
import no.nav.helse.flex.service.IdentService
import no.nav.helse.flex.soknadsopprettelse.AndreArbeidsforholdHenting
import no.nav.helse.flex.soknadsopprettelse.erForsteSoknadTilArbeidsgiverIForlop
import no.nav.helse.flex.soknadsopprettelse.hentTidligsteFomForSykmelding
import no.nav.helse.flex.soknadsopprettelse.prettyOrgnavn
import no.nav.helse.flex.soknadsopprettelse.settOppSoknadAnnetArbeidsforhold
import no.nav.helse.flex.soknadsopprettelse.settOppSoknadArbeidsledig
import no.nav.helse.flex.soknadsopprettelse.settOppSoknadArbeidstaker
import no.nav.helse.flex.soknadsopprettelse.settOppSoknadSelvstendigOgFrilanser
import no.nav.helse.flex.soknadsopprettelse.settOppSykepengesoknadBehandlingsdager
import no.nav.helse.flex.soknadsopprettelse.skapReisetilskuddsoknad
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.system.measureTimeMillis

@Service
@Transactional
class AktiverEnkeltSoknad(
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val soknadProducer: SoknadProducer,
    private val identService: IdentService,
    private val sykepengesoknadRepository: SykepengesoknadRepository,
    private val registry: MeterRegistry,
    private val andreArbeidsforholdHenting: AndreArbeidsforholdHenting,
    @Value("\${ANDRE_INNTEKTSKILDER_V2:false}") var andreInntektskilderV2: Boolean,
) {
    val log = logger()

    fun aktiverSoknad(id: String) {

        log.info("Forsøker å aktivere soknad $id")

        val sok = sykepengesoknadRepository.findBySykepengesoknadUuid(id)

        if (sok == null) {
            log.warn("Søknad $id mangler fra databasen")
            throw RuntimeException("Søknad $id mangler fra databasen")
        }

        if (sok.status != Soknadstatus.FREMTIDIG) {
            log.warn("Søknad $id er allerede aktivert")
            return
        }

        val aktiverTid = measureTimeMillis {
            sykepengesoknadDAO.aktiverSoknad(id)
        }
        val lagSpm = measureTimeMillis {
            lagSporsmalPaSoknad(id)
        }
        val publiserSoknad = measureTimeMillis {

            val soknad = sykepengesoknadDAO.finnSykepengesoknad(id)

            when (soknad.soknadstype) {
                Soknadstype.OPPHOLD_UTLAND -> throw IllegalArgumentException("Søknad med type ${soknad.soknadstype.name} kan ikke aktiveres")
                else -> soknadProducer.soknadEvent(soknad)
            }
        }
        log.info("Aktiverte søknad med id $id - Aktiver: $aktiverTid Spm: $lagSpm Kafka: $publiserSoknad")
        registry.counter("aktiverte_sykepengesoknader").increment()
    }

    private fun lagSporsmalPaSoknad(id: String) {
        val soknad = sykepengesoknadDAO.finnSykepengesoknad(id)
        val soknadMetadata = soknad.tilSoknadMetadata()
        val start = System.currentTimeMillis()

        val identer = identService.hentFolkeregisterIdenterMedHistorikkForFnr(soknad.fnr)
        val slutt = System.currentTimeMillis()
        log.info("Hentet identer for søknad med id $id - ${slutt - start}ms")

        val andreSoknader = sykepengesoknadDAO.finnSykepengesoknader(identer).filterNot { it.id == soknad.id }

        val sporsmal = genererSykepengesoknadSporsmal(soknadMetadata, andreSoknader)

        sykepengesoknadDAO.byttUtSporsmal(soknad.copy(sporsmal = sporsmal))
    }

    fun genererSykepengesoknadSporsmal(
        soknadMetadata: SoknadMetadata,
        eksisterendeSoknader: List<Sykepengesoknad>,
    ): List<Sporsmal> {

        val tidligsteFomForSykmelding = hentTidligsteFomForSykmelding(soknadMetadata, eksisterendeSoknader)
        val erForsteSoknadISykeforlop = erForsteSoknadTilArbeidsgiverIForlop(eksisterendeSoknader, soknadMetadata)

        val erEnkeltstaendeBehandlingsdagSoknad = soknadMetadata.soknadstype == Soknadstype.BEHANDLINGSDAGER

        if (erEnkeltstaendeBehandlingsdagSoknad) {
            return settOppSykepengesoknadBehandlingsdager(
                soknadMetadata,
                erForsteSoknadISykeforlop,
                tidligsteFomForSykmelding
            )
        }

        val erReisetilskudd = soknadMetadata.soknadstype == Soknadstype.REISETILSKUDD
        if (erReisetilskudd) {
            return skapReisetilskuddsoknad(
                soknadMetadata
            )
        }

        return when (soknadMetadata.arbeidssituasjon) {
            Arbeidssituasjon.ARBEIDSTAKER -> {

                settOppSoknadArbeidstaker(
                    soknadMetadata = soknadMetadata,
                    erForsteSoknadISykeforlop = erForsteSoknadISykeforlop,
                    tidligsteFomForSykmelding = tidligsteFomForSykmelding,
                    andreKjenteArbeidsforhold = if (andreInntektskilderV2) {
                        andreArbeidsforholdHenting.hentArbeidsforhold(soknadMetadata)
                    } else {
                        null
                    }
                )
            }

            Arbeidssituasjon.NAERINGSDRIVENDE, Arbeidssituasjon.FRILANSER -> settOppSoknadSelvstendigOgFrilanser(
                soknadMetadata,
                erForsteSoknadISykeforlop
            )

            Arbeidssituasjon.ARBEIDSLEDIG -> settOppSoknadArbeidsledig(soknadMetadata, erForsteSoknadISykeforlop)
            Arbeidssituasjon.ANNET -> settOppSoknadAnnetArbeidsforhold(soknadMetadata, erForsteSoknadISykeforlop)
        }
    }
}

fun Sykepengesoknad.tilSoknadMetadata(): SoknadMetadata {
    return SoknadMetadata(
        id = this.id,
        fnr = this.fnr,
        startSykeforlop = this.startSykeforlop!!,
        fom = this.fom!!,
        tom = this.tom!!,
        arbeidssituasjon = arbeidssituasjon!!,
        arbeidsgiverOrgnummer = this.arbeidsgiverOrgnummer,
        arbeidsgiverNavn = this.arbeidsgiverNavn?.prettyOrgnavn(),
        sykmeldingId = this.sykmeldingId!!,
        sykmeldingSkrevet = this.sykmeldingSkrevet!!,
        sykmeldingsperioder = this.soknadPerioder!!,
        egenmeldtSykmelding = this.egenmeldtSykmelding,
        merknader = this.merknaderFraSykmelding,
        soknadstype = this.soknadstype
    )
}
