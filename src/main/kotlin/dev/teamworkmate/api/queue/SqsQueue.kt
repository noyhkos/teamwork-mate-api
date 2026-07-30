package dev.teamworkmate.api.queue

import dev.teamworkmate.api.analysis.AnalysisJobService
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest
import software.amazon.awssdk.services.sqs.model.Message
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Production hand-off. The producer side only sends; the consumer side runs in
 * whichever process enables it, so api and worker can be scaled separately.
 *
 * Redelivery is SQS's job: a message stays invisible for the queue's visibility
 * timeout and is only deleted after the job returns, so a crashed worker means
 * the job is retried rather than lost. After maxReceiveCount it lands in the DLQ.
 */
class SqsQueue(
    private val sqs: SqsClient,
    private val queueUrl: String,
) : QueuePort {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun submit(teamId: UUID) {
        sqs.sendMessage(
            SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(teamId.toString())
                .build(),
        )
        log.info("queued analysis for team {}", teamId)
    }
}

/** Long-polling consumer. Started only when `queue.worker.enabled` is true. */
class SqsWorker(
    private val sqs: SqsClient,
    private val queueUrl: String,
    private val jobs: ObjectProvider<AnalysisJobService>,
    private val waitSeconds: Int = 20,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val running = AtomicBoolean(true)
    private val pollers = Executors.newSingleThreadExecutor { r ->
        Thread(r, "sqs-worker").apply { isDaemon = true }
    }

    fun start() {
        pollers.execute {
            log.info("sqs worker polling {}", queueUrl)
            while (running.get()) {
                runCatching { pollOnce() }
                    .onFailure { if (running.get()) log.error("sqs poll failed", it) }
            }
        }
    }

    private fun pollOnce() {
        val response = sqs.receiveMessage(
            ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(1)
                .waitTimeSeconds(waitSeconds) // long poll — no busy spin, no per-request cost
                .build(),
        )
        response.messages().forEach(::handle)
    }

    private fun handle(message: Message) {
        val teamId = runCatching { UUID.fromString(message.body().trim()) }.getOrNull()
        if (teamId == null) {
            // Unparseable body would poison the queue forever; drop it to the DLQ path
            // by leaving it for redelivery is pointless, so delete and log loudly.
            log.error("dropping malformed message: {}", message.body())
            delete(message)
            return
        }
        try {
            jobs.getObject().run(teamId)
            delete(message) // only after the job finished — a crash means redelivery
        } catch (e: Exception) {
            log.error("job failed for team {}; leaving message for redelivery", teamId, e)
        }
    }

    private fun delete(message: Message) {
        sqs.deleteMessage(
            DeleteMessageRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(message.receiptHandle())
                .build(),
        )
    }

    @PreDestroy
    fun stop() {
        running.set(false)
        pollers.shutdownNow()
        pollers.awaitTermination(5, TimeUnit.SECONDS)
    }
}
