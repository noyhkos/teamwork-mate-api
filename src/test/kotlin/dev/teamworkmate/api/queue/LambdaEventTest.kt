package dev.teamworkmate.api.queue

import com.jayway.jsonpath.JsonPath
import dev.teamworkmate.api.analysis.FakeCalcConfig
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.Test

/**
 * The Lambda Web Adapter delivers an SQS batch as a plain POST of the event JSON,
 * so the consumer is exercised exactly as the platform would drive it.
 */
@SpringBootTest(properties = ["llm.gemini.api-key=", "queue.consumer=lambda"])
@AutoConfigureMockMvc
@Import(FakeCalcConfig::class)
class LambdaEventTest(@Autowired val mvc: MockMvc) {

    private fun sqsEvent(vararg bodies: String): String =
        """{"Records":[${bodies.mapIndexed { i, b -> """{"messageId":"m$i","body":"$b"}""" }.joinToString(",")}]}"""

    private fun teamWithMembers(): Pair<String, String> {
        val body = mvc.perform(post("/api/teams").contentType(MediaType.APPLICATION_JSON).content("""{"name":"람다팀"}"""))
            .andReturn().response.contentAsString
        val teamId = JsonPath.read<String>(body, "$.teamId")
        val token = JsonPath.read<String>(body, "$.token")
        listOf("ENTJ", "ISFP", "INFJ").forEachIndexed { i, mbti ->
            mvc.perform(
                post("/api/teams/$token/members").contentType(MediaType.APPLICATION_JSON)
                    .content("""{"nickname":"멤버$i","birthDate":"2000-01-27","birthTime":"10:30","gender":"M","mbti":"$mbti"}"""),
            ).andExpect(status().isCreated)
        }
        return teamId to token
    }

    @Test
    fun `an SQS batch runs the job and reports no failures`() {
        val (teamId, token) = teamWithMembers()

        mvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(sqsEvent(teamId)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.batchItemFailures.length()").value(0))

        mvc.perform(get("/api/teams/$token")).andExpect(jsonPath("$.status").value("done"))
        mvc.perform(get("/api/teams/$token/report")).andExpect(status().isOk)
    }

    @Test
    fun `a malformed body is reported so redrive can send it to the DLQ`() {
        mvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(sqsEvent("not-a-uuid")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.batchItemFailures.length()").value(1))
            .andExpect(jsonPath("$.batchItemFailures[0].itemIdentifier").value("m0"))
    }

    @Test
    fun `one bad message does not sink the rest of the batch`() {
        val (teamId, token) = teamWithMembers()

        mvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(sqsEvent("nope", teamId)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.batchItemFailures.length()").value(1))
            .andExpect(jsonPath("$.batchItemFailures[0].itemIdentifier").value("m0"))

        mvc.perform(get("/api/teams/$token")).andExpect(jsonPath("$.status").value("done"))
    }

    @Test
    fun `health endpoint answers for the readiness check`() {
        mvc.perform(get("/healthz")).andExpect(status().isOk).andExpect(jsonPath("$.ok").value(true))
    }
}
