package com.flowops.aiexport.seed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SeedHttpClient {
    private static final String SESSION_COOKIE = "SESSION";
    private static final String CSRF_COOKIE = "XSRF-TOKEN";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, String> cookies = new LinkedHashMap<>();
    private final String root;

    SeedHttpClient(String root) {
        this.root = root;
    }

    void forget() {
        cookies.clear();
    }

    boolean holdsSession() {
        return cookies.containsKey(SESSION_COOKIE);
    }

    void mintCrossSiteToken() {
        get("/api/auth/session");
    }

    Response get(String path) {
        return send("GET", path, null);
    }

    Response post(String path, String body) {
        return send("POST", path, body);
    }

    Response patch(String path, String body) {
        return send("PATCH", path, body);
    }

    Response put(String path, String body) {
        return send("PUT", path, body);
    }

    JsonNode postOrFail(String path, String body, String what) {
        Response response = post(path, body);
        response.orFail(what);
        return response.body();
    }

    JsonNode getOrFail(String path, String what) {
        Response response = get(path);
        response.orFail(what);
        return response.body();
    }

    JsonNode patchOrFail(String path, String body, String what) {
        Response response = patch(path, body);
        response.orFail(what);
        return response.body();
    }

    JsonNode putOrFail(String path, String body, String what) {
        Response response = put(path, body);
        response.orFail(what);
        return response.body();
    }

    private Response send(String method, String path, String body) {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(root + path))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json");

        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        if (!cookies.isEmpty()) {
            request.header("Cookie", cookieHeader());
        }
        if (cookies.containsKey(CSRF_COOKIE)) {
            request.header(CSRF_HEADER, cookies.get(CSRF_COOKIE));
        }
        request.method(
                method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));

        try {
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            remember(response);
            return new Response(method, path, response.statusCode(), response.body(), json);
        } catch (IOException failure) {
            throw new SeedFailedException(method + " " + path + " could not be sent", failure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new SeedFailedException(method + " " + path + " was interrupted", interrupted);
        }
    }

    private void remember(HttpResponse<String> response) {
        List<String> set = response.headers().allValues("set-cookie");
        for (String each : set) {
            String pair = each.split(";", 2)[0];
            int equals = pair.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            String name = pair.substring(0, equals).trim();
            String value = pair.substring(equals + 1).trim();
            if (value.isEmpty()) {
                cookies.remove(name);
            } else {
                cookies.put(name, value);
            }
        }
    }

    private String cookieHeader() {
        return cookies.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((left, right) -> left + "; " + right)
                .orElse("");
    }

    record Response(String method, String path, int status, String raw, ObjectMapper json) {
        boolean ok() {
            return status >= 200 && status < 300;
        }

        JsonNode body() {
            if (raw == null || raw.isBlank()) {
                return com.fasterxml.jackson.databind.node.NullNode.getInstance();
            }
            try {
                return json.readTree(raw);
            } catch (IOException malformed) {
                throw new SeedFailedException(
                        method + " " + path + " answered something that is not JSON: " + raw, malformed);
            }
        }

        void orFail(String what) {
            if (!ok()) {
                throw new SeedFailedException(
                        what + " failed: " + method + " " + path + " answered " + status + " " + raw);
            }
        }
    }
}
