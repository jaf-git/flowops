package com.flowops.nodepipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.nodepipeline.application.MeasureJobElapsed;
import com.flowops.nodepipeline.application.port.WaitReadPort;
import com.flowops.nodepipeline.domain.wait.BracketClose;
import com.flowops.nodepipeline.domain.wait.JobElapsed;
import com.flowops.nodepipeline.domain.wait.WaitKind;
import com.flowops.nodepipeline.domain.wait.WaitSpan;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TheClockSeparatesOurTimeFromTheClientsTest {
    private static final Instant OPENED = Instant.parse("2026-06-01T00:00:00Z");
    private static final UUID JOB = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final Instant ASKED_AT = Instant.parse("2026-07-01T00:00:00Z");

    @Test
    void nineteenDaysElevenOfThemWaitingOnTheClient() {
        JobElapsed measured = measure(
                closedAfter(19), clientWait(3, 9, BracketClose.DELIVERED), clientWait(9, 14, BracketClose.DELIVERED));

        assertThat(measured.totalDays()).isEqualTo(19);
        assertThat(measured.externalWaitDays()).isEqualTo(11);
        assertThat(measured.workingDays()).isEqualTo(8);
        assertThat(measured.daysWaitingByKind()).containsEntry(WaitKind.CLIENT, 11L);
    }

    @Test
    void aHandoverSatisfiesNoWaitSoTheClientIsStillWaiting() {
        JobElapsed measured = measure(closedAfter(19), clientWait(3, 5, BracketClose.HANDED_OVER));

        assertThat(measured.externalWaitDays()).isEqualTo(16);
        assertThat(measured.workingDays()).isEqualTo(3);
    }

    @Test
    void aCadenceCloseSatisfiesNoWaitEither() {
        JobElapsed measured = measure(closedAfter(19), clientWait(3, 5, BracketClose.CADENCE_CLOSED));

        assertThat(measured.externalWaitDays()).isEqualTo(16);
    }

    @Test
    void deliveredAndDoneBothSatisfyAWait() {
        assertThat(measure(closedAfter(19), clientWait(3, 5, BracketClose.DELIVERED))
                        .externalWaitDays())
                .isEqualTo(2);
        assertThat(measure(closedAfter(19), clientWait(3, 5, BracketClose.DONE)).externalWaitDays())
                .isEqualTo(2);
    }

    @Test
    void onlyClientAndSupplierWaitsLeaveTheWorkingClock() {
        JobElapsed measured = measure(
                closedAfter(19),
                clientWait(2, 5, BracketClose.DELIVERED),
                wait(WaitKind.SUPPLIER, 6, 8, BracketClose.DELIVERED),
                wait(WaitKind.COLLEAGUE, 10, 12, BracketClose.DELIVERED),
                wait(WaitKind.APPROVAL, 12, 14, BracketClose.DONE));

        assertThat(measured.externalWaitDays()).isEqualTo(5);
        assertThat(measured.workingDays()).isEqualTo(14);
        assertThat(measured.daysWaitingByKind())
                .containsEntry(WaitKind.CLIENT, 3L)
                .containsEntry(WaitKind.SUPPLIER, 2L)
                .containsEntry(WaitKind.COLLEAGUE, 2L)
                .containsEntry(WaitKind.APPROVAL, 2L);
    }

    @Test
    void overlappingWaitsAreCountedOnce() {
        JobElapsed measured = measure(
                closedAfter(19),
                clientWait(2, 7, BracketClose.DELIVERED),
                wait(WaitKind.SUPPLIER, 2, 7, BracketClose.DELIVERED));

        assertThat(measured.externalWaitDays()).isEqualTo(5);
        assertThat(measured.workingDays()).isEqualTo(14);
    }

    @Test
    void anOpenWaitStopsWhereTheEngagementDoes() {
        JobElapsed measured = measure(closedAfter(19), stillOpenClientWaitFrom(4));

        assertThat(measured.totalDays()).isEqualTo(19);
        assertThat(measured.externalWaitDays()).isEqualTo(15);
    }

    @Test
    void aRunningEngagementIsMeasuredToNow() {
        JobElapsed measured = measure(new WaitReadPort.JobWindow(JOB, OPENED, null), stillOpenClientWaitFrom(4));

        assertThat(measured.totalDays()).isEqualTo(30);
        assertThat(measured.externalWaitDays()).isEqualTo(26);
    }

    @Test
    void anEngagementThatNeverWaitedIsAllWorkingDays() {
        JobElapsed measured = measure(closedAfter(19));

        assertThat(measured.totalDays()).isEqualTo(19);
        assertThat(measured.externalWaitDays()).isZero();
        assertThat(measured.workingDays()).isEqualTo(19);
        assertThat(measured.daysWaitingByKind()).containsOnlyKeys(WaitKind.values());
    }

    @Test
    void anEngagementThatDoesNotExistIsAbsent() {
        MeasureJobElapsed measure = new MeasureJobElapsed(new StubWaits(null, List.of()), fixedClock());

        assertThat(measure.of(JOB)).isEmpty();
    }

    private static JobElapsed measure(WaitReadPort.JobWindow window, WaitSpan... waits) {
        return new MeasureJobElapsed(new StubWaits(window, List.of(waits)), fixedClock())
                .of(JOB)
                .orElseThrow();
    }

    private static Clock fixedClock() {
        return Clock.fixed(ASKED_AT, ZoneOffset.UTC);
    }

    private static WaitReadPort.JobWindow closedAfter(int days) {
        return new WaitReadPort.JobWindow(JOB, OPENED, day(days));
    }

    private static WaitSpan clientWait(int fromDay, int toDay, BracketClose ending) {
        return wait(WaitKind.CLIENT, fromDay, toDay, ending);
    }

    private static WaitSpan wait(WaitKind kind, int fromDay, int toDay, BracketClose ending) {
        return new WaitSpan(kind, day(fromDay), day(toDay), null, ending, day(toDay));
    }

    private static WaitSpan stillOpenClientWaitFrom(int fromDay) {
        return new WaitSpan(WaitKind.CLIENT, day(fromDay), null, null, null, null);
    }

    private static Instant day(int day) {
        return OPENED.plus(Duration.ofDays(day));
    }

    private record StubWaits(WaitReadPort.JobWindow window, List<WaitSpan> waits) implements WaitReadPort {
        @Override
        public Optional<WaitReadPort.JobWindow> windowOf(UUID jobId) {
            return Optional.ofNullable(window);
        }

        @Override
        public List<WaitSpan> waitsIn(UUID jobId) {
            return new ArrayList<>(waits);
        }
    }
}
