package com.flowops.aiexport.seed;

import com.flowops.discovery.application.closebracket.CloseBracket;
import com.flowops.discovery.application.declarewait.DeclareWait;
import com.flowops.discovery.application.markintobracket.ComposeAddress;
import com.flowops.discovery.application.markintobracket.PlaceMarkInBracket;
import com.flowops.discovery.application.shared.port.WorkBracketPort;
import com.flowops.discovery.domain.enums.OutputKind;
import com.flowops.discovery.domain.enums.WaitKind;
import com.flowops.discovery.domain.model.BracketAddress;
import com.flowops.discovery.domain.model.JobId;
import com.flowops.discovery.domain.model.MarkOutcome;
import com.flowops.discovery.domain.model.WorkNodeId;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@org.springframework.context.annotation.Profile("demo")
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.LOWEST_PRECEDENCE)
public class SeedWorkBrackets {
    private static final Logger LOG = LoggerFactory.getLogger(SeedWorkBrackets.class);

    private final PlaceMarkInBracket marking;
    private final ComposeAddress addresses;
    private final DeclareWait waiting;
    private final CloseBracket closing;
    private final WorkBracketPort brackets;
    private final SeedEngagements engagements;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public SeedWorkBrackets(
            PlaceMarkInBracket marking,
            ComposeAddress addresses,
            DeclareWait waiting,
            CloseBracket closing,
            WorkBracketPort brackets,
            SeedEngagements engagements,
            JdbcTemplate jdbc,
            Clock clock) {
        this.marking = marking;
        this.addresses = addresses;
        this.waiting = waiting;
        this.closing = closing;
        this.brackets = brackets;
        this.engagements = engagements;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public String walk() {
        Integer already =
                jdbc.queryForObject("select count(*) from work_bracket where is_boundary = false", Integer.class);
        if (already != null && already > 0) {
            return "work_bracket already holds " + already + " pieces of work; nothing seeded";
        }

        People people = readPeople();
        if (people.incomplete()) {
            return "the workspace is not seeded yet; no brackets written";
        }

        JobId job = firstJob();

        UUID conversation = firstConversationOf(people.owner());

        if (job == null || conversation == null) {
            return "no job or conversation to hang brackets from; no brackets written";
        }

        List<WorkNodeId> nodes = readNodes(job, 7);
        if (nodes.size() < 7) {
            return "only " + nodes.size() + " work nodes exist; the walk needs seven";
        }

        BracketAddress photos = addresses.composeFor(job, conversation, people.andrei());
        MarkOutcome first = marking.place(job, photos, nodes.get(0), people.ionut());

        MarkOutcome second = marking.place(job, photos, nodes.get(1), people.ionut());

        if (!(second instanceof MarkOutcome.Joined)) {
            throw new IllegalStateException(
                    "R1 is broken: a second mark at an identical address opened a new bracket instead of joining it");
        }

        BracketAddress copy = addresses.composeFor(job, conversation, people.ioana());
        MarkOutcome third = marking.place(job, copy, nodes.get(2), people.ionut());

        waiting.declare(
                third.bracket().id(),
                WaitKind.COLLEAGUE,
                first.bracket().id(),
                "Cannot write the captions until the photos are picked",
                null);

        closing.delivered(
                first.bracket().id(),
                OutputKind.LINK,
                "https://drive.example/aurora-summer-menu",
                first.bracket().closureRight());

        BracketAddress ads = addresses.composeFor(job, conversation, people.cosmin());
        MarkOutcome fourth = marking.place(job, ads, nodes.get(4), people.ionut());
        closing.dropped(
                fourth.bracket().id(),
                "Client postponed the campaign",
                fourth.bracket().closureRight());

        BracketAddress reporting = addresses.composeFor(job, conversation, people.daria());
        marking.place(job, reporting, nodes.get(6), people.ionut());

        String campaign = engagements.walk();

        int live = brackets.findLiveIn(job).size();
        Integer total = jdbc.queryForObject("select count(*) from work_bracket", Integer.class);
        Integer waits = jdbc.queryForObject("select count(*) from work_node_wait", Integer.class);

        String summary = "brackets=" + total + " live=" + live + " waits=" + waits + " (second mark joined: "
                + (second instanceof MarkOutcome.Joined) + "); " + campaign;

        LOG.info("discovery bracket seed: {}", summary);
        return summary;
    }

    private People readPeople() {
        Map<String, UUID> byEmail = new java.util.HashMap<>();
        jdbc.query("select email, id from auth_user", rs -> {
            byEmail.put(rs.getString("email"), rs.getObject("id", UUID.class));
        });

        return new People(
                byEmail.get("maria@atelier.ro"),
                byEmail.get("ionut@atelier.ro"),
                byEmail.get("andrei@atelier.ro"),
                byEmail.get("ioana@atelier.ro"),
                byEmail.get("cosmin@atelier.ro"),
                byEmail.get("daria@atelier.ro"));
    }

    private JobId firstJob() {
        return jdbc
                .query(
                        "select id from job order by opened_at limit 1",
                        (rs, row) -> JobId.of(rs.getObject("id", UUID.class)))
                .stream()
                .findFirst()
                .orElse(null);
    }

    private UUID firstConversationOf(UUID person) {
        return jdbc
                .query(
                        "select conversation_id from conversation_participant where person_id = ? limit 1",
                        (rs, row) -> rs.getObject("conversation_id", UUID.class),
                        person)
                .stream()
                .findFirst()
                .orElse(null);
    }

    private List<WorkNodeId> readNodes(JobId job, int howMany) {
        return jdbc.query(
                "select id from work_node where job_id = ? and bracket_id is null order by created_at limit ?",
                (rs, row) -> new WorkNodeId(rs.getObject("id", UUID.class)),
                job.value(),
                howMany);
    }

    private record People(UUID owner, UUID ionut, UUID andrei, UUID ioana, UUID cosmin, UUID daria) {
        boolean incomplete() {
            return owner == null || ionut == null || andrei == null || ioana == null || cosmin == null || daria == null;
        }
    }
}
