package com.flowops.canvas.application.stream;

public interface CanvasSink {
    void send(CanvasDelta delta) throws Exception;

    void keepAlive() throws Exception;

    void close();
}
