package com.flowops.aiexport.seed;

final class SeedJson {
    private SeedJson() {}

    static String quote(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
