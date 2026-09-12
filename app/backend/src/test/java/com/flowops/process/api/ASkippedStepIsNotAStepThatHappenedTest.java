package com.flowops.process.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@Tag("SOP-METADATA-01")
class ASkippedStepIsNotAStepThatHappenedTest extends ProcessScenarioTest {
    @Autowired
    private JdbcTemplate jdbc;

    private String insightSql;
    private String usesSql;

    @BeforeEach
    void theShippedStatements() throws Exception {
        insightSql = source("aiinsight/infrastructure/persistence/TemplateHistoryAdapter.java");
        usesSql = source("process/infrastructure/persistence/TemplateUsesAdapter.java");
    }

    private static String source(String path) throws Exception {
        return java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/flowops/" + path));
    }

    @Test
    void theEdgeQueryExcludesASkippedUpstreamStep() {
        assertThat(insightSql)
                .as("a skipped upstream step closes, so closed_at alone admits it and every edge into "
                        + "one would be reported as a false dependency")
                .contains("upstream.skipped_at is null");
    }

    @Test
    void traceabilityDoesNotCountASkippedStepAsAUse() {
        assertThat(usesSql)
                .as("the listing and the count must agree, and a step decided against is not a use")
                .contains("s.skipped_at is null")
                .contains("task_template_id = ? and skipped_at is null");
    }

    @Test
    void thePlannedQueryDoesNotFilterSkippedSteps() {
        String planned = between(insightSql, "s.origin = 'DEFINITION'", "order by s.position");

        assertThat(planned)
                .as("a skipped step was still planned; filtering here manufactures the missing-step "
                        + "findings the obligation exists to prevent (decision 617)")
                .doesNotContain("skipped_at");
    }

    @Test
    void theTasklessQueriesNeedNoPredicateAndHaveNotGrownOne() {
        Map<String, String> alreadyExcluded = Map.of(
                "attached", "s.origin = 'ATTACHED'",
                "durations", "s.task_id is not null",
                "blocks", "join instance_step s on s.task_id = x.task_id");

        List<String> present = alreadyExcluded.entrySet().stream()
                .filter(each -> insightSql.contains(each.getValue()))
                .map(Map.Entry::getKey)
                .toList();

        assertThat(present)
                .as("all three still exclude a skipped step by construction, so a predicate here would "
                        + "be noise that a later reader would take for a rule")
                .containsExactlyInAnyOrder("attached", "durations", "blocks");
    }

    @Test
    void theSchemaStillRefusesAClosedStepWithNoTaskUnlessItWasSkipped() {
        String constraint = jdbc.queryForObject(
                """
                select pg_get_constraintdef(oid) from pg_constraint
                where conname = 'instance_step_assigned_has_a_task'
                """,
                String.class);

        assertThat(constraint)
                .as("bounded to the skip; `or task_id is null` would re-admit the duplicate-work state "
                        + "V25 exists to forbid")
                .contains("skipped_at IS NOT NULL")
                .contains("CASE");
    }

    private static String between(String haystack, String from, String to) {
        int start = haystack.indexOf(from);
        assertThat(start).as("the `planned` query still contains %s", from).isNotNegative();
        int end = haystack.indexOf(to, start);
        return haystack.substring(start, end < 0 ? haystack.length() : end);
    }
}
