package dev.teamworkmate.api.queue

import dev.teamworkmate.api.analysis.AnalysisJobService
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.ObjectMapper
import java.util.UUID

data class BatchItemFailure(val itemIdentifier: String)
data class PartialBatchResponse(val batchItemFailures: List<BatchItemFailure>)

/**
 * Consumer side on Lambda. The Web Adapter turns a non-HTTP trigger into a POST
 * of the raw event payload, so an SQS-triggered function reaches Spring here.
 *
 * Registered only when queue.consumer=lambda, which keeps the endpoint off the
 * public api function — that one produces and must never expose a job trigger.
 */
@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = ["queue.consumer"],
    havingValue = "lambda",
)
class LambdaEventController(
    private val jobs: AnalysisJobService,
    private val om: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/events")
    fun onEvent(@RequestBody body: String): PartialBatchResponse {
        val records = om.readTree(body).path("Records")
        val failures = mutableListOf<BatchItemFailure>()

        records.values().forEach { record ->
            val messageId = record.path("messageId").stringValue("")
            val teamId = runCatching { UUID.fromString(record.path("body").stringValue("").trim()) }.getOrNull()
            if (teamId == null) {
                // Malformed body would fail forever; let it exhaust redrive into the DLQ.
                log.error("unparseable message body (messageId={})", messageId)
                failures += BatchItemFailure(messageId)
                return@forEach
            }
            try {
                jobs.run(teamId)
            } catch (e: Exception) {
                log.error("job failed for team {}; reporting partial failure", teamId, e)
                failures += BatchItemFailure(messageId)
            }
        }

        // Reporting failures per message means successful ones are not redelivered.
        return PartialBatchResponse(failures)
    }
}
