/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.num;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.Test;

class DoubleNumFactoryTest {

    private static final NumFactory doubleFactory = DoubleNumFactory.getInstance();

    @Test
    final void testDefaultNum() {
        assertNumEquals(-1, doubleFactory.minusOne());

        assertNumEquals(-0, doubleFactory.zero());
        assertNumEquals(0, doubleFactory.zero());
        assertNumEquals(+0, doubleFactory.zero());

        assertNumEquals(1, doubleFactory.one());
        assertNumEquals(2, doubleFactory.two());
        assertNumEquals(3, doubleFactory.three());
        assertNumEquals(100, doubleFactory.hundred());
        assertNumEquals(1000, doubleFactory.thousand());
    }

    @Test
    final void testProduces() {
        var decimalFactory = DecimalNumFactory.getInstance();

        assertTrue(doubleFactory.produces(null));
        assertTrue(doubleFactory.produces(NaN.NaN));
        assertTrue(doubleFactory.produces(doubleFactory.one()));
        assertFalse(doubleFactory.produces(decimalFactory.one()));
    }

    @Test
    final void serializationPreservesSingletonInstance() throws Exception {
        byte[] serialized;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
                ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(DoubleNumFactory.getInstance());
            serialized = output.toByteArray();
        }

        try (ByteArrayInputStream input = new ByteArrayInputStream(serialized);
                ObjectInputStream objectInput = new ObjectInputStream(input)) {
            assertSame(DoubleNumFactory.getInstance(), objectInput.readObject());
        }
    }
}
