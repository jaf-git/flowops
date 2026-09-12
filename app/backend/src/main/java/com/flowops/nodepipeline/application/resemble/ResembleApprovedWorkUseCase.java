package com.flowops.nodepipeline.application.resemble;

import java.util.Optional;

public interface ResembleApprovedWorkUseCase {
    Optional<Resemblance> forWhatWasSaid(String text);

    record Resemblance(String templateId, String title, double score, String why) {}
}
