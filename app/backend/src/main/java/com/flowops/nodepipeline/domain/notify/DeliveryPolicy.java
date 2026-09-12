package com.flowops.nodepipeline.domain.notify;

import com.flowops.nodepipeline.domain.MatchTier;
import com.flowops.nodepipeline.domain.job.JobTier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class DeliveryPolicy {
    private final NoticeBudget budget;

    public DeliveryPolicy(NoticeBudget budget) {
        this.budget = budget;
    }

    public List<PipelineMessage> propose(
            List<NodeFinding> nodes,
            List<JobFinding> jobs,
            List<DiscoveryFinding> discoveries,
            Audience audience,
            NudgeHistory history) {
        List<PipelineMessage> messages = new ArrayList<>();
        for (JobFinding job : jobs) {
            aboutTheEngagement(job, audience).ifPresent(messages::add);
        }
        messages.addAll(staffingNotes(nodes, audience));

        Set<String> spokenAtJobLevel = new LinkedHashSet<>();
        for (PipelineMessage message : messages) {
            if (message.kind().spokenAtJobLevel()) {
                spokenAtJobLevel.add(message.subject());
            }
        }

        messages.addAll(toThePeopleWhoMarked(nodes, spokenAtJobLevel, audience, history));
        messages.addAll(toTheOwner(jobs, discoveries, audience));

        messages.sort(byStrengthThenIdentity());
        return List.copyOf(messages);
    }

    public List<PipelineMessage> capPerPerson(List<PipelineMessage> proposed) {
        List<PipelineMessage> ordered = new ArrayList<>(proposed);
        ordered.sort(byStrengthThenIdentity());

        Map<UUID, Integer> taken = new HashMap<>();
        List<PipelineMessage> sending = new ArrayList<>();
        for (PipelineMessage message : ordered) {
            int already = taken.getOrDefault(message.to().person(), 0);
            if (already < budget.perPersonPerDigest()) {
                sending.add(message);
                taken.put(message.to().person(), already + 1);
            }
        }
        return List.copyOf(sending);
    }

    public NoticeBudget budget() {
        return budget;
    }

    private static Optional<PipelineMessage> aboutTheEngagement(JobFinding job, Audience audience) {
        MessageKind kind =
                switch (job.tier()) {
                    case STALE -> MessageKind.THE_ENGAGEMENT_HAS_STALLED;
                    case PARTIAL_RUN -> MessageKind.STEPS_THIS_ENGAGEMENT_SKIPPED;
                    default -> null;
                };
        if (kind == null) {
            return Optional.empty();
        }
        return audience.whoOpened(job.jobId())
                .map(owner -> new PipelineMessage(
                        owner,
                        kind,
                        job.jobId(),
                        job.reason(),
                        job.score(),
                        List.of(new PipelineMessage.Evidence(job.decisionId(), job.key(), job.jobId()))));
    }

    private static List<PipelineMessage> staffingNotes(List<NodeFinding> nodes, Audience audience) {
        Map<String, List<NodeFinding>> byEngagement = new LinkedHashMap<>();
        for (NodeFinding node : nodes) {
            if (node.tier() == MatchTier.ROLE_MISMATCH) {
                byEngagement
                        .computeIfAbsent(node.jobId(), key -> new ArrayList<>())
                        .add(node);
            }
        }

        List<PipelineMessage> notes = new ArrayList<>();
        for (Map.Entry<String, List<NodeFinding>> engagement : byEngagement.entrySet()) {
            Optional<Recipient> owner = audience.whoOpened(engagement.getKey());
            if (owner.isEmpty()) {
                continue;
            }
            List<NodeFinding> mismatched = engagement.getValue();
            NodeFinding strongest = mismatched.stream()
                    .max(Comparator.comparingDouble(NodeFinding::score))
                    .orElseThrow();
            notes.add(new PipelineMessage(
                    owner.get(),
                    MessageKind.STAFFING_NOTE,
                    engagement.getKey(),
                    mismatched.size() == 1
                            ? strongest.reason()
                            : "%d marks by roles that do not usually do this work: %s"
                                    .formatted(mismatched.size(), strongest.reason()),
                    strongest.score(),
                    mismatched.stream()
                            .map(node -> new PipelineMessage.Evidence(node.decisionId(), node.key(), node.nodeId()))
                            .toList()));
        }
        return notes;
    }

    private List<PipelineMessage> toThePeopleWhoMarked(
            List<NodeFinding> nodes, Set<String> spokenAtJobLevel, Audience audience, NudgeHistory history) {
        Map<Pair, List<Candidate>> byPersonAndTemplate = new LinkedHashMap<>();

        for (NodeFinding node : nodes) {
            if (node.tier() != MatchTier.NUDGE && node.tier() != MatchTier.ADJUST) {
                continue;
            }

            if (node.jobId() == null) {
                continue;
            }
            if (spokenAtJobLevel.contains(node.jobId())) {
                continue;
            }
            if (history.nodesAlreadyNudged().contains(node.nodeId())) {
                continue;
            }
            Optional<Recipient> marker = audience.whoMarked(node.nodeId());
            if (marker.isEmpty()) {
                continue;
            }
            byPersonAndTemplate
                    .computeIfAbsent(new Pair(marker.get().person(), node.templateId()), key -> new ArrayList<>())
                    .add(new Candidate(marker.get(), node));
        }

        List<PipelineMessage> messages = new ArrayList<>();
        for (Map.Entry<Pair, List<Candidate>> group : byPersonAndTemplate.entrySet()) {
            messages.addAll(resolve(group.getKey(), group.getValue(), history));
        }
        return messages;
    }

    private List<PipelineMessage> resolve(Pair pair, List<Candidate> group, NudgeHistory history) {
        int prior = history.repeatsFor(pair.person(), pair.templateId());

        if (prior > budget.habitRepeats()) {
            return List.of();
        }

        boolean pattern = pair.templateId() != null && prior + group.size() > budget.habitRepeats();
        if (pattern) {
            Candidate strongest = group.stream()
                    .max(Comparator.comparingDouble(
                            candidate -> candidate.node().score()))
                    .orElseThrow();
            return List.of(new PipelineMessage(
                    strongest.recipient(),
                    MessageKind.A_HABIT_WORTH_A_TEMPLATE,
                    strongest.node().jobId(),
                    "%d times, the same way: %s"
                            .formatted(prior + group.size(), strongest.node().reason()),
                    strongest.node().score(),
                    group.stream()
                            .map(candidate -> new PipelineMessage.Evidence(
                                    candidate.node().decisionId(),
                                    candidate.node().key(),
                                    candidate.node().nodeId()))
                            .toList()));
        }

        return group.stream()
                .map(candidate -> new PipelineMessage(
                        candidate.recipient(),
                        candidate.node().tier() == MatchTier.ADJUST
                                ? MessageKind.WHICH_TEMPLATE_WAS_THIS
                                : MessageKind.WORK_COULD_HAVE_BEEN_FASTER,
                        candidate.node().jobId(),
                        candidate.node().reason(),
                        candidate.node().score(),
                        List.of(new PipelineMessage.Evidence(
                                candidate.node().decisionId(),
                                candidate.node().key(),
                                candidate.node().nodeId()))))
                .toList();
    }

    private static List<PipelineMessage> toTheOwner(
            List<JobFinding> jobs, List<DiscoveryFinding> discoveries, Audience audience) {
        Optional<Recipient> owner = audience.theWorkspaceOwner();
        if (owner.isEmpty()) {
            return List.of();
        }

        List<PipelineMessage> messages = new ArrayList<>();
        String workspace = audience.workspace().toString();

        List<PipelineMessage.Evidence> undocumented = new ArrayList<>();
        int engagements = 0;
        for (JobFinding job : jobs) {
            if (job.tier() == JobTier.UNKNOWN_PATTERN) {
                engagements++;
                undocumented.add(new PipelineMessage.Evidence(job.decisionId(), job.key(), job.jobId()));
            }
        }
        int drafted = 0;
        for (DiscoveryFinding discovery : discoveries) {
            if (discovery.grain() == DiscoveryFinding.Grain.DRAFT_PROCESS) {
                drafted++;
                undocumented.add(
                        new PipelineMessage.Evidence(discovery.decisionId(), discovery.key(), discovery.subject()));
            }
        }
        if (!undocumented.isEmpty()) {
            messages.add(new PipelineMessage(
                    owner.get(),
                    MessageKind.AN_UNDOCUMENTED_PROCESS,
                    workspace,
                    "%d engagements matched nothing written down; %d ways of working repeat across them"
                            .formatted(engagements, drafted),
                    1.0,
                    undocumented));
        }

        List<PipelineMessage.Evidence> kinds = discoveries.stream()
                .filter(discovery -> discovery.grain() == DiscoveryFinding.Grain.STEP_KIND)
                .map(discovery ->
                        new PipelineMessage.Evidence(discovery.decisionId(), discovery.key(), discovery.subject()))
                .toList();
        if (!kinds.isEmpty()) {
            messages.add(new PipelineMessage(
                    owner.get(),
                    MessageKind.WORK_WORTH_A_TEMPLATE,
                    workspace,
                    "%d kinds of work recur with no template of any kind".formatted(kinds.size()),
                    0.99,
                    kinds));
        }
        return messages;
    }

    private static Comparator<PipelineMessage> byStrengthThenIdentity() {
        return Comparator.comparingDouble(PipelineMessage::score).reversed().thenComparing(PipelineMessage::key);
    }

    private record Pair(UUID person, String templateId) {}

    private record Candidate(Recipient recipient, NodeFinding node) {}
}
