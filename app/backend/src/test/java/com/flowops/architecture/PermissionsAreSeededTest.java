package com.flowops.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.support.ApplicationTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@Tag("architecture")
class PermissionsAreSeededTest extends ApplicationTest {
    private static final List<Path> ACCESS_CONTROL_FILES = List.of(
            Path.of("../../specs/01_FEATURE_AUTH/AUTH_02_ACCESS_CONTROL.md"),
            Path.of("../../specs/02_FEATURE_WORKSPACE/WORKSPACE_02_ACCESS_CONTROL.md"),
            Path.of("../../specs/03_FEATURE_TASK/TASK_02_ACCESS_CONTROL.md"),
            Path.of("../../specs/04_FEATURE_PROCESS/PROCESS_02_ACCESS_CONTROL.md"));

    private static final Pattern PERMISSION_ROW = Pattern.compile("^\\| `([A-Z][A-Z_]{3,})` \\|", Pattern.MULTILINE);

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void everyPermissionNamedInASpecificationIsSeeded() throws IOException {
        Set<String> specified = new TreeSet<>();
        for (Path file : ACCESS_CONTROL_FILES) {
            assertThat(file)
                    .as("an access-control file this check binds to must exist; a rename must fail here "
                            + "rather than quietly reduce what is checked")
                    .exists();
            Matcher rows = PERMISSION_ROW.matcher(Files.readString(file, StandardCharsets.UTF_8));
            while (rows.find()) {
                specified.add(rows.group(1));
            }
        }

        assertThat(specified)
                .as("the parse must find permissions; an empty set would make this check vacuous")
                .hasSizeGreaterThan(10);

        Set<String> seeded = new TreeSet<>(jdbc.queryForList("select name from auth_permission", String.class));

        Set<String> missing = specified.stream()
                .filter(name -> !seeded.contains(name))
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(missing)
                .as(
                        """
                        These permissions are named in an access-control specification and have no row in \
                        auth_permission. Any endpoint enforcing one is unreachable by every caller, including \
                        the owner, and no unit test will show it — V2's blanket owner grant ran once and does \
                        not reach anything added later. Add them in a migration. Declaring a permission grants \
                        nobody anything; the grant belongs in the slice that builds the use case.\
                        """)
                .isEmpty();
    }
}
