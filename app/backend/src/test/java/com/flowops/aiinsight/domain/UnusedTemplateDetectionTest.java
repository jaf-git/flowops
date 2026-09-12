package com.flowops.aiinsight.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.aiinsight.domain.UnusedTemplateDetection.TemplateUsage;
import com.flowops.aiinsight.domain.UnusedTemplateDetection.UnusedTemplate;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("AI-INSIGHT-UNUSED-TEMPLATE-01")
class UnusedTemplateDetectionTest {
    private static final Instant NOW = Instant.parse("2026-08-21T10:00:00Z");

    private static Instant daysAgo(int days) {
        return NOW.minus(Duration.ofDays(days));
    }

    @Test
    void reportsAnApprovedTemplateNobodyHasUsedInsideTheWindow() {
        UUID audit = UUID.randomUUID();

        List<UnusedTemplate> found = UnusedTemplateDetection.over(
                List.of(new TemplateUsage(audit, "Quarterly audit prep", daysAgo(400), daysAgo(180), 4, List.of())),
                NOW);

        assertThat(found).singleElement().satisfies(stale -> {
            assertThat(stale.templateId()).isEqualTo(audit);
            assertThat(stale.name()).isEqualTo("Quarterly audit prep");
            assertThat(stale.lastUsedAt()).isEqualTo(daysAgo(180));
            assertThat(stale.everUsed()).isTrue();
            assertThat(stale.daysSinceLastUse()).isEqualTo(180);
        });
    }

    @Test
    void distinguishesNeverUsedFromLongUnused() {
        List<UnusedTemplate> found = UnusedTemplateDetection.over(
                List.of(new TemplateUsage(UUID.randomUUID(), "Site survey", daysAgo(400), null, 0, List.of())), NOW);

        assertThat(found).singleElement().satisfies(never -> {
            assertThat(never.everUsed())
                    .as("a template approved a year ago and never once used is a different fact from one"
                            + " that fell out of use, and the sentence a person reads must differ")
                    .isFalse();
            assertThat(never.lastUsedAt()).isNull();
            assertThat(never.daysSinceLastUse()).isEqualTo(UnusedTemplateDetection.NEVER);
        });
    }

    @Test
    void saysNothingAboutATemplateApprovedInsideTheWindow() {
        List<UnusedTemplate> found = UnusedTemplateDetection.over(
                List.of(new TemplateUsage(UUID.randomUUID(), "Onboarding checklist", daysAgo(10), null, 0, List.of())),
                NOW);

        assertThat(found).isEmpty();
    }

    @Test
    void saysNothingAboutATemplateUsedInsideTheWindow() {
        List<UnusedTemplate> found = UnusedTemplateDetection.over(
                List.of(new TemplateUsage(
                        UUID.randomUUID(), "Invoice follow-up", daysAgo(400), daysAgo(3), 90, List.of())),
                NOW);

        assertThat(found).isEmpty();
    }

    @Test
    void saysNothingAboutATemplateThatWasNeverApproved() {
        List<UnusedTemplate> found = UnusedTemplateDetection.over(
                List.of(new TemplateUsage(UUID.randomUUID(), "Draft nobody blessed", null, null, 0, List.of())), NOW);

        assertThat(found).isEmpty();
    }

    @Test
    void reportsTheStalestFirst() {
        List<UnusedTemplate> found = UnusedTemplateDetection.over(
                List.of(
                        new TemplateUsage(
                                UUID.randomUUID(), "Used 100 days ago", daysAgo(400), daysAgo(100), 2, List.of()),
                        new TemplateUsage(UUID.randomUUID(), "Never used", daysAgo(400), null, 0, List.of()),
                        new TemplateUsage(
                                UUID.randomUUID(), "Used 300 days ago", daysAgo(400), daysAgo(300), 1, List.of())),
                NOW);

        assertThat(found)
                .extracting(UnusedTemplate::name)
                .as("never used is the strongest claim in the set, then the longest since")
                .containsExactly("Never used", "Used 300 days ago", "Used 100 days ago");
    }

    @Test
    void statesTheWindowItJudgedAgainst() {
        UnusedTemplate stale = UnusedTemplateDetection.over(
                        List.of(new TemplateUsage(
                                UUID.randomUUID(), "Quarterly audit prep", daysAgo(400), daysAgo(180), 4, List.of())),
                        NOW)
                .getFirst();

        assertThat(stale.windowDays())
                .as("the evidence line names the window, because a template used every January is"
                        + " unused in July and the reader is the one who knows their business")
                .isEqualTo(90);
    }

    @Test
    void treatsTheWindowEdgeAsStillInsideIt() {
        List<UnusedTemplate> found = UnusedTemplateDetection.over(
                List.of(new TemplateUsage(UUID.randomUUID(), "Edge case", daysAgo(400), daysAgo(90), 1, List.of())),
                NOW);

        assertThat(found).isEmpty();
    }
}
