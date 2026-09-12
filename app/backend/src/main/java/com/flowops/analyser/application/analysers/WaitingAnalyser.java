package com.flowops.analyser.application.analysers;

import com.flowops.analyser.domain.Absence;
import com.flowops.analyser.domain.Analyser;
import com.flowops.analyser.domain.AnalyserStage;
import com.flowops.analyser.domain.Category;
import com.flowops.analyser.domain.Clean;
import com.flowops.analyser.domain.Confidence;
import com.flowops.analyser.domain.Finding;
import com.flowops.analyser.domain.Precondition;
import com.flowops.analyser.domain.Report;
import com.flowops.analyser.domain.Severity;
import com.flowops.analyser.domain.Snapshot;
import com.flowops.analyser.domain.SubjectKind;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class WaitingAnalyser implements Analyser {
    private static final Set<String> SOMEBODY_ELSES = Set.of("CLIENT", "SUPPLIER");

    private static final int ENOUGH_TO_TAKE_A_MEDIAN = 5;

    @Override
    public AnalyserStage stage() {
        return AnalyserStage.MEASURE;
    }

    @Override
    public String id() {
        return "S4_WAITING";
    }

    @Override
    public Category category() {
        return Category.YOUR_TIME;
    }

    @Override
    public Report analyse(Snapshot snapshot) {
        List<Snapshot.Wait> waits = snapshot.waits();

        if (waits.isEmpty()) {
            return Report.blocked(
                    id(),
                    0,
                    Precondition.unmet(
                            "somebody to have recorded that work was waiting",
                            "nothing in this window records a wait",
                            "Say what a piece of work is waiting on"));
        }

        List<Finding> findings = new ArrayList<>();
        List<Absence> absences = new ArrayList<>();
        List<Clean> clean = new ArrayList<>();

        neverCameBack(waits)
                .ifPresentOrElse(
                        findings::add,
                        () -> clean.add(new Clean(
                                "every_wait_resolved",
                                "%d waits checked; everybody who was being waited on came back."
                                        .formatted(waits.size()))));

        howLongPeopleWait(waits, findings, absences);

        return new Report(
                id(),
                waits.size(),
                findings,
                absences,
                clean,
                List.of(new Precondition(
                        "enough waits to measure a median — at least %d of a kind".formatted(ENOUGH_TO_TAKE_A_MEDIAN),
                        "%d recorded in this window".formatted(waits.size()),
                        waits.size() >= ENOUGH_TO_TAKE_A_MEDIAN,
                        waits.size() >= ENOUGH_TO_TAKE_A_MEDIAN ? null : "Record more waits as they happen")));
    }

    private Optional<Finding> neverCameBack(List<Snapshot.Wait> waits) {
        List<Snapshot.Wait> unresolved =
                waits.stream().filter(wait -> !wait.satisfied()).toList();

        if (unresolved.isEmpty()) {
            return Optional.empty();
        }

        long external = unresolved.stream()
                .filter(wait -> SOMEBODY_ELSES.contains(wait.waitingOn()))
                .count();
        long givenUpOn = unresolved.stream().filter(Snapshot.Wait::cancelled).count();

        List<String> because = new ArrayList<>();
        because.add("%d of those were waiting on somebody outside — a client or a supplier.".formatted(external));
        because.add("%d were cancelled, which means somebody gave up rather than got an answer.".formatted(givenUpOn));
        because.add("None of this is time anybody spent working. It is time work spent stopped, and it "
                + "is reported apart from effort for that reason.");

        return Optional.of(new Finding(
                id(),
                "wait_never_satisfied",
                SubjectKind.WORKSPACE,
                "workspace",
                category(),
                "%d of %d waits were never answered".formatted(unresolved.size(), waits.size()),
                because,
                Map.of(
                        Finding.EvidenceKind.WAIT,
                        unresolved.stream().map(Snapshot.Wait::id).toList()),
                external > 0 ? Severity.HIGH : Severity.MEDIUM,
                Confidence.HIGH,
                unresolved.size(),
                waits.size(),
                "Chase what was never answered"));
    }

    private void howLongPeopleWait(List<Snapshot.Wait> waits, List<Finding> findings, List<Absence> absences) {
        Map<String, List<Snapshot.Wait>> byKind = waits.stream()
                .filter(wait -> wait.waitingOn() != null)
                .collect(Collectors.groupingBy(Snapshot.Wait::waitingOn, LinkedHashMap::new, Collectors.toList()));

        for (Map.Entry<String, List<Snapshot.Wait>> kind : byKind.entrySet()) {
            List<Long> days = kind.getValue().stream()
                    .filter(Snapshot.Wait::satisfied)
                    .filter(wait -> wait.openedAt() != null)
                    .map(wait -> Duration.between(wait.openedAt(), wait.satisfiedAt())
                            .toDays())
                    .sorted()
                    .toList();

            if (days.isEmpty()) {
                absences.add(new Absence(
                        "no_satisfied_wait_of_kind_" + kind.getKey().toLowerCase(java.util.Locale.ROOT),
                        ("No %s wait in this window was ever answered, so there is no length to report for "
                                        + "it — %d were recorded and none finished.")
                                .formatted(
                                        kind.getKey().toLowerCase(java.util.Locale.ROOT),
                                        kind.getValue().size()),
                        false));
                continue;
            }

            findings.add(new Finding(
                    id(),
                    "days_waiting_on_" + kind.getKey().toLowerCase(java.util.Locale.ROOT),
                    SubjectKind.WORKSPACE,
                    "workspace",
                    category(),
                    "Waiting on %s %s takes about %d days"
                            .formatted(
                                    article(kind.getKey()),
                                    kind.getKey().toLowerCase(java.util.Locale.ROOT),
                                    days.get(days.size() / 2)),
                    List.of(
                            "Measured over %d answered waits of %d recorded."
                                    .formatted(days.size(), kind.getValue().size()),
                            SOMEBODY_ELSES.contains(kind.getKey())
                                    ? "This is somebody else's silence. It never enters a figure about "
                                            + "anybody here — invariant I4."
                                    : "This is our own. It is worth acting on, and it is still not a "
                                            + "figure about one person."),
                    Map.of(
                            Finding.EvidenceKind.WAIT,
                            kind.getValue().stream().map(Snapshot.Wait::id).toList()),
                    Severity.LOW,
                    days.size() >= ENOUGH_TO_TAKE_A_MEDIAN ? Confidence.MEDIUM : Confidence.LOW,
                    days.size(),
                    kind.getValue().size(),
                    SOMEBODY_ELSES.contains(kind.getKey()) ? "Set expectations earlier" : "Look at your own handoffs"));
        }
    }

    private static String article(String kind) {
        return "AEIOU".indexOf(Character.toUpperCase(kind.charAt(0))) >= 0 ? "an" : "a";
    }
}
