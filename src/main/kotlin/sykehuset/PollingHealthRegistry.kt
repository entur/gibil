package org.gibil.health

import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Component
class PollingHealthRegistry {

    data class PollingStatus(
        val lastSuccess: Instant? = null,
        val lastFailure: Instant? = null,
        val lastFailureReason: String? = null,
        val consecutiveFailures: Int = 0
    )

    private val registry = ConcurrentHashMap<String, PollingStatus>()

    fun recordSuccess(name: String) {
        registry[name] = PollingStatus(lastSuccess = Instant.now())
    }

    fun recordFailure(name: String, reason: String) {
        val current = registry[name] ?: PollingStatus()
        registry[name] = current.copy(
            lastFailure = Instant.now(),
            lastFailureReason = reason,
            consecutiveFailures = current.consecutiveFailures + 1
        )
    }

    fun getAll(): Map<String, PollingStatus> = registry
}