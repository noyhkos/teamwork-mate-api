package dev.teamworkmate.api.analysis

import com.jayway.jsonpath.JsonPath
import dev.teamworkmate.api.facts.CalcPort
import dev.teamworkmate.api.team.Member
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import kotlin.test.Test

/** Golden calc response (석현 chart), reused for every member — differentiation comes from MBTI. */
private val GOLDEN = """
    {
      "birthTimeKnown": true,
      "solarDate": "2000-01-27",
      "pillars": {"year":{"ganzhi":"己卯"},"month":{"ganzhi":"丁丑"},"day":{"ganzhi":"甲申"},"hour":{"ganzhi":"己巳"}},
      "dayMaster": {"stem":"甲","stemKo":"갑","element":"목"},
      "fiveElements": {"목":2,"화":2,"토":3,"금":1,"수":0},
      "tenGods": {"year":{"stem":"정재","branch":"겁재"},"month":{"stem":"상관","branch":"정재"},
                  "day":{"stem":"(일간)","branch":"편관"},"hour":{"stem":"정재","branch":"식신"}},
      "dayStrength": {"strength":"neutral","score":62},
      "geukguk": "식상격",
      "samjae": {"inSamjae":true,"phase":"눌삼재","cycleBranches":["巳","午","未"],"targetYearGanzhi":"丙午"},
      "compactText": "compact",
      "fullCompactText": "full",
      "calcVersion": "ssaju@0.2.0"
    }
""".trimIndent()

/** Same chart with the hour pillar removed — mirrors calc's birthTimeKnown=false path. */
private val GOLDEN_NO_HOUR = """
    {
      "birthTimeKnown": false,
      "solarDate": "2000-01-27",
      "pillars": {"year":{"ganzhi":"己卯"},"month":{"ganzhi":"丁丑"},"day":{"ganzhi":"甲申"}},
      "dayMaster": {"stem":"甲","stemKo":"갑","element":"목"},
      "fiveElements": {"목":2,"화":1,"토":2,"금":1,"수":0},
      "tenGods": {"year":{"stem":"정재","branch":"겁재"},"month":{"stem":"상관","branch":"정재"},
                  "day":{"stem":"(일간)","branch":"편관"}},
      "dayStrength": null,
      "geukguk": null,
      "samjae": {"inSamjae":true,"phase":"눌삼재","cycleBranches":["巳","午","未"],"targetYearGanzhi":"丙午"},
      "compactText": "compact-no-hour",
      "calcVersion": "ssaju@0.2.0"
    }
""".trimIndent()

@TestConfiguration
class FakeCalcConfig {
    @Bean
    @Primary
    fun fakeCalcPort(om: ObjectMapper): CalcPort = object : CalcPort {
        override fun fetchFacts(member: Member, now: Instant): JsonNode =
            om.readTree(if (member.birthTime == null) GOLDEN_NO_HOUR else GOLDEN)
    }
}

// Force-blank the LLM key so this class always exercises the phrase-layer-disabled path.
@SpringBootTest(properties = ["llm.gemini.api-key="])
@AutoConfigureMockMvc
@Import(FakeCalcConfig::class)
class AnalysisApiTest(@Autowired val mvc: MockMvc) {

    private fun setupTeam(memberMbtis: List<String>): Pair<String, String> {
        val body = mvc.perform(post("/api/teams").contentType(MediaType.APPLICATION_JSON).content("""{"name":"분석팀"}"""))
            .andReturn().response.contentAsString
        val invite = JsonPath.read<String>(body, "$.inviteToken")
        val admin = JsonPath.read<String>(body, "$.adminToken")
        memberMbtis.forEachIndexed { i, mbti ->
            mvc.perform(
                post("/api/teams/invite/$invite/members").contentType(MediaType.APPLICATION_JSON)
                    .content("""{"nickname":"멤버$i","birthDate":"2000-01-27","birthTime":"10:30","gender":"M","mbti":"$mbti"}"""),
            ).andExpect(status().isCreated)
        }
        return invite to admin
    }

    @Test
    fun `full pipeline - analyze then report`() {
        val (invite, admin) = setupTeam(listOf("ENTJ", "ISFP", "ESFJ", "INTP"))

        mvc.perform(post("/api/teams/admin/$admin/analyze"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.members").value(4))
            .andExpect(jsonPath("$.pairs").value(6)) // C(4,2)
            .andExpect(jsonPath("$.archetype").isNotEmpty)
            .andExpect(jsonPath("$.shareSlug").isNotEmpty)
            .andExpect(jsonPath("$.phrases").value("skipped")) // no LLM key in this context

        val report = mvc.perform(get("/api/teams/invite/$invite/report"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.roles.length()").value(4))
            .andExpect(jsonPath("$.bestPair.total").isNumber)
            .andExpect(jsonPath("$.worstPair.factors").isArray)
            .andExpect(jsonPath("$.harmonyScore").isNumber)
            .andExpect(jsonPath("$.samjaeMembers.length()").value(4)) // golden chart is in samjae
            .andExpect(jsonPath("$.riskNote").isNotEmpty)
            .andExpect(jsonPath("$.llmPhrases").value(false))
            .andExpect(jsonPath("$.roles[0].reason").isNotEmpty) // deterministic template fallback
            .andReturn().response.contentAsString

        // roles are unique across 4 members
        val roles = JsonPath.read<List<String>>(report, "$.roles[*].role")
        kotlin.test.assertEquals(roles.toSet().size, roles.size)

        // public share link serves the same report
        val slug = JsonPath.read<String>(report, "$.shareSlug")
        mvc.perform(get("/api/reports/$slug"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.archetype").value(JsonPath.read<String>(report, "$.archetype")))
    }

    @Test
    fun `analyze is idempotent - rerun replaces scores`() {
        val (_, admin) = setupTeam(listOf("ENTJ", "ISFP"))
        mvc.perform(post("/api/teams/admin/$admin/analyze")).andExpect(status().isOk)
        mvc.perform(post("/api/teams/admin/$admin/analyze"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.pairs").value(1))
    }

    @Test
    fun `analyze needs at least 2 members`() {
        val (_, admin) = setupTeam(listOf("ENTJ"))
        mvc.perform(post("/api/teams/admin/$admin/analyze")).andExpect(status().isConflict)
    }

    @Test
    fun `report before analysis is 404`() {
        val (invite, _) = setupTeam(listOf("ENTJ", "ISFP"))
        mvc.perform(get("/api/teams/invite/$invite/report")).andExpect(status().isNotFound)
    }

    @Test
    fun `analyze requires the admin capability, not invite`() {
        val (invite, _) = setupTeam(listOf("ENTJ", "ISFP"))
        mvc.perform(post("/api/teams/admin/$invite/analyze")).andExpect(status().isNotFound)
    }
}
