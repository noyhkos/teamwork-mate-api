package dev.teamworkmate.api.domain

import dev.teamworkmate.api.domain.mbti.Mbti
import dev.teamworkmate.api.domain.pairs.PairScorer
import dev.teamworkmate.api.domain.saju.DayStrength
import dev.teamworkmate.api.domain.saju.SajuSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PairScorerTest {

    private fun saju(stem: String, elementsKo: Map<String, Int>) = SajuSummary.fromRaw(
        dayMasterStem = stem,
        elementsKo = elementsKo,
        gods = listOf("비견", "식신", "정재", "정관", "정인"),
        strength = DayStrength.NEUTRAL,
        birthTimeKnown = true,
    )

    private val allPresent = mapOf("목" to 2, "화" to 2, "토" to 2, "금" to 1, "수" to 1)

    /** Golden: A=검증차트(甲, 수0) × B=합성(己, 목0) — 상극(-15)+천간합(+15)+보완2건(+8). */
    @Test
    fun `golden pair is pinned`() {
        val a = saju("甲", mapOf("목" to 2, "화" to 2, "토" to 3, "금" to 1, "수" to 0))
        val b = saju("己", mapOf("목" to 0, "화" to 1, "토" to 3, "금" to 2, "수" to 2))
        val score = PairScorer.score(a, Mbti.parse("ENTJ"), b, Mbti.parse("ISFJ"))
        assertEquals(58, score.sajuScore)
        assertEquals(62, score.mbtiScore)
        assertEquals(60, score.total)
        assertTrue(score.factors.any { it.code == "SAJU_STEM_HAP" })
        assertTrue(score.factors.any { it.code == "SAJU_SANGGEUK" })
        assertTrue(score.factors.any { it.code == "SAJU_COMPLEMENT" && it.delta == 8.0 })
    }

    @Test
    fun `sangsaeng pair beats sanggeuk pair, all else equal`() {
        val a = saju("甲", allPresent) // 목
        val fire = saju("丙", allPresent) // 목생화
        val earth = saju("戊", allPresent) // 목극토 (천간 합충 없음)
        val m = Mbti.parse("ISTJ")
        val sangsaeng = PairScorer.score(a, m, fire, m).sajuScore
        val sanggeuk = PairScorer.score(a, m, earth, m).sajuScore
        assertTrue(sangsaeng > sanggeuk)
        assertEquals(30, sangsaeng - sanggeuk) // +15 vs -15
    }

    @Test
    fun `stem chung is penalized`() {
        val a = saju("甲", allPresent)
        val chung = saju("庚", allPresent) // 甲庚충 (금극목 상극도 겹침)
        val m = Mbti.parse("ISTJ")
        val score = PairScorer.score(a, m, chung, m)
        assertTrue(score.factors.any { it.code == "SAJU_STEM_CHUNG" })
        assertEquals(25, score.sajuScore) // 50 - 15(상극) - 10(충)
    }

    @Test
    fun `same S-N beats different S-N`() {
        val a = saju("甲", allPresent)
        val b = saju("丙", allPresent)
        val same = PairScorer.score(a, Mbti.parse("ISTJ"), b, Mbti.parse("ISTJ")).mbtiScore
        val diff = PairScorer.score(a, Mbti.parse("ISTJ"), b, Mbti.parse("INTJ")).mbtiScore
        assertTrue(same > diff)
    }

    @Test
    fun `score is symmetric`() {
        val a = saju("甲", mapOf("목" to 3, "화" to 1, "토" to 2, "금" to 1, "수" to 1))
        val b = saju("辛", mapOf("목" to 1, "화" to 2, "토" to 1, "금" to 3, "수" to 1))
        val ab = PairScorer.score(a, Mbti.parse("ENFP"), b, Mbti.parse("ISTJ"))
        val ba = PairScorer.score(b, Mbti.parse("ISTJ"), a, Mbti.parse("ENFP"))
        assertEquals(ab.total, ba.total)
        assertEquals(ab.factors.map { it.code }.sorted(), ba.factors.map { it.code }.sorted())
    }

    @Test
    fun `every point is explained by a factor`() {
        val a = saju("甲", allPresent)
        val b = saju("癸", mapOf("목" to 1, "화" to 1, "토" to 1, "금" to 1, "수" to 4)) // 수생목 상생
        val score = PairScorer.score(a, Mbti.parse("ENTP"), b, Mbti.parse("ISFJ"))
        val sajuDelta = score.factors.filter { it.code.startsWith("SAJU_") }.sumOf { it.delta }
        val mbtiDelta = score.factors.filter { it.code.startsWith("MBTI_") }.sumOf { it.delta }
        assertEquals(Math.round(50.0 + sajuDelta).toInt(), score.sajuScore)
        assertEquals(Math.round(50.0 + mbtiDelta).toInt(), score.mbtiScore)
    }
}
