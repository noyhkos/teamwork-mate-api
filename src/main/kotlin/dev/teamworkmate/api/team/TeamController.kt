package dev.teamworkmate.api.team

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

data class CreateTeamRequest(val name: String? = null)

/** Returned once at creation — losing this URL means losing the team. */
data class TeamCreatedResponse(val teamId: String, val token: String)

/**
 * The only team view there is. Nicknames only: nobody — not even the creator —
 * has a reason to read someone else's birth data back out of the service.
 */
data class TeamView(
    val name: String?,
    val status: TeamStatus,
    val memberCount: Int,
    val members: List<String>,
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
        return TeamView(team.name, team.status, members.size, members.map { it.nickname }, team.shareSlug)
    }
}
