package dev.teamworkmate.api.llm

import com.jayway.jsonpath.JsonPath
import dev.teamworkmate.api.analysis.FakeCalcConfig
import dev.teamworkmate.api.team.TeamRepository
import org.junit.jupiter.api.BeforeEach
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Scriptable LlmPort. Kind is detected from the prompt's output-format marker;
 * subjects are scraped from the <근거> block only, so the fixture can never echo
 * back a nickname the grounding did not contain.
 */
class ScriptedLlm : LlmPort {
    override val enabled = true

    var judgeFaithful = true
    val judgeVerdicts = ArrayDeque<Boolean>() // consumed first, per judge call
    var failGeneration = false
    var failJudge = false
    var malformedJudge = false
    var failKinds: Set<String> = emptySet()
    var omitNickname: String? = null

    override fun complete(model: String, prompt: String, schemaJson: String): String {
        if (prompt.contains("사실 검증관")) {
            if (failJudge) throw RuntimeException("simulated judge outage")
            if (malformedJudge) return """{"ok":true}""" // schema-shaped but missing faithful/claims
            val faithful = if (judgeVerdicts.isNotEmpty()) judgeVerdicts.removeFirst() else judgeFaithful
            return if (faithful) {
                """{"claims":[{"subject":"team","text":"근거 내 사실","verdict":"supported"}],"faithful":true}"""
            } else {
                """{"claims":[{"subject":"team","text":"근거에 없는 주장","verdict":"unsupported"}],"faithful":false}"""
            }
        }

        val kind = kindOf(prompt)
        if (failGeneration || kind in failKinds) throw RuntimeException("simulated LLM outage")

        val grounding = prompt.substringAfter("<근거>").substringBefore("</근거>")
        return when (kind) {
            "role_reasons" -> {
                val nicknames = Regex("\"nickname\":\"([^\"]+)\"").findAll(grounding)
                    .map { it.groupValues[1] }.distinct().filter { it != omitNickname }
                """{"reasons":[${nicknames.joinToString(",") { """{"nickname":"$it","text":"LLM: $it 역할 문구"}""" }}]}"""
            }
            "pair_chemistry" -> {
                val keys = Regex("\"key\":\"(best|worst)\"").findAll(grounding)
                    .map { it.groupValues[1] }.distinct()
                """{"pairs":[${keys.joinToString(",") { """{"key":"$it","text":"LLM: $it 페어 문구"}""" }}]}"""
            }
            else -> """{"text":"LLM: 팀 소개 문구"}"""
        }
    }

    private fun kindOf(prompt: String): String = when {
        prompt.contains("\"reasons\"") -> "role_reasons"
        prompt.contains("\"pairs\"") -> "pair_chemistry"
        else -> "team_intro"
    }
}

@TestConfiguration
class FakeLlmConfig {
    @Bean
    @Primary
    fun scriptedLlm(): ScriptedLlm = ScriptedLlm()
}

