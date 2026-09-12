package com.flowops.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowops.discovery.application.claimbracket.ClaimBracket;
import com.flowops.discovery.application.claimbracket.WorkIsAlreadySomebodysException;
import com.flowops.discovery.application.declarewait.DeclareWait;
import com.flowops.discovery.application.markintobracket.PlaceMarkInBracket;
import com.flowops.discovery.application.shared.port.WorkBracketPort;
import com.flowops.discovery.domain.enums.CloseKind;
import com.flowops.discovery.domain.enums.WaitKind;
import com.flowops.discovery.domain.model.BracketAddress;
import com.flowops.discovery.domain.model.JobId;
import com.flowops.discovery.domain.model.WorkBracket;
import com.flowops.discovery.domain.model.WorkNodeId;
import com.flowops.support.ApplicationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class WorkNobodyHasTakenOnYetTest extends ApplicationTest {
    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlaceMarkInBracket marking;

    @Autowired
    private ClaimBracket claiming;

    @Autowired
    private DeclareWait waiting;

    @Autowired
    private WorkBracketPort brackets;

    private static final Instant NOW = Instant.parse("2026-08-26T09:00:00Z");

    private UUID lena;
    private UUID tariq;
    private JobId job;
    private UUID conversation;

    @BeforeEach
    void aGroupChat() {
        lena = person("lena");
        tariq = person("tariq");
        conversation = UUID.randomUUID();
        job = JobId.of(job(lena));
    }

    @Test
    @DisplayName("R2.1 — taking work on carries the obligation to finish it")
    void claimingMovesTheClosureRight() {
        WorkBracket unclaimed = openUnclaimed();
        assertThat(unclaimed.closureRight()).isEqualTo(lena);

        ClaimBracket.Claimed claimed = claiming.claim(unclaimed.id(), tariq);

        assertThat(claimed.wasMerged()).isFalse();
        assertThat(claimed.bracket().address().performerId()).isEqualTo(tariq);
        assertThat(claimed.bracket().closureRight())
                .describedAs("R2.1 - taking work on is the opt-in that carries the obligation")
                .isEqualTo(tariq);
    }

    @Test
    @DisplayName("R2.1 — a duplicate folds into the work the claimer already had open, and waiters move with it")
    void claimingWorkYouAlreadyHaveMergesIt() {
        WorkBracket tariqsOwn = marking.place(
                        job, new BracketAddress(conversation, null, null, "PHOTO", tariq), node(), tariq)
                .bracket();

        WorkBracket unclaimed = openUnclaimed();

        WorkBracket karimsDesign = marking.place(
                        job, new BracketAddress(conversation, null, null, "DESIGN", person("karim")), node(), lena)
                .bracket();
        var wait = waiting.declare(
                karimsDesign.id(),
                WaitKind.COLLEAGUE,
                unclaimed.id(),
                "Cannot design until the photos are picked",
                null);

        ClaimBracket.Claimed claimed = claiming.claim(unclaimed.id(), tariq);

        assertThat(claimed.wasMerged()).isTrue();
        assertThat(claimed.bracket().id())
                .describedAs("the work now lives in the bracket Tariq already had open")
                .isEqualTo(tariqsOwn.id());

        WorkBracket folded = brackets.find(unclaimed.id()).orElseThrow();
        assertThat(folded.closeKind()).contains(CloseKind.MERGED);

        assertThat(jdbc.queryForObject(
                        "select satisfied_at from work_node_wait where id = ?", Timestamp.class, wait.id()))
                .describedAs("a merge is not a completion; nobody may be told work arrived that nobody did")
                .isNull();

        assertThat(jdbc.queryForObject(
                        "select count(*) from work_node_wait where on_bracket_id = ? "
                                + "and satisfied_at is null and cancelled_at is null",
                        Integer.class,
                        tariqsOwn.id().value()))
                .describedAs("R7.7 - the wait moved onto the work that carries on, rather than dying")
                .isEqualTo(1);

        assertThat(jdbc.queryForObject(
                        "select count(*) from work_node where bracket_id = ? and node_role = 'START'",
                        Integer.class,
                        tariqsOwn.id().value()))
                .describedAs("one START per bracket - the merged one's becomes ordinary work, or the "
                        + "bracket's opening instant is ambiguous and every duration reads from a guess")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("R2.1 — somebody else's work is a handover, never a claim")
    void claimingSomebodyElsesWorkIsRefused() {
        WorkBracket tariqs = marking.place(
                        job, new BracketAddress(conversation, null, null, "PHOTO", tariq), node(), tariq)
                .bracket();

        assertThatThrownBy(() -> claiming.claim(tariqs.id(), lena))
                .isInstanceOf(WorkIsAlreadySomebodysException.class)
                .hasMessageContaining("handover");
    }

    private WorkBracket openUnclaimed() {
        return marking.place(job, new BracketAddress(conversation, null, null, "PHOTO", null), node(), lena)
                .bracket();
    }

    private WorkNodeId node() {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into work_node (id, job_id, text, creator_id, created_at, state, direction, kind) "
                        + "values (?, ?, 'the Sunrise photos still need picking', ?, ?, 'MARKED', 'STANDALONE', 'WORK')",
                id,
                job.value(),
                lena,
                Timestamp.from(NOW));
        return WorkNodeId.of(id);
    }

    private UUID person(String name) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into auth_user (id, email, account_state, role_name, created_at) "
                        + "values (?, ?, 'ACTIVE', 'EMPLOYEE', ?)",
                id,
                name + "-" + id + "@atelier.ro",
                Timestamp.from(NOW));
        return id;
    }

    private UUID job(UUID openedBy) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into job (id, name, status, standing, opened_at, opened_by, last_activity_at) "
                        + "values (?, 'Sunrise Bakery · summer menu', 'OPEN', false, ?, ?, ?)",
                id,
                Timestamp.from(NOW),
                openedBy,
                Timestamp.from(NOW));
        return id;
    }
}
