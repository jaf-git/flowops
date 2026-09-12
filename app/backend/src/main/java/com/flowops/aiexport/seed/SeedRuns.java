package com.flowops.aiexport.seed;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class SeedRuns {
    private static final String THE_STEP_THE_PLAYBOOKS_FORGET = "Chase the client for feedback";

    private static final String CLIENT_SIGN_OFF = "Get the client to approve it";

    private static final int[] RUNS_PER_PLAYBOOK = {5, 4, 3, 3, 3};

    private final SeedCompany company;
    private final SeedSchedule schedule;
    private final SeedTaskLifecycle lifecycle;
    private final ErrandTemplates errandTemplates;

    private final Map<String, UUID> libraryByTitle = new LinkedHashMap<>();

    SeedRuns(SeedCompany company, SeedSchedule schedule, SeedTaskLifecycle lifecycle, ErrandTemplates errandTemplates) {
        this.company = company;
        this.schedule = schedule;
        this.lifecycle = lifecycle;
        this.errandTemplates = errandTemplates;
    }

    record Template(UUID id, String name, List<UUID> stepDefinitions) {}

    private record Run(UUID id, List<UUID> steps) {}

    private record Step(String title, String description, int hours) {}

    private static Step step(String title, String description, int hours) {
        return new Step(title, description, hours);
    }

    record Playbook(
            Template template,
            boolean forgetsAStep,
            int attachAfterIndex,
            int blockAtIndex,
            String blockReason,
            int slowIndex,
            int slowWaitingHours) {}

    private Playbook newClientSetup(SeedCompany.Person owner) {
        Template template = authorTemplate(
                owner,
                "New client setup",
                "Everything that has to happen between somebody signing and us doing the first piece of work.",
                List.of(
                        step("Kick-off call", "Meet them properly and write down what they want", 2),
                        step("Collect their logos and photos", "Whatever they already have, in one folder", 3),
                        step("Set up their accounts", "Ads, analytics and the scheduling tool", 3),
                        step("Send them the first plan", "One page, so they can see where this is going", 2)));

        return new Playbook(template, true, 1, 1, "waiting on the client to send their files", 0, 15);
    }

    private Playbook monthlySocialPlan(SeedCompany.Person owner) {
        Template template = authorTemplate(
                owner,
                "Monthly social media plan",
                "What goes out next month, agreed before the month starts.",
                List.of(
                        step("Check last month's numbers", "What people actually looked at", 2),
                        step("Pick the topics", "Three or four, not thirty", 2),
                        step("Write the posts", "Captions and the pictures they go with", 4),
                        step(CLIENT_SIGN_OFF, "One pass, with everything in front of them", 2)));
        return new Playbook(template, true, 1, 3, "waiting on the client to approve next month's plan", 3, 13);
    }

    private Playbook websiteTextRefresh(SeedCompany.Person owner) {
        Template template = authorTemplate(
                owner,
                "Website text refresh",
                "Rewriting a client's pages so they say what the business actually does.",
                List.of(
                        step("List the pages that need work", "Every page, and whether it earns its place", 2),
                        step("Write the new text", "In their voice, not ours", 4),
                        step("One round of edits", "One editor, one pass, everything", 2),
                        step("Put it live", "With the old pages pointing at the new ones", 2),
                        step(CLIENT_SIGN_OFF, "One pass, with everything in front of them", 2)));

        dependency(owner, template, 2, 0);
        return new Playbook(template, false, -1, 1, "waiting on the client's lawyer to check the wording", 2, 17);
    }

    private Playbook adCampaign(SeedCompany.Person owner) {
        Template template = authorTemplate(
                owner,
                "Ad campaign",
                "From the brief to the day the ads go live.",
                List.of(
                        step("Write the brief", "The one thing it has to achieve", 2),
                        step("Make the images", "Every size the platforms want", 3),
                        step("Set up the ads", "Loaded, dated and checked twice", 2),
                        step("Turn them on", "Published, and watched for the first day", 1),
                        step(CLIENT_SIGN_OFF, "One pass, with everything in front of them", 2)));
        return new Playbook(template, true, 2, 1, "waiting on the client to send product photos", 1, 19);
    }

    private Playbook monthlyReport(SeedCompany.Person owner) {
        Template template = authorTemplate(
                owner,
                "Monthly report",
                "Last month's numbers, written up so a person can read them.",
                List.of(
                        step("Pull the numbers", "From each place they live", 2),
                        step("Compare with last month", "What moved, and whether it matters", 2),
                        step("Write the summary", "Half a page, in plain words", 3),
                        step("Send it to the client", "With the two things we would do next", 1)));
        return new Playbook(template, false, -1, -1, null, 2, 11);
    }

    private UUID taskTemplate(SeedCompany.Person owner, String title, String description, int hours) {
        return libraryByTitle.computeIfAbsent(title, wanted -> {
            SeedEstimatePolicy.Guess guess = SeedEstimatePolicy.guessFor(wanted);
            JsonNode created = owner.browser()
                    .postOrFail(
                            "/api/task-templates",
                            """
                            {"title":%s,"description":%s,"priority":"NORMAL","estimatedHours":%s,
                             "checklist":[],"submitForApproval":true}
                            """
                                    .formatted(
                                            SeedJson.quote(wanted), SeedJson.quote(description), guess.estimateJson()),
                            "writing the template " + wanted);
            UUID id = UUID.fromString(created.path("id").asText());

            owner.browser().postOrFail("/api/task-templates/" + id + "/approval", null, "approving " + wanted);
            return id;
        });
    }

    private Template authorTemplate(SeedCompany.Person owner, String name, String overview, List<Step> steps) {
        List<String> referenced = new ArrayList<>();
        for (Step each : steps) {
            UUID work = taskTemplate(owner, each.title(), each.description(), each.hours());
            referenced.add("{\"taskTemplateId\":\"" + work + "\",\"expectedDurationHours\":" + each.hours() + "}");
        }
        JsonNode created = owner.browser()
                .postOrFail(
                        "/api/process-templates",
                        "{\"name\":" + SeedJson.quote(name) + ",\"overview\":" + SeedJson.quote(overview)
                                + ",\"steps\":[" + String.join(",", referenced) + "]}",
                        "authoring " + name);

        List<UUID> stepIds = new ArrayList<>();
        for (JsonNode each : created.path("steps")) {
            stepIds.add(UUID.fromString(each.path("id").asText()));
        }
        if (stepIds.size() != steps.size()) {
            throw new SeedFailedException(
                    name + " was authored with " + stepIds.size() + " steps rather than " + steps.size());
        }
        Template template = new Template(UUID.fromString(created.path("id").asText()), name, stepIds);

        for (int index = 1; index < stepIds.size(); index++) {
            dependency(owner, template, index, index - 1);
        }
        return template;
    }

    private void dependency(SeedCompany.Person owner, Template template, int dependentIndex, int dependsOnIndex) {
        owner.browser()
                .postOrFail(
                        "/api/process-templates/" + template.id() + "/dependencies",
                        "{\"dependentStepId\":\"" + template.stepDefinitions().get(dependentIndex)
                                + "\",\"dependsOnStepId\":\""
                                + template.stepDefinitions().get(dependsOnIndex) + "\"}",
                        "declaring a dependency in " + template.name());
    }

    static List<String> everyPlaybookStepTitle() {
        return List.of(
                "Kick-off call",
                "Collect their logos and photos",
                "Set up their accounts",
                "Send them the first plan",
                "Check last month's numbers",
                "Pick the topics",
                "Write the posts",
                CLIENT_SIGN_OFF,
                "List the pages that need work",
                "Write the new text",
                "One round of edits",
                "Put it live",
                "Write the brief",
                "Make the images",
                "Set up the ads",
                "Turn them on",
                "Pull the numbers",
                "Compare with last month",
                "Write the summary",
                "Send it to the client",
                THE_STEP_THE_PLAYBOOKS_FORGET);
    }

    List<Playbook> authorEveryPlaybook() {
        SeedCompany.Person owner = company.owner();
        return List.of(
                newClientSetup(owner),
                monthlySocialPlan(owner),
                websiteTextRefresh(owner),
                adCampaign(owner),
                monthlyReport(owner));
    }

    static List<String> everyStepTitle() {
        return List.of(THE_STEP_THE_PLAYBOOKS_FORGET);
    }

    void theFinishedRuns(List<Playbook> playbooks, List<SeedTeam> teams, int howMany) {
        List<Integer> deal = new ArrayList<>();
        for (int round = 0; round < RUNS_PER_PLAYBOOK[0]; round++) {
            for (int playbook = 0; playbook < playbooks.size(); playbook++) {
                if (round < RUNS_PER_PLAYBOOK[playbook]) {
                    deal.add(playbook);
                }
            }
        }

        int[] runsSoFar = new int[playbooks.size()];
        for (int i = 0; i < howMany && i < deal.size(); i++) {
            int which = deal.get(i);
            Playbook playbook = playbooks.get(which);
            SeedTeam team = teams.get(i % teams.size());
            SeedCompany.Person worker = team.reports().get(i % team.reports().size());
            int nth = runsSoFar[which]++;

            boolean addsTheForgottenStep = playbook.forgetsAStep() && nth % 4 != 3;

            oneRun(
                    playbook,
                    playbook.template().name() + " — " + SeedClients.forRun(nth),
                    team.manager(),
                    worker,
                    addsTheForgottenStep,
                    playbook.template().stepDefinitions().size());

            if (i % 2 == 1) {
                schedule.nextWorkingMorning();
            }
        }
    }

    void theRunsStillInFlight(List<Playbook> playbooks, List<SeedTeam> teams, int howMany) {
        for (int i = 0; i < howMany; i++) {
            Playbook playbook = playbooks.get(i % playbooks.size());
            SeedTeam team = teams.get(i % teams.size());
            SeedCompany.Person worker =
                    team.liveReports().get((i + 1) % team.liveReports().size());
            String name = playbook.template().name() + " — " + SeedClients.forLiveRun(i);

            int stepsDone =
                    i % Math.max(1, playbook.template().stepDefinitions().size() - 1);
            oneRun(playbook, name, team.manager(), worker, playbook.forgetsAStep() && i % 2 == 0, stepsDone);

            Run run = latestRunOf(team.manager(), name);
            UUID task = assign(team.manager(), run, stepsDone, worker, 6 + i);
            lifecycle.leaveItLive(worker, task, i, playbook.blockReason());
            schedule.advanceHours(3);
        }
    }

    private void oneRun(
            Playbook playbook,
            String name,
            SeedCompany.Person processOwner,
            SeedCompany.Person worker,
            boolean addsTheForgottenStep,
            int stepsToFinish) {
        Run run = instantiate(company.owner(), playbook.template(), name, processOwner);

        for (int index = 0; index < stepsToFinish; index++) {
            boolean blocks = index == playbook.blockAtIndex();
            boolean waits = index == playbook.slowIndex();

            lifecycle.driveToClosure(
                    worker,
                    processOwner,
                    assign(processOwner, run, index, worker, waits ? 9 : 4),
                    index == stepsToFinish - 1 ? schedule.occasionallyMuchLonger(2) : schedule.hoursAround(3),
                    blocks ? 9 : 0,
                    waits ? playbook.slowWaitingHours() : 1,
                    blocks ? playbook.blockReason() : null);

            if (addsTheForgottenStep && index == playbook.attachAfterIndex()) {
                lifecycle.driveToClosure(
                        worker, processOwner, attachTheForgottenStep(processOwner, run, worker), 1, 0, 4, null);
            }
        }
    }

    private Run instantiate(
            SeedCompany.Person starter, Template template, String name, SeedCompany.Person processOwner) {
        JsonNode created = starter.browser()
                .postOrFail(
                        "/api/process-instances",
                        "{\"templateId\":\"" + template.id() + "\",\"name\":" + SeedJson.quote(name)
                                + ",\"processOwnerId\":\"" + processOwner.personId() + "\"}",
                        "starting " + name);

        List<UUID> steps = new ArrayList<>();
        for (JsonNode each : created.path("steps")) {
            steps.add(UUID.fromString(each.path("id").asText()));
        }
        return new Run(UUID.fromString(created.path("id").asText()), steps);
    }

    private UUID assign(
            SeedCompany.Person processOwner, Run run, int stepIndex, SeedCompany.Person assignee, int daysAllowed) {
        Instant deadline = schedule.deadlineIn(daysAllowed);
        UUID step = run.steps().get(stepIndex);
        JsonNode instance = processOwner
                .browser()
                .postOrFail(
                        "/api/process-instances/" + run.id() + "/steps/" + step + "/assignment",
                        "{\"assigneeId\":\"" + assignee.personId() + "\",\"deadline\":\"" + deadline + "\"}",
                        "assigning step " + (stepIndex + 1) + " to " + assignee.displayName());

        return taskOf(instance, step);
    }

    private UUID taskOf(JsonNode instance, UUID step) {
        for (JsonNode each : instance.path("steps")) {
            if (step.toString().equals(each.path("id").asText())) {
                String task = each.path("taskId").asText();
                if (task.isBlank()) {
                    throw new SeedFailedException("step " + step + " was assigned and carries no task");
                }
                return UUID.fromString(task);
            }
        }
        throw new SeedFailedException("step " + step + " is not in the run the assignment answered with");
    }

    private UUID attachTheForgottenStep(SeedCompany.Person processOwner, Run run, SeedCompany.Person assignee) {
        Instant deadline = schedule.deadlineIn(2);

        JsonNode task = processOwner
                .browser()
                .postOrFail(
                        "/api/task-templates/" + errandTemplates.forActivity(THE_STEP_THE_PLAYBOOKS_FORGET) + "/tasks",
                        "{\"title\":" + SeedJson.quote(THE_STEP_THE_PLAYBOOKS_FORGET)
                                + ",\"description\":\"Nudge them; nothing has come back yet.\",\"assigneeId\":\""
                                + assignee.personId() + "\",\"deadline\":\"" + deadline + "\",\"priority\":\"NORMAL\"}",
                        "stamping the step the playbook forgot");
        UUID taskId = UUID.fromString(task.path("taskId").asText());

        processOwner
                .browser()
                .postOrFail(
                        "/api/process-instances/" + run.id() + "/tasks",
                        "{\"taskId\":\"" + taskId + "\",\"dependsOnStepIds\":[]}",
                        "attaching " + THE_STEP_THE_PLAYBOOKS_FORGET + " to the run");
        return taskId;
    }

    private Run latestRunOf(SeedCompany.Person processOwner, String name) {
        JsonNode instances = processOwner.browser().getOrFail("/api/process-instances", "listing the runs");
        for (JsonNode each : instances.path("instances")) {
            if (name.equals(each.path("name").asText())) {
                JsonNode full = processOwner
                        .browser()
                        .getOrFail("/api/process-instances/" + each.path("id").asText(), "reading the run " + name);
                List<UUID> steps = new ArrayList<>();
                for (JsonNode step : full.path("steps")) {
                    steps.add(UUID.fromString(step.path("id").asText()));
                }
                return new Run(UUID.fromString(full.path("id").asText()), steps);
            }
        }
        throw new SeedFailedException("the run " + name + " was started and cannot be found again");
    }
}
