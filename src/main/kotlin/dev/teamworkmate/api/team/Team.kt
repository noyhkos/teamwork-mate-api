package dev.teamworkmate.api.team

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** Lowercase names intentionally match the DB CHECK constraint values. */
enum class TeamStatus { collecting, ready, processing, done, failed }

@Entity
@Table(name = "teams")
class Team(
    @Id
    val id: UUID = UUID.randomUUID(),

    var name: String? = null,

    // Possession of this URL is the permission — there is no second, elevated link.
    @Column(name = "access_token", nullable = false, unique = true)
    val accessToken: String,

    @Column(name = "share_slug")
    var shareSlug: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: TeamStatus = TeamStatus.collecting,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
)
