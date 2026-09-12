package com.flowops.automation.application.detect;

import com.flowops.automation.application.shared.port.StepRiskReadPort;
import com.flowops.automation.application.shared.port.WorkspaceThresholdPort.Thresholds;
import com.flowops.shared.marker.WorkMarkers;
import com.flowops.shared.notice.NotificationKind;
import com.flowops.shared.notice.SubjectRef;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class StalledStepDetection implements Detection {
    private final StepRiskReadPort steps;

    public StalledStepDetection(StepRiskReadPort steps) {
        this.steps = steps;
    }

    @Override
    public NotificationKind kind() {
        return NotificationKind.STEP_STALLED;
    }

    @Override
    public List<Finding> evaluate(Instant now, Thresholds thresholds) {
        return steps.reachableAndUnassigned().stream()
                .filter(step -> WorkMarkers.beyond(step.reachableAt(), now, thresholds.stall(), thresholds.calendar()))
                .map(step -> new Finding(kind(), SubjectRef.step(step.id()), step.runOwner(), step.reachableAt()))
                .toList();
    }
}
