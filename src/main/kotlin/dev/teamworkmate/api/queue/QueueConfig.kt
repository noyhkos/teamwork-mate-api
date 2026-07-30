package dev.teamworkmate.api.queue

import dev.teamworkmate.api.analysis.AnalysisJobService
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class QueueConfig {

    /** ObjectProvider breaks the queue -> job -> queue construction cycle. */
    @Bean
    fun queuePort(
        @Value("\${queue.mode:memory}") mode: String,
        jobs: ObjectProvider<AnalysisJobService>,
    ): QueuePort = when (mode) {
        "direct" -> DirectQueue(jobs)
        "memory" -> InMemoryQueue(jobs)
        else -> error("unknown queue.mode: $mode (expected direct | memory)")
    }
}
