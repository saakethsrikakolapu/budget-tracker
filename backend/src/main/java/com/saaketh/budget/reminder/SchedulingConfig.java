package com.saaketh.budget.reminder;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on @Scheduled jobs (the monthly upload reminder). Set app.scheduling.enabled=false to run
 * without them; tests call the job's method directly with a chosen date instead of waiting.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
class SchedulingConfig {
}
