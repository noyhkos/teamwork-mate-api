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
    fun add(team: Team, req: NewMember): Member {
        // Only the worker's own window is closed. Anyone holding the link can
        // start the analysis, so a single early press used to lock the roster
        // forever and strand whoever had not filled it in yet; a late arrival
        // now joins and the team re-runs.
        if (team.status == TeamStatus.processing) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "분석이 끝난 뒤에 다시 시도해 주세요.")
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
        )
        try {
            return members.saveAndFlush(member)
        } catch (e: DataIntegrityViolationException) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "이미 같은 닉네임이 있어요.", e)
        }
    }

    /**
     * Scores, saju facts and pair rows all cascade from members in the DB, so
     * removing a member leaves no orphans behind — only a report that no
     * longer matches the roster, which TeamView flags as stale.
     */
    @Transactional
    fun remove(team: Team, memberId: UUID) {
        if (team.status == TeamStatus.processing) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "분석이 끝난 뒤에 다시 시도해 주세요.")
        }
        val member = members.findById(memberId).orElse(null)
        if (member == null || member.teamId != team.id) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "이미 지워진 팀원이에요.")
        }
        members.delete(member)
    }

    @Transactional(readOnly = true)
    fun listFor(teamId: UUID): List<Member> = members.findByTeamIdOrderByCreatedAt(teamId)
}
