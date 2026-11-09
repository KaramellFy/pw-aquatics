package com.pwaquatics.shop.support;

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Helpers for MockMvc requests. */
public final class TestRequests {

    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);

    private TestRequests() {}

    /** A unique client IP per call, so per-IP rate limits don't leak between tests. */
    public static RequestPostProcessor uniqueClientIp() {
        int n = NEXT_IP.getAndIncrement();
        String ip = "10.%d.%d.%d".formatted((n >> 16) & 255, (n >> 8) & 255, n & 255);
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
