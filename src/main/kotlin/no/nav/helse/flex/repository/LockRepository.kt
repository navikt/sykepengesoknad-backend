package no.nav.helse.flex.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Repository
class LockRepository(
    private val jdbcTemplate: JdbcTemplate
) {

    @Transactional(propagation = Propagation.REQUIRED)
    fun settAdvisoryLock(vararg keys: String): Boolean {
        return jdbcTemplate.queryForObject("SELECT pg_try_advisory_xact_lock(${keys.joinToString(",")})", Boolean::class.java) ?: false
    }
}
