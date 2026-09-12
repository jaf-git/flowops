package com.flowops.aiexport.seed;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class ErrandTemplates {
    private final Map<String, UUID> byTitle;

    private ErrandTemplates(Map<String, UUID> byTitle) {
        this.byTitle = byTitle;
    }

    static ErrandTemplates authoredBy(SeedHttpClient author, List<String> titles) {
        Map<String, UUID> byTitle = new LinkedHashMap<>();
        for (String title : titles) {
            if (byTitle.containsKey(title)) {
                continue;
            }
            byTitle.put(title, write(author, title));
        }
        return new ErrandTemplates(byTitle);
    }

    private static UUID write(SeedHttpClient author, String title) {
        JsonNode created = author.postOrFail(
                "/api/task-templates",
                """
                {"title":%s,"description":null,"type":"Errand","priority":"NORMAL","estimatedHours":%s,
                 "checklist":[],"submitForApproval":true}
                """
                        .formatted(
                                SeedJson.quote(title),
                                SeedEstimatePolicy.guessFor(title).estimateJson()),
                "writing the errand template " + title);

        UUID id = UUID.fromString(created.get("id").asText());
        author.postOrFail("/api/task-templates/" + id + "/approval", null, "approving " + title);
        return id;
    }

    UUID forActivity(String title) {
        UUID id = byTitle.get(title);
        if (id == null) {
            throw new SeedFailedException("no errand template was authored for \"" + title
                    + "\", so the task stamped from it would carry no template at all");
        }
        return id;
    }

    int size() {
        return byTitle.size();
    }
}
