package com.flowops.workspace.infrastructure.token;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.workspace.application.shared.port.GenerateInvitationTokenPort.MintedToken;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("AUTH-ACCEPT-INVITE-01")
class InvitationTokenGeneratorTest {
    private final InvitationTokenGenerator generator = new InvitationTokenGenerator();

    @Test
    void hashingTheTokenThatWasMintedGivesTheHashThatWasStored() {
        MintedToken minted = generator.mint();

        assertThat(generator.hash(minted.token().value()))
                .as("what acceptance computes must equal what invitation stored")
                .isEqualTo(minted.hash());
    }

    @Test
    void theSameTokenAlwaysHashesToTheSameValue() {
        MintedToken minted = generator.mint();

        assertThat(generator.hash(minted.token().value()))
                .isEqualTo(generator.hash(minted.token().value()));
    }

    @Test
    void twoMintedTokensDifferAndSoDoTheirHashes() {
        MintedToken first = generator.mint();
        MintedToken second = generator.mint();

        assertThat(first.token().value()).isNotEqualTo(second.token().value());
        assertThat(first.hash()).isNotEqualTo(second.hash());
    }

    @Test
    void theHashIsLowercaseHexAndNothingElse() {
        assertThat(generator.mint().hash()).matches("[0-9a-f]{64}");
    }
}
