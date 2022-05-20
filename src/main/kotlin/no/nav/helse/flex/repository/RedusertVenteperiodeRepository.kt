package no.nav.helse.flex.repository

import org.springframework.data.annotation.Id
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface RedusertVenteperiodeRepository : CrudRepository<RedusertVenteperiodeDbRecord, String> {
    @Modifying
    @Query(
        """
        INSERT INTO redusert_venteperiode(sykmelding_id)
        VALUES (:sykmeldingId)
        ON CONFLICT DO NOTHING
        """
    )
    fun insert(sykmeldingId: String)

    fun existsBySykmeldingId(sykmeldingId: String): Boolean
}

@Table("redusert_venteperiode")
data class RedusertVenteperiodeDbRecord(
    @Id
    val sykmeldingId: String
)
