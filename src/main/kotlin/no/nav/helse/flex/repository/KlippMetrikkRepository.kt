package no.nav.helse.flex.repository

import org.springframework.data.annotation.Id
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface KlippMetrikkRepository : CrudRepository<KlippMetrikkDbRecord, String> {
    @Query(
        """
        with gruppering as (
            select sykmelding_uuid, eksisterende_sykepengesoknad_id, array_agg(variant order by timestamp) as varianter
            FROM klipp_metrikk
            WHERE soknadstatus = 'SENDT'
            group by sykmelding_uuid, eksisterende_sykepengesoknad_id
            having count(*) > 1
        )
        
        select sykmelding_uuid, eksisterende_sykepengesoknad_id, varianter[1] as variant
        from gruppering
        """
    )
    fun findDuplikateMetrikker(): List<DuplikateMetrikker>
}

data class DuplikateMetrikker(
    val sykmeldingUuid: String,
    val eksisterendeSykepengesoknadId: String,
    val variant: String
)

@Table("klipp_metrikk")
data class KlippMetrikkDbRecord(
    @Id
    val id: String? = null,
    val sykmeldingUuid: String,
    val variant: String,
    val soknadstatus: String,
    val timestamp: Instant,
    val eksisterendeSykepengesoknadId: String,
    val endringIUforegrad: String,
    val klippet: Boolean
)
