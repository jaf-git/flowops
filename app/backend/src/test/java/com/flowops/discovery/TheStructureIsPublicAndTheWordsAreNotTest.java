package com.flowops.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.discovery.application.collaboration.SeeWhoWorkedTogether;
import com.flowops.discovery.application.publicgraph.ViewPublicGraph;
import com.flowops.discovery.application.shared.port.CollaborationReadPort;
import com.flowops.discovery.application.shared.port.PublicGraphPort;
import com.flowops.discovery.application.shared.port.WorkGraphPort;
import com.flowops.discovery.application.vocabulary.WatchTheVocabulary;
import com.flowops.discovery.domain.enums.EvidenceOrigin;
import com.flowops.discovery.domain.enums.OutputKind;
import com.flowops.discovery.domain.model.CollaborationTier;
import com.flowops.discovery.domain.model.JobId;
import com.flowops.discovery.domain.model.WorkNodeId;
import com.flowops.support.ApplicationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class TheStructureIsPublicAndTheWordsAreNotTest extends ApplicationTest {
    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ViewPublicGraph graph;

    @Autowired
    private WorkGraphPort workGraph;

    @Autowired
    private SeeWhoWorkedTogether collaboration;

    @Autowired
    private WatchTheVocabulary vocabulary;

    private static final Instant NOW = Instant.parse("2026-08-26T09:00:00Z");

    private UUID sara;
    private UUID karim;
    private UUID outsider;
    private JobId job;
    private UUID privateChat;

    @BeforeEach
    void aPrivateConversationAndSomebodyOutsideIt() {
        sara = person("sara");
        karim = person("karim");
        outsider = person("hala");
        job = JobId.of(job(sara));

        privateChat = conversationBetween(sara, karim);
    }

    private UUID conversationBetween(UUID one, UUID other) {
        jdbc.update(
                """
                insert into workspace (id, name, workspace_use, singleton, created_at)
                values (?, 'Atelier', 'AGENCY', true, ?)
                on conflict (singleton) do nothing
                """,
                UUID.randomUUID(),
                Timestamp.from(NOW));

        UUID workspace = jdbc.queryForObject("select id from workspace limit 1", UUID.class);

        boolean oneIsLower = one.toString().compareTo(other.toString()) < 0;
        UUID lo = oneIsLower ? one : other;
        UUID hi = oneIsLower ? other : one;

        UUID conversation = UUID.randomUUID();
        jdbc.update(
                """
                insert into conversation (id, workspace_id, kind, created_at, participant_lo, participant_hi)
                values (?, ?, 'DIRECT', ?, ?, ?)
                """,
                conversation,
                workspace,
                Timestamp.from(NOW),
                lo,
                hi);

        jdbc.update(
                "insert into conversation_participant (conversation_id, person_id) values (?, ?), (?, ?)",
                conversation,
                one,
                conversation,
                other);

        return conversation;
    }

    @Test
    @DisplayName("R13.1 withdrawn 2026-09-01 — a node carries its words, to every member")
    void thePublicGraphCarriesTheWords() {
        String said = "Dana is furious about the price, do not put this in writing";
        bracketFrom(message(), "DESIGN", karim, said);

        List<PublicGraphPort.PublicNode> shape = graph.shapeOf(job);

        assertThat(shape).hasSize(1);
        assertThat(shape.get(0).workType()).isEqualTo("DESIGN");
        assertThat(shape.get(0).performerName())
                .describedAs("R13.2 - attribution is a fact about a piece of work and is permitted")
                .isNotBlank();

        assertThat(shape.get(0).text())
                .describedAs("the owner withdrew R13.1 on 2026-09-01 so the graph could be read as a "
                        + "process; a reader outside this conversation now sees what was said in it")
                .isEqualTo(said);
    }

    @Test
    @DisplayName("R7.10 — no edge crosses a job, even when a row in the table says otherwise")
    void noEdgeCrossesAJob() {
        UUID here = bracketFrom(message(), "DESIGN", karim);
        JobId elsewhere = JobId.of(job(sara));
        UUID there = bracketIn(elsewhere, "PHOTO", sara);

        waitOf(here, there);

        assertThat(graph.graphOf(job).edges())
                .describedAs("a wait whose target lives in another engagement is not an edge in this one")
                .isEmpty();
        assertThat(graph.graphOf(elsewhere).edges())
                .describedAs("nor in the other one, read from the far side")
                .isEmpty();
    }

    @Test
    @DisplayName("R7 — a wait inside one job is an edge, and the edge carries none of its reason")
    void aWaitIsAnEdgeAndCarriesNoReason() {
        String reason = "Dana is furious about the price, do not put this in writing";
        UUID waiter = bracketFrom(message(), "DESIGN", karim);
        UUID awaited = bracketFrom(message(), "PHOTO", sara);

        waitOf(waiter, awaited, reason);

        List<PublicGraphPort.PublicEdge> edges = graph.graphOf(job).edges();

        assertThat(edges).hasSize(1);
        assertThat(edges.get(0).kind()).isEqualTo(PublicGraphPort.PublicEdge.EdgeKind.WAIT);
        assertThat(edges.get(0).toString())
                .describedAs("the reason is not filtered out of this edge; there is no field for it, so "
                        + "no future edit can put it back by accident")
                .doesNotContain(reason);
    }

    @Test
    @DisplayName("§9.0 — a node carries the close kind, so a scar cannot render as a completion")
    void aNodeCarriesWhatItsColourNeeds() {
        UUID handedOver = bracketFrom(message(), "VIDEO", karim);

        jdbc.update(
                "update work_bracket set state = 'CLOSED', close_kind = 'HANDED_OVER', closed_at = ? where id = ?",
                Timestamp.from(NOW),
                handedOver);

        PublicGraphPort.PublicNode node = graph.shapeOf(job).get(0);

        assertThat(node.state()).isEqualTo("CLOSED");
        assertThat(node.closeKind())
                .describedAs("R15.5 — a known outcome is evidence and an unknown one is a hole, and the "
                        + "renderer cannot tell them apart from `state`")
                .isEqualTo("HANDED_OVER");
        assertThat(node.boundary()).isFalse();
        assertThat(node.nodeRole()).isEqualTo("START");
    }

    @Test
    @DisplayName("R12.6 — search tells an outsider how many matches exist, never what they say")
    void searchCountsWhatItWillNotShow() {
        bracketFrom(message(), "DESIGN", karim, "the leaflet wording needs another pass");

        ViewPublicGraph.Found forKarim = graph.search("leaflet", karim);
        assertThat(forKarim.matches()).hasSize(1);
        assertThat(forKarim.matches().get(0).text()).contains("leaflet wording");
        assertThat(forKarim.beyondReach()).isZero();

        ViewPublicGraph.Found forOutsider = graph.search("leaflet", outsider);
        assertThat(forOutsider.matches())
                .describedAs("a sentence from a conversation this person is not in is never fetched")
                .isEmpty();
        assertThat(forOutsider.beyondReach())
                .describedAs("R12.6 - the count is not a leak: it says the product holds something, "
                        + "which the person already knows, without saying what")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("R19.2 — two people asked for one thing together collapse to one step")
    void oneMessageAndOneWorkTypeIsACollaboration() {
        UUID assignment = message();
        bracketFrom(assignment, "VIDEO", sara);
        bracketFrom(assignment, "VIDEO", karim);

        List<SeeWhoWorkedTogether.Group> groups = collaboration.inJob(job);

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).tier()).isEqualTo(CollaborationTier.GOOD);
        assertThat(groups.get(0).collapsesToOneStep()).isTrue();
    }

    @Test
    @DisplayName("R19.2 — one message with different work types is a split, not a collaboration")
    void oneMessageWithDifferentWorkTypesIsNotACollaboration() {
        UUID assignment = message();
        bracketFrom(assignment, "CONTENT", sara);
        bracketFrom(assignment, "DESIGN", karim);

        assertThat(collaboration.inJob(job))
                .describedAs("work type is what separates a split from a collaboration; five people asked "
                        + "for five different things in one sentence are not doing one thing")
                .isEmpty();
    }

    @Test
    @DisplayName("R19.1 — two brackets publishing one output are one piece of work")
    void thesameOutputIsStrongEvidence() {
        UUID one = bracketFrom(message(), "VIDEO", sara);
        UUID other = bracketFrom(message(), "VIDEO", karim);
        deliver(one, "https://drive.example/video-final");
        deliver(other, "https://drive.example/video-final");

        List<SeeWhoWorkedTogether.Group> groups = collaboration.inJob(job);

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).tier())
                .describedAs("R19.1 - the strongest tier, and reported once rather than also as the "
                        + "weaker tier it would otherwise satisfy")
                .isEqualTo(CollaborationTier.STRONG);
        assertThat(groups.get(0).evidence()).isEqualTo("https://drive.example/video-final");
    }

    @Test
    @DisplayName("R19.3 — same work, different people, nothing shared: a band, never a collapsed step")
    void sameWorkTypeAloneIsWeak() {
        bracketFrom(message(), "CONTENT", sara);
        bracketFrom(message(), "CONTENT", karim);

        List<SeeWhoWorkedTogether.Group> groups = collaboration.inJob(job);

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).tier()).isEqualTo(CollaborationTier.WEAK);
        assertThat(groups.get(0).collapsesToOneStep())
                .describedAs("two collaborators who each joined their own bracket can end up with "
                        + "different messages and outputs, so this is all that remains - and it is not enough")
                .isFalse();
    }

    @Test
    @DisplayName("R19.4 — an asset delivered into a second engagement is reported; the same value twice "
            + "inside one engagement is not")
    void anAssetThatServedTwoEngagementsIsReported() {
        String designs = "the summer menu designs · " + UUID.randomUUID();
        String brief = "the round one brief · " + UUID.randomUUID();

        JobId roundTwo = JobId.of(job(sara));
        UUID inRoundOne = bracketFrom(message(), "DESIGN", karim);
        UUID inRoundTwo = bracketIn(roundTwo, "DESIGN", karim);
        delivered(inRoundOne, OutputKind.MESSAGE_REF, designs);
        delivered(inRoundTwo, OutputKind.MESSAGE_REF, designs);

        delivered(bracketFrom(message(), "CONTENT", sara), OutputKind.MESSAGE_REF, brief);
        delivered(bracketFrom(message(), "CONTENT", karim), OutputKind.MESSAGE_REF, brief);

        assertThat(deliveredKindsOf(designs))
                .describedAs("the fixture landed before anything is concluded from an absence: this read "
                        + "answers empty for a dozen reasons, and a test over an unpopulated table passes "
                        + "in exactly the words a correct one uses")
                .containsExactly("MESSAGE_REF", "MESSAGE_REF");
        assertThat(deliveredKindsOf(brief)).containsExactly("MESSAGE_REF", "MESSAGE_REF");

        List<CollaborationReadPort.Reuse> reuse = reuseOf(designs);

        assertThat(reuse)
                .describedAs("once, not twice - the pair is ordered by identifier so a caller counting "
                        + "reused assets does not count every one of them double")
                .hasSize(1);
        assertThat(List.of(reuse.get(0).firstJob(), reuse.get(0).secondJob()))
                .containsExactlyInAnyOrder(job.value(), roundTwo.value());
        assertThat(List.of(reuse.get(0).firstBracket(), reuse.get(0).secondBracket()))
                .containsExactlyInAnyOrder(inRoundOne, inRoundTwo);

        assertThat(reuseOf(brief))
                .describedAs("R19.4 is the fact of one asset crossing between engagements; two people "
                        + "delivering one thing inside a single engagement crossed nothing")
                .isEmpty();
    }

    @Test
    @DisplayName("R19.4 — two engagements that both delivered the words \"final version\" did not share an asset")
    void freeTextThatReadsTheSameIsNotOneAsset() {
        String phrase = "final version · " + UUID.randomUUID();

        JobId otherEngagement = JobId.of(job(sara));
        delivered(bracketFrom(message(), "DESIGN", karim), OutputKind.TEXT, phrase);
        delivered(bracketIn(otherEngagement, "DESIGN", sara), OutputKind.TEXT, phrase);

        assertThat(deliveredKindsOf(phrase))
                .describedAs("both rows are present and in different engagements, so an empty answer "
                        + "below is the rule and not an empty table")
                .containsExactly("TEXT", "TEXT");

        assertThat(reuseOf(phrase)).isEmpty();
    }

    @Test
    @DisplayName("R19.4 — a link is not compared against a message reference, whichever side of the join it is on")
    void thetwoSidesOfAReusePairCarryTheSameKind() {
        String value = "https://drive.example/menu-designs · " + UUID.randomUUID();

        JobId otherEngagement = JobId.of(job(sara));
        UUID here = bracketFrom(message(), "DESIGN", karim);
        UUID there = bracketIn(otherEngagement, "DESIGN", sara);

        boolean hereSortsFirst = here.toString().compareTo(there.toString()) < 0;
        delivered(hereSortsFirst ? here : there, OutputKind.LINK, value);
        delivered(hereSortsFirst ? there : here, OutputKind.MESSAGE_REF, value);

        assertThat(deliveredKindsOf(value))
                .describedAs("two engagements, one value, and the only thing separating them is the kind")
                .containsExactlyInAnyOrder("LINK", "MESSAGE_REF");

        assertThat(reuseOf(value)).isEmpty();
    }

    @Test
    @DisplayName("R20.1 — the chip says a word is new, and offers the ones already in use")
    void anUnusedWordIsFlaggedAndNeverBlocked() {
        bracketFrom(message(), "RESEARCH", sara);

        assertThat(vocabulary.aboutToUse("RESEARCH").neverUsedBefore())
                .describedAs("it is in use now, so it is no longer news")
                .isFalse();

        var novel = vocabulary.aboutToUse("RESEARCH_BRIEF");
        assertThat(novel.neverUsedBefore()).isTrue();
        assertThat(novel.closestExisting())
                .describedAs("somebody reaching for a word they know and adding to it is how a taxonomy "
                        + "splits; the neighbour is offered while they are still typing")
                .contains("RESEARCH");
    }

    private void deliver(UUID bracket, String output) {
        delivered(bracket, OutputKind.LINK, output);
    }

    private void delivered(UUID bracket, OutputKind kind, String output) {
        jdbc.update(
                "update work_bracket set state = 'CLOSED', close_kind = 'DELIVERED', output_kind = ?, "
                        + "output_value = ?, closed_at = ? where id = ?",
                kind.name(),
                output,
                Timestamp.from(NOW),
                bracket);
    }

    private List<String> deliveredKindsOf(String output) {
        return jdbc.queryForList(
                "select output_kind from work_bracket where output_value = ? and close_kind = 'DELIVERED'",
                String.class,
                output);
    }

    private List<CollaborationReadPort.Reuse> reuseOf(String output) {
        return collaboration.assetsThatCameForFree().stream()
                .filter(row -> output.equals(row.value()))
                .toList();
    }

    private UUID bracketFrom(UUID messageId, String workType, UUID performer) {
        return bracketFrom(messageId, workType, performer, "a sentence somebody said");
    }

    private UUID bracketFrom(UUID messageId, String workType, UUID performer, String text) {
        UUID node = node(privateChat, text, performer, workType);
        workGraph.recordEvidence(
                workGraph.findNode(WorkNodeId.of(node)).orElseThrow(), messageId, EvidenceOrigin.PRIMARY);

        UUID bracket = UUID.randomUUID();
        jdbc.update(
                """
                insert into work_bracket (id, job_id, conversation_id, work_type, performer_ref,
                    opened_by_node, closure_right, state, is_boundary, opened_at, last_activity_at)
                values (?, ?, ?, ?, ?, ?, ?, 'OPEN', false, ?, ?)
                """,
                bracket,
                job.value(),
                privateChat,
                workType,
                performer,
                node,
                performer,
                Timestamp.from(NOW),
                Timestamp.from(NOW));

        jdbc.update("update work_node set bracket_id = ?, node_role = 'START' where id = ?", bracket, node);
        return bracket;
    }

    private UUID bracketIn(JobId which, String workType, UUID performer) {
        UUID node = node(privateChat, "work in another engagement", performer, workType);
        jdbc.update("update work_node set job_id = ? where id = ?", which.value(), node);

        UUID bracket = UUID.randomUUID();
        jdbc.update(
                """
                insert into work_bracket (id, job_id, conversation_id, work_type, performer_ref,
                    opened_by_node, closure_right, state, is_boundary, opened_at, last_activity_at)
                values (?, ?, ?, ?, ?, ?, ?, 'OPEN', false, ?, ?)
                """,
                bracket,
                which.value(),
                privateChat,
                workType,
                performer,
                node,
                performer,
                Timestamp.from(NOW),
                Timestamp.from(NOW));

        jdbc.update("update work_node set bracket_id = ?, node_role = 'START' where id = ?", bracket, node);
        return bracket;
    }

    private void waitOf(UUID waiter, UUID awaited) {
        waitOf(waiter, awaited, "waiting on the shots");
    }

    private void waitOf(UUID waiter, UUID awaited, String reason) {
        jdbc.update(
                """
                insert into work_node_wait (id, bracket_id, kind, on_bracket_id, reason, opened_at)
                values (?, ?, 'COLLEAGUE', ?, ?, ?)
                """,
                UUID.randomUUID(),
                waiter,
                awaited,
                reason,
                Timestamp.from(NOW));
    }

    private UUID node(UUID conversation, String text, UUID performer, String workType) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                insert into work_node (id, job_id, text, creator_id, performer_id, created_at,
                    state, direction, kind, conversation_id, work_type)
                values (?, ?, ?, ?, ?, ?, 'MARKED', 'STANDALONE', 'WORK', ?, ?)
                """,
                id,
                job.value(),
                text,
                performer,
                performer,
                Timestamp.from(NOW),
                conversation,
                workType);

        return id;
    }

    private UUID message() {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                insert into message (id, conversation_id, author_id, body, sent_at, seq, kind)
                values (?, ?, ?, 'Sara and Karim, do the video together', ?, 0, 'SPOKEN')
                """,
                id,
                privateChat,
                sara,
                Timestamp.from(NOW));
        return id;
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
