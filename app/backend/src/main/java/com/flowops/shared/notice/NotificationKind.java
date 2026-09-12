package com.flowops.shared.notice;

public enum NotificationKind {
    WORK_ASSIGNED(NotificationGroup.ASSIGNMENT, SubjectKind.TASK, Timing.IMMEDIATE, CancelCondition.TASK_LEFT_CREATED),

    WORK_RETURNED(
            NotificationGroup.ASSIGNMENT, SubjectKind.TASK, Timing.IMMEDIATE, CancelCondition.TASK_LEFT_IN_PROGRESS),

    WORK_APPROVED(NotificationGroup.ASSIGNMENT, SubjectKind.TASK, Timing.IMMEDIATE, CancelCondition.NEVER),

    WORK_REJECTED(NotificationGroup.ASSIGNMENT, SubjectKind.TASK, Timing.IMMEDIATE, CancelCondition.TASK_REASSIGNED),

    DEADLINE_PROPOSED(
            NotificationGroup.ASSIGNMENT, SubjectKind.TASK, Timing.IMMEDIATE, CancelCondition.PROPOSAL_DECIDED),

    DEADLINE_DECIDED(NotificationGroup.ASSIGNMENT, SubjectKind.TASK, Timing.IMMEDIATE, CancelCondition.NEVER),

    LONG_BLOCK_1(NotificationGroup.TIME, SubjectKind.TASK, Timing.HELD, CancelCondition.TASK_UNBLOCKED),

    LONG_BLOCK_2(NotificationGroup.TIME, SubjectKind.TASK, Timing.HELD, CancelCondition.TASK_UNBLOCKED),

    STALE_REVIEW(NotificationGroup.TIME, SubjectKind.TASK, Timing.HELD, CancelCondition.TASK_LEFT_REVIEW),

    OVERDUE_RUNG_1(
            NotificationGroup.ESCALATION, SubjectKind.TASK, Timing.ESCALATION, CancelCondition.TASK_LEFT_OVERDUE),

    OVERDUE_RUNG_2(
            NotificationGroup.ESCALATION, SubjectKind.TASK, Timing.ESCALATION, CancelCondition.TASK_LEFT_OVERDUE),

    OVERDUE_RUNG_3(
            NotificationGroup.ESCALATION, SubjectKind.TASK, Timing.ESCALATION, CancelCondition.TASK_LEFT_OVERDUE),

    STEP_REACHABLE(NotificationGroup.PROCESS, SubjectKind.STEP, Timing.IMMEDIATE, CancelCondition.STEP_ASSIGNED),

    STEP_STALLED(NotificationGroup.PROCESS, SubjectKind.STEP, Timing.HELD, CancelCondition.STEP_ASSIGNED),

    RUN_COMPLETED(NotificationGroup.PROCESS, SubjectKind.RUN, Timing.IMMEDIATE, CancelCondition.NEVER),

    TASK_ATTACHED_TO_RUN(NotificationGroup.PROCESS, SubjectKind.STEP, Timing.HELD, CancelCondition.TASK_DETACHED),

    AWAITED_WORK_ARRIVED(NotificationGroup.DISCOVERY, SubjectKind.BRACKET, Timing.IMMEDIATE, CancelCondition.NEVER),

    AWAITED_WORK_DROPPED(NotificationGroup.DISCOVERY, SubjectKind.BRACKET, Timing.IMMEDIATE, CancelCondition.NEVER),

    BRACKET_STILL_GOING(NotificationGroup.DISCOVERY, SubjectKind.BRACKET, Timing.HELD, CancelCondition.BRACKET_CLOSED),

    JOB_FORCE_CLOSED(NotificationGroup.DISCOVERY, SubjectKind.JOB, Timing.IMMEDIATE, CancelCondition.NEVER),

    SOMEBODY_IS_WAITING_ON_YOU(
            NotificationGroup.DISCOVERY, SubjectKind.BRACKET, Timing.IMMEDIATE, CancelCondition.BRACKET_CLOSED),

    WAIT_DATE_HAS_PASSED(NotificationGroup.DISCOVERY, SubjectKind.BRACKET, Timing.HELD, CancelCondition.BRACKET_CLOSED),

    EXTERNAL_WAIT_IS_LONG(NotificationGroup.DISCOVERY, SubjectKind.JOB, Timing.HELD, CancelCondition.NEVER),

    A_TEMPLATE_EXISTS_FOR_THIS(NotificationGroup.DISCOVERY, SubjectKind.JOB, Timing.HELD, CancelCondition.NEVER),

    AN_ENGAGEMENT_NEEDS_A_LOOK(NotificationGroup.DISCOVERY, SubjectKind.JOB, Timing.HELD, CancelCondition.NEVER),

    SOMETHING_IS_WORTH_WRITING_DOWN(
            NotificationGroup.DISCOVERY, SubjectKind.WORKSPACE, Timing.HELD, CancelCondition.NEVER),

    WEEKLY_SUMMARY(NotificationGroup.WEEKLY, SubjectKind.WORKSPACE, Timing.HELD, CancelCondition.EVERY_SECTION_EMPTY);

    private final NotificationGroup group;
    private final SubjectKind subjectKind;
    private final Timing timing;
    private final CancelCondition cancelCondition;

    NotificationKind(NotificationGroup group, SubjectKind subjectKind, Timing timing, CancelCondition cancelCondition) {
        this.group = group;
        this.subjectKind = subjectKind;
        this.timing = timing;
        this.cancelCondition = cancelCondition;
    }

    public NotificationGroup group() {
        return group;
    }

    public SubjectKind subjectKind() {
        return subjectKind;
    }

    public Timing timing() {
        return timing;
    }

    public CancelCondition cancelCondition() {
        return cancelCondition;
    }

    public boolean suppressible() {
        return group.disableable();
    }
}
