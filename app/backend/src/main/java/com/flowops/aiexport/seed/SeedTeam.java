package com.flowops.aiexport.seed;

import java.util.List;

record SeedTeam(
        SeedCompany.Person manager,
        List<SeedCompany.Person> reports,
        List<SeedCompany.Person> liveReports,
        Errands errands) {
    static SeedTeam of(SeedCompany.Person manager, List<SeedCompany.Person> reports, Errands errands) {
        return new SeedTeam(manager, reports, reports, errands);
    }

    static SeedTeam whereSomebodyLeaves(
            SeedCompany.Person manager, List<SeedCompany.Person> reports, SeedCompany.Person leaving, Errands errands) {
        List<SeedCompany.Person> staying = reports.stream()
                .filter(person -> !person.displayName().equals(leaving.displayName()))
                .toList();
        if (staying.isEmpty()) {
            throw new SeedFailedException("every member of " + manager.displayName()
                    + "'s team is leaving, so there is nobody to hold the live work");
        }
        return new SeedTeam(manager, reports, staying, errands);
    }
}
