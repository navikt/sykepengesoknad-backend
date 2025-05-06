package no.nav.helse.flex

import no.nav.helse.flex.vedtaksperiodebehandling.*
import no.nav.helse.flex.vedtaksperiodebehandling.StatusVerdi.*
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class LagringAvSisTest : FellesTestOppsett() {
    @Autowired
    lateinit var vedtaksperiodeBehandlingRepository: VedtaksperiodeBehandlingRepository

    @Autowired
    lateinit var vedtaksperiodeBehandlingSykepengesoknadRepository: VedtaksperiodeBehandlingSykepengesoknadRepository

    val tidspunkt = OffsetDateTime.now()

    val behandlingstatusmelding =
        Behandlingstatusmelding(
            vedtaksperiodeId = UUID.randomUUID().toString(),
            behandlingId = UUID.randomUUID().toString(),
            status = Behandlingstatustype.OPPRETTET,
            tidspunkt = tidspunkt,
            eksterneSøknadIder = listOf(UUID.randomUUID().toString()),
        )

    @Test
    @Order(1)
    fun `Vi venter på saksbehandler`() {
        sendBehandlingsstatusMelding(behandlingstatusmelding)
        sendBehandlingsstatusMelding(
            behandlingstatusmelding.copy(
                status = Behandlingstatustype.VENTER_PÅ_ARBEIDSGIVER,
            ),
        )

        val dbRecord =
            awaitOppdatertStatus(
                VENTER_PÅ_ARBEIDSGIVER,
                behandlingstatusmelding.vedtaksperiodeId,
                behandlingstatusmelding.behandlingId,
            )

        vedtaksperiodeBehandlingSykepengesoknadRepository.findByVedtaksperiodeBehandlingIdIn(listOf(dbRecord.id!!)) shouldHaveSize 1
    }

    @Test
    @Order(3)
    fun `Vi  går til venter på SB`() {
        val opppdatertBehandlingstatusMelding =
            behandlingstatusmelding.copy(
                status = Behandlingstatustype.VENTER_PÅ_SAKSBEHANDLER,
                eksterneSøknadIder =
                    behandlingstatusmelding.eksterneSøknadIder
                        .toMutableList()
                        .also { it.add(UUID.randomUUID().toString()) },
            )
        sendBehandlingsstatusMelding(
            opppdatertBehandlingstatusMelding,
        )
        val dbRecord =
            awaitOppdatertStatus(
                VENTER_PÅ_SAKSBEHANDLER,
                behandlingstatusmelding.vedtaksperiodeId,
                behandlingstatusmelding.behandlingId,
            )

        val soknadRelasjoner =
            vedtaksperiodeBehandlingSykepengesoknadRepository
                .findByVedtaksperiodeBehandlingIdIn(listOf(dbRecord.id!!))
                .map {
                    it.sykepengesoknadUuid
                }.toSet()
        soknadRelasjoner shouldHaveSize 2

        soknadRelasjoner shouldBeEqualTo opppdatertBehandlingstatusMelding.eksterneSøknadIder.toSet()
    }

    @Test
    @Order(3)
    fun `Vi  går til ferdig`() {
        sendBehandlingsstatusMelding(
            behandlingstatusmelding.copy(
                status = Behandlingstatustype.FERDIG,
            ),
        )
        val dbRecord =
            awaitOppdatertStatus(FERDIG, behandlingstatusmelding.vedtaksperiodeId, behandlingstatusmelding.behandlingId)

        vedtaksperiodeBehandlingSykepengesoknadRepository.findByVedtaksperiodeBehandlingIdIn(listOf(dbRecord.id!!)) shouldHaveSize 2
    }

    fun awaitOppdatertStatus(
        forventetSisteSpleisstatus: StatusVerdi,
        vedtaksperiodeId: String,
        behandlingId: String,
    ): VedtaksperiodeBehandlingDbRecord {
        await().atMost(5, TimeUnit.SECONDS).until {
            val vedtaksperiode =
                vedtaksperiodeBehandlingRepository.findByVedtaksperiodeIdAndBehandlingId(
                    vedtaksperiodeId,
                    behandlingId,
                )
            if (vedtaksperiode == null) {
                false
            } else {
                vedtaksperiode.sisteSpleisstatus == forventetSisteSpleisstatus
            }
        }
        return vedtaksperiodeBehandlingRepository.findByVedtaksperiodeIdAndBehandlingId(
            vedtaksperiodeId,
            behandlingId,
        )!!
    }
}
