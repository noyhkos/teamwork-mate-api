package dev.teamworkmate.api.facts

import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SajuFactsMapperTest {

    private val om = ObjectMapper()
    private val memberId = UUID.randomUUID()
    private val now = Instant.parse("2026-07-30T03:00:00Z")

    // Shape mirrors the real calc /saju response (golden chart, trimmed texts).
    private val fullResponse = """
        {
          "birthTimeKnown": true,
          "solarDate": "2000-01-27",
          "pillars": {"year":{"ganzhi":"己卯"},"month":{"ganzhi":"丁丑"},"day":{"ganzhi":"甲申"},"hour":{"ganzhi":"己巳"}},
          "dayMaster": {"stem":"甲","stemKo":"갑","element":"목"},
          "fiveElements": {"목":2,"화":2,"토":3,"금":1,"수":0},
          "tenGods": {"year":{"stem":"정재","branch":"겁재"}},
          "dayStrength": {"strength":"neutral","score":62},
          "geukguk": "식상격",
          "samjae": {"inSamjae":true,"phase":"눌삼재","cycleBranches":["巳","午","未"],"targetYearGanzhi":"丙午"},
          "compactText": "## 기본...",
          "fullCompactText": "## 원국...",
          "calcVersion": "ssaju@0.2.0"
        }
    """.trimIndent()

    @Test
    fun `maps full response`() {
        val e = SajuFactsMapper.toEntity(memberId, om.readTree(fullResponse), now)
        assertEquals(memberId, e.memberId)
        assertEquals(true, e.birthTimeKnown)
        assertEquals("甲", e.dayMaster)
        assertEquals(2, e.woodCnt)
        assertEquals(3, e.earthCnt)
        assertEquals(0, e.waterCnt)
        assertTrue(e.pillars.contains("甲申"))
        assertTrue(e.dayStrength!!.contains("62"))
        assertEquals("식상격", e.geukguk)
        assertTrue(e.samjae!!.contains("눌삼재"))
        assertEquals("ssaju@0.2.0", e.calcVersion)
        assertEquals(now, e.computedAt)
    }

    @Test
    fun `maps unknown-birth-time response with nulls intact`() {
        val json = om.readTree(
            fullResponse
                .replace("\"birthTimeKnown\": true", "\"birthTimeKnown\": false")
                .replace(""""dayStrength": {"strength":"neutral","score":62}""", "\"dayStrength\": null")
                .replace(""""geukguk": "식상격"""", "\"geukguk\": null")
                .replace(""""fullCompactText": "## 원국..."""", "\"fullCompactText\": null"),
        )
        val e = SajuFactsMapper.toEntity(memberId, json, now)
        assertEquals(false, e.birthTimeKnown)
        assertNull(e.dayStrength)
        assertNull(e.geukguk)
        assertNull(e.fullCompactText)
    }
}
