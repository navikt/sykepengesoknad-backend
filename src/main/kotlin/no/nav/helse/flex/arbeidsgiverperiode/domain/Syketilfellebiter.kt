package no.nav.helse.flex.arbeidsgiverperiode.domain
import no.nav.helse.flex.arbeidsgiverperiode.ListContainsPredicate
import java.time.LocalDate

class Syketilfellebiter(
    val biter: List<Syketilfellebit>,
    private val prioriteringsliste: List<ListContainsPredicate<Tag>>,
) {
    fun tilSyketilfelleIntradag(dag: LocalDate): SyketilfelleIntradag =
        SyketilfelleIntradag(dag, biter.filter { dag in (it.fom..(it.tom)) }, prioriteringsliste)

    fun finnTidligsteFom(): LocalDate = this.finnBit(Comparator.comparing { it.fom }).fom

    fun finnSenesteTom(): LocalDate = this.finnBit(Comparator.comparing<Syketilfellebit, LocalDate> { it.tom }.reversed()).tom

    private fun finnBit(comparator: Comparator<Syketilfellebit>): Syketilfellebit = biter.sortedWith(comparator).first()
}
