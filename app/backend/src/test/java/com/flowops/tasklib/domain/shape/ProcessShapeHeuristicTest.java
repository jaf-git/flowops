package com.flowops.tasklib.domain.shape;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.tasklib.domain.TemplateDetails;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProcessShapeHeuristicTest {
    private final ProcessShapeHeuristic heuristic = ProcessShapeHeuristic.standard();

    @Test
    void ordinaryTaskWorkIsNotQuestioned() {
        List<ProcessShapeHint> hints =
                heuristic.inspect(withChecklist("Deschide registrul", "Compară totalurile", "Trimite pe email"));

        assertThat(hints).isEmpty();
    }

    @Test
    void aTemplateWithNoChecklistIsNeverQuestioned() {
        assertThat(heuristic.inspect(withChecklist())).isEmpty();
    }

    @Test
    void anItemAskingSomebodyElseForApprovalIsNoticed() {
        List<ProcessShapeHint> hints =
                heuristic.inspect(withChecklist("Verifică prețurile", "Cere aprobarea contabilei"));

        assertThat(hints).hasSize(1);
        assertThat(hints.get(0).signal()).isEqualTo("handover-language");

        assertThat(hints.get(0).evidence()).isEqualTo("Cere aprobarea contabilei");
    }

    @Test
    void theSameItemIsNoticedWithoutItsDiacritics() {
        assertThat(heuristic.inspect(withChecklist("Trimite catre contabil"))).hasSize(1);
        assertThat(heuristic.inspect(withChecklist("Trimite către contabil"))).hasSize(1);
    }

    @Test
    void englishHandoverLanguageIsNoticedToo() {
        assertThat(heuristic.inspect(withChecklist("Send to the accountant"))).hasSize(1);
        assertThat(heuristic.inspect(withChecklist("Wait for approval"))).isNotEmpty();
    }

    @Test
    void aWordMerelyContainingAHandoverStemIsStillNoticedAndThatIsTheRightWayRound() {
        assertThat(heuristic.inspect(withChecklist("Notează dacă a fost aprobat")))
                .hasSize(1);
    }

    @Test
    void aChecklistLongEnoughToBeAPlanIsQuestioned() {
        List<ProcessShapeHint> hints = heuristic.inspect(withChecklist("Unu", "Doi", "Trei", "Patru", "Cinci"));

        assertThat(hints).hasSize(1);
        assertThat(hints.get(0).signal()).isEqualTo("checklist-length");
        assertThat(hints.get(0).evidence()).isEqualTo("5");
    }

    @Test
    void fourItemsIsANormalMorningAndStaysSilent() {
        assertThat(heuristic.inspect(withChecklist("Unu", "Doi", "Trei", "Patru")))
                .isEmpty();
    }

    @Test
    void twoSuspicionsAreBothReportedRatherThanOnlyTheFirst() {
        List<ProcessShapeHint> hints =
                heuristic.inspect(withChecklist("Unu", "Doi", "Trei", "Patru", "Cere aprobarea contabilei"));

        assertThat(hints).hasSize(2);
        assertThat(hints.stream().map(ProcessShapeHint::signal))
                .containsExactlyInAnyOrder("handover-language", "checklist-length");
    }

    @Test
    void theMisfiledSeedTemplateIsMissedAndThatIsRecordedRatherThanHidden() {
        List<ProcessShapeHint> hints = heuristic.inspect(
                withChecklist("Colectează brieful", "Trimite contractul", "Programează ședința de start"));

        assertThat(hints).isEmpty();
    }

    @Test
    void anExplicitHandoverInTheSameTemplateIsCaught() {
        List<ProcessShapeHint> hints = heuristic.inspect(
                withChecklist("Colectează brieful", "Trimite către client contractul", "Programează ședința"));

        assertThat(hints).extracting(ProcessShapeHint::signal).containsExactly("handover-language");
    }

    @Test
    void aSignalCanBeAddedWithoutTouchingTheOnesAlreadyThere() {
        ProcessShapeSignal everythingIsSuspicious =
                details -> java.util.Optional.of(new ProcessShapeHint("invented", details.title()));

        ProcessShapeHeuristic extended = new ProcessShapeHeuristic(List.of(everythingIsSuspicious));

        assertThat(extended.inspect(withChecklist()))
                .extracting(ProcessShapeHint::signal)
                .containsExactly("invented");
    }

    private TemplateDetails withChecklist(String... items) {
        return new TemplateDetails(
                "Verificare factură lunară", "Compară totalurile.", "Facturare", "NORMAL", null, List.of(items));
    }
}
