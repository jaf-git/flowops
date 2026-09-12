package com.flowops.support;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public final class SseConversation implements AutoCloseable {
    public record SseEvent(String id, String name, String data) {}

    private static final int READ_TIMEOUT_MS = 30_000;

    private static final int CONNECT_TIMEOUT_MS = 10_000;

    private final BlockingQueue<SseEvent> received = new LinkedBlockingQueue<>();

    private volatile HttpURLConnection connection;
    private volatile int status;
    private volatile boolean stopped;
    private volatile Exception failure;

    static SseConversation opening(String url, Map<String, String> headers) {
        SseConversation conversation = new SseConversation();

        Thread reader = new Thread(() -> conversation.read(url, headers), "sse " + url);
        reader.setDaemon(true);
        reader.start();

        return conversation;
    }

    private void read(String url, Map<String, String> headers) {
        HttpURLConnection opened = null;

        try {
            opened = (HttpURLConnection) URI.create(url).toURL().openConnection();
            opened.setRequestProperty("Accept", "text/event-stream");
            headers.forEach(opened::setRequestProperty);
            opened.setConnectTimeout(CONNECT_TIMEOUT_MS);
            opened.setReadTimeout(READ_TIMEOUT_MS);
            opened.connect();

            status = opened.getResponseCode();
            connection = opened;

            InputStream body = status >= 400 ? opened.getErrorStream() : opened.getInputStream();

            if (body == null) {
                return;
            }

            try (BufferedReader lines = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
                String id = null;
                String name = null;
                StringBuilder data = new StringBuilder();
                String line;

                while (!stopped && (line = lines.readLine()) != null) {
                    if (line.isEmpty()) {
                        if (name != null || !data.isEmpty()) {
                            received.add(new SseEvent(id, name, data.toString()));
                        }
                        id = null;
                        name = null;
                        data.setLength(0);
                    } else if (line.startsWith("id:")) {
                        id = line.substring(3).trim();
                    } else if (line.startsWith("event:")) {
                        name = line.substring(6).trim();
                    } else if (line.startsWith("data:")) {
                        data.append(line.substring(5).trim());
                    }
                }
            }
        } catch (Exception ended) {
            failure = ended;
        } finally {
            if (opened != null) {
                opened.disconnect();
            }
        }
    }

    public SseEvent awaiting(String name, Duration within) throws InterruptedException {
        long deadline = System.nanoTime() + within.toNanos();
        StringBuilder seen = new StringBuilder();

        while (System.nanoTime() < deadline) {
            SseEvent event = received.poll(50, TimeUnit.MILLISECONDS);

            if (event == null) {
                continue;
            }
            if (name.equals(event.name())) {
                return event;
            }
            seen.append(event.name()).append(' ');
        }

        throw new AssertionError("no `%s` frame arrived within %s. Status %d, frames seen [%s]%s"
                .formatted(name, within, status, seen.toString().trim(), failure == null ? "" : ", after " + failure));
    }

    public SseConversation established(Duration within) throws InterruptedException {
        long deadline = System.nanoTime() + within.toNanos();

        while (connection == null && failure == null && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }

        if (connection == null) {
            throw new AssertionError("the stream never opened" + (failure == null ? "" : ": " + failure));
        }

        return this;
    }

    public int status() {
        return status;
    }

    @Override
    public void close() {
        stopped = true;

        if (connection != null) {
            connection.disconnect();
        }
    }
}
