package com.flowops.discovery.domain.model;

import java.util.Locale;
import java.util.Map;

public final class WorkTypeCatalogue {
    public static final String GENERAL = "GENERAL";

    private static final Map<String, String> BY_ROLE = Map.ofEntries(
            Map.entry("ACCOUNTS", "CLIENT_INTAKE"),
            Map.entry("TEAM_LEAD", "COORDINATION"),
            Map.entry("WRITER", "CONTENT"),
            Map.entry("DESIGN", "DESIGN"),
            Map.entry("PHOTO", "PHOTO"),
            Map.entry("VIDEO", "VIDEO"),
            Map.entry("ADS", "ADS"),
            Map.entry("SCHEDULING", "SCHEDULING"),
            Map.entry("REPORTING", "REPORTING"),
            Map.entry("FINANCE", "FINANCE"),
            Map.entry("DEV", "DEV"));

    private WorkTypeCatalogue() {}

    private static final String[][] BY_WORD = {
        {"video", "VIDEO"},
        {"photo", "PHOTO"},
        {"accountant", "FINANCE"},
        {"accounting", "FINANCE"},
        {"credit", "FINANCE"},
        {"financ", "FINANCE"},
        {"bookkeep", "FINANCE"},
        {"account", "CLIENT_INTAKE"},
        {"intake", "CLIENT_INTAKE"},
        {"ads", "ADS"},
        {"advert", "ADS"},
        {"design", "DESIGN"},
        {"writer", "CONTENT"},
        {"content", "CONTENT"},
        {"copy", "CONTENT"},
        {"edit", "CONTENT"},
        {"proofread", "CONTENT"},
        {"schedul", "SCHEDULING"},
        {"report", "REPORTING"},
        {"analy", "REPORTING"},
        {"dev", "DEV"},
        {"engineer", "DEV"},
        {"lead", "COORDINATION"},
        {"manager", "COORDINATION"},
        {"owner", "COORDINATION"},
        {"director", "COORDINATION"},
    };

    public static String forRole(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return GENERAL;
        }

        String normalised =
                roleName.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');

        String exact = BY_ROLE.get(normalised);
        if (exact != null) {
            return exact;
        }

        String lowered = roleName.trim().toLowerCase(Locale.ROOT);
        for (String[] pair : BY_WORD) {
            if (lowered.contains(pair[0])) {
                return pair[1];
            }
        }

        return GENERAL;
    }

    public static boolean isUnmapped(String workType) {
        return GENERAL.equals(workType);
    }

    public static String titleOf(String workType) {
        if (workType == null || workType.isBlank()) {
            return GENERAL.charAt(0) + GENERAL.substring(1).toLowerCase(Locale.ROOT);
        }

        String words = workType.trim().replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
