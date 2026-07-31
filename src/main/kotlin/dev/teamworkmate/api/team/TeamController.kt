package dev.teamworkmate.api.team

import dev.teamworkmate.api.analysis.RoleScoreRepository
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

data class CreateTeamRequest(
    @field:NotBlank
    @field:Size(max = 40)
    val name: String,
)

/** Returned once at creation — losing this URL means losing the team. */
data class TeamCreatedResponse(val teamId: String, val token: String)

/**
 * What the roster shows about a member. Birth time, gender and calendar stay
 * behind the wall — they are only ever inputs to the calc. Birth date and MBTI
 * are here because the waiting screen shows them, which means anyone holding
 * the team link can read them for the whole team.
 */
data class TeamMemberView(
    val id: String,
    val nickname: String,
    val birthDate: LocalDate,
    val mbti: String,
)

/** The only team view there is. */
data class TeamView(
    val name: String,
    val status: TeamStatus,
    val memberCount: Int,
    val members: List<TeamMemberView>,
    val shareSlug: String?, // set once analysis is done
    // The report no longer describes the team on screen. Decided here rather
    // than in the browser, because the roster changes from other people's
    // devices, not this one.
    val staleReport: Boolean,
)

@RestController
@RequestMapping("/api/teams")
class TeamController(
    private val service: TeamService,
    private val memberService: MemberService,
    private val roleScores: RoleScoreRepository,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody req: CreateTeamRequest): TeamCreatedResponse {
        val team = service.create(req.name)
        return TeamCreatedResponse(team.id.toString(), team.accessToken)
    }

    @GetMapping("/{token}")
    fun byToken(@PathVariable token: String): TeamView {
        val team = service.byToken(token)
        val members = memberService.listFor(team.id)
        // Compare who the report describes with who is on the roster now. A
        // timestamp would only notice arrivals; this also notices departures
        // and a swap that leaves the count unchanged.
        val described = roleScores.findByTeamIdAndAssignedTrue(team.id).map { it.memberId }.toSet()
        return TeamView(
            name = team.name,
            status = team.status,
            memberCount = members.size,
            members = members.map { TeamMemberView(it.id.toString(), it.nickname, it.birthDate, it.mbti) },
            shareSlug = team.shareSlug,
            staleReport = team.shareSlug != null && described != members.map { it.id }.toSet(),
        )
    }
}
