/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
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
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.LinearBorrowingCostModel;
import org.ta4j.core.analysis.cost.LinearTransactionCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

class PositionSerializationTest {

    private static Stream<NumFactory> numFactories() {
        return Stream.of(DoubleNumFactory.getInstance(), DecimalNumFactory.getInstance());
    }

    @ParameterizedTest
    @MethodSource("numFactories")
    public void validNewOpenAndClosedPositionsRemainOperational(NumFactory factory) throws Exception {
        for (TradeType type : TradeType.values()) {
            Position restored = restore(
                    new Position(type, new LinearTransactionCostModel(0.01), new LinearBorrowingCostModel(0.01)));
            assertTrue(restored.isNew());
            assertTrue(restored.getTransactionCostModel() instanceof ZeroCostModel);
            assertTrue(restored.getHoldingCostModel() instanceof ZeroCostModel);
            restored.operate(1, factory.numOf(100), factory.one());
            restored = restore(restored);
            assertTrue(restored.isOpened());
            assertEquals(type, restored.getEntry().getType());
            assertNumEquals(0, restored.getPositionCost(3));
            assertNumEquals(0, restored.getHoldingCost(3));
            restored.operate(3, factory.numOf(110), factory.one());
            restored = restore(restored);
            assertTrue(restored.isClosed());
            assertEquals(type.complementType(), restored.getExit().getType());
            assertNumEquals(0, restored.getPositionCost());
            assertNumEquals(0, restored.getPositionCost(4));
            assertNumEquals(0, restored.getHoldingCost());
            assertNumEquals(0, restored.getHoldingCost(4));
            assertNumEquals(type == TradeType.BUY ? 10 : -10, restored.getProfit());
            assertFalse(restored.hasRecordedTransactionCosts());
        }
    }

    @ParameterizedTest
    @MethodSource("numFactories")
    public void constructorSupportedEarlierAndEqualExitIndicesRemainValid(NumFactory factory) throws Exception {
        for (TradeType type : TradeType.values()) {
            for (int exitIndex : new int[] { 2, 5 }) {
                Trade entry = new BaseTrade(5, type, factory.numOf(100), factory.one());
                Trade exit = new BaseTrade(exitIndex, type.complementType(), factory.numOf(110), factory.one());
                Position original = new Position(entry, exit);
                Position restored = restore(original);
                assertEquals(original, restored);
                assertEquals(exitIndex, restored.getExit().getIndex());
                assertNumEquals(0, restored.getPositionCost());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("numFactories")
    public void serializableCustomTradeModelsRemainEffectiveAfterLoading(NumFactory factory) throws Exception {
        CostModel model = new SerializableFeeModel(2);
        Trade entry = new PersistentModelTrade(1, TradeType.BUY, factory.numOf(100), factory.one(), model);
        Trade exit = new PersistentModelTrade(3, TradeType.SELL, factory.numOf(110), factory.one(), model);
        Position closed = restore(new Position(entry, exit, model, new ZeroCostModel()));
        assertSame(closed.getEntry().getCostModel(), closed.getTransactionCostModel());
        assertNumEquals(4, closed.getPositionCost());
        assertNumEquals(4, closed.getPositionCost(4));

        Position open = restore(new Position(entry, null, model, new ZeroCostModel()));
        assertNumEquals(2, open.getPositionCost(3));
        open.operate(3, factory.numOf(110), factory.one());
        assertTrue(open.isClosed());
        assertNumEquals(2, open.getExit().getCost());
        assertNumEquals(4, open.getPositionCost());
    }

    @Test
    public void deserializationRejectsNullStartingType() {
        assertThrows(InvalidObjectException.class, () -> restore(new SerializedPositionState(null, null, null)));
    }

    @Test
    public void deserializationRejectsExitWithoutEntry() {
        assertThrows(InvalidObjectException.class,
                () -> restore(new SerializedPositionState(TradeType.BUY, null, trade(2, TradeType.SELL))));
    }

    @Test
    public void deserializationRejectsStartingTypeMismatch() {
        assertThrows(InvalidObjectException.class,
                () -> restore(new SerializedPositionState(TradeType.SELL, trade(1, TradeType.BUY), null)));
    }

    @Test
    public void deserializationRejectsSameEntryAndExitTypes() {
        assertThrows(InvalidObjectException.class, () -> restore(
                new SerializedPositionState(TradeType.BUY, trade(1, TradeType.BUY), trade(2, TradeType.BUY))));
    }

    @Test
    public void deserializationRejectsNullCustomEntryType() {
        assertThrows(InvalidObjectException.class,
                () -> restore(new SerializedPositionState(TradeType.BUY, trade(1, null), null)));
    }

    @Test
    public void deserializationRejectsNullCustomExitType() {
        assertThrows(InvalidObjectException.class,
                () -> restore(new SerializedPositionState(TradeType.BUY, trade(1, TradeType.BUY), trade(2, null))));
    }

    @Test
    public void deserializationRejectsNullCustomEntryCostModel() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Trade entry = new PersistentModelTrade(1, TradeType.BUY, factory.numOf(100), factory.one(), null);
        assertThrows(InvalidObjectException.class,
                () -> restore(new SerializedPositionState(TradeType.BUY, entry, null)));
    }

    @Test
    public void deserializationRejectsNullCustomExitCostModel() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Trade exit = new PersistentModelTrade(2, TradeType.SELL, factory.numOf(110), factory.one(), null);
        assertThrows(InvalidObjectException.class,
                () -> restore(new SerializedPositionState(TradeType.BUY, trade(1, TradeType.BUY), exit)));
    }

    @Test
    public void deserializationRejectsMismatchedCustomCostModels() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Trade entry = new PersistentModelTrade(1, TradeType.BUY, factory.numOf(100), factory.one(),
                new SerializableFeeModel(2));
        Trade exit = new PersistentModelTrade(2, TradeType.SELL, factory.numOf(110), factory.one(),
                new SerializableFeeModel(3));
        assertThrows(InvalidObjectException.class,
                () -> restore(new SerializedPositionState(TradeType.BUY, entry, exit)));
    }

