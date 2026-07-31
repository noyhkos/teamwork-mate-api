package dev.teamworkmate.api.domain.roles

import dev.teamworkmate.api.domain.saju.SajuSummary
import dev.teamworkmate.api.domain.saju.TenGodGroup
import dev.teamworkmate.api.domain.traits.Trait
import dev.teamworkmate.api.domain.traits.TraitVector

/**
 * Role fitness = weighted sum over the trait vector (+ ten-god ratio boosts).
 * Pure formulas.
 *
 * Every role leans on a different pair of trait axes, and the five ten-god
 * groups are spread across them so two people with the same MBTI still land
 * differently. Weights sum to 1.0 per role.
 */
object RoleScorer {

    fun scores(traits: TraitVector, saju: SajuSummary): Map<Role, Double> {
        val bigyeop = saju.tenGodRatio(TenGodGroup.BIGYEOP) * 100
        val siksang = saju.tenGodRatio(TenGodGroup.SIKSANG) * 100
        val jaeseong = saju.tenGodRatio(TenGodGroup.JAESEONG) * 100
        val gwanseong = saju.tenGodRatio(TenGodGroup.GWANSEONG) * 100
        val inseong = saju.tenGodRatio(TenGodGroup.INSEONG) * 100

        return mapOf(
            Role.LEADER to 0.4 * traits[Trait.COMMAND] + 0.3 * traits[Trait.DRIVE] +
                0.2 * traits[Trait.HARMONY] + 0.1 * traits[Trait.STEADY],

            // Reads the board and picks the move: creative like IDEA, but cautious
            // instead of driving, weighted by 인성 — the study and planning star.
            Role.STRATEGIST to 0.4 * traits[Trait.CREATIVE] + 0.3 * traits[Trait.CAUTION] +
                0.3 * inseong,

            Role.TREASURER to 0.4 * traits[Trait.DETAIL] + 0.3 * traits[Trait.STEADY] +
                0.3 * jaeseong,

            // Runs at whatever just broke. 비겁 is the star of one's own force.
            Role.FIXER to 0.4 * traits[Trait.DRIVE] + 0.3 * traits[Trait.COMMAND] +
                0.3 * bigyeop,

            // Keeps the rules. 관성 is the star of authority and discipline.
            Role.ENFORCER to 0.4 * traits[Trait.CAUTION] + 0.3 * traits[Trait.COMMAND] +
                0.3 * gwanseong,

            Role.IDEA to 0.5 * traits[Trait.CREATIVE] + 0.3 * traits[Trait.DRIVE] +
                0.2 * traits[Trait.SOCIAL],

            // Faces outward, where MOOD faces the room. 재성 covers outside dealings.
            Role.DIPLOMAT to 0.4 * traits[Trait.SOCIAL] + 0.3 * traits[Trait.HARMONY] +
                0.3 * jaeseong,

            Role.MEDIATOR to 0.5 * traits[Trait.HARMONY] + 0.3 * traits[Trait.STEADY] +
                0.2 * traits[Trait.SOCIAL],

            Role.MOOD to 0.5 * traits[Trait.SOCIAL] + 0.3 * siksang +
                0.2 * traits[Trait.HARMONY],

            // Writes it down. Shares DETAIL with TREASURER, split apart by 인성.
            Role.SCRIBE to 0.5 * traits[Trait.DETAIL] + 0.2 * traits[Trait.CAUTION] +
                0.3 * inseong,

            Role.BRAKE to 0.5 * traits[Trait.CAUTION] + 0.3 * traits[Trait.DETAIL] +
                0.2 * traits[Trait.STEADY],
        )
    }
}
