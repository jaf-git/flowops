package com.flowops.tasklib.application.published;

import com.flowops.tasklib.application.port.TaskTemplatePort;
import com.flowops.tasklib.domain.TaskTemplate;
import com.flowops.tasklib.domain.TemplateDetails;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TemplateResolutionService implements TemplateResolutionUseCase {
    private static final int MATCH_CANDIDATES = 1;

    private final TaskTemplatePort templates;
    private final Clock clock;

    public TemplateResolutionService(TaskTemplatePort templates, Clock clock) {
        this.templates = templates;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID resolve(String title, String description, UUID author) {
        String wanted = title == null ? "" : title.trim();
        if (wanted.isEmpty()) {
            throw new IllegalArgumentException("a template cannot be resolved from a blank title");
        }

        Optional<TaskTemplate> existing = matching(wanted);
        if (existing.isPresent()) {
            return existing.get().id();
        }

        Instant now = clock.instant();
        TaskTemplate created = TaskTemplate.written(details(wanted, description), author, true, now)
                .approved(now);
        try {
            templates.save(created);
            return created.id();
        } catch (DuplicateKeyException lostTheRace) {
            return matching(wanted).map(TaskTemplate::id).orElseThrow(() -> lostTheRace);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> findMeaning(String title) {
        String wanted = title == null ? "" : title.trim();
        return wanted.isEmpty() ? Optional.empty() : matching(wanted).map(TaskTemplate::id);
    }

    private Optional<TaskTemplate> matching(String title) {
        Optional<TaskTemplate> named = templates.approvedNamed(title);
        if (named.isPresent()) {
            return named;
        }
        List<TaskTemplatePort.Resemblance> alike = templates.resembling(title, MATCH_CANDIDATES);
        return alike.isEmpty() ? Optional.empty() : Optional.of(alike.get(0).template());
    }

    private static TemplateDetails details(String title, String description) {
        return new TemplateDetails(title, description, null, "NORMAL", null, List.of());
    }
}
