package no.nav.helse.flex.personhendelse

import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericRecord
import java.time.LocalDate

val GenericRecord.opplysningstype get(): String {
    return get("opplysningstype").toString()
}
val GenericRecord.erDodsfall get(): Boolean {
    return opplysningstype == OPPLYSNINGSTYPE_DODSFALL
}
val GenericRecord.fnr get(): String {
    return (get("personidenter") as GenericData.Array<*>)
        .map { it.toString() }
        .first { it.length == 11 }
}
val GenericRecord.endringstype get(): String {
    return get("endringstype").toString()
}
val GenericRecord.dodsdato get(): LocalDate {
    return LocalDate.ofEpochDay(
        (get("doedsfall") as GenericRecord?)?.get("doedsdato").toString().toLong()
    )
}

const val OPPRETTET = "OPPRETTET"
const val KORRIGERT = "KORRIGERT"
const val ANNULLERT = "ANNULLERT"
const val OPPHOERT = "OPPHOERT"
const val OPPLYSNINGSTYPE_DODSFALL = "DOEDSFALL_V1"
