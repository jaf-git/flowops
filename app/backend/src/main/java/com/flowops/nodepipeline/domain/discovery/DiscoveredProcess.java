package com.flowops.nodepipeline.domain.discovery;

import java.util.List;

public record DiscoveredProcess(
        List<String> core,
        List<String> jobIds,
        int runs,
        double orderConfidence,
        boolean orderReliable,
        double certainty,
        List<List<String>> variants,
        String orderWithheldReason) {
    public DiscoveredProcess {
        core = core == null ? List.of() : List.copyOf(core);
        jobIds = jobIds == null ? List.of() : List.copyOf(jobIds);
        variants = variants == null ? List.of() : List.copyOf(variants);

        if (orderReliable != (orderWithheldReason == null)) {
            throw new IllegalArgumentException(
                    "a shown order withholds nothing and a withheld order must say why: reliable=" + orderReliable
                            + ", reason=" + orderWithheldReason);
        }
    }

    public DiscoveredProcess(
            List<String> core,
            List<String> jobIds,
            int runs,
            double orderConfidence,
            boolean orderReliable,
            double certainty,
            List<List<String>> variants) {
        this(
                core,
                jobIds,
                runs,
                orderConfidence,
                orderReliable,
                certainty,
                variants,
                orderReliable ? null : "order not shown");
    }

    public List<String> displayOrder() {
        return orderReliable ? core : core.stream().sorted().toList();
    }
}
