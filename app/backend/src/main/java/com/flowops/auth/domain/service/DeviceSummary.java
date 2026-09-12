package com.flowops.auth.domain.service;

import com.flowops.auth.domain.model.SessionMetadata;
import java.util.Locale;

public final class DeviceSummary {
    private static final int MAXIMUM_LENGTH = 200;

    private DeviceSummary() {}

    public static String from(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return SessionMetadata.UNKNOWN;
        }
        String lowered = userAgent.toLowerCase(Locale.ROOT);
        String browser = browserOf(lowered);
        String platform = platformOf(lowered);
        if (browser.equals(SessionMetadata.UNKNOWN) && platform.equals(SessionMetadata.UNKNOWN)) {
            return truncate(userAgent);
        }
        return browser + " on " + platform;
    }

    private static String browserOf(String userAgent) {
        if (userAgent.contains("edg/")) {
            return "Edge";
        }
        if (userAgent.contains("chrome/") && !userAgent.contains("chromium")) {
            return "Chrome";
        }
        if (userAgent.contains("firefox/")) {
            return "Firefox";
        }
        if (userAgent.contains("safari/")) {
            return "Safari";
        }
        return SessionMetadata.UNKNOWN;
    }

    private static String platformOf(String userAgent) {
        if (userAgent.contains("windows")) {
            return "Windows";
        }
        if (userAgent.contains("android")) {
            return "Android";
        }
        if (userAgent.contains("iphone") || userAgent.contains("ipad")) {
            return "iOS";
        }
        if (userAgent.contains("mac os")) {
            return "macOS";
        }
        if (userAgent.contains("linux")) {
            return "Linux";
        }
        return SessionMetadata.UNKNOWN;
    }

    private static String truncate(String value) {
        return value.length() <= MAXIMUM_LENGTH ? value : value.substring(0, MAXIMUM_LENGTH);
    }
}
