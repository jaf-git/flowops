package com.flowops.aiexport.seed;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

final class SeedOrganisation {
    static final String AGENCY_OWNER = "Agency owner";

    private static final Map<String, String> WHO_DOES_WHAT = Map.of(
            SeedCompany.STRATEGY_MANAGER,
            "Account manager",
            SeedCompany.SOCIAL_MANAGER,
            "Account manager",
            "Andrei Munteanu",
            "Content writer",
            SeedCompany.THE_COLLEAGUE_WHO_LEAVES,
            "Designer",
            "Cosmin Vasile",
            "Ads specialist",
            "Daria Enache",
            "Content writer");

    private final SeedCompany company;

    SeedOrganisation(SeedCompany company) {
        this.company = company;
    }

    void sayWhatEverybodyDoes() {
        SeedCompany.Person owner = company.owner();
        Map<String, UUID> rolesByName = readTheVocabulary(owner);

        assign(owner, owner, rolesByName, AGENCY_OWNER);
        WHO_DOES_WHAT.forEach(
                (displayName, roleName) -> assign(owner, company.named(displayName), rolesByName, roleName));
    }

    static String statedJobOf(String displayName) {
        return WHO_DOES_WHAT.getOrDefault(displayName, AGENCY_OWNER);
    }

    private Map<String, UUID> readTheVocabulary(SeedCompany.Person reader) {
        Map<String, UUID> byName = new LinkedHashMap<>();
        JsonNode chart = reader.browser().getOrFail("/api/workspace/organisation", "reading the organisation chart");
        for (JsonNode department : chart.path("departments")) {
            for (JsonNode role : department.path("roles")) {
                byName.put(
                        role.path("name").asText(),
                        UUID.fromString(role.path("id").asText()));
            }
        }
        if (byName.isEmpty()) {
            throw new SeedFailedException(
                    "the workspace has no functional roles, so V64's seeded vocabulary did not arrive");
        }
        return byName;
    }

    private void assign(
            SeedCompany.Person actor, SeedCompany.Person person, Map<String, UUID> rolesByName, String roleName) {
        UUID role = rolesByName.get(roleName);
        if (role == null) {
            throw new SeedFailedException("there is no functional role called \"" + roleName
                    + "\", so the seeded vocabulary and this class disagree");
        }
        actor.browser()
                .putOrFail(
                        "/api/workspace/people/" + person.membershipId() + "/functional-role",
                        "{\"functionalRoleId\":\"" + role + "\"}",
                        "recording that " + person.displayName() + " is a " + roleName.toLowerCase());
    }
}
