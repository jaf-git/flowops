package com.flowops.task.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("TASK-APPROVE-01")
class ApprovalScoreIsNotAggregatedTest {
    private static final Path BACKEND = Path.of("src/main/java/com/flowops");
    private static final Path FRONTEND = Path.of("../frontend/src");

    private static final List<String> FORBIDDEN = List.of(
            "avg(score",
            "avg(a.score",
            "sum(score",
            "sum(a.score",
            "groupbyassignee",
            "groupbya.assignee",
            "groupbyt.assignee",
            "averagescore",
            "scoreaverage",
            "scorebyassignee",
            "scoresbyassignee",
            "findbyreviewer");

    @Test
    void noSourceFileAnywhereGroupsOrAveragesAScoreByPerson() throws IOException {
        List<Path> sources = allSources();

        assertThat(sources)
                .as("the scan must find files; an empty population would pass this check without looking at anything")
                .hasSizeGreaterThan(100);

        for (Path source : sources) {
            String text = codeOnly(Files.readString(source, StandardCharsets.UTF_8));

            if (!text.contains("score")) {
                continue;
            }
            for (String forbidden : FORBIDDEN) {
                assertThat(text)
                        .as(
                                "%s appears to aggregate approval scores per person. DECISION-APPROVAL-SCORE-01"
                                        + " forbids it: the score describes one piece of work, and grouped by"
                                        + " assignee it becomes a permanent quality ranking of a person. The"
                                        + " guarantee is enforced by the absence of the query. If a feature"
                                        + " genuinely needs a per-person quality signal, that is a new decision"
                                        + " made in the open.",
                                source)
                        .doesNotContain(forbidden.replace(" ", ""));
            }
        }
    }

    @Test
    void theApprovalRepositoryCanOnlyBeAskedAboutATask() throws IOException {
        Path repository = BACKEND.resolve("task/infrastructure/persistence/repository/TaskApprovalJpaRepository.java");

        assertThat(repository).exists();
        String source = Files.readString(repository, StandardCharsets.UTF_8);

        String code = codeOnly(source);

        assertThat(source).contains("findByTaskId");
        assertThat(code).doesNotContain("assignee");
        assertThat(code).doesNotContain("groupby");
    }

    @Test
    void noEndpointPathOffersScoresAsAThingToBeListed() throws IOException {
        List<Path> controllers = allSources().stream()
                .filter(path -> path.getFileName().toString().endsWith("Controller.java"))
                .toList();

        assertThat(controllers)
                .as("the scan must find controllers; an empty set would make this check vacuous")
                .isNotEmpty();

        for (Path controller : controllers) {
            String code = codeOnly(Files.readString(controller, StandardCharsets.UTF_8));
            assertThat(code)
                    .as("%s exposes a path about scores; a score belongs to the task it judged".formatted(controller))
                    .doesNotContain("/scores");
        }
    }

    private static String codeOnly(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)//.*$", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "");
    }

    private static List<Path> allSources() throws IOException {
        try (Stream<Path> backend = Files.walk(BACKEND);
                Stream<Path> frontend = Files.walk(FRONTEND)) {
            return Stream.concat(backend, frontend)
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return name.endsWith(".java") || name.endsWith(".ts") || name.endsWith(".tsx");
                    })
                    .toList();
        }
    }
}
