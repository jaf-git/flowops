package com.flowops.aiexport.application.port;

import com.flowops.aiexport.application.AnalyticalRecords.ConversionRecord;
import com.flowops.aiexport.application.AnalyticalRecords.InstanceRecord;
import com.flowops.aiexport.application.AnalyticalRecords.StepRecord;
import com.flowops.aiexport.application.AnalyticalRecords.TaskRecord;
import com.flowops.aiexport.application.PerExportPseudonymiser;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

public interface AnalyticalReadPort {
    record Window(Instant from, Instant to, UUID subjectId) {
        public static Window everything() {
            return new Window(null, null, null);
        }

        public boolean isScoped() {
            return subjectId != null;
        }
    }

    void eachTask(Window window, PerExportPseudonymiser as, Consumer<TaskRecord> sink);

    void eachInstance(Window window, PerExportPseudonymiser as, Consumer<InstanceRecord> sink);

    void eachStep(Window window, Consumer<StepRecord> sink);

    void eachConversion(Window window, PerExportPseudonymiser as, Consumer<ConversionRecord> sink);

    Sufficiency sufficiency(Window window);

    boolean processTemplateExists(UUID subjectId);

    record Sufficiency(
            long tasksTotal,
            long tasksClosed,
            Integer templatesTotal,
            Integer templatesWithFewerThanFiveUses,
            long instancesCompleted,
            long templatesWithNoCompletedInstance,
            double stepsAttachedRatio,
            Double medianTaskLifespanDays,
            long dateRangeDays) {}
}
