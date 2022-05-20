package no.nav.helse.flex.repository

import java.sql.ResultSet

fun ResultSet.getNullableString(columnLabel: String): String? {
    return this.getString(columnLabel)
}

fun ResultSet.getNullableBoolean(columnLabel: String): Boolean? {
    val boolean = this.getBoolean(columnLabel)
    if (this.wasNull()) {
        return null
    }
    return boolean
}
