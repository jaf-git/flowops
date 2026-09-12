package com.flowops.discovery;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NoFigureIsEverKeyedToAPersonTest {
    private static final Set<String> NAMES_A_PERSON = Set.of(
            "person",
            "people",
            "performer",
            "marker",
            "speaker",
            "assignee",
            "employee",
            "user",
            "holder",
            "member",
            "staff",
            "colleague",
            "worker",
            "creator",
            "owner");

    private static final Set<String> IS_A_FIGURE = Set.of(
            "count",
            "counts",
            "total",
            "totals",
            "tally",
            "sum",
            "average",
            "avg",
            "rate",
            "rates",
            "rank",
            "ranking",
            "leaderboard",
            "busiest",
            "slowest",
            "fastest",
            "most",
            "percent",
            "histogram");

    private static final JavaClasses DISCOVERY = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.flowops.discovery");

    @Test
    @DisplayName("R12.1 — no method in Discovery pairs a person with a number")
    void noMethodNamesAFigureAboutAPerson() {
        noMethods()
                .should(beNamedLikeAFigureAboutAPerson())
                .because("R12.1 - counts, tallies, rates and rankings key to a client, a project, a work "
                        + "type or a role pair, and never to a human. The guarantee is the absence of the "
                        + "route: a person's name is attribution on work they hold, never an axis")
                .allowEmptyShould(true)
                .check(DISCOVERY);
    }

    @Test
    @DisplayName("R12.1 — no query in Discovery groups by a person")
    void noQueryGroupsByAPerson() throws IOException {
        Pattern groupBy = Pattern.compile("group\\s+by\\s+([^\"';)]+)", Pattern.CASE_INSENSITIVE);

        List<String> offences = new ArrayList<>();

        try (Stream<Path> sources = Files.walk(Path.of("src/main/java/com/flowops/discovery"))) {
            for (Path source :
                    sources.filter(p -> p.toString().endsWith(".java")).toList()) {
                String text = Files.readString(source, StandardCharsets.UTF_8);
                Matcher clause = groupBy.matcher(text);

                while (clause.find()) {
                    String columns = clause.group(1).toLowerCase(Locale.ROOT);

                    if (mentionsAPerson(stripRoles(columns))) {
                        offences.add(source.getFileName() + " → group by "
                                + clause.group(1).trim());
                    }
                }
            }
        }

        assertThat(offences)
                .describedAs("R12.1 - a query grouping by a person is the product's central claim broken, "
                        + "and it arrives as SQL rather than as a method name. Key it to the client, the "
                        + "project, the work type or the role pair instead")
                .isEmpty();
    }

    @Test
    @DisplayName("the detector itself recognises the forbidden shape, and leaves the permitted one alone")
    void theCheckIsNotInert() {
        assertThat(mentionsAPerson("performer_ref")).isTrue();
        assertThat(mentionsAPerson("n.creator_id, w.marker_id")).isTrue();
        assertThat(mentionsAPerson("m.user_id")).isTrue();

        assertThat(mentionsAPerson(stripRoles("n.performer_role_id"))).isFalse();
        assertThat(mentionsAPerson(stripRoles("r.id, r.name"))).isFalse();
        assertThat(mentionsAPerson("b.work_type, b.counterparty_id")).isFalse();

        assertThat(camelCaseWords("countByPerformer")).containsExactly("count", "by", "performer");
        assertThat(camelCaseWords("openWaitsHeldBy")).doesNotContain("holder");
    }

    private static boolean mentionsAPerson(String columns) {
        return NAMES_A_PERSON.stream().anyMatch(columns::contains);
    }

    private static String stripRoles(String columns) {
        return columns.toLowerCase(Locale.ROOT).replaceAll("\\w*role\\w*", "");
    }

    private static ArchCondition<JavaMethod> beNamedLikeAFigureAboutAPerson() {
        return new ArchCondition<>("be named like a figure about a person") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                List<String> words = camelCaseWords(method.getName());

                boolean namesAPerson = words.stream().anyMatch(NAMES_A_PERSON::contains);
                boolean isAFigure = words.stream().anyMatch(IS_A_FIGURE::contains);

                if (namesAPerson && isAFigure) {
                    events.add(SimpleConditionEvent.violated(
                            method,
                            method.getFullName() + " pairs a person with a number. "
                                    + "'Sunrise design: 3 rounds' is a fact about the account and is allowed; "
                                    + "the same figure keyed to the person who did it is not"));
                }
            }
        };
    }

    private static List<String> camelCaseWords(String name) {
        return Arrays.stream(name.split("(?<!^)(?=[A-Z])|_"))
                .map(word -> word.toLowerCase(Locale.ROOT))
                .toList();
    }
}
