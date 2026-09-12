package com.flowops.process.domain.model;

public record ProcessMetadata(String triggerNote, String endCondition, String ownerRole) {
    public ProcessMetadata {
        triggerNote = blankToNull(triggerNote);
        endCondition = blankToNull(endCondition);
        ownerRole = blankToNull(ownerRole);
    }

    public static ProcessMetadata empty() {
        return new ProcessMetadata(null, null, null);
    }

    public boolean isEmpty() {
        return triggerNote == null && endCondition == null && ownerRole == null;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
