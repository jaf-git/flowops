package com.flowops.workspace.application.viewsetupprefill;

import java.util.List;

public record ViewSetupPrefillResult(
        boolean setupCompleted, String suggestedTimezone, List<String> availableTimezones) {
    public ViewSetupPrefillResult {
        availableTimezones = List.copyOf(availableTimezones);
    }
}
