package dev.teamworkmate.api.queue

import dev.teamworkmate.api.analysis.AnalysisJobService
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.services.sqs.SqsClient

@Configuration
class QueueConfig {

    /** ObjectProvider breaks the queue -> job -> queue construction cycle. */
    @Bean
    fun queuePort(
        @Value("\${queue.mode:memory}") mode: String,
        @Value("\${queue.sqs.url:}") queueUrl: String,
        jobs: ObjectProvider<AnalysisJobService>,
        sqs: ObjectProvider<SqsClient>,
    ): QueuePort = when (mode) {
        "direct" -> DirectQueue(jobs)
        "memory" -> InMemoryQueue(jobs)
        "sqs" -> {
            require(queueUrl.isNotBlank()) { "queue.sqs.url is required when queue.mode=sqs" }
            SqsQueue(sqs.getObject(), queueUrl)
        }
        else -> error("unknown queue.mode: $mode (expected direct | memory | sqs)")
    }

    /** Region and credentials come from the standard AWS chain (task role in ECS). */
    @Bean(destroyMethod = "close")
    fun sqsClient(@Value("\${queue.mode:memory}") mode: String): SqsClient? =
        if (mode == "sqs") SqsClient.create() else null

    /**
     * Consumer side. Disabled by default so the api task only produces;
     * the worker task runs with queue.worker.enabled=true.
     */
    @Bean(initMethod = "start")
    fun sqsWorker(
        @Value("\${queue.mode:memory}") mode: String,
        @Value("\${queue.worker.enabled:false}") enabled: Boolean,
        @Value("\${queue.sqs.url:}") queueUrl: String,
        sqs: ObjectProvider<SqsClient>,
        jobs: ObjectProvider<AnalysisJobService>,
    ): SqsWorker? =
        if (mode == "sqs" && enabled) SqsWorker(sqs.getObject(), queueUrl, jobs) else null
}
