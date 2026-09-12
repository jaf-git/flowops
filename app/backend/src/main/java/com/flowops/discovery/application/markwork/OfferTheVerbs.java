package com.flowops.discovery.application.markwork;

import com.flowops.discovery.application.markintobracket.ComposeAddress;
import com.flowops.discovery.application.shared.port.ActivityPort;
import com.flowops.discovery.application.shared.port.JobHeaderPort;
import com.flowops.discovery.application.shared.port.WorkStripPort;
import com.flowops.discovery.domain.enums.MarkVerb;
import com.flowops.discovery.domain.model.JobId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OfferTheVerbs {
    private final ComposeAddress addresses;
    private final WorkStripPort strip;
    private final ActivityPort activities;
    private final JobHeaderPort jobs;

    public OfferTheVerbs(ComposeAddress addresses, WorkStripPort strip, ActivityPort activities, JobHeaderPort jobs) {
        this.addresses = addresses;
        this.strip = strip;
        this.activities = activities;
        this.jobs = jobs;
    }

    @Transactional(readOnly = true)
    public Offer offerFor(JobId job, UUID conversationId, UUID performerId, String workTypeOverride) {
        ComposeAddress.Destination destination =
                addresses.previewFor(job, conversationId, performerId, workTypeOverride);

        List<MarkVerb> verbs = new ArrayList<>();

        verbs.add(destination.joins() ? MarkVerb.ADD : MarkVerb.CREATE);

        List<WorkStripPort.OpenWork> others = strip.othersOpenHere(job, conversationId, performerId);
        if (!others.isEmpty()) {
            verbs.add(MarkVerb.JOIN);
        }

        return new Offer(
                destination.describe(),
                destination.joins(),
                destination.bracketId(),
                destination.workType(),
                List.copyOf(verbs),
                others,
                jobs.headerOf(job).map(JobHeaderPort.JobHeader::client).orElse(null),
                echoOf(job, destination.workType()));
    }

    private EarlierWorkHere echoOf(JobId job, String workType) {
        if (workType == null || workType.isBlank()) {
            return null;
        }

        List<ActivityPort.PriorWork> prior = activities.workOfThisKindIn(job.value(), workType);
        if (prior.isEmpty()) {
            return null;
        }

        return new EarlierWorkHere(
                workType,
                distinct(prior, ActivityPort.PriorWork::performerName),
                distinct(prior, ActivityPort.PriorWork::activityName));
    }

    private static List<String> distinct(
            List<ActivityPort.PriorWork> prior, java.util.function.Function<ActivityPort.PriorWork, String> part) {
        return prior.stream()
                .map(part)
                .filter(one -> one != null && !one.isBlank())
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    public record Offer(
            String describe,
            boolean joins,
            UUID bracketId,
            String workType,
            List<MarkVerb> verbs,
            List<WorkStripPort.OpenWork> othersHere,
            String clientName,
            EarlierWorkHere earlierWorkHere) {}

    public record EarlierWorkHere(String workType, List<String> people, List<String> activities) {}
}
