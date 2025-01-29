package no.nav.helse.flex.fakes

import org.springframework.data.repository.CrudRepository
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * @param T  Entitetstypen
 * @param ID Typen til entitetens ID
 * @param getId  En lambda for å hente ut ID fra en gitt entitet (f.eks. { it.id })
 */
open class InMemoryCrudRepository<T : Any, ID : Any>(
    private val getId: (T) -> ID?,
    private val copyWithId: (T, ID) -> T,
    private val generateId: () -> ID,
) : CrudRepository<T, ID> {
    private val store = ConcurrentHashMap<ID, T>()

    override fun findById(id: ID): Optional<T> {
        val t = store[id]!! as T
        return Optional.ofNullable(t)
    }

    override fun <S : T> save(entity: S): S {
        val currentId = getId(entity)

        // Dersom entiteten ikke har ID => generer en, og lag kopi med ny ID
        val finalEntity =
            if (currentId == null) {
                val newId = generateId()
                copyWithId(entity, newId) as S
            } else {
                entity
            }

        store[getId(finalEntity)!!] = finalEntity

        // Returnerer entiteten (med ID satt)
        return finalEntity
    }

    override fun existsById(id: ID): Boolean {
        return store.containsKey(id)
    }

    override fun <S : T> saveAll(entities: MutableIterable<S>): MutableIterable<S> {
        TODO("Not yet implemented")
    }

    override fun findAll(): MutableIterable<T> {
        return store.values.toMutableList()
    }

    override fun count(): Long {
        return store.size.toLong()
    }

    override fun deleteAll() {
        store.clear()
    }

    override fun deleteAll(entities: MutableIterable<T>) {
        for (entity in entities) {
            delete(entity!!)
        }
    }

    override fun deleteAllById(ids: MutableIterable<ID>) {
        for (id in ids) {
            store.remove(id)
        }
    }

    override fun delete(entity: T) {
        val id = getId(entity)
        store.remove(id, entity)
    }

    override fun deleteById(id: ID) {
        store.remove(id)
    }

    override fun findAllById(ids: MutableIterable<ID>): MutableIterable<T> {
        val result = mutableListOf<T>()
        for (id in ids) {
            store[id]?.let { result.add(it) }
        }
        return result
    }
}
