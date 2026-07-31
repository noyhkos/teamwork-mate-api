package dev.teamworkmate.api.team

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

data class CreateTeamRequest(val name: String? = null)

/** Returned once at creation — losing this URL means losing the team. */
data class TeamCreatedResponse(val teamId: String, val token: String)

/**
 * What the roster shows about a member. Birth time, gender and calendar stay
 * behind the wall — they are only ever inputs to the calc. Birth date and MBTI
 * are here because the waiting screen shows them, which means anyone holding
 * the team link can read them for the whole team.
 */
data class TeamMemberView(
    val nickname: String,
    val birthDate: LocalDate,
    val mbti: String,
)

/** The only team view there is. */
data class TeamView(
    val name: String?,
    val status: TeamStatus,
    val memberCount: Int,
    val members: List<TeamMemberView>,
    val shareSlug: String?, // set once analysis is done
)

@RestController
@RequestMapping("/api/teams")
class TeamController(
    private val service: TeamService,
    private val memberService: MemberService,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody(required = false) req: CreateTeamRequest?): TeamCreatedResponse {
        val team = service.create(req?.name)
        return TeamCreatedResponse(team.id.toString(), team.accessToken)
    }

    @GetMapping("/{token}")
    fun byToken(@PathVariable token: String): TeamView {
        val team = service.byToken(token)
        val members = memberService.listFor(team.id)
        return TeamView(
            name = team.name,
            status = team.status,
            memberCount = members.size,
            members = members.map { TeamMemberView(it.nickname, it.birthDate, it.mbti) },
            shareSlug = team.shareSlug,
        )
    }
}
