package com.flowops.task.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.task.application.shared.TaskAudience;
import com.flowops.task.application.viewthroughput.ThroughputReadPort;
import com.flowops.task.application.viewthroughput.ViewThroughputUseCase;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("TASK-THROUGHPUT-01")
class ThroughputWeekBoundaryTest extends TaskScenarioTest {
    private static final Instant SUNDAY_LATE = Instant.parse("2026-08-23T22:30:00Z");

    private static final ZoneId BUCHAREST = ZoneId.of("Europe/Bucharest");

    private static final LocalDate SUNDAYS_WEEK = LocalDate.parse("2026-08-17");

    private static final LocalDate MONDAYS_WEEK = LocalDate.parse("2026-08-24");

    @Autowired
    private ThroughputReadPort throughput;

    private TaskAudience everybody;

    @BeforeEach
    void aCompanyAndOneTransitionOnTheBoundary() throws Exception {
        Company company = buildTheCompany();

        String task = createTaskFor(company.andrei());

        jdbc.update(
                "update task_state_transition set occurred_at = ? where task_id = ?::uuid",
                OffsetDateTime.ofInstant(SUNDAY_LATE, ZoneOffset.UTC),
                task);

        everybody = new TaskAudience(true, Set.of(), null);
    }

    private long createdIn(ZoneId zone, LocalDate week) {
        List<ViewThroughputUseCase.Week> series =
                throughput.weeklyCounts(everybody, SUNDAYS_WEEK, MONDAYS_WEEK.plusDays(6), zone);
        return series.stream()
                .filter(row -> row.starting().equals(week))
                .mapToLong(ViewThroughputUseCase.Week::created)
                .sum();
    }

    @Test
    void countsTheWorkInTheWeekTheWorkspaceIsLivingIn() {
        assertThat(createdIn(BUCHAREST, MONDAYS_WEEK))
                .as("22:30 UTC on Sunday is 01:30 Monday in Bucharest, so it belongs to Monday's week")
                .isEqualTo(1);
        assertThat(createdIn(BUCHAREST, SUNDAYS_WEEK))
                .as("and to no other week — a row counted twice is worse than a row counted late")
                .isZero();
    }

    @Test
    void theZoneIsWhatDecides() {
        assertThat(createdIn(ZoneOffset.UTC, SUNDAYS_WEEK))
                .as("in UTC the same instant is still Sunday, so it belongs to the earlier week")
                .isEqualTo(1);
        assertThat(createdIn(ZoneOffset.UTC, MONDAYS_WEEK)).isZero();
    }
}
