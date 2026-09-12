package com.flowops.tasklib.domain;

import com.flowops.tasklib.domain.exception.UnknownMetadataValueException;
import java.util.List;
import java.util.Optional;

public record TemplateMetadata(
        String responsibleRole,
        String triggerNote,
        String requiredInput,
        String expectedOutput,
        OutputKind outputKind,
        String completionCriteria) {
    public TemplateMetadata {
        responsibleRole = blankToNull(responsibleRole);
        triggerNote = blankToNull(triggerNote);
        requiredInput = blankToNull(requiredInput);
        expectedOutput = blankToNull(expectedOutput);
        completionCriteria = blankToNull(completionCriteria);
    }

    public static TemplateMetadata empty() {
        return new TemplateMetadata(null, null, null, null, null, null);
    }

    public Optional<MetadataField> nextMissing() {
        return java.util.Arrays.stream(MetadataField.values())
                .filter(field -> valueOf(field) == null)
                .findFirst();
    }

    public boolean isComplete() {
        return nextMissing().isEmpty();
    }

    public int answered() {
        return (int) java.util.Arrays.stream(MetadataField.values())
                .filter(field -> valueOf(field) != null)
                .count();
    }

    public List<MetadataField> missing() {
        return java.util.Arrays.stream(MetadataField.values())
                .filter(field -> valueOf(field) == null)
                .toList();
    }

    public TemplateMetadata with(MetadataField field, String answer) {
        String value = blankToNull(answer);
        return switch (field) {
            case RESPONSIBLE_ROLE -> new TemplateMetadata(
                    value, triggerNote, requiredInput, expectedOutput, outputKind, completionCriteria);
            case TRIGGER_NOTE -> new TemplateMetadata(
                    responsibleRole, value, requiredInput, expectedOutput, outputKind, completionCriteria);
            case REQUIRED_INPUT -> new TemplateMetadata(
                    responsibleRole, triggerNote, value, expectedOutput, outputKind, completionCriteria);
            case EXPECTED_OUTPUT -> new TemplateMetadata(
                    responsibleRole, triggerNote, requiredInput, value, outputKind, completionCriteria);
            case OUTPUT_KIND -> new TemplateMetadata(
                    responsibleRole, triggerNote, requiredInput, expectedOutput, kindFrom(value), completionCriteria);
            case COMPLETION_CRITERIA -> new TemplateMetadata(
                    responsibleRole, triggerNote, requiredInput, expectedOutput, outputKind, value);
        };
    }

    public String valueOf(MetadataField field) {
        return switch (field) {
            case RESPONSIBLE_ROLE -> responsibleRole;
            case TRIGGER_NOTE -> triggerNote;
            case REQUIRED_INPUT -> requiredInput;
            case EXPECTED_OUTPUT -> expectedOutput;
            case OUTPUT_KIND -> outputKind == null ? null : outputKind.name();
            case COMPLETION_CRITERIA -> completionCriteria;
        };
    }

    private static OutputKind kindFrom(String value) {
        if (value == null) {
            return null;
        }
        return OutputKind.named(value)
                .orElseThrow(() -> new UnknownMetadataValueException(
                        "'" + value + "' is not a kind of output this product recognises"));
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
