package org.gibil.health

import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.stereotype.Component

@Component("pollingServices")
class PollingHealthIndicator(
    private val registry: PollingHealthRegistry
) : HealthIndicator {

    override fun health(): Health {
        val all = registry.getAll()
        val unhealthy = all.filterValues { it.consecutiveFailures >= 3 }

        return if (unhealthy.isEmpty()) {
            Health.up()
                .withDetail("services", all.mapValues { it.value.lastSuccess })
                .build()
        } else {
            Health.down()
                .withDetail("failing", unhealthy)
                .build()
        }
    }
}