    @Test
    public void deserializationRejectsMissingSuperclassData() {
        assertThrows(InvalidObjectException.class, () -> restore(new LegacyPositionSubclass()));
    }

    private static Trade trade(int index, TradeType type) {
        NumFactory factory = DoubleNumFactory.getInstance();
        return new PersistentModelTrade(index, type, factory.numOf(100), factory.one(), new SerializableFeeModel(0));
    }

    private static Position restore(Object state) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(state);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())) {
            @Override
            protected ObjectStreamClass readClassDescriptor() throws IOException, ClassNotFoundException {
                ObjectStreamClass descriptor = super.readClassDescriptor();
                if (descriptor.getName().equals(SerializedPositionState.class.getName())) {
                    return ObjectStreamClass.lookup(Position.class);
                }
                if (descriptor.getName().equals(LegacyPositionSubclass.class.getName())) {
                    return ObjectStreamClass.lookup(PositionSubclass.class);
                }
                return descriptor;
            }
        }) {
            return (Position) input.readObject();
        }
    }

    /**
     * Matches Position's persisted fields without mutating production internals.
     */
    private static final class SerializedPositionState implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private final Trade entry;
        private final Trade exit;
        private final TradeType startingType;

        private SerializedPositionState(TradeType startingType, Trade entry, Trade exit) {
            this.startingType = startingType;
            this.entry = entry;
            this.exit = exit;
        }
    }

    private static final class LegacyPositionSubclass implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }

    private static final class PositionSubclass extends Position {
        @Serial
        private static final long serialVersionUID = 1L;
    }

    private static final class PersistentModelTrade extends BaseTrade {
        @Serial
        private static final long serialVersionUID = 1L;
        private final TradeType savedType;
        private final CostModel savedModel;

        private PersistentModelTrade(int index, TradeType type, Num price, Num amount, CostModel model) {
            super(index, type == null ? TradeType.BUY : type, price, amount,
                    model == null ? new ZeroCostModel() : model);
            savedType = type;
            savedModel = model;
        }

        @Override
        public TradeType getType() {
            return savedType;
        }

        @Override
        public CostModel getCostModel() {
            return savedModel;
        }
    }

    private static final class SerializableFeeModel implements CostModel, Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private final int fee;

        private SerializableFeeModel(int fee) {
            this.fee = fee;
        }

        @Override
        public Num calculate(Position position, int finalIndex) {
            Num total = calculate(position.getEntry().getValue(), position.getEntry().getAmount());
            return position.getExit() == null ? total
                    : total.plus(calculate(position.getExit().getValue(), position.getExit().getAmount()));
        }

        @Override
        public Num calculate(Position position) {
            return calculate(position, position.getExit().getIndex());
        }

        @Override
        public Num calculate(Num price, Num amount) {
            return price.getNumFactory().numOf(fee);
        }

        @Override
        public boolean equals(CostModel model) {
            return model instanceof SerializableFeeModel other && fee == other.fee;
        }
    }
}
