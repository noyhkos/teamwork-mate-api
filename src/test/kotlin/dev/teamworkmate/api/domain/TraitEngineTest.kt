package dev.teamworkmate.api.domain

import dev.teamworkmate.api.domain.mbti.Mbti
import dev.teamworkmate.api.domain.saju.DayStrength
import dev.teamworkmate.api.domain.saju.SajuSummary
import dev.teamworkmate.api.domain.traits.Trait
import dev.teamworkmate.api.domain.traits.TraitEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TraitEngineTest {

    /**
     * Golden chart: 2000-01-27 10:30 M (cross-verified in calc/ssaju-verification).
     * 오행 목2 화2 토3 금1 수0 / 십신 재성3 식상2 관성1 비겁1 인성0 / 신강약 neutral.
     */
    private val goldenSaju = SajuSummary.fromRaw(
        dayMasterStem = "甲",
        elementsKo = mapOf("목" to 2, "화" to 2, "토" to 3, "금" to 1, "수" to 0),
        gods = listOf("정재", "상관", "정재", "식신", "편관", "정재", "겁재", "(일간)"),
        strength = DayStrength.NEUTRAL,
        birthTimeKnown = true,
    )

    @Test
    fun `golden chart saju traits are pinned`() {
        val v = TraitEngine.sajuTraits(goldenSaju)
        assertEquals(63, v[Trait.DRIVE])
        assertEquals(53, v[Trait.CAUTION])
        assertEquals(61, v[Trait.SOCIAL])
        assertEquals(62, v[Trait.DETAIL])
        assertEquals(63, v[Trait.CREATIVE])
        assertEquals(58, v[Trait.HARMONY])
        assertEquals(55, v[Trait.COMMAND])
        assertEquals(69, v[Trait.STEADY])
    }

    @Test
    fun `golden chart - earth and jaeseong dominance makes STEADY the top trait`() {
        val v = TraitEngine.sajuTraits(goldenSaju)
        assertEquals(listOf(Trait.STEADY), v.top(1))
        assertTrue(v[Trait.CAUTION] <= Trait.entries.minOf { v[it] } + 1)
    }

    @Test
    fun `day-master placeholder is excluded from ten-god counts`() {
        assertEquals(7, goldenSaju.tenGodTotal)
    }

    @Test
    fun `unknown birth time - null strength applies no adjustment`() {
        val withNull = goldenSaju.copy(strength = null)
        val withNeutral = goldenSaju.copy(strength = DayStrength.NEUTRAL)
        assertEquals(TraitEngine.sajuTraits(withNeutral), TraitEngine.sajuTraits(withNull))
    }

    @Test
    fun `strong day master boosts drive and command`() {
        val strong = TraitEngine.sajuTraits(goldenSaju.copy(strength = DayStrength.STRONG))
        val neutral = TraitEngine.sajuTraits(goldenSaju)
        assertTrue(strong[Trait.DRIVE] > neutral[Trait.DRIVE])
        assertTrue(strong[Trait.COMMAND] > neutral[Trait.COMMAND])
    }

    @Test
    fun `mbti mapping - ENTJ commands, ISFP harmonizes`() {
        val entj = TraitEngine.mbtiTraits(Mbti.parse("ENTJ"))
        val isfp = TraitEngine.mbtiTraits(Mbti.parse("ISFP"))
        assertTrue(entj[Trait.COMMAND] > isfp[Trait.COMMAND])
        assertTrue(isfp[Trait.HARMONY] > entj[Trait.HARMONY])
    }

    @Test
    fun `mbti mapping - E raises SOCIAL over I with same other axes`() {
        val estj = TraitEngine.mbtiTraits(Mbti.parse("ESTJ"))
        val istj = TraitEngine.mbtiTraits(Mbti.parse("ISTJ"))
        assertTrue(estj[Trait.SOCIAL] > istj[Trait.SOCIAL])
    }

    @Test
    fun `combined is the mean of saju and mbti vectors`() {
        val saju = TraitEngine.sajuTraits(goldenSaju)
        val mbti = TraitEngine.mbtiTraits(Mbti.parse("ENTJ"))
        val combined = TraitEngine.combined(goldenSaju, Mbti.parse("ENTJ"))
        assertEquals(Math.round(0.5 * saju[Trait.COMMAND] + 0.5 * mbti[Trait.COMMAND]).toInt(), combined[Trait.COMMAND])
    }

    @Test
    fun `invalid mbti rejected`() {
        assertFailsWith<IllegalArgumentException> { Mbti.parse("ABCD") }
    }
}
