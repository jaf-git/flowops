package com.flowops.aiexport.seed;

import java.util.List;

record Errands(List<String> activities, List<String> subjects, String subjectNoun) {
    private static final List<String> MONTHS = List.of("", "September", "October", "November", "December");

    String title(int n) {
        int a = Math.floorMod(n, activities.size());
        int s = Math.floorMod(n / activities.size(), subjects.size());
        int round = Math.floorMod(n / (activities.size() * subjects.size()), MONTHS.size());
        String said = activities.get(a).replace("{}", subjects.get(s));
        return round == 0 ? said : said + " — " + MONTHS.get(round);
    }

    String templateTitle(int n) {
        return generalised(activities.get(Math.floorMod(n, activities.size())));
    }

    List<String> everyTemplateTitle() {
        return activities.stream().map(this::generalised).toList();
    }

    private String generalised(String activity) {
        return activity.replace("{}", subjectNoun);
    }

    static Errands clientServices() {
        return new Errands(
                List.of(
                        "File the signed contract for {}",
                        "Renew the domain name for {}",
                        "Set up a shared folder for {}",
                        "Order business cards for {}",
                        "Archive last year's paperwork for {}",
                        "Reserve a meeting room for the {} workshop",
                        "Print the handouts for {}",
                        "Amend the billing address for {}",
                        "Register {} on the reporting dashboard",
                        "Close down the old mailbox for {}"),
                SeedClients.settled(),
                "the client");
    }

    static Errands studio() {
        return new Errands(
                List.of(
                        "Back up the raw photos for {}",
                        "Sort the asset folder for {}",
                        "Export the logo in every format for {}",
                        "Compress the video files for {}",
                        "Label the picture files for {}",
                        "Move {}'s old drafts to the archive",
                        "Confirm the fonts are licensed for {}",
                        "Rebuild the folder structure for {}",
                        "Empty the shared bin for {}",
                        "Colour-check the printed proofs for {}"),
                SeedClients.settled(),
                "the client");
    }
}
