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

@RestController
@RequestMapping("/api/teams")
class MemberController(
    private val teamService: TeamService,
    private val memberService: MemberService,
) {

    /** Anyone holding the team link — for themselves or for someone who won't. */
    @PostMapping("/{token}/members")
    @ResponseStatus(HttpStatus.CREATED)
    fun add(@PathVariable token: String, @Valid @RequestBody req: AddMemberRequest): MemberCreatedResponse {
        val team = teamService.byToken(token)
        val member = memberService.add(team, req.toNewMember())
        return MemberCreatedResponse(member.id.toString(), member.nickname)
    }
}
