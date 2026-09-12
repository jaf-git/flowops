package com.flowops.nodepipeline.domain.notify;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class DigestDiff {
    private DigestDiff() {}

    public static Diff since(Set<String> shownBefore, List<PipelineMessage> proposed) {
        Set<String> before = shownBefore == null ? Set.of() : shownBefore;

        List<PipelineMessage> fresh = new ArrayList<>();
        List<PipelineMessage> repeated = new ArrayList<>();
        Set<String> stillStanding = new LinkedHashSet<>();

        for (PipelineMessage message : proposed) {
            boolean carriesSomethingNew = false;
            for (PipelineMessage.Evidence evidence : message.evidence()) {
                stillStanding.add(evidence.key());
                carriesSomethingNew |= !before.contains(evidence.key());
            }
            if (carriesSomethingNew) {
                fresh.add(message);
            } else {
                repeated.add(message);
            }
        }

        List<String> settled =
                before.stream().filter(key -> !stillStanding.contains(key)).toList();

        return new Diff(List.copyOf(fresh), List.copyOf(repeated), settled);
    }

    public record Diff(List<PipelineMessage> fresh, List<PipelineMessage> repeated, List<String> settled) {}
}
