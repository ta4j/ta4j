/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ExecutionIntentTest {

    @Test
    void rejectsBlankIntentId() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExecutionIntent(" ", ExecutionSide.BUY, Instant.now(), null));
    }

    @Test
    void rejectsNullSide() {
        assertThrows(NullPointerException.class, () -> new ExecutionIntent("intent-1", null, Instant.now(), null));
    }

    @Test
    void rejectsNullCreatedAt() {
        assertThrows(NullPointerException.class, () -> new ExecutionIntent("intent-1", ExecutionSide.BUY, null, null));
    }

    @Test
    void rejectsBlankCorrelationId() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExecutionIntent("intent-1", ExecutionSide.BUY, Instant.now(), " "));
    }
}
