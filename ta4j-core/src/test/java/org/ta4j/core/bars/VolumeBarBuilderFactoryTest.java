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

class VolumeBarBuilderFactoryTest {

    @Test
    void constructorRejectsNonPositiveVolumeThreshold() {
        assertThrows(IllegalArgumentException.class, () -> new VolumeBarBuilderFactory(0));
        assertThrows(IllegalArgumentException.class, () -> new VolumeBarBuilderFactory(-1));
    }

    @Test
    void deserializationRejectsNonPositiveThresholdAndMissingCarryOverPolicy() {
        SerializedVolumeBarBuilderFactoryState state = new SerializedVolumeBarBuilderFactoryState();
        state.volumeThreshold = 0;
        assertThrows(InvalidObjectException.class, () -> restoreFactoryState(state));

        SerializedVolumeBarBuilderFactoryState nullPolicyState = new SerializedVolumeBarBuilderFactoryState();
        nullPolicyState.volumeThreshold = 10;
        nullPolicyState.carryOverPolicy = null;
        assertThrows(InvalidObjectException.class, () -> restoreFactoryState(nullPolicyState));
    }

    private static final class SerializedVolumeBarBuilderFactoryState implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private int volumeThreshold;
        private RemainderCarryOverPolicy carryOverPolicy = RemainderCarryOverPolicy.NONE;
        private boolean realtimeBars;
    }

    private static Object restoreFactoryState(SerializedVolumeBarBuilderFactoryState state) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(state);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())) {
            @Override
            protected ObjectStreamClass readClassDescriptor() throws IOException, ClassNotFoundException {
                ObjectStreamClass descriptor = super.readClassDescriptor();
                return descriptor.getName().equals(SerializedVolumeBarBuilderFactoryState.class.getName())
                        ? ObjectStreamClass.lookup(VolumeBarBuilderFactory.class)
                        : descriptor;
            }
        }) {
            return input.readObject();
        }
    }
}
