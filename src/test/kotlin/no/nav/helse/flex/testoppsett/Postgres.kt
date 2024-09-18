package no.nav.helse.flex.testoppsett

import org.testcontainers.containers.PostgreSQLContainer

class PostgreSQLContainer14 : PostgreSQLContainer<PostgreSQLContainer14>("postgres:14-alpine")

fun startPostgresContainer() {
    PostgreSQLContainer14().apply {
        withEnv("POSTGRES_SHARED_BUFFERS", "64MB")
        withEnv("POSTGRES_MAX_CONNECTIONS", "20")
        // Cloud SQL har wal_level = 'logical' på grunn av flagget cloudsql.logical_decoding i
        // naiserator.yaml. Vi må sette det samme lokalt for at flyway migrering skal fungere.
        withCommand("postgres", "-c", "wal_level=logical")
        start()
        System.setProperty("spring.datasource.url", "$jdbcUrl&reWriteBatchedInserts=true")
        System.setProperty("spring.datasource.username", username)
        System.setProperty("spring.datasource.password", password)
    }
}
