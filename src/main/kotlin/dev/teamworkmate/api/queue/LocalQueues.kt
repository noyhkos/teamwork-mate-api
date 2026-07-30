package dev.teamworkmate.api.queue

import dev.teamworkmate.api.analysis.AnalysisJobService
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Runs the job inline — deterministic, for tests. */
class DirectQueue(private val jobs: ObjectProvider<AnalysisJobService>) : QueuePort {
    override fun submit(teamId: UUID) = jobs.getObject().run(teamId)
}

/**
 * Single background worker thread — the local stand-in for SQS.
 * Not durable: a restart drops queued work, which is exactly why production uses SQS.
 */
class InMemoryQueue(private val jobs: ObjectProvider<AnalysisJobService>) : QueuePort {
    private val log = LoggerFactory.getLogger(javaClass)
    private val worker = Executors.newSingleThreadExecutor { r ->
        Thread(r, "analysis-worker").apply { isDaemon = true }
    }

    override fun submit(teamId: UUID) {
        worker.execute {
            runCatching { jobs.getObject().run(teamId) }
                .onFailure { log.error("worker crashed for team {}", teamId, it) }
        }
    }

    @PreDestroy
    fun shutdown() {
        worker.shutdown()
        worker.awaitTermination(30, TimeUnit.SECONDS)
    }
}
