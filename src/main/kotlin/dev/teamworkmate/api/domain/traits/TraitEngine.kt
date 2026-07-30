package dev.teamworkmate.api.domain.traits

import dev.teamworkmate.api.domain.mbti.Mbti
import dev.teamworkmate.api.domain.saju.DayStrength
import dev.teamworkmate.api.domain.saju.Element
import dev.teamworkmate.api.domain.saju.SajuSummary
import dev.teamworkmate.api.domain.saju.TenGodGroup

/**
 * 사주/MBTI facts → 8-axis trait vector. Pure and deterministic:
 * scores come only from [TraitWeights] constants, never from an LLM.
 */
object TraitEngine {

    fun sajuTraits(saju: SajuSummary): TraitVector {
        val acc = baseAccumulator()
        for (e in Element.entries) {
            val ratio = saju.elementRatio(e)
            TraitWeights.ELEMENT.getValue(e).forEach { (t, w) -> acc.merge(t, ratio * w, Double::plus) }
        }
        for (g in TenGodGroup.entries) {
            val ratio = saju.tenGodRatio(g)
            TraitWeights.TEN_GOD.getValue(g).forEach { (t, w) -> acc.merge(t, ratio * w, Double::plus) }
        }
        when (saju.strength) {
            DayStrength.STRONG -> TraitWeights.STRENGTH_STRONG.forEach { (t, w) -> acc.merge(t, w, Double::plus) }
            DayStrength.WEAK -> TraitWeights.STRENGTH_WEAK.forEach { (t, w) -> acc.merge(t, w, Double::plus) }
            else -> Unit // NEUTRAL or unknown birth time: no adjustment
        }
        return TraitVector.of(acc)
    }

    fun mbtiTraits(mbti: Mbti): TraitVector {
        val acc = baseAccumulator()
        applyAxis(acc, TraitWeights.MBTI_E, mbti.extraverted)
        applyAxis(acc, TraitWeights.MBTI_S, mbti.sensing)
        applyAxis(acc, TraitWeights.MBTI_T, mbti.thinking)
        applyAxis(acc, TraitWeights.MBTI_J, mbti.judging)
        return TraitVector.of(acc)
    }

    fun combined(saju: SajuSummary, mbti: Mbti): TraitVector {
        val s = sajuTraits(saju)
        val m = mbtiTraits(mbti)
        return TraitVector.of(Trait.entries.associateWith { t ->
            TraitWeights.SAJU_WEIGHT * s[t] + TraitWeights.MBTI_WEIGHT * m[t]
        })
    }

    private fun baseAccumulator(): MutableMap<Trait, Double> =
        Trait.entries.associateWith { TraitWeights.BASE }.toMutableMap()

    private fun applyAxis(acc: MutableMap<Trait, Double>, axis: TraitWeights.MbtiAxis, value: Boolean) {
        (if (value) axis.onTrue else axis.onFalse).forEach { (t, w) -> acc.merge(t, w, Double::plus) }
    }
}
