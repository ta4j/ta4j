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

class AmountBarBuilderFactoryTest {

    @Test
    void constructorRejectsNonPositiveAmountThreshold() {
        assertThrows(IllegalArgumentException.class, () -> new AmountBarBuilderFactory(0, true));
        assertThrows(IllegalArgumentException.class, () -> new AmountBarBuilderFactory(-1, true));
    }

    @Test
    void deserializationRejectsNonPositiveThresholdAndMissingCarryOverPolicy() {
        SerializedAmountBarBuilderFactoryState state = new SerializedAmountBarBuilderFactoryState();
        state.amountThreshold = 0;
        assertThrows(InvalidObjectException.class, () -> restoreFactoryState(state));

        SerializedAmountBarBuilderFactoryState nullPolicyState = new SerializedAmountBarBuilderFactoryState();
        nullPolicyState.amountThreshold = 10;
        nullPolicyState.carryOverPolicy = null;
        assertThrows(InvalidObjectException.class, () -> restoreFactoryState(nullPolicyState));
    }

    private static final class SerializedAmountBarBuilderFactoryState implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private int amountThreshold;
        private boolean setAmountByVolume;
        private RemainderCarryOverPolicy carryOverPolicy = RemainderCarryOverPolicy.NONE;
        private boolean realtimeBars;
    }

    private static Object restoreFactoryState(SerializedAmountBarBuilderFactoryState state) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(state);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())) {
            @Override
            protected ObjectStreamClass readClassDescriptor() throws IOException, ClassNotFoundException {
                ObjectStreamClass descriptor = super.readClassDescriptor();
                return descriptor.getName().equals(SerializedAmountBarBuilderFactoryState.class.getName())
                        ? ObjectStreamClass.lookup(AmountBarBuilderFactory.class)
                        : descriptor;
            }
        }) {
            return input.readObject();
        }
    }
}
