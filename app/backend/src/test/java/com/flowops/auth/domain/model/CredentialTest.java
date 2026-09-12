package com.flowops.auth.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.auth.domain.service.PasswordHasher;
import com.flowops.auth.domain.service.ReversibleHasher;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CredentialTest {
    private static final UserId USER_ID = UserId.generate();
    private static final Instant UPDATED_AT = Instant.parse("2026-08-02T09:00:00Z");

    private final PasswordHasher hasher = new ReversibleHasher();

    @Test
    void theRawPasswordIsNeverStored() {
        Credential credential = Credential.issue(USER_ID, "correcthorsebattery", hasher, UPDATED_AT);

        assertThat(credential.passwordHash()).isNotEqualTo("correcthorsebattery");
    }

    @Test
    void theAlgorithmIsRecordedAlongsideTheHash() {
        Credential credential = Credential.issue(USER_ID, "correcthorsebattery", hasher, UPDATED_AT);

        assertThat(credential.algorithm()).isEqualTo(hasher.algorithm());
    }

    @Test
    void theCorrectPasswordMatches() {
        Credential credential = Credential.issue(USER_ID, "correcthorsebattery", hasher, UPDATED_AT);

        assertThat(credential.matches("correcthorsebattery", hasher)).isTrue();
    }

    @Test
    void aWrongPasswordDoesNotMatch() {
        Credential credential = Credential.issue(USER_ID, "correcthorsebattery", hasher, UPDATED_AT);

        assertThat(credential.matches("something-else", hasher)).isFalse();
    }

    @Test
    void replacingTheSecretKeepsTheOwnerAndMovesTheTimestamp() {
        Credential credential = Credential.issue(USER_ID, "correcthorsebattery", hasher, UPDATED_AT);

        Credential replaced = credential.replaceWith("a-different-one", hasher, UPDATED_AT.plusSeconds(60));

        assertThat(replaced.userId()).isEqualTo(USER_ID);
        assertThat(replaced.matches("a-different-one", hasher)).isTrue();
        assertThat(replaced.updatedAt()).isEqualTo(UPDATED_AT.plusSeconds(60));
    }
}