@SpringBootTest
@AutoConfigureMockMvc
@Import(FakeCalcConfig::class, FakeLlmConfig::class)
class LlmPhraseApiTest(
    @Autowired val mvc: MockMvc,
    @Autowired val llm: ScriptedLlm,
    @Autowired val teams: TeamRepository,
    @Autowired val generations: LlmGenerationRepository,
    @Autowired val evals: EvalResultRepository,
) {

    @BeforeEach
    fun reset() {
        llm.judgeFaithful = true
        llm.judgeVerdicts.clear()
        llm.failGeneration = false
        llm.failJudge = false
        llm.malformedJudge = false
        llm.failKinds = emptySet()
        llm.omitNickname = null
    }

    /** Third member has no birth time — exercises the birthTimeKnown=false grounding path. */
    private fun setupTeam(): Pair<String, String> {
        val body = mvc.perform(post("/api/teams").contentType(MediaType.APPLICATION_JSON).content("""{"name":"문구팀"}"""))
            .andReturn().response.contentAsString
        val invite = JsonPath.read<String>(body, "$.inviteToken")
        val admin = JsonPath.read<String>(body, "$.adminToken")
        listOf("ENTJ" to "10:30", "ISFP" to "22:10", "ESFJ" to null).forEachIndexed { i, (mbti, time) ->
            val timeField = time?.let { ""","birthTime":"$it"""" } ?: ""
            mvc.perform(
                post("/api/teams/invite/$invite/members").contentType(MediaType.APPLICATION_JSON)
                    .content("""{"nickname":"멤버$i","birthDate":"2000-01-27"$timeField,"gender":"M","mbti":"$mbti"}"""),
            ).andExpect(status().isCreated)
        }
        return invite to admin
    }

    private fun analyze(admin: String, expectedPhrases: String): String =
        mvc.perform(post("/api/teams/admin/$admin/analyze"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.phrases").value(expectedPhrases))
            .andReturn().response.contentAsString
            .let { JsonPath.read<String>(it, "$.shareSlug") }

    private fun generationsFor(slug: String) =
        generations.findByTeamIdOrderByCreatedAt(teams.findByShareSlug(slug)!!.id)

    @Test
    fun `accepted path - judged text is served and audited`() {
        val (invite, admin) = setupTeam()
        val slug = analyze(admin, "llm")

        mvc.perform(get("/api/teams/invite/$invite/report"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.llmPhrases").value(true))
            .andExpect(jsonPath("$.intro").value("LLM: 팀 소개 문구"))
            .andExpect(jsonPath("$.roles[0].reason").value(org.hamcrest.Matchers.startsWith("LLM:")))
            .andExpect(jsonPath("$.bestPair.reason").value("LLM: best 페어 문구"))
            .andExpect(jsonPath("$.worstPair.reason").value("LLM: worst 페어 문구"))

        val gens = generationsFor(slug)
        assertEquals(3, gens.size)
        assertTrue(gens.all { it.status == "accepted" && it.attempt == 1 && it.output != null })
        assertEquals(setOf("role_reasons", "pair_chemistry", "team_intro"), gens.map { it.kind }.toSet())
        val evalRows = evals.findByGenerationIdIn(gens.map { it.id })
        assertEquals(3, evalRows.size)
        assertTrue(evalRows.all { it.faithful })
    }

    @Test
    fun `grounding never leaks unknown hour facts for a member without birth time`() {
        val (_, admin) = setupTeam()
        val slug = analyze(admin, "llm")

        // jsonb normalizes key order and spacing, so match tolerantly.
        val roleGrounding = generationsFor(slug).first { it.kind == "role_reasons" }.grounding
        val strengthOf = { nick: String ->
            Regex("\"nickname\":\\s*\"$nick\".*?\"strength\":\\s*(null|\"[^\"]*\")")
                .find(roleGrounding)?.groupValues?.get(1)
        }
        // 멤버2 has no birth time: strength must be null so the prompt forbids hour talk.
        assertEquals("null", strengthOf("멤버2"), "strength must be null when birth time is unknown")
        assertEquals("\"중화\"", strengthOf("멤버0"), "timed members still carry strength")
    }

    @Test
    fun `judge rejects - report falls back to template and retry carries feedback`() {
        llm.judgeFaithful = false
        val (invite, admin) = setupTeam()
        val slug = analyze(admin, "failed")

        mvc.perform(get("/api/teams/invite/$invite/report"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.llmPhrases").value(false))
            .andExpect(jsonPath("$.intro").doesNotExist())
            .andExpect(jsonPath("$.roles[0].reason").value(org.hamcrest.Matchers.containsString("적합도")))
            .andExpect(jsonPath("$.bestPair.reason").doesNotExist())

        val gens = generationsFor(slug)
        assertEquals(6, gens.size) // 3 kinds x 2 attempts
        assertTrue(gens.all { it.status == "rejected" })
        val second = gens.filter { it.attempt == 2 }
        assertEquals(3, second.size)
        assertTrue(second.all { it.prompt.contains("[재시도 피드백]") && it.prompt.contains("근거에 없는 주장") })
        assertTrue(gens.filter { it.attempt == 1 }.none { it.prompt.contains("[재시도 피드백]") })
        val evalRows = evals.findByGenerationIdIn(gens.map { it.id })
        assertEquals(6, evalRows.size)
        assertTrue(evalRows.none { it.faithful })
    }

    @Test
    fun `judge rejects then accepts - second attempt is served`() {
        // per kind: attempt-1 unfaithful, attempt-2 faithful (3 kinds run sequentially)
        llm.judgeVerdicts.addAll(listOf(false, true, false, true, false, true))
        val (invite, admin) = setupTeam()
        val slug = analyze(admin, "llm")

        mvc.perform(get("/api/teams/invite/$invite/report"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.intro").value("LLM: 팀 소개 문구"))

        val gens = generationsFor(slug)
        assertEquals(6, gens.size)
        assertEquals(3, gens.count { it.status == "accepted" && it.attempt == 2 })
        assertEquals(3, gens.count { it.status == "rejected" && it.attempt == 1 })
    }

    @Test
    fun `one kind fails - other kinds still serve LLM text (partial)`() {
        llm.failKinds = setOf("pair_chemistry")
        val (invite, admin) = setupTeam()
        val slug = analyze(admin, "partial")

        mvc.perform(get("/api/teams/invite/$invite/report"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.llmPhrases").value(true))
            .andExpect(jsonPath("$.intro").value("LLM: 팀 소개 문구"))
            .andExpect(jsonPath("$.roles[0].reason").value(org.hamcrest.Matchers.startsWith("LLM:")))
            .andExpect(jsonPath("$.bestPair.reason").doesNotExist()) // failed kind only

        val gens = generationsFor(slug)
        assertEquals(2, gens.count { it.kind == "pair_chemistry" && it.status == "failed" })
        assertEquals(1, gens.count { it.kind == "role_reasons" && it.status == "accepted" })
        assertEquals(1, gens.count { it.kind == "team_intro" && it.status == "accepted" })
    }

    @Test
    fun `llm outage - analysis still succeeds, generations recorded as failed`() {
        llm.failGeneration = true
        val (invite, admin) = setupTeam()
        val slug = analyze(admin, "failed")

        mvc.perform(get("/api/teams/invite/$invite/report"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.roles[0].reason").isNotEmpty)

        val gens = generationsFor(slug)
        assertEquals(6, gens.size)
        assertTrue(gens.all { it.status == "failed" && it.output == null })
        assertTrue(evals.findByGenerationIdIn(gens.map { it.id }).isEmpty())
    }

    @Test
    fun `judge outage - unverified text is never served and retries stop`() {
        llm.failJudge = true
        val (invite, admin) = setupTeam()
        val slug = analyze(admin, "failed")

        mvc.perform(get("/api/teams/invite/$invite/report"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.llmPhrases").value(false))
            .andExpect(jsonPath("$.intro").doesNotExist())

        // one attempt per kind, rejected with output kept but no eval row — the
        // audit signature that distinguishes transport failure from a real verdict
        val gens = generationsFor(slug)
        assertEquals(3, gens.size)
        assertTrue(gens.all { it.status == "rejected" && it.attempt == 1 && it.output != null })
        assertTrue(evals.findByGenerationIdIn(gens.map { it.id }).isEmpty())
    }

    @Test
    fun `malformed judge JSON - nothing served, every call still audited`() {
        llm.malformedJudge = true
        val (invite, admin) = setupTeam()
        val slug = analyze(admin, "failed")

        mvc.perform(get("/api/teams/invite/$invite/report"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.llmPhrases").value(false))

        val gens = generationsFor(slug)
        assertEquals(6, gens.size) // missing "faithful" reads as false -> retried
        assertTrue(gens.all { it.status == "rejected" })
        // claims missing -> stored as an empty array, never as invalid jsonb
        assertTrue(evals.findByGenerationIdIn(gens.map { it.id }).all { it.claims == "[]" })
    }

    @Test
    fun `llm omits a member - role phrases fall back wholesale, no partial row updates`() {
        llm.omitNickname = "멤버1"
        val (invite, admin) = setupTeam()
        val slug = analyze(admin, "partial")

        mvc.perform(get("/api/teams/invite/$invite/report"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.roles[0].reason").value(org.hamcrest.Matchers.containsString("적합도")))
            .andExpect(jsonPath("$.intro").value("LLM: 팀 소개 문구"))

        assertEquals(2, generationsFor(slug).count { it.kind == "role_reasons" && it.status == "failed" })
    }
}
