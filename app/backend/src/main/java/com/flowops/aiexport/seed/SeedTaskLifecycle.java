package com.flowops.aiexport.seed;

import java.time.Duration;
import java.util.UUID;

final class SeedTaskLifecycle {
    private static final int ORDINARY_WAITING_HOURS = 1;

    private final SeedSchedule schedule;

    SeedTaskLifecycle(SeedSchedule schedule) {
        this.schedule = schedule;
    }

    void driveToClosure(SeedCompany.Person assignee, SeedCompany.Person reviewer, UUID task, int workHours) {
        driveToClosure(assignee, reviewer, task, workHours, 0, ORDINARY_WAITING_HOURS, null);
    }

    void driveToClosure(
            SeedCompany.Person assignee,
            SeedCompany.Person reviewer,
            UUID task,
            int workHours,
            int blockedHours,
            int waitingHours,
            String blockReason) {
        schedule.advanceHours(waitingHours);
        assignee.browser().postOrFail("/api/tasks/" + task + "/accept", "{}", "accepting the task");

        schedule.advanceMinutes(15);
        assignee.browser().postOrFail("/api/tasks/" + task + "/start", "{}", "starting the task");

        if (blockedHours > 0) {
            schedule.advanceHours(Math.max(1, workHours / 2));
            assignee.browser()
                    .postOrFail(
                            "/api/tasks/" + task + "/block",
                            "{\"reason\":" + SeedJson.quote(blockReason) + "}",
                            "blocking the task");

            schedule.advanceHours(schedule.hoursAround(blockedHours));
            assignee.browser()
                    .postOrFail(
                            "/api/tasks/" + task + "/unblock",
                            "{\"resolution\":\"they sent it through in the end\"}",
                            "unblocking the task");
            schedule.advanceHours(Math.max(1, workHours - workHours / 2));
        } else {
            schedule.advanceHours(workHours);
        }

        assignee.browser()
                .postOrFail(
                        "/api/tasks/" + task + "/complete",
                        "{\"note\":\"Done and sent on.\",\"externalLink\":null}",
                        "completing the task");

        schedule.advanceMinutes(30L + schedule.upTo(60));
        reviewer.browser()
                .postOrFail(
                        "/api/tasks/" + task + "/approve",
                        "{\"score\":" + (4 + schedule.upTo(2)) + ",\"comment\":\"Looks right.\"}",
                        "approving the task");

        schedule.advance(Duration.ofMinutes(10L + schedule.upTo(30)));
        reviewer.browser().postOrFail("/api/tasks/" + task + "/close", "{}", "closing the task");
    }

    void leaveItLive(SeedCompany.Person worker, UUID task, int which, String blockReason) {
        int state = Math.floorMod(which, 5);
        if (state == 0) {
            return;
        }

        worker.browser().postOrFail("/api/tasks/" + task + "/accept", "{}", "accepting");
        if (state == 1) {
            return;
        }

        schedule.advanceHours(1);
        worker.browser().postOrFail("/api/tasks/" + task + "/start", "{}", "starting");
        if (state == 2) {
            return;
        }

        schedule.advanceHours(2);
        if (state == 3) {
            worker.browser()
                    .postOrFail(
                            "/api/tasks/" + task + "/block",
                            "{\"reason\":" + SeedJson.quote(blockReason == null ? "waiting on the client" : blockReason)
                                    + "}",
                            "blocking the task that stays blocked");
            return;
        }

        worker.browser()
                .postOrFail(
                        "/api/tasks/" + task + "/complete",
                        "{\"note\":\"Ready for a look.\",\"externalLink\":null}",
                        "completing, and leaving it for review");
    }
}
