package com.flowops.notification.application.shared.port;

public interface WeeklySectionPort {
    String key();

    int count();

    default boolean alwaysRenders() {
        return false;
    }
}
