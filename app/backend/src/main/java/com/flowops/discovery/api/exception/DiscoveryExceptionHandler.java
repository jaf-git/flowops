package com.flowops.discovery.api.exception;

import com.flowops.discovery.application.activities.ActivityMergeRefusedException;
import com.flowops.discovery.application.activities.UnknownActivityException;
import com.flowops.discovery.application.claimbracket.WorkIsAlreadySomebodysException;
import com.flowops.discovery.application.clients.ClientHasEngagementsException;
import com.flowops.discovery.application.clients.ClientNameTakenException;
import com.flowops.discovery.application.clients.UnknownClientException;
import com.flowops.discovery.application.closejob.JobAlreadyEndedException;
import com.flowops.discovery.application.closejob.JobStillHasLiveWorkException;
import com.flowops.discovery.application.closejob.OnlyTheOwnerMayForceCloseException;
import com.flowops.discovery.application.declarewait.ItsTargetDecidesWhenItArrivedException;
import com.flowops.discovery.application.declarewait.UnknownWaitException;
import com.flowops.discovery.application.declarewait.WaitAlreadyEndedException;
import com.flowops.discovery.application.declarewait.WaitIsNotYoursToDeclareException;
import com.flowops.discovery.application.declarewait.WaitIsNotYoursToEndException;
import com.flowops.discovery.application.deliverwork.NothingOfYoursIsOpenHereException;
import com.flowops.discovery.application.deliverwork.SeveralPiecesOfWorkCouldBeDeliveredException;
import com.flowops.discovery.application.markmessage.UnknownJobException;
import com.flowops.discovery.application.markwork.ThatWorkIsAlreadyOpenException;
import com.flowops.discovery.application.openjob.AnEngagementForThisClientIsAlreadyOpenException;
import com.flowops.discovery.application.shared.exception.AlreadyNudgedException;
import com.flowops.discovery.application.shared.exception.MessageNotMarkableException;
import com.flowops.discovery.application.shared.exception.NodeAlreadyTemplatedException;
import com.flowops.discovery.application.shared.exception.NodeIsNotOrphanException;
import com.flowops.discovery.application.shared.exception.NotAuthenticatedException;
import com.flowops.discovery.application.shared.exception.OutputAlreadyRecordedException;
import com.flowops.discovery.application.shared.exception.ProposalIsNotAShapeException;
import com.flowops.discovery.application.shared.exception.RecommendationAlreadyDecidedException;
import com.flowops.discovery.application.shared.exception.TrackAlreadyClosedException;
import com.flowops.discovery.application.shared.exception.TrackNotFullyTemplatedException;
import com.flowops.discovery.application.shared.exception.TypeNameTakenException;
import com.flowops.discovery.application.shared.exception.UndoWindowClosedException;
import com.flowops.discovery.application.shared.exception.UnknownRecommendationException;
import com.flowops.discovery.application.shared.exception.UnknownTrackException;
import com.flowops.discovery.application.shared.exception.UnknownTrackTypeException;
import com.flowops.discovery.application.shared.exception.UnknownWorkNodeException;
import com.flowops.discovery.application.undomark.TheEngagementHoldsOtherWorkException;
import com.flowops.discovery.domain.exception.IllegalNodeTransitionException;
import com.flowops.discovery.domain.exception.NotYoursToDescribeException;
import com.flowops.shared.published.PublishedRefusal;
import com.flowops.shared.web.ErrorResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.flowops.discovery")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DiscoveryExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(DiscoveryExceptionHandler.class);

    @ExceptionHandler(NotAuthenticatedException.class)
    public ResponseEntity<ErrorResponse> onNoSession(NotAuthenticatedException failure) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("NOT_AUTHENTICATED", "Sign in to continue."));
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ErrorResponse> onDenied(AuthorizationDeniedException failure) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("NOT_PERMITTED", "You do not have permission to do that."));
    }

    @ExceptionHandler(MessageNotMarkableException.class)
    public ResponseEntity<ErrorResponse> onNotMarkable(MessageNotMarkableException failure) {
        return ResponseEntity.unprocessableEntity()
                .body(ErrorResponse.of(
                        "MESSAGE_NOT_MARKABLE",
                        "That message cannot be marked as work.",
                        List.of(new ErrorResponse.FieldViolation("messageId", "NOT_MARKABLE"))));
    }

    @ExceptionHandler(UnknownJobException.class)
    public ResponseEntity<ErrorResponse> onUnknownJob(UnknownJobException failure) {
        return ResponseEntity.unprocessableEntity()
                .body(ErrorResponse.of(
                        "UNKNOWN_JOB",
                        "That engagement does not exist.",
                        List.of(new ErrorResponse.FieldViolation("jobId", "UNKNOWN"))));
    }

    @ExceptionHandler(IllegalNodeTransitionException.class)
    public ResponseEntity<ErrorResponse> onIllegalTransition(IllegalNodeTransitionException failure) {
        LOG.warn(
                "A unit of work was asked to go from {} to {}, which machine 17.1 does not draw.",
                failure.from(),
                failure.to());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(
                        "ILLEGAL_NODE_TRANSITION", "That work is not in a state where that can happen."));
    }

    @ExceptionHandler(UndoWindowClosedException.class)
    public ResponseEntity<ErrorResponse> onUndoWindowClosed(UndoWindowClosedException failure) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("UNDO_WINDOW_CLOSED", "That can no longer be taken back."));
    }

    @ExceptionHandler(NotYoursToDescribeException.class)
    public ResponseEntity<ErrorResponse> onNotYoursToDescribe(NotYoursToDescribeException failure) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("NOT_YOURS_TO_DESCRIBE", failure.getMessage()));
    }

    @ExceptionHandler(UnknownWorkNodeException.class)
    public ResponseEntity<ErrorResponse> onUnknownWorkNode(UnknownWorkNodeException failure) {
        return ResponseEntity.unprocessableEntity()
                .body(ErrorResponse.of(
                        "UNKNOWN_WORK_NODE",
                        "That unit of work does not exist.",
                        List.of(new ErrorResponse.FieldViolation("nodeId", "UNKNOWN"))));
    }

    @ExceptionHandler(UnknownTrackException.class)
    public ResponseEntity<ErrorResponse> onUnknownTrack(UnknownTrackException failure) {
        return ResponseEntity.unprocessableEntity()
                .body(ErrorResponse.of(
                        "UNKNOWN_TRACK",
                        "That thread of work does not exist.",
                        List.of(new ErrorResponse.FieldViolation("trackId", "UNKNOWN"))));
    }

    @ExceptionHandler(AlreadyNudgedException.class)
    public ResponseEntity<ErrorResponse> onAlreadyNudged(AlreadyNudgedException failure) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("ALREADY_NUDGED", "That has already been nudged once."));
    }

    @ExceptionHandler(OutputAlreadyRecordedException.class)
    public ResponseEntity<ErrorResponse> onOutputAlreadyRecorded(OutputAlreadyRecordedException failure) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(
                        "OUTPUT_ALREADY_RECORDED", "What that work produced has already been recorded."));
    }

    @ExceptionHandler(JobStillHasLiveWorkException.class)
    public ResponseEntity<ErrorResponse> onJobStillHasLiveWork(JobStillHasLiveWorkException failure) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(
                        "JOB_STILL_HAS_LIVE_WORK",
                        failure.liveWork() + " piece(s) of work are still open in this job. "
                                + "Finish them, or force-close the job and say why."));
    }

    @ExceptionHandler(JobAlreadyEndedException.class)
    public ResponseEntity<ErrorResponse> onJobAlreadyEnded(JobAlreadyEndedException failure) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(
                        "JOB_ALREADY_ENDED",
                        failure.wasForced()
                                ? "That engagement was cancelled. New work belongs in a different job."
                                : "That engagement is closed. New work opens a round two linked to it."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> onRefusedArgument(IllegalArgumentException refusal) {
        LOG.warn("A request was refused by a domain rule: {}", refusal.getMessage());
        return ResponseEntity.badRequest().body(ErrorResponse.of("REFUSED", refusal.getMessage(), List.of()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> onRefusedState(IllegalStateException refusal) {
        LOG.warn("A request met work in the wrong state: {}", refusal.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("NOT_IN_THAT_STATE", refusal.getMessage(), List.of()));
    }

    @ExceptionHandler(AnEngagementForThisClientIsAlreadyOpenException.class)
    public ResponseEntity<ErrorResponse> onDuplicateEngagement(AnEngagementForThisClientIsAlreadyOpenException open) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(
                        "AN_ENGAGEMENT_FOR_THIS_CLIENT_IS_ALREADY_OPEN",
                        "There is already an open engagement for this client — \"" + open.itsName()
                                + "\". Add to it, or open a separate one anyway.",
                        List.of(new ErrorResponse.FieldViolation(
                                "alreadyOpenJobId", open.alreadyOpen().value().toString()))));
    }

    @ExceptionHandler(UnknownClientException.class)
    public ResponseEntity<ErrorResponse> onUnknownClient(UnknownClientException failure) {
        return ResponseEntity.unprocessableEntity()
                .body(ErrorResponse.of(
                        "UNKNOWN_CLIENT",
                        "That client does not exist.",
                        List.of(new ErrorResponse.FieldViolation("counterpartyId", "UNKNOWN"))));
    }

    @ExceptionHandler(UnknownActivityException.class)
    public ResponseEntity<ErrorResponse> onUnknownActivity(UnknownActivityException failure) {
        return ResponseEntity.unprocessableEntity()
                .body(ErrorResponse.of(
                        "UNKNOWN_ACTIVITY",
                        "That activity does not exist.",
                        List.of(new ErrorResponse.FieldViolation("activityId", "UNKNOWN"))));
    }

    @ExceptionHandler(ActivityMergeRefusedException.class)
    public ResponseEntity<ErrorResponse> onActivityMergeRefused(ActivityMergeRefusedException failure) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("ACTIVITY_MERGE_REFUSED", failure.getMessage()));
    }

    @ExceptionHandler(ClientNameTakenException.class)
    public ResponseEntity<ErrorResponse> onClientNameTaken(ClientNameTakenException failure) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("CLIENT_NAME_TAKEN", "Another client already goes by that name."));
    }

    @ExceptionHandler(ClientHasEngagementsException.class)
    public ResponseEntity<ErrorResponse> onClientHasEngagements(ClientHasEngagementsException failure) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(
                        "CLIENT_HAS_ENGAGEMENTS",
                        failure.engagements() + " engagement(s) are for that client, so it cannot be removed."));
    }

    @ExceptionHandler(UnknownWaitException.class)
    public ResponseEntity<ErrorResponse> onUnknownWait(UnknownWaitException failure) {
        return ResponseEntity.unprocessableEntity()
                .body(ErrorResponse.of(
                        "UNKNOWN_WAIT",
                        "That wait is no longer there.",
                        List.of(new ErrorResponse.FieldViolation("waitId", "UNKNOWN"))));
    }

    @ExceptionHandler(WaitIsNotYoursToEndException.class)
    public ResponseEntity<ErrorResponse> onWaitNotYours(WaitIsNotYoursToEndException failure) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(
                        "WAIT_IS_NOT_YOURS_TO_END",
                        "Only the person holding the blocked work can say what it was waiting for arrived."));
    }

    @ExceptionHandler(WaitIsNotYoursToDeclareException.class)
    public ResponseEntity<ErrorResponse> onWaitNotYoursToDeclare(WaitIsNotYoursToDeclareException failure) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(
                        "WAIT_IS_NOT_YOURS_TO_DECLARE",
                        "Only the person doing this work can say it is waiting on something."));
    }

    @ExceptionHandler(ItsTargetDecidesWhenItArrivedException.class)
    public ResponseEntity<ErrorResponse> onTargetDecides(ItsTargetDecidesWhenItArrivedException failure) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(
                        ErrorResponse.of(
                                "ITS_TARGET_DECIDES_WHEN_IT_ARRIVED",
                                "That wait is on work in this graph. It clears when that work is closed as delivered or done."));
    }

    @ExceptionHandler(WaitAlreadyEndedException.class)
    public ResponseEntity<ErrorResponse> onWaitAlreadyEnded(WaitAlreadyEndedException failure) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("WAIT_ALREADY_ENDED", "That wait has already been cleared."));
    }

    @ExceptionHandler(OnlyTheOwnerMayForceCloseException.class)
    public ResponseEntity<ErrorResponse> onNotTheOwner(OnlyTheOwnerMayForceCloseException failure) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(
                        "ONLY_THE_OWNER_MAY_FORCE_CLOSE", "Only the person who opened this job can force it closed."));
    }

    @ExceptionHandler(TheEngagementHoldsOtherWorkException.class)
    public ResponseEntity<ErrorResponse> onEngagementHoldsOtherWork(TheEngagementHoldsOtherWorkException failure) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(
                        "ENGAGEMENT_HOLDS_OTHER_WORK",
                        "Somebody has already marked work into this job, so its opening cannot be taken back."));
    }

    @ExceptionHandler(WorkIsAlreadySomebodysException.class)
    public ResponseEntity<ErrorResponse> onAlreadyClaimed(WorkIsAlreadySomebodysException failure) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(
                        "WORK_IS_ALREADY_SOMEBODYS",
                        "Somebody is already doing that. Use hand over if it is moving to you."));
    }

    @ExceptionHandler(ThatWorkIsAlreadyOpenException.class)
    public ResponseEntity<ErrorResponse> onWorkAlreadyOpen(ThatWorkIsAlreadyOpenException open) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(
                        "THAT_WORK_IS_ALREADY_OPEN",
                        "You already have \"" + open.destination() + "\" open here. Add to it instead.",
                        List.of(new ErrorResponse.FieldViolation(
                                "alreadyOpenBracketId", open.alreadyOpen().toString()))));
    }

    @ExceptionHandler(NothingOfYoursIsOpenHereException.class)
    public ResponseEntity<ErrorResponse> onNothingOfYoursIsOpen(NothingOfYoursIsOpenHereException nothing) {
        String said = nothing.heldByOthers().isEmpty()
                ? "Nothing of yours is open here. Mark it as work first."
                : "Nothing of yours is open here — " + String.join(" and ", nothing.heldByOthers())
                        + " holds the work that is.";

        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of("NOTHING_OF_YOURS_IS_OPEN_HERE", said));
    }

    @ExceptionHandler(SeveralPiecesOfWorkCouldBeDeliveredException.class)
    public ResponseEntity<ErrorResponse> onSeveralCouldBeDelivered(SeveralPiecesOfWorkCouldBeDeliveredException many) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(
                        "SEVERAL_COULD_BE_DELIVERED",
                        "You have " + many.candidates().size() + " pieces of work open here. Which one does "
                                + "this deliver?",
                        many.candidates().stream()
                                .map(candidate -> new ErrorResponse.FieldViolation("bracketId", candidate.toString()))
                                .toList()));
    }

    @ExceptionHandler(TrackAlreadyClosedException.class)
    public ResponseEntity<ErrorResponse> onTrackAlreadyClosed(TrackAlreadyClosedException failure) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("TRACK_ALREADY_CLOSED", "That thread of work is closed."));
    }

    @ExceptionHandler(NodeIsNotOrphanException.class)
    public ResponseEntity<ErrorResponse> onNotOrphan(NodeIsNotOrphanException failure) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("NODE_IS_NOT_ORPHAN", "That work already belongs to a thread."));
    }

    @ExceptionHandler(UnknownTrackTypeException.class)
    public ResponseEntity<ErrorResponse> onUnknownTrackType(UnknownTrackTypeException failure) {
        return ResponseEntity.unprocessableEntity()
                .body(ErrorResponse.of(
                        "UNKNOWN_TRACK_TYPE",
                        "That type of work does not exist.",
                        List.of(new ErrorResponse.FieldViolation("typeId", "UNKNOWN"))));
    }

    @ExceptionHandler(TypeNameTakenException.class)
    public ResponseEntity<ErrorResponse> onTypeNameTaken(TypeNameTakenException failure) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(
                        "TYPE_NAME_TAKEN",
                        "A type of work already goes by that name.",
                        List.of(new ErrorResponse.FieldViolation("name", "TAKEN"))));
    }

    @ExceptionHandler(NodeAlreadyTemplatedException.class)
    public ResponseEntity<ErrorResponse> onAlreadyTemplated(NodeAlreadyTemplatedException failure) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("NODE_ALREADY_TEMPLATED", "That work is already a task template."));
    }

    @ExceptionHandler(TrackNotFullyTemplatedException.class)
    public ResponseEntity<ErrorResponse> onNotFullyTemplated(TrackNotFullyTemplatedException failure) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(
                        "TRACK_NOT_FULLY_TEMPLATED", "Every unit of work in that thread needs a task template first."));
    }

    @ExceptionHandler(UnknownRecommendationException.class)
    public ResponseEntity<ErrorResponse> onUnknownRecommendation(UnknownRecommendationException failure) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(
                        "UNKNOWN_RECOMMENDATION",
                        "That suggestion is no longer on the board. Run the analysis again to see what it says now."));
    }

    @ExceptionHandler(RecommendationAlreadyDecidedException.class)
    public ResponseEntity<ErrorResponse> onAlreadyDecided(RecommendationAlreadyDecidedException failure) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(
                        "RECOMMENDATION_ALREADY_DECIDED", "Somebody has already dealt with that suggestion."));
    }

    @ExceptionHandler(ProposalIsNotAShapeException.class)
    public ResponseEntity<ErrorResponse> onNotAShape(ProposalIsNotAShapeException failure) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(
                        "PROPOSAL_IS_NOT_A_SHAPE", "There is no process to write down from that suggestion."));
    }

    @ExceptionHandler(PublishedRefusal.class)
    public ResponseEntity<ErrorResponse> onNeighbourRefusal(PublishedRefusal refusal) {
        HttpStatus status =
                switch (refusal.kind()) {
                    case NOT_FOUND -> HttpStatus.NOT_FOUND;
                    case NOT_PERMITTED -> HttpStatus.FORBIDDEN;
                    case CONFLICT -> HttpStatus.CONFLICT;
                };
        return ResponseEntity.status(status).body(ErrorResponse.of(refusal.code(), refusal.getMessage(), List.of()));
    }
}
