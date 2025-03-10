package no.nav.helse.flex.fakes

import no.nav.helse.flex.aktivering.RetryRecord
import no.nav.helse.flex.aktivering.RetryRepository
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svar
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.forskuttering.ForskutteringRepository
import no.nav.helse.flex.forskuttering.domain.Forskuttering
import no.nav.helse.flex.repository.*
import no.nav.helse.flex.service.FolkeregisterIdenter
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

@Repository
@Primary
@Profile("fakes")
class JulesoknadkandidatDAOFake : JulesoknadkandidatDAO {
    override fun hentJulesoknadkandidater(): List<JulesoknadkandidatDAO.Julesoknadkandidat> {
        TODO("Not yet implemented")
    }

    override fun lagreJulesoknadkandidat(sykepengesoknadUuid: String) {
        TODO("Not yet implemented")
    }

    override fun slettJulesoknadkandidat(julesoknadkandidatId: String) {
        TODO("Not yet implemented")
    }
}

@Repository
@Primary
@Profile("fakes")
class OppsamlingAvRepositoryFakes : RetryRepository {
    override fun inkrementerRetries(
        id: String,
        now: Instant,
    ): Int {
        TODO("Not yet implemented")
    }

    override fun <S : RetryRecord?> save(entity: S & Any): S & Any {
        TODO("Not yet implemented")
    }

    override fun <S : RetryRecord?> saveAll(entities: MutableIterable<S>): MutableIterable<S> {
        TODO("Not yet implemented")
    }

    override fun findById(id: String): Optional<RetryRecord> {
        TODO("Not yet implemented")
    }

    override fun existsById(id: String): Boolean {
        TODO("Not yet implemented")
    }

    override fun findAll(): MutableIterable<RetryRecord> {
        TODO("Not yet implemented")
    }

    override fun findAllById(ids: MutableIterable<String>): MutableIterable<RetryRecord> {
        TODO("Not yet implemented")
    }

    override fun count(): Long {
        TODO("Not yet implemented")
    }

    override fun deleteById(id: String) {
        TODO("Not yet implemented")
    }

    override fun delete(entity: RetryRecord) {
        TODO("Not yet implemented")
    }

    override fun deleteAllById(ids: MutableIterable<String>) {
        TODO("Not yet implemented")
    }

    override fun deleteAll(entities: MutableIterable<RetryRecord>) {
        TODO("Not yet implemented")
    }

    override fun deleteAll() {
        TODO("Not yet implemented")
    }
}

@Repository
@Primary
@Profile("fakes")
class ForskutteringRepositoryFake : ForskutteringRepository {
    override fun findByNarmesteLederId(narmesteLederId: UUID): Forskuttering? {
        TODO("Not yet implemented")
    }

    override fun finnForskuttering(
        brukerFnr: String,
        orgnummer: String,
    ): Forskuttering? {
        TODO("Not yet implemented")
    }

    override fun <S : Forskuttering?> save(entity: S & Any): S & Any {
        TODO("Not yet implemented")
    }

    override fun <S : Forskuttering?> saveAll(entities: MutableIterable<S>): MutableIterable<S> {
        TODO("Not yet implemented")
    }

    override fun findById(id: String): Optional<Forskuttering> {
        TODO("Not yet implemented")
    }

    override fun existsById(id: String): Boolean {
        TODO("Not yet implemented")
    }

    override fun findAll(): MutableIterable<Forskuttering> {
        TODO("Not yet implemented")
    }

    override fun findAllById(ids: MutableIterable<String>): MutableIterable<Forskuttering> {
        TODO("Not yet implemented")
    }

    override fun count(): Long {
        TODO("Not yet implemented")
    }

    override fun deleteById(id: String) {
        TODO("Not yet implemented")
    }

    override fun delete(entity: Forskuttering) {
        TODO("Not yet implemented")
    }

    override fun deleteAllById(ids: MutableIterable<String>) {
        TODO("Not yet implemented")
    }

    override fun deleteAll(entities: MutableIterable<Forskuttering>) {
        TODO("Not yet implemented")
    }

    override fun deleteAll() {
        TODO("Not yet implemented")
    }
}

@Repository
@Profile("fakes")
@Primary
class KlippMetrikkRepositoryFake :
    InMemoryCrudRepository<KlippMetrikkDbRecord, String>(
        getId = { it.id },
        copyWithId = { record, newId ->
            record.copy(id = newId)
        },
        generateId = { UUID.randomUUID().toString() },
    ),
    KlippMetrikkRepository

@Repository
@Primary
@Profile("fakes")
class SporsmalDAOFake : SporsmalDAO {
    override fun finnSporsmal(sykepengesoknadIds: Set<String>): HashMap<String, MutableList<Sporsmal>> {
        TODO("Not yet implemented")
    }

    override fun populerMedSvar(svarMap: HashMap<String, MutableList<Svar>>) {
        TODO("Not yet implemented")
    }

    override fun slettSporsmalOgSvar(soknadsIder: List<String>) {
        TODO("Not yet implemented")
    }

    override fun slettEnkeltSporsmal(sporsmalsIder: List<String>) {
        TODO("Not yet implemented")
    }
}

