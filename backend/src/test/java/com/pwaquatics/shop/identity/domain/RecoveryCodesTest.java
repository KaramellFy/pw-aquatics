package com.pwaquatics.shop.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecoveryCodesTest {

    @Test
    void generatesTenDifferentReadableCodes() {
        List<String> codes = RecoveryCodes.generate();

        assertThat(codes).hasSize(RecoveryCodes.COUNT);
        assertThat(new HashSet<>(codes)).hasSize(RecoveryCodes.COUNT);
        assertThat(codes).allMatch(code -> code.matches("[a-z2-7]{4}-[a-z2-7]{4}-[a-z2-7]{4}"));
        assertThat(codes).allMatch(code -> RecoveryCodes.isCodeShaped(RecoveryCodes.normalise(code)));
    }

    @Test
    void ignoresCaseSpacesAndDashesWhenTyped() {
        assertThat(RecoveryCodes.normalise(" ABCD-efgh 2345 ")).isEqualTo("abcdefgh2345");
        assertThat(RecoveryCodes.isCodeShaped("abcdefgh2345")).isTrue();
        assertThat(RecoveryCodes.isCodeShaped("abcdefgh234")).isFalse();
        assertThat(RecoveryCodes.isCodeShaped("abcdefgh2341")).isFalse();
        assertThat(RecoveryCodes.isCodeShaped("123456")).isFalse();
    }
}
