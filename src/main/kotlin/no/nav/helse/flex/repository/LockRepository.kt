package no.nav.helse.flex.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Repository
class LockRepository(
    private val jdbcTemplate: JdbcTemplate
) {

    // Må loope gjennom alle keys fordi fnr er større enn en int
    // pg_try_advisory_xact_lock(key bigint)
    // pg_try_advisory_xact_lock(key1 int, key2 int)
    @Transactional(propagation = Propagation.REQUIRED)
    fun settAdvisoryLock(vararg keys: String): Boolean {
        var locked = true
        keys.forEach {
            val lock = jdbcTemplate.queryForObject("SELECT pg_try_advisory_xact_lock($it);", Boolean::class.java) ?: false
            if (!lock) {
                locked = false
            }
        }
        return locked
    }
}
