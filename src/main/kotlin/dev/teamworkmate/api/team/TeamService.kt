package dev.teamworkmate.api.team

import java.security.SecureRandom
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

interface TeamRepository : JpaRepository<Team, UUID> {
    fun findByAccessToken(token: String): Team?
    fun findByShareSlug(slug: String): Team?
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
    fun create(name: String): Team = teams.save(
        Team(
            name = name.trim(),
            accessToken = TokenGenerator.generate(),
        ),
    )

    @Transactional(readOnly = true)
    fun byToken(token: String): Team =
        teams.findByAccessToken(token) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "team not found")
}
