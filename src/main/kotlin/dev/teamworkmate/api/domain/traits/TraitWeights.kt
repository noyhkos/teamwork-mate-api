package dev.teamworkmate.api.domain.traits

import dev.teamworkmate.api.domain.saju.Element
import dev.teamworkmate.api.domain.saju.TenGodGroup

/**
 * Tunable heuristic constants mapping 사주/MBTI facts onto the 8 traits.
 * These encode folk interpretations for entertainment, not 명리학 canon —
 * kept in one place so tuning never touches engine logic.
 */
object TraitWeights {
    const val BASE = 50.0
    const val SAJU_WEIGHT = 0.5
    const val MBTI_WEIGHT = 0.5

    // Applied as: delta += elementRatio * weight
    val ELEMENT: Map<Element, Map<Trait, Double>> = mapOf(
        Element.WOOD to mapOf(Trait.CREATIVE to 30.0, Trait.DRIVE to 15.0),
        Element.FIRE to mapOf(Trait.DRIVE to 30.0, Trait.SOCIAL to 20.0),
        Element.EARTH to mapOf(Trait.STEADY to 30.0, Trait.HARMONY to 20.0),
        Element.METAL to mapOf(Trait.DETAIL to 30.0, Trait.COMMAND to 15.0),
        Element.WATER to mapOf(Trait.CAUTION to 30.0, Trait.CREATIVE to 15.0),
    )

    // Applied as: delta += tenGodRatio * weight
    val TEN_GOD: Map<TenGodGroup, Map<Trait, Double>> = mapOf(
        TenGodGroup.BIGYEOP to mapOf(Trait.COMMAND to 25.0, Trait.DRIVE to 15.0),
        TenGodGroup.SIKSANG to mapOf(Trait.CREATIVE to 20.0, Trait.SOCIAL to 20.0),
        TenGodGroup.JAESEONG to mapOf(Trait.DETAIL to 20.0, Trait.STEADY to 15.0),
        TenGodGroup.GWANSEONG to mapOf(Trait.CAUTION to 20.0, Trait.STEADY to 10.0),
        TenGodGroup.INSEONG to mapOf(Trait.CAUTION to 15.0, Trait.HARMONY to 20.0),
    )

    val STRENGTH_STRONG: Map<Trait, Double> = mapOf(Trait.DRIVE to 8.0, Trait.COMMAND to 8.0)
    val STRENGTH_WEAK: Map<Trait, Double> = mapOf(Trait.HARMONY to 8.0, Trait.CAUTION to 8.0)

    data class MbtiAxis(val onTrue: Map<Trait, Double>, val onFalse: Map<Trait, Double>)

    // true side: E / S / T / J
    val MBTI_E = MbtiAxis(
        onTrue = mapOf(Trait.SOCIAL to 20.0, Trait.DRIVE to 5.0),
        onFalse = mapOf(Trait.SOCIAL to -15.0, Trait.CAUTION to 8.0),
    )
    val MBTI_S = MbtiAxis(
        onTrue = mapOf(Trait.DETAIL to 15.0, Trait.STEADY to 8.0),
        onFalse = mapOf(Trait.CREATIVE to 20.0),
    )
    val MBTI_T = MbtiAxis(
        onTrue = mapOf(Trait.COMMAND to 15.0, Trait.HARMONY to -8.0),
        onFalse = mapOf(Trait.HARMONY to 18.0, Trait.SOCIAL to 6.0),
    )
    val MBTI_J = MbtiAxis(
        onTrue = mapOf(Trait.DETAIL to 10.0, Trait.STEADY to 8.0, Trait.CAUTION to 5.0),
        onFalse = mapOf(Trait.CREATIVE to 10.0, Trait.DRIVE to 6.0, Trait.CAUTION to -8.0),
    )
}
