package com.flowops.task.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowops.task.application.createtask.CreateProcessTaskUseCase;
import com.flowops.task.application.createtask.CreateProcessTaskUseCase.NewProcessTask;
import com.flowops.task.application.shared.exception.AssigneeNotActiveException;
import com.flowops.task.application.shared.exception.AssigneeOutOfScopeException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("PROCESS-ASSIGN-REACHABLE-01")
class CreateProcessTaskRoundTripTest extends TaskScenarioTest {
    @Autowired
    private CreateProcessTaskUseCase createProcessTask;

    private Company company;

    @BeforeEach
    void buildTheCompanyFirst() throws Exception {
        company = buildTheCompany();
    }

    private NewProcessTask forStep(UUID assignee, UUID creator) {
        return new NewProcessTask(
                "Pregătește echipamentul",
                "laptop, acces, birou",
                assignee,
                creator,
                Instant.now().plusSeconds(86_400),
                "NORMAL",
                UUID.randomUUID(),
                UUID.randomUUID(),
                null);
    }

    @Test
    void createsTheTaskWhenTheNamedCreatorMayDirectTheAssignee() {
        NewProcessTask requested = forStep(company.elena(), company.maria());

        UUID created = createProcessTask.execute(requested);

        Map<String, Object> row = jdbc.queryForMap("select * from task where id = ?", created);
        assertThat(row.get("assignee_user_id")).isEqualTo(company.elena());
        assertThat(row.get("creator_user_id")).isEqualTo(company.maria());
        assertThat(row.get("state")).isEqualTo("CREATED");
        assertThat(row.get("process_instance_id")).isEqualTo(requested.processInstanceId());
        assertThat(row.get("instance_step_id")).isEqualTo(requested.instanceStepId());
    }

    @Test
    void refusesAnAssigneeOutsideTheNamedCreatorsSubtree() {
        assertThatThrownBy(() -> createProcessTask.execute(forStep(company.elena(), company.ionut())))
                .isInstanceOf(AssigneeOutOfScopeException.class);

        assertThat(jdbc.queryForObject("select count(*) from task", Integer.class))
                .isZero();
    }

    @Test
    void refusesAnAssigneeWhoIsNotActive() {
        assertThatThrownBy(() -> createProcessTask.execute(forStep(UUID.randomUUID(), company.maria())))
                .isInstanceOf(AssigneeNotActiveException.class);

        assertThat(jdbc.queryForObject("select count(*) from task", Integer.class))
                .isZero();
    }

    @Test
    void opensTheSameWaitPhaseAndWritesTheSameTrailAsAnOrdinaryTask() {
        UUID created = createProcessTask.execute(forStep(company.elena(), company.maria()));

        assertThat(jdbc.queryForObject(
                        "select count(*) from task_phase_timer where task_id = ? and phase_kind = 'WAIT' "
                                + "and ended_at is null",
                        Integer.class,
                        created))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select count(*) from task_state_transition where task_id = ?", Integer.class, created))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from task_event where task_id = ?", Integer.class, created))
                .isEqualTo(1);
    }

    @Test
    void anOrdinaryTaskCarriesNoProvenanceAtAll() throws Exception {
        String created = createTaskFor(company.elena());

        Map<String, Object> row = jdbc.queryForMap("select * from task where id = ?::uuid", created);
        assertThat(row.get("process_instance_id")).isNull();
        assertThat(row.get("instance_step_id")).isNull();
    }
}
