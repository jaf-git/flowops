package com.flowops.workspace.api.exception;

import com.flowops.shared.web.ErrorResponse;
import com.flowops.workspace.application.assignfunctionalrole.UnknownFunctionalRoleException;
import com.flowops.workspace.application.manageorganisation.DepartmentHasRolesException;
import com.flowops.workspace.application.manageorganisation.DepartmentNameTakenException;
import com.flowops.workspace.application.manageorganisation.FunctionalRoleIsHeldException;
import com.flowops.workspace.application.manageorganisation.FunctionalRoleNameTakenException;
import com.flowops.workspace.application.manageorganisation.UnknownDepartmentException;
import com.flowops.workspace.application.shared.exception.ConfirmationNameMismatchException;
import com.flowops.workspace.application.shared.exception.ConsentNotGivenException;
import com.flowops.workspace.application.shared.exception.ConsentVersionStaleException;
import com.flowops.workspace.application.shared.exception.ExportLimitReachedException;
import com.flowops.workspace.application.shared.exception.InvitationAlreadyAcceptedException;
import com.flowops.workspace.application.shared.exception.InvitationNotUsableException;
import com.flowops.workspace.application.shared.exception.InvitationRefusedException;
import com.flowops.workspace.application.shared.exception.MemberNameRequiredException;
import com.flowops.workspace.application.shared.exception.MembershipNotFoundException;
import com.flowops.workspace.application.shared.exception.NotAuthenticatedException;
import com.flowops.workspace.application.shared.exception.OnlyOwnerException;
import com.flowops.workspace.application.shared.exception.OwnerNameRequiredException;
import com.flowops.workspace.application.shared.exception.PasswordUnacceptableException;
import com.flowops.workspace.application.shared.exception.ReauthenticationRequiredException;
import com.flowops.workspace.application.shared.exception.SetupNotPermittedException;
import com.flowops.workspace.application.shared.exception.SubjectNotDeactivatedException;
import com.flowops.workspace.application.shared.exception.UnknownSettingValueException;
import com.flowops.workspace.domain.exception.AnalysisThresholdInvalidException;
import com.flowops.workspace.domain.exception.AtRiskWindowInvalidException;
import com.flowops.workspace.domain.exception.CycleWouldFormException;
import com.flowops.workspace.domain.exception.EscalationIntervalsUnorderedException;
import com.flowops.workspace.domain.exception.ManagerNotEligibleException;
import com.flowops.workspace.domain.exception.NoWorkingDaysException;
import com.flowops.workspace.domain.exception.OwnerHasNoManagerException;
import com.flowops.workspace.domain.exception.QuietHoursCoverTheDayException;
import com.flowops.workspace.domain.exception.SelfManagerException;
import com.flowops.workspace.domain.exception.SubjectInactiveException;
import com.flowops.workspace.domain.exception.UnknownTimezoneException;
import com.flowops.workspace.domain.exception.WorkspaceNameRequiredException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.flowops.workspace")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WorkspaceExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(WorkspaceExceptionHandler.class);

    @ExceptionHandler(WorkspaceNameRequiredException.class)
    public ResponseEntity<ErrorResponse> onWorkspaceNameMissing(WorkspaceNameRequiredException failure) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(
                        "WORKSPACE_NAME_REQUIRED",
                        "Give the workspace a name.",
                        List.of(new ErrorResponse.FieldViolation("workspaceName", "REQUIRED"))));
    }

    @ExceptionHandler(OwnerNameRequiredException.class)
    public ResponseEntity<ErrorResponse> onOwnerNameMissing(OwnerNameRequiredException failure) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(
                        "OWNER_NAME_REQUIRED",
                        "Tell us what to call you.",
                        List.of(new ErrorResponse.FieldViolation("ownerName", "REQUIRED"))));
    }

    @ExceptionHandler(UnknownTimezoneException.class)
    public ResponseEntity<ErrorResponse> onUnknownTimezone(UnknownTimezoneException failure) {
        LOG.warn("Setup was offered a timezone this runtime does not know: {}", failure.offered());
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(
                        "TIMEZONE_UNKNOWN",
                        "That is not a timezone we recognise. Choose one from the list.",
                        List.of(new ErrorResponse.FieldViolation("timezone", "UNKNOWN"))));
    }

    @ExceptionHandler(SetupNotPermittedException.class)
    public ResponseEntity<ErrorResponse> onSetupNotPermitted(SetupNotPermittedException failure) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("SETUP_NOT_PERMITTED", "Setting this workspace up is not yours to do."));
    }

    @ExceptionHandler(UnknownFunctionalRoleException.class)
    public ResponseEntity<ErrorResponse> onUnknownFunctionalRole(UnknownFunctionalRoleException failure) {
        LOG.warn("A functional role was assigned that does not exist: {}", failure.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(
                        "UNKNOWN_FUNCTIONAL_ROLE",
                        "That job is not one of this workspace's roles.",
                        List.of(new ErrorResponse.FieldViolation("functionalRoleId", "UNKNOWN"))));
    }

    @ExceptionHandler(EscalationIntervalsUnorderedException.class)
    public ResponseEntity<ErrorResponse> onLadderUnordered(EscalationIntervalsUnorderedException failure) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(
                        "ESCALATION_INTERVALS_UNORDERED",
                        "Each escalation step must wait longer than the one before it.",
                        List.of(
                                new ErrorResponse.FieldViolation(
                                        "escalationIntervalsHours[" + failure.earlier() + "]", "OUT_OF_ORDER"),
                                new ErrorResponse.FieldViolation(
                                        "escalationIntervalsHours[" + failure.later() + "]", "OUT_OF_ORDER"))));
    }

    @ExceptionHandler(AtRiskWindowInvalidException.class)
    public ResponseEntity<ErrorResponse> onAtRiskWindowInvalid(AtRiskWindowInvalidException failure) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(
                        "AT_RISK_WINDOW_INVALID",
                        "The at-risk window is a number of hours greater than zero.",
                        List.of(new ErrorResponse.FieldViolation("atRiskWindowHours", "MUST_BE_POSITIVE"))));
    }

    @ExceptionHandler(AnalysisThresholdInvalidException.class)
    public ResponseEntity<ErrorResponse> onAnalysisThresholdInvalid(AnalysisThresholdInvalidException failure) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(
                        "ANALYSIS_THRESHOLD_INVALID",
                        "A coverage share is between 1 and 100, and an idleness window is greater than zero.",
                        List.of(new ErrorResponse.FieldViolation(failure.field(), "OUT_OF_RANGE"))));
    }

    @ExceptionHandler(QuietHoursCoverTheDayException.class)
    public ResponseEntity<ErrorResponse> onQuietHoursCoverTheDay(QuietHoursCoverTheDayException failure) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(
                        "QUIET_HOURS_COVER_THE_DAY",
                        "Quiet hours cannot cover the whole day, or nothing would ever be chased.",
                        List.of(
                                new ErrorResponse.FieldViolation("quietHoursStart", "COVERS_THE_DAY"),
                                new ErrorResponse.FieldViolation("quietHoursEnd", "COVERS_THE_DAY"))));
    }

    @ExceptionHandler(NoWorkingDaysException.class)
    public ResponseEntity<ErrorResponse> onNoWorkingDays(NoWorkingDaysException failure) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(
                        "NO_WORKING_DAYS",
                        "Choose at least one working day, or nothing can be measured.",
                        List.of(new ErrorResponse.FieldViolation("workingDays", "AT_LEAST_ONE"))));
    }

    @ExceptionHandler(CycleWouldFormException.class)
    ResponseEntity<ErrorResponse> onCycle(CycleWouldFormException failure) {
        LOG.info("reporting line refused: CYCLE");
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(
                        "CYCLE",
                        failure.getMessage(),
                        failure.path().stream()
                                .map(step -> new ErrorResponse.FieldViolation(
                                        "step", step.value().toString()))
                                .toList()));
    }

    @ExceptionHandler(OwnerHasNoManagerException.class)
    ResponseEntity<ErrorResponse> onOwnerHasNoManager(OwnerHasNoManagerException failure) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("OWNER_HAS_NO_MANAGER", failure.getMessage()));
    }

    @ExceptionHandler(SelfManagerException.class)
    ResponseEntity<ErrorResponse> onSelfManager(SelfManagerException failure) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("SELF_MANAGER", failure.getMessage()));
    }

    @ExceptionHandler(SubjectInactiveException.class)
    ResponseEntity<ErrorResponse> onSubjectInactive(SubjectInactiveException failure) {
        LOG.info("reporting line refused: SUBJECT_INACTIVE");
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("SUBJECT_INACTIVE", failure.getMessage()));
    }

    @ExceptionHandler(ManagerNotEligibleException.class)
    ResponseEntity<ErrorResponse> onManagerNotEligible(ManagerNotEligibleException failure) {
        LOG.info("reporting line refused: {}", failure.code());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(failure.code(), failure.getMessage()));
    }

    @ExceptionHandler(PasswordUnacceptableException.class)
    public ResponseEntity<ErrorResponse> passwordUnacceptable(PasswordUnacceptableException refused) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(
                        "PASSWORD_POLICY_VIOLATION",
                        refused.getMessage(),
                        refused.rules().stream()
                                .map(rule -> new ErrorResponse.FieldViolation("password", rule))
                                .toList()));
    }

    @ExceptionHandler(MemberNameRequiredException.class)
    public ResponseEntity<ErrorResponse> memberNameRequired(MemberNameRequiredException refused) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("DISPLAY_NAME_REQUIRED", refused.getMessage(), List.of()));
    }

    @ExceptionHandler(ConsentNotGivenException.class)
    public ResponseEntity<ErrorResponse> consentNotGiven(ConsentNotGivenException refused) {
        return ResponseEntity.badRequest().body(new ErrorResponse("CONSENT_REQUIRED", refused.getMessage(), List.of()));
    }

    @ExceptionHandler(DepartmentNameTakenException.class)
    public ResponseEntity<ErrorResponse> departmentNameTaken(DepartmentNameTakenException refused) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        "DEPARTMENT_NAME_TAKEN", "A part of the business already goes by that name.", List.of()));
    }

    @ExceptionHandler(FunctionalRoleNameTakenException.class)
    public ResponseEntity<ErrorResponse> roleNameTaken(FunctionalRoleNameTakenException refused) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("FUNCTIONAL_ROLE_NAME_TAKEN", "A job already goes by that name.", List.of()));
    }

    @ExceptionHandler(UnknownDepartmentException.class)
    public ResponseEntity<ErrorResponse> unknownDepartment(UnknownDepartmentException refused) {
        return ResponseEntity.unprocessableEntity()
                .body(new ErrorResponse(
                        "UNKNOWN_DEPARTMENT",
                        "That part of the business does not exist.",
                        List.of(new ErrorResponse.FieldViolation("departmentId", "UNKNOWN"))));
    }

    @ExceptionHandler(FunctionalRoleIsHeldException.class)
    public ResponseEntity<ErrorResponse> roleIsHeld(FunctionalRoleIsHeldException refused) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        "FUNCTIONAL_ROLE_IS_HELD",
                        refused.held() + " person(s) still hold that job. Move them to another one first.",
                        List.of()));
    }

    @ExceptionHandler(DepartmentHasRolesException.class)
    public ResponseEntity<ErrorResponse> departmentHasRoles(DepartmentHasRolesException refused) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        "DEPARTMENT_HAS_ROLES",
                        refused.roles() + " job(s) still sit in that part of the business. Move or remove them first.",
                        List.of()));
    }

    @ExceptionHandler(ConsentVersionStaleException.class)
    public ResponseEntity<ErrorResponse> consentVersionStale(ConsentVersionStaleException refused) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("CONSENT_VERSION_STALE", refused.getMessage(), List.of()));
    }

    @ExceptionHandler(InvitationNotUsableException.class)
    ResponseEntity<ErrorResponse> onInvitationNotUsable(InvitationNotUsableException failure) {
        return ResponseEntity.status(HttpStatus.GONE)
                .body(ErrorResponse.of("INVITATION_NOT_USABLE", failure.getMessage()));
    }

    @ExceptionHandler(MembershipNotFoundException.class)
    ResponseEntity<ErrorResponse> onMembershipNotFound(MembershipNotFoundException failure) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("MEMBERSHIP_NOT_FOUND", failure.getMessage()));
    }

    @ExceptionHandler(NotAuthenticatedException.class)
    public ResponseEntity<ErrorResponse> onNotAuthenticated(NotAuthenticatedException failure) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("NOT_AUTHENTICATED", "Sign in to continue."));
    }

    @ExceptionHandler(InvitationRefusedException.class)
    public ResponseEntity<ErrorResponse> onInvitationRefused(InvitationRefusedException failure) {
        HttpStatus status = statusFor(failure);
        LOG.info("invitation refused: {}", failure.code());

        if (failure instanceof InvitationAlreadyAcceptedException accepted) {
            return ResponseEntity.status(status)
                    .body(ErrorResponse.of(
                            failure.code(),
                            failure.getMessage(),
                            List.of(new ErrorResponse.FieldViolation("member", accepted.memberName()))));
        }

        return ResponseEntity.status(status).body(ErrorResponse.of(failure.code(), failure.getMessage()));
    }

    private HttpStatus statusFor(InvitationRefusedException failure) {
        return switch (failure.code()) {
            case "ALREADY_MEMBER", "DEACTIVATED_MEMBER", "DUPLICATE_INVITATION", "ALREADY_ACCEPTED" -> HttpStatus
                    .CONFLICT;
            case "INVITATION_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "NOT_YOURS" -> HttpStatus.FORBIDDEN;
            case "RATE_LIMIT" -> HttpStatus.TOO_MANY_REQUESTS;
            case "DECLINE_WINDOW", "MANAGER_INACTIVE", "SELF_INVITATION" -> HttpStatus.UNPROCESSABLE_ENTITY;
            case "ROLE_CEILING" -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ErrorResponse> onAuthorizationDenied(AuthorizationDeniedException failure) {
        LOG.info("workspace action refused: the caller lacks the required permission");
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("NOT_PERMITTED", "You may not perform this action."));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> onUnreadableBody(HttpMessageNotReadableException failure) {
        LOG.info("workspace request rejected: the body could not be read");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("MALFORMED_BODY", "That request body could not be read."));
    }

    @ExceptionHandler(ReauthenticationRequiredException.class)
    public ResponseEntity<ErrorResponse> onReauthenticationRequired(ReauthenticationRequiredException failure) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("REAUTHENTICATION_REQUIRED", "Confirm your password before erasing somebody."));
    }

    @ExceptionHandler(ExportLimitReachedException.class)
    public ResponseEntity<ErrorResponse> onExportLimitReached(ExportLimitReachedException failure) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ErrorResponse.of(
                        "EXPORT_LIMIT", "You have taken several copies already today. Try again tomorrow."));
    }

    @ExceptionHandler(UnknownSettingValueException.class)
    public ResponseEntity<ErrorResponse> onUnknownSettingValue(UnknownSettingValueException failure) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(
                        "REQUEST_INVALID",
                        "One of the values sent is not one we recognise.",
                        List.of(new ErrorResponse.FieldViolation(failure.field(), "NOT_RECOGNISED"))));
    }

    @ExceptionHandler(SubjectNotDeactivatedException.class)
    public ResponseEntity<ErrorResponse> onSubjectStillActive(SubjectNotDeactivatedException failure) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(
                        "SUBJECT_ACTIVE", "End this person's access first. Erasing somebody takes two steps."));
    }

    @ExceptionHandler(ConfirmationNameMismatchException.class)
    public ResponseEntity<ErrorResponse> onTypedNameMismatch(ConfirmationNameMismatchException failure) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(
                        "NAME_MISMATCH",
                        "That is not their name. Nothing was destroyed.",
                        List.of(new ErrorResponse.FieldViolation("typedName", "DOES_NOT_MATCH"))));
    }

    @ExceptionHandler(OnlyOwnerException.class)
    ResponseEntity<ErrorResponse> onOnlyOwner(OnlyOwnerException failure) {
        LOG.info("deactivation refused: ONLY_OWNER");
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("ONLY_OWNER", failure.getMessage()));
    }
}
