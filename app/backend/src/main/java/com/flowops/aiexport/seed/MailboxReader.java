package com.flowops.aiexport.seed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MailboxReader {
    private static final Pattern PASSCODE = Pattern.compile("code is (\\S+?)\\.");

    private static final Pattern INVITATION_TOKEN = Pattern.compile("[?&]token=([A-Za-z0-9_\\-]+)");

    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json = new ObjectMapper();
    private final String root;

    MailboxReader(String root) {
        this.root = root;
    }

    String newestMessageIdFor(String address) {
        JsonNode items = search(address);
        return items.isArray() && !items.isEmpty() ? items.get(0).path("ID").asText() : null;
    }

    String passcodeFor(String address, String since) {
        return extract(address, since, PASSCODE, "signup passcode");
    }

    String invitationTokenFor(String address, String since) {
        return extract(address, since, INVITATION_TOKEN, "invitation token");
    }

    private String extract(String address, String since, Pattern pattern, String what) {
        String body = messageAfter(address, since);
        Matcher found = pattern.matcher(body);
        if (!found.find()) {
            throw new SeedFailedException("the message to " + address + " carries no " + what + ". Body was:\n" + body);
        }
        return found.group(1);
    }

    private String messageAfter(String address, String since) {
        for (int attempt = 0; attempt < 60; attempt++) {
            JsonNode items = search(address);
            if (items.isArray() && !items.isEmpty()) {
                String newest = items.get(0).path("ID").asText();
                if (!newest.equals(since)) {
                    return fetch("/api/v1/message/" + newest).path("Text").asText();
                }
            }
            sleepBriefly();
        }
        throw new SeedFailedException("no new message reached " + address
                + " within fifteen seconds. Is the mail catcher at " + root + " running and is the"
                + " application configured to send to it?");
    }

    private JsonNode search(String address) {
        return fetch("/api/v1/search?query=" + URLEncoder.encode("to:" + address, StandardCharsets.UTF_8))
                .path("messages");
    }

    private JsonNode fetch(String path) {
        try {
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(root + path))
                            .timeout(Duration.ofSeconds(5))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new SeedFailedException("the mail catcher at " + root + " answered " + response.statusCode()
                        + ". Is it running? The development stack starts it with docker compose.");
            }
            return json.readTree(response.body());
        } catch (IOException unreachable) {
            throw new SeedFailedException("the mail catcher at " + root + " could not be reached", unreachable);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new SeedFailedException("interrupted while reading the mail catcher", interrupted);
        }
    }

    private void sleepBriefly() {
        try {
            Thread.sleep(250);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new SeedFailedException("interrupted while waiting for mail", interrupted);
        }
    }
}
