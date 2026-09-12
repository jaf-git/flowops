package com.flowops.aiexport.seed;

import java.time.Instant;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("demo")
public class DemoClockConfiguration {
    static final Instant HISTORY_BEGINS = Instant.parse("2026-08-15T08:00:00Z");

    static final Instant LIVE_WORK_BEGINS = Instant.parse("2026-12-05T08:00:00Z");

    static final Instant THE_HORIZON = Instant.parse("2027-01-01T08:00:00Z");

    @Bean
    @Primary
    public MutableClock demoClock() {
        return new MutableClock(HISTORY_BEGINS, ZoneId.of("Europe/Bucharest"));
    }
}
