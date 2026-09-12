package com.flowops.workspace.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.workspace.WorkspaceIntegrationTest;
import com.flowops.workspace.application.shared.port.GenerateInvitationTokenPort;
import com.flowops.workspace.application.shared.port.LoadInvitationPort;
import com.flowops.workspace.application.shared.port.LoadMembershipPort;
import com.flowops.workspace.application.shared.port.LoadWorkspacePort;
import com.flowops.workspace.application.shared.port.SaveInvitationPort;
import com.flowops.workspace.domain.enums.InvitationState;
import com.flowops.workspace.domain.enums.InvitedRole;
import com.flowops.workspace.domain.model.EmailAddress;
import com.flowops.workspace.domain.model.Invitation;
import com.flowops.workspace.domain.model.InvitationId;
import com.flowops.workspace.domain.model.MembershipId;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("AUTH-ACCEPT-INVITE-01")
class InvitationLockTest extends WorkspaceIntegrationTest {
    private static final int SECONDS_BEFORE_GIVING_UP = 10;

    @Autowired
    private SaveInvitationPort saveInvitation;

    @Autowired
    private LoadInvitationPort loadInvitation;

    @Autowired
    private LoadWorkspacePort loadWorkspace;

    @Autowired
    private LoadMembershipPort loadMembership;

    @Autowired
    private GenerateInvitationTokenPort tokens;

    @Autowired
    private TransactionTemplate transactions;

    @Test
    void theLockedLookupFindsTheInvitationTheTokenBelongsTo() throws Exception {
        GenerateInvitationTokenPort.MintedToken minted = tokens.mint();
        InvitationId saved = anInvitationStoredWith(minted.hash());

        Optional<Invitation> found = transactions.execute(status -> loadInvitation.findByTokenHashForUpdate(
                tokens.hash(minted.token().value())));

        assertThat(found).isPresent();
        assertThat(found.orElseThrow().id()).isEqualTo(saved);
    }

    @Test
    void aTokenThatMatchesNothingLocksNothing() throws Exception {
        anInvitationStoredWith(tokens.mint().hash());

        Optional<Invitation> found = transactions.execute(status -> loadInvitation.findByTokenHashForUpdate(
                tokens.hash(tokens.mint().token().value())));

        assertThat(found).isEmpty();
    }

    @Test
    void theSecondTransactionCannotReadTheInvitationUntilTheFirstHasCommitted() throws Exception {
        GenerateInvitationTokenPort.MintedToken minted = tokens.mint();
        anInvitationStoredWith(minted.hash());
        String hash = tokens.hash(minted.token().value());

        CountDownLatch firstHoldsTheLock = new CountDownLatch(1);
        CountDownLatch secondIsAboutToRead = new CountDownLatch(1);
        ExecutorService threads = Executors.newSingleThreadExecutor();
        try {
            Future<?> first = threads.submit(() -> transactions.execute(status -> {
                Invitation locked =
                        loadInvitation.findByTokenHashForUpdate(hash).orElseThrow();
                firstHoldsTheLock.countDown();
                awaitQuietly(secondIsAboutToRead);

                sleepQuietly();
                return saveInvitation.save(locked.acceptAt(Instant.now()));
            }));

            assertThat(firstHoldsTheLock.await(SECONDS_BEFORE_GIVING_UP, TimeUnit.SECONDS))
                    .as("the first transaction never acquired the lock")
                    .isTrue();
            secondIsAboutToRead.countDown();

            InvitationState seenBySecond = transactions.execute(status ->
                    loadInvitation.findByTokenHashForUpdate(hash).orElseThrow().state());

            first.get(SECONDS_BEFORE_GIVING_UP, TimeUnit.SECONDS);
            assertThat(seenBySecond)
                    .as("the second reader saw the invitation before the first transaction had finished with it")
                    .isEqualTo(InvitationState.ACCEPTED);
        } finally {
            threads.shutdownNow();
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(SECONDS_BEFORE_GIVING_UP, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void sleepQuietly() {
        try {
            TimeUnit.MILLISECONDS.sleep(500);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private InvitationId anInvitationStoredWith(String tokenHash) throws Exception {
        setUpTheWorkspace(anOwnerSignedIn());
        Instant now = Instant.now();
        MembershipId owner = loadMembership.listAll().getFirst().id();

        Invitation invitation = new Invitation(
                InvitationId.of(UUID.randomUUID()),
                loadWorkspace.load().id(),
                EmailAddress.of("cosmin@atelier.ro"),
                InvitedRole.EMPLOYEE,
                owner,
                owner,
                InvitationState.SENT,
                tokenHash,
                now.plus(7, ChronoUnit.DAYS),
                now,
                null);
        return saveInvitation.save(invitation).id();
    }
}
