package no.nav.helse.flex.repository

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Repository
class LockRepository(
    private val namedParameterJdbcTemplate: NamedParameterJdbcTemplate
) {

    // Må loope gjennom alle keys fordi fnr er større enn en int
    // pg_try_advisory_xact_lock(key bigint)
    // pg_try_advisory_xact_lock(key1 int, key2 int)
    @Transactional(propagation = Propagation.REQUIRED)
    fun settAdvisoryLock(vararg keys: Long): Boolean {
        var locked = true
        keys.forEach {
            val lock = namedParameterJdbcTemplate.queryForObject(
                "SELECT pg_try_advisory_xact_lock(:key)",
                MapSqlParameterSource().addValue("key", it),
                Boolean::class.java
            ) ?: false
            if (!lock) {
                locked = false
            }
        }
        return locked
    }
}
