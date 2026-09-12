package com.flowops.aiexport.seed;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class SeedCompany {
    private static final String OWNER_EMAIL = "maria@atelier.ro";

    private static final String OWNER_NAME = "Maria Ionescu";

    private static final String PASSWORD = "password1234";

    private final String root;
    private final MailboxReader mailbox;
    private final Map<String, Person> byName = new LinkedHashMap<>();

    SeedCompany(String root, MailboxReader mailbox) {
        this.root = root;
        this.mailbox = mailbox;
    }

    record Person(
            String email, String displayName, String role, UUID personId, UUID membershipId, SeedHttpClient browser) {}

    Person owner() {
        return byName.get(OWNER_NAME);
    }

    List<Person> everybody() {
        return List.copyOf(byName.values());
    }

    Person named(String displayName) {
        Person found = byName.get(displayName);
        if (found == null) {
            throw new SeedFailedException("the seed has no person called " + displayName);
        }
        return found;
    }

    static final String STRATEGY_MANAGER = "Ionuț Petrescu";

    static final String SOCIAL_MANAGER = "Ioana Radu";

    static final String THE_COLLEAGUE_WHO_LEAVES = "Elena Dobre";

    Person build() {
        Person owner = claimTheInstallation(OWNER_EMAIL, OWNER_NAME);

        Person ionut = invite(owner, "ionut@atelier.ro", STRATEGY_MANAGER, "MANAGER", owner.membershipId());
        Person ioana = invite(owner, "ioana@atelier.ro", SOCIAL_MANAGER, "MANAGER", owner.membershipId());

        invite(owner, "andrei@atelier.ro", "Andrei Munteanu", "EMPLOYEE", ionut.membershipId());
        invite(owner, "elena@atelier.ro", THE_COLLEAGUE_WHO_LEAVES, "EMPLOYEE", ionut.membershipId());

        invite(owner, "cosmin@atelier.ro", "Cosmin Vasile", "EMPLOYEE", ioana.membershipId());
        invite(owner, "daria@atelier.ro", "Daria Enache", "EMPLOYEE", ioana.membershipId());

        return owner;
    }

    private Person claimTheInstallation(String email, String displayName) {
        SeedHttpClient browser = new SeedHttpClient(root);
        browser.mintCrossSiteToken();

        String before = mailbox.newestMessageIdFor(email);
        browser.postOrFail("/api/auth/signup/passcode", quoted("email", email), "requesting the owner's passcode");
        String passcode = mailbox.passcodeFor(email, before);

        browser.postOrFail(
                "/api/auth/signup",
                "{\"email\":\"" + email + "\",\"passcode\":\"" + passcode + "\",\"password\":\"" + PASSWORD + "\"}",
                "claiming the installation as " + displayName);

        browser.postOrFail(
                "/api/workspace/setup",
                "{\"ownerName\":\"" + displayName + "\",\"workspaceName\":\"Atelier Ionescu\","
                        + "\"use\":\"WORK\",\"timezone\":\"Europe/Bucharest\"}",
                "setting the workspace up");

        Directory row = readBackFromTheDirectory(browser, displayName);
        Person owner = new Person(email, displayName, "OWNER", row.personId(), row.membershipId(), browser);
        byName.put(displayName, owner);
        return owner;
    }

    private Person invite(Person inviter, String email, String displayName, String role, UUID managerId) {
        String before = mailbox.newestMessageIdFor(email);
        inviter.browser()
                .postOrFail(
                        "/api/workspace/invitations",
                        "{\"emailAddress\":\"" + email + "\",\"role\":\"" + role + "\",\"managerId\":\"" + managerId
                                + "\"}",
                        "inviting " + displayName);

        String token = mailbox.invitationTokenFor(email, before);

        SeedHttpClient browser = new SeedHttpClient(root);
        browser.mintCrossSiteToken();

        JsonNode preview =
                browser.getOrFail("/api/workspace/invitations/" + token, "reading " + displayName + "'s invitation");
        String consentVersion = preview.path("consent").path("version").asText();

        browser.postOrFail(
                "/api/workspace/invitations/" + token + "/accept",
                "{\"displayName\":\"" + displayName + "\",\"password\":\"" + PASSWORD
                        + "\",\"consentAccepted\":true,\"consentVersion\":\"" + consentVersion + "\"}",
                displayName + " accepting their invitation");

        Directory row = readBackFromTheDirectory(inviter.browser(), displayName);
        Person joined = new Person(email, displayName, role, row.personId(), row.membershipId(), browser);
        byName.put(displayName, joined);
        signIn(joined);
        return joined;
    }

    void reauthenticate(Person person) {
        person.browser()
                .postOrFail(
                        "/api/auth/reauthenticate",
                        "{\"password\":\"" + PASSWORD + "\"}",
                        person.displayName() + " confirming their password");
    }

    void signIn(Person person) {
        person.browser().forget();
        person.browser().mintCrossSiteToken();
        person.browser()
                .postOrFail(
                        "/api/auth/login",
                        "{\"email\":\"" + person.email() + "\",\"password\":\"" + PASSWORD + "\"}",
                        person.displayName() + " signing in");
    }

    private record Directory(UUID personId, UUID membershipId) {}

    private Directory readBackFromTheDirectory(SeedHttpClient reader, String displayName) {
        JsonNode people = reader.getOrFail("/api/workspace/people", "reading the directory")
                .path("people");
        for (JsonNode each : people) {
            if (displayName.equals(each.path("displayName").asText())) {
                return new Directory(
                        UUID.fromString(each.path("personId").asText()),
                        UUID.fromString(each.path("membershipId").asText()));
            }
        }
        throw new SeedFailedException(displayName + " does not appear in the directory after being created");
    }

    private static String quoted(String field, String value) {
        return "{\"" + field + "\":\"" + value + "\"}";
    }

    List<Person> employees() {
        List<Person> employees = new ArrayList<>();
        for (Person each : byName.values()) {
            if ("EMPLOYEE".equals(each.role())) {
                employees.add(each);
            }
        }
        return employees;
    }
}
