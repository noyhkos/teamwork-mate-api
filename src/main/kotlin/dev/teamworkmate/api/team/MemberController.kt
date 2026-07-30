package dev.teamworkmate.api.team

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.LocalTime

data class AddMemberRequest(
    @field:NotBlank
    val nickname: String,

    @field:NotNull
    val birthDate: LocalDate,

    val birthTime: LocalTime? = null,

    @field:Pattern(regexp = "M|F")
    val gender: String,

    @field:Pattern(regexp = "^[EIei][SNsn][TFtf][JPjp]$")
    val mbti: String,

    @field:Pattern(regexp = "solar|lunar")
    val calendar: String = "solar",

    val leapMonth: Boolean = false,
) {
    fun toNewMember() = NewMember(nickname, birthDate, birthTime, gender, mbti, calendar, leapMonth)
}

data class MemberCreatedResponse(val memberId: String, val nickname: String)

data class ComputeFactsResponse(val computed: Int)

@RestController
@RequestMapping("/api/teams")
class MemberController(
    private val teamService: TeamService,
    private val memberService: MemberService,
    private val sajuFactsService: dev.teamworkmate.api.facts.SajuFactsService,
) {

    /**
     * Admin-only synchronous facts computation for every member.
     * Day 5 moves this behind SQS; the service call stays identical.
     */
    @PostMapping("/admin/{token}/compute-facts")
    fun computeFacts(@PathVariable token: String): ComputeFactsResponse {
        val team = teamService.byAdminToken(token)
        val now = java.time.Instant.now()
        val members = memberService.listFor(team.id)
        members.forEach { sajuFactsService.computeAndStore(it, now) }
        return ComputeFactsResponse(members.size)
    }

    /** Self entry — anyone holding the invite link. */
    @PostMapping("/invite/{token}/members")
    @ResponseStatus(HttpStatus.CREATED)
    fun addViaInvite(@PathVariable token: String, @Valid @RequestBody req: AddMemberRequest): MemberCreatedResponse {
        val team = teamService.byInviteToken(token)
        val member = memberService.add(team, req.toNewMember(), enteredBy = "self")
        return MemberCreatedResponse(member.id.toString(), member.nickname)
    }

    /** Proxy entry — admin fills in members they know. */
    @PostMapping("/admin/{token}/members")
    @ResponseStatus(HttpStatus.CREATED)
    fun addViaAdmin(@PathVariable token: String, @Valid @RequestBody req: AddMemberRequest): MemberCreatedResponse {
        val team = teamService.byAdminToken(token)
        val member = memberService.add(team, req.toNewMember(), enteredBy = "admin")
        return MemberCreatedResponse(member.id.toString(), member.nickname)
    }
}
