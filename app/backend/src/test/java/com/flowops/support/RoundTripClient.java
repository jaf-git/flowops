package com.flowops.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

public final class RoundTripClient {
    private static final String CSRF_COOKIE = "XSRF-TOKEN";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";

    private final TestRestTemplate rest;
    private final Map<String, String> cookies = new LinkedHashMap<>();

    public RoundTripClient(TestRestTemplate rest) {
        this.rest = rest;
    }

    public void forget() {
        cookies.clear();
    }

    public ResponseEntity<String> get(String path) {
        return exchange(HttpMethod.GET, path, null);
    }

    public ResponseEntity<String> post(String path, String body) {
        return exchange(HttpMethod.POST, path, body);
    }

    public ResponseEntity<String> patch(String path, String body) {
        return exchange(HttpMethod.PATCH, path, body);
    }

    public ResponseEntity<String> delete(String path) {
        return exchange(HttpMethod.DELETE, path, null);
    }

    public ResponseEntity<String> exchange(HttpMethod method, String path, String body) {
        HttpHeaders headers = baseHeaders();
        if (cookies.containsKey(CSRF_COOKIE)) {
            headers.add(CSRF_HEADER, cookies.get(CSRF_COOKIE));
        }
        return send(method, path, body, headers);
    }

    public ResponseEntity<String> withoutCrossSiteToken(HttpMethod method, String path, String body) {
        return send(method, path, body, baseHeaders());
    }

    public ResponseEntity<String> withWrongCrossSiteToken(HttpMethod method, String path, String body) {
        HttpHeaders headers = baseHeaders();
        headers.add(CSRF_HEADER, "not-the-token-you-were-given");
        return send(method, path, body, headers);
    }

    public SseConversation openEventStream(String path, Long resumeFrom) {
        Map<String, String> headers = new LinkedHashMap<>();

        if (!cookies.isEmpty()) {
            headers.put(HttpHeaders.COOKIE, cookieHeader());
        }
        if (cookies.containsKey(CSRF_COOKIE)) {
            headers.put(CSRF_HEADER, cookies.get(CSRF_COOKIE));
        }
        if (resumeFrom != null) {
            headers.put("Last-Event-ID", Long.toString(resumeFrom));
        }

        return SseConversation.opening(rest.getRootUri() + path, headers);
    }

    public ResponseEntity<byte[]> postForBytes(String path, String body) {
        HttpHeaders headers = baseHeaders();
        if (cookies.containsKey(CSRF_COOKIE)) {
            headers.add(CSRF_HEADER, cookies.get(CSRF_COOKIE));
        }
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), byte[].class);
    }

    public boolean holdsSession() {
        return cookies.containsKey("SESSION");
    }

    public String cookie(String name) {
        return cookies.get(name);
    }

    public RoundTripClient replaying(String name, String value) {
        cookies.put(name, value);
        return this;
    }

    private HttpHeaders baseHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (!cookies.isEmpty()) {
            headers.add(HttpHeaders.COOKIE, cookieHeader());
        }
        return headers;
    }

    private ResponseEntity<String> send(HttpMethod method, String path, String body, HttpHeaders headers) {
        ResponseEntity<String> response = rest.exchange(path, method, new HttpEntity<>(body, headers), String.class);
        rememberCookiesFrom(response);
        return response;
    }

    private void rememberCookiesFrom(ResponseEntity<String> response) {
        List<String> setCookie = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (setCookie == null) {
            return;
        }
        for (String header : setCookie) {
            String pair = header.split(";", 2)[0];
            int equals = pair.indexOf('=');
            if (equals > 0) {
                String name = pair.substring(0, equals);
                String value = pair.substring(equals + 1);
                if (value.isEmpty()) {
                    cookies.remove(name);
                } else {
                    cookies.put(name, value);
                }
            }
        }
    }

    private String cookieHeader() {
        List<String> pairs = new ArrayList<>();
        cookies.forEach((name, value) -> pairs.add(name + "=" + value));
        return String.join("; ", pairs);
    }
}
