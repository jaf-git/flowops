package com.flowops.discovery.application.activities;

public class ActivityMergeRefusedException extends RuntimeException {
    public ActivityMergeRefusedException(String why) {
        super(why);
    }
}
