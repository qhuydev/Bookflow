package com.bookflow;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BookFlowApplicationUnitTest {

    @Test
    void applicationClassUsesRootPackage() {
        assertThat(BookFlowApplication.class.getPackageName()).isEqualTo("com.bookflow");
    }

    @Test
    void testsRunOnJava21() {
        assertThat(Runtime.version().feature()).isEqualTo(21);
    }
}
