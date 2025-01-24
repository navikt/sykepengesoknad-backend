package no.nav.helse.flex.repository

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest

interface LockRepository {
    @Transactional(propagation = Propagation.REQUIRED)
    fun settAdvisoryLock(vararg keys: Long): Boolean

    @Transactional(propagation = Propagation.REQUIRED)
    fun settAdvisoryTransactionLock(key: String)
}

@Repository
class LockRepositoryImpl(
    private val namedParameterJdbcTemplate: NamedParameterJdbcTemplate,
) : LockRepository {
    // Må loope gjennom alle keys fordi fnr er større enn en int
    // pg_try_advisory_xact_lock(key bigint)
    // pg_try_advisory_xact_lock(key1 int, key2 int)
    @Transactional(propagation = Propagation.REQUIRED)
    override fun settAdvisoryLock(vararg keys: Long): Boolean {
        var locked = true
        keys.forEach {
            val lock =
                namedParameterJdbcTemplate.queryForObject(
                    "SELECT pg_try_advisory_xact_lock(:key)",
                    MapSqlParameterSource().addValue("key", it),
                    Boolean::class.java,
                ) ?: false
            if (!lock) {
                locked = false
            }
        }
        return locked
    }

    @Transactional(propagation = Propagation.REQUIRED)
    override fun settAdvisoryTransactionLock(key: String) {
        val hash = key.stringToLongHashSHA256()
        namedParameterJdbcTemplate.queryForObject(
            "SELECT pg_try_advisory_xact_lock(:key)",
            MapSqlParameterSource().addValue("key", hash),
            Boolean::class.java,
        ) ?: false
    }
}

fun String.stringToLongHashSHA256(): Long {
    val bytes = this.toByteArray()
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(bytes) // Bruker SHA-256 til å hash'e input
    var hash = 0L
    // Bruker de første 8 bytene av hashen til å lage et Long-tall
    for (i in 0..7) {
        hash = (hash shl 8) + (digest[i].toInt() and 0xff)
    }
    return hash
}
