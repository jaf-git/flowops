package com.flowops.chat.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.chat.api.dto.AssignmentContextResponse;
import com.flowops.chat.api.dto.ConversationsResponse;
import com.flowops.chat.api.dto.MessagesResponse;
import com.flowops.chat.api.dto.WorkDraftResponse;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("CHAT-WIRE-CONTRACT")
class ChatWireContractTest {
    private static List<String> componentsOf(Class<?> record) {
        return Arrays.stream(record.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
    }

    @Nested
    class TheWorkDraft {
        @Test
        void carriesExactlyWhatTheReviewingFormReads() {
            assertThat(componentsOf(WorkDraftResponse.class))
                    .as("a rename here is a crash there, and it has been")
                    .containsExactlyInAnyOrder("shape", "title", "assigneeId", "deadline", "steps");
        }

        @Test
        void aStepCarriesExactlyWhatTheFormReads() {
            assertThat(componentsOf(WorkDraftResponse.Step.class))
                    .containsExactlyInAnyOrder("quotedFrom", "title", "description", "assigneeId", "deadline");
        }

        @Test
        void everySourcedValueKeepsItsProvenanceBesideIt() {
            assertThat(componentsOf(WorkDraftResponse.Sourced.class)).containsExactlyInAnyOrder("value", "source");
        }

        @Test
        void theSteererIsNotCalledWhatProcessCallsIt() {
            assertThat(componentsOf(WorkDraftResponse.class))
                    .doesNotContain("processOwnerId", "processOwner", "ownerId");
        }
    }

    @Nested
    class TheAssignmentContext {
        @Test
        void carriesExactlyWhatTheHeaderControlsRead() {
            assertThat(componentsOf(AssignmentContextResponse.class))
                    .containsExactlyInAnyOrder(
                            "counterpartId",
                            "counterpartName",
                            "counterpartActive",
                            "mayAssignTask",
                            "mayStartRun",
                            "templates");
        }

        @Test
        void keepsPermissionAndEmptinessApart() {
            assertThat(componentsOf(AssignmentContextResponse.class))
                    .as("mayStartRun is not derivable from templates being empty")
                    .contains("mayStartRun", "templates");
        }

        @Test
        void aTemplateCarriesEnoughToChooseOneAndNothingAboutItsGraph() {
            assertThat(componentsOf(AssignmentContextResponse.Template.class))
                    .containsExactlyInAnyOrder("id", "name", "overview", "stepCount");
        }
    }

    @Nested
    class TheThread {
        @Test
        void anEntryCarriesExactlyWhatTheThreadReads() {
            assertThat(componentsOf(MessagesResponse.Row.class))
                    .containsExactlyInAnyOrder(
                            "kind",
                            "id",
                            "authorId",
                            "authorName",
                            "body",
                            "sentAt",
                            "editedAt",
                            "deletedAt",
                            "convertedTaskId",
                            "work",
                            "seq");
        }

        @Test
        void theWorkAMarkNamesCarriesNoSentence() {
            assertThat(componentsOf(MessagesResponse.Work.class)).containsExactlyInAnyOrder("kind", "id");
        }
    }

    @Nested
    class TheRail {
        @Test
        void aConversationCarriesExactlyWhatTheRailReads() {
            assertThat(componentsOf(ConversationsResponse.Row.class))
                    .containsExactlyInAnyOrder(
                            "id",
                            "kind",
                            "counterpartId",
                            "counterpartName",
                            "counterpartActive",
                            "name",
                            "lastMessagePreview",
                            "lastMessageDeleted",
                            "lastMessageAt",
                            "unreadCount");
        }

        @Test
        void carriesNoNumberAboutAnybodyButTheCallerThemselves() {
            assertThat(componentsOf(ConversationsResponse.Row.class))
                    .doesNotContain("messageCount", "responseTime", "responseTimeMinutes", "activityScore", "lastSeen");
        }
    }
}
