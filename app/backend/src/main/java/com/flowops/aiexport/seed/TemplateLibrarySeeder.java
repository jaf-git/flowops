package com.flowops.aiexport.seed;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("demo")
@Order(15)
public class TemplateLibrarySeeder implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(TemplateLibrarySeeder.class);

    private static final String PASSWORD = "password1234";

    private static final Template[] LIBRARY = {
        new Template(
                "Monthly performance report",
                "Last month's reach, engagement and spend, written up for the client's meeting.",
                "Reporting",
                "NORMAL",
                "1.5",
                List.of(
                        "Export the figures from each channel",
                        "Compare against last month",
                        "Write the commentary",
                        "Send to the account lead")),
        new Template(
                "Quarterly brand consistency check",
                "Everything that went out with the client's name on it, checked against the guidelines.",
                "Brand",
                "HIGH",
                "6",
                List.of("Collect the quarter's assets", "Check against the guidelines", "Note what drifted")),
        new Template(
                "Weekly social summary",
                "The week's numbers, for Monday's stand-up.",
                "Social",
                "NORMAL",
                "2",
                List.of("Export the figures", "Compare against last week")),
        new Template(
                "New client onboarding",
                "Collect the brief, get the contract signed, hand over to production, book the kickoff.",
                "Onboarding",
                "HIGH",
                "4",
                List.of(
                        "Collect the brief from the client",
                        "Send the contract for signature",
                        "Hand over to the production team",
                        "Book the kickoff")),
    };

    private static final int[] STAMPS = {6, 2, 0, 1};

    private final JdbcTemplate jdbc;
    private final WebServerApplicationContext server;

    public TemplateLibrarySeeder(JdbcTemplate jdbc, WebServerApplicationContext server) {
        this.jdbc = jdbc;
        this.server = server;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (arguments.containsOption("discovery")) {
            LOG.info("Discovery mode: the library is deliberately left empty, so there is nothing to plant.");
            return;
        }
        if (alreadySeeded()) {
            LOG.info("The task library already holds templates; nothing to seed.");
            return;
        }
        if (assignees().isEmpty()) {
            LOG.warn("No colleagues to stamp work for, so the library is not seeded.");
            return;
        }

        String root = "http://localhost:" + server.getWebServer().getPort();
        SeedHttpClient owner = signIn(root, ownerEmail());
        List<String> people = assignees();

        int stamped = 0;
        List<UUID> approved = new ArrayList<>();

        for (int index = 0; index < LIBRARY.length; index++) {
            Template template = LIBRARY[index];
            UUID id = author(owner, template);
            approved.add(id);

            for (int copy = 0; copy < STAMPS[index]; copy++) {
                String assignee = people.get((stamped + copy) % people.size());
                stamp(owner, id, template, assignee, copy + 1);
            }
            stamped += STAMPS[index];
        }

        theLibraryPredatesTheHistoryItDescribes();

        schedule(owner, approved.get(0), people.get(0));

        LOG.info(
                "Seeded {} task templates and stamped {} tasks from them, plus one monthly schedule."
                        + " Run with --exercise-templates to drive that work through review.",
                LIBRARY.length,
                stamped);
    }

    private UUID author(SeedHttpClient owner, Template template) {
        JsonNode created = owner.postOrFail(
                "/api/task-templates",
                """
                {"title":%s,"description":%s,"type":%s,"priority":"%s","estimatedHours":%s,
                 "checklist":[%s],"submitForApproval":true}
                """
                        .formatted(
                                SeedJson.quote(template.title()),
                                SeedJson.quote(template.description()),
                                SeedJson.quote(template.type()),
                                template.priority(),
                                template.estimatedHours(),
                                template.checklist().stream()
                                        .map(SeedJson::quote)
                                        .reduce((left, right) -> left + "," + right)
                                        .orElse("")),
                "writing the template " + template.title());

        UUID id = UUID.fromString(created.get("id").asText());
        owner.postOrFail("/api/task-templates/" + id + "/approval", null, "approving " + template.title());
        return id;
    }

    private void stamp(SeedHttpClient owner, UUID templateId, Template template, String assigneeId, int number) {
        owner.postOrFail(
                "/api/task-templates/" + templateId + "/tasks",
                """
                {"title":%s,"assigneeId":"%s"}
                """
                        .formatted(SeedJson.quote(template.title() + " #" + number), assigneeId),
                "stamping " + template.title() + " #" + number);
    }

    private void schedule(SeedHttpClient owner, UUID templateId, String assigneeId) {
        owner.postOrFail(
                "/api/task-templates/" + templateId + "/schedules",
                """
                {"assigneeId":"%s","cadence":"MONTHLY","dayOfMonth":1}
                """
                        .formatted(assigneeId),
                "scheduling the monthly invoice check");
    }

    /**
     * Moves the seeded library's approval back to the day before the history starts.
     *
     * <p>This runner is {@code @Order(15)} and {@link DemoHistorySeeder} is {@code @Order(10)}, so
     * by the time a template is approved the demo clock has already walked past every mark the
     * history contains. {@code CandidateTemplate.eligibleFor} then refuses all of them — correctly,
     * because it will not judge last week's work with a template approved this morning — and the
     * result was that a pipeline run over the demonstration corpus scored **nothing**: 800 marks,
     * every one abstaining with {@code no_eligible_template}.
     *
     * <p>Reordering the runners does not work: this one stamps tasks onto colleagues, and the
     * colleagues are seeded by the history it would have to run before. Back-dating states the
     * thing that is actually true of the fixture — a business writes down how it works, and then
     * does the work — which is the only arrangement in which the matcher has anything to match.
     */
    private void theLibraryPredatesTheHistoryItDescribes() {
        String placeholders = String.join(",", Collections.nCopies(LIBRARY.length, "?"));

        Object[] arguments = new Object[LIBRARY.length + 1];
        arguments[0] = Timestamp.from(DemoClockConfiguration.HISTORY_BEGINS.minus(1, ChronoUnit.DAYS));
        for (int at = 0; at < LIBRARY.length; at++) {
            arguments[at + 1] = LIBRARY[at].title();
        }

        int moved = jdbc.update(
                "update task_template set approved_at = ? where approved_at is not null and title in (" + placeholders
                        + ")",
                arguments);

        LOG.info("Back-dated {} approved templates to before the history, so the matcher has a library.", moved);
    }

    private boolean alreadySeeded() {
        String placeholders = String.join(",", Collections.nCopies(LIBRARY.length, "?"));
        Object[] titles = Arrays.stream(LIBRARY).map(Template::title).toArray();
        Integer mine = jdbc.queryForObject(
                "select count(*) from task_template where title in (" + placeholders + ")", Integer.class, titles);
        return mine != null && mine > 0;
    }

    private List<String> assignees() {
        return jdbc.queryForList(
                """
                select u.id::text
                from auth_user u
                join workspace_membership m on m.user_id = u.id
                where u.email <> ? and m.status = 'ACTIVE'
                order by u.created_at
                limit 6
                """,
                String.class,
                ownerEmail());
    }

    private String ownerEmail() {
        return jdbc.queryForObject("select email from auth_user order by created_at limit 1", String.class);
    }

    private SeedHttpClient signIn(String root, String email) {
        SeedHttpClient browser = new SeedHttpClient(root);
        browser.mintCrossSiteToken();
        browser.postOrFail(
                "/api/auth/login",
                "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}",
                "signing " + email + " in");
        return browser;
    }

    private record Template(
            String title,
            String description,
            String type,
            String priority,
            String estimatedHours,
            List<String> checklist) {}
}
