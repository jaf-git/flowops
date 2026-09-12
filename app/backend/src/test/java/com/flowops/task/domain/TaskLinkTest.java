package com.flowops.task.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.task.domain.enums.LinkRole;
import com.flowops.task.domain.model.LinkUrl;
import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.TaskId;
import com.flowops.task.domain.model.TaskLink;
import com.flowops.task.domain.model.TaskLinkId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("TASK-LINK-01")
class TaskLinkTest {
    private static final Instant NOW = Instant.parse("2026-08-13T09:00:00Z");
    private static final String BRIEF = "https://drive.atelier.ro/brief-q3.pdf";

    @Test
    void aLabelledLinkShowsItsLabel() {
        TaskLink link = TaskLink.attached(
                TaskId.generate(),
                LinkUrl.of(BRIEF),
                "Brief for Q3",
                LinkRole.INPUT,
                PersonId.of(UUID.randomUUID()),
                NOW);

        assertThat(link.displayText()).isEqualTo("Brief for Q3");
        assertThat(link.label()).contains("Brief for Q3");
    }

    @Test
    void anUnlabelledLinkShowsItsHost() {
        TaskLink link = TaskLink.attached(
                TaskId.generate(), LinkUrl.of(BRIEF), null, LinkRole.OUTPUT, PersonId.of(UUID.randomUUID()), NOW);

        assertThat(link.label()).isEmpty();
        assertThat(link.displayText()).isEqualTo("drive.atelier.ro");
    }

    @Test
    void anEmptyLabelIsAbsentRatherThanEmpty() {
        TaskLink link = TaskLink.attached(
                TaskId.generate(), LinkUrl.of(BRIEF), "   ", LinkRole.REFERENCE, PersonId.of(UUID.randomUUID()), NOW);

        assertThat(link.label()).as("blank is not a caption").isEmpty();
        assertThat(link.displayText()).isEqualTo("drive.atelier.ro");
    }

    @Test
    void anAddressCarryingNoHostDisplaysAsEmptyRatherThanNull() {
        TaskLink link = TaskLink.rebuild(
                TaskLinkId.generate(),
                TaskId.generate(),
                LinkUrl.rebuild("https:///a/path/with/no/host"),
                null,
                LinkRole.REFERENCE,
                PersonId.of(UUID.randomUUID()),
                NOW);

        assertThat(link.displayText()).isNotNull().isEmpty();
    }

    @Test
    void anAttachedLinkRecordsWhoAttachedItAndWhen() {
        PersonId ionut = PersonId.of(UUID.randomUUID());
        TaskId task = TaskId.generate();

        TaskLink link = TaskLink.attached(task, LinkUrl.of(BRIEF), "Brief for Q3", LinkRole.INPUT, ionut, NOW);

        assertThat(link.id()).isNotNull();
        assertThat(link.task()).isEqualTo(task);
        assertThat(link.addedBy()).isEqualTo(ionut);
        assertThat(link.addedAt()).isEqualTo(NOW);
        assertThat(link.role()).isEqualTo(LinkRole.INPUT);
    }
}
