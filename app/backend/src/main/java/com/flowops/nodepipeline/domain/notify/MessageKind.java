package com.flowops.nodepipeline.domain.notify;

public enum MessageKind {
    WORK_COULD_HAVE_BEEN_FASTER(Recipient.Standing.MARKER),

    WHICH_TEMPLATE_WAS_THIS(Recipient.Standing.MARKER),

    A_HABIT_WORTH_A_TEMPLATE(Recipient.Standing.MARKER),

    STAFFING_NOTE(Recipient.Standing.JOB_OWNER),

    THE_ENGAGEMENT_HAS_STALLED(Recipient.Standing.JOB_OWNER),

    STEPS_THIS_ENGAGEMENT_SKIPPED(Recipient.Standing.JOB_OWNER),

    AN_UNDOCUMENTED_PROCESS(Recipient.Standing.WORKSPACE_OWNER),

    WORK_WORTH_A_TEMPLATE(Recipient.Standing.WORKSPACE_OWNER);

    private final Recipient.Standing addressee;

    MessageKind(Recipient.Standing addressee) {
        this.addressee = addressee;
    }

    public Recipient.Standing addressee() {
        return addressee;
    }

    public boolean spokenAtJobLevel() {
        return addressee == Recipient.Standing.JOB_OWNER;
    }

    public boolean forTheOwner() {
        return addressee == Recipient.Standing.WORKSPACE_OWNER;
    }
}
