package dev.teamworkmate.api.facts

import tools.jackson.databind.JsonNode
import java.time.Instant
import java.util.UUID

/** Pure mapping from the calc service response JSON to the persistence entity. */
object SajuFactsMapper {

    fun toEntity(memberId: UUID, calcResponse: JsonNode, computedAt: Instant): SajuFacts {
        val elements = calcResponse.required("fiveElements")

        fun optionalJson(field: String): String? {
            val node = calcResponse.path(field)
            return if (node.isNull || node.isMissingNode) null else node.toString()
        }

        return SajuFacts(
            memberId = memberId,
            birthTimeKnown = calcResponse.required("birthTimeKnown").booleanValue(),
            dayMaster = calcResponse.required("dayMaster").required("stem").stringValue(),
            pillars = calcResponse.required("pillars").toString(),
            fiveElements = elements.toString(),
            woodCnt = elements.path("목").intValue(),
            fireCnt = elements.path("화").intValue(),
            earthCnt = elements.path("토").intValue(),
            metalCnt = elements.path("금").intValue(),
            waterCnt = elements.path("수").intValue(),
            tenGods = calcResponse.required("tenGods").toString(),
            dayStrength = optionalJson("dayStrength"),
            geukguk = calcResponse.path("geukguk").let { if (it.isNull || it.isMissingNode) null else it.stringValue() },
            samjae = optionalJson("samjae"),
            compactText = calcResponse.required("compactText").stringValue(),
            fullCompactText = calcResponse.path("fullCompactText")
                .let { if (it.isNull || it.isMissingNode) null else it.stringValue() },
            calcVersion = calcResponse.required("calcVersion").stringValue(),
            computedAt = computedAt,
        )
    }
}
