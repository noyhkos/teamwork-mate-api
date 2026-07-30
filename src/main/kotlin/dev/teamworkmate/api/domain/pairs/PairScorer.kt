package dev.teamworkmate.api.domain.pairs

import dev.teamworkmate.api.domain.mbti.Mbti
import dev.teamworkmate.api.domain.saju.Element
import dev.teamworkmate.api.domain.saju.SajuRelations
import dev.teamworkmate.api.domain.saju.SajuSummary
import kotlin.math.min

/** One explainable contribution to a pair score — the glass-box trail for UI/LLM grounding. */
data class PairFactor(val code: String, val label: String, val delta: Double)

data class PairScore(
    val total: Int, // 0..100
    val sajuScore: Int,
    val mbtiScore: Int,
    val factors: List<PairFactor>,
)

/**
 * Pair compatibility from 사주 relations + MBTI axis rules.
 * Symmetric, pure, deterministic. Heuristics are for entertainment; every point
 * carries a factor entry so nothing is a black box.
 */
object PairScorer {
    private const val BASE = 50.0
    private const val SAJU_WEIGHT = 0.5
    private const val MBTI_WEIGHT = 0.5

    private const val SANGSAENG = 15.0 // 상생
    private const val SANGGEUK = -15.0 // 상극
    private const val BIHWA = 5.0 // 같은 오행
    private const val STEM_HAP = 15.0 // 천간합
    private const val STEM_CHUNG = -10.0 // 천간충
    private const val COMPLEMENT_EACH = 4.0
    private const val COMPLEMENT_CAP = 16.0

    fun score(a: SajuSummary, aMbti: Mbti, b: SajuSummary, bMbti: Mbti): PairScore {
        val factors = mutableListOf<PairFactor>()
        val saju = sajuPart(a, b, factors)
        val mbti = mbtiPart(aMbti, bMbti, factors)
        val total = Math.round(SAJU_WEIGHT * saju + MBTI_WEIGHT * mbti).toInt()
        return PairScore(total, Math.round(saju).toInt(), Math.round(mbti).toInt(), factors)
    }

    private fun sajuPart(a: SajuSummary, b: SajuSummary, factors: MutableList<PairFactor>): Double {
        var score = BASE
        val ea = a.dayMasterElement
        val eb = b.dayMasterElement
        val pairLabel = listOf(ea, eb).sortedBy { it.ordinal }.joinToString("·") { it.ko }

        when {
            ea == eb -> {
                score += BIHWA
                factors += PairFactor("SAJU_BIHWA", "일간 오행 비화($pairLabel) — 결이 같은 편안함", BIHWA)
            }
            SajuRelations.generates(ea, eb) || SajuRelations.generates(eb, ea) -> {
                score += SANGSAENG
                factors += PairFactor("SAJU_SANGSAENG", "일간 오행 상생($pairLabel) — 서로 밀어주는 흐름", SANGSAENG)
            }
            SajuRelations.overcomes(ea, eb) || SajuRelations.overcomes(eb, ea) -> {
                score += SANGGEUK
                factors += PairFactor("SAJU_SANGGEUK", "일간 오행 상극($pairLabel) — 부딪히기 쉬운 조합", SANGGEUK)
            }
        }

        val stems = listOf(a.dayMasterStem, b.dayMasterStem).sorted().joinToString("")
        if (SajuRelations.isStemHap(a.dayMasterStem, b.dayMasterStem)) {
            score += STEM_HAP
            factors += PairFactor("SAJU_STEM_HAP", "천간합($stems) — 끌리는 합", STEM_HAP)
        } else if (SajuRelations.isStemChung(a.dayMasterStem, b.dayMasterStem)) {
            score += STEM_CHUNG
            factors += PairFactor("SAJU_STEM_CHUNG", "천간충($stems) — 튀는 긴장감", STEM_CHUNG)
        }

        val complemented = Element.entries.count { a.lacks(it) && b.has(it) } +
            Element.entries.count { b.lacks(it) && a.has(it) }
        if (complemented > 0) {
            val delta = min(complemented * COMPLEMENT_EACH, COMPLEMENT_CAP)
            score += delta
            factors += PairFactor("SAJU_COMPLEMENT", "오행 보완 ${complemented}건 — 부족한 기운을 서로 채움", delta)
        }

        return score.coerceIn(0.0, 100.0)
    }

    private fun mbtiPart(a: Mbti, b: Mbti, factors: MutableList<PairFactor>): Double {
        var score = BASE

        fun add(code: String, label: String, delta: Double) {
            score += delta
            factors += PairFactor(code, label, delta)
        }

        when {
            a.extraverted != b.extraverted -> add("MBTI_EI_MIX", "E·I 조합 — 에너지 보완", 8.0)
            a.extraverted -> add("MBTI_EE", "둘 다 E — 시끌벅적 시너지", 5.0)
            else -> add("MBTI_II", "둘 다 I — 조용한 안정", 3.0)
        }
        if (a.sensing == b.sensing) add("MBTI_SN_MATCH", "S/N 일치 — 대화가 잘 통함", 12.0)
        else add("MBTI_SN_GAP", "S/N 불일치 — 관점 차이로 소통 비용", -8.0)
        if (a.thinking != b.thinking) add("MBTI_TF_MIX", "T·F 조합 — 논리와 공감의 균형", 6.0)
        else add("MBTI_TF_MATCH", "T/F 일치 — 판단 기준이 같음", 3.0)
        when {
            a.judging != b.judging -> add("MBTI_JP_MIX", "J·P 조합 — 계획과 유연의 보완", 5.0)
            a.judging -> add("MBTI_JJ", "둘 다 J — 착착 맞는 일정", 6.0)
            else -> add("MBTI_PP", "둘 다 P — 마감은 누가 챙기지?", -4.0)
        }

        return score.coerceIn(0.0, 100.0)
    }
}
