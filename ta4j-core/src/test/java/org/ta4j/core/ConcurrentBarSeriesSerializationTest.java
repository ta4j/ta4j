/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.Collections;

import org.junit.jupiter.api.Test;

/**
 * Structural deserialization invariants are independent of numeric precision.
 */
class ConcurrentBarSeriesSerializationTest {

    @Test
    void deserializationRejectsMissingClassData() {
        InvalidObjectException failure = assertThrows(InvalidObjectException.class,
                () -> restoreWithoutConcurrentSeriesData(new SerializedSubclassState()));
        assertEquals("ConcurrentBarSeries class data is required", failure.getMessage());
    }

    private Object restoreWithoutConcurrentSeriesData(SerializedSubclassState state) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(state);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())) {
            @Override
            protected ObjectStreamClass readClassDescriptor() throws IOException, ClassNotFoundException {
                ObjectStreamClass descriptor = super.readClassDescriptor();
                return descriptor.getName().equals(SerializedSubclassState.class.getName())
                        ? ObjectStreamClass.lookup(ConcurrentBarSeriesSubclass.class)
                        : descriptor;
            }
        }) {
            return input.readObject();
        }
    }

    /**
     * Writes a stream whose hierarchy holds no {@link ConcurrentBarSeries} class
     * data, as a subclass saved before it extended the concurrent series would.
     */
    private static final class SerializedSubclassState implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }

    private static final class ConcurrentBarSeriesSubclass extends ConcurrentBarSeries {
        @Serial
        private static final long serialVersionUID = 1L;

        private ConcurrentBarSeriesSubclass() {
            super("test", Collections.emptyList());
        }
    }
}
