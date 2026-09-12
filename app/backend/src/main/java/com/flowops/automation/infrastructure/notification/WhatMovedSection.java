package com.flowops.automation.infrastructure.notification;

import com.flowops.notification.application.shared.port.WeeklySectionPort;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class WhatMovedSection implements WeeklySectionPort {
    private static final Duration ONE_WEEK = Duration.ofDays(7);

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public WhatMovedSection(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public String key() {
        return "whatMoved";
    }

    @Override
    public int count() {
        Integer moved = jdbc.queryForObject(
                "select count(*) from automation_event where occurred_at >= ?",
                Integer.class,
                Timestamp.from(clock.instant().minus(ONE_WEEK)));
        return moved == null ? 0 : moved;
    }

    @Override
    public boolean alwaysRenders() {
        return true;
    }
}
