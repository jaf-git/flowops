package com.flowops.discovery.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.discovery.domain.enums.CloseKind;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class WaitResolutionTest {
    @ParameterizedTest
    @EnumSource(
            value = CloseKind.class,
            names = {"DELIVERED", "DONE"})
    @DisplayName("only a real completion releases somebody who was waiting")
    void completionsRelease(CloseKind completion) {
        assertThat(WaitResolution.of(completion)).isEqualTo(WaitResolution.SATISFIED);
    }

    @ParameterizedTest
    @EnumSource(
            value = CloseKind.class,
            names = {"DROPPED", "LAPSED", "PARENT_CLOSED", "OVERRIDE", "CADENCE_CLOSED", "JOB_END"})
    @DisplayName("every other ending kills the wait rather than satisfying it")
    void nonCompletionsKill(CloseKind ending) {
        assertThat(WaitResolution.of(ending)).isEqualTo(WaitResolution.DIED);
    }

    @Test
    @DisplayName("a handover neither releases nor kills — the work has moved, not arrived")
    void handoverReTargets() {
        assertThat(WaitResolution.of(CloseKind.HANDED_OVER)).isEqualTo(WaitResolution.RE_TARGETS);
    }

    @Test
    @DisplayName("a merge re-targets too — the work folded into another bracket, it did not die")
    void mergeReTargets() {
        assertThat(WaitResolution.of(CloseKind.MERGED)).isEqualTo(WaitResolution.RE_TARGETS);
    }

    @Test
    @DisplayName("exactly two of the ten close kinds may ever release a waiter")
    void onlyTwoKindsRelease() {
        long releasing = Arrays.stream(CloseKind.values())
                .filter(kind -> WaitResolution.of(kind) == WaitResolution.SATISFIED)
                .count();

        assertThat(releasing)
                .describedAs("D6 admits DELIVERED and DONE and nothing else. A third releasing kind would "
                        + "measure every downstream duration from a moment nothing was delivered, "
                        + "and no test or screen would show it.")
                .isEqualTo(2);
    }

    @ParameterizedTest
    @EnumSource(CloseKind.class)
    @DisplayName("CloseKind and WaitResolution agree about what a completion is")
    void theTwoTypesAgree(CloseKind kind) {
        boolean releases = WaitResolution.of(kind) == WaitResolution.SATISFIED;

        assertThat(releases).isEqualTo(kind.satisfiesAWait());
    }
}
