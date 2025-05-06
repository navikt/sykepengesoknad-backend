package no.nav.helse.flex.arbeidsgiverperiode.domain
import no.nav.helse.flex.arbeidsgiverperiode.ListContainsPredicate
import java.time.LocalDate

class SyketilfelleIntradag(
    private val dag: LocalDate,
    private val biter: List<Syketilfellebit>,
    private val prioriteringsliste: List<ListContainsPredicate<Tag>>,
) {
    fun velgSyketilfelledag(): Syketilfelledag =
        biter
            .groupBy { it.inntruffet.toLocalDate() }
            .toSortedMap()
            .mapValues(this::finnPresedens)
            .mapNotNull(Map.Entry<LocalDate, Syketilfellebit?>::value)
            .map { it.toSyketilfelledag() }
            .lastOrNull() ?: Syketilfelledag(dag, null, biter)

    private fun finnPresedens(entry: Map.Entry<LocalDate, List<Syketilfellebit>>): Syketilfellebit? {
        val (_, syketilfeller) = entry

        prioriteringsliste.forEach { prioriteringselement ->
            return syketilfeller.find { it.tags.toList() in prioriteringselement } ?: return@forEach
        }

        return null
    }

    private fun Syketilfellebit.toSyketilfelledag(): Syketilfelledag =
        Syketilfelledag(
            dag = this@SyketilfelleIntradag.dag,
            prioritertSyketilfellebit = this,
            syketilfellebiter = this@SyketilfelleIntradag.biter,
        )
}
