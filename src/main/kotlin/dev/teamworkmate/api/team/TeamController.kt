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

/** Member view — must never leak the admin token nor other members' birth data. */
data class TeamInviteView(
    val name: String?,
    val status: TeamStatus,
    val memberCount: Int,
    val members: List<String>, // nicknames only
    val shareSlug: String?, // set once analysis is done
)

data class AdminMemberView(
    val id: String,
    val nickname: String,
    val birthDate: String,
    val birthTime: String?,
    val gender: String,
    val mbti: String,
    val enteredBy: String,
)

/** Admin view — includes the invite token for re-sharing and full member details. */
data class TeamAdminView(
    val name: String?,
    val status: TeamStatus,
    val inviteToken: String,
    val members: List<AdminMemberView>,
    val shareSlug: String?,
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
        return TeamCreatedResponse(team.id.toString(), team.inviteToken, team.adminToken)
    }

    @GetMapping("/invite/{token}")
    fun byInvite(@PathVariable token: String): TeamInviteView {
        val team = service.byInviteToken(token)
        val members = memberService.listFor(team.id)
        return TeamInviteView(team.name, team.status, members.size, members.map { it.nickname }, team.shareSlug)
    }

    @GetMapping("/admin/{token}")
    fun byAdmin(@PathVariable token: String): TeamAdminView {
        val team = service.byAdminToken(token)
        val members = memberService.listFor(team.id).map {
            AdminMemberView(
                id = it.id.toString(),
                nickname = it.nickname,
                birthDate = it.birthDate.toString(),
                birthTime = it.birthTime?.toString(),
                gender = it.gender,
                mbti = it.mbti,
                enteredBy = it.enteredBy,
            )
        }
        return TeamAdminView(team.name, team.status, team.inviteToken, members, team.shareSlug)
    }
}
