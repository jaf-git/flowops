package com.flowops.discovery.application.activities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowops.discovery.application.shared.port.ActivityPort;
import com.flowops.discovery.application.shared.port.StaleLibraryPort;
import com.flowops.discovery.domain.model.Activity;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ManageActivitiesTest {
    private static final UUID CRISTINA = UUID.randomUUID();

    private final StubActivities stored = new StubActivities();
    private final StubLibrary library = new StubLibrary();
    private final ManageActivities activities =
            new ManageActivities(stored, library, Clock.fixed(Instant.parse("2026-09-05T09:00:00Z"), ZoneOffset.UTC));

    @Test
    @DisplayName("a name that normalises to one already here returns that one rather than making a second")
    void namingIsIdempotentBySlug() {
        Activity first = activities.add("Write the caption", CRISTINA);
        Activity again = activities.add("  write the CAPTION  ", CRISTINA);

        assertThat(again.id())
                .as("the synonym problem is not solved here, it is prevented - two spellings of one activity "
                        + "would divide one step's history between them")
                .isEqualTo(first.id());
        assertThat(stored.rows).hasSize(1);
    }

    @Test
    @DisplayName("diacritics fold, so a Romanian keyboard and an English one arrive at the same activity")
    void diacriticsFold() {
        Activity written = activities.add("Ședință de brief", CRISTINA);

        assertThat(written.slug()).isEqualTo("sedinta-de-brief");
        assertThat(activities.add("Sedinta de brief", CRISTINA).id()).isEqualTo(written.id());
    }

    @Test
    @DisplayName("the slug can never forge a component of the step key it is carried inside")
    void theSlugIsSafeInsideAStepKey() {
        assertThat(Activity.slugOf("Review + sign-off")).isEqualTo("review-sign-off");
        assertThat(Activity.slugOf("Design / layout")).isEqualTo("design-layout");
        assertThat(Activity.slugOf("Concept: first pass")).isEqualTo("concept-first-pass");
        assertThat(Activity.slugOf("Tag #3")).isEqualTo("tag-3");
    }

    @Test
    @DisplayName("a name that leaves nothing behind is refused rather than stored as an empty identity")
    void aNameThatNormalisesToNothingIsRefused() {
        assertThatThrownBy(() -> activities.add("+++", CRISTINA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name the activity in words");
    }

    @Test
    @DisplayName("using an activity counts the use and names the work, in one go")
    void usingItCountsAndNames() {
        Activity caption = activities.add("Write the caption", CRISTINA);
        UUID node = UUID.randomUUID();

        activities.used(caption.id(), node, CRISTINA);

        assertThat(stored.rows.get(caption.id()).timesUsed())
                .as("times_used is maintained rather than counted live, because it orders the suggestion list")
                .isEqualTo(1);
        assertThat(stored.named).containsEntry(node, caption.id());
    }

    @Test
    @DisplayName("an activity nobody has ever heard of is refused with its identifier")
    void anUnknownActivityIsRefused() {
        UUID nobody = UUID.randomUUID();

        assertThatThrownBy(() -> activities.used(nobody, UUID.randomUUID(), CRISTINA))
                .isInstanceOf(UnknownActivityException.class);
    }

    @Test
    @DisplayName("a merge moves the work across, keeps the pointer, and leaves one activity choosable")
    void mergingJoinsTwoNamesForOneThing() {
        Activity caption = activities.add("Write the caption", CRISTINA);
        Activity copy = activities.add("Post copy", CRISTINA);

        UUID undoneByCaption = UUID.randomUUID();
        UUID doneByCopy = UUID.randomUUID();
        activities.used(caption.id(), undoneByCaption, CRISTINA);
        activities.used(copy.id(), doneByCopy, CRISTINA);

        Activity surviving = activities.merge(copy.id(), caption.id());

        assertThat(surviving.id()).isEqualTo(caption.id());
        assertThat(surviving.timesUsed())
                .as("the two histories join, or the suggestion order would rank the survivor below "
                        + "activities it has now absorbed")
                .isEqualTo(2);
        assertThat(stored.named)
                .as("every node the losing name held now carries the surviving one, which is the whole "
                        + "point: two half-populated steps become one")
                .containsEntry(doneByCopy, caption.id());
        assertThat(stored.rows.get(copy.id()).mergedIntoId())
                .as("the pointer is the only record of what a person decided")
                .contains(caption.id());
        assertThat(activities.choosable()).extracting(Activity::id).containsExactly(caption.id());
    }

    @Test
    @DisplayName("an activity cannot be merged into itself, nor into one that is already gone")
    void aMergeThatWouldLoseBothNamesIsRefused() {
        Activity caption = activities.add("Write the caption", CRISTINA);
        Activity copy = activities.add("Post copy", CRISTINA);

        assertThatThrownBy(() -> activities.merge(caption.id(), caption.id()))
                .isInstanceOf(ActivityMergeRefusedException.class)
                .hasMessageContaining("itself");

        activities.retire(copy.id());

        assertThatThrownBy(() -> activities.merge(caption.id(), copy.id()))
                .isInstanceOf(ActivityMergeRefusedException.class)
                .hasMessageContaining("RETIRED");
    }

    @Test
    @DisplayName("retiring stops a name spreading and changes nothing already marked with it")
    void retiringLeavesTheWorkAlone() {
        Activity vague = activities.add("Stuff", CRISTINA);
        UUID marked = UUID.randomUUID();
        activities.used(vague.id(), marked, CRISTINA);

        activities.retire(vague.id());

        assertThat(activities.choosable()).isEmpty();
        assertThat(stored.named)
                .as("a step key that has been discovered stays the step it was, or every shape built "
                        + "on it silently becomes a different shape")
                .containsEntry(marked, vague.id());
    }

    @Test
    @DisplayName("retiring a merged activity is refused, because it would lose which one it became")
    void retiringAMergedActivityIsRefused() {
        Activity caption = activities.add("Write the caption", CRISTINA);
        Activity copy = activities.add("Post copy", CRISTINA);

        activities.merge(copy.id(), caption.id());

        assertThatThrownBy(() -> activities.retire(copy.id()))
                .isInstanceOf(ActivityMergeRefusedException.class)
                .hasMessageContaining("merged into another activity");
    }

    @Test
    @DisplayName("a merge that leaves approved templates naming the old word raises a finding and retires nothing")
    void aMergeSaysWhatItLeftInTheLibrary() {
        Activity caption = activities.add("Write the caption", CRISTINA);
        Activity copy = activities.add("Post copy", CRISTINA);

        library.approved.put(
                "Post copy", List.of(new StaleLibraryPort.StaleTemplate(UUID.randomUUID(), "Post copy", 4)));

        activities.merge(copy.id(), caption.id());

        assertThat(library.raised)
                .as("the person who has just merged is the one who can say whether the templates still hold")
                .hasSize(1);
        assertThat(library.raised.getFirst().absorbed()).isEqualTo("Post copy");
        assertThat(library.raised.getFirst().surviving()).isEqualTo("Write the caption");
        assertThat(library.raised.getFirst().templates()).hasSize(1);
    }

    @Test
    @DisplayName("a merge that leaves nothing behind says nothing, rather than raising an empty finding")
    void aMergeThatLeavesACleanLibraryIsSilent() {
        Activity caption = activities.add("Write the caption", CRISTINA);
        Activity copy = activities.add("Post copy", CRISTINA);

        activities.merge(copy.id(), caption.id());

        assertThat(library.raised).isEmpty();
    }

    private record Raised(String absorbed, String surviving, List<StaleLibraryPort.StaleTemplate> templates) {}

    private static final class StubLibrary implements StaleLibraryPort {
        private final Map<String, List<StaleTemplate>> approved = new LinkedHashMap<>();
        private final List<Raised> raised = new ArrayList<>();

        @Override
        public List<StaleTemplate> approvedTemplatesNaming(String activityName) {
            return approved.getOrDefault(activityName, List.of());
        }

        @Override
        public void templatesLeftBehindByAMerge(
                String absorbedActivity, String survivingActivity, List<StaleTemplate> templates, Instant at) {
            raised.add(new Raised(absorbedActivity, survivingActivity, templates));
        }
    }

    private static final class StubActivities implements ActivityPort {
        private final Map<UUID, Activity> rows = new LinkedHashMap<>();
        private final Map<UUID, UUID> named = new LinkedHashMap<>();

        @Override
        public UUID nextId() {
            return UUID.randomUUID();
        }

        @Override
        public void save(Activity activity) {
            rows.put(activity.id(), activity);
        }

        @Override
        public Optional<Activity> find(UUID id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public Optional<Activity> findBySlug(String slug) {
            return rows.values().stream()
                    .filter(activity -> activity.slug().equals(slug))
                    .findFirst();
        }

        @Override
        public List<Activity> all() {
            return new ArrayList<>(rows.values());
        }

        @Override
        public List<Activity> choosable() {
            return rows.values().stream()
                    .filter(activity -> activity.status().isChoosable())
                    .toList();
        }

        @Override
        public void nameTheWork(UUID workNodeId, UUID activityId, UUID by, Instant at) {
            named.put(workNodeId, activityId);
        }

        @Override
        public long marksNaming(UUID activityId) {
            return named.values().stream().filter(activityId::equals).count();
        }

        @Override
        public Map<UUID, List<String>> departmentSpread() {
            return Map.of();
        }

        @Override
        public Map<UUID, List<String>> clientSpread() {
            return Map.of();
        }

        @Override
        public List<PriorWork> workOfThisKindIn(UUID jobId, String workType) {
            return List.of();
        }

        @Override
        public void repointUsage(UUID from, UUID into) {
            named.replaceAll((node, activity) -> activity.equals(from) ? into : activity);
        }
    }
}
