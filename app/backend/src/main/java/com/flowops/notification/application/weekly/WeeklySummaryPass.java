package com.flowops.notification.application.weekly;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WeeklySummaryPass {
    private static final Logger LOG = LoggerFactory.getLogger(WeeklySummaryPass.class);

    private final WeeklySummaryUseCase weekly;

    public WeeklySummaryPass(WeeklySummaryUseCase weekly) {
        this.weekly = weekly;
    }

    @Scheduled(
            cron = "${flowops.notification.weekly-cron:0 0 17 * * FRI}",
            zone = "${flowops.timezone:Europe/Bucharest}")
    public void raiseTheWeekly() {
        try {
            int raised = weekly.raise();
            if (raised > 0) {
                LOG.debug("weekly summary raised for {} people", raised);
            }
        } catch (RuntimeException failure) {
            LOG.warn("the weekly summary was not raised; the next Friday tries again", failure);
        }
    }
}
