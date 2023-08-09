package no.nav.helse.flex.repository

import no.nav.helse.flex.util.serialisertTilString
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
        println("Prøver å sette advisory lock for keys: ${keys.serialisertTilString()}")
        val locked = jdbcTemplate.queryForObject("SELECT pg_try_advisory_xact_lock(${keys.joinToString(",")})", Boolean::class.java) ?: false
        println("advisory lock = $locked")
        return locked
    }
}
