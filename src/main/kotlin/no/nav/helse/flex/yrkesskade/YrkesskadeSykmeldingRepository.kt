package no.nav.helse.flex.yrkesskade

import org.springframework.data.annotation.Id
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface YrkesskadeSykmeldingRepository : CrudRepository<YrkesskadeSykmeldingDbRecord, String> {
    @Modifying
    @Query(
        """
        INSERT INTO yrkesskade_sykmelding(sykmelding_id)
        VALUES (:sykmeldingId)
        ON CONFLICT DO NOTHING
        """
    )
    fun insert(sykmeldingId: String)

    fun existsBySykmeldingId(sykmeldingId: String): Boolean
}

@Table("yrkesskade_sykmelding")
data class YrkesskadeSykmeldingDbRecord(
    @Id
    val sykmeldingId: String
)
