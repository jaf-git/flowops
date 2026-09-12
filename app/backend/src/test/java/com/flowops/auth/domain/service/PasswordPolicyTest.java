package com.flowops.auth.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.auth.domain.enums.PasswordRule;
import com.flowops.auth.domain.model.EmailAddress;
import java.util.List;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {
    private static final EmailAddress EMAIL = new EmailAddress("founder@flowops.test");

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void aPasswordOfTheMinimumLengthIsAccepted() {
        List<PasswordRule> violations = policy.violationsFor("twelvechars!", EMAIL);

        assertThat(violations).isEmpty();
    }

    @Test
    void aPasswordShorterThanTheMinimumNamesTheLengthRule() {
        List<PasswordRule> violations = policy.violationsFor("elevenchar", EMAIL);

        assertThat(violations).containsExactly(PasswordRule.MINIMUM_LENGTH);
    }

    @Test
    void aPasswordLongerThanTheMaximumNamesTheLengthRule() {
        List<PasswordRule> violations = policy.violationsFor("x".repeat(129), EMAIL);

        assertThat(violations).containsExactly(PasswordRule.MAXIMUM_LENGTH);
    }

    @Test
    void aPasswordEqualToTheEmailLocalPartIsRejected() {
        List<PasswordRule> violations =
                policy.violationsFor("averylongfounder", new EmailAddress("averylongfounder@flowops.test"));

        assertThat(violations).containsExactly(PasswordRule.NOT_EMAIL_LOCAL_PART);
    }

    @Test
    void theEmailLocalPartIsComparedWithoutRegardToCase() {
        List<PasswordRule> violations =
                policy.violationsFor("AVeryLongFounder", new EmailAddress("averylongfounder@flowops.test"));

        assertThat(violations).containsExactly(PasswordRule.NOT_EMAIL_LOCAL_PART);
    }

    @Test
    void noCompositionRuleIsImposed() {
        List<PasswordRule> violations = policy.violationsFor("correcthorsebatterystaple", EMAIL);

        assertThat(violations).isEmpty();
    }

    @Test
    void aNewPasswordIdenticalToTheCurrentOneIsRejectedOnChange() {
        List<PasswordRule> violations = policy.violationsForChange("thecurrentone!", EMAIL, true);

        assertThat(violations).containsExactly(PasswordRule.NOT_CURRENT_PASSWORD);
    }

    @Test
    void aNewPasswordDifferentFromTheCurrentOneIsAcceptedOnChange() {
        List<PasswordRule> violations = policy.violationsForChange("somethingelse!", EMAIL, false);

        assertThat(violations).isEmpty();
    }
}
