package com.flowops.task.application.streamevents;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.support.ApplicationTest;
import com.flowops.task.application.shared.port.AppendTaskEventPort;
import com.flowops.task.application.streamevents.TaskEventFeedUseCase.TaskEventRecord;
import com.flowops.task.domain.event.TaskEvent;
import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.TaskId;
import jakarta.persistence.EntityManagerFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("CANVAS-VIEW-PROCESS-01")
class TaskEventCommitOrderTest extends ApplicationTest {
    private static final Duration LONG_ENOUGH = Duration.ofSeconds(5);

    @Autowired
    private TaskEventFeedUseCase feed;

    @Autowired
    private AppendTaskEventPort append;

    @Autowired
    private PlatformTransactionManager transactions;

    @Autowired
    private EntityManagerFactory entityManagers;

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
                "order-" + person + "@atelier.ro");

        List<UUID> installed = jdbc.queryForList("select id from workspace limit 1", UUID.class);
        UUID workspace = installed.isEmpty() ? installWorkspace() : installed.get(0);

        UUID taskId = UUID.randomUUID();
        jdbc.update(
                "insert into task (id, workspace_id, title, creator_user_id, priority, state, created_at)"
                        + " values (?, ?, 'Livrare comandă', ?, 'NORMAL', 'Created', now())",
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
    void aSecondWriterCannotTakeANumberWhileTheFirstIsStillInFlight() throws Exception {
        CountDownLatch mariaHasHerNumber = new CountDownLatch(1);
        CountDownLatch mariaMayCommit = new CountDownLatch(1);
        CountDownLatch raduHasHisNumber = new CountDownLatch(1);

        Thread maria = writerHolding(mariaHasHerNumber, mariaMayCommit, TaskEvent.created(task, actor, Instant.now()));
        Thread radu = new Thread(() -> {
            awaitQuietly(mariaHasHerNumber, LONG_ENOUGH);
            inTransaction(() -> {
                append.append(TaskEvent.accepted(task, actor, Instant.now()));
                flush();
                raduHasHisNumber.countDown();
            });
        });

        maria.start();
        radu.start();

        assertThat(mariaHasHerNumber.await(LONG_ENOUGH.toMillis(), TimeUnit.MILLISECONDS))
                .as("the fixture never got started")
                .isTrue();

        boolean raduTookOneWhileMariaWasInFlight =
                raduHasHisNumber.await(LONG_ENOUGH.toMillis(), TimeUnit.MILLISECONDS);

        mariaMayCommit.countDown();
        maria.join();
        radu.join();

        assertThat(raduTookOneWhileMariaWasInFlight)
                .as("a number taken while an earlier one is uncommitted can become visible first, "
                        + "and the earlier event is then behind every cursor for ever")
                .isFalse();
    }

    @Test
    void anEventNumberedEarlierButCommittedLaterIsStillReachable() throws Exception {
        CountDownLatch mariaHasHerNumber = new CountDownLatch(1);
        CountDownLatch mariaMayCommit = new CountDownLatch(1);
        CountDownLatch raduHasCommitted = new CountDownLatch(1);

        Thread maria = writerHolding(mariaHasHerNumber, mariaMayCommit, TaskEvent.created(task, actor, Instant.now()));
        Thread radu = new Thread(() -> {
            awaitQuietly(mariaHasHerNumber, LONG_ENOUGH);
            inTransaction(() -> {
                append.append(TaskEvent.accepted(task, actor, Instant.now()));
                flush();
            });
            raduHasCommitted.countDown();
        });

        maria.start();
        radu.start();

        assertThat(mariaHasHerNumber.await(LONG_ENOUGH.toMillis(), TimeUnit.MILLISECONDS))
                .as("the fixture never got started")
                .isTrue();
        raduHasCommitted.await(LONG_ENOUGH.toMillis(), TimeUnit.MILLISECONDS);

        long whereAReaderWouldBeLeft = feed.currentCursor();

        mariaMayCommit.countDown();
        maria.join();
        radu.join();

        assertThat(feed.since(whereAReaderWouldBeLeft, 100))
                .as("both moves have to be reachable from the position a reader held while they were "
                        + "in flight — an event no cursor can reach is worse than one delivered twice")
                .extracting(TaskEventRecord::action)
                .containsExactlyInAnyOrder("TASK_CREATED", "TASK_ACCEPTED");
    }

    private Thread writerHolding(CountDownLatch hasItsNumber, CountDownLatch mayCommit, TaskEvent event) {
        return new Thread(() -> inTransaction(() -> {
            append.append(event);
            flush();
            hasItsNumber.countDown();
            awaitQuietly(mayCommit, LONG_ENOUGH.plusSeconds(5));
        }));
    }

    private void inTransaction(Runnable work) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> work.run());
    }

    private void flush() {
        EntityManagerFactoryUtils.getTransactionalEntityManager(entityManagers).flush();
    }

    private void awaitQuietly(CountDownLatch latch, Duration atMost) {
        try {
            latch.await(atMost.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
