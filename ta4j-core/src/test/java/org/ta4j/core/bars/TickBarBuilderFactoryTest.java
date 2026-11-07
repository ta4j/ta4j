/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.bars;

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

import org.junit.jupiter.api.Test;

class TickBarBuilderFactoryTest {

    @Test
    void constructorRejectsNonPositiveTickCount() {
        assertThrows(IllegalArgumentException.class, () -> new TickBarBuilderFactory(0));
        assertThrows(IllegalArgumentException.class, () -> new TickBarBuilderFactory(-1));
    }

    @Test
    void deserializationRejectsNonPositiveTickCount() {
        SerializedTickBarBuilderFactoryState state = new SerializedTickBarBuilderFactoryState();
        state.tickCount = 0;
        assertThrows(InvalidObjectException.class, () -> restoreFactoryState(state));
    }

    private static final class SerializedTickBarBuilderFactoryState implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private boolean realtimeBars;
        private int tickCount;
    }

    private static Object restoreFactoryState(SerializedTickBarBuilderFactoryState state) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(state);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())) {
            @Override
            protected ObjectStreamClass readClassDescriptor() throws IOException, ClassNotFoundException {
                ObjectStreamClass descriptor = super.readClassDescriptor();
                return descriptor.getName().equals(SerializedTickBarBuilderFactoryState.class.getName())
                        ? ObjectStreamClass.lookup(TickBarBuilderFactory.class)
                        : descriptor;
            }
        }) {
            return input.readObject();
        }
    }
}
