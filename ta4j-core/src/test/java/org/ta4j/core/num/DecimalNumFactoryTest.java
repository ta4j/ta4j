/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.num;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serial;
import java.io.Serializable;
import java.math.MathContext;

import org.junit.jupiter.api.Test;

class DecimalNumFactoryTest {

    private static final NumFactory decimalFactory = DecimalNumFactory.getInstance();

    @Test
    final void testDefaultNum() {
        assertNumEquals(-1, decimalFactory.minusOne());

        assertNumEquals(-0, decimalFactory.zero());
        assertNumEquals(0, decimalFactory.zero());
        assertNumEquals(+0, decimalFactory.zero());

        assertNumEquals(1, decimalFactory.one());
        assertNumEquals(2, decimalFactory.two());
        assertNumEquals(3, decimalFactory.three());
        assertNumEquals(100, decimalFactory.hundred());
        assertNumEquals(1000, decimalFactory.thousand());
    }

    @Test
    final void testProduces() {
        var doubleFactory = DoubleNumFactory.getInstance();

        assertTrue(decimalFactory.produces(null));
        assertTrue(decimalFactory.produces(NaN.NaN));
        assertTrue(decimalFactory.produces(decimalFactory.one()));
        assertFalse(decimalFactory.produces(doubleFactory.one()));
    }

    @Test
    void shouldComputeExpUsingConfiguredPrecision() {
        NumFactory highPrecisionFactory = DecimalNumFactory.getInstance(40);
        Num one = highPrecisionFactory.one();
        Num e = one.exp();

        assertNumEquals("2.718281828459045235360287471352662497761", e);
    }

    @Test
    void testSerializationRoundTrip() throws Exception {
        final NumFactory original = DecimalNumFactory.getInstance(32);
        final NumFactory restored = (NumFactory) deserialize(serialize(original));

        assertEquals(original.one(), restored.one());
        assertEquals(original.zero(), restored.zero());
        assertEquals(original.minusOne(), restored.minusOne());
        assertEquals(original.numOf("123.456"), restored.numOf("123.456"));
    }

    @Test
    void testDeserializationRejectsNullMathContext() {
        final MathContext mc = MathContext.DECIMAL64;
        final SerializedDecimalNumFactoryState state = new SerializedDecimalNumFactoryState(null,
                DecimalNum.valueOf(-1, mc), DecimalNum.valueOf(0, mc), DecimalNum.valueOf(1, mc),
                DecimalNum.valueOf(2, mc), DecimalNum.valueOf(3, mc), DecimalNum.valueOf(100, mc),
                DecimalNum.valueOf(1000, mc));
        assertThrows(InvalidObjectException.class, () -> restoreState(state));
    }

    @Test
    void testDeserializationRejectsNullCachedConstant() {
        final MathContext mc = MathContext.DECIMAL64;
        final SerializedDecimalNumFactoryState state = new SerializedDecimalNumFactoryState(mc, null,
                DecimalNum.valueOf(0, mc), DecimalNum.valueOf(1, mc), DecimalNum.valueOf(2, mc),
                DecimalNum.valueOf(3, mc), DecimalNum.valueOf(100, mc), DecimalNum.valueOf(1000, mc));
        assertThrows(InvalidObjectException.class, () -> restoreState(state));
    }

    private static byte[] serialize(final Object value) throws IOException {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
            out.flush();
            return bytes.toByteArray();
        }
    }

    private static Object deserialize(final byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return in.readObject();
        }
    }

    private static Object restoreState(final SerializedDecimalNumFactoryState state) throws Exception {
        final byte[] bytes = serialize(state);
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes)) {
            @Override
            protected ObjectStreamClass readClassDescriptor() throws IOException, ClassNotFoundException {
                ObjectStreamClass descriptor = super.readClassDescriptor();
                return descriptor.getName().equals(SerializedDecimalNumFactoryState.class.getName())
                        ? ObjectStreamClass.lookup(DecimalNumFactory.class)
                        : descriptor;
            }
        }) {
            return input.readObject();
        }
    }

    private static final class SerializedDecimalNumFactoryState implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private final MathContext mathContext;
        private final DecimalNum minusOne;
        private final DecimalNum zero;
        private final DecimalNum one;
        private final DecimalNum two;
        private final DecimalNum three;
        private final DecimalNum hundred;
        private final DecimalNum thousand;

        private SerializedDecimalNumFactoryState(final MathContext mathContext, final DecimalNum minusOne,
                final DecimalNum zero, final DecimalNum one, final DecimalNum two, final DecimalNum three,
                final DecimalNum hundred, final DecimalNum thousand) {
            this.mathContext = mathContext;
            this.minusOne = minusOne;
            this.zero = zero;
            this.one = one;
            this.two = two;
            this.three = three;
            this.hundred = hundred;
            this.thousand = thousand;
        }
    }

}
