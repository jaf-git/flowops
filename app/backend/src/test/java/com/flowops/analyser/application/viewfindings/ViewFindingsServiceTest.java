package com.flowops.analyser.application.viewfindings;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.analyser.application.shared.port.DismissalPort;
import com.flowops.analyser.application.shared.port.FindingReadPort;
import com.flowops.analyser.domain.Category;
import com.flowops.analyser.domain.Confidence;
import com.flowops.analyser.domain.Lifecycle;
import com.flowops.analyser.domain.PriorityRule;
import com.flowops.analyser.domain.Severity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("ANALYSER-VIEW-FINDINGS-01")
class ViewFindingsServiceTest {
    private static final Instant FROM = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-09-01T00:00:00Z");

    private static final class StubFindings implements FindingReadPort {
        private final Optional<Run> run;

        StubFindings(Optional<Run> run) {
            this.run = run;
        }

        @Override
        public Optional<Run> latestFindings() {
            return run;
        }

        @Override
        public Optional<Row> byId(UUID id) {
            return run.flatMap(r ->
                    r.findings().stream().filter(row -> row.id().equals(id)).findFirst());
        }
    }

    private static final class StubDismissals implements DismissalPort {
        private final Map<String, InForce> inForce = new HashMap<>();

        StubDismissals saidNoTo(String findingKey) {
            inForce.put(findingKey, new InForce(findingKey, "abc:40:-", TO));
            return this;
        }

        @Override
        public void dismiss(String key, String analyser, String fingerprint, UUID decider, Instant when) {
            throw new UnsupportedOperationException("the queue never writes a judgement");
        }

        @Override
        public Map<String, InForce> current() {
            return Map.copyOf(inForce);
        }
    }

    private ViewFindingsService serviceOver(FindingReadPort findings, DismissalPort dismissals) {
        return new ViewFindingsService(
                findings,
                dismissals,
                PriorityRule.DEFAULT_GRACE,
                PriorityRule.DEFAULT_DECAY,
                PriorityRule.DEFAULT_FLOOR);
    }

    private static FindingReadPort.Row row(
            String analyser, String kind, Category category, Severity severity, int reach, Lifecycle lifecycle) {
        return new FindingReadPort.Row(
                UUID.randomUUID(),
                analyser + ':' + kind + ":subject",
                analyser,
                kind,
                "DETECT",
                "WORK_TYPE",
                "subject",
                "Subject",
                null,
                category,
                analyser + " says something about " + reach,
                List.of("Because."),
                Map.of(),
                severity,
                Confidence.HIGH,
                reach,
                100,
                "Do something",
                lifecycle,
                1,
                FROM);
    }

    private static FindingReadPort.Run runOf(FindingReadPort.Row... rows) {
        return new FindingReadPort.Run(UUID.randomUUID(), FROM, TO, TO, List.of(rows));
    }

    @Test
    void beforeAnythingHasRunThereIsNoQueueRatherThanAnEmptyOne() {
        ViewFindingsService service = serviceOver(new StubFindings(Optional.empty()), new StubDismissals());

        assertThat(service.execute()).isEmpty();
    }

    @Test
    void aRunThatFoundNothingIsAnEmptyQueueRatherThanNoQueue() {
        ViewFindingsService service = serviceOver(new StubFindings(Optional.of(runOf())), new StubDismissals());

        ViewFindingsUseCase.Queue queue = service.execute().orElseThrow();

        assertThat(queue.groups()).isEmpty();
        assertThat(queue.standing().shown()).isZero();
        assertThat(queue.standing().nothingNew()).isTrue();
    }

    @Test
    void theGroupsArriveInCategoryOrderRatherThanRowOrder() {
        ViewFindingsService service = serviceOver(
                new StubFindings(Optional.of(runOf(
                        row("S8", "a", Category.YOUR_PROCESSES, Severity.HIGH, 10, Lifecycle.NEW),
                        row("S1", "b", Category.YOUR_WORK, Severity.HIGH, 10, Lifecycle.NEW),
                        row("S6", "c", Category.YOUR_LIBRARY, Severity.HIGH, 10, Lifecycle.NEW)))),
                new StubDismissals());

        assertThat(service.execute().orElseThrow().groups())
                .extracting(ViewFindingsUseCase.Group::category)
                .containsExactly(Category.YOUR_WORK, Category.YOUR_LIBRARY, Category.YOUR_PROCESSES);
    }

