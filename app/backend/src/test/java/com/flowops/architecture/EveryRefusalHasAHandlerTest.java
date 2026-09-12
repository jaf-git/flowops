package com.flowops.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.ExceptionHandler;

class EveryRefusalHasAHandlerTest {
    private static final Path SOURCE = Path.of("src", "main", "java", "com", "flowops");

    private static final Set<String> ANSWERED_ELSEWHERE =
            Set.of("PublishedRefusal", "RefusedByDomain", "NotAuthenticatedException");

    private static final Set<String> KNOWN_UNANSWERED = Set.of(
            "InvitedAddressTakenException",
            "InvitedNameRequiredException",
            "InvitedPasswordRefusedException",
            "DisplayNameRequiredException",
            "InvalidEmailAddressException",
            "CursorTooOldException",
            "AlreadyMemberException",
            "DeactivatedMemberException",
            "DeclineWindowException",
            "DuplicateInvitationException",
            "InvitationAlreadyAcceptedException",
            "InvitationLimitReachedException",
            "InvitationNotFoundException",
            "InvitationNotYoursException",
            "ManagerInactiveException",
            "ManagerNotEligibleForReportsException",
            "RoleCeilingException",
            "SelfInvitationException");

    private static Stream<Path> javaFilesUnder(Path root) throws IOException {
        return Files.walk(root).filter(path -> path.toString().endsWith(".java"));
    }

    private static Map<String, String> refusalsByFeature() throws IOException {
        Map<String, String> refusals = new LinkedHashMap<>();
        try (Stream<Path> files = javaFilesUnder(SOURCE)) {
            files.forEach(path -> {
                String name = path.getFileName().toString().replace(".java", "");
                String asText = path.toString().replace('\\', '/');
                if (!name.endsWith("Exception") || ANSWERED_ELSEWHERE.contains(name)) {
                    return;
                }

                if (!asText.contains("/application/") && !asText.contains("/domain/")) {
                    return;
                }
                String feature = featureOf(asText);
                if (feature != null) {
                    refusals.put(name, feature);
                }
            });
        }
        return refusals;
    }

    private static String featureOf(String path) {
        int at = path.indexOf("com/flowops/");
        if (at < 0) {
            return null;
        }
        String rest = path.substring(at + "com/flowops/".length());
        int slash = rest.indexOf('/');
        return slash < 0 ? null : rest.substring(0, slash);
    }

    private static Set<String> handledIn(String feature) throws IOException {
        Path api = SOURCE.resolve(feature).resolve("api");
        Set<String> handled = new TreeSet<>();
        if (!Files.isDirectory(api)) {
            return handled;
        }
        try (Stream<Path> files = javaFilesUnder(api)) {
            files.forEach(path -> {
                try {
                    String text = Files.readString(path);
                    int from = 0;
                    while (true) {
                        int at = text.indexOf("@ExceptionHandler(", from);
                        if (at < 0) {
                            break;
                        }
                        int close = text.indexOf(')', at);
                        for (String named : text.substring(at, close).split("[^A-Za-z0-9_]+")) {
                            if (named.endsWith("Exception")) {
                                handled.add(named);
                            }
                        }
                        from = close;
                    }
                } catch (IOException unreadable) {
                    throw new IllegalStateException(unreadable);
                }
            });
        }
        return handled;
    }

    @Test
    void everyRefusalAFeatureThrowsIsAnsweredByThatFeaturesOwnAdvice() throws IOException {
        Map<String, String> refusals = refusalsByFeature();
        assertThat(refusals)
                .as("the walk found no exceptions at all, so it is not checking anything")
                .isNotEmpty();

        Map<String, Set<String>> handledPerFeature = new LinkedHashMap<>();
        List<String> unanswered = new ArrayList<>();

        for (Map.Entry<String, String> refusal : refusals.entrySet()) {
            String feature = refusal.getValue();
            Set<String> handled = handledPerFeature.computeIfAbsent(feature, this::handledInQuietly);
            if (!handled.contains(refusal.getKey()) && !KNOWN_UNANSWERED.contains(refusal.getKey())) {
                unanswered.add("%s.%s".formatted(feature, refusal.getKey()));
            }
        }

        assertThat(unanswered)
                .as(
                        """
                        These refusals reach no @ExceptionHandler in their own feature's api ring, so they \
                        fall to the catch-all and answer 500 — the server blaming itself for a rule it \
                        applied on purpose. Add the arm beside the exception, or add the class to \
                        ANSWERED_ELSEWHERE with a sentence saying who answers it.""")
                .isEmpty();
    }

    @Test
    void theBaselineShrinksAndNeverQuietlyOutlivesItsFixes() throws IOException {
        Map<String, String> refusals = refusalsByFeature();
        List<String> nowAnswered = new ArrayList<>();

        for (String known : KNOWN_UNANSWERED) {
            String feature = refusals.get(known);
            if (feature != null && handledInQuietly(feature).contains(known)) {
                nowAnswered.add("%s.%s".formatted(feature, known));
            }
        }

        assertThat(nowAnswered)
                .as("these now have a handler, so delete them from KNOWN_UNANSWERED — a stale baseline "
                        + "silences the rule for a route somebody already fixed")
                .isEmpty();
    }

    private Set<String> handledInQuietly(String feature) {
        try {
            return handledIn(feature);
        } catch (IOException unreadable) {
            throw new IllegalStateException(unreadable);
        }
    }

    @Test
    void theAnnotationThisRuleLooksForIsTheOneSpringActsOn() {
        Class<? extends Annotation> annotation = ExceptionHandler.class;

        assertThat(annotation.getName()).isEqualTo("org.springframework.web.bind.annotation.ExceptionHandler");
        assertThat(Stream.of(annotation.getMethods()).map(Method::getName)).contains("value");
    }
}
