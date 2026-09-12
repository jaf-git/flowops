package com.flowops.aiexport.seed;

import com.flowops.auth.infrastructure.session.SessionPrincipal;
import com.flowops.discovery.application.closebracket.CloseBracket;
import com.flowops.discovery.application.closejob.CloseJob;
import com.flowops.discovery.application.declarewait.DeclareWait;
import com.flowops.discovery.application.markintobracket.ComposeAddress;
import com.flowops.discovery.application.markintobracket.PlaceMarkInBracket;
import com.flowops.discovery.application.openjob.OpenJobUseCase;
import com.flowops.discovery.application.shared.WorkCapture;
import com.flowops.discovery.application.shared.port.WorkBracketPort;
import com.flowops.discovery.domain.enums.Direction;
import com.flowops.discovery.domain.enums.NodeKind;
import com.flowops.discovery.domain.enums.OutputKind;
import com.flowops.discovery.domain.enums.WaitKind;
import com.flowops.discovery.domain.model.BracketAddress;
import com.flowops.discovery.domain.model.BracketId;
import com.flowops.discovery.domain.model.JobId;
import com.flowops.discovery.domain.model.MarkOutcome;
import com.flowops.discovery.domain.model.WorkNodeId;
import com.flowops.discovery.domain.model.WorkNodeWait;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("demo")
@Order(Ordered.LOWEST_PRECEDENCE)
public class SeedRepeatedWork {
    private static final Logger LOG = LoggerFactory.getLogger(SeedRepeatedWork.class);

    private static final int JOBS = 6;

    private static final int JOBS_THAT_WAIT = 4;

    private static final int DAYS_PER_JOB = 4;

    private static final String OWNER_EMAIL = "maria@atelier.ro";

    private final OpenJobUseCase openJobs;
    private final PlaceMarkInBracket marking;
    private final ComposeAddress addresses;
    private final DeclareWait waiting;
    private final CloseBracket closing;
    private final CloseJob closingJobs;
    private final WorkCapture capture;
    private final WorkBracketPort brackets;
    private final JdbcTemplate jdbc;
    private final MutableClock clock;

    private Instant windowStart;

