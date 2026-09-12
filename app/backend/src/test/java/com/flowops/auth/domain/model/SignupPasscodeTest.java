package com.flowops.auth.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.auth.domain.service.PasswordHasher;
import com.flowops.auth.domain.service.ReversibleHasher;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SignupPasscodeTest {
    private static final EmailAddress EMAIL = new EmailAddress("founder@flowops.test");
    private static final Instant ISSUED_AT = Instant.parse("2026-08-02T09:00:00Z");
    private static final Duration LIFETIME = Duration.ofMinutes(10);
    private static final int CEILING = 5;

    private final PasswordHasher hasher = new ReversibleHasher();

    @Test
    void anIssuedPasscodeStoresTheCodeHashedAndNotInClear() {
        SignupPasscode passcode = SignupPasscode.issue(EMAIL, "123456", hasher, ISSUED_AT, LIFETIME);

        assertThat(passcode.codeHash()).isNotEqualTo("123456");
    }

    @Test
    void anIssuedPasscodeExpiresAfterItsLifetime() {
        SignupPasscode passcode = SignupPasscode.issue(EMAIL, "123456", hasher, ISSUED_AT, LIFETIME);

        assertThat(passcode.isExpired(ISSUED_AT.plus(LIFETIME).plusSeconds(1))).isTrue();
    }

    @Test
    void anIssuedPasscodeIsLiveWithinItsLifetime() {
        SignupPasscode passcode = SignupPasscode.issue(EMAIL, "123456", hasher, ISSUED_AT, LIFETIME);

        assertThat(passcode.isExpired(ISSUED_AT.plusSeconds(30))).isFalse();
    }

    @Test
    void theCorrectCodeMatches() {
        SignupPasscode passcode = SignupPasscode.issue(EMAIL, "123456", hasher, ISSUED_AT, LIFETIME);

        assertThat(passcode.matches("123456", hasher)).isTrue();
    }

    @Test
    void aWrongCodeDoesNotMatch() {
        SignupPasscode passcode = SignupPasscode.issue(EMAIL, "123456", hasher, ISSUED_AT, LIFETIME);

        assertThat(passcode.matches("654321", hasher)).isFalse();
    }

    @Test
    void aUsedPasscodeCannotBeUsedAgain() {
        SignupPasscode passcode = SignupPasscode.issue(EMAIL, "123456", hasher, ISSUED_AT, LIFETIME);

        SignupPasscode used = passcode.markUsed();

        assertThat(used.isUsable(ISSUED_AT.plusSeconds(30), CEILING)).isFalse();
    }

    @Test
    void thePasscodeLocksOnceTheFailureCeilingIsReached() {
        SignupPasscode passcode = SignupPasscode.issue(EMAIL, "123456", hasher, ISSUED_AT, LIFETIME);

        SignupPasscode exhausted = passcode.recordFailure()
                .recordFailure()
                .recordFailure()
                .recordFailure()
                .recordFailure();

        assertThat(exhausted.isLocked(CEILING)).isTrue();
    }

    @Test
    void thePasscodeIsNotLockedBelowTheCeiling() {
        SignupPasscode passcode = SignupPasscode.issue(EMAIL, "123456", hasher, ISSUED_AT, LIFETIME);

        SignupPasscode failedOnce = passcode.recordFailure();

        assertThat(failedOnce.isLocked(CEILING)).isFalse();
    }
}
