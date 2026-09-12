package com.flowops.task.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.support.RoundTripClient;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("AUTH_REQ_BLOCKER_25")
class SessionAuthoritiesAreLiveRoundTripTest extends TaskScenarioTest {
    @Test
    void aPermissionGrantedAfterSigningInIsUsableWithoutSigningInAgain() throws Exception {
        buildTheCompany();
        jdbc.update("delete from auth_role_permission where role_name = 'MANAGER'"
                + " and permission_name = 'TASK_REVIEW'");
        try {
            RoundTripClient ionut = signedInBrowser("ionut@atelier.ro");
            assertThat(ionut.get("/api/tasks/review-queue").getStatusCode())
                    .as("he does not hold it yet, so the door is shut")
                    .isEqualTo(HttpStatus.FORBIDDEN);

            jdbc.update("insert into auth_role_permission (role_name, permission_name)"
                    + " values ('MANAGER', 'TASK_REVIEW')");

            ResponseEntity<String> afterTheGrant = ionut.get("/api/tasks/review-queue");

            assertThat(afterTheGrant.getStatusCode())
                    .as("the same session, one request later, with the permission his role now holds")
                    .isEqualTo(HttpStatus.OK);
        } finally {
            jdbc.update("insert into auth_role_permission (role_name, permission_name)"
                    + " select 'MANAGER', 'TASK_REVIEW' where not exists (select 1 from"
                    + " auth_role_permission where role_name = 'MANAGER' and permission_name = 'TASK_REVIEW')");
        }
    }

    @Test
    void aPermissionWithdrawnStopsWorkingOnTheNextRequest() throws Exception {
        buildTheCompany();
        RoundTripClient ionut = signedInBrowser("ionut@atelier.ro");
        assertThat(ionut.get("/api/tasks/review-queue").getStatusCode())
                .as("he holds it while he signs in")
                .isEqualTo(HttpStatus.OK);

        jdbc.update("delete from auth_role_permission where role_name = 'MANAGER'"
                + " and permission_name = 'TASK_REVIEW'");
        try {
            ResponseEntity<String> afterTheRevocation = ionut.get("/api/tasks/review-queue");

            assertThat(afterTheRevocation.getStatusCode())
                    .as("revocation is immediate, not eventual")
                    .isEqualTo(HttpStatus.FORBIDDEN);
        } finally {
            jdbc.update("insert into auth_role_permission (role_name, permission_name)"
                    + " values ('MANAGER', 'TASK_REVIEW')");
        }
    }
}
