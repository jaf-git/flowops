package com.flowops.nodepipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.nodepipeline.application.DeliverPipelineFindings;
import com.flowops.nodepipeline.application.port.NotifyPipelineFindingsPort;
import com.flowops.nodepipeline.application.port.PipelineAudiencePort;
import com.flowops.nodepipeline.application.port.PipelineFindingsPort;
import com.flowops.nodepipeline.domain.MatchTier;
import com.flowops.nodepipeline.domain.notify.NodeFinding;
import com.flowops.nodepipeline.domain.notify.NudgeHistory;
import com.flowops.nodepipeline.domain.notify.PipelineMessage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeliverPipelineFindingsTest {
    private static final UUID INES = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000ff");
    private static final UUID RUN = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID DECISION = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final String NODE = "00000000-0000-0000-0000-0000000000e1";

    private final List<String> log = new ArrayList<>();

    @Test
    void aFindingIsDeliveredBeforeItIsRecordedAsShown() {
        Findings findings = new Findings(oneNudge(), Set.of(), List.of());
        Notices notices = new Notices();

        Optional<DeliverPipelineFindings.Delivery> delivery =
                new DeliverPipelineFindings(findings, new People(), notices).deliverLatest();

        assertThat(notices.told).hasSize(1);
        assertThat(notices.told.get(0).to().person())
                .as("ADR-002: the person who marked the work, not the one who spoke")
                .isEqualTo(INES);
        assertThat(findings.shown).containsExactly(DECISION);
        assertThat(log)
                .as("stamped first, a failed delivery would leave rows claiming somebody saw what never "
                        + "arrived — and next week's diff would suppress exactly those")
                .containsExactly("told", "markShown");
        assertThat(delivery.orElseThrow().individualMessages()).isEqualTo(1);
    }

    @Test
    void aFindingAlreadyShownInAnEarlierDigestIsNotDeliveredTwice() {
        Findings findings = new Findings(oneNudge(), Set.of("NODE_MATCH|" + NODE + "|T-CAPS"), List.of());
        Notices notices = new Notices();

        Optional<DeliverPipelineFindings.Delivery> delivery =
                new DeliverPipelineFindings(findings, new People(), notices).deliverLatest();

        assertThat(notices.told)
                .as("the second identical digest is the one the owner stops opening")
                .isEmpty();
        assertThat(findings.shown).isEmpty();
        assertThat(delivery.orElseThrow().repeated()).isEqualTo(1);
    }

    @Test
    void aPairAlreadyPastTheHabitThresholdIsSilentAcrossRuns() {
        Findings findings = new Findings(
                oneNudge(),
                Set.of(),
                List.of(
                        new NudgeHistory.PriorNudge(INES, "T-CAPS"),
                        new NudgeHistory.PriorNudge(INES, "T-CAPS"),
                        new NudgeHistory.PriorNudge(INES, "T-CAPS")));
        Notices notices = new Notices();

        new DeliverPipelineFindings(findings, new People(), notices).deliverLatest();

        assertThat(notices.told).isEmpty();
    }

    @Test
    void aPipelineThatHasNeverRunDeliversNothingAndSaysSo() {
        Findings none = new Findings(null, Set.of(), List.of());

        assertThat(new DeliverPipelineFindings(none, new People(), new Notices()).deliverLatest())
                .isEmpty();
    }

    private static PipelineFindingsPort.RunFindings oneNudge() {
        NodeFinding node = new NodeFinding(DECISION, NODE, "j1", "T-CAPS", MatchTier.NUDGE, 0.88, "text 0.82");
        return new PipelineFindingsPort.RunFindings(RUN, List.of(node), List.of(), List.of());
    }

    private final class Findings implements PipelineFindingsPort {
        private final RunFindings run;
        private final Set<String> before;
        private final List<NudgeHistory.PriorNudge> prior;
        private final List<UUID> shown = new ArrayList<>();

        Findings(RunFindings run, Set<String> before, List<NudgeHistory.PriorNudge> prior) {
            this.run = run;
            this.before = before;
            this.prior = prior;
        }

        @Override
        public Optional<RunFindings> latestRun() {
            return Optional.ofNullable(run);
        }

        @Override
        public Set<String> shownBefore(UUID runId) {
            return before;
        }

        @Override
        public List<NudgeHistory.PriorNudge> priorNudges(UUID runId) {
            return prior;
        }

        @Override
        public void markShown(Collection<UUID> decisionIds) {
            if (!decisionIds.isEmpty()) {
                log.add("markShown");
            }
            shown.addAll(decisionIds);
        }
    }

    private final class Notices implements NotifyPipelineFindingsPort {
        private final List<PipelineMessage> told = new ArrayList<>();

        @Override
        public void tell(PipelineMessage message) {
            log.add("told");
            told.add(message);
        }
    }

    private static final class People implements PipelineAudiencePort {
        @Override
        public UUID workspace() {
            return UUID.fromString("00000000-0000-0000-0000-0000000000b1");
        }

        @Override
        public Optional<UUID> workspaceOwner() {
            return Optional.of(OWNER);
        }

        @Override
        public Map<String, UUID> openedBy(Collection<String> jobIds) {
            return Map.of("j1", OWNER);
        }

        @Override
        public Map<String, UUID> markedBy(Collection<String> nodeIds) {
            return Map.of(NODE, INES);
        }

        @Override
        public Set<UUID> stillHere(Collection<UUID> people) {
            return new HashSet<>(people);
        }

        @Override
        public Set<String> alreadyNudged(Collection<String> nodeIds) {
            return Set.of();
        }
    }
}
