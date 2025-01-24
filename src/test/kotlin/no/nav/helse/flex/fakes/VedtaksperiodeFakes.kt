package no.nav.helse.flex.fakes

import no.nav.helse.flex.vedtaksperiodebehandling.*
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import java.util.*

@Repository
@Profile("fakes")
class VedtaksperiodeBehandlingRepositoryFake : VedtaksperiodeBehandlingRepository {
    override fun findByVedtaksperiodeIdAndBehandlingId(
        vedtaksperiodeId: String,
        behandlingId: String,
    ): VedtaksperiodeBehandlingDbRecord? {
        TODO("Not yet implemented")
    }

    override fun finnVedtaksperiodeiderForSoknad(soknadUuider: List<String>): List<SoknadVedtaksperiodeId> {
        TODO("Not yet implemented")
    }

    override fun <S : VedtaksperiodeBehandlingDbRecord?> save(entity: S & Any): S & Any {
        TODO("Not yet implemented")
    }

    override fun <S : VedtaksperiodeBehandlingDbRecord?> saveAll(entities: MutableIterable<S>): MutableIterable<S> {
        TODO("Not yet implemented")
    }

    override fun findById(id: String): Optional<VedtaksperiodeBehandlingDbRecord> {
        TODO("Not yet implemented")
    }

    override fun existsById(id: String): Boolean {
        TODO("Not yet implemented")
    }

    override fun findAll(): MutableIterable<VedtaksperiodeBehandlingDbRecord> {
        TODO("Not yet implemented")
    }

    override fun findAllById(ids: MutableIterable<String>): MutableIterable<VedtaksperiodeBehandlingDbRecord> {
        TODO("Not yet implemented")
    }

    override fun count(): Long {
        TODO("Not yet implemented")
    }

    override fun deleteById(id: String) {
        TODO("Not yet implemented")
    }

    override fun delete(entity: VedtaksperiodeBehandlingDbRecord) {
        TODO("Not yet implemented")
    }

    override fun deleteAllById(ids: MutableIterable<String>) {
        TODO("Not yet implemented")
    }

    override fun deleteAll(entities: MutableIterable<VedtaksperiodeBehandlingDbRecord>) {
        TODO("Not yet implemented")
    }

    override fun deleteAll() {
        TODO("Not yet implemented")
    }
}

@Repository
@Profile("fakes")
class VedtaksperiodeBehandlingSykepengesoknadRepositoryFake : VedtaksperiodeBehandlingSykepengesoknadRepository {
    override fun findByVedtaksperiodeBehandlingIdIn(ider: List<String>): List<VedtaksperiodeBehandlingSykepengesoknadDbRecord> {
        TODO("Not yet implemented")
    }

    override fun findBySykepengesoknadUuidIn(ider: List<String>): List<VedtaksperiodeBehandlingSykepengesoknadDbRecord> {
        TODO("Not yet implemented")
    }

    override fun <S : VedtaksperiodeBehandlingSykepengesoknadDbRecord?> save(entity: S & Any): S & Any {
        TODO("Not yet implemented")
    }

    override fun <S : VedtaksperiodeBehandlingSykepengesoknadDbRecord?> saveAll(entities: MutableIterable<S>): MutableIterable<S> {
        TODO("Not yet implemented")
    }

    override fun findById(id: String): Optional<VedtaksperiodeBehandlingSykepengesoknadDbRecord> {
        TODO("Not yet implemented")
    }

    override fun existsById(id: String): Boolean {
        TODO("Not yet implemented")
    }

    override fun findAll(): MutableIterable<VedtaksperiodeBehandlingSykepengesoknadDbRecord> {
        TODO("Not yet implemented")
    }

    override fun findAllById(ids: MutableIterable<String>): MutableIterable<VedtaksperiodeBehandlingSykepengesoknadDbRecord> {
        TODO("Not yet implemented")
    }

    override fun count(): Long {
        TODO("Not yet implemented")
    }

    override fun deleteById(id: String) {
        TODO("Not yet implemented")
    }

    override fun delete(entity: VedtaksperiodeBehandlingSykepengesoknadDbRecord) {
        TODO("Not yet implemented")
    }

    override fun deleteAllById(ids: MutableIterable<String>) {
        TODO("Not yet implemented")
    }

    override fun deleteAll(entities: MutableIterable<VedtaksperiodeBehandlingSykepengesoknadDbRecord>) {
        TODO("Not yet implemented")
    }

    override fun deleteAll() {
        TODO("Not yet implemented")
    }
}

@Repository
@Profile("fakes")
class VedtaksperiodeBehandlingStatusRepositoryFake : VedtaksperiodeBehandlingStatusRepository {
    override fun <S : VedtaksperiodeBehandlingStatusDbRecord?> save(entity: S & Any): S & Any {
        TODO("Not yet implemented")
    }

    override fun <S : VedtaksperiodeBehandlingStatusDbRecord?> saveAll(entities: MutableIterable<S>): MutableIterable<S> {
        TODO("Not yet implemented")
    }

    override fun findById(id: String): Optional<VedtaksperiodeBehandlingStatusDbRecord> {
        TODO("Not yet implemented")
    }

    override fun existsById(id: String): Boolean {
        TODO("Not yet implemented")
    }

    override fun findAll(): MutableIterable<VedtaksperiodeBehandlingStatusDbRecord> {
        TODO("Not yet implemented")
    }

    override fun findAllById(ids: MutableIterable<String>): MutableIterable<VedtaksperiodeBehandlingStatusDbRecord> {
        TODO("Not yet implemented")
    }

    override fun count(): Long {
        TODO("Not yet implemented")
    }

    override fun deleteById(id: String) {
        TODO("Not yet implemented")
    }

    override fun delete(entity: VedtaksperiodeBehandlingStatusDbRecord) {
        TODO("Not yet implemented")
    }

    override fun deleteAllById(ids: MutableIterable<String>) {
        TODO("Not yet implemented")
    }

    override fun deleteAll(entities: MutableIterable<VedtaksperiodeBehandlingStatusDbRecord>) {
        TODO("Not yet implemented")
    }

    override fun deleteAll() {
        TODO("Not yet implemented")
    }
}
