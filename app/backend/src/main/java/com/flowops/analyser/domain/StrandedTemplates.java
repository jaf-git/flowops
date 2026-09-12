package com.flowops.analyser.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Approved templates still carrying the name of an activity that was merged away.
 *
 * <p><b>One definition, two moments.</b> The merge raises this the instant a person makes the
 * decision, because they are the only one who can say whether the templates still describe real
 * work. Every analysis raises it again for as long as it is true, which is what catches the merge
 * made in a workspace that had never run an analysis for the first one to attach to.
 *
 * <p>Both produce the same {@code analyser:kind:subject} key, so the second sighting is the same
 * finding rather than a duplicate: {@code times_seen} climbs, {@code first_seen_at} holds, and a
 * dismissal made once stays made. That is the whole reason this lives here as one object rather than
 * as two similar-looking builders.
 *
 * <p>Nothing here retires anything. {@code DECISION-NO-TEMPLATE-BINDING-01} binds no template to the
 * work it resembles, so deciding two words mean one thing is not evidence that a template somebody
 * approved has stopped being right.
 */
public record StrandedTemplates(String absorbed, String surviving, List<Stranded> templates) {
    public static final String DETECTOR = "S2_VOCABULARY";

    public static final String KIND = "templates_left_by_a_merge";

    public StrandedTemplates {
        templates = templates == null ? List.of() : List.copyOf(templates);
    }

    public record Stranded(String id, String title, int timesUsed) {}

    /** The stable half of the finding's key, so the merge and the analyser agree on one identity. */
    public static String subjectFor(String absorbed) {
        return "merged:" + absorbed.toLowerCase(Locale.ROOT);
    }

    public boolean isEmpty() {
        return templates.isEmpty();
    }

    public Finding asFinding() {
        int used = templates.stream().mapToInt(Stranded::timesUsed).sum();
        boolean one = templates.size() == 1;

        List<String> because = new ArrayList<>();
        because.add("\"%s\" was merged into \"%s\", so the work itself now counts as one activity."
                .formatted(absorbed, surviving));
        because.add(
                one
                        ? "The template drafted from the old name is untouched: %s."
                                .formatted(templates.getFirst().title())
                        : "The %d templates drafted from the old name are untouched.".formatted(templates.size()));
        because.add(
                used == 0
                        ? "Neither name has been used to stamp a task yet, so nothing is in flight against them."
                        : "They have stamped %d task(s) between them, and that history is unchanged.".formatted(used));
        because.add("Nothing was retired. Deciding that two words mean one thing is not evidence that a "
                + "template somebody approved has stopped describing real work, and only a person can tell.");

        return new Finding(
                DETECTOR,
                KIND,
                SubjectKind.WORKSPACE,
                subjectFor(absorbed),
                Category.YOUR_LIBRARY,
                one
                        ? "One approved template still names \"%s\", which is now \"%s\"".formatted(absorbed, surviving)
                        : "%d approved templates still name \"%s\", which is now \"%s\""
                                .formatted(templates.size(), absorbed, surviving),
                because,
                Map.of(
                        Finding.EvidenceKind.TEMPLATE,
                        templates.stream().map(Stranded::id).toList()),
                Severity.LOW,
                Confidence.HIGH,
                templates.size(),
                null,
                "Review them");
    }
}
