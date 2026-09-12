package com.flowops.aiexport.seed;

import java.time.Instant;
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
@Order(10)
public class DemoHistorySeeder implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(DemoHistorySeeder.class);

    private final MutableClock clock;
    private final JdbcTemplate jdbc;
    private final WebServerApplicationContext server;

    public DemoHistorySeeder(MutableClock clock, JdbcTemplate jdbc, WebServerApplicationContext server) {
        this.clock = clock;
        this.jdbc = jdbc;
        this.server = server;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        boolean thin = arguments.containsOption("thin");

        if (holdsWork()) {
            LOG.warn(
                    """
                    This installation already holds work, so the seed will not run.

                    Adding to an existing history would double every count the export reports, and
                    nothing downstream could tell that had happened.

                    To seed a fresh one, recreate the database and start again:
                      docker exec flowops-postgres psql -U flowops -d postgres \\
                        -c 'drop database if exists flowops' -c 'create database flowops owner flowops'

                    There is deliberately no --wipe flag. Emptying the tables here would also empty the
                    roles and permissions the migrations own, leaving a schema in which nobody can sign
                    up — and reconstructing those rows would mean re-implementing Flyway inside a demo
                    seeder, which is exactly the kind of near-correct code that fails confusingly a
                    month later.""");
            restoreTheClockToWhereTheHistoryEnded();
            return;
        }

        boolean observationsOnly = arguments.containsOption("discovery");

        String root = "http://localhost:" + server.getWebServer().getPort();
        LOG.info(
                "Seeding a demonstration history against {} (mode: {})",
                root,
                observationsOnly ? "discovery" : thin ? "thin" : "full");

        if (observationsOnly) {
            seedTheObservationsOnly(root);
            return;
        }

        SeedNarrative.Scale scale = thin ? SeedNarrative.Scale.thin() : SeedNarrative.Scale.full();

        MailboxReader mailbox = new MailboxReader(mailCatcherRoot());
        SeedCompany company = new SeedCompany(root, mailbox);
        SeedCompany.Person owner = company.build();

        LOG.info(
                "Seeding roughly {} tasks across {} finished and {} live runs, and several hundred chat messages"
                        + " of which about one in four is converted into work through the real endpoint. Every"
                        + " one of them goes through the product's own API, so this takes minutes rather than"
                        + " seconds — expect 10 to 20.",
                scale.approximateTasks(),
                scale.completedRuns(),
                scale.runsInFlight());

        SeedNarrative narrative = new SeedNarrative(clock, company, 20260819L);
        narrative.runEverything(scale, DemoClockConfiguration.LIVE_WORK_BEGINS);

        LOG.info(
                "Seeded {} people under {}, {} completed runs and {} left in flight. History runs to {}.",
                company.everybody().size(),
                owner.displayName(),
                scale.completedRuns(),
                scale.runsInFlight(),
                clock.instant());
        if (thin) {
            LOG.info("Thin mode: below every insight threshold, so the product should say nothing at all.");
        }
    }

    private void seedTheObservationsOnly(String root) {
        MailboxReader mailbox = new MailboxReader(mailCatcherRoot());
        SeedCompany company = new SeedCompany(root, mailbox);
        SeedCompany.Person owner = company.build();

        LOG.info("Seeding conversations only. No templates, no processes, no tasks — this is the workspace"
                + " as it stands before anything has been described.");

        new SeedNarrative(clock, company, 20260819L).runTheObservationsOnly();

        LOG.info(
                "Seeded {} people under {} and the conversations between them. History runs to {}.",
                company.everybody().size(),
                owner.displayName(),
                clock.instant());
    }

    private boolean holdsWork() {
        Integer users = jdbc.queryForObject("select count(*) from auth_user", Integer.class);
        return users != null && users > 0;
    }

    private void restoreTheClockToWhereTheHistoryEnded() {
        Instant lastEvent = jdbc.queryForObject("select max(occurred_at) from task_event", Instant.class);
        if (lastEvent == null) {
            return;
        }
        clock.advanceTo(lastEvent);
        LOG.info("Demonstration clock restored to {}, where the seeded history ends.", lastEvent);
    }

    private String mailCatcherRoot() {
        return "http://localhost:8025";
    }
}
