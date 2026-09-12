package com.flowops.tasklib.application.port;

import com.flowops.tasklib.domain.TaskTemplate;
import com.flowops.tasklib.domain.TemplateStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskTemplatePort {
    Optional<TaskTemplate> byId(UUID id);

    List<TaskTemplate> byIds(Collection<UUID> ids);

    Optional<TaskTemplate> approvedNamed(String title);

    Optional<TaskTemplate> earliestDraftNamed(String title);

    void save(TaskTemplate template);

    void recordDiscovered(UUID templateId, DiscoveredFacts facts);

    record DiscoveredFacts(
            String workType,
            java.util.List<String> keywords,
            String description,
            java.util.List<String> checklist,
            String requiredInput,
            String completionCriteria) {
        public DiscoveredFacts {
            keywords = keywords == null ? java.util.List.of() : java.util.List.copyOf(keywords);
            checklist = checklist == null ? java.util.List.of() : java.util.List.copyOf(checklist);
        }
    }

    List<TaskTemplate> search(TemplateQuery query, UUID viewer);

    int count(TemplateQuery query, UUID viewer);

    List<TaskTemplate> awaitingApproval();

    List<String> typesInUse();

    List<DraftCandidate> draftCandidates();

    record DraftCandidate(
            String title,
            List<String> variants,
            int drafts,
            java.time.Instant firstSeen,
            java.time.Instant lastSeen,
            List<UUID> templateIds) {}

    List<Resemblance> resembling(String title, int limit);

    record Resemblance(TaskTemplate template, double similarity) {}

    void recordUse(UUID id);

    record TemplateQuery(
            String text, String type, TemplateStatus status, boolean mine, TemplateSort sort, int page, int size) {}

    enum TemplateSort {
        MOST_USED,
        NEWEST,
        ALPHABETICAL
    }
}
