/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.bars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;

import org.junit.jupiter.api.Test;

class TimeBarBuilderFactoryTest {

    @Test
    void constructorRejectsNonPositiveTimePeriod() {
        assertThrows(IllegalArgumentException.class, () -> new TimeBarBuilderFactory(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new TimeBarBuilderFactory(Duration.ofSeconds(-1)));
        assertThrows(IllegalArgumentException.class, () -> new TimeBarBuilderFactory(Duration.ZERO, true));
        assertThrows(IllegalArgumentException.class, () -> new TimeBarBuilderFactory(Duration.ofMinutes(-5), true));
    }

    @Test
    void roundTripSerializationPreservesState() throws Exception {
        TimeBarBuilderFactory factoryWithPeriod = new TimeBarBuilderFactory(Duration.ofMinutes(15), true);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(factoryWithPeriod);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            Object deserialized = in.readObject();
            assertNotNull(deserialized);
            assertEquals(TimeBarBuilderFactory.class, deserialized.getClass());
        }

        TimeBarBuilderFactory factoryWithoutPeriod = new TimeBarBuilderFactory(false);
        ByteArrayOutputStream bytesWithoutPeriod = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytesWithoutPeriod)) {
            out.writeObject(factoryWithoutPeriod);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytesWithoutPeriod.toByteArray()))) {
            Object deserialized = in.readObject();
            assertNotNull(deserialized);
            assertEquals(TimeBarBuilderFactory.class, deserialized.getClass());
        }
    }

    @Test
    void deserializationRejectsNonPositiveTimePeriod() {
        SerializedTimeBarBuilderFactoryState zeroPeriodState = new SerializedTimeBarBuilderFactoryState();
        zeroPeriodState.timePeriod = Duration.ZERO;
        assertThrows(InvalidObjectException.class, () -> restoreFactoryState(zeroPeriodState));

        SerializedTimeBarBuilderFactoryState negativePeriodState = new SerializedTimeBarBuilderFactoryState();
        negativePeriodState.timePeriod = Duration.ofSeconds(-10);
        assertThrows(InvalidObjectException.class, () -> restoreFactoryState(negativePeriodState));
    }

    private static final class SerializedTimeBarBuilderFactoryState implements Serializable {
        @Serial
        private static final long serialVersionUID = -1417573901424958954L;
        private boolean realtimeBars;
        private Duration timePeriod;
    }

    private static Object restoreFactoryState(SerializedTimeBarBuilderFactoryState state) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(state);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())) {
            @Override
            protected ObjectStreamClass readClassDescriptor() throws IOException, ClassNotFoundException {
                ObjectStreamClass descriptor = super.readClassDescriptor();
                return descriptor.getName().equals(SerializedTimeBarBuilderFactoryState.class.getName())
                        ? ObjectStreamClass.lookup(TimeBarBuilderFactory.class)
                        : descriptor;
            }
        }) {
            return input.readObject();
        }
    }
}
