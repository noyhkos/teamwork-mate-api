package dev.teamworkmate.api.team

import java.security.SecureRandom
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

interface TeamRepository : JpaRepository<Team, UUID> {
    fun findByInviteToken(token: String): Team?
    fun findByAdminToken(token: String): Team?
}

/** URL-safe capability tokens — possession of the URL is the permission. */
object TokenGenerator {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    private val random = SecureRandom()

    fun generate(length: Int = 22): String =
        buildString(length) { repeat(length) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }
}

@Service
class TeamService(private val teams: TeamRepository) {

    @Transactional
    fun create(name: String?): Team = teams.save(
        Team(
            name = name?.takeIf { it.isNotBlank() },
            inviteToken = TokenGenerator.generate(),
            adminToken = TokenGenerator.generate(),
        ),
    )

    @Transactional(readOnly = true)
    fun byInviteToken(token: String): Team =
        teams.findByInviteToken(token) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "team not found")

    @Transactional(readOnly = true)
    fun byAdminToken(token: String): Team =
        teams.findByAdminToken(token) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "team not found")
}