    @Test
    void insideAGroupTheWorstComesFirst() {
        ViewFindingsService service = serviceOver(
                new StubFindings(Optional.of(runOf(
                        row("S6", "small", Category.YOUR_LIBRARY, Severity.LOW, 3, Lifecycle.STILL_TRUE),
                        row("S6", "big", Category.YOUR_LIBRARY, Severity.CRITICAL, 90, Lifecycle.WORSENING)))),
                new StubDismissals());

        List<ViewFindingsUseCase.Item> items =
                service.execute().orElseThrow().groups().getFirst().items();

        assertThat(items).extracting(ViewFindingsUseCase.Item::kind).containsExactly("big", "small");
        assertThat(items.getFirst().whyItRanks()).contains("Critical because it touches 90 of 100");
    }

    @Test
    void aFindingSomebodyJustDismissedIsGoneBeforeTheNextRun() {
        FindingReadPort.Row dismissed =
                row("S6", "dead_templates", Category.YOUR_LIBRARY, Severity.HIGH, 41, Lifecycle.STILL_TRUE);
        ViewFindingsService service = serviceOver(
                new StubFindings(Optional.of(runOf(dismissed))), new StubDismissals().saidNoTo(dismissed.key()));

        ViewFindingsUseCase.Queue queue = service.execute().orElseThrow();

        assertThat(queue.groups()).isEmpty();

        assertThat(queue.standing().dismissed()).isEqualTo(1);
        assertThat(queue.standing().shown()).isZero();
    }

    @Test
    void aDismissedFindingTheRunMarkedWorseIsShownAgain() {
        FindingReadPort.Row returned =
                row("S6", "dead_templates", Category.YOUR_LIBRARY, Severity.HIGH, 62, Lifecycle.WORSENING);
        ViewFindingsService service = serviceOver(
                new StubFindings(Optional.of(runOf(returned))), new StubDismissals().saidNoTo(returned.key()));

        ViewFindingsUseCase.Queue queue = service.execute().orElseThrow();

        assertThat(queue.groups()).hasSize(1);
        assertThat(queue.standing().worsening()).isEqualTo(1);
        assertThat(queue.standing().dismissed()).isZero();
        assertThat(queue.standing().nothingNew()).isFalse();
    }

    @Test
    void aRunWhereNothingIsNewOrWorseSaysSo() {
        List<FindingReadPort.Row> rows = new ArrayList<>();
        for (int index = 0; index < 14; index++) {
            rows.add(row("S1", "kind" + index, Category.YOUR_WORK, Severity.MEDIUM, 5, Lifecycle.STILL_TRUE));
        }
        ViewFindingsService service = serviceOver(
                new StubFindings(
                        Optional.of(new FindingReadPort.Run(UUID.randomUUID(), FROM, TO, TO, List.copyOf(rows)))),
                new StubDismissals());

        ViewFindingsUseCase.Standing standing = service.execute().orElseThrow().standing();

        assertThat(standing.nothingNew()).isTrue();
        assertThat(standing.stillTrue()).isEqualTo(14);
        assertThat(standing.shown()).isEqualTo(14);
    }

    @Test
    void oneNewFindingIsEnoughToMakeTheWeekNotQuiet() {
        ViewFindingsService service = serviceOver(
                new StubFindings(Optional.of(runOf(
                        row("S1", "a", Category.YOUR_WORK, Severity.MEDIUM, 5, Lifecycle.STILL_TRUE),
                        row("S1", "b", Category.YOUR_WORK, Severity.MEDIUM, 5, Lifecycle.NEW)))),
                new StubDismissals());

        assertThat(service.execute().orElseThrow().standing().nothingNew()).isFalse();
    }
}
