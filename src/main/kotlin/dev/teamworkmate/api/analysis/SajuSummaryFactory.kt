package dev.teamworkmate.api.analysis

import dev.teamworkmate.api.facts.SajuFacts
import dev.teamworkmate.api.domain.saju.DayStrength
import dev.teamworkmate.api.domain.saju.SajuSummary
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

/** Rehydrates the pure-domain SajuSummary from a persisted SajuFacts row. */
object SajuSummaryFactory {

    fun fromFacts(facts: SajuFacts, om: ObjectMapper): SajuSummary {
        val elements = mapOf(
            "목" to facts.woodCnt,
            "화" to facts.fireCnt,
            "토" to facts.earthCnt,
            "금" to facts.metalCnt,
            "수" to facts.waterCnt,
        )
        val gods = collectStrings(om.readTree(facts.tenGods))
        val strength = facts.dayStrength?.let {
            when (om.readTree(it).path("strength").stringValue()) {
                "strong" -> DayStrength.STRONG
                "weak" -> DayStrength.WEAK
                else -> DayStrength.NEUTRAL
            }
        }
        return SajuSummary.fromRaw(
            dayMasterStem = facts.dayMaster,
            elementsKo = elements,
            gods = gods,
            strength = strength,
            birthTimeKnown = facts.birthTimeKnown,
        )
    }

    // tenGods JSON is nested {pillar:{stem,branch}}; non-god placeholders are filtered by fromRaw.
    private fun collectStrings(node: JsonNode): List<String> = when {
        node.isString -> listOf(node.stringValue())
        node.isObject || node.isArray -> node.values().flatMap { collectStrings(it) }
        else -> emptyList()
    }
}
