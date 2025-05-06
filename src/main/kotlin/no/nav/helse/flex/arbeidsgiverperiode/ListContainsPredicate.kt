package no.nav.helse.flex.arbeidsgiverperiode

class ListContainsPredicate<T> private constructor(
    private val predicate: (List<T>) -> Boolean,
) {
    companion object {
        fun <T> of(t: T) = ListContainsPredicate<T> { t in it }

        fun <T> tagsSize(size: Int) = ListContainsPredicate<T> { it.size == size }

        fun <T> not(t: T) = not(of(t))

        fun <T> not(lcp: ListContainsPredicate<T>) = ListContainsPredicate<T> { it !in lcp }
    }

    operator fun contains(listToTest: List<T>): Boolean = predicate(listToTest)

    infix fun and(other: T) = and(of(other))

    infix fun and(other: ListContainsPredicate<T>) = ListContainsPredicate<T> { it in this && it in other }

    infix fun or(other: T) = or(of(other))

    infix fun or(other: ListContainsPredicate<T>) = ListContainsPredicate<T> { it in this || it in other }

    operator fun not() = ListContainsPredicate<T> { it !in this }
}

infix fun <T> T.or(other: ListContainsPredicate<T>) = ListContainsPredicate.of(this) or other

infix fun <T> T.or(other: T) = ListContainsPredicate.of(this) or other

infix fun <T> T.and(other: ListContainsPredicate<T>) = ListContainsPredicate.of(this) and other

infix fun <T> T.and(other: T) = ListContainsPredicate.of(this) and other

operator fun <T> T.not() = ListContainsPredicate.not(this)
