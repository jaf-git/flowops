package com.flowops.nodepipeline.application;

import com.flowops.nodepipeline.application.port.NotifyPipelineFindingsPort;
import com.flowops.nodepipeline.application.port.PipelineAudiencePort;
import com.flowops.nodepipeline.application.port.PipelineFindingsPort;
import com.flowops.nodepipeline.domain.notify.Audience;
import com.flowops.nodepipeline.domain.notify.DeliveryPolicy;
import com.flowops.nodepipeline.domain.notify.DigestDiff;
import com.flowops.nodepipeline.domain.notify.JobFinding;
import com.flowops.nodepipeline.domain.notify.NodeFinding;
import com.flowops.nodepipeline.domain.notify.NoticeBudget;
import com.flowops.nodepipeline.domain.notify.NudgeHistory;
import com.flowops.nodepipeline.domain.notify.PipelineMessage;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeliverPipelineFindings {
    private final PipelineFindingsPort findings;
    private final PipelineAudiencePort people;
    private final NotifyPipelineFindingsPort notices;
    private final DeliveryPolicy policy;

    public DeliverPipelineFindings(
            PipelineFindingsPort findings, PipelineAudiencePort people, NotifyPipelineFindingsPort notices) {
        this.findings = findings;
        this.people = people;
        this.notices = notices;
        this.policy = new DeliveryPolicy(NoticeBudget.reference());
    }

    @Transactional
    public Optional<Delivery> deliverLatest() {
        Optional<PipelineFindingsPort.RunFindings> latest = findings.latestRun();
        if (latest.isEmpty()) {
            return Optional.empty();
        }
        PipelineFindingsPort.RunFindings run = latest.get();

        List<PipelineMessage> proposed = policy.propose(
                run.nodes(),
                run.jobs(),
                run.discoveries(),
                audienceFor(run),
                new NudgeHistory(
                        people.alreadyNudged(
                                run.nodes().stream().map(NodeFinding::nodeId).toList()),
                        findings.priorNudges(run.runId())));

        DigestDiff.Diff diff = DigestDiff.since(findings.shownBefore(run.runId()), proposed);
        List<PipelineMessage> sending = policy.capPerPerson(diff.fresh());

        List<UUID> shown = new ArrayList<>();
        for (PipelineMessage message : sending) {
            notices.tell(message);
            shown.addAll(message.decisionIds());
        }
        findings.markShown(shown);

        return Optional.of(new Delivery(
                run.runId(),
                proposed.size(),
                diff.repeated().size(),
                diff.settled().size(),
                (int) sending.stream().filter(m -> m.kind().forTheOwner()).count(),
                (int) sending.stream().filter(m -> !m.kind().forTheOwner()).count(),
                sending.stream().map(PipelineMessage::key).toList()));
    }

    private Audience audienceFor(PipelineFindingsPort.RunFindings run) {
        Set<String> jobIds =
                new LinkedHashSet<>(run.jobs().stream().map(JobFinding::jobId).toList());
        run.nodes().forEach(node -> jobIds.add(node.jobId()));

        var openedBy = people.openedBy(jobIds);
        var markedBy =
                people.markedBy(run.nodes().stream().map(NodeFinding::nodeId).toList());
        Optional<UUID> owner = people.workspaceOwner();

        Set<UUID> everybody = new LinkedHashSet<>(openedBy.values());
        everybody.addAll(markedBy.values());
        owner.ifPresent(everybody::add);

        return new Audience(people.workspace(), owner.orElse(null), openedBy, markedBy, people.stillHere(everybody));
    }

    public record Delivery(
            UUID runId,
            int proposed,
            int repeated,
            int settled,
            int ownerItems,
            int individualMessages,
            List<String> sent) {}
}
