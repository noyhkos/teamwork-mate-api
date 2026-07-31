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
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@SpringBootTest
@AutoConfigureMockMvc
class MemberApiTest(
    @Autowired val mvc: MockMvc,
    @Autowired val teamRepository: TeamRepository,
    @Autowired val memberService: MemberService,
) {

    private fun createTeam(): String {
        val body = mvc.perform(post("/api/teams").contentType(MediaType.APPLICATION_JSON).content("""{"name":"멤버테스트"}"""))
            .andReturn().response.contentAsString
        return JsonPath.read<String>(body, "$.token")
    }

    private fun memberJson(nickname: String, birthTime: String? = "\"10:30\"") = """
        {"nickname":"$nickname","birthDate":"2000-01-27","birthTime":$birthTime,
         "gender":"M","mbti":"entj","calendar":"solar"}
    """.trimIndent()

    @Test
    fun `anyone with the link can add a member, for themselves or someone else`() {
        val token = createTeam()

        mvc.perform(post("/api/teams/$token/members").contentType(MediaType.APPLICATION_JSON).content(memberJson("석현")))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.nickname").value("석현"))

        mvc.perform(post("/api/teams/$token/members").contentType(MediaType.APPLICATION_JSON).content(memberJson("영희", birthTime = "null")))
            .andExpect(status().isCreated)

        // the roster carries what the waiting screen renders — and nothing else
        val view = mvc.perform(get("/api/teams/$token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.memberCount").value(2))
            .andExpect(jsonPath("$.members[0].nickname").value("석현"))
            .andExpect(jsonPath("$.members[0].birthDate").value("2000-01-27"))
            .andExpect(jsonPath("$.members[0].mbti").value("ENTJ"))
            .andExpect(jsonPath("$.members[1].nickname").value("영희"))
            .andReturn().response.contentAsString

        // birth time, gender and calendar are calc inputs, never roster fields
        assertFalse(view.contains("10:30"))
        assertFalse(view.contains("\"gender\""))
        assertFalse(view.contains("\"calendar\""))
    }

    @Test
    fun `duplicate nickname in one team is 409`() {
        val token = createTeam()
        mvc.perform(post("/api/teams/$token/members").contentType(MediaType.APPLICATION_JSON).content(memberJson("중복")))
            .andExpect(status().isCreated)
        mvc.perform(post("/api/teams/$token/members").contentType(MediaType.APPLICATION_JSON).content(memberJson("중복")))
            .andExpect(status().isConflict)
    }

    @Test
    fun `invalid mbti is 400`() {
        val token = createTeam()
        val bad = """{"nickname":"x","birthDate":"2000-01-01","gender":"M","mbti":"ABCD"}"""
        mvc.perform(post("/api/teams/$token/members").contentType(MediaType.APPLICATION_JSON).content(bad))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `mbti is normalized on the way in`() {
        val token = createTeam()
        mvc.perform(post("/api/teams/$token/members").contentType(MediaType.APPLICATION_JSON).content(memberJson("소문자")))
            .andExpect(status().isCreated)

        val team = teamRepository.findByAccessToken(token)!!
        assertEquals("ENTJ", memberService.listFor(team.id).single().mbti)
    }

    @Test
    fun `adding members is blocked unless team is collecting`() {
        val token = createTeam()
        val team = teamRepository.findByAccessToken(token)!!
        team.status = TeamStatus.processing
        teamRepository.save(team)

        mvc.perform(post("/api/teams/$token/members").contentType(MediaType.APPLICATION_JSON).content(memberJson("늦은사람")))
            .andExpect(status().isConflict)
    }

    @Test
    fun `unknown team member add is 404`() {
        mvc.perform(post("/api/teams/${UUID.randomUUID()}/members").contentType(MediaType.APPLICATION_JSON).content(memberJson("유령")))
            .andExpect(status().isNotFound)
    }
}
