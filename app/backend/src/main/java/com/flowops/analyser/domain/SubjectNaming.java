package com.flowops.analyser.domain;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public final class SubjectNaming {
    private static final String SHAPE_STEP_SEPARATOR = "\\+";

    private SubjectNaming() {}

    public static String of(Finding finding, Map<String, String> names) {
        String subject = finding.subject();
        if (subject == null || subject.isBlank()) {
            return WorkName.NOT_NAMED;
        }

        return switch (finding.subjectKind()) {
            case SHAPE -> Arrays.stream(subject.split(SHAPE_STEP_SEPARATOR))
                    .map(step -> WorkName.named(step, names))
                    .collect(Collectors.joining(" · "));

            case WORK_TYPE -> names.getOrDefault(
                    subject, WorkName.humanised(subject).orElse(WorkName.NOT_NAMED));

            case CLIENT, ROLE_PAIR, ADDRESS, JOB -> subject;

            case WORKSPACE -> "This workspace";
        };
    }
}
