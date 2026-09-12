package com.flowops.analyser.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("ANALYSER-RUN-01")
class TheSnapshotReadsSixTablesAndNoMoreTest {
    // Nine now, and the name is left alone: what the test guards is that the list is argued,
    // not the number it currently holds.
    private static final Path ADAPTER =
            Path.of("src/main/java/com/flowops/analyser/infrastructure/persistence/SnapshotAdapter.java");

    /**
     * The tables the snapshot may read, each one argued rather than assumed.
     *
     * <p><b>{@code activity}, added 2026-09-06.</b> The snapshot carries the activities a person
     * merged away, so an analyser can notice that approved templates are still carrying a name
     * nobody can pick again. The merge already raises that finding the moment it happens; this is
     * what catches a merge made in a workspace with no completed analysis for the first one to
     * attach to, and what keeps raising it until somebody decides.
     *
     * <p>Only two columns leave the table — the merged name and its survivor's — and neither is a
     * fact about a person. It is read where every other table in this list is read, so nine
     * analysers can see it; that is the cost, and the alternative was a merge that reports nothing
     * in exactly the workspace least likely to notice.
     */
    private static final Set<String> PERMITTED = new LinkedHashSet<>(List.of(
            "work_node",
            "work_node_state_transition",
            "work_bracket",
            "work_node_wait",
            "job",
            "task_template",
            "counterparty",
            "work_type_vocabulary",
            "activity"));

    private static final Pattern READS = Pattern.compile("\\b(?:from|join)\\s+([a-z][a-z0-9_]*)\\b");

    private static Set<String> tablesReadBy(String source) {
        String sqlOnly = source.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ");
        Set<String> read = new LinkedHashSet<>();
        Matcher matcher = READS.matcher(sqlOnly);
        while (matcher.find()) {
            read.add(matcher.group(1));
        }
        return read;
    }

    @Test
    void theSnapshotReadsExactlyTheSixTablesItsContractNames() throws IOException {
        assertThat(Files.exists(ADAPTER))
                .as("%s is gone, so this test reads nothing and can no longer fail", ADAPTER.toAbsolutePath())
                .isTrue();

        Set<String> read = tablesReadBy(Files.readString(ADAPTER, StandardCharsets.UTF_8));

        assertThat(read)
                .as("no table names were found in the adapter at all, so the contract checked nothing")
                .isNotEmpty();

        assertThat(read)
                .as("the snapshot is one read handed to every analyser, so a table added here is a table "
                        + "nine analysers can suddenly see. Widening it is a decision, and this is where "
                        + "it is argued")
                .isSubsetOf(PERMITTED);
    }

    @Test
    void everyTableTheContractPermitsIsStillRead() throws IOException {
        Set<String> read = tablesReadBy(Files.readString(ADAPTER, StandardCharsets.UTF_8));

        assertThat(read)
                .as("a table the contract permits and the adapter no longer reads is a part of the "
                        + "snapshot that has gone empty without anything saying so")
                .containsAll(PERMITTED);
    }
}
