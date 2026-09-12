package com.flowops.notification.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.notification.application.inbox.MarkReadUseCase;
import com.flowops.notification.application.inbox.ViewInboxUseCase;
import com.flowops.notification.application.preferences.PreferencesUseCase;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestParam;

class NoRouteServesAnotherPersonsInboxTest {
    private static final List<Class<?>> THE_SURFACE = List.of(
            NotificationController.class, ViewInboxUseCase.class, MarkReadUseCase.class, PreferencesUseCase.class);

    private static final List<String> A_PERSON =
            List.of("person", "user", "recipient", "assignee", "member", "employee", "report", "colleague");

    @Test
    @DisplayName("no method on the surface accepts a person")
    void theAbsenceIsTheEnforcement() {
        for (Class<?> type : THE_SURFACE) {
            for (Method method : type.getDeclaredMethods()) {
                Arrays.stream(method.getParameters()).forEach(parameter -> {
                    String name = parameter.getName().toLowerCase(java.util.Locale.ROOT);
                    boolean namesAPerson = A_PERSON.stream().anyMatch(name::contains);
                    assertThat(namesAPerson)
                            .as(
                                    "%s.%s takes a parameter called '%s'. An inbox read by somebody else is a"
                                            + " performance history containing only the moments something went"
                                            + " wrong (NOTIFICATION_02 §2)",
                                    type.getSimpleName(), method.getName(), parameter.getName())
                            .isFalse();
                });
            }
        }
    }

    @Test
    @DisplayName("no route takes a query parameter of any kind")
    void thereIsNothingToFilterBy() {
        for (Method method : NotificationController.class.getDeclaredMethods()) {
            for (var parameter : method.getParameters()) {
                assertThat(parameter.isAnnotationPresent(RequestParam.class))
                        .as("%s accepts a query parameter", method.getName())
                        .isFalse();
            }
        }
    }

    @Test
    @DisplayName("there is no route that marks everything read")
    void markAllReadDoesNotExist() {
        boolean marksEverything = Arrays.stream(NotificationController.class.getDeclaredMethods())
                .map(method -> method.getName().toLowerCase(java.util.Locale.ROOT))
                .anyMatch(name -> name.contains("all") || name.contains("clear") || name.contains("dismiss"));

        assertThat(marksEverything).isFalse();
    }

    @Test
    @DisplayName("the one identifier a route accepts is a notification, never a person")
    void theOnlyPathVariableIsANotification() {
        long identifiers = Arrays.stream(NotificationController.class.getDeclaredMethods())
                .flatMap(method -> Arrays.stream(method.getParameters()))
                .filter(parameter -> parameter.getType().equals(UUID.class))
                .count();

        assertThat(identifiers)
                .as("only POST /notifications/{id}/read takes an identifier, and it is the notification's")
                .isEqualTo(1);
    }
}
