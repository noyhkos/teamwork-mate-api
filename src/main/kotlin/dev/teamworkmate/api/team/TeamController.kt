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

/** Returned once at creation — the caller must save both URLs. */
data class TeamCreatedResponse(
    val teamId: String,
    val inviteToken: String,
    val adminToken: String,
)

/** Member view — must never leak the admin token. */
data class TeamInviteView(
    val name: String?,
    val status: TeamStatus,
)

/** Admin view — includes the invite token for re-sharing. */
data class TeamAdminView(
    val name: String?,
    val status: TeamStatus,
    val inviteToken: String,
)

@RestController
@RequestMapping("/api/teams")
class TeamController(private val service: TeamService) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody(required = false) req: CreateTeamRequest?): TeamCreatedResponse {
        val team = service.create(req?.name)
        return TeamCreatedResponse(team.id.toString(), team.inviteToken, team.adminToken)
    }

    @GetMapping("/invite/{token}")
    fun byInvite(@PathVariable token: String): TeamInviteView {
        val team = service.byInviteToken(token)
        return TeamInviteView(team.name, team.status)
    }

    @GetMapping("/admin/{token}")
    fun byAdmin(@PathVariable token: String): TeamAdminView {
        val team = service.byAdminToken(token)
        return TeamAdminView(team.name, team.status, team.inviteToken)
    }
}
