package com.pwaquatics.shop.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class TotpTest {

    /** The SHA-1 key of RFC 6238, Appendix B. */
    private static final byte[] RFC_KEY = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    /** RFC 6238 Appendix B lists eight digits; apps show the last six. */
    @Test
    void matchesTheTestVectorsOfRfc6238() {
        assertThat(codeAt(59)).isEqualTo("287082");
        assertThat(codeAt(1111111109)).isEqualTo("081804");
        assertThat(codeAt(1111111111)).isEqualTo("050471");
        assertThat(codeAt(1234567890)).isEqualTo("005924");
        assertThat(codeAt(2000000000)).isEqualTo("279037");
        assertThat(codeAt(20000000000L)).isEqualTo("353130");
    }

    @Test
    void acceptsTheCurrentStepAndOneEitherSide() {
        Instant now = Instant.ofEpochSecond(1_800_000_015);
        long step = Totp.step(now);

        assertThat(Totp.match(RFC_KEY, Totp.code(RFC_KEY, step), now, 0)).isEqualTo(OptionalLong.of(step));
        assertThat(Totp.match(RFC_KEY, Totp.code(RFC_KEY, step - 1), now, 0)).isEqualTo(OptionalLong.of(step - 1));
        assertThat(Totp.match(RFC_KEY, Totp.code(RFC_KEY, step + 1), now, 0)).isEqualTo(OptionalLong.of(step + 1));
        assertThat(Totp.match(RFC_KEY, Totp.code(RFC_KEY, step - 2), now, 0)).isEmpty();
        assertThat(Totp.match(RFC_KEY, Totp.code(RFC_KEY, step + 2), now, 0)).isEmpty();
    }

    @Test
    void acceptsEachCodeOnceAndNothingOlderThanTheLastOne() {
        Instant now = Instant.ofEpochSecond(1_800_000_015);
        long step = Totp.step(now);
        String code = Totp.code(RFC_KEY, step);

        assertThat(Totp.match(RFC_KEY, code, now, step)).isEmpty();
        assertThat(Totp.match(RFC_KEY, Totp.code(RFC_KEY, step - 1), now, step)).isEmpty();
        assertThat(Totp.match(RFC_KEY, Totp.code(RFC_KEY, step + 1), now, step)).isEqualTo(OptionalLong.of(step + 1));
    }

    @Test
    void refusesTextThatIsNotASixDigitCode() {
        Instant now = Instant.ofEpochSecond(59);
        assertThat(Totp.match(RFC_KEY, "28708", now, 0)).isEmpty();
        assertThat(Totp.match(RFC_KEY, "2870820", now, 0)).isEmpty();
        assertThat(Totp.match(RFC_KEY, "28708a", now, 0)).isEmpty();
        assertThat(Totp.match(RFC_KEY, "", now, 0)).isEmpty();
        assertThat(Totp.match(RFC_KEY, "287082", now, 0)).isPresent();
    }

    private static String codeAt(long epochSecond) {
        return Totp.code(RFC_KEY, Totp.step(Instant.ofEpochSecond(epochSecond)));
    }
}
