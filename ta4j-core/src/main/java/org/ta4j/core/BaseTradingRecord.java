/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core;

import java.util.ArrayList;
import java.util.List;

import org.ta4j.core.Pos.PosType;
import org.ta4j.core.cost.CostModel;
import org.ta4j.core.cost.ZeroCostModel;
import org.ta4j.core.num.Num;

/**
 * Base implementation of a {@link TradingRecord}.
 *
 */
public class BaseTradingRecord implements TradingRecord {

    private static final long serialVersionUID = -4436851731855891220L;

    /**
     * The name of the trading record
     */
    private String name;

    /**
     * The recorded positions
     */
    private List<Pos> positions = new ArrayList<>();

    /**
     * The recorded BUY (LONG) positions
     */
    private List<Pos> buyPositions = new ArrayList<>();

    /**
     * The recorded SELL (SHORT) positions
     */
    private List<Pos> sellPositions = new ArrayList<>();

    /**
     * The recorded entry positions
     */
    private List<Pos> entryPositions = new ArrayList<>();

    /**
     * The recorded exit positions
     */
    private List<Pos> exitPositions = new ArrayList<>();

    /**
     * The recorded position pairs.
     */
    private List<PosPair> posPairs = new ArrayList<>();

    /**
     * The entry type (BUY or SELL) in the trading session
     */
    private PosType startingType;

    /**
     * The current non-closed position (there's always one)
     */
    private PosPair currentPosition;

    /**
     * Trading cost models
     */
    private CostModel transactionCostModel;
    private CostModel holdingCostModel;

    /**
     * Constructor.
     */
    public BaseTradingRecord() {
        this(PosType.BUY);
    }

    /**
     * Constructor.
     *
     * @param name the name of the tradingRecord
     */
    public BaseTradingRecord(String name) {
        this(PosType.BUY);
        this.name = name;
    }

    /**
     * Constructor.
     *
     * @param name              the name of the trading record
     * @param entryPositionType the {@link PosType position type} of entries in the
     *                          trading session
     */
    public BaseTradingRecord(String name, PosType entryPositionType) {
        this(entryPositionType, new ZeroCostModel(), new ZeroCostModel());
        this.name = name;
    }

    /**
     * Constructor.
     *
     * @param entryPositionType the {@link PosType position type} of entries in the
     *                          trading session
     */
    public BaseTradingRecord(PosType entryPositionType) {
        this(entryPositionType, new ZeroCostModel(), new ZeroCostModel());
    }

    /**
     * Constructor.
     *
     * @param entryPositionType    the {@link PosType position type} of entries in
     *                             the trading session
     * @param transactionCostModel the cost model for transactions of the asset
     * @param holdingCostModel     the cost model for holding asset (e.g. borrowing)
     */
    public BaseTradingRecord(PosType entryPositionType, CostModel transactionCostModel, CostModel holdingCostModel) {
        if (entryPositionType == null) {
            throw new IllegalArgumentException("Starting type must not be null");
        }
        this.startingType = entryPositionType;
        this.transactionCostModel = transactionCostModel;
        this.holdingCostModel = holdingCostModel;
        currentPosition = new PosPair(entryPositionType, transactionCostModel, holdingCostModel);
    }

    /**
     * Constructor.
     *
     * @param positions the positions to be recorded (cannot be empty)
     */
    public BaseTradingRecord(Pos... positions) {
        this(new ZeroCostModel(), new ZeroCostModel(), positions);
    }

    /**
     * Constructor.
     *
     * @param transactionCostModel the cost model for transactions of the asset
     * @param holdingCostModel     the cost model for holding asset (e.g. borrowing)
     * @param positions            the positions to be recorded (cannot be empty)
     */
    public BaseTradingRecord(CostModel transactionCostModel, CostModel holdingCostModel, Pos... positions) {
        this(positions[0].getType(), transactionCostModel, holdingCostModel);
        for (Pos pos : positions) {
            boolean newOrderWillBeAnEntry = currentPosition.isNew();
            if (newOrderWillBeAnEntry && pos.getType() != startingType) {
                // Special case for entry/exit types reversal
                // E.g.: BUY, SELL,
                // BUY, SELL,
                // SELL, BUY,
                // BUY, SELL
                currentPosition = new PosPair(pos.getType(), transactionCostModel, holdingCostModel);
            }
            Pos newOrder = currentPosition.operate(pos.getIndex(), pos.getPricePerAsset(), pos.getAmount());
            recordOrder(newOrder, newOrderWillBeAnEntry);
        }
    }

    @Override
    public PosType getStartingType() {
        return startingType;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public PosPair getCurrentPair() {
        return currentPosition;
    }

    @Override
    public void operate(int index, Num price, Num amount) {
        if (currentPosition.isClosed()) {
            // Current position closed, should not occur
            throw new IllegalStateException("Current position should not be closed");
        }
        boolean newOrderWillBeAnEntry = currentPosition.isNew();
        Pos newOrder = currentPosition.operate(index, price, amount);
        recordOrder(newOrder, newOrderWillBeAnEntry);
    }

    @Override
    public boolean enter(int index, Num price, Num amount) {
        if (currentPosition.isNew()) {
            operate(index, price, amount);
            return true;
        }
        return false;
    }

    @Override
    public boolean exit(int index, Num price, Num amount) {
        if (currentPosition.isOpened()) {
            operate(index, price, amount);
            return true;
        }
        return false;
    }

    @Override
    public List<PosPair> getPairs() {
        return posPairs;
    }

    @Override
    public Pos getLastPosition() {
        if (!positions.isEmpty()) {
            return positions.get(positions.size() - 1);
        }
        return null;
    }

    @Override
    public Pos getLastPosition(PosType positionType) {
        if (PosType.BUY.equals(positionType) && !buyPositions.isEmpty()) {
            return buyPositions.get(buyPositions.size() - 1);
        } else if (PosType.SELL.equals(positionType) && !sellPositions.isEmpty()) {
            return sellPositions.get(sellPositions.size() - 1);
        }
        return null;
    }

    @Override
    public Pos getLastEntry() {
        if (!entryPositions.isEmpty()) {
            return entryPositions.get(entryPositions.size() - 1);
        }
        return null;
    }

    @Override
    public Pos getLastExit() {
        if (!exitPositions.isEmpty()) {
            return exitPositions.get(exitPositions.size() - 1);
        }
        return null;
    }

    /**
     * Records a position and the corresponding position pair (if closed).
     *
     * @param position the position to be recorded
     * @param isEntry  true if the position is an entry, false otherwise (exit)
     */
    private void recordOrder(Pos position, boolean isEntry) {
        if (position == null) {
            throw new IllegalArgumentException("Position should not be null");
        }

        // Storing the new position in entries/exits lists
        if (isEntry) {
            entryPositions.add(position);
        } else {
            exitPositions.add(position);
        }

        // Storing the new position in positions list
        positions.add(position);
        if (PosType.BUY.equals(position.getType())) {
            // Storing the new position in buy positions list
            buyPositions.add(position);
        } else if (PosType.SELL.equals(position.getType())) {
            // Storing the new position in sell positions list
            sellPositions.add(position);
        }

        // Storing the position if closed
        if (currentPosition.isClosed()) {
            posPairs.add(currentPosition);
            currentPosition = new PosPair(startingType, transactionCostModel, holdingCostModel);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("BaseTradingRecord: " + name != null ? name : "" + "\n");
        for (Pos pos : positions) {
            sb.append(pos.toString()).append("\n");
        }
        return sb.toString();
    }
}
