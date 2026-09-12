package com.flowops.analyser.application.shared.port;

import com.flowops.analyser.domain.Category;
import com.flowops.analyser.domain.Confidence;
import com.flowops.analyser.domain.Finding;
import com.flowops.analyser.domain.FindingContext;
import com.flowops.analyser.domain.Lifecycle;
import com.flowops.analyser.domain.Severity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface FindingReadPort {
    Optional<Run> latestFindings();

    Optional<Row> byId(UUID id);

    record Run(UUID runId, Instant windowFrom, Instant windowTo, Instant ranAt, List<Row> findings) {}

    record Row(
            UUID id,
            String key,
            String analyser,
            String kind,
            String stage,
            String subjectKind,
            String subject,
            String subjectName,
            FindingContext context,
            Category category,
            String headline,
            List<String> because,
            Map<Finding.EvidenceKind, List<String>> evidence,
            Severity severity,
            Confidence confidence,
            int reach,
            Integer reachOf,
            String action,
            Lifecycle lifecycle,
            int timesSeen,
            Instant firstSeenAt) {}
}
