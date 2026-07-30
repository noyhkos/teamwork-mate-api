package dev.teamworkmate.api.team

import com.jayway.jsonpath.JsonPath
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse

@SpringBootTest
@AutoConfigureMockMvc
class MemberApiTest(
    @Autowired val mvc: MockMvc,
    @Autowired val teamRepository: TeamRepository,
) {

    private fun createTeam(): Pair<String, String> {
        val body = mvc.perform(post("/api/teams").contentType(MediaType.APPLICATION_JSON).content("""{"name":"멤버테스트"}"""))
            .andReturn().response.contentAsString
        return JsonPath.read<String>(body, "$.inviteToken") to JsonPath.read<String>(body, "$.adminToken")
    }

    private fun memberJson(nickname: String, birthTime: String? = "\"10:30\"") = """
        {"nickname":"$nickname","birthDate":"2000-01-27","birthTime":$birthTime,
         "gender":"M","mbti":"entj","calendar":"solar"}
    """.trimIndent()

    @Test
    fun `self and admin entry both work and are tracked`() {
        val (invite, admin) = createTeam()

        mvc.perform(post("/api/teams/invite/$invite/members").contentType(MediaType.APPLICATION_JSON).content(memberJson("석현")))
            .andExpect(status().isCreated)

        mvc.perform(post("/api/teams/admin/$admin/members").contentType(MediaType.APPLICATION_JSON).content(memberJson("영희", birthTime = "null")))
            .andExpect(status().isCreated)

        // invite view: nicknames only, no birth data, count updated
        val inviteBody = mvc.perform(get("/api/teams/invite/$invite"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.memberCount").value(2))
            .andExpect(jsonPath("$.members[0]").value("석현"))
            .andReturn().response.contentAsString
        assertFalse(inviteBody.contains("2000-01-27"))

        // admin view: full details incl. enteredBy + normalized MBTI
        mvc.perform(get("/api/teams/admin/$admin"))
            .andExpect(jsonPath("$.members.length()").value(2))
            .andExpect(jsonPath("$.members[0].mbti").value("ENTJ"))
            .andExpect(jsonPath("$.members[0].enteredBy").value("self"))
            .andExpect(jsonPath("$.members[1].enteredBy").value("admin"))
            .andExpect(jsonPath("$.members[1].birthTime").value(null))
    }

    @Test
    fun `duplicate nickname in one team is 409`() {
        val (invite, _) = createTeam()
        mvc.perform(post("/api/teams/invite/$invite/members").contentType(MediaType.APPLICATION_JSON).content(memberJson("중복")))
            .andExpect(status().isCreated)
        mvc.perform(post("/api/teams/invite/$invite/members").contentType(MediaType.APPLICATION_JSON).content(memberJson("중복")))
            .andExpect(status().isConflict)
    }

    @Test
    fun `invalid mbti is 400`() {
        val (invite, _) = createTeam()
        val bad = """{"nickname":"x","birthDate":"2000-01-01","gender":"M","mbti":"ABCD"}"""
        mvc.perform(post("/api/teams/invite/$invite/members").contentType(MediaType.APPLICATION_JSON).content(bad))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `adding members is blocked unless team is collecting`() {
        val (invite, _) = createTeam()
        val team = teamRepository.findByInviteToken(invite)!!
        team.status = TeamStatus.processing
        teamRepository.save(team)

        mvc.perform(post("/api/teams/invite/$invite/members").contentType(MediaType.APPLICATION_JSON).content(memberJson("늦은사람")))
            .andExpect(status().isConflict)
    }

    @Test
    fun `admin token cannot be used on the invite member endpoint`() {
        val (_, admin) = createTeam()
        // capability boundary: tokens are not interchangeable
        mvc.perform(post("/api/teams/invite/$admin/members").contentType(MediaType.APPLICATION_JSON).content(memberJson("경계")))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `unknown team member add is 404`() {
        mvc.perform(post("/api/teams/invite/${UUID.randomUUID()}/members").contentType(MediaType.APPLICATION_JSON).content(memberJson("유령")))
            .andExpect(status().isNotFound)
    }
}
