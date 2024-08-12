package no.nav.helse.flex.vedtaksperiodebehandling

import org.springframework.data.annotation.Id
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface VedtaksperiodeBehandlingRepository : CrudRepository<VedtaksperiodeBehandlingDbRecord, String> {
    fun findByVedtaksperiodeIdAndBehandlingId(
        vedtaksperiodeId: String,
        behandlingId: String,
    ): VedtaksperiodeBehandlingDbRecord?

    @Query(
        """
            SELECT vbs.sykepengesoknad_uuid, vb.vedtaksperiode_id
            FROM vedtaksperiode_behandling_sykepengesoknad vbs,
            vedtaksperiode_behandling vb 
            WHERE vb.id = vbs.vedtaksperiode_behandling_id
            AND vbs.sykepengesoknad_uuid in(:soknadUuider)
        """,
    )
    fun finnVedtaksperiodeiderForSoknad(
        @Param("soknadUuider") soknadUuider: List<String>,
    ): List<SoknadVedtaksperiodeId>
}

data class SoknadVedtaksperiodeId(
    val sykepengesoknadUuid: String,
    val vedtaksperiodeId: String,
)

@Table("vedtaksperiode_behandling")
data class VedtaksperiodeBehandlingDbRecord(
    @Id
    val id: String? = null,
    val opprettetDatabase: Instant,
    val oppdatertDatabase: Instant,
    val sisteSpleisstatus: StatusVerdi,
    val sisteSpleisstatusTidspunkt: Instant,
    val vedtaksperiodeId: String,
    val behandlingId: String,
)

@Table("vedtaksperiode_behandling_sykepengesoknad")
data class VedtaksperiodeBehandlingSykepengesoknadDbRecord(
    @Id
    val id: String? = null,
    val vedtaksperiodeBehandlingId: String,
    val sykepengesoknadUuid: String,
)

@Repository
interface VedtaksperiodeBehandlingSykepengesoknadRepository :
    CrudRepository<VedtaksperiodeBehandlingSykepengesoknadDbRecord, String> {
    fun findByVedtaksperiodeBehandlingIdIn(ider: List<String>): List<VedtaksperiodeBehandlingSykepengesoknadDbRecord>

    fun findBySykepengesoknadUuidIn(ider: List<String>): List<VedtaksperiodeBehandlingSykepengesoknadDbRecord>
}

@Repository
interface VedtaksperiodeBehandlingStatusRepository : CrudRepository<VedtaksperiodeBehandlingStatusDbRecord, String>

@Table("vedtaksperiode_behandling_status")
data class VedtaksperiodeBehandlingStatusDbRecord(
    @Id
    val id: String? = null,
    val vedtaksperiodeBehandlingId: String,
    val opprettetDatabase: Instant,
    val tidspunkt: Instant,
    val status: StatusVerdi,
)

enum class StatusVerdi {
    OPPRETTET,
    VENTER_PÅ_ARBEIDSGIVER,
    VENTER_PÅ_SAKSBEHANDLER,
    VENTER_PÅ_ANNEN_PERIODE,
    FERDIG,
    BEHANDLES_UTENFOR_SPEIL,
}
