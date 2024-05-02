package no.nav.helse.flex

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.nio.file.Files
import java.nio.file.Path

class DatabaseExportTest : FellesTestOppsett() {


    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    fun generateDbml(): String {

        fun String.dataTypeToDbmlType(): String {
            return when (this) {
                "character varying" -> "varchar"
                "timestamp without time zone" -> "timestamp"
                "timestamp with time zone" -> "timestamp"
                else -> this
            }
        }

        val dbml = StringBuilder()

        val tables: List<Map<String?, Any?>> =
            jdbcTemplate.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema='public'")
        for (row in tables) {
            val tableName = row["table_name"] as String
            if (tableName == "flyway_schema_history") continue
            dbml.append("Table ").append(tableName).append(" {\n")

            val columns: List<Map<String?, Any?>> = jdbcTemplate.queryForList(
                "SELECT column_name, data_type FROM information_schema.columns WHERE table_name=?",
                tableName
            )

            // sort columns. id first, then fnr, then the rest alpahebetically
            val sortedColumns = columns.sortedWith(compareBy({
                when (it["column_name"]) {
                    "id" -> 0
                    "fnr" -> 1
                    else -> 2
                }
            }, {
                // Dette sørger for at når kolonneverdien er 'else', altså ikke 'id' eller 'fnr',
                // vil den sortere disse kolonnene alfabetisk basert på kolonnenavn
                if (it["column_name"] in listOf("id", "fnr")) "" else it["column_name"] as String
            }))


            for (column in sortedColumns) {
                val columnName = column["column_name"] as String
                val dataType = column["data_type"] as String
                dbml.append("    ").append(columnName).append(" ").append(dataType.dataTypeToDbmlType()).append("\n")
            }

            dbml.append("}\n")
        }
        // Adding foreign keys
        val foreignKeys = jdbcTemplate.queryForList(
            """
            SELECT tc.table_name, kcu.column_name, ccu.table_name AS foreign_table_name, ccu.column_name AS foreign_column_name 
            FROM 
                information_schema.table_constraints AS tc 
                JOIN information_schema.key_column_usage AS kcu 
                    ON tc.constraint_name = kcu.constraint_name
                JOIN information_schema.constraint_column_usage AS ccu 
                    ON ccu.constraint_name = tc.constraint_name
            WHERE constraint_type = 'FOREIGN KEY'
        """
        )

        for (fk in foreignKeys) {
            val tableName = fk["table_name"] as String
            val columnName = fk["column_name"] as String
            val foreignTableName = fk["foreign_table_name"] as String
            val foreignColumnName = fk["foreign_column_name"] as String
            dbml.append("Ref: ").append(tableName).append(".").append(columnName)
                .append(" > ").append(foreignTableName).append(".").append(foreignColumnName).append("\n")
        }


        return dbml.toString()
    }

    @Test
    fun genererDbmlFil() {
        val dbmlContent: String = generateDbml()
        // create out if not exists
        Files.createDirectories(Path.of("out"))
        Files.writeString(Path.of("out", "databaseschema.dbml"), dbmlContent)
    }
}
