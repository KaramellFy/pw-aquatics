package com.pwaquatics.shop.pages.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pwaquatics.shop.pages.application.PageShell;
import com.pwaquatics.shop.pages.application.PagesProperties;
import com.pwaquatics.shop.pages.application.ShellSource.ShellUnavailableException;
import com.pwaquatics.shop.support.FakeShellSource;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** Loading the page template from a stand-in frontend container. */
class FrontendShellSourceTest {

    private HttpServer frontend;
    private volatile String template = FakeShellSource.TEMPLATE;
    private volatile String etag = "\"build-1\"";
    private final AtomicInteger fullLoads = new AtomicInteger();
    private final AtomicInteger notModified = new AtomicInteger();
    private final MutableClock clock = new MutableClock();

    @BeforeEach
    void startFrontend() throws IOException {
        frontend = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        frontend.createContext("/index.html", exchange -> {
            if (etag.equals(exchange.getRequestHeaders().getFirst("If-None-Match"))) {
                notModified.incrementAndGet();
                exchange.sendResponseHeaders(304, -1);
                exchange.close();
                return;
            }
            fullLoads.incrementAndGet();
            exchange.getResponseHeaders().add("ETag", etag);
            send(exchange, template);
        });
        frontend.createContext("/page-texts.json", exchange -> send(exchange, FakeShellSource.TEXTS));
        frontend.start();
    }

    @AfterEach
    void stopFrontend() {
        frontend.stop(0);
    }

    @Test
    void loadsTheTemplateAndChecksForANewBuildWithoutDownloadingItAgain() {
        FrontendShellSource source = source();
        PageShell first = source.current();
        assertThat(first.render("en", null, null, null)).contains("index-abc.js");

        // Within a second the known template is used as it is.
        assertThat(source.current()).isSameAs(first);
        assertThat(fullLoads).hasValue(1);
        assertThat(notModified).hasValue(0);

        // Later the frontend is asked again; unchanged, it answers 304.
        clock.advance(Duration.ofSeconds(2));
        assertThat(source.current()).isSameAs(first);
        assertThat(notModified).hasValue(1);

        // A new frontend build is picked up at the next check.
        template = FakeShellSource.TEMPLATE.replace("index-abc", "index-def");
        etag = "\"build-2\"";
        clock.advance(Duration.ofSeconds(2));
        assertThat(source.current().render("en", null, null, null)).contains("index-def.js");
        assertThat(fullLoads).hasValue(2);
    }

    @Test
    void keepsTheLastTemplateWhileTheFrontendIsDown() {
        FrontendShellSource source = source();
        PageShell known = source.current();
        frontend.stop(0);
        clock.advance(Duration.ofSeconds(2));
        assertThat(source.current()).isSameAs(known);
    }

    @Test
    void withoutATemplatePagesAreNotFilledIn() {
        frontend.stop(0);
        assertThatThrownBy(source()::current).isInstanceOf(ShellUnavailableException.class);

        FrontendShellSource switchedOff = new FrontendShellSource(
                new PagesProperties(null, Duration.ofSeconds(1)), new ObjectMapper(), clock);
        assertThatThrownBy(switchedOff::current).isInstanceOf(ShellUnavailableException.class);
    }

    private FrontendShellSource source() {
        URI url = URI.create("http://127.0.0.1:" + frontend.getAddress().getPort() + "/");
        return new FrontendShellSource(new PagesProperties(url, Duration.ofSeconds(1)), new ObjectMapper(), clock);
    }

    private static void send(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-16T12:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
