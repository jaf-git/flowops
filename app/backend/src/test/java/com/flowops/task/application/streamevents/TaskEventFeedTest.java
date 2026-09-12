package com.flowops.task.application.streamevents;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.support.ApplicationTest;
import com.flowops.task.application.shared.port.AppendTaskEventPort;
import com.flowops.task.application.streamevents.TaskEventFeedUseCase.TaskEventRecord;
import com.flowops.task.domain.event.TaskEvent;
import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.TaskId;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@Tag("CANVAS-VIEW-PROCESS-01")
class TaskEventFeedTest extends ApplicationTest {
    @Autowired
    private TaskEventFeedUseCase feed;

    @Autowired
    private AppendTaskEventPort append;

    @Autowired
    private JdbcTemplate jdbc;

    private TaskId task;
    private PersonId actor;

    @BeforeEach
    void seedSomethingForTheLogToPointAt() {
        jdbc.update("delete from task_event");

        UUID person = UUID.randomUUID();
        jdbc.update(
                "insert into auth_user (id, email, account_state, role_name, created_at, setup_completed)"
                        + " values (?, ?, 'ACTIVE', 'OWNER', now(), true)",
                person,
                "feed-" + person + "@atelier.ro");

        List<UUID> installed = jdbc.queryForList("select id from workspace limit 1", UUID.class);
        UUID workspace = installed.isEmpty() ? installWorkspace() : installed.get(0);

        UUID taskId = UUID.randomUUID();
        jdbc.update(
                "insert into task (id, workspace_id, title, creator_user_id, priority, state, created_at)"
                        + " values (?, ?, 'Comandă furnizor', ?, 'NORMAL', 'Created', now())",
                taskId,
                workspace,
                person);

        task = TaskId.of(taskId);
        actor = PersonId.of(person);
    }

    @AfterEach
    void takeBackWhatWasSeeded() {
        jdbc.update("delete from task_event where task_id = ?", task.value());
        jdbc.update("delete from task where id = ?", task.value());
        jdbc.update("delete from auth_user where id = ?", actor.value());
    }

    private UUID installWorkspace() {
        UUID workspace = UUID.randomUUID();
        jdbc.update("insert into workspace (id, name, created_at) values (?, 'Atelier Radu', now())", workspace);
        return workspace;
    }

    @Test
    void anEmptyLogHasACursorOfZero() {
        assertThat(feed.currentCursor()).isZero();
        assertThat(feed.since(0, 100)).isEmpty();
    }

    @Test
    void everyEventTakesTheNextPositionInTheStream() {
        appendThree();

        List<TaskEventRecord> everything = feed.since(0, 100);

        assertThat(everything).hasSize(3);
        assertThat(everything)
                .extracting(TaskEventRecord::action)
                .containsExactly("TASK_CREATED", "TASK_ACCEPTED", "TASK_STARTED");
        assertThat(everything).extracting(TaskEventRecord::sequence).isSorted();
        assertThat(everything.stream().map(TaskEventRecord::sequence).distinct().count())
                .as("a repeated cursor would replay one event and silently skip another")
                .isEqualTo(3);
        assertThat(feed.currentCursor()).isEqualTo(everything.get(2).sequence());
    }

    @Test
    void aCursorReplaysWhatCameAfterItAndNothingElse() {
        appendThree();
        List<TaskEventRecord> everything = feed.since(0, 100);
        long afterTheFirst = everything.get(0).sequence();

        List<TaskEventRecord> missed = feed.since(afterTheFirst, 100);

        assertThat(missed).extracting(TaskEventRecord::action).containsExactly("TASK_ACCEPTED", "TASK_STARTED");
    }

    @Test
    void theReplayIsBoundedByWhatWasAskedFor() {
        appendThree();

        assertThat(feed.since(0, 2)).hasSize(2);
        assertThat(feed.since(0, 0)).isEmpty();
    }

    @Test
    void twoEventsSharingATimestampStillHaveAnOrder() {
        Instant sameMoment = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        append.append(new TaskEvent(
                UUID.randomUUID(), task, com.flowops.task.domain.enums.TaskAction.TASK_CREATED, actor, sameMoment));
        append.append(new TaskEvent(
                UUID.randomUUID(), task, com.flowops.task.domain.enums.TaskAction.TASK_ACCEPTED, actor, sameMoment));

        List<TaskEventRecord> both = feed.since(0, 100);

        assertThat(both).hasSize(2);
        assertThat(both.get(0).sequence()).isLessThan(both.get(1).sequence());
        assertThat(both).extracting(TaskEventRecord::at).containsExactly(sameMoment, sameMoment);
    }

    private void appendThree() {
        Instant now = Instant.now();
        append.append(TaskEvent.created(task, actor, now));
        append.append(TaskEvent.accepted(task, actor, now.plusSeconds(1)));
        append.append(new TaskEvent(
                UUID.randomUUID(),
                task,
                com.flowops.task.domain.enums.TaskAction.TASK_STARTED,
                actor,
                now.plusSeconds(2)));
    }
}
