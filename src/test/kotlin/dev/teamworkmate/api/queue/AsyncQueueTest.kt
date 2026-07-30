package dev.teamworkmate.api.queue

import com.jayway.jsonpath.JsonPath
import dev.teamworkmate.api.analysis.FakeCalcConfig
import dev.teamworkmate.api.analysis.GOLDEN
import dev.teamworkmate.api.facts.CalcPort
import dev.teamworkmate.api.team.Member
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Exercises the real hand-off: the request thread returns 202 while a background
 * worker finishes the job. The direct-queue suites cannot prove this.
 */
@SpringBootTest(properties = ["llm.gemini.api-key=", "queue.mode=memory"])
@AutoConfigureMockMvc
@Import(FakeCalcConfig::class)
class AsyncQueueTest(@Autowired val mvc: MockMvc) {

    private fun status(admin: String): String =
        JsonPath.read(mvc.perform(get("/api/teams/admin/$admin")).andReturn().response.contentAsString, "$.status")

    @Test
    fun `analyze returns immediately and a background worker finishes the job`() {
        val body = mvc.perform(post("/api/teams").contentType(MediaType.APPLICATION_JSON).content("""{"name":"비동기팀"}"""))
            .andReturn().response.contentAsString
        val invite = JsonPath.read<String>(body, "$.inviteToken")
        val admin = JsonPath.read<String>(body, "$.adminToken")
        listOf("ENTJ", "ISFP").forEachIndexed { i, mbti ->
            mvc.perform(
                post("/api/teams/invite/$invite/members").contentType(MediaType.APPLICATION_JSON)
                    .content("""{"nickname":"멤버$i","birthDate":"2000-01-27","birthTime":"10:30","gender":"M","mbti":"$mbti"}"""),
            ).andExpect(status().isCreated)
        }

        val startedAt = System.nanoTime()
        mvc.perform(post("/api/teams/admin/$admin/analyze")).andExpect(status().isAccepted)
        val submitMs = (System.nanoTime() - startedAt) / 1_000_000
        // The pipeline itself takes far longer than this; a slow return means the
        // work leaked back onto the request thread.
        kotlin.test.assertTrue(submitMs < 1_000, "submit should return promptly, took ${submitMs}ms")

        repeat(100) {
            if (status(admin) == "done") {
                mvc.perform(get("/api/teams/invite/$invite/report")).andExpect(status().isOk)
                return
            }
            Thread.sleep(100)
        }
        fail("worker did not finish within 10s (status=${status(admin)})")
    }

}

/** Holds the worker inside the pipeline so "still processing" is a fact, not a race. */
@TestConfiguration
class GatedCalcConfig {
    val gate = CountDownLatch(1)

    @Bean
    @Primary
    fun gatedCalcPort(om: ObjectMapper): CalcPort = object : CalcPort {
        override fun fetchFacts(member: Member, now: Instant): JsonNode {
            gate.await(10, TimeUnit.SECONDS)
            return om.readTree(GOLDEN)
        }
    }
}

@SpringBootTest(properties = ["llm.gemini.api-key=", "queue.mode=memory"])
@AutoConfigureMockMvc
@Import(GatedCalcConfig::class)
class ConcurrentSubmitTest(@Autowired val mvc: MockMvc, @Autowired val gated: GatedCalcConfig) {

    @Test
    fun `duplicate submit while the worker is mid-flight is rejected`() {
        val body = mvc.perform(post("/api/teams").contentType(MediaType.APPLICATION_JSON).content("""{"name":"중복팀"}"""))
            .andReturn().response.contentAsString
        val invite = JsonPath.read<String>(body, "$.inviteToken")
        val admin = JsonPath.read<String>(body, "$.adminToken")
        listOf("ENTJ", "ISFP").forEachIndexed { i, mbti ->
            mvc.perform(
                post("/api/teams/invite/$invite/members").contentType(MediaType.APPLICATION_JSON)
                    .content("""{"nickname":"멤버$i","birthDate":"2000-01-27","birthTime":"10:30","gender":"M","mbti":"$mbti"}"""),
            ).andExpect(status().isCreated)
        }

        try {
            mvc.perform(post("/api/teams/admin/$admin/analyze")).andExpect(status().isAccepted)
            // The claim commits before the hand-off, so the second submit sees `processing`.
            val second = mvc.perform(post("/api/teams/admin/$admin/analyze")).andReturn().response.status
            assertEquals(409, second)
        } finally {
            gated.gate.countDown() // let the worker drain before the context closes
        }
    }
}
