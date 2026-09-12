package com.flowops.auth.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowops.auth.domain.exception.InvalidEmailAddressException;
import org.junit.jupiter.api.Test;

class EmailAddressTest {
    @Test
    void anAddressIsNormalisedToLowerCaseSoOneAccountCannotBeCreatedTwice() {
        EmailAddress email = new EmailAddress("Founder@FlowOps.Test");

        assertThat(email.value()).isEqualTo("founder@flowops.test");
    }

    @Test
    void surroundingWhitespaceIsTrimmed() {
        EmailAddress email = new EmailAddress("  founder@flowops.test  ");

        assertThat(email.value()).isEqualTo("founder@flowops.test");
    }

    @Test
    void theLocalPartIsTheSegmentBeforeTheAt() {
        EmailAddress email = new EmailAddress("founder@flowops.test");

        assertThat(email.localPart()).isEqualTo("founder");
    }

    @Test
    void anAddressWithoutAnAtIsRefused() {
        assertThatThrownBy(() -> new EmailAddress("founder.flowops.test"))
                .isInstanceOf(InvalidEmailAddressException.class);
    }

    @Test
    void theRefusalDoesNotEchoTheAddressBecauseItIsPersonalData() {
        assertThatThrownBy(() -> new EmailAddress("not-an-address"))
                .isInstanceOf(InvalidEmailAddressException.class)
                .hasMessageNotContaining("not-an-address");
    }
}
