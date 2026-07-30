package dev.teamworkmate.api.domain.roles

import dev.teamworkmate.api.domain.saju.SajuSummary
import dev.teamworkmate.api.domain.saju.TenGodGroup
import dev.teamworkmate.api.domain.traits.Trait
import dev.teamworkmate.api.domain.traits.TraitVector

/** Role fitness = weighted sum over the trait vector (+ ten-god ratio boosts). Pure formulas. */
object RoleScorer {

    fun scores(traits: TraitVector, saju: SajuSummary): Map<Role, Double> {
        val jaeseong = saju.tenGodRatio(TenGodGroup.JAESEONG) * 100
        val siksang = saju.tenGodRatio(TenGodGroup.SIKSANG) * 100
        val leader = 0.4 * traits[Trait.COMMAND] + 0.3 * traits[Trait.DRIVE] +
            0.2 * traits[Trait.HARMONY] + 0.1 * traits[Trait.STEADY]

        return mapOf(
            Role.LEADER to leader,
            Role.VICE to 0.7 * leader + 0.3 * traits[Trait.HARMONY],
            Role.TREASURER to 0.4 * traits[Trait.DETAIL] + 0.3 * traits[Trait.STEADY] + 0.3 * jaeseong,
            Role.MOOD to 0.5 * traits[Trait.SOCIAL] + 0.3 * siksang + 0.2 * traits[Trait.HARMONY],
            Role.IDEA to 0.5 * traits[Trait.CREATIVE] + 0.3 * traits[Trait.DRIVE] + 0.2 * traits[Trait.SOCIAL],
            Role.MEDIATOR to 0.5 * traits[Trait.HARMONY] + 0.3 * traits[Trait.STEADY] + 0.2 * traits[Trait.SOCIAL],
            Role.BRAKE to 0.5 * traits[Trait.CAUTION] + 0.3 * traits[Trait.DETAIL] + 0.2 * traits[Trait.STEADY],
        )
    }
}
