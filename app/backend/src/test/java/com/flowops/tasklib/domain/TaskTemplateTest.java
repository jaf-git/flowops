package com.flowops.tasklib.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowops.tasklib.domain.exception.IllegalTemplateTransitionException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TaskTemplateTest {
    private static final Instant WRITTEN = Instant.parse("2026-08-20T09:00:00Z");
    private static final Instant LATER = Instant.parse("2026-08-20T11:00:00Z");
    private static final UUID AUTHOR = UUID.randomUUID();

    @Test
    void aSubmittedTemplateGoesStraightIntoTheQueueAndADraftDoesNot() {
        assertThat(written(true).status()).isEqualTo(TemplateStatus.PROPOSED);
        assertThat(written(false).status()).isEqualTo(TemplateStatus.DRAFT);
    }

    @Test
    void approvingSomethingNobodyProposedIsRefused() {
        TaskTemplate approved = written(true).approved(LATER);

        assertThatThrownBy(() -> approved.approved(LATER))
                .isInstanceOf(IllegalTemplateTransitionException.class)
                .hasMessageContaining("awaiting approval");
    }

    @Test
    void aRetiredTemplateCannotBeApprovedBackIntoTheLibrary() {
        TaskTemplate retired = written(true).retired(LATER);

        assertThatThrownBy(() -> retired.approved(LATER)).isInstanceOf(IllegalTemplateTransitionException.class);
    }

    @Test
    void sendingBackReturnsItToItsAuthorAsADraftCarryingTheReason() {
        TaskTemplate sentBack = written(true).sentBack("Ultimul pas cere aprobarea altcuiva", LATER);

        assertThat(sentBack.status()).isEqualTo(TemplateStatus.DRAFT);
        assertThat(sentBack.rejectionReason()).isEqualTo("Ultimul pas cere aprobarea altcuiva");
        assertThat(sentBack.details().title()).isEqualTo("Verificare factură lunară");
    }

    @Test
    void resubmittingClearsTheReasonItPreviouslyCameBackWith() {
        TaskTemplate resubmitted =
                written(true).sentBack("prea multe verificări", LATER).submitted(LATER);

        assertThat(resubmitted.status()).isEqualTo(TemplateStatus.PROPOSED);
        assertThat(resubmitted.rejectionReason()).isNull();
    }

    @Test
    void approvingClearsTheReasonTooSoAnApprovedTemplateCarriesNoOldComplaint() {
        TaskTemplate approved = written(true)
                .sentBack("prea multe verificări", LATER)
                .submitted(LATER)
                .approved(LATER);

        assertThat(approved.rejectionReason()).isNull();
    }

    @Test
    void onlyAnApprovedTemplateMayBeUsed() {
        assertThatThrownBy(() -> written(false).requireUsable()).isInstanceOf(IllegalTemplateTransitionException.class);
        assertThatThrownBy(() -> written(true).requireUsable()).isInstanceOf(IllegalTemplateTransitionException.class);
        assertThatThrownBy(() -> written(true).approved(LATER).retired(LATER).requireUsable())
                .isInstanceOf(IllegalTemplateTransitionException.class);
    }

    @Test
    void anApprovedTemplateIsUsable() {
        written(true).approved(LATER).requireUsable();
    }

    @Test
    void editingAProposalDoesNotWithdrawIt() {
        TaskTemplate revised = written(true).revisedTo(details("Verificare factură trimestrială"), LATER);

        assertThat(revised.status()).isEqualTo(TemplateStatus.PROPOSED);
        assertThat(revised.details().title()).isEqualTo("Verificare factură trimestrială");
        assertThat(revised.approved(LATER).status()).isEqualTo(TemplateStatus.APPROVED);
    }

    @Test
    void everyTransitionKeepsTheIdentityTheUsageCountAndTheAuthor() {
        TaskTemplate original = written(true);

        TaskTemplate moved = original.sentBack("nu", LATER).submitted(LATER).approved(LATER);

        assertThat(moved.id()).isEqualTo(original.id());
        assertThat(moved.authorId()).isEqualTo(original.authorId());
        assertThat(moved.timesUsed()).isEqualTo(original.timesUsed());
        assertThat(moved.createdAt()).isEqualTo(original.createdAt());
    }

    @Test
    void retiringTwiceIsRefusedRatherThanSilentlyAccepted() {
        TaskTemplate retired = written(true).retired(LATER);

        assertThatThrownBy(() -> retired.retired(LATER)).isInstanceOf(IllegalTemplateTransitionException.class);
    }

    @Test
    void aTemplateCanBeWithdrawnStraightFromTheQueue() {
        assertThat(written(true).retired(LATER).status()).isEqualTo(TemplateStatus.RETIRED);
    }

    @Test
    void theDetailsDefendThemselvesAgainstACallerKeepingItsOwnList() {
        List<String> mutable = new java.util.ArrayList<>(List.of("Deschide registrul"));
        TemplateDetails held = new TemplateDetails("Verificare", null, null, "NORMAL", null, mutable);

        mutable.add("Adăugat pe furiș");

        assertThat(held.checklist()).containsExactly("Deschide registrul");
    }

    @Test
    void aTemplateEntersTheLibraryOnceAndRemembersWhen() {
        TaskTemplate proposal = written(true);
        assertThat(proposal.approvedAt()).as("nothing has been blessed yet").isNull();

        TaskTemplate approved = proposal.approved(LATER);
        assertThat(approved.approvedAt()).isEqualTo(LATER);
    }

    @Test
    void aBouncedProposalEntersTheLibraryWhenItIsFinallyApproved() {
        Instant muchLater = Instant.parse("2026-09-04T09:00:00Z");

        TaskTemplate returned = written(true).sentBack("Add the VAT line", LATER);
        assertThat(returned.approvedAt())
                .as("sent back, so it never entered the library")
                .isNull();

        TaskTemplate blessed = returned.submitted(muchLater).approved(muchLater);

        assertThat(blessed.approvedAt())
                .as("it entered when it was approved, not when it was first written")
                .isEqualTo(muchLater);
        assertThat(blessed.createdAt())
                .as("and the two dates are genuinely different, which is why one cannot stand in for the other")
                .isEqualTo(WRITTEN);
    }

    @Test
    void everyOtherTransitionCarriesTheDateForward() {
        TaskTemplate approved = written(true).approved(LATER);

        assertThat(approved.revisedTo(details("Verificare factură"), LATER).approvedAt())
                .isEqualTo(LATER);
        assertThat(approved.describedBy(TemplateMetadata.empty(), LATER).approvedAt())
                .isEqualTo(LATER);
        assertThat(approved.retired(LATER).approvedAt()).isEqualTo(LATER);
        assertThat(approved.convertedTo(UUID.randomUUID(), LATER).approvedAt()).isEqualTo(LATER);
        assertThat(approved.alsoRunsIn(UUID.randomUUID(), LATER).approvedAt()).isEqualTo(LATER);
    }

    private TaskTemplate written(boolean submitForApproval) {
        return TaskTemplate.written(details("Verificare factură lunară"), AUTHOR, submitForApproval, WRITTEN);
    }

    private TemplateDetails details(String title) {
        return new TemplateDetails(
                title,
                "Compară totalurile cu registrul.",
                "Facturare",
                "HIGH",
                new BigDecimal("1.5"),
                List.of("Deschide registrul", "Compară totalurile"));
    }
}
