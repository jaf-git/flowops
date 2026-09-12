package com.flowops.discovery.application.digest;

import com.flowops.discovery.application.digest.WeeklyDigestUseCase.Decision;
import java.time.Instant;
import java.util.List;

public interface DigestDecisionSource {
    List<Ranked> since(Instant since);

    record Ranked(Decision decision, long value) {}
}