@Repository
@Primary
@Profile("fakes")
class KlippetSykepengesoknadRepositoryFake : KlippetSykepengesoknadRepository {
    override fun findBySykmeldingUuid(sykmeldingUuid: String): KlippetSykepengesoknadDbRecord? {
        TODO("Not yet implemented")
    }

    override fun findAllBySykepengesoknadUuidIn(sykepengesoknadUuid: List<String>): List<KlippetSykepengesoknadDbRecord> {
        TODO("Not yet implemented")
    }

    override fun findAllBySykmeldingUuidIn(sykmeldingUuid: List<String>): List<KlippetSykepengesoknadDbRecord> {
        TODO("Not yet implemented")
    }

    override fun <S : KlippetSykepengesoknadDbRecord?> save(entity: S & Any): S & Any {
        TODO("Not yet implemented")
    }

    override fun <S : KlippetSykepengesoknadDbRecord?> saveAll(entities: MutableIterable<S>): MutableIterable<S> {
        TODO("Not yet implemented")
    }

    override fun findById(id: String): Optional<KlippetSykepengesoknadDbRecord> {
        TODO("Not yet implemented")
    }

    override fun existsById(id: String): Boolean {
        TODO("Not yet implemented")
    }

    override fun findAll(): MutableIterable<KlippetSykepengesoknadDbRecord> {
        TODO("Not yet implemented")
    }

    override fun findAllById(ids: MutableIterable<String>): MutableIterable<KlippetSykepengesoknadDbRecord> {
        TODO("Not yet implemented")
    }

    override fun count(): Long {
        TODO("Not yet implemented")
    }

    override fun deleteById(id: String) {
        TODO("Not yet implemented")
    }

    override fun delete(entity: KlippetSykepengesoknadDbRecord) {
        TODO("Not yet implemented")
    }

    override fun deleteAllById(ids: MutableIterable<String>) {
        TODO("Not yet implemented")
    }

    override fun deleteAll(entities: MutableIterable<KlippetSykepengesoknadDbRecord>) {
        TODO("Not yet implemented")
    }

    override fun deleteAll() {
        TODO("Not yet implemented")
    }
}

@Repository
@Profile("fakes")
@Primary
class DodsmeldingDAOFake : DodsmeldingDAO {
    override fun fnrMedToUkerGammelDodsmelding(): List<DodsmeldingDAO.Dodsfall> {
        TODO("Not yet implemented")
    }

    override fun harDodsmelding(identer: FolkeregisterIdenter): Boolean {
        TODO("Not yet implemented")
    }

    override fun oppdaterDodsdato(
        identer: FolkeregisterIdenter,
        dodsdato: LocalDate,
    ) {
        TODO("Not yet implemented")
    }

    override fun lagreDodsmelding(
        identer: FolkeregisterIdenter,
        dodsdato: LocalDate,
        meldingMottattDato: OffsetDateTime,
    ) {
        TODO("Not yet implemented")
    }

    override fun slettDodsmelding(identer: FolkeregisterIdenter) {
        TODO("Not yet implemented")
    }
}

@Repository
@Primary
@Profile("fakes")
class LockRepositoryFake : LockRepository {
    override fun settAdvisoryLock(vararg keys: Long): Boolean {
        TODO("Not yet implemented")
    }

    override fun settAdvisoryTransactionLock(key: String) {
        TODO("Not yet implemented")
    }
}

@Repository
@Primary
@Profile("fakes")
class SoknadLagrerFake : SoknadLagrer {
    @Autowired
    lateinit var sykepengesoknadRepository: SykepengesoknadRepositoryFake

    @Autowired
    lateinit var sporsmalRepositoryFake: SporsmalRepositoryFake

    @Autowired
    lateinit var svarRepositoryFake: SvarRepositoryFake

    override fun lagreSoknad(soknad: Sykepengesoknad) {
        val normalisertSoknad = soknad.normaliser()
        sykepengesoknadRepository.lagreSoknad(normalisertSoknad.soknad)
    }

    override fun lagreSporsmalOgSvarFraSoknad(soknad: Sykepengesoknad) {
        val normalisertSoknad = soknad.normaliser()
        normalisertSoknad.sporsmal.lagreSporsmal()
        normalisertSoknad.svar.lagreSvar()
    }

    override fun List<SykepengesoknadDbRecord>.lagre() {
        TODO("Not yet implemented")
    }

    override fun List<SoknadsperiodeDbRecord>.lagrePerioder() {
        TODO("Not yet implemented")
    }

    override fun List<SporsmalDbRecord>.lagreSporsmal() {
        sporsmalRepositoryFake.lagreSporsmal(this)
    }

    override fun List<SvarDbRecord>.lagreSvar() {
        svarRepositoryFake.lagreSvar(this)
    }
}

@Configuration
@Profile("fakes")
class TestDataSourceConfig {
    @Bean
    @Primary // Sørger for at denne bean-en prioriteres framfor en "ekte" DataSource
    fun mockDataSource(): NamedParameterJdbcTemplate {
        val dataSource: NamedParameterJdbcTemplate =
            Mockito.mock<NamedParameterJdbcTemplate>(NamedParameterJdbcTemplate::class.java)
        // Evt. konfigurer mocken til å gjøre akkurat det du trenger
        return dataSource
    }
}
