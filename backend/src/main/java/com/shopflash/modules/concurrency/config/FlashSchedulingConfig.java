package com.shopflash.modules.concurrency.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Bật @Scheduled cho job của module concurrency (FlashJobs). */
@Configuration
@EnableScheduling
public class FlashSchedulingConfig {
}
