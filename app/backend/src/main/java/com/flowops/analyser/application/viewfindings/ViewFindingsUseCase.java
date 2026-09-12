package com.flowops.analyser.application.viewfindings;

import com.flowops.analyser.domain.Category;
import com.flowops.analyser.domain.Confidence;
import com.flowops.analyser.domain.FindingContext;
import com.flowops.analyser.domain.Lifecycle;
import com.flowops.analyser.domain.Severity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ViewFindingsUseCase {
    Optional<Queue> execute();

    record Queue(
            UUID runId, Instant windowFrom, Instant windowTo, Instant ranAt, List<Group> groups, Standing standing) {}

    record Group(Category category, List<Item> items) {}

    record Item(
            UUID id,
            String analyser,
            String kind,
            String stage,
            String subjectKind,
            String subject,
            String subjectName,
            FindingContext context,
            String headline,
            List<String> because,
            Severity severity,
            Confidence confidence,
            int reach,
            Integer reachOf,
            String action,
            Lifecycle lifecycle,
            int timesSeen,
            Instant firstSeenAt,
            double priority,
            String whyItRanks,
            List<Evidence> evidence) {}

    record Evidence(String kind, String id) {}

    record Standing(
            int shown, int fresh, int worsening, int stillTrue, int improving, int dismissed, boolean nothingNew) {}
}
