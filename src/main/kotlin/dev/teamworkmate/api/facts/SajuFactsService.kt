package dev.teamworkmate.api.facts

import dev.teamworkmate.api.team.Member
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestClient
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.time.Instant

/** Port so tests (and future transports) can swap the calc backend. */
interface CalcPort {
    fun fetchFacts(member: Member, now: Instant): JsonNode
}

/** Thin HTTP client for the Node calc microservice (ssaju is TS-only, hence the language boundary). */
@Component
class SajuCalcClient(
    @Value("\${calc.base-url}") baseUrl: String,
    private val objectMapper: ObjectMapper,
) : CalcPort {
    private val client = RestClient.builder().baseUrl(baseUrl).build()

    override fun fetchFacts(member: Member, now: Instant): JsonNode {
        val body = buildMap<String, Any> {
            put("birthDate", member.birthDate.toString())
            member.birthTime?.let { put("birthTime", "%02d:%02d".format(it.hour, it.minute)) }
            put("gender", member.gender)
            put("calendar", member.calendar)
            put("leapMonth", member.leapMonth)
            put("now", now.toString())
        }
        val response = client.post().uri("/saju").body(body).retrieve().body(String::class.java)
            ?: error("empty calc response")
        return objectMapper.readTree(response)
    }
}

@Service
class SajuFactsService(
    private val calcClient: CalcPort,
    private val repository: SajuFactsRepository,
) {

    /** Idempotent: same member recomputed simply overwrites its single facts row. */
    @Transactional
    fun computeAndStore(member: Member, now: Instant): SajuFacts {
        val response = calcClient.fetchFacts(member, now)
        return repository.save(SajuFactsMapper.toEntity(member.id, response, now))
    }
}
