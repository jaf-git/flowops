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
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@Tag("architecture")
class EveryEnforcedPermissionReachesARoleTest extends ApplicationTest {
    private static final Path MAIN_SOURCES = Path.of("src/main/java");

    private static final Pattern QUOTED_IDENTIFIER = Pattern.compile("['\"]([A-Z][A-Z_]{3,})['\"]");

    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT = Pattern.compile("//[^\\n]*");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void everyPermissionTheCodeEnforcesIsHeldBySomebody() throws IOException {
        Set<String> declared = new TreeSet<>(jdbc.queryForList("select name from auth_permission", String.class));
        assertThat(declared)
                .as("no permissions are declared at all; this check would pass over an empty set")
                .isNotEmpty();

        Set<String> enforced = new TreeSet<>();
        try (Stream<Path> sources = Files.walk(MAIN_SOURCES)) {
            List<Path> files =
                    sources.filter(path -> path.toString().endsWith(".java")).toList();
            assertThat(files)
                    .as("no Java sources were read; the scan would prove nothing")
                    .isNotEmpty();
            for (Path file : files) {
                String code = withoutComments(Files.readString(file, StandardCharsets.UTF_8));
                Matcher named = QUOTED_IDENTIFIER.matcher(code);
                while (named.find()) {
                    if (declared.contains(named.group(1))) {
                        enforced.add(named.group(1));
                    }
                }
            }
        }

        assertThat(enforced)
                .as("the scan found no permission named anywhere in the code, which cannot be true "
                        + "while endpoints carry @PreAuthorize; the population is asserted before the result "
                        + "is believed")
                .hasSizeGreaterThan(10);

        Set<String> granted = new TreeSet<>(
                jdbc.queryForList("select distinct permission_name from auth_role_permission", String.class));

        Set<String> ungranted = enforced.stream()
                .filter(name -> !granted.contains(name))
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(ungranted)
                .as(
                        """
                        These permissions are enforced by shipped code and granted to no role. Any endpoint or \
                        rule behind one is unreachable by every caller, the owner included, and it fails as a \
                        403 that is indistinguishable from a correct refusal. Grant them in a migration, or \
                        stop enforcing them. This has now happened three times on this project: three WORKSPACE \
                        permissions, V20's own warning about them, and PROCESS_VIEW_ANY between V24 and V25.\
                        """)
                .isEmpty();
    }

    private static String withoutComments(String source) {
        return LINE_COMMENT
                .matcher(BLOCK_COMMENT.matcher(source).replaceAll(" "))
                .replaceAll(" ");
    }
}
