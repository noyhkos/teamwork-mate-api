package dev.teamworkmate.api.team

import com.jayway.jsonpath.JsonPath
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.Test
import kotlin.test.assertFalse

@SpringBootTest
@AutoConfigureMockMvc
class TeamApiTest(@Autowired val mvc: MockMvc) {

    @Test
    fun `create team, then read it back with the one token`() {
        val body = mvc.perform(
            post("/api/teams").contentType(MediaType.APPLICATION_JSON).content("""{"name":"스터디팀"}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.teamId").isNotEmpty)
            .andExpect(jsonPath("$.token").isNotEmpty)
            .andReturn().response.contentAsString
        val token = JsonPath.read<String>(body, "$.token")

        val view = mvc.perform(get("/api/teams/$token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("스터디팀"))
            .andExpect(jsonPath("$.status").value("collecting"))
            .andExpect(jsonPath("$.memberCount").value(0))
            .andExpect(jsonPath("$.shareSlug").doesNotExist())
            .andReturn().response.contentAsString

        // the token is the capability — the view must not echo it back into shared UI
        assertFalse(view.contains(token))
    }

    @Test
    fun `unknown token is 404`() {
        mvc.perform(get("/api/teams/nope")).andExpect(status().isNotFound)
    }

    @Test
    fun `a team must be named`() {
        // The waiting screen puts the name in its title slot, so a nameless
        // team would render as a hole rather than a team.
        listOf("", "{}", """{"name":""}""", """{"name":"   "}""").forEach { body ->
            mvc.perform(
                post("/api/teams").contentType(MediaType.APPLICATION_JSON).content(body),
            ).andExpect(status().isBadRequest)
        }
    }

    @Test
    fun `the name is trimmed on the way in`() {
        val body = mvc.perform(
            post("/api/teams").contentType(MediaType.APPLICATION_JSON).content("""{"name":"  여백팀  "}"""),
        ).andExpect(status().isCreated).andReturn().response.contentAsString

        mvc.perform(get("/api/teams/${JsonPath.read<String>(body, "$.token")}"))
            .andExpect(jsonPath("$.name").value("여백팀"))
    }
}
