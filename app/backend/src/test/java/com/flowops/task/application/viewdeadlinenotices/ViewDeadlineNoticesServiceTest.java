package com.flowops.task.application.viewdeadlinenotices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowops.task.application.shared.exception.NotAuthenticatedException;
import com.flowops.task.application.shared.port.IdentifyCallerPort;
import com.flowops.task.application.shared.port.LoadDeadlineNoticesPort;
import com.flowops.task.application.shared.port.LoadPersonPort;
import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.TaskId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("TASK-SET-DEADLINE-01")
@ExtendWith(MockitoExtension.class)
class ViewDeadlineNoticesServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-12T09:00:00Z");
    private static final PersonId MARIA = PersonId.of(UUID.randomUUID());
    private static final PersonId ELENA = PersonId.of(UUID.randomUUID());

    @Mock
    private IdentifyCallerPort identifyCallerPort;

    @Mock
    private LoadDeadlineNoticesPort loadDeadlineNoticesPort;

    @Mock
    private LoadPersonPort loadPersonPort;

    private ViewDeadlineNoticesService service;

    @BeforeEach
    void buildTheService() {
        service = new ViewDeadlineNoticesService(identifyCallerPort, loadDeadlineNoticesPort, loadPersonPort);
    }

    @Test
    void theQuestionIsAlwaysAskedAboutTheCallerAndNeverAboutAnybodyElse() {
        signedInAs(MARIA);
        when(loadDeadlineNoticesPort.unacknowledgedFor(MARIA)).thenReturn(List.of());

        service.execute();

        verify(loadDeadlineNoticesPort).unacknowledgedFor(MARIA);
    }

    @Test
    void eachNoticeCarriesTheAssigneesNameResolvedForThisResponse() {
        signedInAs(MARIA);
        theresANoticeFrom(ELENA);
        when(loadPersonPort.describe(ELENA))
                .thenReturn(Optional.of(new LoadPersonPort.Person(ELENA, "Elena Dumitrescu", true)));

        ViewDeadlineNoticesResult.Notice only = service.execute().notices().getFirst();

        assertThat(only.assignee()).isEqualTo(ELENA);
        assertThat(only.assigneeName()).isEqualTo("Elena Dumitrescu");
        assertThat(only.deadline()).isEqualTo(NOW.plusSeconds(3 * 86_400));
    }

    @Test
    void aNoticeFromSomebodyErasedStillAppearsWithNoName() {
        signedInAs(MARIA);
        theresANoticeFrom(ELENA);
        when(loadPersonPort.describe(ELENA)).thenReturn(Optional.empty());

        assertThat(service.execute().notices()).hasSize(1);
        assertThat(service.execute().notices().getFirst().assigneeName()).isEmpty();
    }

    @Test
    void thereIsNoAnswerWithoutASession() {
        when(identifyCallerPort.currentCaller()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute()).isInstanceOf(NotAuthenticatedException.class);
    }

    private void signedInAs(PersonId person) {
        when(identifyCallerPort.currentCaller()).thenReturn(Optional.of(person));
    }

    private void theresANoticeFrom(PersonId assignee) {
        when(loadDeadlineNoticesPort.unacknowledgedFor(MARIA))
                .thenReturn(List.of(new LoadDeadlineNoticesPort.Notice(
                        TaskId.generate(), "Pregătește dosarul fiscal", assignee, NOW.plusSeconds(3 * 86_400), NOW)));
    }
}
