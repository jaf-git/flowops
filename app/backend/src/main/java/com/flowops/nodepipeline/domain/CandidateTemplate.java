package com.flowops.nodepipeline.domain;

import java.time.LocalDate;
import java.util.List;

public record CandidateTemplate(
        String id,
        String title,
        String description,
        String workType,
        String responsibleRole,
        String outputKind,
        String expectedOutput,
        String requiredInput,
        String completionCriteria,
        List<String> checklist,
        String status,
        LocalDate approvedAt,
        List<String> keywords,
        String precedingRole,
        String followingRole,
        Integer position) {
    public CandidateTemplate {
        checklist = checklist == null ? List.of() : List.copyOf(checklist);
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
    }

    public int substance() {
        return (int) java.util.stream.Stream.of(
                        description, expectedOutput, requiredInput, completionCriteria, responsibleRole, outputKind)
                .filter(field -> field != null && field.length() > 3)
                .count();
    }

    public boolean eligibleFor(LocalDate workDoneOn) {
        return "APPROVED".equals(status)
                && approvedAt != null
                && workDoneOn != null
                && !approvedAt.isAfter(workDoneOn)
                && substance() >= 2;
    }
}
