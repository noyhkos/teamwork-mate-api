package dev.teamworkmate.api.team

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

interface MemberRepository : JpaRepository<Member, UUID> {
    fun findByTeamIdOrderByCreatedAt(teamId: UUID): List<Member>
    fun countByTeamId(teamId: UUID): Long
}

data class NewMember(
    val nickname: String,
    val birthDate: LocalDate,
    val birthTime: LocalTime?,
    val gender: String,
    val mbti: String,
    val calendar: String,
    val leapMonth: Boolean,
)

@Service
class MemberService(private val members: MemberRepository) {

    @Transactional
    fun add(team: Team, req: NewMember, enteredBy: String): Member {
        if (team.status != TeamStatus.collecting) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "team is not collecting members (status=${team.status})")
        }
        val member = Member(
            teamId = team.id,
            nickname = req.nickname.trim(),
            birthDate = req.birthDate,
            birthTime = req.birthTime,
            gender = req.gender,
            mbti = req.mbti.uppercase(),
            calendar = req.calendar,
            leapMonth = req.leapMonth,
            enteredBy = enteredBy,
        )
        try {
            return members.saveAndFlush(member)
        } catch (e: DataIntegrityViolationException) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "nickname already exists in this team", e)
        }
    }

    @Transactional(readOnly = true)
    fun listFor(teamId: UUID): List<Member> = members.findByTeamIdOrderByCreatedAt(teamId)
}