    public SeedRepeatedWork(
            OpenJobUseCase openJobs,
            PlaceMarkInBracket marking,
            ComposeAddress addresses,
            DeclareWait waiting,
            CloseBracket closing,
            CloseJob closingJobs,
            WorkCapture capture,
            WorkBracketPort brackets,
            JdbcTemplate jdbc,
            MutableClock clock) {
        this.openJobs = openJobs;
        this.marking = marking;
        this.addresses = addresses;
        this.waiting = waiting;
        this.closing = closing;
        this.closingJobs = closingJobs;
        this.capture = capture;
        this.brackets = brackets;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public String walk() {
        Integer already = jdbc.queryForObject("select count(*) from job where name like 'Repeat %'", Integer.class);

        if (already != null && already > 0) {
            return already + " repeat jobs already exist; nothing seeded";
        }

        Map<String, UUID> people = people();
        UUID owner = people.get(OWNER_EMAIL);
        UUID intake = people.get("ionut@atelier.ro");
        UUID writer = people.get("andrei@atelier.ro");
        UUID ads = people.get("cosmin@atelier.ro");

        if (owner == null || intake == null || writer == null || ads == null) {
            return "the workspace is not seeded yet; nothing written";
        }

        UUID intakeChat = directChat(owner, intake);
        UUID writerChat = directChat(owner, writer);
        UUID adsChat = directChat(owner, ads);

        SeedEngagements.Messages messages = new SeedEngagements.Messages(jdbc);

        Instant restoreTo = clock.instant();
        windowStart = restoreTo.minus(Duration.ofDays((long) JOBS * DAYS_PER_JOB + 2));

        int opened = 0;
        int waits = 0;
        int closed = 0;

        try {
            for (int i = 0; i < JOBS; i++) {
                int base = i * DAYS_PER_JOB;

                at(base, 9);
                JobId job = openJob(owner, intakeChat, messages, "Repeat " + (i + 1));

                at(base, 10);
                MarkOutcome first = mark(job, intakeChat, owner, intake, messages);
                MarkOutcome second = mark(job, writerChat, owner, writer, messages);
                MarkOutcome third = mark(job, adsChat, owner, ads, messages);
                opened += 3;

                WorkNodeWait clientWait = null;
                if (i < JOBS_THAT_WAIT) {
                    at(base, 11);
                    clientWait = waiting.declare(
                            third.bracket().id(),
                            WaitKind.CLIENT,
                            null,
                            "Waiting on the client to approve the copy before it goes live",
                            null);
                    waits++;
                }

                at(base + 1, 15);
                deliver(first.bracket().id(), OutputKind.TEXT, "Brief agreed with the client");

                at(base + 2, 16);
                deliver(second.bracket().id(), OutputKind.LINK, "https://drive.example/repeat-" + (i + 1));

                if (clientWait != null) {
                    at(base + 3, 9);
                    waiting.withdraw(clientWait);
                }

                at(base + 3, 11);
                deliver(third.bracket().id(), OutputKind.LINK, "https://ads.example/repeat-" + (i + 1) + "/live");

                at(base + 3, 17);
                CloseJob.Readiness readiness = closingJobs.reconsider(job);
                if (!readiness.ready()) {
                    throw new SeedFailedException("Repeat " + (i + 1) + " still holds " + readiness.liveWork()
                            + " live brackets, so it cannot close and the shape it exists to repeat is never learned");
                }

                CloseJob.Ended ended = closingJobs.close(job, owner);
                if (!ended.shapeEligible()) {
                    throw new SeedFailedException("Repeat " + (i + 1)
                            + " closed and is not shape-eligible, so the six jobs written to clear the shape "
                            + "detector's floor of three clear nothing");
                }
                closed++;
            }
        } finally {
            clock.set(restoreTo);
            SecurityContextHolder.clearContext();
        }

        String summary = "jobs=" + JOBS + " closed=" + closed + " brackets=" + opened + " waits=" + waits;
        LOG.info("repeated work seed: {}", summary);
        return summary;
    }

    private JobId openJob(UUID owner, UUID conversation, SeedEngagements.Messages messages, String name) {
        UUID message = messages.next(conversation);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                new UsernamePasswordAuthenticationToken(new SessionPrincipal(owner, OWNER_EMAIL), null, List.of()));
        SecurityContextHolder.setContext(context);
        try {
            return openJobs.execute(new OpenJobUseCase.OpenJob(message, name, name, null, null, true))
                    .job();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private MarkOutcome mark(
            JobId job, UUID conversation, UUID marker, UUID performer, SeedEngagements.Messages messages) {
        UUID message = messages.next(conversation);
        WorkNodeId node = capture.capture(message, marker, job, Direction.REQUEST, NodeKind.WORK, performer)
                .node()
                .id();

        BracketAddress address = addresses.composeFor(job, conversation, performer);
        return marking.place(job, address, node, marker);
    }

    private void deliver(BracketId id, OutputKind kind, String value) {
        UUID holder = brackets.find(id).orElseThrow().closureRight();
        closing.delivered(id, kind, value, holder);
    }

    private void at(int day, int hour) {
        clock.set(windowStart.plus(Duration.ofDays(day)).plus(Duration.ofHours(hour)));
    }

    private Map<String, UUID> people() {
        Map<String, UUID> byEmail = new HashMap<>();
        jdbc.query("select email, id from auth_user", rs -> {
            byEmail.put(rs.getString("email"), rs.getObject("id", UUID.class));
        });
        return byEmail;
    }

    private UUID directChat(UUID one, UUID other) {
        return jdbc
                .query(
                        """
                        select p.conversation_id
                        from conversation_participant p
                        join conversation_participant q
                          on q.conversation_id = p.conversation_id and q.person_id = ?
                        where p.person_id = ?
                          and (select count(*) from conversation_participant c
                               where c.conversation_id = p.conversation_id) = 2
                        limit 1
                        """,
                        (rs, row) -> rs.getObject("conversation_id", UUID.class),
                        one,
                        other)
                .stream()
                .findFirst()
                .orElseThrow(() -> new SeedFailedException("there is no direct conversation between " + one + " and "
                        + other + ", so the chat half of the bracket address cannot be produced"));
    }
}
