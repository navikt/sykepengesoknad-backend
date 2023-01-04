package no.nav.helse.flex.repository

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface KlippMetrikkRepository : CrudRepository<KlippMetrikkDbRecord, String>

@Table("klipp_metrikk")
data class KlippMetrikkDbRecord(
    @Id
    val id: String? = null,
    val sykmeldingUuid: String,
    val variant: String,
    val soknadstatus: String,
    val timestamp: Instant,
)
