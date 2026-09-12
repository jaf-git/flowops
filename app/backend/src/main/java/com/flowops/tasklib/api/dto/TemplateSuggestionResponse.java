package com.flowops.tasklib.api.dto;

import com.flowops.tasklib.application.port.TaskTemplatePort;
import java.util.UUID;

public record TemplateSuggestionResponse(UUID id, String title, int timesUsed, double similarity) {
    public static TemplateSuggestionResponse of(TaskTemplatePort.Resemblance resemblance) {
        return new TemplateSuggestionResponse(
                resemblance.template().id(),
                resemblance.template().details().title(),
                resemblance.template().timesUsed(),
                resemblance.similarity());
    }
}
