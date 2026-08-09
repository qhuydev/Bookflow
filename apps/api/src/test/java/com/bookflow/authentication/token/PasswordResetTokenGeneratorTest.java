package com.bookflow.authentication.token;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordResetTokenGeneratorTest {
    @Test
    void generatesIndependentOpaqueTokensAndStableSha256Hashes() {
        var generator = new PasswordResetTokenGenerator();
        var first = generator.generate();
        var second = generator.generate();

        assertThat(first.rawToken()).isNotEqualTo(second.rawToken());
        assertThat(first.hash()).hasSize(64).isEqualTo(generator.sha256(first.rawToken()));
        assertThat(second.hash()).hasSize(64).isEqualTo(generator.sha256(second.rawToken()));
        assertThat(first.hash()).isNotEqualTo(first.rawToken());
    }
}
