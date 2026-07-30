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
    fun `create team, then read via invite and admin capabilities`() {
        val body = mvc.perform(
            post("/api/teams").contentType(MediaType.APPLICATION_JSON).content("""{"name":"스터디팀"}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.inviteToken").isNotEmpty)
            .andExpect(jsonPath("$.adminToken").isNotEmpty)
            .andReturn().response.contentAsString

        val invite = JsonPath.read<String>(body, "$.inviteToken")
        val admin = JsonPath.read<String>(body, "$.adminToken")

        val inviteBody = mvc.perform(get("/api/teams/invite/$invite"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("스터디팀"))
            .andExpect(jsonPath("$.status").value("collecting"))
            .andReturn().response.contentAsString

        // capability boundary: the member view must never leak the admin token
        assertFalse(inviteBody.contains(admin))

        mvc.perform(get("/api/teams/admin/$admin"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.inviteToken").value(invite))
    }

    @Test
    fun `unknown tokens are 404`() {
        mvc.perform(get("/api/teams/invite/nope")).andExpect(status().isNotFound)
        mvc.perform(get("/api/teams/admin/nope")).andExpect(status().isNotFound)
    }

    @Test
    fun `create works without a body`() {
        mvc.perform(post("/api/teams"))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.teamId").isNotEmpty)
    }
}
