package com.flowops.aiexport.seed;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
@Order(20)
public class TemplateWorkExerciser implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(TemplateWorkExerciser.class);

    private static final String PASSWORD = "password1234";

    private static final int[] MINUTES = {60, 95, 130, 480, 600, 720};

    private static final int THIN_SAMPLE = 2;

    private final MutableClock clock;
    private final JdbcTemplate jdbc;
    private final WebServerApplicationContext server;

    public TemplateWorkExerciser(MutableClock clock, JdbcTemplate jdbc, WebServerApplicationContext server) {
        this.clock = clock;
        this.jdbc = jdbc;
        this.server = server;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (!arguments.containsOption("exercise-templates")) {
            return;
        }
        if (alreadyExercised()) {
            LOG.info("Template work has already been driven through review; nothing to do.");
            return;
        }

        String root = "http://localhost:" + server.getWebServer().getPort();
        Map<UUID, List<Candidate>> byTemplate = candidates();
        if (byTemplate.isEmpty()) {
            LOG.warn("No unstarted work is stamped from any template, so there is nothing to exercise.");
            return;
        }

        SeedHttpClient reviewer = signIn(root, ownerEmail());
        List<Walk> walks = choose(byTemplate, root);
        if (walks.isEmpty()) {
            LOG.warn("Every stamped task lacks an assignee, so none of it can be walked through.");
            return;
        }

        Instant start = clock.instant();
        for (Walk walk : walks) {
            walk.browser.post("/api/tasks/" + walk.taskId + "/accept", null);

            walk.browser.postOrFail(
                    "/api/tasks/" + walk.taskId + "/deadline",
                    "{\"deadline\":\"" + start.plus(Duration.ofDays(5)) + "\"}",
                    "dating " + walk.taskId);
            walk.browser.postOrFail("/api/tasks/" + walk.taskId + "/start", null, "starting " + walk.taskId);
        }

        for (Walk walk : walks) {
            clock.advanceTo(start.plus(Duration.ofMinutes(walk.minutes)));
            complete(walk, "Done and checked against the template's checklist.");

            if (walk.sentBackOnce) {
                reviewer.postOrFail(
                        "/api/tasks/" + walk.taskId + "/return",
                        "{\"reason\":\"The third checklist item is not evidenced. Attach what you sent.\"}",
                        "sending " + walk.taskId + " back");

                clock.advanceTo(clock.instant().plus(Duration.ofMinutes(45)));
                complete(walk, "Attached the email and the reconciliation sheet.");
            }

            reviewer.postOrFail(
                    "/api/tasks/" + walk.taskId + "/approve",
                    "{\"score\":4,\"comment\":\"Reads correctly and the figures tie out.\"}",
                    "approving " + walk.taskId);
            reviewer.postOrFail("/api/tasks/" + walk.taskId + "/close", null, "closing " + walk.taskId);
        }

        LOG.info(
                "Drove {} stamped tasks through review across {} templates. {} was sent back once, so first-try"
                        + " approval has something other than a full house to report. Clock now at {}.",
                walks.size(),
                walks.stream().map(Walk::templateId).distinct().count(),
                walks.stream().filter(Walk::sentBackOnce).count(),
                clock.instant());
    }

    private List<Walk> choose(Map<UUID, List<Candidate>> byTemplate, String root) {
        List<Walk> walks = new ArrayList<>();
        Map<String, SeedHttpClient> browsers = new LinkedHashMap<>();
        int template = 0;

        for (Map.Entry<UUID, List<Candidate>> entry : byTemplate.entrySet()) {
            int wanted = template == 0 ? MINUTES.length : THIN_SAMPLE;
            List<Candidate> taking = entry.getValue()
                    .subList(0, Math.min(wanted, entry.getValue().size()));

            for (int index = 0; index < taking.size(); index++) {
                Candidate candidate = taking.get(index);
                SeedHttpClient browser = browsers.computeIfAbsent(candidate.email(), email -> signIn(root, email));
                walks.add(new Walk(
                        candidate.taskId(),
                        entry.getKey(),
                        browser,
                        MINUTES[Math.min(index, MINUTES.length - 1)],
                        template == 0 && index == taking.size() - 1));
            }

            template++;
            if (template == 2) {
                break;
            }
        }

        walks.sort((left, right) -> Integer.compare(left.minutes, right.minutes));
        return walks;
    }

    private void complete(Walk walk, String note) {
        walk.browser.postOrFail(
                "/api/tasks/" + walk.taskId + "/complete", "{\"note\":\"" + note + "\"}", "completing " + walk.taskId);
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

    private Map<UUID, List<Candidate>> candidates() {
        Map<UUID, List<Candidate>> grouped = new LinkedHashMap<>();
        jdbc.query(
                """
                select t.id, t.template_id, u.email
                from task t
                join task_template tpl on tpl.id = t.template_id
                join auth_user u on u.id = t.assignee_user_id

                join workspace_membership m on m.user_id = u.id and m.status = 'ACTIVE'
                where t.template_id is not null
                  and t.state in ('CREATED', 'ACCEPTED')
                  and u.email <> ?
                order by tpl.times_used desc, tpl.id, t.created_at
                """,
                resultSet -> {
                    grouped.computeIfAbsent(
                                    UUID.fromString(resultSet.getString("template_id")), key -> new ArrayList<>())
                            .add(new Candidate(
                                    UUID.fromString(resultSet.getString("id")), resultSet.getString("email")));
                },
                ownerEmail());
        return grouped;
    }

    private boolean alreadyExercised() {
        Integer closed = jdbc.queryForObject(
                "select count(*) from task where template_id is not null and state = 'CLOSED'", Integer.class);
        return closed != null && closed > 0;
    }

    private String ownerEmail() {
        return jdbc.queryForObject("select email from auth_user order by created_at limit 1", String.class);
    }

    private record Candidate(UUID taskId, String email) {}

    private record Walk(UUID taskId, UUID templateId, SeedHttpClient browser, int minutes, boolean sentBackOnce) {}
}
