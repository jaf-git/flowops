package com.flowops.aiexport.seed;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class SeedNarrative {
    private final SeedCompany company;
    private final SeedSchedule schedule;
    private final SeedTaskLifecycle lifecycle;

    private ErrandTemplates errandTemplates;

    SeedNarrative(MutableClock clock, SeedCompany company, long randomSeed) {
        this.company = company;
        this.schedule = new SeedSchedule(clock, randomSeed);
        this.lifecycle = new SeedTaskLifecycle(schedule);
    }

    record Scale(int completedRuns, int runsInFlight, int errandsClosed, int errandsInFlight) {
        static Scale full() {
            return new Scale(18, 5, 70, 30);
        }

        static Scale thin() {
            return new Scale(2, 0, 3, 1);
        }

        int approximateTasks() {
            return completedRuns * 5 + runsInFlight * 2 + errandsClosed + errandsInFlight;
        }
    }

    private boolean isThin(Scale scale) {
        return scale.completedRuns() < 3;
    }

    private void reached(String stage) {
        LOG.info("The clock is at {} after {}.", schedule.now(), stage);
    }

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(SeedNarrative.class);

    void runTheObservationsOnly() {
        new SeedOrganisation(company).sayWhatEverybodyDoes();

        SeedDiscovery discovery = new SeedDiscovery(company, schedule);
        new SeedConversations(company, schedule, lifecycle).seedTheMessagesAndMarkTheWorkInThem(discovery);
        reached("the conversations");

        discovery.sayWhatDiscoveryMadeOfIt();
    }

    void runEverything(Scale scale, Instant liveWorkAnchor) {
        SeedCompany.Person owner = company.owner();

        new SeedOrganisation(company).sayWhatEverybodyDoes();

        SeedTeam clientTeam = SeedTeam.whereSomebodyLeaves(
                company.named(SeedCompany.STRATEGY_MANAGER),
                List.of(company.named("Andrei Munteanu"), company.named(SeedCompany.THE_COLLEAGUE_WHO_LEAVES)),
                company.named(SeedCompany.THE_COLLEAGUE_WHO_LEAVES),
                Errands.clientServices());
        SeedTeam studioTeam = SeedTeam.of(
                company.named(SeedCompany.SOCIAL_MANAGER),
                List.of(company.named("Cosmin Vasile"), company.named("Daria Enache")),
                Errands.studio());
        List<SeedTeam> teams = List.of(clientTeam, studioTeam);

        List<String> activities = new ArrayList<>();
        teams.forEach(team -> activities.addAll(team.errands().everyTemplateTitle()));
        activities.addAll(SeedRuns.everyStepTitle());
        errandTemplates = ErrandTemplates.authoredBy(owner.browser(), activities);

        SeedRuns runs = new SeedRuns(company, schedule, lifecycle, errandTemplates);
        var playbooks = runs.authorEveryPlaybook();

        SeedDiscovery discovery = new SeedDiscovery(company, schedule);
        new SeedConversations(company, schedule, lifecycle).seedTheHistoryAndMarkTheWorkInIt(discovery);
        reached("the conversations");

        discovery.sayWhatDiscoveryMadeOfIt();

        runs.theFinishedRuns(playbooks, teams, scale.completedRuns());
        reached("the finished runs");

        theErrandsThatWereDone(teams, scale.errandsClosed());
        reached("the closed errands");

        if (!schedule.jumpTo(liveWorkAnchor) && !isThin(scale)) {
            LOG.warn(
                    "The finished history ran past {} and reached {}, so the live work sits at the end of the"
                            + " history rather than at the anchor. Not fatal — every date is still consistent."
                            + " To move it, seed less or push LIVE_WORK_BEGINS out in DemoClockConfiguration.",
                    liveWorkAnchor,
                    schedule.now());
        }

        runs.theRunsStillInFlight(playbooks, teams, scale.runsInFlight());
        theErrandsStillInFlight(teams, scale.errandsInFlight());
        theColleagueWhoLeftAndAskedToBeForgotten(owner, company.named(SeedCompany.THE_COLLEAGUE_WHO_LEAVES));
    }

    private void theErrandsThatWereDone(List<SeedTeam> teams, int howMany) {
        int each = howMany / teams.size();
        for (SeedTeam team : teams) {
            for (int i = 0; i < each; i++) {
                SeedCompany.Person worker =
                        team.reports().get(i % team.reports().size());
                boolean mustFinish = SeedCompany.THE_COLLEAGUE_WHO_LEAVES.equals(worker.displayName());
                String work = team.errands().templateTitle(i);

                UUID task = stampErrand(team.manager(), worker, team.errands(), i, 4);
                if (mustFinish || SeedClosurePolicy.closes(work, i)) {
                    lifecycle.driveToClosure(worker, team.manager(), task, 1, 0, 1, null);
                } else {
                    lifecycle.leaveItLive(worker, task, i, null);
                }

                if (i % 12 == 11) {
                    schedule.nextWorkingMorning();
                }
            }
        }
    }

    private void theErrandsStillInFlight(List<SeedTeam> teams, int howMany) {
        int each = howMany / teams.size();
        for (SeedTeam team : teams) {
            for (int i = 0; i < each; i++) {
                SeedCompany.Person worker =
                        team.liveReports().get(i % team.liveReports().size());

                UUID task = stampErrand(team.manager(), worker, team.errands(), LIVE_TITLES_BEGIN_AT + i, daysOut(i));
                lifecycle.leaveItLive(worker, task, i, null);
                if (i % 9 == 8) {
                    schedule.nextWorkingMorning();
                }
            }
        }
    }

    private int daysOut(int i) {
        return i % 5 == 0 ? 1 : 3 + (i * 3) % 28;
    }

    private static final int LIVE_TITLES_BEGIN_AT = 100_000;

    private UUID stampErrand(
            SeedCompany.Person creator, SeedCompany.Person assignee, Errands errands, int n, int daysAllowed) {
        String title = errands.title(n);
        UUID templateId = errandTemplates.forActivity(errands.templateTitle(n));
        Instant deadline = schedule.deadlineIn(daysAllowed);
        JsonNode task = creator.browser()
                .postOrFail(
                        "/api/task-templates/" + templateId + "/tasks",
                        "{\"title\":" + SeedJson.quote(title) + ",\"assigneeId\":\"" + assignee.personId()
                                + "\",\"deadline\":\"" + deadline + "\",\"priority\":\"" + priority(title) + "\"}",
                        "stamping \"" + title + "\"");

        return UUID.fromString(task.path("taskId").asText());
    }

    private static String priority(String title) {
        int bucket = Math.floorMod(title.hashCode(), 10);
        if (bucket == 0) {
            return "URGENT";
        }
        return bucket < 4 ? "HIGH" : "NORMAL";
    }

    private void theColleagueWhoLeftAndAskedToBeForgotten(SeedCompany.Person owner, SeedCompany.Person leaving) {
        owner.browser()
                .postOrFail(
                        "/api/workspace/people/" + leaving.membershipId() + "/deactivate",
                        "{}",
                        "deactivating " + leaving.displayName());
        schedule.nextWorkingMorning();
        company.reauthenticate(owner);
        owner.browser()
                .postOrFail(
                        "/api/workspace/people/" + leaving.membershipId() + "/erase",
                        "{\"typedName\":" + SeedJson.quote(leaving.displayName()) + "}",
                        "erasing " + leaving.displayName());
    }
}
